(ns pingpong-clojure.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 :options
 (fn [db _]
   (:options db)))

(rf/reg-sub
 :errors
 :<- [:options]
 (fn [options _]
   (let [playerOneName (:playerOneName options)
         playerTwoName (:playerTwoName options)
         namesEmpty (and (empty? playerOneName) (empty? playerTwoName))
         namesAreSame (= playerOneName playerTwoName)
         namesTooShort (or (< (count playerOneName) 3)
                           (< (count playerTwoName) 3))]
     {:namesEmpty namesEmpty :namesAreSame namesAreSame :namesTooShort namesTooShort})))

(rf/reg-sub
 :game
 (fn [db _]
   (:game db)))