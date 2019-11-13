(ns pingpong-clojure.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(defonce game-state (atom {}))

(defn get-awailable-games [] (http/get "http://127.0.0.1:9090" {:with-credentials? false}))

(enable-console-print!)

(println "This text is printed from src/pingpong-clojure/core.cljs. Go ahead and edit it and see reloading in action.")

;; define your app data so that it doesn't get over-written on reload

(def conn
  (js/WebSocket. "ws://127.0.0.1:9090/ws"))

(defn send-to-server 
  ([msg data] (.send conn (.stringify js/JSON (js-obj "command" msg "extra" (clj->js data)))))
  ([msg] (.send conn (.stringify js/JSON (js-obj "command" msg)))))

(set! (.-onopen conn) (fn [e] (println "CONNECTION ESTABLISHED")))

(set! (.-onmessage conn)
      (fn [e]
        (let [data (js->clj (.parse js/JSON (.-data e)) :keywordize-keys true)]
          (reset! game-state data))))

(defonce input-state (atom {:playerTwo {:leftDown false :rightDown false}
                            :playerOne {:leftDown false :rightDown false}}))


(defn update-input-state! [key value]
  (if (= key "ArrowRight") (send-to-server (if value "own-right-down" "own-right-up")))
  (if (= key "ArrowLeft") (send-to-server (if value "own-left-down" "own-left-up")))
  (if (= key "s") (send-to-server (if value "enemy-right-down" "enemy-right-up")))
  (if (= key "a") (send-to-server (if value "enemy-left-down" "enemy-left-up"))))

(defn keydown-listener [event] (update-input-state! (.-key event) true))
(defn keyup-listener [event] (update-input-state! (.-key event) false))

(.addEventListener js/window "keydown" keydown-listener)
(.addEventListener js/window "keyup" keyup-listener)

(defn bar-location [id] (-> (. js/document (getElementById id))
                          r/dom-node
                          .getBoundingClientRect))

(defn ball-location [] (-> (. js/document (getElementById "ball"))
                         r/dom-node
                         .getBoundingClientRect))

(defn bar [id player]
  [:div {:id id
         :class (clojure.string/join " " (map #(:name %) (:bonuses player)))
         :style {:max-width (str (:width player) "%")
                 :left (str (:x player) "%")
                 :background-color (:color player)}}])

(defn ball [m] [:circle {:style {:fill (:color m)}
                        :id "ball"
                        :cx (str (* js/window.innerWidth (/ (get-in m [:position :x]) 100)) "px")
                        :cy (str (* js/window.innerHeight (/ (get-in m [:position :y]) 100)) "px")
                        :r (str (get-in m [:radius]) "%")}])


(defonce game-type-selection-options-defaults {:game-type :not-selected :playerOneName "" :playerTwoName ""})
(defonce game-type-selection-options (atom {:game-type :not-selected :playerOneName "" :playerTwoName ""}))

(defn game-type-selection []
  (let [game-type (-> @game-type-selection-options :game-type)]
    (cond (= game-type :not-selected)
          [:div {:class "modal-dialog" :role "document"}
           [:div {:class "modal-content"}
            [:div {:class "modal-header"}
             [:h5 {:class "modal-title"} "Lets play a game"]]
            [:div {:class "modal-footer"}
             [:button {:type "button" :class "btn btn-primary"
                       :on-click #(swap! game-type-selection-options assoc :game-type :local)}
              "Local game"]
             [:button {:type "button" :class "btn btn-primary"
                       :on-click #(swap! game-type-selection-options assoc :game-type :online)}
              "Online game"]]]]
          (= game-type :local)
          [:div {:class "modal-dialog" :role "document"}
           [:div {:class "modal-content"}
            [:div {:class "modal-header"}
             [:h5 {:class "modal-title"} "Local game"]]
            [:div {:class "modal-body"}
             [:form {:class "form-inline"}
              [:div {:class "input-group"}
               [:input {:type "text"
                        :class "form-control mr-sm-2"
                        :placeholder "Player 1 Name"
                        :value (-> @game-type-selection-options -> :playerOneName)
                        :on-change #(swap! game-type-selection-options assoc :playerOneName (-> % .-target .-value))}]]
              [:div {:class "input-group"}
               [:input {:type "text"
                        :class "form-control"
                        :placeholder "Player 2 Name"
                        :value (-> @game-type-selection-options -> :playerTwoName)
                        :on-change #(swap! game-type-selection-options assoc :playerTwoName (-> % .-target .-value))}]]]]
            [:div {:class "modal-footer"}
             [:button {:type "button" :class "btn btn-primary"
                       :on-click #(reset! game-type-selection-options game-type-selection-options-defaults)}
              "Cancel"]
             [:button
              {:type "button"
               :disabled (or (let [min-length 3]
                               (< (-> @game-type-selection-options :playerOneName count) min-length)
                               (< (-> @game-type-selection-options :playerTwoName count) min-length)))
               :class "btn btn-primary" 
               :on-click #(send-to-server "start-local" {:playerOneName (:playerOneName @game-type-selection-options)
                                                         :playerTwoName (:playerTwoName @game-type-selection-options)})} "Start"]]]])))

(defn game [] (let [state (-> @game-state :game :state)]
                (cond
                  (= state "running") [:div
                                       [:svg {:style {:width "100%" :height "100%" :position "absolute"}}
                                        [ball (:ball @game-state)]
                                        (let [bonuses (-> @game-state :bonuses)]
                                          (doall (map-indexed (fn [index item]
                                                                ^{:key (str "bl-" index)}
                                                                [ball item]) bonuses)))]
                                       [bar "enemy" (:playerTwo @game-state)]
                                       [bar "own" (:playerOne @game-state)]]
                  (= state "game-over") [:div {:class "modal-dialog" :role "document"}
                                         [:div {:class "modal-content"}
                                          [:div {:class "modal-header"}
                                           [:h5 {:class "modal-title"} "Game over"]]
                                          [:div {:class "modal-body"} "Winner: " (:winner @game-state)]
                                          [:div {:class "modal-footer"}
                                           [:button {:type "button" :class "btn btn-danger"
                                                     :on-click #(do
                                                                  (reset! game-state {}) 
                                                                  (reset! game-type-selection-options game-type-selection-options-defaults))} "Cancel"]
                                           [:button {:type "button" :class "btn btn-primary"
                                                     :on-click #(send-to-server "start-local" {:playerOneName (:playerOneName @game-type-selection-options)
                                                                                               :playerTwoName (:playerTwoName @game-type-selection-options)})}
                                            "Play again"]]]]
                  :else [game-type-selection])))

(defn no-awailable-games [] [:div {:class "modal-dialog" :role "document"}
                             [:div {:class "modal-content"}
                              [:div {:class "modal-header"}
                               [:h5 {:class "modal-title"} "New game"]]
                              [:div {:class "modal-body"} "No awailable games at the moment. Create new game?"]
                              [:div {:class "modal-footer"}
                               [:button {:type "button" :class "btn btn-danger"
                                         :on-click #(reset! game-type-selection-options game-type-selection-options-defaults)} "Cancel"]
                               [:button {:type "button" :class "btn btn-primary"
                                         :on-click #(swap! game-type-selection-options assoc :game-type :online)}
                                "Create new game"]]]])

(defn game-selection []
  (let [playerOneName (atom "")]
    (go (let [response (<! (get-awailable-games))]
          (seq (:body response)) (swap! game-type-selection-options assoc :awailable-games (seq (:body response)))))
    (fn []
      (cond
        (= (-> @game-type-selection-options :online-game-selection) nil)
        [:div {:class "modal-dialog" :role "document"}
         [:div {:class "modal-content"}
          [:div {:class "modal-header"}
           [:h5 {:class "modal-title"} "Choose existing game or create new"]]
          [:div {:class "modal-body"}
           [:div {:class "list-group"}
            (cond (-> @game-type-selection-options :awailable-games)
              (for [game (-> @game-type-selection-options :awailable-games)]
                ^{:key (str "tr-" game)}
                [:a {:href "#" :class "list-group-item list-group-item-action"
                     :on-click #(send-to-server "join-game" {:game-name (:name game)})} (:name game)])
              :else
              ^{:key (str "tr-no-games")}
              [:li {:class "list-group-item list-group-item-action"} "No awailable games"])]]
          [:div {:class "modal-footer"}
           [:button {:type "button" :class "btn btn-light"
                     :on-click #(go (let [response (<! (get-awailable-games))]
                                      (println (:body response))
                                      (seq (:body response)) (swap! game-type-selection-options assoc :awailable-games (seq (:body response)))))}
            "Refresh"]
           [:button {:type "button" :class "btn btn-danger"
                     :on-click #(reset! game-type-selection-options game-type-selection-options-defaults)}
            "Cancel"]
           [:button {:type "button" :class "btn btn-primary"
                     :on-click #(swap! game-type-selection-options assoc :online-game-selection :create-new-game)}
            "Create new game"]]]]
        :else
        [:div {:class "modal-dialog" :role "document"}
         [:div {:class "modal-content"}
          [:div {:class "modal-header"}
           [:h5 {:class "modal-title"} "Create new game"]]
          [:div {:class "modal-body"}
           [:form {:class "form-inline"}
            [:input {:type "text"
                     :class "form-control"
                     :placeholder "Your name"
                     :value @playerOneName
                     :on-change #(reset! playerOneName (-> % .-target .-value))}]]]
          [:div {:class "modal-footer"}
           [:button {:type "button" :class "btn btn-danger"
                     :on-click #(reset! game-type-selection-options game-type-selection-options-defaults)}
            "Cancel"]
           [:button {:type "button" :class "btn btn-primary"
                     :on-click #(do
                                  (send-to-server "start-online" {:playerOneName @playerOneName})
                                  (swap! game-type-selection-options assoc :online-game-selection nil))}
            "Create game"]]]]))))

(defn game-ui []
  (cond (and (empty? @game-state) (= (-> @game-type-selection-options :game-type) :online)) [game-selection]
    :else [game]))

(r/render-component [game-ui] (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your game-state to force rerendering depending on
  ;; your application
  ;; (swap! game-state update-in [:__figwheel_counter] inc)
  )

