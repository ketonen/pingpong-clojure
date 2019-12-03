(ns pingpong-clojure.components
  (:require
   [reagent.core :as r :refer [atom]]
   [re-frame.core :as rf]
   [pingpong-clojure.helpers :as h]))

(defn game-over [game-state]
  [:div {:class "modal-dialog" :role "document"}
   [:div {:class "modal-content"}
    [:div {:class "modal-header"}
     [:h5 {:class "modal-title"} "Game over"]]
    [:div {:class "modal-body"} "Winner: " (:winner game-state)]
    [:div {:class "modal-footer"}
     [:button {:type "button" :class "btn btn-danger" :on-click #(rf/dispatch [:initialize])} "Cancel"]
     [:button {:type "button" :class "btn btn-primary"
               :on-click #(rf/dispatch [:start-game :local])} "Play again"]]]])

(defn game-type-selection [] [:div {:class "modal-dialog" :role "document"}
                              [:div {:class "modal-content"}
                               [:div {:class "modal-header"}
                                [:h5 {:class "modal-title"} "Lets play a game"]]
                               [:div {:class "modal-footer"}
                                [:button {:type "button" :class "btn btn-primary"
                                          :on-click #(rf/dispatch [:game-type-selected :local])}
                                 "Local game"]
                                [:button {:type "button" :class "btn btn-primary"
                                          :on-click #(rf/dispatch [:game-type-selected :online])}
                                 "Online game"]]]])

(defn validation-messages [errors] [:div
                                    [:ul
                                     (when (and (not (:namesEmpty errors)) (:namesAreSame errors))
                                       [:li "Players names can not be same"])
                                     (when (:namesTooShort errors)
                                       [:li "Name must be at least 3 char long"])]])

(defn local-game-menu []
  (let [errors (h/errors? @(rf/subscribe [:options]))]
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
                  :value (:playerOneName @(rf/subscribe [:options]))
                  :on-change #(rf/dispatch [:player-name-updated [:playerOneName (-> % .-target .-value)]])}]]
        [:div {:class "input-group"}
         [:input {:type "text"
                  :class "form-control"
                  :placeholder "Player 2 Name"
                  :value (:playerTwoName @(rf/subscribe [:options]))
                  :on-change #(rf/dispatch [:player-name-updated [:playerTwoName (-> % .-target .-value)]])}]]]
       [validation-messages errors]]
      [:div {:class "modal-footer"}
       [:button {:type "button" :class "btn btn-primary"
                 :on-click #(rf/dispatch [:game-type-selected :not-selected])} "Cancel"]
       [:button
        {:type "button"
         :disabled (some true? (vals errors))
         :class "btn btn-primary"
         :on-click #(rf/dispatch [:start-game :local])} "Start"]]]]))

(defn join-game [] [:div {:class "modal-dialog" :role "document"}
                    [:div {:class "modal-content"}
                     [:div {:class "modal-header"}
                      [:h5 {:class "modal-title"} "Give your name"]]
                     [:div {:class "modal-body"}
                      [:input {:type "text"
                               :class "form-control"
                               :placeholder "Your name"
                               :value (:playerTwoName @(rf/subscribe [:options]))
                               :on-change #(rf/dispatch [:player-name-updated [:playerTwoName (-> % .-target .-value)]])}]]
                     [:div {:class "modal-footer"}
                      [:button {:type "button" :class "btn btn-primary"
                                :on-click #(rf/dispatch [:start-game :join-game])}
                       "Join game"]]]])

(defn online-game-listing []
  [:div {:class "modal-dialog" :role "document"}
   [:div {:class "modal-content"}
    [:div {:class "modal-header"}
     [:h5 {:class "modal-title"} "Choose existing game or create new"]]
    [:div {:class "modal-body"}
     [:div {:class "list-group"}
      (let [games (:awailable-games @(rf/subscribe [:options]))]
        (cond (empty? games)
              ^{:key (str "tr-no-games")}
              [:li {:class "list-group-item list-group-item-action"} "No awailable games"]
              :else (for [game games]
                      ^{:key (str "tr-" game)}
                      [:a {:href "#" :class "list-group-item list-group-item-action"
                           :on-click #(rf/dispatch [:select-game (:name game)])} (:name game)])))]]
    [:div {:class "modal-footer"}
     [:button {:type "button" :class "btn btn-light" :on-click #(rf/dispatch [:update-awailable-games])} "Refresh"]
     [:button {:type "button" :class "btn btn-danger"
               :on-click #(rf/dispatch [:game-type-selected :not-selected])} "Cancel"]
     [:button {:type "button" :class "btn btn-primary"
               :on-click #(rf/dispatch [:game-type-selected :new-online-game])} "Create new game"]]]])

(defn create-new-online-game []
  [:div {:class "modal-dialog" :role "document"}
   [:div {:class "modal-content"}
    [:div {:class "modal-header"}
     [:h5 {:class "modal-title"} "Create new game"]]
    [:div {:class "modal-body"}
     [:form {:class "form-inline"}
      [:input {:type "text"
               :class "form-control"
               :placeholder "Your name"
               :value (:playerOneName @(rf/subscribe [:options]))
               :on-change #(rf/dispatch [:player-name-updated [:playerOneName (-> % .-target .-value)]])}]]]
    [:div {:class "modal-footer"}
     [:button {:type "button" :class "btn btn-danger"
               :on-click #(reset! h/game-type-selection-options h/game-type-selection-options-defaults)} "Cancel"]
     [:button {:type "button" :class "btn btn-primary"
               :on-click #(rf/dispatch [:start-game :online])} "Create game"]]]])

(defn no-awailable-games []
  [:div {:class "modal-dialog" :role "document"}
   [:div {:class "modal-content"}
    [:div {:class "modal-header"}
     [:h5 {:class "modal-title"} "New game"]]
    [:div {:class "modal-body"} "No awailable games at the moment. Create new game?"]
    [:div {:class "modal-footer"}
     [:button {:type "button" :class "btn btn-danger"
               :on-click #(reset! h/game-type-selection-options h/game-type-selection-options-defaults)} "Cancel"]
     [:button {:type "button" :class "btn btn-primary"
               :on-click #(rf/dispatch [:new-online-game true])} "Create new game"]]]])

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

(defn game-view [game-state] [:div
                              [:svg {:style {:width "100%" :height "100%" :position "absolute"}}
                               (when (-> game-state :ball :visible) [ball (:ball game-state)])
                               (let [bonuses (:bonuses game-state)]
                                 (doall (map-indexed (fn [index item]
                                                       ^{:key (str "bl-" index)}
                                                       [ball item]) bonuses)))]
                              [bar "enemy" (:playerTwo game-state)]
                              [bar "own" (:playerOne game-state)]])

(defn game [game-state]
  (let [state (-> game-state :game :state)]
    (condp = state
      "running" [game-view game-state]
      "game-over" [game-over game-state]
      [game-type-selection])))

(defn menu []
  (let [options @(rf/subscribe [:options])
        game-type (:game-type options)
        selected-game (:selected-game options)]
    (if selected-game
      [join-game]
      (condp = game-type
        :not-selected [game-type-selection]
        :local [local-game-menu]
        :online [online-game-listing]
        :new-online-game [create-new-online-game]))))