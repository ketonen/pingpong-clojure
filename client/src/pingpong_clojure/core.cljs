(ns pingpong-clojure.core
  (:require [reagent.core :as r :refer [atom]]
            [pingpong-clojure.components :as c]
            [pingpong-clojure.helpers :as h]))

(defonce game-state (atom {}))

(enable-console-print!)

(println "This text is printed from src/pingpong-clojure/core.cljs. Go ahead and edit it and see reloading in action.")

(def conn (js/WebSocket. "ws://127.0.0.1:9090/ws"))
(set! (.-onopen conn) (fn [_] (println "CONNECTION ESTABLISHED")))
(set! (.-onmessage conn)
      (fn [e]
        (let [data (js->clj (.parse js/JSON (.-data e)) :keywordize-keys true)]
          (reset! game-state data))))

(defonce input-state (atom {:playerTwo {:leftDown false :rightDown false}
                            :playerOne {:leftDown false :rightDown false}}))


(defn update-input-state! [key value]
  (when (= key "ArrowRight") (h/send-to-server conn (if value "own-right-down" "own-right-up")))
  (when (= key "ArrowLeft") (h/send-to-server conn (if value "own-left-down" "own-left-up")))
  (when (= key "s") (h/send-to-server conn (if value "enemy-right-down" "enemy-right-up")))
  (when (= key "a") (h/send-to-server conn (if value "enemy-left-down" "enemy-left-up"))))

(defn keydown-listener [event] (update-input-state! (.-key event) true))
(defn keyup-listener [event] (update-input-state! (.-key event) false))

(.addEventListener js/window "keydown" keydown-listener)
(.addEventListener js/window "keyup" keyup-listener)

(defn bar-location [id] (-> (.getElementById js/document id)
                            r/dom-node
                            .getBoundingClientRect))

(defn ball-location [] (-> (.getElementById js/document "ball")
                           r/dom-node
                           .getBoundingClientRect))

(defn bar [id player]
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


(defonce game-type-selection-options-defaults {:game-type :not-selected :playerOneName "" :playerTwoName ""})
(defonce game-type-selection-options (atom {:game-type :not-selected :playerOneName "" :playerTwoName ""}))

(defn game [] (let [state (-> @game-state :game :state)]
                (cond (= state "running") [:div
                                           [:svg {:style {:width "100%" :height "100%" :position "absolute"}}
                                            (when (-> @game-state :ball :visible) [ball (:ball @game-state)])
                                            (let [bonuses (:bonuses @game-state)]
                                              (doall (map-indexed (fn [index item]
                                                                    ^{:key (str "bl-" index)}
                                                                    [ball item]) bonuses)))]
                                           [bar "enemy" (:playerTwo @game-state)]
                                           [bar "own" (:playerOne @game-state)]]
                      (= state "game-over") [c/game-over conn game-state game-type-selection-options game-type-selection-options-defaults]
                      :else [c/game-type-selection conn game-type-selection-options game-type-selection-options-defaults])))

(defn game-ui []
  (cond (and (empty? @game-state) (= (:game-type @game-type-selection-options) :online))
        [c/game-selection conn game-type-selection-options game-type-selection-options-defaults]
        :else [game]))

(r/render-component [game-ui] (.getElementById js/document "app"))

(defn on-js-reload []
  ;; optionally touch your game-state to force rerendering depending on
  ;; your application
  ;; (swap! game-state update-in [:__figwheel_counter] inc)
  )

