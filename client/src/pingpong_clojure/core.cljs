(ns pingpong-clojure.core
  (:require [reagent.core :as r :refer [atom]]
            [re-frame.core :as rf]
            [day8.re-frame.http-fx]
            [devtools.core :as devtools]
            [pingpong-clojure.events] ;; These two are only required to make the compiler
            [pingpong-clojure.subs]   ;; load them (see docs/App-Structure.md)
            [pingpong-clojure.components :as c]
            [pingpong-clojure.helpers :as h]))

(devtools/install!)       ;; https://github.com/binaryage/cljs-devtools
(enable-console-print!)

;; -- Domino 2 - Event Handlers -----------------------------------------------

(println "This text is printed from src/pingpong-clojure/core.cljs. Go ahead and edit it and see reloading in action.")

(defonce input-state (atom {:playerTwo {:leftDown false :rightDown false}
                            :playerOne {:leftDown false :rightDown false}}))

(defn update-input-state! [key value]
  (when (= key "ArrowRight") (h/send-to-server (if value "own-right-down" "own-right-up")))
  (when (= key "ArrowLeft") (h/send-to-server (if value "own-left-down" "own-left-up")))
  (when (= key "s") (h/send-to-server (if value "enemy-right-down" "enemy-right-up")))
  (when (= key "a") (h/send-to-server (if value "enemy-left-down" "enemy-left-up"))))

(defn keydown-listener [event] (update-input-state! (.-key event) true))
(defn keyup-listener [event] (update-input-state! (.-key event) false))

(.addEventListener js/window "keydown" keydown-listener)
(.addEventListener js/window "keyup" keyup-listener)

(defn game-ui []
  (let [game @(rf/subscribe [:game])]
    (if (seq game)
      [c/game game]
      [c/menu])))

(defn render
  []
  (r/render [game-ui]
            (.getElementById js/document "app")))

(defn ^:export main
  []
  (rf/dispatch-sync [:initialize])
  (render)) ;; put a value into application state

