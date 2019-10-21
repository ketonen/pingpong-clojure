(ns server.core
  (:gen-class)
  (:use org.httpkit.server)
  (:use [overtone.at-at]
        [clojure.data.json :only [json-str read-json]]))


(def games (atom ()))

(def game-state (atom 
                 {:game {:state :running}
                  :playerOne {:x 30 :height 3 :y 2 :bar-width 10 :input {:leftDown false :rightDown false}}
                  :playerTwo {:x 30 :height 3 :y 98 :bar-width 10 :input {:leftDown false :rightDown false}}
                  :ball {:diameter 5 :position {:x 50 :y 50} :step {:x 1 :y 1}}}))

(defn move-ball! []
  (swap! game-state update-in [:ball :position :x] + (get-in @game-state [:ball :step :x]))
  (swap! game-state update-in [:ball :position :y] + (get-in @game-state [:ball :step :y])))

(defn game-over? [] false)

(defn collide? [ball target] (let
                               [bar (target @game-state)
                                ballLeft (:x (:position ball))
                                ballRight (+ (:x (:position ball)) (:diameter ball))
                                ballTop (+ (:y (:position ball)) (:diameter ball))
                                ballBottom (:x (:position ball))
                                barLeft (:x bar)
                                barRight (+ (:x bar) (:bar-width bar))
                                barTop (:y bar)
                                barBottom (+ (:y bar) (:height bar))]
                               (not
                                (or
                                 (< ballRight barLeft)
                                 (> ballLeft barRight)
                                 (< ballBottom barTop)
                                 (> ballTop barBottom)))))

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
(ball-top-or-bottom-wall?)

(defn increase-ball-speed! [] (swap! game-state update-in [:ball :step :x] inc))
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
(defn add-momentum! [] ())

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
          (let [ball-position (get-in @game-state [:ball])]
            (if (or (collide? ball-position :playerOne)
                    (collide? ball-position :playerTwo))
              (do
                (println "COLLIDE")
                (revert-direction :y)
                (increase-ball-speed!))))
          (send-changes-to-clients game))))))

(def my-pool (mk-pool))

(defn start-game [] 
  (every 30 game-loop my-pool))

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
                                (= command "enemy-left-up") (swap! game-state assoc-in [:playerTwo :input :leftDown] false)))
                            (println @game-state))))))

(start-game)
(run-server handler {:port 9090})

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))