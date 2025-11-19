(ns combatsys.renderer.session-browser
  "Session browser UI for LOD 6 Multi-Session Analytics.

   Philosophy (Brett Victor):
   - Make sessions visible and browsable
   - Immediate filter/sort feedback
   - Show aggregate patterns across sessions"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [combatsys.renderer.state :as state]))

;; ============================================================================
;; UTILITY FUNCTIONS
;; ============================================================================

(defn format-date
  "Format ISO date string to readable format"
  [iso-string]
  (when iso-string
    (let [date (js/Date. iso-string)]
      (.toLocaleDateString date "en-US"
                           #js {:month "short"
                                :day "numeric"
                                :year "numeric"
                                :hour "numeric"
                                :minute "2-digit"}))))

(defn format-duration
  "Format milliseconds to human-readable duration"
  [ms]
  (when ms
    (let [seconds (/ ms 1000)]
      (cond
        (< seconds 60) (str (.toFixed seconds 0) "s")
        (< seconds 3600) (str (.toFixed (/ seconds 60) 1) "m")
        :else (str (.toFixed (/ seconds 3600) 1) "h")))))

(defn format-number
  "Format number with 1 decimal place"
  [n]
  (if (number? n)
    (.toFixed n 1)
    "N/A"))

;; ============================================================================
;; COMPONENTS
;; ============================================================================

(defn browser-header
  "Header with session count and aggregate stats"
  []
  (let [session-count @(rf/subscribe [::state/session-browser/total-count])
        aggregate-stats @(rf/subscribe [::state/session-browser/aggregate-stats])]
    [:div {:style {:padding "20px"
                   :background-color "#1a1a1a"
                   :color "white"
                   :border-bottom "1px solid #333"}}
     [:h1 {:style {:margin "0 0 15px 0"
                   :font-size "24px"}}
      "Your Sessions " [:span {:style {:color "#666"
                                       :font-size "18px"}}
                        "(" session-count ")"]]

     ;; Aggregate stats row
     [:div {:style {:display "flex"
                    :gap "30px"
                    :font-size "14px"}}
      [:div {:style {:display "flex"
                     :flex-direction "column"}}
       [:span {:style {:color "#888"
                       :font-size "12px"}}
        "Total Training Time"]
       [:span {:style {:color "#00FF00"
                       :font-size "18px"
                       :font-weight "600"}}
        (if aggregate-stats
          (str (.toFixed (:total-duration-hours aggregate-stats) 1) " hours")
          "0 hours")]]

      [:div {:style {:display "flex"
                     :flex-direction "column"}}
       [:span {:style {:color "#888"
                       :font-size "12px"}}
        "Avg Session"]
       [:span {:style {:color "#00FF00"
                       :font-size "18px"
                       :font-weight "600"}}
        (if aggregate-stats
          (format-duration (:avg-session-duration-ms aggregate-stats))
          "—")]]

      [:div {:style {:display "flex"
                     :flex-direction "column"}}
       [:span {:style {:color "#888"
                       :font-size "12px"}}
        "Avg Breathing"]
       [:span {:style {:color "#00FF00"
                       :font-size "18px"
                       :font-weight "600"}}
        (if (and aggregate-stats (> (:avg-breathing-rate aggregate-stats) 0))
          (str (format-number (:avg-breathing-rate aggregate-stats)) " bpm")
          "—")]]

      [:div {:style {:display "flex"
                     :flex-direction "column"}}
       [:span {:style {:color "#888"
                       :font-size "12px"}}
        "Avg Posture"]
       [:span {:style {:color "#00FF00"
                       :font-size "18px"
                       :font-weight "600"}}
        (if (and aggregate-stats (> (:avg-posture-score aggregate-stats) 0))
          (str (.toFixed (* 100 (:avg-posture-score aggregate-stats)) 0) "%")
          "—")]]]]))

(defn filter-controls
  "Filter and sort controls"
  []
  (let [search-text @(rf/subscribe [::state/session-browser/search-text])
        sort-by @(rf/subscribe [::state/session-browser/sort-by])
        date-filter @(rf/subscribe [::state/session-browser/date-filter])]
    [:div {:style {:padding "15px"
                   :background-color "#f5f5f5"
                   :border-bottom "1px solid #ddd"
                   :display "flex"
                   :gap "15px"
                   :align-items "center"
                   :flex-wrap "wrap"}}

     ;; Search box
     [:div {:style {:flex "1 1 250px"}}
      [:input {:type "text"
               :placeholder "Search sessions..."
               :value search-text
               :on-change #(rf/dispatch [::state/session-browser/set-search
                                         (-> % .-target .-value)])
               :style {:width "100%"
                       :padding "8px 12px"
                       :font-size "14px"
                       :border "1px solid #ccc"
                       :border-radius "4px"
                       :box-sizing "border-box"}}]]

     ;; Sort dropdown
     [:div {:style {:display "flex"
                    :align-items "center"
                    :gap "8px"}}
      [:label {:style {:font-size "14px"
                       :color "#666"}}
       "Sort by:"]
      [:select {:value (name sort-by)
                :on-change #(rf/dispatch [::state/session-browser/set-sort
                                          (keyword (-> % .-target .-value))])
                :style {:padding "8px 12px"
                        :font-size "14px"
                        :border "1px solid #ccc"
                        :border-radius "4px"
                        :background-color "white"
                        :cursor "pointer"}}
       [:option {:value "date"} "Date (Newest First)"]
       [:option {:value "duration"} "Duration"]
       [:option {:value "name"} "Name"]]]

     ;; Date filter dropdown
     [:div {:style {:display "flex"
                    :align-items "center"
                    :gap "8px"}}
      [:label {:style {:font-size "14px"
                       :color "#666"}}
       "Date Range:"]
      [:select {:value (name date-filter)
                :on-change #(rf/dispatch [::state/session-browser/set-date-filter
                                          (keyword (-> % .-target .-value))])
                :style {:padding "8px 12px"
                        :font-size "14px"
                        :border "1px solid #ccc"
                        :border-radius "4px"
                        :background-color "white"
                        :cursor "pointer"}}
       [:option {:value "all"} "All Time"]
       [:option {:value "last-7-days"} "Last 7 Days"]
       [:option {:value "last-30-days"} "Last 30 Days"]
       [:option {:value "last-90-days"} "Last 90 Days"]]]]))

(defn session-card
  "Individual session card"
  [session selected?]
  (let [session-id (:session/id session)]
    [:div {:class (str "session-card" (when selected? " selected"))
           :on-click #(rf/dispatch [::state/session-browser/toggle-selection session-id])
           :style {:padding "15px"
                   :margin "8px 0"
                   :border (if selected?
                            "2px solid #007AFF"
                            "1px solid #ddd")
                   :border-radius "8px"
                   :background-color (if selected?
                                      "#E3F2FD"
                                      "white")
                   :cursor "pointer"
                   :transition "all 0.2s ease"
                   :box-shadow (if selected?
                                "0 2px 8px rgba(0,122,255,0.2)"
                                "0 1px 3px rgba(0,0,0,0.1)")}}

     ;; Header row
     [:div {:style {:display "flex"
                    :align-items "center"
                    :margin-bottom "10px"}}
      ;; Checkbox
      [:input {:type "checkbox"
               :checked selected?
               :on-click #(.stopPropagation %)
               :style {:width "18px"
                       :height "18px"
                       :margin-right "10px"
                       :cursor "pointer"}}]

      ;; Session name
      [:div {:style {:flex "1"
                     :font-size "16px"
                     :font-weight "600"
                     :color "#333"}}
       (:session/name session "Untitled Session")]

      ;; Date
      [:div {:style {:font-size "12px"
                     :color "#888"}}
       (format-date (:session/created-at session))]]

     ;; Metrics row
     [:div {:style {:display "flex"
                    :gap "20px"
                    :margin "10px 0"
                    :padding "10px 0 10px 28px"
                    :border-top "1px solid #eee"}}
      [:div {:style {:display "flex"
                     :flex-direction "column"}}
       [:span {:style {:font-size "11px"
                       :color "#888"
                       :text-transform "uppercase"}}
        "Duration"]
       [:span {:style {:font-size "14px"
                       :color "#333"
                       :font-weight "500"}}
        (format-duration (:session/duration-ms session))]]

      (when-let [breathing-rate (get-in session [:session/summary-stats :avg-breathing-rate])]
        [:div {:style {:display "flex"
                       :flex-direction "column"}}
         [:span {:style {:font-size "11px"
                         :color "#888"
                         :text-transform "uppercase"}}
          "Breathing"]
         [:span {:style {:font-size "14px"
                         :color "#4CAF50"
                         :font-weight "500"}}
          (str (format-number breathing-rate) " bpm")]])

      (when-let [posture-score (get-in session [:session/summary-stats :avg-posture-score])]
        [:div {:style {:display "flex"
                       :flex-direction "column"}}
         [:span {:style {:font-size "11px"
                         :color "#888"
                         :text-transform "uppercase"}}
          "Posture"]
         [:span {:style {:font-size "14px"
                         :color "#2196F3"
                         :font-weight "500"}}
          (str (.toFixed (* 100 posture-score) 0) "%")]])]

     ;; Actions row
     [:div {:style {:display "flex"
                    :gap "8px"
                    :padding-left "28px"}}
      [:button {:on-click #(do (.stopPropagation %)
                               (rf/dispatch [::state/load-session-from-disk session-id]))
                :style {:padding "6px 12px"
                        :font-size "12px"
                        :background-color "#007AFF"
                        :color "white"
                        :border "none"
                        :border-radius "4px"
                        :cursor "pointer"}}
       "View"]

      [:button {:on-click #(do (.stopPropagation %)
                               (when (js/confirm "Delete this session? This cannot be undone.")
                                 (rf/dispatch [::state/session-browser/delete-session session-id])))
                :style {:padding "6px 12px"
                        :font-size "12px"
                        :background-color "#EF5350"
                        :color "white"
                        :border "none"
                        :border-radius "4px"
                        :cursor "pointer"}}
       "Delete"]]]))

(defn session-list
  "List of session cards"
  []
  (let [filtered-sessions @(rf/subscribe [::state/session-browser/filtered-sessions])
        selected-ids @(rf/subscribe [::state/session-browser/selected-ids])
        selected-set (set selected-ids)]
    [:div {:style {:padding "15px"
                   :max-height "calc(100vh - 380px)"
                   :overflow-y "auto"}}
     (if (seq filtered-sessions)
       [:div
        (for [session filtered-sessions]
          ^{:key (:session/id session)}
          [session-card session (contains? selected-set (:session/id session))])]

       ;; Empty state
       [:div {:style {:text-align "center"
                      :padding "60px 20px"
                      :color "#999"}}
        [:p {:style {:font-size "18px"
                     :margin "0 0 10px 0"}}
         "No sessions found"]
        [:p {:style {:font-size "14px"
                     :margin "0 0 20px 0"}}
         "Try adjusting your filters or record a new session"]
        [:button {:on-click #(rf/dispatch [::state/set-view :live-feed])
                  :style {:padding "12px 24px"
                          :font-size "14px"
                          :background-color "#007AFF"
                          :color "white"
                          :border "none"
                          :border-radius "6px"
                          :cursor "pointer"}}
         "Record Your First Session"]])]))

(defn action-bar
  "Bottom action bar for bulk operations"
  []
  (let [selected-count @(rf/subscribe [::state/session-browser/selected-count])]
    (when (> selected-count 0)
      [:div {:style {:position "fixed"
                     :bottom 0
                     :left 0
                     :right 0
                     :padding "15px 20px"
                     :background-color "#1a1a1a"
                     :border-top "1px solid #333"
                     :display "flex"
                     :gap "15px"
                     :align-items "center"
                     :box-shadow "0 -2px 10px rgba(0,0,0,0.1)"}}

       [:span {:style {:color "white"
                       :font-size "14px"}}
        (str selected-count " session" (when (> selected-count 1) "s") " selected")]

       (when (= 2 selected-count)
         [:button {:on-click #(rf/dispatch [::state/session-browser/compare-selected])
                   :style {:padding "10px 20px"
                           :font-size "14px"
                           :background-color "#00FF00"
                           :color "#000"
                           :border "none"
                           :border-radius "6px"
                           :font-weight "600"
                           :cursor "pointer"}}
          "Compare Selected (2)"])

       [:button {:on-click #(rf/dispatch [::state/session-browser/clear-selection])
                 :style {:padding "10px 20px"
                         :font-size "14px"
                         :background-color "transparent"
                         :color "#00FF00"
                         :border "1px solid #00FF00"
                         :border-radius "6px"
                         :cursor "pointer"}}
        "Clear Selection"]])))

(defn session-browser-view
  "Main session browser view"
  []
  [:div {:style {:display "flex"
                 :flex-direction "column"
                 :height "100vh"}}
   [browser-header]
   [filter-controls]
   [session-list]
   [action-bar]])
