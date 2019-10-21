(ns server.core-test
  (:require [clojure.test :refer :all]
            [server.core :refer :all]))

#_(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))

(deftest should-collide
  (testing "Ball should collide with bar"
    (is (= (collide?
            {:game {:state :running}
             :playerOne {:x 30, :height 3, :y 2, :width 20, :input {:leftDown false, :rightDown false}}
             :playerTwo {:x 30, :height 3, :y 98, :width 20, :input {:leftDown false, :rightDown false}}
             :ball {:radius 2, :position {:x 50, :y 5}, :step {:x 0, :y -1}}})
           :playerTwo))))

(deftest should-collide-bar
  (testing "DAA"
    (is (= (collide-bar?
            {:radius 2, :position {:x 50, :y 5}, :step {:x 0, :y -1}}
            {:x 30, :height 3, :y 98, :width 20, :input {:leftDown false, :rightDown false}})
           true))))

