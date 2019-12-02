(ns pingpong-clojure.helpers
  (:require [cljs-http.client :as http]))


(defn get-awailable-games [] (http/get "http://127.0.0.1:9090" {:with-credentials? false}))

(defn send-to-server
  ([conn msg data] (.send conn (.stringify js/JSON (js-obj "command" msg "extra" (clj->js data)))))
  ([conn msg] (.send conn (.stringify js/JSON (js-obj "command" msg)))))

(defn errors? [game-type-selection-options] (let [playerOneName (:playerOneName @game-type-selection-options)
                       playerTwoName (:playerTwoName @game-type-selection-options)
                       namesEmpty (and (empty? playerOneName) (empty? playerTwoName))
                       namesAreSame (= playerOneName playerTwoName)
                       namesTooShort (or (< (count playerOneName) 3)
                                         (< (count playerTwoName) 3))]
                   {:namesEmpty namesEmpty :namesAreSame namesAreSame :namesTooShort namesTooShort}))