(ns pingpong-clojure.core
  (:require [reagent.core :as r :refer [atom]]
            [re-frame.core :as rf]
            [day8.re-frame.http-fx]
            [devtools.core :as devtools]
            [ajax.core :as ajax]
            [pingpong-clojure.components :as c]
            [pingpong-clojure.helpers :as h]))

(devtools/install!)       ;; https://github.com/binaryage/cljs-devtools
(enable-console-print!)

(def conn (js/WebSocket. "ws://127.0.0.1:9090/ws"))
(set! (.-onopen conn) (fn [_] (println "CONNECTION ESTABLISHED")))
(set! (.-onmessage conn)
      (fn [e]
        (let [data (js->clj (.parse js/JSON (.-data e)) :keywordize-keys true)]
          (rf/dispatch [:game-state-updated data]))))

(defn send-to-server
  ([conn msg data] (.send conn (.stringify js/JSON (js-obj "command" msg "extra" (clj->js data)))))
  ([conn msg] (.send conn (.stringify js/JSON (js-obj "command" msg)))))

;; -- Domino 2 - Event Handlers -----------------------------------------------

(rf/reg-event-db              ;; sets up initial application state
 :initialize                 ;; usage:  (dispatch [:initialize])
 (fn [_ _]                   ;; the two parameters are not important here, so use _
   (println "INIT")
   {:options h/game-type-selection-options-defaults
    :game {}}))    ;; so the application state will initially be a map with two keys

(rf/reg-event-db
 :game-state-updated
 (fn [db [_ state]]
   (assoc-in db [:game] state)))

(rf/reg-event-db
 :game-type-selected
 (fn [db [_ game-type]]
   (assoc-in db [:options :game-type] game-type)))

(rf/reg-event-db
 :player-name-updated
 (fn [db [_ [player name]]]
   (assoc-in db [:options player] name)))

(rf/reg-event-db
 :success-http-result
 (fn [db [_ result]]
   (assoc-in db [:options :awailable-games] result)))

(rf/reg-event-db
 :select-game
 (fn [db [_ result]]
   (println result)
   (assoc-in db [:options :selected-game] result)))

(rf/reg-event-db
 :bad-http-result
 (fn [db [_ result]]
   (println result)
   db
   ;; result is a map containing details of the failure
   #_(assoc db :failure-http-result result)))

(rf/reg-event-fx
 :update-awailable-games
 (fn [_]
   {:http-xhrio {:method          :get
                 :uri             "http://127.0.0.1:9090"
                 :response-format (ajax/json-response-format {:keywords? true})  ;; IMPORTANT!: You must provide this.
                 :on-success      [:success-http-result]
                 :on-failure      [:bad-http-result]}}))

(rf/reg-event-fx
 :start-game
 (fn [cofx [_ game-type]]
   (let [db (:db cofx)]
     (condp = game-type
       :join-game (send-to-server conn "join-game" {:game-name (-> db :options :selected-game)
                                                      :playerTwoName (-> db :options :playerTwoName)})
       :local (send-to-server conn "start-local" {:playerOneName (-> db :options :playerOneName)
                                                    :playerTwoName (-> db :options :playerTwoName)})
       :online (do (send-to-server conn "start-online" {:playerOneName (-> db :options :playerOneName)})
                   (rf/dispatch [:game-type-selected :online]))))))

(rf/reg-event-db
 :new-online-game
 (fn [db [_ b]]
   (assoc-in db [:options :new-online-game] b)))

(rf/reg-event-db
 :awailable-games
 (fn [db [_ games]]
   (assoc-in db [:options :awailable-games] games)))

(rf/reg-sub
 :options
 (fn [db _]
   (:options db)))

(rf/reg-sub
 :game
 (fn [db _]
   (:game db)))


(println "This text is printed from src/pingpong-clojure/core.cljs. Go ahead and edit it and see reloading in action.")


(defonce input-state (atom {:playerTwo {:leftDown false :rightDown false}
                            :playerOne {:leftDown false :rightDown false}}))


(defn update-input-state! [key value]
  (when (= key "ArrowRight") (send-to-server conn (if value "own-right-down" "own-right-up")))
  (when (= key "ArrowLeft") (send-to-server conn (if value "own-left-down" "own-left-up")))
  (when (= key "s") (send-to-server conn (if value "enemy-right-down" "enemy-right-up")))
  (when (= key "a") (send-to-server conn (if value "enemy-left-down" "enemy-left-up"))))

(defn keydown-listener [event] (update-input-state! (.-key event) true))
(defn keyup-listener [event] (update-input-state! (.-key event) false))

(.addEventListener js/window "keydown" keydown-listener)
(.addEventListener js/window "keyup" keyup-listener)

(defn game-ui []
  (let [game @(rf/subscribe [:game])]
    (if (seq game)
      [c/game game]
      [c/menu conn])))

(defn render
  []
  (r/render [game-ui]
            (js/document.getElementById "app")))

#_(defn ^:dev/after-load clear-cache-and-render!
    []
  ;; The `:dev/after-load` metadata causes this function to be called
  ;; after shadow-cljs hot-reloads code. We force a UI update by clearing
  ;; the Reframe subscription cache.
    (rf/clear-subscription-cache!)
    (render))

(rf/dispatch-sync [:initialize]) ;; put a value into application state
(render)                         ;; mount the application's ui into '<div id="app" />

(defn on-js-reload []
  ;; optionally touch your game-state to force rerendering depending on
  ;; your application
  ;; (swap! game-state update-in [:__figwheel_counter] inc)
  )

