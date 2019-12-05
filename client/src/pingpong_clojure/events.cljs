(ns pingpong-clojure.events
  (:require [ajax.core :as ajax]
            [re-frame.core :as rf]
            [pingpong-clojure.helpers :as h]))

(rf/reg-event-db              ;; sets up initial application state
 :initialize                 ;; usage:  (dispatch [:initialize])
 (fn [_ _]                   ;; the two parameters are not important here, so use _
   {:options {:game-type :not-selected :playerOneName "" :playerTwoName ""}
    :game {}}))    ;; so the application state will initially be a map with two keys

(rf/reg-event-db
 :game-state-updated
 (fn [db [_ state]]
   (assoc-in db [:game] state)))

(rf/reg-event-db
 :game-type-selected
 (fn [db [_ game-type]]
   (assoc-in db [:options :game-type] game-type)))

(rf/reg-event-db
 :player-name-updated
 (fn [db [_ [player name]]]
   (assoc-in db [:options player] name)))

(rf/reg-event-db
 :success-http-result
 (fn [db [_ result]]
   (assoc-in db [:options :awailable-games] result)))

(rf/reg-event-db
 :select-game
 (fn [db [_ result]]
   (println result)
   (assoc-in db [:options :selected-game] result)))

(rf/reg-event-db
 :bad-http-result
 (fn [db [_ result]]
   (println result)
   db))


(rf/reg-event-fx
 :update-awailable-games
 (fn [_]
   {:http-xhrio {:method          :get
                 :uri             "http://127.0.0.1:9090"
                 :response-format (ajax/json-response-format {:keywords? true})  ;; IMPORTANT!: You must provide this.
                 :on-success      [:success-http-result]
                 :on-failure      [:bad-http-result]}}))

(rf/reg-event-fx
 :start-game
 (fn [cofx [_ game-type]]
   (let [db (:db cofx)]
     (condp = game-type
       :join-game (h/send-to-server "join-game" {:game-name (-> db :options :selected-game)
                                                 :playerTwoName (-> db :options :playerTwoName)})
       :local (h/send-to-server "start-local" {:playerOneName (-> db :options :playerOneName)
                                               :playerTwoName (-> db :options :playerTwoName)})
       :online (do (h/send-to-server "start-online" {:playerOneName (-> db :options :playerOneName)})
                   (rf/dispatch [:game-type-selected :online]))))))

(rf/reg-event-db
 :new-online-game
 (fn [db [_ b]]
   (assoc-in db [:options :new-online-game] b)))

(rf/reg-event-db
 :awailable-games
 (fn [db [_ games]]
   (assoc-in db [:options :awailable-games] games)))
