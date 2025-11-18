(ns combatsys.renderer.views
  "Reagent UI components for the motion analysis platform.

   Philosophy (Brett Victor):
   - Make invisible visible
   - Show reasoning, not just results
   - Immediate feedback"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [combatsys.renderer.state :as state]
            [combatsys.renderer.video-capture :as video]
            [combatsys.renderer.onboarding :as onboarding]))

;; ============================================================
;; UTILITY COMPONENTS
;; ============================================================

(defn button
  "Reusable button component"
  [{:keys [on-click label disabled?]}]
  [:button
   {:on-click on-click
    :disabled disabled?
    :style {:padding "10px 20px"
            :margin "5px"
            :font-size "14px"
            :cursor (if disabled? "not-allowed" "pointer")
            :background-color (if disabled? "#ccc" "#007AFF")
            :color "white"
            :border "none"
            :border-radius "5px"}}
   label])

(defn panel
  "Reusable panel container"
  [title & children]
  [:div {:style {:border "1px solid #ddd"
                 :border-radius "8px"
                 :padding "15px"
                 :margin "10px 0"
                 :background-color "#f9f9f9"}}
   [:h3 {:style {:margin "0 0 10px 0"
                 :color "#333"}}
    title]
   [:div children]])

;; ============================================================
;; SKELETON OVERLAY (Simplified for LOD 0)
;; ============================================================

(defn draw-skeleton
  "Draw skeleton on canvas (simplified for LOD 0)"
  [canvas landmarks]
  (when canvas
    (let [ctx (.getContext canvas "2d")
          width (.-width canvas)
          height (.-height canvas)]

      ;; Clear canvas
      (.clearRect ctx 0 0 width height)

      ;; Draw landmarks as circles
      (doseq [lm landmarks]
        (let [x (* (:landmark/x lm) width)
              y (* (:landmark/y lm) height)]
          (set! (.-fillStyle ctx) "#00FF00")
          (.beginPath ctx)
          (.arc ctx x y 5 0 (* 2 js/Math.PI))
          (.fill ctx)))

      ;; Draw connections (simplified - just shoulders to hips)
      (set! (.-strokeStyle ctx) "#00FF00")
      (set! (.-lineWidth ctx) 2)
      (.beginPath ctx)
      ;; This would draw actual skeleton connections in real implementation
      (.stroke ctx))))

(defn skeleton-canvas
  "Canvas for drawing skeleton overlay"
  []
  (let [canvas-ref (atom nil)]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (reset! canvas-ref (r/dom-node this)))

      :component-did-update
      (fn [this]
        (let [frame @(rf/subscribe [::state/current-frame-at-position])]
          (when (and @canvas-ref frame)
            (let [landmarks (get-in frame [:frame/pose :pose/landmarks])]
              (draw-skeleton @canvas-ref landmarks)))))

      :reagent-render
      (fn []
        [:canvas
         {:width 640
          :height 480
          :style {:border "1px solid #333"
                  :background-color "#000"}}])})))

;; ============================================================
;; TIMELINE SCRUBBER
;; ============================================================

(defn timeline-scrubber
  "Interactive timeline with scrubber control"
  []
  (let [session @(rf/subscribe [::state/current-session])
        position @(rf/subscribe [::state/timeline-position])
        duration (or (:session/duration-ms session) 0)]
    [panel "Timeline"
     [:div {:style {:display "flex"
                    :flex-direction "column"
                    :gap "10px"}}
      [:input
       {:type "range"
        :min 0
        :max duration
        :value position
        :on-change (fn [e]
                     (rf/dispatch [::state/set-timeline-position
                                   (js/parseInt (.. e -target -value))]))
        :style {:width "100%"}}]

      [:div {:style {:display "flex"
                     :justify-content "space-between"
                     :font-size "12px"
                     :color "#666"}}
       [:span (str (/ position 1000) "s")]
       [:span (str (/ duration 1000) "s")]]]]))

;; ============================================================
;; ANALYSIS TABS (LOD 4 - New)
;; ============================================================

(defn analysis-tab-button
  "Single tab button with active state styling.

   Args:
     tab-id: Keyword (:breathing, :posture, etc.)
     label: Display string
     active?: Boolean - is this the active tab?
     disabled?: Boolean - is this tab disabled?"
  [{:keys [tab-id label active? disabled?]}]
  [:button
   {:on-click (when-not disabled?
                #(rf/dispatch [::state/set-analysis-tab tab-id]))
    :disabled disabled?
    :style {:padding "12px 24px"
            :margin "0"
            :font-size "14px"
            :font-weight (if active? "600" "400")
            :cursor (if disabled? "not-allowed" "pointer")
            :background-color (cond
                               active? "#1a1a1a"
                               disabled? "transparent"
                               :else "transparent")
            :color (cond
                    active? "#00FF00"
                    disabled? "#666"
                    :else "#ccc")
            :border "none"
            :border-bottom (if active? "3px solid #00FF00" "3px solid transparent")
            :transition "all 0.2s ease"
            :opacity (if disabled? "0.5" "1")}}
   label])

(defn analysis-tabs
  "Tab bar for switching between analyses.

   Shows available tabs based on which analyses have run.
   Includes disabled placeholders for future analyzers."
  []
  (let [current-tab @(rf/subscribe [::state/analysis-tab])
        available-tabs @(rf/subscribe [::state/available-analysis-tabs])
        has-breathing? (some #(= % :breathing) available-tabs)
        has-posture? (some #(= % :posture) available-tabs)]
    [:div {:style {:display "flex"
                   :gap "0px"
                   :border-bottom "2px solid #333"
                   :margin-bottom "20px"
                   :background-color "#0a0a0a"}}

     ;; Breathing tab (show if analysis exists)
     (when has-breathing?
       [analysis-tab-button
        {:tab-id :breathing
         :label "Breathing"
         :active? (= current-tab :breathing)
         :disabled? false}])

     ;; Posture tab (show if analysis exists)
     (when has-posture?
       [analysis-tab-button
        {:tab-id :posture
         :label "Posture"
         :active? (= current-tab :posture)
         :disabled? false}])

     ;; Future tabs (disabled placeholders)
     [analysis-tab-button
      {:tab-id :gait
       :label "Gait (Coming Soon)"
       :active? false
       :disabled? true}]

     [analysis-tab-button
      {:tab-id :balance
       :label "Balance (Coming Soon)"
       :active? false
       :disabled? true}]]))

;; ============================================================
;; BREATHING PANEL
;; ============================================================

(defn breathing-panel
  "Complete breathing analysis panel."
  []
  (let [analysis @(rf/subscribe [::state/breathing-analysis])]
    [panel "Breathing Analysis"
     (if analysis
       [:div
        [:div {:style {:display "flex"
                       :flex-direction "column"
                       :gap "10px"}}
         [:div
          [:strong "Rate: "]
          [:span {:style {:font-size "24px"
                          :color "#007AFF"}}
           (str (:rate-bpm analysis) " bpm")]]

         [:div
          [:strong "Depth Score: "]
          [:span (str (:depth-score analysis))]
          [:div {:style {:width "200px"
                         :height "10px"
                         :background-color "#ddd"
                         :border-radius "5px"
                         :overflow "hidden"}}
           [:div {:style {:width (str (* (:depth-score analysis) 100) "%")
                          :height "100%"
                          :background-color "#4CAF50"}}]]]

         [:div
          [:strong "Fatigue Windows: "]
          [:span (count (:fatigue-windows analysis))]]

         ;; Insights
         [:div {:style {:margin-top "10px"}}
          [:strong "Key Insights:"]
          [:ul {:style {:margin "5px 0"
                        :padding-left "20px"}}
           (for [insight (:insights analysis)]
             ^{:key (:insight/title insight)}
             [:li
              [:div {:style {:margin "5px 0"}}
               [:strong (:insight/title insight)]
               [:p {:style {:margin "3px 0"
                            :font-size "12px"
                            :color "#666"}}
                (:insight/description insight)]
               [:p {:style {:margin "3px 0"
                            :font-size "12px"
                            :font-style "italic"}}
                "💡 " (:insight/recommendation insight)]]])]]]]

       [:div "No breathing analysis available. Record a session or load demo."])]))

;; ============================================================
;; POSTURE PANEL (LOD 4 - Enhanced)
;; ============================================================

(defn posture-metric-card
  "Single posture metric with status indicator.

   Args:
     label: Metric name
     value: Metric value (string)
     status: :good | :warning | :error"
  [{:keys [label value status]}]
  [:div {:style {:padding "12px"
                 :border-radius "6px"
                 :background-color "#f9f9f9"
                 :border-left (case status
                               :good "4px solid #4CAF50"
                               :warning "4px solid #FFA726"
                               :error "4px solid #EF5350"
                               "4px solid #ccc")}}
   [:div {:style {:font-size "12px"
                  :color "#666"
                  :margin-bottom "4px"}}
    label]
   [:div {:style {:font-size "20px"
                  :font-weight "600"
                  :color (case status
                          :good "#4CAF50"
                          :warning "#FFA726"
                          :error "#EF5350"
                          "#333")}}
    value]
   [:div {:style {:font-size "11px"
                  :margin-top "4px"
                  :color (case status
                          :good "#4CAF50"
                          :warning "#FFA726"
                          :error "#EF5350"
                          "#999")}}
    (case status
      :good "✓ Good"
      :warning "⚠ Attention"
      :error "✗ Poor"
      "—")]])

(defn posture-insight-card
  "Single insight card with severity styling.

   Args:
     insight: Insight map with :insight/title, :insight/description,
              :insight/severity, :insight/recommendation"
  [insight]
  (let [severity (:insight/severity insight)]
    [:div {:style {:padding "12px"
                   :margin "8px 0"
                   :border-radius "6px"
                   :background-color (case severity
                                      :high "#FFEBEE"
                                      :medium "#FFF3E0"
                                      :low "#E3F2FD"
                                      "#f9f9f9")
                   :border-left (case severity
                                 :high "4px solid #EF5350"
                                 :medium "4px solid #FFA726"
                                 :low "4px solid #42A5F5"
                                 "4px solid #ccc")}}
     ;; Header with title and severity badge
     [:div {:style {:display "flex"
                    :justify-content "space-between"
                    :align-items "center"
                    :margin-bottom "8px"}}
      [:h4 {:style {:margin "0"
                    :font-size "14px"
                    :color "#333"}}
       (:insight/title insight)]
      [:span {:style {:padding "2px 8px"
                      :border-radius "12px"
                      :font-size "10px"
                      :font-weight "600"
                      :text-transform "uppercase"
                      :background-color (case severity
                                         :high "#EF5350"
                                         :medium "#FFA726"
                                         :low "#42A5F5"
                                         "#999")
                      :color "white"}}
       (name severity)]]

     ;; Description
     [:p {:style {:margin "0 0 8px 0"
                  :font-size "13px"
                  :color "#666"
                  :line-height "1.5"}}
      (:insight/description insight)]

     ;; Recommendation
     [:div {:style {:padding "8px"
                    :background-color "rgba(255,255,255,0.7)"
                    :border-radius "4px"
                    :font-size "12px"}}
      [:strong {:style {:color "#555"}} "💡 Recommendation: "]
      [:span {:style {:color "#666"}}
       (:insight/recommendation insight)]]]))

(defn posture-panel
  "Complete posture analysis panel with metrics grid and insights."
  []
  (let [analysis @(rf/subscribe [::state/posture-analysis])]
    [panel "Posture Analysis"
     (if analysis
       [:div
        ;; Metrics Grid
        [:div {:style {:display "grid"
                       :grid-template-columns "repeat(2, 1fr)"
                       :gap "12px"
                       :margin-bottom "20px"}}

         ;; Forward Head Posture
         [posture-metric-card
          {:label "Forward Head Posture"
           :value (str (.toFixed (:head-forward-cm analysis) 1) " cm")
           :status (cond
                    (< (:head-forward-cm analysis) 3) :good
                    (< (:head-forward-cm analysis) 5) :warning
                    :else :error)}]

         ;; Shoulder Imbalance
         [posture-metric-card
          {:label "Shoulder Imbalance"
           :value (str (.toFixed (js/Math.abs (:shoulder-imbalance-deg analysis)) 1) "°")
           :status (cond
                    (< (js/Math.abs (:shoulder-imbalance-deg analysis)) 3) :good
                    (< (js/Math.abs (:shoulder-imbalance-deg analysis)) 5) :warning
                    :else :error)}]

         ;; Spine Alignment
         [posture-metric-card
          {:label "Spine Alignment"
           :value (clojure.string/capitalize (name (:spine-alignment analysis)))
           :status (if (= :neutral (:spine-alignment analysis))
                    :good
                    :warning)}]

         ;; Overall Score
         [posture-metric-card
          {:label "Overall Posture Score"
           :value (str (.toFixed (* 100 (:overall-score analysis)) 0) "/100")
           :status (cond
                    (>= (:overall-score analysis) 0.8) :good
                    (>= (:overall-score analysis) 0.6) :warning
                    :else :error)}]]

        ;; Insights Section
        [:div {:style {:margin-top "20px"}}
         [:h3 {:style {:margin "0 0 12px 0"
                       :font-size "16px"
                       :color "#333"}}
          "💡 Posture Insights"]

         [:div
          (if (empty? (:insights analysis))
            [:p {:style {:color "#999"
                         :font-style "italic"}}
             "No specific insights at this time."]
            (for [insight (:insights analysis)]
              ^{:key (:insight/title insight)}
              [posture-insight-card insight]))]]]

       ;; No analysis available
       [:div {:style {:text-align "center"
                      :padding "40px"
                      :color "#999"}}
        [:p "No posture analysis available yet."]
        [:p {:style {:font-size "12px"}}
         "Run 'Analyze Session' to see posture metrics."]])]))

;; ============================================================
;; SESSION BROWSER
;; ============================================================

(defn session-browser
  "List of all sessions"
  []
  (let [sessions @(rf/subscribe [::state/all-sessions])
        current-id @(rf/subscribe [::state/current-session])]
    [panel "Sessions"
     [:div {:style {:max-height "400px"
                    :overflow-y "auto"}}
      (if (empty? sessions)
        [:div "No sessions yet. Load demo or record a new session."]

        [:div
         (for [session sessions]
           ^{:key (:session/id session)}
           [:div
            {:on-click #(rf/dispatch [::state/select-session (:session/id session)])
             :style {:padding "10px"
                     :margin "5px 0"
                     :border "1px solid #ddd"
                     :border-radius "5px"
                     :cursor "pointer"
                     :background-color (if (= (:session/id session)
                                             (:session/id current-id))
                                        "#E3F2FD"
                                        "white")}}
            [:div {:style {:font-weight "bold"}}
             (str "Session " (subs (str (:session/id session)) 0 8))]
            [:div {:style {:font-size "12px"
                           :color "#666"}}
             (str "Duration: " (/ (:session/duration-ms session) 1000) "s")]
            [:div {:style {:font-size "12px"
                           :color "#666"}}
             (str "Frames: " (count (:session/timeline session)))]])])]]))

;; ============================================================
;; CONTROL PANEL
;; ============================================================

(defn control-panel
  "Main control buttons"
  []
  (let [mode @(rf/subscribe [::state/capture-mode])
        current-session @(rf/subscribe [::state/current-session])]
    [:div {:style {:padding "15px"
                   :background-color "#f0f0f0"
                   :border-bottom "1px solid #ddd"}}
     [:div {:style {:display "flex"
                    :gap "10px"
                    :align-items "center"}}

      [button {:label "Load Demo Session"
               :on-click #(rf/dispatch [::state/load-demo-session])}]

      [button {:label "Calibration..."
               :on-click #(rf/dispatch [::state/calibration/start-wizard])}]

      [button {:label (if (= mode :recording) "Stop Recording" "Start Recording")
               :on-click #(rf/dispatch [(if (= mode :recording)
                                          ::state/stop-recording
                                          ::state/start-recording)])}]

      [button {:label "Analyze All"
               :disabled? (not current-session)
               :on-click #(when current-session
                           (rf/dispatch [::state/analyze-session
                                        (:session/id current-session)]))}]

      [:div {:style {:margin-left "auto"
                     :font-size "12px"
                     :color "#666"}}
       (str "Mode: " (name mode))]]]))

;; ============================================================
;; MAIN APP VIEW (LOD 4 - Updated with Tabs)
;; ============================================================

(defn main-view
  "Root component - routes between different views"
  []
  (let [current-view @(rf/subscribe [::state/current-view])
        mode @(rf/subscribe [::state/capture-mode])
        current-tab @(rf/subscribe [::state/analysis-tab])
        available-tabs @(rf/subscribe [::state/available-analysis-tabs])]

    ;; Route to calibration wizard if active
    (if (= current-view :calibration)
      [onboarding/calibration-wizard]

      ;; Otherwise show main analysis view
      [:div {:style {:font-family "system-ui, -apple-system, sans-serif"
                     :height "100vh"
                     :display "flex"
                     :flex-direction "column"}}

       ;; Header
       [:div {:style {:padding "20px"
                      :background-color "#1a1a1a"
                      :color "white"
                      :text-align "center"}}
        [:h1 {:style {:margin 0}}
         "CombatSys Motion Analysis"]
        [:p {:style {:margin "5px 0"
                     :font-size "14px"
                     :opacity 0.8}}
         "Camera-only breathing, gait, and posture analysis"]]

       ;; Control panel
       [control-panel]

   ;; Main content
   [:div {:style {:display "flex"
                  :flex 1
                  :overflow "hidden"}}

    ;; Left sidebar - session browser
    [:div {:style {:width "300px"
                   :padding "15px"
                   :overflow-y "auto"
                   :border-right "1px solid #ddd"}}
     [session-browser]]

    ;; Center - visualization
    [:div {:style {:flex 1
                   :padding "15px"
                   :overflow-y "auto"
                   :display "flex"
                   :flex-direction "column"
                   :gap "15px"}}

     ;; Live camera feed
     [panel "Live Camera Feed"
      [video/video-feed
       {:capture-enabled? (= mode :recording)
        :target-fps 15
        :on-frame-captured (fn [frame]
                            (when (= mode :recording)
                              (rf/dispatch [::state/append-frame frame])))}]]

     ;; Camera selector
     [video/camera-selector
      {:on-camera-selected (fn [camera-id]
                            (println "Camera selected:" camera-id))}]

     [timeline-scrubber]]

     ;; Right sidebar - analysis (UPDATED with tabs)
     [:div {:style {:width "350px"
                    :padding "15px"
                    :overflow-y "auto"
                    :border-left "1px solid #ddd"
                    :display "flex"
                    :flex-direction "column"
                    :gap "0px"}}

      ;; Analysis tabs (show if any analysis exists)
      (when (seq available-tabs)
        [analysis-tabs])

      ;; Tab content
      (when (seq available-tabs)
        [:div
         (case current-tab
           :breathing [breathing-panel]
           :posture [posture-panel]
           [:div "Unknown tab"])])

      ;; Show message if no analyses yet
      (when (empty? available-tabs)
        [:div {:style {:text-align "center"
                       :padding "40px"
                       :color "#999"}}
         [:p "No analyses available yet."]
         [:p {:style {:font-size "12px"}}
          "Load a demo session or record a new one, then click 'Analyze All'."]])]]])))  ;; Close if statement)
