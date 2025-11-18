(ns combatsys.renderer.views
  "Reagent UI components for the motion analysis platform.
   
   Philosophy (Brett Victor):
   - Make invisible visible
   - Show reasoning, not just results
   - Immediate feedback"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [combatsys.renderer.state :as state]))

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
;; BREATHING METRICS PANEL
;; ============================================================

(defn breathing-metrics
  "Display breathing analysis results"
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
;; POSTURE METRICS PANEL
;; ============================================================

(defn posture-metrics
  "Display posture analysis results"
  []
  (let [analysis @(rf/subscribe [::state/posture-analysis])]
    [panel "Posture Analysis"
     (if analysis
       [:div
        [:div {:style {:display "flex"
                       :flex-direction "column"
                       :gap "10px"}}
         [:div
          [:strong "Forward Head: "]
          [:span {:style {:color (if (> (:head-forward-cm analysis) 5)
                                    "#FF6B6B"
                                    "#4CAF50")}}
           (str (:head-forward-cm analysis) " cm")]]
         
         [:div
          [:strong "Shoulder Imbalance: "]
          [:span (str (:shoulder-imbalance-deg analysis) "°")]]
         
         [:div
          [:strong "Spine Alignment: "]
          [:span {:style {:text-transform "capitalize"}}
           (name (:spine-alignment analysis))]]
         
         [:div
          [:strong "Overall Score: "]
          [:span {:style {:font-size "20px"
                          :color "#007AFF"}}
           (str (* (:overall-score analysis) 100) "%")]]
         
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
                (:insight/description insight)]]])]]]]
       
       [:div "No posture analysis available."])]))

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
      
      [button {:label (if (= mode :recording) "Stop Recording" "Start Recording")
               :on-click #(rf/dispatch [(if (= mode :recording)
                                          ::state/stop-recording
                                          ::state/start-recording)])}]
      
      [button {:label "Analyze Session"
               :disabled? (not current-session)
               :on-click #(when current-session
                           (rf/dispatch [::state/analyze-session
                                        (:session/id current-session)]))}]
      
      [:div {:style {:margin-left "auto"
                     :font-size "12px"
                     :color "#666"}}
       (str "Mode: " (name mode))]]]))

;; ============================================================
;; MAIN APP VIEW
;; ============================================================

(defn main-view
  "Root component"
  []
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
     
     [panel "Skeleton View"
      [skeleton-canvas]]
     
     [timeline-scrubber]]
    
    ;; Right sidebar - metrics
    [:div {:style {:width "350px"
                   :padding "15px"
                   :overflow-y "auto"
                   :border-left "1px solid #ddd"
                   :display "flex"
                   :flex-direction "column"
                   :gap "15px"}}
     [breathing-metrics]
     [posture-metrics]]]])
