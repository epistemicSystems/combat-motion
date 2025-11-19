(ns combatsys.renderer.onboarding
  "Calibration wizard UI for user onboarding.

   Philosophy (Brett Victor):
   - Make the calibration process transparent and visible
   - Show what we're measuring and why
   - Provide immediate feedback

   Flow:
   1. Welcome screen - explain why we calibrate
   2. Step 0: T-pose (10s) - measure body proportions
   3. Step 1: Breathing (60s) - measure breathing baseline
   4. Step 2: Movement (60s) - measure ROM ranges
   5. Completion screen - show personalized baseline"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [combatsys.renderer.state :as state]
            [combatsys.renderer.video-capture :as video]))

;; ============================================================
;; WIZARD STEP DEFINITIONS
;; ============================================================

(def wizard-steps
  [{:step-idx 0
    :step-type :t-pose
    :title "Step 1: T-Pose Calibration"
    :duration-s 10
    :description "Stand in a T-pose with arms extended horizontally. We'll measure your body proportions."
    :instructions ["Stand facing the camera, 6-8 feet away"
                   "Extend arms horizontally to sides (like a 'T')"
                   "Keep feet shoulder-width apart"
                   "Stay still and breathe normally"]
    :diagram-type :t-pose}

   {:step-idx 1
    :step-type :breathing
    :title "Step 2: Breathing Baseline"
    :duration-s 60
    :description "Stand still and breathe normally. We'll measure your typical breathing pattern."
    :instructions ["Stand comfortably facing the camera"
                   "Arms relaxed at sides"
                   "Breathe naturally - don't force it"
                   "Stay as still as possible"]
    :diagram-type :breathing}

   {:step-idx 2
    :step-type :movement
    :title "Step 3: Movement Range"
    :duration-s 60
    :description "Perform various movements. We'll measure your range of motion."
    :instructions ["Arm circles: Forward and backward"
                   "Shoulder shrugs: Up and down"
                   "Knee bends: Gentle squats"
                   "Hip rotations: Side to side"]
    :diagram-type :movement}])

;; ============================================================
;; UTILITY COMPONENTS
;; ============================================================

(defn button
  "Styled button component."
  [{:keys [on-click label disabled? primary?]}]
  [:button
   {:on-click on-click
    :disabled disabled?
    :style {:padding "12px 24px"
            :margin "8px"
            :font-size "16px"
            :font-weight "600"
            :cursor (if disabled? "not-allowed" "pointer")
            :background-color (cond
                               disabled? "#ccc"
                               primary? "#007AFF"
                               :else "#f0f0f0")
            :color (if primary? "white" "#333")
            :border "none"
            :border-radius "8px"
            :box-shadow (when-not disabled? "0 2px 4px rgba(0,0,0,0.1)")
            :transition "all 0.2s"
            :opacity (if disabled? 0.6 1.0)}}
   label])

(defn progress-indicator
  "Visual progress indicator showing 3 steps."
  [current-step-idx]
  [:div {:style {:display "flex"
                 :justify-content "center"
                 :align-items "center"
                 :margin "20px 0"}}
   (for [i (range 3)]
     ^{:key i}
     [:div {:style {:display "flex"
                    :align-items "center"}}
      ;; Step circle
      [:div {:style {:width "40px"
                     :height "40px"
                     :border-radius "50%"
                     :background-color (cond
                                        (< i current-step-idx) "#4CAF50" ;; Complete (green)
                                        (= i current-step-idx) "#007AFF" ;; Active (blue)
                                        :else "#ddd") ;; Pending (gray)
                     :color "white"
                     :display "flex"
                     :align-items "center"
                     :justify-content "center"
                     :font-weight "bold"
                     :font-size "18px"}}
       (inc i)]

      ;; Connector line (except after last step)
      (when (< i 2)
        [:div {:style {:width "60px"
                       :height "4px"
                       :background-color (if (< i current-step-idx) "#4CAF50" "#ddd")
                       :margin "0 4px"}}])])])

(defn countdown-timer
  "Large countdown timer display."
  [seconds]
  [:div {:style {:text-align "center"
                 :margin "30px 0"}}
   [:div {:style {:font-size "72px"
                  :font-weight "bold"
                  :color "#007AFF"
                  :font-family "monospace"}}
    seconds]
   [:div {:style {:font-size "16px"
                  :color "#666"
                  :margin-top "10px"}}
    "seconds remaining"]])

;; ============================================================
;; SVG DIAGRAMS
;; ============================================================

(defn t-pose-diagram
  "Simple SVG diagram showing T-pose."
  []
  [:svg {:width "200"
         :height "300"
         :view-box "0 0 200 300"
         :style {:margin "20px auto"
                 :display "block"}}
   ;; Head
   [:circle {:cx 100 :cy 40 :r 25 :fill "#007AFF" :opacity 0.3}]
   ;; Body
   [:rect {:x 85 :y 65 :width 30 :height 80 :fill "#007AFF" :opacity 0.3}]
   ;; Arms (extended horizontally)
   [:rect {:x 20 :y 80 :width 65 :height 15 :fill "#007AFF" :opacity 0.5}]
   [:rect {:x 115 :y 80 :width 65 :height 15 :fill "#007AFF" :opacity 0.5}]
   ;; Legs
   [:rect {:x 85 :y 145 :width 12 :height 80 :fill "#007AFF" :opacity 0.3}]
   [:rect {:x 103 :y 145 :width 12 :height 80 :fill "#007AFF" :opacity 0.3}]
   ;; Label
   [:text {:x 100 :y 280 :text-anchor "middle" :font-size "14" :fill "#666"}
    "Arms out like a 'T'"]])

(defn breathing-diagram
  "Simple SVG diagram showing breathing posture."
  []
  [:svg {:width "200"
         :height "300"
         :view-box "0 0 200 300"
         :style {:margin "20px auto"
                 :display "block"}}
   ;; Head
   [:circle {:cx 100 :cy 40 :r 25 :fill "#4CAF50" :opacity 0.3}]
   ;; Body (with breathing indicator)
   [:rect {:x 85 :y 65 :width 30 :height 80 :fill "#4CAF50" :opacity 0.3}]
   ;; Breathing waves
   [:path {:d "M 70 105 Q 80 100, 90 105 T 110 105 T 130 105"
           :stroke "#4CAF50"
           :stroke-width 2
           :fill "none"
           :opacity 0.6}]
   ;; Arms (relaxed at sides)
   [:rect {:x 65 :y 80 :width 15 :height 60 :fill "#4CAF50" :opacity 0.3}]
   [:rect {:x 120 :y 80 :width 15 :height 60 :fill "#4CAF50" :opacity 0.3}]
   ;; Legs
   [:rect {:x 85 :y 145 :width 12 :height 80 :fill "#4CAF50" :opacity 0.3}]
   [:rect {:x 103 :y 145 :width 12 :height 80 :fill "#4CAF50" :opacity 0.3}]
   ;; Label
   [:text {:x 100 :y 280 :text-anchor "middle" :font-size "14" :fill "#666"}
    "Breathe naturally"]])

(defn movement-diagram
  "Simple SVG diagram showing movement."
  []
  [:svg {:width "200"
         :height "300"
         :view-box "0 0 200 300"
         :style {:margin "20px auto"
                 :display "block"}}
   ;; Multiple poses showing motion
   ;; Pose 1 (faint)
   [:g {:opacity 0.2}
    [:circle {:cx 90 :cy 40 :r 20 :fill "#FF9800"}]
    [:rect {:x 80 :y 60 :width 20 :height 60 :fill "#FF9800"}]]
   ;; Pose 2 (medium)
   [:g {:opacity 0.4}
    [:circle {:cx 100 :cy 35 :r 20 :fill "#FF9800"}]
    [:rect {:x 90 :y 55 :width 20 :height 60 :fill "#FF9800"}]]
   ;; Pose 3 (solid)
   [:g {:opacity 0.6}
    [:circle {:cx 110 :cy 40 :r 20 :fill "#FF9800"}]
    [:rect {:x 100 :y 60 :width 20 :height 60 :fill "#FF9800"}]]
   ;; Motion arrows
   [:path {:d "M 60 100 L 140 100"
           :stroke "#FF9800"
           :stroke-width 2
           :marker-end "url(#arrowhead)"}]
   [:defs
    [:marker {:id "arrowhead"
              :marker-width 10
              :marker-height 10
              :ref-x 5
              :ref-y 3
              :orient "auto"}
     [:polygon {:points "0 0, 10 3, 0 6" :fill "#FF9800"}]]]
   ;; Label
   [:text {:x 100 :y 280 :text-anchor "middle" :font-size "14" :fill "#666"}
    "Move through full range"]])

(defn diagram-for-step
  "Render appropriate diagram for step type."
  [step-type]
  (case step-type
    :t-pose [t-pose-diagram]
    :breathing [breathing-diagram]
    :movement [movement-diagram]
    [:div]))

;; ============================================================
;; WELCOME SCREEN
;; ============================================================

(defn welcome-screen
  "Initial welcome screen explaining calibration."
  []
  [:div {:style {:max-width "600px"
                 :margin "60px auto"
                 :padding "40px"
                 :background-color "#f9f9f9"
                 :border-radius "12px"
                 :box-shadow "0 4px 12px rgba(0,0,0,0.1)"
                 :text-align "center"}}
   [:h1 {:style {:color "#333"
                 :margin-bottom "20px"}}
    "Welcome to CombatSys"]

   [:p {:style {:font-size "18px"
                :color "#666"
                :line-height "1.6"
                :margin "20px 0"}}
    "Let's personalize your training experience by learning your baseline."]

   [:div {:style {:background-color "white"
                  :padding "30px"
                  :border-radius "8px"
                  :margin "30px 0"
                  :text-align "left"}}
    [:h2 {:style {:color "#007AFF"
                  :margin-bottom "15px"}}
     "Why calibrate?"]

    [:ul {:style {:font-size "16px"
                  :color "#555"
                  :line-height "1.8"}}
     [:li "📏 Measure YOUR body proportions (not generic averages)"]
     [:li "🫁 Learn YOUR normal breathing pattern"]
     [:li "🤸 Track YOUR range of motion"]
     [:li "🎯 Get personalized insights based on YOUR baseline"]]]

   [:div {:style {:margin "30px 0"
                  :padding "20px"
                  :background-color "#FFF3CD"
                  :border-radius "8px"
                  :border "1px solid #FFEAA7"}}
    [:p {:style {:font-size "14px"
                 :color "#856404"
                 :margin 0}}
     "⏱️ This takes about 2 minutes. You only need to do it once!"]]

   [:div {:style {:margin-top "40px"}}
    [button {:label "Start Calibration"
             :on-click #(rf/dispatch [::state/calibration/start-wizard])
             :primary? true}]

    [button {:label "Skip for now"
             :on-click #(rf/dispatch [::state/calibration/skip-wizard])
             :primary? false}]]])

;; ============================================================
;; TIMER MANAGEMENT (Side Effect Component)
;; ============================================================

(defn timer-manager
  "Component that manages the countdown timer.

   Uses js/setInterval to tick every second.
   Cleans up interval on unmount."
  []
  (let [recording? @(rf/subscribe [::state/calibration/recording?])
        timer-handle (r/atom nil)]

    (r/create-class
     {:component-did-mount
      (fn [_]
        (println "Timer manager mounted"))

      :component-did-update
      (fn [this _]
        ;; Start timer when recording starts
        (let [currently-recording? @(rf/subscribe [::state/calibration/recording?])]
          (when (and currently-recording? (not @timer-handle))
            (println "Starting countdown timer...")
            (reset! timer-handle
                    (js/setInterval
                     #(rf/dispatch [::state/calibration/tick])
                     1000))))) ;; Tick every 1000ms (1 second)

      :component-will-unmount
      (fn [_]
        (println "Timer manager unmounting - cleaning up interval")
        (when @timer-handle
          (js/clearInterval @timer-handle)
          (reset! timer-handle nil)))

      :reagent-render
      (fn []
        ;; Stop timer when recording stops
        (when (and @timer-handle (not @(rf/subscribe [::state/calibration/recording?])))
          (println "Stopping countdown timer...")
          (js/clearInterval @timer-handle)
          (reset! timer-handle nil))

        ;; This component is invisible - just manages side effects
        [:div {:style {:display "none"}}])})))

;; ============================================================
;; STEP VIEW
;; ============================================================

(defn height-input
  "Input field for user height (shown during T-pose step)."
  []
  (let [height @(rf/subscribe [::state/calibration/user-height-cm])]
    [:div {:style {:margin "20px 0"
                   :padding "20px"
                   :background-color "#FFF"
                   :border-radius "8px"
                   :border "2px solid #007AFF"}}
     [:label {:style {:display "block"
                      :font-size "16px"
                      :font-weight "600"
                      :color "#333"
                      :margin-bottom "10px"}}
      "Your Height (cm):"]

     [:input {:type "number"
              :value (or height "")
              :on-change #(rf/dispatch [::state/calibration/set-user-height
                                       (js/parseInt (-> % .-target .-value))])
              :placeholder "e.g., 178"
              :style {:width "150px"
                      :padding "12px"
                      :font-size "18px"
                      :border "1px solid #ddd"
                      :border-radius "6px"}}]

     [:p {:style {:font-size "14px"
                  :color "#666"
                  :margin-top "10px"}}
      "We use this to convert camera measurements to centimeters."]]))

(defn step-view
  "View for individual calibration step."
  [step-data]
  (let [recording? @(rf/subscribe [::state/calibration/recording?])
        seconds-remaining @(rf/subscribe [::state/calibration/seconds-remaining])
        height @(rf/subscribe [::state/calibration/user-height-cm])
        step-idx (:step-idx step-data)
        can-start? (if (= 0 step-idx)
                     (and height (> height 100) (< height 250)) ;; Valid height
                     true)]

    [:div {:style {:max-width "900px"
                   :margin "40px auto"
                   :padding "30px"}}
     ;; Progress indicator
     [progress-indicator step-idx]

     ;; Step title
     [:h1 {:style {:text-align "center"
                   :color "#333"
                   :margin "30px 0"}}
      (:title step-data)]

     ;; Description
     [:p {:style {:text-align "center"
                  :font-size "18px"
                  :color "#666"
                  :margin-bottom "30px"}}
      (:description step-data)]

     ;; Main content area (2 columns)
     [:div {:style {:display "flex"
                    :gap "30px"
                    :margin "30px 0"}}
      ;; Left column: Instructions + Diagram
      [:div {:style {:flex "1"
                     :background-color "#f9f9f9"
                     :padding "30px"
                     :border-radius "12px"}}
       [:h3 {:style {:color "#007AFF"
                     :margin-bottom "15px"}}
        "Instructions:"]

       [:ol {:style {:font-size "16px"
                     :color "#555"
                     :line-height "2"
                     :padding-left "20px"}}
        (for [[idx instruction] (map-indexed vector (:instructions step-data))]
          ^{:key idx}
          [:li instruction])]

       ;; Diagram
       [diagram-for-step (:diagram-type step-data)]]

      ;; Right column: Camera preview + Timer
      [:div {:style {:flex "1"}}
       ;; Height input (T-pose step only)
       (when (= 0 step-idx)
         [height-input])

       ;; Camera preview placeholder
       [:div {:style {:background-color "#000"
                      :border-radius "8px"
                      :aspect-ratio "4/3"
                      :display "flex"
                      :align-items "center"
                      :justify-content "center"
                      :margin-bottom "20px"}}
        (if recording?
          [:div {:style {:color "white"
                         :font-size "16px"}}
           "📹 Camera active (showing live feed with skeleton overlay)"]
          [:div {:style {:color "#666"
                         :font-size "16px"}}
           "Camera preview will appear here"])]

       ;; Timer (if recording)
       (when recording?
         [countdown-timer seconds-remaining])]]

     ;; Action buttons
     [:div {:style {:text-align "center"
                    :margin-top "40px"}}
      (if recording?
        ;; Recording in progress
        [:div
         [:div {:style {:font-size "18px"
                        :color "#007AFF"
                        :margin-bottom "20px"
                        :font-weight "600"}}
          "⏺️ Recording..."]
         [button {:label "Cancel"
                  :on-click #(rf/dispatch [::state/calibration/cancel-wizard])
                  :primary? false}]]

        ;; Ready to start
        [:div
         (when (and (= 0 step-idx) (not can-start?))
           [:p {:style {:color "#d32f2f"
                        :margin-bottom "15px"
                        :font-size "14px"}}
            "Please enter your height to continue"])

         [button {:label (str "Start (" (:duration-s step-data) " seconds)")
                  :on-click #(rf/dispatch [::state/calibration/start-step step-idx])
                  :primary? true
                  :disabled? (not can-start?)}]

         [button {:label "Cancel Calibration"
                  :on-click #(rf/dispatch [::state/calibration/cancel-wizard])
                  :primary? false}]])]]))

;; ============================================================
;; COMPLETION SCREEN
;; ============================================================

(defn completion-screen
  "Final screen showing calibration results."
  []
  (let [profile @(rf/subscribe [::state/calibration/completed-profile])]
    [:div {:style {:max-width "700px"
                   :margin "60px auto"
                   :padding "40px"
                   :background-color "#f9f9f9"
                   :border-radius "12px"
                   :box-shadow "0 4px 12px rgba(0,0,0,0.1)"
                   :text-align "center"}}
     [:div {:style {:font-size "48px"
                    :margin-bottom "20px"}}
      "✅"]

     [:h1 {:style {:color "#4CAF50"
                   :margin-bottom "15px"}}
      "Calibration Complete!"]

     [:p {:style {:font-size "18px"
                  :color "#666"
                  :margin-bottom "30px"}}
      "Your personalized baseline has been created."]

     ;; Profile summary
     (when profile
       [:div {:style {:background-color "white"
                      :padding "30px"
                      :border-radius "8px"
                      :margin "30px 0"
                      :text-align "left"}}
        [:h2 {:style {:color "#007AFF"
                      :margin-bottom "20px"}}
         "Your Baseline:"]

        [:div {:style {:display "grid"
                       :grid-template-columns "1fr 1fr"
                       :gap "15px"
                       :font-size "16px"}}
         ;; Height
         [:div
          [:strong "Height: "]
          (str (:height-cm profile) " cm")]

         ;; Breathing rate
         [:div
          [:strong "Breathing rate: "]
          (str (get-in profile [:breathing-baseline :typical-rate-bpm]) " bpm")]

         ;; Breathing depth
         [:div
          [:strong "Breath depth: "]
          (str (int (* 100 (get-in profile [:breathing-baseline :typical-depth]))) "%")]

         ;; Joint distances
         [:div
          [:strong "Shoulder width: "]
          (str (int (get-in profile [:baseline-pose :joint-distances :shoulder-width-cm])) " cm")]]

        [:p {:style {:font-size "14px"
                     :color "#666"
                     :margin-top "20px"
                     :font-style "italic"}}
         "These measurements will be used to provide personalized insights during your training sessions."]])

     ;; Finish button
     [:div {:style {:margin-top "40px"}}
      [button {:label "Start Training"
               :on-click #(rf/dispatch [::state/calibration/finish])
               :primary? true}]]]))

;; ============================================================
;; MAIN WIZARD COMPONENT
;; ============================================================

(defn calibration-wizard
  "Main wizard component that routes between screens."
  []
  (let [step-idx @(rf/subscribe [::state/calibration/current-step-idx])]
    [:div
     ;; Timer manager (invisible, handles countdown)
     [timer-manager]

     ;; Main wizard content
     (cond
       ;; Welcome screen (step-idx = 0 but not started)
       (nil? step-idx)
       [welcome-screen]

       ;; Steps 0-2 (T-pose, Breathing, Movement)
       (<= 0 step-idx 2)
       [step-view (nth wizard-steps step-idx)]

       ;; Completion screen (step-idx = 3+)
       (>= step-idx 3)
       [completion-screen]

       ;; Fallback
       :else
       [:div "Invalid wizard state"])]))
