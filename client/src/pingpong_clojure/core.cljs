(ns pingpong-clojure.core
  (:require [reagent.core :as r :refer [atom]]))

(enable-console-print!)

(println "This text is printed from src/pingpong-clojure/core.cljs. Go ahead and edit it and see reloading in action.")

;; define your app data so that it doesn't get over-written on reload

(defonce input-state (atom {:enemy {:leftDown false :rightDown false}
                            :own {:leftDown false :rightDown false}}))

(def app-state-defaults {:game {:state :game-over}
                         :ball {:position {:x 200 :y 200} :step {:x 0 :y 3}}
                         :bars {:own {:x 10 :y 10} :enemy {:x 20 :y 20}}})

(defonce app-state (atom app-state-defaults))

(defonce state* (atom {}))

(defn ball-hit-side-wall? []
  (do
    (or
     (< (get-in @app-state [:ball :position :x]) 0)
     (> (get-in @app-state [:ball :position :x]) js/window.innerWidth))))

(defn move-ball [] (do
                     (swap! app-state assoc-in [:ball :position :y]
                            (+ (get-in @app-state [:ball :position :y])
                               (get-in @app-state [:ball :step :y])))
                     (swap! app-state assoc-in [:ball :position :x]
                            (+ (get-in @app-state [:ball :position :x])
                               (get-in @app-state [:ball :step :x])))))

(defn revert-direction-x [] (swap! app-state update-in [:ball :step :x] -))
(defn revert-direction-y []
  (swap! app-state update-in [:ball :step :y] -)
  (println (if (< (get-in @app-state [:ball :step :y]) 0) :up :down)))

(defn game-over? [] (or
                     (and (< (get-in @app-state [:ball :step :y]) 0)
                          (< (get-in @app-state [:ball :position :y]) 0))
                     (and (> (get-in @app-state [:ball :step :y]) 0)
                          (> (get-in @app-state [:ball :position :y]) js/window.innerHeight))))



(defn update-input-state! [key value]
  (if (= key "ArrowRight") (swap! input-state assoc-in [:own :rightDown] value))
  (if (= key "ArrowLeft") (swap! input-state assoc-in [:own :leftDown] value))
  (if (= key "s") (swap! input-state assoc-in [:enemy :rightDown] value))
  (if (= key "a") (swap! input-state assoc-in [:enemy :leftDown] value)))

(defn keydown-listener [event] (update-input-state! (.-key event) true))
(defn keyup-listener [event] (update-input-state! (.-key event) false))

(.addEventListener js/window "keydown" keydown-listener)
(.addEventListener js/window "keyup" keyup-listener)

(defn update-bars-locations! []
  (let [bar (bar-location "own")]
    (if (and (< (.-right bar) js/window.innerWidth) (= true (get-in @input-state [:own :rightDown])))
      (swap! app-state update-in [:bars :own :x] inc))
    (if (and (> (.-left bar) 0) (= true (get-in @input-state [:own :leftDown])))
      (swap! app-state update-in [:bars :own :x] dec)))
  (let [bar (bar-location "enemy")]
    (if (and (< (.-right bar) js/window.innerWidth) (= true (get-in @input-state [:enemy :rightDown])))
      (swap! app-state update-in [:bars :enemy :x] inc))
    (if (and (> (.-left bar) 0) (= true (get-in @input-state [:enemy :leftDown]))) 
      (swap! app-state update-in [:bars :enemy :x] dec))))

(defn between [v min max] (and (> v min) (< v max)))

(defn increase-ball-speed! [] 
  (let [y (get-in @app-state [:ball :step :y])
        x (get-in @app-state [:ball :step :x])]
    (if (between y -7 7)
      (swap! app-state update-in [:ball :step :y] #(if (< % 0) (dec %) (inc %))))
    (if (and (between x -7 7) (not= x 0))
      (swap! app-state update-in [:ball :step :x] #(if (< % 0) (dec %) (inc %))))))
  
(defn add-momentum! [f] 
  (println "momentum added")
  (swap! app-state update-in [:ball :step :x] f))

(defn handler []
  (if (= (get-in @app-state [:game :state]) :running)
    (do
      (update-bars-locations!)
      (if (ball-hit-side-wall?) (revert-direction-x))
      (let [ball (ball-location)
            ballDirection (if (< (get-in @app-state [:ball :step :y]) 0) :up :down)]
        (do
          (cond
            (and (= ballDirection :up) (collide? ball (bar-location "enemy")))
            (do
              (println "COLLIDE ENEMY")
              (revert-direction-y)
              (cond
                (get-in @input-state [:enemy :rightDown]) (add-momentum! inc)
                (get-in @input-state [:enemy :leftDown]) (add-momentum! dec))
              (increase-ball-speed!))
            (and (= ballDirection :down) (collide? ball (bar-location "own")))
            (do
              (println "COLLIDE OWN")
              (revert-direction-y)
              (cond
                (get-in @input-state [:own :rightDown]) (add-momentum! inc)
                (get-in @input-state [:own :leftDown]) (add-momentum! dec))
              (increase-ball-speed!)))))
      (move-ball)
      (if (game-over?)
        (do
          (println "GAME OVER")
          (swap! app-state assoc-in [:game :state] :game-over)
          (println "Clearing interval")
          (js/clearInterval (:polling-id @state*)))))))

(defn start-game-loop []   
  (swap! state* assoc :polling-id (js/setInterval handler 20)))

(defn bar-own [] [:div {:id "own" :style {:left (str (get-in @app-state [:bars :own :x]) "%")}}])

(defn bar-enemy [] [:div {:id "enemy" :style {:left (str (get-in @app-state [:bars :enemy :x]) "%")}}])

(defn ball [] [:svg {:style {:width "100%" :height "100%" :position "absolute"}}
               [:circle {:style {:fill "black"}
                         :id "ball"
                         :cx (str (get-in @app-state [:ball :position :x]) "px")
                         :cy (str (get-in @app-state [:ball :position :y]) "px")
                         :r "20"}]])

(defn bar-location [id] (-> (. js/document (getElementById id))
                            r/dom-node
                            .getBoundingClientRect))

(defn ball-location [] (-> (. js/document (getElementById "ball"))
                           r/dom-node
                           .getBoundingClientRect))

(defn collide? [ballRect barRect] (let
                                   [ballLeft (.-left ballRect)
                                    ballRight (.-right ballRect)
                                    ballTop (.-top ballRect)
                                    ballBottom (.-bottom ballRect)
                                    barLeft (.-left barRect)
                                    barRight (.-right barRect)
                                    barTop (.-top barRect)
                                    barBottom (.-bottom barRect)]
                                    (not
                                     (or
                                      (< ballRight barLeft)
                                      (> ballLeft barRight)
                                      (< ballBottom barTop)
                                      (> ballTop barBottom)))))

(defn game-ui [state]
  (let [state (get-in @app-state [:game :state])]
    (cond (= state :running)
          [:div
           [:svg {:style {:width "100%" :height "100%" :position "absolute"}}
            [:circle {:style {:fill "black"}
                      :id "ball"
                      :cx (str (get-in @app-state [:ball :position :x]) "px")
                      :cy (str (get-in @app-state [:ball :position :y]) "px")
                      :r "20"}]]
           [:div {:id "enemy" :style {:left (str (get-in @app-state [:bars :enemy :x]) "%")}}]
           [:div {:id "own" :style {:left (str (get-in @app-state [:bars :own :x]) "%")}}]]
          :else
          [:div {:class "modal-dialog" :role "document"}
           [:div {:class "modal-content"}
            [:div {:class "modal-header"}
             [:h5 {:class "modal-title"} "Lets play a game"]]
            [:div {:class "modal-body"}]
            [:div {:class "modal-footer"}
             [:button {:type "button" :class "btn btn-primary"
                       :on-click #(do
                                    (println "RESETTING GAME")
                                    (reset! app-state app-state-defaults)
                                    (swap! app-state assoc-in [:game :state] :running)
                                    (start-game-loop))}
              "Start"]]]])))

(r/render-component [game-ui app-state] (. js/document (getElementById "app")))

(defn on-js-reload []
  (js/clearInterval (:polling-id @state*))
  ; (swap! state* assoc :polling-id (js/setInterval handler 10))
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )

