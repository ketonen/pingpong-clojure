(ns pingpong-clojure.helpers)

(defn errors? [game-type-selection-options] (let [playerOneName (:playerOneName game-type-selection-options)
                                                  playerTwoName (:playerTwoName game-type-selection-options)
                                                  namesEmpty (and (empty? playerOneName) (empty? playerTwoName))
                                                  namesAreSame (= playerOneName playerTwoName)
                                                  namesTooShort (or (< (count playerOneName) 3)
                                                                    (< (count playerTwoName) 3))]
                                              {:namesEmpty namesEmpty :namesAreSame namesAreSame :namesTooShort namesTooShort}))

(defonce game-type-selection-options-defaults {:game-type :not-selected :playerOneName "" :playerTwoName ""})
(defonce game-type-selection-options (atom {:game-type :not-selected :playerOneName "" :playerTwoName ""}))
