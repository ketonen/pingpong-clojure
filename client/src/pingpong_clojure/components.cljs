(ns pingpong-clojure.components
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [reagent.core :as r :refer [atom]]
   [pingpong-clojure.helpers :as h]
   [cljs.core.async :refer [<!]]))

(defn game-over [conn game-state game-type-selection-options game-type-selection-options-defaults]
  [:div {:class "modal-dialog" :role "document"}
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
               :on-click #(h/send-to-server conn "start-local" {:playerOneName (:playerOneName @game-type-selection-options)
                                                                :playerTwoName (:playerTwoName @game-type-selection-options)})}
      "Play again"]]]])

(defn game-type-selection [conn game-type-selection-options game-type-selection-options-defaults]
  (let [game-type (:game-type (deref game-type-selection-options))]
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
                        :value (:playerOneName (deref game-type-selection-options))
                        :on-change #(swap! game-type-selection-options assoc :playerOneName (-> % .-target .-value))}]]
              [:div {:class "input-group"}
               [:input {:type "text"
                        :class "form-control"
                        :placeholder "Player 2 Name"
                        :value (:playerTwoName (deref game-type-selection-options))
                        :on-change #(swap! game-type-selection-options assoc :playerTwoName (-> % .-target .-value))}]]]
             [:div
              (let [errors (h/errors? game-type-selection-options)]
                [:ul
                 (when (and (not (:namesEmpty errors)) (:namesAreSame errors))
                   [:li "Players names can not be same"])
                 (when (:namesTooShort errors)
                   [:li "Name must be at least 3 char long"])])]]
            [:div {:class "modal-footer"}
             [:button {:type "button" :class "btn btn-primary"
                       :on-click #(reset! game-type-selection-options game-type-selection-options-defaults)}
              "Cancel"]
             [:button
              {:type "button"
               :disabled (some true? (vals (h/errors? game-type-selection-options)))
               :class "btn btn-primary"
               :on-click #(h/send-to-server conn "start-local" {:playerOneName (:playerOneName @game-type-selection-options)
                                                                :playerTwoName (:playerTwoName @game-type-selection-options)})} "Start"]]]])))

(defn join-game [conn selectedGame joining-player-name] [:div {:class "modal-dialog" :role "document"}
                                                         [:div {:class "modal-content"}
                                                          [:div {:class "modal-header"}
                                                           [:h5 {:class "modal-title"} "Give your name"]]
                                                          [:div {:class "modal-body"}
                                                           [:input {:type "text"
                                                                    :class "form-control"
                                                                    :placeholder "Your name"
                                                                    :value @joining-player-name
                                                                    :on-change #(reset! joining-player-name (-> % .-target .-value))}]]
                                                          [:div {:class "modal-footer"}
                                                           [:button {:type "button" :class "btn btn-primary"
                                                                     :on-click #(h/send-to-server conn "join-game" {:game-name @selectedGame
                                                                                                                    :playerTwoName @joining-player-name})}
                                                            "Join game"]]]])

(defn online-game-listing [selectedGame game-type-selection-options game-type-selection-options-defaults]
  [:div {:class "modal-dialog" :role "document"}
   [:div {:class "modal-content"}
    [:div {:class "modal-header"}
     [:h5 {:class "modal-title"} "Choose existing game or create new"]]
    [:div {:class "modal-body"}
     [:div {:class "list-group"}
      (cond (:awailable-games (deref game-type-selection-options))
            (for [game (:awailable-games (deref game-type-selection-options))]
              ^{:key (str "tr-" game)}
              [:a {:href "#" :class "list-group-item list-group-item-action"
                   :on-click #(reset! selectedGame (:name game))} (:name game)])
            :else
            ^{:key (str "tr-no-games")}
            [:li {:class "list-group-item list-group-item-action"} "No awailable games"])]]
    [:div {:class "modal-footer"}
     [:button {:type "button" :class "btn btn-light"
               :on-click #(go (let [response (<! (h/get-awailable-games))]
                                (println (:body response))
                                (seq (:body response)) (swap! game-type-selection-options assoc :awailable-games (seq (:body response)))))}
      "Refresh"]
     [:button {:type "button" :class "btn btn-danger"
               :on-click #(reset! game-type-selection-options game-type-selection-options-defaults)}
      "Cancel"]
     [:button {:type "button" :class "btn btn-primary"
               :on-click #(swap! game-type-selection-options assoc :online-game-selection :create-new-game)}
      "Create new game"]]]])

(defn create-new-online-game [conn playerOneName game-type-selection-options game-type-selection-options-defaults]
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
               :on-click #(reset! game-type-selection-options game-type-selection-options-defaults)} "Cancel"]
     [:button {:type "button" :class "btn btn-primary"
               :on-click #(do (h/send-to-server conn "start-online" {:playerOneName @playerOneName})
                              (swap! game-type-selection-options assoc :online-game-selection nil))} "Create game"]]]])

(defn game-selection [conn game-type-selection-options game-type-selection-options-defaults]
  (let [playerOneName (atom "") selectedGame (atom "") joining-player-name (atom "")]
    (go (let [response (<! (h/get-awailable-games))]
          (seq (:body response)) (swap! game-type-selection-options assoc :awailable-games (seq (:body response)))))
    (fn []
      (cond (nil? (:online-game-selection (deref game-type-selection-options)))
            (cond (seq @selectedGame) [join-game conn selectedGame joining-player-name]
                  :else [online-game-listing selectedGame game-type-selection-options game-type-selection-options-defaults])
            :else [create-new-online-game conn playerOneName game-type-selection-options game-type-selection-options-defaults]))))


(defn no-awailable-games [game-type-selection-options game-type-selection-options-defaults]
  [:div {:class "modal-dialog" :role "document"}
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