(ns server.core-test
  (:require [clojure.test :refer :all]
            [server.logic :refer :all])
  (:use [clojure.pprint]))

(deftest game-loop-tests
  (let [playerOneX 30
        playerTwoX 30
        x {:playerOne {:channel "channel" :name "playerName"}
           :game-state  {:game {:state :running}
                         :bonuses ()
                         :playerOne {:x playerOneX :height 3 :y 98 :width 20 :color "blue" :input {:leftDown false :rightDown true}}
                         :playerTwo {:x playerTwoX :height 3 :y 2 :width 20 :color "red" :input {:leftDown true :rightDown false}}
                         :ball {:radius 2 :position {:x 50 :y 50} :step {:x 0 :y 1}}}}
        n (game-loop x)]
    (is (< playerOneX (-> (:game-state n) :playerOne :x)))
    (is (> playerTwoX (-> (:game-state n) :playerTwo :x)))))

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
    (is (= 60 (:x (:position (:ball (move-ball game-state))))))
    (is (= 60 (:y (:position (:ball (move-ball game-state))))))))

(deftest generate-bonus-tests
  (with-redefs [server.logic/generate-bonus? (fn [] true)]
    (let [game-state  {:bonuses ()}]
      (is 1 (count (generate-bonuses! game-state))))))

#_(deftest object-hit-top-or-bottom-wall?-test
    (is true (object-hit-top-or-bottom-wall? {:name "invisible-ball"
                                              :color "red"
                                           :radius 2
                                           :position {:x 50, :y 2.600000000000117}
                                           :step {:x 0, :y -0.30000000000000004}})))

(deftest bonus-should-collide
  (let [gs {:game {:state :running}
            :playerOne {:x 30
                        :height 3
                        :y 98
                        :width 20
                        :color "blue"
                        :input {:leftDown false, :rightDown false}
                        :bonuses ()}
            :playerTwo {:x 30
                        :height 3
                        :y 2
                        :width 20
                        :color "red"
                        :input {:leftDown false, :rightDown false}
                        :bonuses ()}
            :ball {:radius 2
                   :position {:x 50, :y 40.70000000000012}
                   :step {:x 0, :y 1.2000000000000002}}
            :bonuses '({:name "invisible-ball"
                        :color "red"
                        :radius 2
                        :position {:x 50, :y 2.600000000000117}
                        :step {:x 0, :y -0.30000000000000004}})}]
    (is (= 
         {:player :playerTwo :object {:name "invisible-ball"
                                      :color "red"
                                      :radius 2
                                      :position {:x 50, :y 2.600000000000117}
                                      :step {:x 0, :y -0.30000000000000004}}} 
         (collide? gs (-> gs :bonuses first))))))

(deftest check-bonuses-collision-tests
  (with-redefs [server.logic/now (fn [] "time")]
    (let [game-state {:game {:state :running}
                      :playerOne {:x 30
                                  :height 3
                                  :y 98
                                  :width 20
                                  :color "blue"
                                  :input {:leftDown false, :rightDown false}
                                  :bonuses ()}
                      :playerTwo {:x 30
                                  :height 3
                                  :y 2
                                  :width 20
                                  :color "red"
                                  :input {:leftDown false, :rightDown false}
                                  :bonuses ()}
                      :ball {:radius 2
                             :position {:x 50, :y 40.70000000000012}
                             :step {:x 0, :y 1.2000000000000002}}
                      :bonuses '({:name "invisible-ball"
                                  :color "red"
                                  :radius 2
                                  :position {:x 50, :y 2.600000000000117}
                                  :step {:x 0, :y -0.30000000000000004}})}
          new-state (check-bonuses-collision game-state)]
      (is (empty? (-> new-state :bonuses)))
      (is (= '({:name "invisible-ball" :start-time "time"}) (-> new-state :playerTwo :bonuses))))))


(deftest check-bonuses-collision-tests-2
  (let [game-state {:game {:state :running}
                    :playerOne
                    {:x 36
                     :height 3
                     :y 98
                     :width 20
                     :color "blue"
                     :input {:leftDown false, :rightDown false}
                     :bonuses ()}
                    :playerTwo
                    {:x 30
                     :height 3
                     :y 2
                     :width 20
                     :color "red"
                     :input {:leftDown false, :rightDown false}
                     :bonuses ()}
                    :ball
                    {:radius 2
                     :position {:x 50, :y 53.80000000000051}
                     :step {:x 0, :y 1.6000000000000005}}
                    :bonuses
                    '({:name "double-bar"
                      :color "blue"
                      :radius 2
                      :position {:x 50, :y 50.0}
                      :step {:x 0, :y -2.7755575615628914E-17}}
                     {:name "double-bar"
                      :color "blue"
                      :radius 2
                      :position {:x 50, :y 88.40000000000029}
                      :step {:x 0, :y 0.4}}
                     {:name "double-bar"
                      :color "blue"
                      :radius 2
                      :position {:x 50, :y 95.60000000000039}
                      :step {:x 0, :y 0.4}}
                     {:name "double-bar"
                      :color "blue"
                      :radius 2
                      :position {:x 50, :y 86.09999999999894}
                      :step {:x 0, :y 0.09999999999999998}})}
        new-state (check-bonuses-collision game-state)]
    (is (= 3 (-> new-state :bonuses count)))
    (is (= '("double-bar") (-> new-state :playerOne :bonuses)))))

(deftest should-not-increate-ball-axis
  (let [game-state {:ball {:step  {:y -1}}}]
    (is (= game-state (increase-ball-axis game-state :y))))
  (let [game-state {:ball {:step  {:x -1}}}]
    (is (= game-state (increase-ball-axis game-state :x))))
  (let [game-state {:ball {:step  {:y 1}}}]
    (is (= game-state (increase-ball-axis game-state :y))))
  (let [game-state {:ball {:step  {:x 1}}}]
    (is (= game-state (increase-ball-axis game-state :x)))))