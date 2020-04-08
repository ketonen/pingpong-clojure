(ns server.core
  (:require [compojure.handler :as handler :refer [site]]
            [compojure.core :refer [defroutes GET]]
            [cheshire.core :refer :all]
            [ring.middleware.reload :as reload]
            [server.logic :refer [game-loop initial-game-state initial-game-state]]
            [clojure.data.json :as json]
            [overtone.at-at :as at])
  (:use [org.httpkit.server :only [run-server send! with-channel on-close on-receive]]))

(set! *warn-on-reflection* true)

(def games (atom []))

(defn value-writer [key value]
  (cond (= key :start-time) (str value)
        (and (= key :ball) (false? (:visible value))) (dissoc value :position)
        :else value))

(defn get-game [games channel]
  (first (filter
          #(or (= (:channel (:playerOne %)) channel) (= (:channel (:playerTwo %)) channel))
          games)))

(defn send-changes-to-channel [game-state channel]
  (send! channel (json/write-str game-state :value-fn value-writer)))

(def game-loop-object (atom nil))

(defn get-awailable-games [games] (vec (map #(select-keys % [:name])
                                            (map (fn* [game] (get-in game [:playerOne]))
                                                 (filter (comp nil? :playerTwo) games)))))

(defmulti add-channel-to-game (fn [& x] (second x)))

(defmethod add-channel-to-game :online
  ([channel gameType playerName]
   {:playerOne {:channel channel :name playerName}
    :game-type gameType
    :game-state initial-game-state}))

(defmethod add-channel-to-game :local
  ([channel gameType playerOneName playerTwoName]
   {:playerOne {:channel channel :name playerOneName}
    :playerTwo {:channel channel :name playerTwoName}
    :game-type gameType
    :game-state initial-game-state}))

(defn remove-channel-from-games [games channel]
  (vec (remove #(or (= (:channel (:playerOne %)) channel)
                    (= (:channel (:playerTwo %)) channel)) games)))

(defn update-game-state! [channel d]
  (let [x (map #(if (or (= (:channel (:playerOne %)) channel) (= (:channel (:playerTwo %)) channel)) d %) @games)]
    (reset! games x)))

(def my-pool (at/mk-pool))

(defn filter-games-with-state [games state] (remove (fn [x] (= (-> x :game-state :game :state) state)) games))

(defn send-game-state-to-players [game]
  (let [gs (:game-state game)]
    (send-changes-to-channel gs (-> game :playerOne :channel))
    (when-let [c (-> game :playerTwo :channel)]
      (send-changes-to-channel gs c))))

(defn start-game []
  (at/every 30 #(do
                  (doseq [x (filter-games-with-state @games :waiting-player)]
                    (send-game-state-to-players x))
                  (reset! games (filter-games-with-state @games :game-over))
                  (let [g (vec (doall (pmap game-loop @games)))]
                    (reset! games g)))
            my-pool))

(defn update-input-state [channel event state] (let [game (get-game @games channel)
                                                     player (if (= channel (-> game :playerOne :channel)) :playerOne :playerTwo)]
                                                 (update-game-state! channel (assoc-in game [:game-state player :input event] state))))

(defn local-game? [game] (= :local (:game-type game)))

(defn update-local-game-input [game channel player input value]
  (when (local-game? game)
    (update-game-state! channel (assoc-in game [:game-state player :input input] value))))

(defn handler [request]
  (with-channel request channel
    (on-close channel
              (fn [status]
                (reset! games (remove-channel-from-games @games channel))
                (when (empty? @games)
                  (at/stop @game-loop-object))
                (println "channel closed: " status)))
    (on-receive channel
                (fn [msg]
                  (let [data (json/read-json msg)
                        command (:command data)]
                    (condp = command
                      "start-local" (do
                                      (println "STARTING LOCAL GAME")
                                      (at/stop-and-reset-pool! my-pool)

                                      (let [game (add-channel-to-game channel :local (:playerOneName (:extra data)) (:playerTwoName (:extra data)))
                                            game (assoc-in game [:game-state :game :state] :running)]
                                        (swap! games conj game))

                                      (when (not= game-loop-object nil)
                                        (reset! game-loop-object (start-game))))
                      "start-online" (do
                                       (println "STARTING ONLINE GAME")
                                       (at/stop-and-reset-pool! my-pool)
                                       (let [game (add-channel-to-game channel :online (:playerOneName (:extra data)))
                                             game (assoc-in game [:game-state :game :state] :waiting-player)]
                                         (swap! games conj game))

                                       (when (not= game-loop-object nil)
                                         (reset! game-loop-object (start-game))))
                      "join-game" (do
                                    (println "JOINING ONLINE GAME")

                                    (let [game-name (-> data :extra :game-name)
                                          game (first (filter #(= game-name (-> % :playerOne :name)) @games))
                                          index (.indexOf ^java.util.List @games game)
                                          game (assoc-in game [:playerTwo :channel] channel)
                                          game (assoc-in game [:playerTwo :name] (-> data :extra :playerTwoName))
                                          game (assoc-in game [:game-state :game :state] :running)]
                                      (swap! games assoc-in [index] game))

                                    (at/stop-and-reset-pool! my-pool)
                                    (when (not= game-loop-object nil)
                                      (reset! game-loop-object (start-game))))
                      "stop" (do (println "STOPPING...")
                                 (at/stop-and-reset-pool! my-pool))
                      "own-right-down" (update-input-state channel :rightDown true)
                      "own-left-down" (update-input-state channel :leftDown true)
                      "own-right-up" (update-input-state channel :rightDown false)
                      "own-left-up" (update-input-state channel :leftDown false)
                      "enemy-right-down"
                      (let [game (get-game @games channel)] (update-local-game-input game channel :playerTwo :rightDown true))
                      "enemy-left-down"
                      (let [game (get-game @games channel)] (update-local-game-input game channel :playerTwo :leftDown true))
                      "enemy-right-up"
                      (let [game (get-game @games channel)] (update-local-game-input game channel :playerTwo :rightDown false))
                      "enemy-left-up"
                      (let [game (get-game @games channel)] (update-local-game-input game channel :playerTwo :leftDown false))))))))

(defroutes all-routes
  (GET "/" [] {:status 200
               :headers {"Content-Type" "application/json; charset=utf-8"
                         "Access-Control-Allow-Origin" "*"}
               :body (json/json-str (get-awailable-games @games))})
  (GET "/ws" [] handler))

(defn in-dev? [& args] true) ;; TODO read a config variable from command line, env, or file?

(def app (site all-routes))

(defn -main [& args]
  (let [handler (if (in-dev? args)
                  (reload/wrap-reload (site #'all-routes)) ;; only reload when dev
                  (site all-routes))]
    (run-server handler {:port 9090})
    (println "SERVER STARTED")))