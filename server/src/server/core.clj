(ns server.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [cheshire.core :refer :all]
            [server.logic :refer [game-loop initial-game-state]])
  (:use [org.httpkit.server]
        [overtone.at-at]
        [clojure.data.json :only [json-str read-json]]))


(def games (atom []))

(defn get-game-state [games channel]
  (first (filter
          #(or (= (:channel (:playerOne %)) channel) (= (:channel (:playerTwo %)) channel))
          games)))

(defn send-changes-to-clients [game-state channel]
  (send! channel (json-str game-state)))

(def game-loop-object (atom nil))

(defn get-awailable-games [] (vec (map #(select-keys % [:name])
                                       (->> (filter (comp nil? :playerTwo) @games)
                                            (map #(get-in % [:playerOne]))))))


(defn add-channel-to-game [channel playerName]
  (let [state {:playerOne {:channel channel :name playerName} :game-state initial-game-state}]
    (swap! games conj state)))

(defn remove-channel-from-games [channel] 
  (reset! games (let [my-count channel]
                  (vec (filter #(not= (or (:channel (:playerOne %)) my-count)
                                      (or (:channel (:playerTwo %)) my-count)) @games)))))

(defn update-game-state [channel d]
  (let [x (map 
           #(if (or (= (:channel (:playerOne %)) channel) (= (:channel (:playerTwo %)) channel)) d %) 
           @games)]
    (reset! games x)))

(def my-pool (mk-pool))

(defn start-game []
  (every 30 #(do
               (doseq [x @games] (send-changes-to-clients (:game-state x) (-> x :playerOne :channel)))
               (reset! games (filter (fn [g] not= :game-over (-> g :game-state :game :state)) @games))
               (let [g (vec (doall (pmap game-loop @games)))]
                 (reset! games g)))
         my-pool))

(defn handler [request]
  (with-channel request channel
    (on-close channel (fn [status]
                        (let [gs (get-game-state @games channel)]
                          (println (and (not= gs nil) (= 1 (count @games))))
                          (if (and (not= gs nil) (= 1 (count @games)))
                            (stop @game-loop-object))
                          (remove-channel-from-games channel)
                          (println "channel closed: " status))))
    (on-receive channel (fn [msg]
                          (let [data (read-json msg)
                                command (:command data)]
                            (cond
                              (= command "start-local") (do
                                                          (println "STARTING GAME")
                                                          (stop-and-reset-pool! my-pool)
                                                          ; (reset! game-state initial-game-state)
                                                          (remove-channel-from-games channel)
                                                          (add-channel-to-game channel "MIKKO")
                                                          (if (not= game-loop-object nil)
                                                            (reset! game-loop-object (start-game))))
                              (= command "stop") (do (println "STOPPING...")
                                                   (stop-and-reset-pool! my-pool))
                              (= command "own-right-down") (update-game-state channel (assoc-in (get-game-state @games channel) [:game-state :playerOne :input :rightDown] true))
                              (= command "own-left-down") (update-game-state channel (assoc-in (get-game-state @games channel) [:game-state :playerOne :input :leftDown] true))
                              (= command "enemy-right-down") (update-game-state channel (assoc-in (get-game-state @games channel) [:game-state :playerTwo :input :rightDown] true))
                              (= command "enemy-left-down") (update-game-state channel (assoc-in (get-game-state @games channel) [:game-state :playerTwo :input :leftDown] true))
                              (= command "own-right-up") (update-game-state channel (assoc-in (get-game-state @games channel) [:game-state :playerOne :input :rightDown] false))
                              (= command "own-left-up") (update-game-state channel (assoc-in (get-game-state @games channel) [:game-state :playerOne :input :leftDown] false))
                              (= command "enemy-right-up") (update-game-state channel (assoc-in (get-game-state @games channel) [:game-state :playerTwo :input :rightDown] false))
                              (= command "enemy-left-up") (update-game-state channel (assoc-in (get-game-state @games channel) [:game-state :playerTwo :input :leftDown] false))))))))

(defroutes all-routes
  (GET "/" [] {:status 200
               :headers {"Content-Type" "application/json; charset=utf-8"
                         "Access-Control-Allow-Origin" "*"}
               :body (generate-string (get-awailable-games) {:pretty true :escape-non-ascii true})})
  (GET "/ws" [] handler))

(defn -main [& args] 
  (run-server all-routes {:port 9090})
  (println "SERVER STARTED"))

