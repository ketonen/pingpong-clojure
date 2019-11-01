(ns server.logic 
  (:require [clj-time.core :as t]))

(defn random-number
  ([start end step] (rand-nth (remove zero? (range start end step))))
  ([start end] (rand-nth (remove zero? (range start end)))))

(def bonuses [(fn [] {:name "double-bar" :color "blue" :radius 2 :position {:x 50 :y 50} :step {:x 0 :y (random-number -0.5 0.5 0.1)}})
              (fn [] {:name "invisible-ball" :color "red" :radius 2 :position {:x 50 :y 50} :step {:x 0 :y (random-number -0.5 0.5 0.1)}})])

(def initial-game-state {:game {:state :running}
                         :playerOne {:x 30 :height 3 :y 98 :width 20 :color "blue" :input {:leftDown false :rightDown false} :bonuses ()}
                         :playerTwo {:x 30 :height 3 :y 2 :width 20 :color "red" :input {:leftDown false :rightDown false} :bonuses ()}
                         :ball {:radius 2 :position {:x 50 :y 50} :step {:x 0 :y 1}}
                         :bonuses ()})

(defn move-ball [game-state]
  (-> game-state
      (update-in [:ball :position :x] + (get-in game-state [:ball :step :x]))
      (update-in [:ball :position :y] + (get-in game-state [:ball :step :y]))))

(defn move-bonuses [game-state]
  (assoc-in game-state [:bonuses]
            (map #(-> % 
                    (update-in [:position :x] + (get-in % [:step :x]))
                    (update-in [:position :y] + (get-in % [:step :y]))) (:bonuses game-state))))

(defn object-hit-top-or-bottom-wall? [obj]
  (let [objTop (:y (:position obj))
        objBottom (+ (:y (:position obj)) (:radius obj))]
    (or (<= objTop 0) (>= objBottom 100))))

(defn collide-bar? [ball bar] (let [barLeft (:x bar)
                                    barRight (+ (:x bar) (:width bar))
                                    barTop (:y bar)
                                    barBottom (+ (:y bar) (:height bar))
                                    ballLeft (:x (:position ball))
                                    ballRight (+ (:x (:position ball)) (:radius ball))
                                    ballTop (:y (:position ball))
                                    ballBottom (+ (:y (:position ball)) (:radius ball))]
                                (not
                                 (or
                                  (< ballRight barLeft)
                                  (> ballLeft barRight)
                                  (< ballBottom barTop)
                                  (> ballTop barBottom)))))

(defn collide? [game-state obj] (let
                             [barPlayerOne (:playerOne game-state)
                              barPlayerTwo (:playerTwo game-state)
                              obj-steps (get-in obj [:step])
                              y-direction (if (> (:y obj-steps) 0) :down :up)]
                              (cond
                                (and (= y-direction :down) (collide-bar? obj barPlayerOne)) {:player :playerOne :object obj}
                                (and (= y-direction :up) (collide-bar? obj barPlayerTwo)) {:player :playerTwo :object obj})))

(defn revert-direction [k game-state]
  (update-in game-state [:ball :step k] -))

(defn object-hit-side-wall? [obj]
  (let [ball-steps (get-in obj [:step])
        x-direction (if (< (:x ball-steps) 0) :down :up)]
    (or
     (and
      (= x-direction :down)
      (>= 0 (get-in obj [:position :x])))
     (and
      (= x-direction :up)
      (<= 100 (get-in obj [:position :x]))))))

(defn abs [n] (max n (- n)))

(defn increase-ball-axis [game-state k]
  (if (< (abs (get-in game-state [:ball :step k])) 1)
    (cond
      (< (get-in game-state [:ball :step k]) 0) (update-in game-state [:ball :step k] - 0.1)
      (> (get-in game-state [:ball :step k]) 0) (update-in game-state [:ball :step k] + 0.1)
      :else game-state)
    game-state))

(defn increase-ball-speed! [game-state]
  (-> game-state
      (increase-ball-axis :x)
      (increase-ball-axis :y)))

(defn decrease-ball-speed! [game-state] (update-in game-state [:ball :step :x] dec))

(defn move-bar! [game-state k]
  (cond
    (and (< (+ (get-in game-state [k :width]) (get-in game-state [k :x])) 100) (get-in game-state [k :input :rightDown]))
    (update-in game-state [k :x] inc)
    (and (> (get-in game-state [k :x]) 0) (get-in game-state [k :input :leftDown]))
    (update-in game-state [k :x] dec)
    :else game-state))

(defn move-bars! [game-state]
  (-> game-state
      (move-bar! :playerOne)
      (move-bar! :playerTwo)))

(defn add-momentum! [game-state f]
  (cond
    (= (get-in game-state [:ball :step :x]) 0) (assoc-in game-state [:ball :step :x] (f 0.2))
    :else (update-in game-state [:ball :step :x] f 0.2)))

(defn check-momentum [collision game-state]
  (cond
    (>= (get-in game-state [:ball :step :x]) 0)
    (cond
      (= true (get-in game-state [collision :input :rightDown])) (add-momentum! game-state +)
      (= true (get-in game-state [collision :input :leftDown])) (add-momentum! game-state -)
      :else game-state)
    (<= (get-in game-state [:ball :step :x]) 0)
    (cond
      (= true (get-in game-state [collision :input :leftDown])) (add-momentum! game-state -)
      (= true (get-in game-state [collision :input :rightDown])) (add-momentum! game-state +)
      :else game-state)
    :else game-state))

(defn generate-bonus? [] (= 1 (rand-int 200)))

(defn generate-bonuses! [game-state]
  (cond
    (generate-bonus?) (update-in game-state [:bonuses] conj ((rand-nth bonuses)))
    :else game-state))

; (defn now [] (t/now))

; (defn expired-bonus? [bonus] (< (:start-time bonus) (now ) ))

(defn check-bonuses-collision [gs]
  (let [game-state (assoc-in gs [:bonuses] (remove object-hit-top-or-bottom-wall? (:bonuses gs)))
        collisions (filter #(collide? game-state %) (:bonuses game-state))
        collisionResults (map #(collide? game-state %) collisions)
        playerOneCollisions (filter #(= :playerOne (:player %)) collisionResults)
        playerTwoCollisions (filter #(= :playerTwo (:player %)) collisionResults)]
    (-> game-state
        (#(remove (fn [x] (collide? % x)) (:bonuses %)))
        (#(assoc-in game-state [:bonuses] %))
      (update-in [:playerOne :bonuses] concat (map (fn [x] (-> x :object :name)) playerOneCollisions))
      (update-in [:playerTwo :bonuses] concat (map (fn [x] (-> x :object :name)) playerTwoCollisions)))))

(defn generate-next-state [game-state]
  (cond
    (object-hit-top-or-bottom-wall? (:ball game-state)) (assoc-in game-state [:game :state] :game-over)
    :else
    (-> game-state
      move-ball
      move-bonuses
      move-bars!
      generate-bonuses!
      check-bonuses-collision
      (#(cond
          (object-hit-side-wall? (:ball %)) (revert-direction :x %)
          :else %))
      (#(if-let [collision (collide? % (:ball %))]
          (do
            (println "COLLIDE!")
            (increase-ball-speed!
             (revert-direction :y
                               (check-momentum (:player collision) %)))) %)))))
    
(defn game-loop [game]
  (let [s (generate-next-state (:game-state game))]
    (assoc-in game [:game-state] s)))
