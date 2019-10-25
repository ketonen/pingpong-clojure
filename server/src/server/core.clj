(ns server.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [cheshire.core :refer :all])
  (:use [org.httpkit.server]
        [overtone.at-at]
        [clojure.data.json :only [json-str read-json]]))


(def games (atom []))

(def initial-game-state {:game {:state :running}
                         :playerOne {:x 30 :height 3 :y 98 :width 20 :color "blue" :input {:leftDown false :rightDown false}}
                         :playerTwo {:x 30 :height 3 :y 2 :width 20 :color "red" :input {:leftDown false :rightDown false}}
                         :ball {:radius 2 :position {:x 50 :y 50} :step {:x 0 :y 1}}})

(defn move-ball! [game-state]
  (update-in game-state [:ball :position :x] + (get-in game-state [:ball :step :x]))
  (update-in game-state [:ball :position :y] + (get-in game-state [:ball :step :y])))

(defn game-over? [ball]
  (let [ballTop (:y (:position ball))
        ballBottom (+ (:y (:position ball)) (:radius ball))]
    (or (<= ballTop 0) (>= ballBottom 100))))

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

(defn revert-direction [k game-state]
  (update-in game-state [:ball :step k] -))

(defn ball-hit-side-wall? [game-state] 
  (let [ball-steps (get-in game-state [:ball :step])
        x-direction (if (< (:x ball-steps) 0) :down :up)]
    (or
     (and
      (= x-direction :down)
      (>= 0 (get-in game-state [:ball :position :x])))
     (and
      (= x-direction :up)
      (<= 100 (get-in game-state [:ball :position :x]))))))

(defn increase-ball-axis [game-state k]
  (if (< (get-in game-state [:ball :step k]) 2.5)
    (cond
      (< (get-in game-state [:ball :step k]) 0) (update-in game-state [:ball :step k] - 0.1)
      (> (get-in game-state [:ball :step k]) 0) (update-in game-state [:ball :step k] + 0.1)
      :else game-state)
    game-state))

(defn increase-ball-speed! [game-state]
  (-> game-state 
      (increase-ball-axis :x)
      (increase-ball-axis :y)))

(defn decrease-ball-speed! [game-state] (swap! game-state update-in [:ball :step :x] dec))
(defn send-changes-to-clients [game-state channel] 
  (send! channel (json-str game-state)))

(defn add-channel-to-game [channel playerName]
  (let [state {:playerOne {:channel channel :name playerName} :game-state initial-game-state}]
    (swap! games conj state)))

(defn remove-channel-from-games [channel] (reset! games (let [my-count channel]
                                            (filter #(not= (or (:channel (:playerOne %)) my-count)
                                                           (or (:channel (:playerTwo %)) my-count)) @games))))


(defn move-bar! [game-state k] (cond
                                 (and (< (+ (get-in game-state [k :width]) (get-in game-state [k :x])) 100) (get-in game-state [k :input :rightDown]))
                                 (update-in game-state [k :x] inc)
                                 (and (> (get-in game-state [k :x]) 0) (get-in game-state [k :input :leftDown]))
                                 (update-in game-state [k :x] dec)
                                 :else game-state))
(defn move-bars! [game-state] 
  (move-bar! game-state :playerOne) (move-bar! game-state :playerTwo))

(defn add-momentum! [game-state f]
  (cond
    (= (get-in game-state [:ball :step :x]) 0) (swap! game-state assoc-in [:ball :step :x] (f 0.2))
    :else (swap! game-state update-in [:ball :step :x] f 0.2)))

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

(defn generate-next-state [game-state]
  (cond
    (game-over? (:ball game-state)) (game-state [:game :state] :game-over)
    :else
    (-> game-state
        move-ball!
        move-bars!
        (#(cond
            (ball-hit-side-wall? %) (revert-direction :x %)
            :else %))
        (#(if-let [collision (collide? %)]
            (do
              (println "COLLIDE!")
              (increase-ball-speed!
               (revert-direction :y
                                 (check-momentum collision %)))) %)))))

(defn game-loop [game]
  (let [s (generate-next-state (:game-state game))]
    (assoc-in game [:game-state] s)))

(def my-pool (mk-pool))

(def game-loop-object (atom nil))
(defn start-game []
  (every 30 #(let [g (doall (pmap game-loop @games))]
                 (reset! games g)
                 (apply
                  (fn [x] (send-changes-to-clients (:game-state x) (-> x :playerOne :channel))) @games))
         my-pool))

(defn get-game-state [games channel]
  (filter 
   #(or (= (:channel (:playerOne %)) channel) (= (:channel (:playerTwo %)) channel)) 
   games))

(defn get-awailable-games [] (vec (map #(select-keys % [:name])
                                       (->> (filter (comp nil? :playerTwo) @games)
                                         (map #(get-in % [:playerOne]))))))

(defn handler [request]
  (with-channel request channel
    (on-close channel (fn [status] 
                        (let [gs (get-game-state @games channel)]
                          (do
                            (println (and (not= gs nil) (= 1 (count @games))))
                            (if (and (not= gs nil) (= 1 (count @games)))
                              (stop @game-loop-object))
                            (println "channel closed: " status)))))
    (on-receive channel (fn [msg]
                          (let [data (read-json msg)
                                command (:command data)]
                            (cond
                              (= command "start-local") (do
                                                          (println "STARTING GAME")
                                                          (stop-and-reset-pool! my-pool)
                                                          ; (reset! game-state initial-game-state)
                                                          (add-channel-to-game channel "KEIJO")
                                                          (if (not= game-loop-object nil)
                                                            (reset! game-loop-object (start-game))))
                              (= command "stop") (do (println "STOPPING...")
                                                     (stop-and-reset-pool! my-pool))
                              (= command "own-right-down") (swap! (get-game-state @games channel) assoc-in [:playerOne :input :rightDown] true)
                              (= command "own-left-down") (swap! (get-game-state @games channel) assoc-in [:playerOne :input :leftDown] true)
                              (= command "enemy-right-down") (swap! (get-game-state @games channel) assoc-in [:playerTwo :input :rightDown] true)
                              (= command "enemy-left-down") (swap! (get-game-state @games channel) assoc-in [:playerTwo :input :leftDown] true)
                              (= command "own-right-up") (swap! (get-game-state @games channel) assoc-in [:playerOne :input :rightDown] false)
                              (= command "own-left-up") (swap! (get-game-state @games channel) assoc-in [:playerOne :input :leftDown] false)
                              (= command "enemy-right-up") (swap! (get-game-state @games channel) assoc-in [:playerTwo :input :rightDown] false)
                              (= command "enemy-left-up") (swap! (get-game-state @games channel) assoc-in [:playerTwo :input :leftDown] false)))))))

(defroutes all-routes
  (GET "/" [] {:status 200
               :headers {"Content-Type" "application/json; charset=utf-8"
                         "Access-Control-Allow-Origin" "*"}
               :body (generate-string (get-awailable-games) {:pretty true :escape-non-ascii true})})
  (GET "/ws" [] handler))

(defn -main [& args] 
  (run-server all-routes {:port 9090})
  (println "SERVER STARTED"))

