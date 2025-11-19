(ns combatsys.renderer.analytics-view
  "Analytics dashboard showing trend analysis across sessions.

   Philosophy (Brett Victor):
   - Make trends visible with charts
   - Show the math (slope, R², trend direction)
   - Explain insufficient data clearly"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [combatsys.renderer.state :as state]
            [combatsys.renderer.charts :as charts]))

;; ============================================================================
;; UTILITY FUNCTIONS
;; ============================================================================

(defn format-date
  "Format ISO date string to short format (M/D)"
  [iso-string]
  (when iso-string
    (let [date (js/Date. iso-string)]
      (str (inc (.getMonth date)) "/" (.getDate date)))))

;; ============================================================================
;; COMPONENTS
;; ============================================================================

(defn analytics-header
  "Header with session count and date range"
  []
  (let [trend-analysis @(rf/subscribe [::state/analytics/trend-analysis])]
    (when trend-analysis
      [:div {:style {:padding "20px"
                     :background-color "#1a1a1a"
                     :color "white"
                     :border-bottom "1px solid #333"}}
       [:h1 {:style {:margin "0 0 10px 0"
                     :font-size "28px"}}
        "Analytics Dashboard"]
       [:p {:style {:margin "0"
                    :font-size "14px"
                    :color "#888"}}
        "Trends based on " (:session-count trend-analysis) " sessions"
        (when-let [date-range (:date-range trend-analysis)]
          (str " • " (format-date (:start-date date-range))
               " to " (format-date (:end-date date-range))))]])))

(defn trend-stats
  "Display trend statistics (direction, R², slope)"
  [trend-data]
  (when trend-data
    [:div {:style {:display "flex"
                   :gap "30px"
                   :padding "15px"
                   :background-color "#f5f5f5"
                   :border-radius "8px"
                   :margin-top "15px"}}
     ;; Trend direction
     [:div {:style {:display "flex"
                    :flex-direction "column"}}
      [:span {:style {:font-size "12px"
                      :color "#888"
                      :text-transform "uppercase"}}
       "Trend"]
      (let [direction (:trend-direction trend-data)
            [emoji label color] (case direction
                                  :improving ["↗" "Improving" "#4CAF50"]
                                  :declining ["↘" "Declining" "#F44336"]
                                  :stable ["→" "Stable" "#FFC107"]
                                  ["?" "Unknown" "#999"])]
        [:span {:style {:font-size "20px"
                        :font-weight "600"
                        :color color}}
         emoji " " label])]

     ;; R² (goodness of fit)
     [:div {:style {:display "flex"
                    :flex-direction "column"}}
      [:span {:style {:font-size "12px"
                      :color "#888"
                      :text-transform "uppercase"}}
       "R² (Fit Quality)"]
      [:span {:style {:font-size "20px"
                      :font-weight "600"
                      :color "#333"}}
       (.toFixed (:r2 trend-data) 3)]
      [:span {:style {:font-size "11px"
                      :color "#666"}}
       (cond
         (>= (:r2 trend-data) 0.8) "Excellent fit"
         (>= (:r2 trend-data) 0.6) "Good fit"
         (>= (:r2 trend-data) 0.4) "Moderate fit"
         :else "Weak fit")]]

     ;; Slope
     [:div {:style {:display "flex"
                    :flex-direction "column"}}
      [:span {:style {:font-size "12px"
                      :color "#888"
                      :text-transform "uppercase"}}
       "Slope"]
      [:span {:style {:font-size "20px"
                      :font-weight "600"
                      :color "#333"}}
       (if (> (:slope trend-data) 0) "+" "")
       (.toFixed (:slope trend-data) 3)]
      [:span {:style {:font-size "11px"
                      :color "#666"}}
       "per session"]]]))

(defn trend-chart-panel
  "Panel showing trend chart with statistics"
  [trend-data title y-label]
  (when trend-data
    [:div {:style {:margin "20px"
                   :background-color "white"
                   :border "1px solid #ddd"
                   :border-radius "8px"
                   :padding "20px"}}
     [charts/line-chart
      {:data (:values trend-data)
       :labels (map format-date (:timestamps trend-data))
       :title title
       :y-label y-label
       :width 700
       :height 350
       :trend-line {:m (:slope trend-data)
                    :b (:intercept trend-data)}}]

     [trend-stats trend-data]]))

(defn insufficient-data-message
  "Message shown when there are not enough sessions for trend analysis"
  []
  [:div {:style {:display "flex"
                 :flex-direction "column"
                 :align-items "center"
                 :justify-content "center"
                 :height "60vh"
                 :text-align "center"}}
   [:div {:style {:font-size "64px"
                  :margin-bottom "20px"}}
    "📊"]
   [:h2 {:style {:margin "0 0 10px 0"
                 :font-size "24px"
                 :color "#333"}}
    "Insufficient Data for Trend Analysis"]
   [:p {:style {:margin "0 0 30px 0"
                :font-size "16px"
                :color "#666"
                :max-width "500px"}}
    "Record at least 3 training sessions to see trends in your performance over time."]
   [:button {:on-click #(rf/dispatch [::state/set-view :live-feed])
             :style {:padding "15px 30px"
                     :font-size "16px"
                     :background-color "#007AFF"
                     :color "white"
                     :border "none"
                     :border-radius "8px"
                     :cursor "pointer"
                     :font-weight "600"}}
    "Record a Session"]])

(defn analytics-dashboard
  "Main analytics dashboard view"
  []
  (let [trend-analysis @(rf/subscribe [::state/analytics/trend-analysis])]
    [:div {:style {:display "flex"
                   :flex-direction "column"
                   :height "100vh"
                   :background-color "#fafafa"}}

     (if trend-analysis
       ;; Show analytics
       [:div
        [analytics-header]

        [:div {:style {:flex "1"
                       :overflow-y "auto"
                       :padding-bottom "20px"}}
         ;; Breathing Rate Trend
         (when-let [breathing-rate (:breathing-rate (:trends trend-analysis))]
           [trend-chart-panel
            breathing-rate
            "Breathing Rate Over Time"
            "Rate (bpm)"])

         ;; Posture Score Trend
         (when-let [posture-score (:posture-score (:trends trend-analysis))]
           [trend-chart-panel
            posture-score
            "Posture Score Over Time"
            "Score (0-1)"])

         ;; Breathing Depth Trend
         (when-let [breathing-depth (:breathing-depth (:trends trend-analysis))]
           [trend-chart-panel
            breathing-depth
            "Breathing Depth Over Time"
            "Depth Score (0-1)"])

         ;; Forward Head Posture Trend
         (when-let [forward-head (:forward-head (:trends trend-analysis))]
           [trend-chart-panel
            forward-head
            "Forward Head Posture Over Time"
            "Distance (cm)"])]

        ;; Bottom action bar
        [:div {:style {:padding "20px"
                       :background-color "#f5f5f5"
                       :border-top "1px solid #ddd"
                       :display "flex"
                       :gap "12px"}}
         [:button {:on-click #(rf/dispatch [::state/set-view :session-browser])
                   :style {:padding "12px 24px"
                           :font-size "14px"
                           :background-color "#007AFF"
                           :color "white"
                           :border "none"
                           :border-radius "6px"
                           :cursor "pointer"
                           :font-weight "600"}}
          "← Back to Sessions"]]]

       ;; Insufficient data
       [insufficient-data-message])]))
