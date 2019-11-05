(ns pingpong-clojure.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(def app-state (atom ()))
(defonce game-state (atom {}))


(defn get-awailable-games []
  (go (let [response (<! (http/get "http://127.0.0.1:9090" {:with-credentials? false}))]
        (cond
          (not (empty? (:body response))) (reset! app-state (seq (:body response)))
          :else (reset! app-state '(:no-games))))))

(enable-console-print!)

(println "This text is printed from src/pingpong-clojure/core.cljs. Go ahead and edit it and see reloading in action.")

;; define your app data so that it doesn't get over-written on reload

(def conn
  (js/WebSocket. "ws://127.0.0.1:9090/ws"))

(defn send-to-server [msg] (.send conn (.stringify js/JSON (js-obj "command" msg))))

(set! (.-onopen conn) (fn [e] (println "CONNECTION ESTABLISHED")))

(set! (.-onmessage conn)
      (fn [e]
        (let [data (js->clj (.parse js/JSON (.-data e)) :keywordize-keys true)]
          (reset! game-state data))))

(defonce input-state (atom {:playerTwo {:leftDown false :rightDown false}
                            :playerOne {:leftDown false :rightDown false}}))


(defn update-input-state! [key value]
  (if (= key "ArrowRight") (send-to-server (if value "own-right-down" "own-right-up")))
  (if (= key "ArrowLeft") (send-to-server (if value "own-left-down" "own-left-up")))
  (if (= key "s") (send-to-server (if value "enemy-right-down" "enemy-right-up")))
  (if (= key "a") (send-to-server (if value "enemy-left-down" "enemy-left-up"))))

(defn keydown-listener [event] (update-input-state! (.-key event) true))
(defn keyup-listener [event] (update-input-state! (.-key event) false))

(.addEventListener js/window "keydown" keydown-listener)
(.addEventListener js/window "keyup" keyup-listener)

(defn bar-location [id] (-> (. js/document (getElementById id))
                          r/dom-node
                          .getBoundingClientRect))

(defn ball-location [] (-> (. js/document (getElementById "ball"))
                         r/dom-node
                         .getBoundingClientRect))

(defn bar [id player] 
  (println (map #(:name %) (:bonuses player)))
  [:div {:id id
         :class (clojure.string/join " " (map #(:name %) (:bonuses player)))
         :style {:max-width (str (:width player) "%")
                 :left (str (:x player) "%")
                 :background-color (:color player)}}])

(defn ball [m] [:circle {:style {:fill (:color m)}
                        :id "ball"
                        :cx (str (* js/window.innerWidth (/ (get-in m [:position :x]) 100)) "px")
                        :cy (str (* js/window.innerHeight (/ (get-in m [:position :y]) 100)) "px")
                        :r (str (get-in m [:radius]) "%")}])

(defn game-ui [state]
  (cond
    (not (empty? @app-state))
    (cond
      (not= (first @app-state) :no-games)
      [:div {:class "modal-dialog" :role "document"}
       [:div {:class "modal-content"}
        [:div {:class "modal-header"}
         [:h5 {:class "modal-title"} "Choose game"]]
        [:div {:class "modal-body"}
         [:div {:class "list-group"}
          (for [i @app-state]
            ^{:key (str "tr-" i)}
            [:a {:href "#" :class "list-group-item list-group-item-action"} (:name i)])]]
        [:div {:class "modal-footer"}
         [:button {:type "button" :class "btn btn-danger"
                   :on-click #(reset! app-state ())}
          "Cancel"]
         [:button {:type "button" :class "btn btn-primary"
                   :on-click #(send-to-server "start-online")}
          "Start"]]]]
      :else
      [:div {:class "modal-dialog" :role "document"}
       [:div {:class "modal-content"}
        [:div {:class "modal-header"}
         [:h5 {:class "modal-title"} "New game"]]
        [:div {:class "modal-body"} "No awailable games at the moment. Create new game?"]
        [:div {:class "modal-footer"}
         [:button {:type "button" :class "btn btn-danger"
                   :on-click #(reset! app-state ())}
          "Cancel"]
         [:button {:type "button" :class "btn btn-primary"
                   :on-click #(send-to-server "start-online")}
          "Create new game"]]]])
    :else 
    (let [state (get-in @game-state [:game :state])]
      (cond
        (= state "running") [:div
                             [:svg {:style {:width "100%" :height "100%" :position "absolute"}}
                              [ball (:ball @game-state)]
                              (let [bonuses (-> @game-state :bonuses)]
                                (doall (map-indexed (fn [index item]
                                                      ^{:key (str "bl-" index)}
                                                      [ball item]) bonuses)))]
                             [bar "enemy" (:playerTwo @game-state)]
                             [bar "own" (:playerOne @game-state)]]
        :else [:div {:class "modal-dialog" :role "document"}
               [:div {:class "modal-content"}
                [:div {:class "modal-header"}
                 [:h5 {:class "modal-title"} "Lets play a game"]]
                [:div {:class "modal-body"} @app-state]
                [:div {:class "modal-footer"}
                 [:button {:type "button" :class "btn btn-primary"
                           :on-click #(send-to-server "start-local")}
                  "Local game"]
                 [:button {:type "button" :class "btn btn-primary"
                           :on-click #(get-awailable-games)}
                  "Online game"]]]]))))

(r/render-component [game-ui] (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your game-state to force rerendering depending on
  ;; your application
  ;; (swap! game-state update-in [:__figwheel_counter] inc)
  )

                                                                                