(ns pingpong-clojure.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(defonce app-state (atom {}))
(defonce game-state (atom {}))

(go (let [response (<! (http/get "http://127.0.0.1:9090" {:with-credentials? false}))]
      (println (:body response))
      (reset! app-state (:body response))))

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

(defn game-ui [state]
  (let [state (get-in @game-state [:game :state])]
    (cond
      (= state "running") [:div
                           [:svg {:style {:width "100%" :height "100%" :position "absolute"}}
                            [:circle {:style {:fill "black"}
                                      :id "ball"
                                      :cx (str (* js/window.innerWidth (/ (get-in @game-state [:ball :position :x]) 100)) "px")
                                      :cy (str (* js/window.innerHeight (/ (get-in @game-state [:ball :position :y]) 100)) "px")
                                      :r (str (get-in @game-state [:ball :radius]) "%")}]]
                           [:div {:id "enemy" :style {:max-width (str (get-in @game-state [:playerTwo :width]) "%")
                                                      :left (str (get-in @game-state [:playerTwo :x]) "%")
                                                      :background-color (get-in @game-state [:playerTwo :color])}}]
                           [:div {:id "own" :style {:max-width (str (get-in @game-state [:playerOne :width]) "%")
                                                    :left (str (get-in @game-state [:playerOne :x]) "%")
                                                    :background-color (get-in @game-state [:playerOne :color])}}]]
      :else [:div {:class "modal-dialog" :role "document"}
             [:div {:class "modal-content"}
              [:div {:class "modal-header"}
               [:h5 {:class "modal-title"} "Lets play a game"]]
              [:div {:class "modal-body"} @app-state]
              [:div {:class "modal-footer"}
               [:button {:type "button" :class "btn btn-primary"
                         :on-click #(send-to-server "start-local")}
                "Start"]]]])))

(r/render-component [game-ui game-state] (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your game-state to force rerendering depending on
  ;; your application
  ;; (swap! game-state update-in [:__figwheel_counter] inc)
  )

                                                                                