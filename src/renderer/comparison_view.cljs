(ns combatsys.renderer.comparison-view
  "Comparison view for side-by-side session analysis.

   Philosophy (Brett Victor):
   - Make the comparison visible and immediate
   - Show deltas and percentages for transparency
   - Natural language insights explain what happened"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [combatsys.renderer.state :as state]))

;; ============================================================================
;; UTILITY FUNCTIONS
;; ============================================================================

(defn format-number
  "Format number with appropriate precision"
  [n]
  (cond
    (nil? n) "N/A"
    (number? n) (if (< (js/Math.abs n) 10)
                  (.toFixed n 2)
                  (.toFixed n 1))
    :else (str n)))

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

;; ============================================================================
;; COMPONENTS
;; ============================================================================

(defn comparison-header
  "Header showing session names, dates, and overall assessment"
  [comparison]
  [:div {:style {:padding "20px"
                 :background-color "#1a1a1a"
                 :color "white"
                 :border-bottom "1px solid #333"}}
   [:h1 {:style {:margin "0 0 20px 0"
                 :font-size "28px"}}
    "Session Comparison"]

   ;; Session info row
   [:div {:style {:display "flex"
                  :align-items "center"
                  :justify-content "space-between"
                  :margin-bottom "20px"
                  :gap "20px"}}
    ;; Session A
    [:div {:style {:flex "1"
                   :background-color "#2a2a2a"
                   :padding "15px"
                   :border-radius "8px"}}
     [:h3 {:style {:margin "0 0 8px 0"
                   :font-size "18px"
                   :color "#00FF00"}}
      (:session-a-name comparison "Session A")]
     [:p {:style {:margin "0"
                  :font-size "14px"
                  :color "#888"}}
      (format-date (:session-a-date comparison))]]

    ;; Arrow
    [:div {:style {:font-size "32px"
                   :color "#666"}}
     "→"]

    ;; Session B
    [:div {:style {:flex "1"
                   :background-color "#2a2a2a"
                   :padding "15px"
                   :border-radius "8px"}}
     [:h3 {:style {:margin "0 0 8px 0"
                   :font-size "18px"
                   :color "#00FF00"}}
      (:session-b-name comparison "Session B")]
     [:p {:style {:margin "0"
                  :font-size "14px"
                  :color "#888"}}
      (format-date (:session-b-date comparison))]]]

   ;; Overall assessment badge
   (let [assessment (:overall-assessment comparison)
         [emoji label color] (case assessment
                               :significant-improvement ["📈" "Significant Improvement" "#4CAF50"]
                               :slight-improvement ["↗" "Slight Improvement" "#8BC34A"]
                               :stable ["→" "Stable" "#FFC107"]
                               :slight-decline ["↘" "Slight Decline" "#FF9800"]
                               :significant-decline ["📉" "Significant Decline" "#F44336"]
                               ["?" "Unknown" "#999"])]
     [:div {:style {:display "inline-block"
                    :padding "12px 24px"
                    :background-color color
                    :color "white"
                    :border-radius "24px"
                    :font-size "18px"
                    :font-weight "600"}}
      emoji " " label])])

(defn metric-comparison-row
  "Single metric comparison row for table"
  [label comparison]
  (when comparison
    (let [{:keys [metric-a metric-b delta pct-change direction improvement?]} comparison
          delta-color (cond
                        improvement? "#4CAF50"
                        (= direction :unchanged) "#999"
                        :else "#F44336")]
      [:tr {:style {:border-bottom "1px solid #eee"}}
       [:td {:style {:padding "12px"
                     :font-weight "500"
                     :color "#333"}}
        label]
       [:td {:style {:padding "12px"
                     :text-align "center"
                     :font-size "16px"
                     :color "#666"}}
        (format-number metric-a)]
       [:td {:style {:padding "12px"
                     :text-align "center"
                     :color "#999"}}
        "→"]
       [:td {:style {:padding "12px"
                     :text-align "center"
                     :font-size "16px"
                     :font-weight "600"
                     :color "#333"}}
        (format-number metric-b)]
       [:td {:style {:padding "12px"
                     :text-align "right"}}
        [:div {:style {:display "flex"
                       :flex-direction "column"
                       :align-items "flex-end"}}
         [:span {:style {:font-size "16px"
                         :font-weight "600"
                         :color delta-color}}
          (if (> delta 0) "+" "")
          (format-number delta)]
         [:span {:style {:font-size "12px"
                         :color "#999"}}
          "("
          (if (> pct-change 0) "+" "")
          (.toFixed pct-change 1)
          "%)"]
         (when improvement?
           [:span {:style {:font-size "14px"
                           :color "#4CAF50"}}
            "✓"])]]])))

(defn breathing-comparison-table
  "Breathing metrics comparison table"
  [breathing-comp]
  [:div {:style {:margin "20px"
                 :background-color "white"
                 :border "1px solid #ddd"
                 :border-radius "8px"
                 :overflow "hidden"}}
   [:h2 {:style {:margin "0"
                 :padding "15px 20px"
                 :background-color "#f5f5f5"
                 :border-bottom "2px solid #ddd"
                 :font-size "20px"
                 :color "#333"}}
    "Breathing Analysis"]

   [:table {:style {:width "100%"
                    :border-collapse "collapse"}}
    [:thead
     [:tr {:style {:background-color "#fafafa"
                   :border-bottom "2px solid #ddd"}}
      [:th {:style {:padding "12px"
                    :text-align "left"
                    :font-weight "600"
                    :color "#666"
                    :text-transform "uppercase"
                    :font-size "12px"}}
       "Metric"]
      [:th {:style {:padding "12px"
                    :text-align "center"
                    :font-weight "600"
                    :color "#666"
                    :text-transform "uppercase"
                    :font-size "12px"}}
       "Session A"]
      [:th {:style {:padding "12px"
                    :text-align "center"
                    :font-weight "600"
                    :color "#666"}}
       ""]
      [:th {:style {:padding "12px"
                    :text-align "center"
                    :font-weight "600"
                    :color "#666"
                    :text-transform "uppercase"
                    :font-size "12px"}}
       "Session B"]
      [:th {:style {:padding "12px"
                    :text-align "right"
                    :font-weight "600"
                    :color "#666"
                    :text-transform "uppercase"
                    :font-size "12px"}}
       "Change"]]]

    [:tbody
     [metric-comparison-row "Rate (bpm)"
      (:rate-comparison breathing-comp)]

     [metric-comparison-row "Depth Score"
      (:depth-comparison breathing-comp)]

     ;; Fatigue windows (special case - not from compare-metric)
     (when-let [fatigue-comp (:fatigue-comparison breathing-comp)]
       (let [count-a (:count-a fatigue-comp)
             count-b (:count-b fatigue-comp)
             delta (:delta fatigue-comp)
             improvement? (:improvement? fatigue-comp)
             delta-color (if improvement? "#4CAF50" "#F44336")]
         [:tr {:style {:border-bottom "1px solid #eee"}}
          [:td {:style {:padding "12px"
                        :font-weight "500"
                        :color "#333"}}
           "Fatigue Windows"]
          [:td {:style {:padding "12px"
                        :text-align "center"
                        :font-size "16px"
                        :color "#666"}}
           count-a]
          [:td {:style {:padding "12px"
                        :text-align "center"
                        :color "#999"}}
           "→"]
          [:td {:style {:padding "12px"
                        :text-align "center"
                        :font-size "16px"
                        :font-weight "600"
                        :color "#333"}}
           count-b]
          [:td {:style {:padding "12px"
                        :text-align "right"}}
           [:div {:style {:display "flex"
                          :flex-direction "column"
                          :align-items "flex-end"}}
            [:span {:style {:font-size "16px"
                            :font-weight "600"
                            :color delta-color}}
             (if (> delta 0) "+" "")
             delta]
            (when improvement?
              [:span {:style {:font-size "14px"
                              :color "#4CAF50"}}
               "✓"])]]]))]]])

(defn posture-comparison-table
  "Posture metrics comparison table"
  [posture-comp]
  [:div {:style {:margin "20px"
                 :background-color "white"
                 :border "1px solid #ddd"
                 :border-radius "8px"
                 :overflow "hidden"}}
   [:h2 {:style {:margin "0"
                 :padding "15px 20px"
                 :background-color "#f5f5f5"
                 :border-bottom "2px solid #ddd"
                 :font-size "20px"
                 :color "#333"}}
    "Posture Analysis"]

   [:table {:style {:width "100%"
                    :border-collapse "collapse"}}
    [:thead
     [:tr {:style {:background-color "#fafafa"
                   :border-bottom "2px solid #ddd"}}
      [:th {:style {:padding "12px"
                    :text-align "left"
                    :font-weight "600"
                    :color "#666"
                    :text-transform "uppercase"
                    :font-size "12px"}}
       "Metric"]
      [:th {:style {:padding "12px"
                    :text-align "center"
                    :font-weight "600"
                    :color "#666"
                    :text-transform "uppercase"
                    :font-size "12px"}}
       "Session A"]
      [:th {:style {:padding "12px"
                    :text-align "center"
                    :font-weight "600"
                    :color "#666"}}
       ""]
      [:th {:style {:padding "12px"
                    :text-align "center"
                    :font-weight "600"
                    :color "#666"
                    :text-transform "uppercase"
                    :font-size "12px"}}
       "Session B"]
      [:th {:style {:padding "12px"
                    :text-align "right"
                    :font-weight "600"
                    :color "#666"
                    :text-transform "uppercase"
                    :font-size "12px"}}
       "Change"]]]

    [:tbody
     [metric-comparison-row "Overall Score"
      (:overall-score-comparison posture-comp)]

     [metric-comparison-row "Forward Head (cm)"
      (:forward-head-comparison posture-comp)]

     [metric-comparison-row "Shoulder Imbalance (°)"
      (:shoulder-imbalance-comparison posture-comp)]]]])

(defn insights-panel
  "Panel showing natural language insights"
  [insights]
  [:div {:style {:margin "20px"
                 :background-color "white"
                 :border "1px solid #ddd"
                 :border-radius "8px"
                 :overflow "hidden"}}
   [:h2 {:style {:margin "0"
                 :padding "15px 20px"
                 :background-color "#f5f5f5"
                 :border-bottom "2px solid #ddd"
                 :font-size "20px"
                 :color "#333"}}
    "💡 Key Insights"]

   [:div {:style {:padding "20px"}}
    (if (seq insights)
      [:div {:style {:display "flex"
                     :flex-direction "column"
                     :gap "12px"}}
       (for [insight insights]
         ^{:key (:title insight)}
         (let [severity (:severity insight)
               [bg-color border-color icon-color] (case severity
                                                    :positive ["#E8F5E9" "#4CAF50" "#2E7D32"]
                                                    :negative ["#FFEBEE" "#F44336" "#C62828"]
                                                    ["#F5F5F5" "#999" "#666"])]
           [:div {:style {:padding "12px"
                          :background-color bg-color
                          :border-left (str "4px solid " border-color)
                          :border-radius "4px"}}
            [:h4 {:style {:margin "0 0 6px 0"
                          :font-size "14px"
                          :font-weight "600"
                          :color "#333"}}
             (case severity
               :positive "✓ "
               :negative "✗ "
               "• ")
             (:title insight)]
            [:p {:style {:margin "0"
                         :font-size "13px"
                         :color "#666"
                         :line-height "1.5"}}
             (:description insight)]]))]

      ;; No insights
      [:p {:style {:color "#999"
                   :font-style "italic"
                   :text-align "center"
                   :padding "20px"}}
       "No significant changes detected"])]])

(defn action-bar
  "Bottom action bar with navigation and export buttons"
  []
  [:div {:style {:padding "20px"
                 :background-color "#f5f5f5"
                 :border-top "1px solid #ddd"
                 :display "flex"
                 :gap "12px"
                 :justify-content "space-between"}}
   [:button {:on-click #(rf/dispatch [::state/set-view :session-browser])
             :style {:padding "12px 24px"
                     :font-size "14px"
                     :background-color "#007AFF"
                     :color "white"
                     :border "none"
                     :border-radius "6px"
                     :cursor "pointer"
                     :font-weight "600"}}
    "← Back to Sessions"]

   [:button {:on-click #(js/alert "Export feature coming soon!")
             :style {:padding "12px 24px"
                     :font-size "14px"
                     :background-color "white"
                     :color "#333"
                     :border "1px solid #ccc"
                     :border-radius "6px"
                     :cursor "pointer"}}
    "Export Report"]])

(defn comparison-view
  "Main comparison view component"
  []
  (let [comparison @(rf/subscribe [::state/comparison/report])]
    (if comparison
      [:div {:style {:display "flex"
                     :flex-direction "column"
                     :height "100vh"
                     :background-color "#fafafa"}}
       [comparison-header comparison]

       [:div {:style {:flex "1"
                      :overflow-y "auto"
                      :padding-bottom "80px"}}
        (when (:breathing-comparison comparison)
          [breathing-comparison-table (:breathing-comparison comparison)])

        (when (:posture-comparison comparison)
          [posture-comparison-table (:posture-comparison comparison)])

        [insights-panel (:insights comparison [])]]

       [action-bar]]

      ;; Loading state
      [:div {:style {:display "flex"
                     :align-items "center"
                     :justify-content "center"
                     :height "100vh"
                     :font-size "18px"
                     :color "#999"}}
       "Loading comparison..."])))
