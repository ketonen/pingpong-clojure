(ns pingpong-clojure.helpers
  (:require [cljs-http.client :as http]))


(defn get-awailable-games [] (http/get "http://127.0.0.1:9090" {:with-credentials? false}))

(defn send-to-server
  ([conn msg data] (.send conn (.stringify js/JSON (js-obj "command" msg "extra" (clj->js data)))))
  ([conn msg] (.send conn (.stringify js/JSON (js-obj "command" msg)))))

(defn errors? [game-type-selection-options] (let [playerOneName (-> @game-type-selection-options :playerOneName)
                       playerTwoName (-> @game-type-selection-options :playerTwoName)
                       namesEmpty (and (empty? playerOneName) (empty? playerTwoName))
                       namesAreSame (= playerOneName playerTwoName)
                       namesTooShort (or (< (-> playerOneName count) 3)
                                         (< (-> playerTwoName count) 3))]
                   {:namesEmpty namesEmpty :namesAreSame namesAreSame :namesTooShort namesTooShort}))