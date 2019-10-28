(ns server.core-test
  (:require [clojure.test :refer :all]
            [server.logic :refer :all]))

(deftest game-loop-tests
  (let [playerOneX 30
        playerTwoX 30
        x {:playerOne {:channel "channel" :name "playerName"}
           :game-state  {:game {:state :running}
                         :playerOne {:x playerOneX :height 3 :y 98 :width 20 :color "blue" :input {:leftDown false :rightDown true}}
                         :playerTwo {:x playerTwoX :height 3 :y 2 :width 20 :color "red" :input {:leftDown true :rightDown false}}
                         :ball {:radius 2 :position {:x 50 :y 50} :step {:x 0 :y 1}}}}
        n (game-loop x)]
    (is (< playerOneX (:x (:playerOne (:game-state n)))))
    (is (> playerTwoX (:x (:playerTwo (:game-state n)))))))

(deftest check-momentum-tests
  (let [game-state  {:game {:state :running}
                     :playerOne {:x 30 :height 3 :y 98 :width 20 :color "blue" :input {:leftDown false :rightDown true}}
                     :playerTwo {:x 30 :height 3 :y 2 :width 20 :color "red" :input {:leftDown true :rightDown false}}
                     :ball {:radius 2 :position {:x 50 :y 50} :step {:x 0 :y 1}}}]
    (is (< 0 (:x (:step (:ball (check-momentum :playerOne game-state))))))))

(deftest move-ball-tests
  (let [game-state  {:game {:state :running}
                     :playerOne {:x 30 :height 3 :y 98 :width 20 :color "blue" :input {:leftDown false :rightDown true}}
                     :playerTwo {:x 30 :height 3 :y 2 :width 20 :color "red" :input {:leftDown true :rightDown false}}
                     :ball {:radius 2 :position {:x 50 :y 50} :step {:x 10 :y 10}}}]
    (is (= 60 (:x (:position (:ball (move-ball! game-state))))))
    (is (= 60 (:y (:position (:ball (move-ball! game-state))))))))
