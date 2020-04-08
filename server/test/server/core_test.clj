(ns server.core-test
  (:require [clojure.test :as test]
            [server.core :as core]
            [clojure.pprint]))

(test/deftest should-send-state-to-both-users
  (let [counter (atom 0)]
    (with-redefs [core/send-changes-to-channel (fn [_ _] (swap! counter inc))]
      (let [game {:playerOne {:channel "channel"}
               :playerTwo {:channel "channel"}
               :game-state {}}]
        (core/send-game-state-to-players game)))
    (test/is (= 2 @counter))))