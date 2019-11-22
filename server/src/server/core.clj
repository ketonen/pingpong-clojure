(ns server.core
  (:require [compojure.core :refer :all]
            [cheshire.core :refer :all]
            [server.logic :refer [game-loop initial-game-state]]
            [clojure.data.json :as json])
  (:use [org.httpkit.server]
        [overtone.at-at]))


(def games (atom []))

(defn value-writer [key value]
  (if (= key :start-time)
    (str value)
    value))

(defn get-game [games channel]
  (first (filter
          #(or (= (:channel (:playerOne %)) channel) (= (:channel (:playerTwo %)) channel))
          games)))

(defn send-changes-to-clients [game-state channel]
  (send! channel (json/write-str game-state :value-fn value-writer)))

(def game-loop-object (atom nil))

(defn get-awailable-games [] (vec (map #(select-keys % [:name])
                                       (->> (filter (comp nil? :playerTwo) @games)
                                            (map #(get-in % [:playerOne]))))))


(defn add-channel-to-game
  ([channel gameType playerName]
   {:playerOne {:channel channel :name playerName} :game-state initial-game-state})
  ([channel gameType playerOneName playerTwoName]
   {:playerOne {:channel channel :name playerOneName}
    :playerTwo {:channel channel :name playerTwoName}
    :game-type gameType
    :game-state initial-game-state}))

(defn remove-channel-from-games [games channel]
  (vec (remove #(or (=  (:channel (:playerOne %)) channel)
                    (=  (:channel (:playerTwo %)) channel)) games)))

(defn update-game-state! [channel d]
  (let [x (map #(if (or (= (:channel (:playerOne %)) channel) (= (:channel (:playerTwo %)) channel)) d %) @games)]
    (reset! games x)))

(def my-pool (mk-pool))

(defn start-game []
  (every 30 #(do
               (doseq [x (remove (fn [x] (= (-> x :game-state :game :state) :waiting-player)) @games)]
                 (let [gs (:game-state x)]
                   (send-changes-to-clients gs (-> x :playerOne :channel))
                   (if-let [c (-> x :playerTwo :channel)]
                     (send-changes-to-clients gs c))))
               (reset! games (remove (fn [g] (= :game-over (-> g :game-state :game :state))) @games))
               (let [g (vec (doall (pmap game-loop @games)))]
                 (reset! games g)))
         my-pool))

(defn update-input-state [channel event state] (let [game (get-game @games channel)
                                                     player (if (= channel (-> game :playerOne :channel)) :playerOne :playerTwo)]
                                                 (update-game-state! channel (assoc-in game [:game-state player :input event] state))))

(defn local-game? [game] (= :local (-> game :game-type)))

(defn handler [request]
  (with-channel request channel
    (on-close channel (fn [status]
                        (reset! games (remove-channel-from-games @games channel))
                        (when (empty? @games)
                          (stop @game-loop-object))
                        (println "channel closed: " status)))
    (on-receive channel (fn [msg]
                          (let [data (json/read-json msg)
                                command (:command data)]
                            (cond
                              (= command "start-local") (do
                                                          (println "STARTING LOCAL GAME")
                                                          (stop-and-reset-pool! my-pool)

                                                          (let [game (add-channel-to-game channel :local (:playerOneName (:extra data)) (:playerTwoName (:extra data)))
                                                                game (assoc-in game [:game-state :game :state] :running)]
                                                            (swap! games conj game))

                                                          (when (not= game-loop-object nil)
                                                            (reset! game-loop-object (start-game))))
                              (= command "start-online") (do
                                                           (println "STARTING ONLINE GAME")
                                                           (stop-and-reset-pool! my-pool)
                                                           (let [game (add-channel-to-game channel :online (:playerOneName (:extra data)))
                                                                 game (assoc-in game [:game-state :game :state] :waiting-player)]
                                                             (swap! games conj game))

                                                           (when (not= game-loop-object nil)
                                                             (reset! game-loop-object (start-game))))
                              (= command "join-game") (do
                                                        (println "JOINING ONLINE GAME")
                                                        (let [game-name (-> data :extra :game-name)
                                                              game (first (filter #(= game-name (-> % :playerOne :name)) @games))
                                                              index (.indexOf @games game)
                                                              game (assoc-in game [:playerTwo :channel] channel)
                                                              game (assoc-in game [:game-state :game :state] :running)]
                                                          (swap! games assoc-in [index] game))
                                                        (stop-and-reset-pool! my-pool)
                                                        (when (not= game-loop-object nil)
                                                          (reset! game-loop-object (start-game))))
                              (= command "stop") (do (println "STOPPING...")
                                                     (stop-and-reset-pool! my-pool))
                              (= command "own-right-down") (update-input-state channel :rightDown true)
                              (= command "own-left-down") (update-input-state channel :leftDown true)
                              (= command "own-right-up") (update-input-state channel :rightDown false)
                              (= command "own-left-up") (update-input-state channel :leftDown false)
                              (= command "enemy-right-down")
                              (let [game (get-game @games channel)]
                                (when (local-game? game)
                                  (update-game-state! channel (assoc-in game [:game-state :playerTwo :input :rightDown] true))))
                              (= command "enemy-left-down")
                              (let [game (get-game @games channel)]
                                (when (local-game? game)
                                  (update-game-state! channel (assoc-in game [:game-state :playerTwo :input :leftDown] true))))
                              (= command "enemy-right-up")
                              (let [game (get-game @games channel)]
                                (when (local-game? game)
                                  (update-game-state! channel (assoc-in game [:game-state :playerTwo :input :rightDown] false))))
                              (= command "enemy-left-up")
                              (let [game (get-game @games channel)]
                                (when (local-game? game)
                                  (update-game-state! channel (assoc-in game [:game-state :playerTwo :input :leftDown] false))))))))))

(defroutes all-routes
  (GET "/" [] {:status 200
               :headers {"Content-Type" "application/json; charset=utf-8"
                         "Access-Control-Allow-Origin" "*"}
               :body (json/json-str (get-awailable-games))})
  (GET "/ws" [] handler))

(defn -main [& args]
  (run-server all-routes {:port 9090})
  (println "SERVER STARTED"))