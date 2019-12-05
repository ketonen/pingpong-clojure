(ns pingpong-clojure.helpers
  (:require [re-frame.core :as rf]))

(def conn (js/WebSocket. "ws://127.0.0.1:9090/ws"))
(set! (.-onopen conn) (fn [_] (println "CONNECTION ESTABLISHED")))
(set! (.-onmessage conn)
      (fn [e]
        (let [data (js->clj (.parse js/JSON (.-data e)) :keywordize-keys true)]
          (rf/dispatch [:game-state-updated data]))))

(defn send-to-server
  ([msg data] (.send conn (.stringify js/JSON (js-obj "command" msg "extra" (clj->js data)))))
  ([msg] (.send conn (.stringify js/JSON (js-obj "command" msg)))))