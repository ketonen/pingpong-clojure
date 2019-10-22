(ns server.core
  (:gen-class)
  (:use org.httpkit.server)
  (:use [overtone.at-at]
        [clojure.data.json :only [json-str read-json]]))


(def games (atom ()))

(def game-state (atom
                 {:game {:state :running}
                  :playerOne {:x 30 :height 3 :y 98 :width 20 :color "blue" :input {:leftDown false :rightDown false}}
                  :playerTwo {:x 30 :height 3 :y 2 :width 20 :color "red" :input {:leftDown false :rightDown false}}
                  :ball {:radius 2 :position {:x 50 :y 50} :step {:x 0 :y 1}}}))

(defn move-ball! []
  (swap! game-state update-in [:ball :position :x] + (get-in @game-state [:ball :step :x]))
  (swap! game-state update-in [:ball :position :y] + (get-in @game-state [:ball :step :y])))

(defn game-over? [] false)

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

(defn collide? [game-state] (let
                             [ball (:ball game-state)
                              barPlayerOne (:playerOne game-state)
                              barPlayerTwo (:playerTwo game-state)
                              ball-steps (get-in game-state [:ball :step])
                              y-direction (if (> (:y ball-steps) 0) :down :up)]
                              (cond
                                (and (= y-direction :down) (collide-bar? ball barPlayerOne)) :playerOne
                                (and (= y-direction :up) (collide-bar? ball barPlayerTwo)) :playerTwo)))

(defn revert-direction [k] (swap! game-state update-in [:ball :step k] -))

(revert-direction :y)

(defn ball-hit-side-wall? [] (let [ball-steps (get-in @game-state [:ball :step])
                                   x-direction (if (< (:x ball-steps) 0) :down :up)]
                               (or
                                (and
                                 (= x-direction :down)
                                 (>= 0 (get-in @game-state [:ball :position :x])))
                                (and
                                 (= x-direction :up)
                                 (<= 100 (get-in @game-state [:ball :position :x]))))))

(defn ball-top-or-bottom-wall? [] (let [ball-steps (get-in @game-state [:ball :step])
                                        y-direction (if (< (:y ball-steps) 0) :down :up)]
                                    (or
                                     (and
                                      (= y-direction :up)
                                      (<= 100 (get-in @game-state [:ball :position :y])))
                                     (and
                                      (= y-direction :down)
                                      (>= 0 (get-in @game-state [:ball :position :y]))))))

(defn increase-ball-speed! [] ())
(defn decrease-ball-speed! [] (swap! game-state update-in [:ball :step :x] dec))
(defn send-changes-to-clients [channel] (send! channel (json-str @game-state)))
(defn add-channel-to-game [channel] (swap! games conj channel))
(defn move-bars! [] (do
                      (if (get-in @game-state [:playerOne :input :rightDown])
                        (swap! game-state update-in [:playerOne :x] inc))
                      (if (get-in @game-state [:playerOne :input :leftDown])
                        (swap! game-state update-in [:playerOne :x] dec))
                      (if (get-in @game-state [:playerTwo :input :rightDown])
                        (swap! game-state update-in [:playerTwo :x] inc))
                      (if (get-in @game-state [:playerTwo :input :leftDown])
                        (swap! game-state update-in [:playerTwo :x] dec))))

(defn add-momentum! [f]
  (cond
    (= (get-in @game-state [:ball :step :x]) 0) (swap! game-state assoc-in [:ball :step :x] (f 0.2))
    :else (swap! game-state update-in [:ball :step :x] f 0.2)))

(defn game-loop []
  (doseq [game @games]
    (do
      (if-not (game-over?)
        (do
          (move-ball!)
          (move-bars!)
          (if (ball-hit-side-wall?)
            (do
              (println "HIT THE SIDE WALL")
              (revert-direction :x)))
          (if (ball-top-or-bottom-wall?)
            (do
              (println "HIT THE TOP/BOTTOM WALL")
              (revert-direction :y)))
          (if-let [collision (collide? @game-state)]
            (do
              (if (>= (get-in @game-state [:ball :step :x]) 0)
                (cond
                  (= true (get-in @game-state [collision :input :rightDown])) (add-momentum! +)
                  (= true (get-in @game-state [collision :input :leftDown])) (add-momentum! -)))
              (if (<= (get-in @game-state [:ball :step :x]) 0)
                (cond
                  (= true (get-in @game-state [collision :input :leftDown])) (add-momentum! -)
                  (= true (get-in @game-state [collision :input :rightDown])) (add-momentum! +)))
              (revert-direction :y)))
          (send-changes-to-clients game))))))

(def my-pool (mk-pool))

(defn start-game [] (every 30 game-loop my-pool))

(defn handler [request]
  (with-channel request channel
    (on-close channel (fn [status] (println "channel closed: " status)))
    (on-receive channel (fn [msg]
                          (let [data (read-json msg)
                                command (:command data)]
                            (do
                              (println command)
                              (cond
                                (= command "start") (add-channel-to-game channel)
                                (= command "stop") ("STOPPING...")
                                (= command "own-right-down") (swap! game-state assoc-in [:playerOne :input :rightDown] true)
                                (= command "own-left-down") (swap! game-state assoc-in [:playerOne :input :leftDown] true)
                                (= command "enemy-right-down") (swap! game-state assoc-in [:playerTwo :input :rightDown] true)
                                (= command "enemy-left-down") (swap! game-state assoc-in [:playerTwo :input :leftDown] true)
                                (= command "own-right-up") (swap! game-state assoc-in [:playerOne :input :rightDown] false)
                                (= command "own-left-up") (swap! game-state assoc-in [:playerOne :input :leftDown] false)
                                (= command "enemy-right-up") (swap! game-state assoc-in [:playerTwo :input :rightDown] false)
                                (= command "enemy-left-up") (swap! game-state assoc-in [:playerTwo :input :leftDown] false))))))))

(start-game)
(run-server handler {:port 9090})

(defn -main [& args] (println "SERVER STARTED"))