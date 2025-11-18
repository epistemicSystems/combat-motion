(ns combatsys.renderer.views-magnification
  "UI components for Eulerian video magnification.

  Components:
  - ROI selector (drag rectangle on canvas)
  - Progress indicator
  - Side-by-side video player"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [combatsys.renderer.state :as state]))

;; ============================================================================
;; ROI Selection
;; ============================================================================

(defn draw-roi-overlay
  "Draw video frame and ROI rectangle on canvas."
  [canvas image-data state]
  (when canvas
    (let [ctx (.getContext canvas "2d")]
      ;; Draw video frame
      (.putImageData ctx image-data 0 0)

      ;; Draw ROI rectangle (if dragging)
      (when (:dragging? state)
        (let [start (:start-pos state)
              end (:current-pos state)]
          (set! (.-strokeStyle ctx) "#00ff00")
          (set! (.-lineWidth ctx) 2)
          (.strokeRect ctx
                       (:x start) (:y start)
                       (- (:x end) (:x start))
                       (- (:y end) (:y start)))))

      ;; Draw final ROI (if selected)
      (when-let [roi (:roi state)]
        (set! (.-strokeStyle ctx) "#00ff00")
        (set! (.-lineWidth ctx) 3)
        (.strokeRect ctx
                     (:x roi) (:y roi)
                     (:width roi) (:height roi))))))

(defn roi-selector
  "Canvas overlay for selecting region of interest.

  User drags mouse to draw rectangle.
  On release, dispatches ::state/magnification/roi-selected event.

  Args:
    video-frame: First frame of video (ImageData or Uint8ClampedArray)
    width: Canvas width
    height: Canvas height

  Component state:
    dragging? - Boolean
    start-pos - {:x X :y Y}
    current-pos - {:x X :y Y}
    roi - {:x X :y Y :width W :height H}"
  [video-frame width height]
  (let [state (r/atom {:dragging? false
                       :start-pos nil
                       :current-pos nil
                       :roi nil})
        canvas-ref (atom nil)]

    (r/create-class
     {:component-did-mount
      (fn [this]
        ;; Draw initial frame
        (when @canvas-ref
          (let [ctx (.getContext @canvas-ref "2d")
                image-data (if (instance? js/ImageData video-frame)
                            video-frame
                            (js/ImageData. video-frame width height))]
            (.putImageData ctx image-data 0 0))))

      :reagent-render
      (fn [video-frame width height]
        [:div.roi-selector
         {:style {:position "relative"
                  :display "inline-block"}}

         [:canvas
          {:ref #(reset! canvas-ref %)
           :width width
           :height height
           :style {:border "2px solid #333"
                   :cursor "crosshair"
                   :display "block"}

           :on-mouse-down
           (fn [e]
             (let [rect (.getBoundingClientRect (.-target e))
                   x (- (.-clientX e) (.-left rect))
                   y (- (.-clientY e) (.-top rect))]
               (swap! state assoc
                      :dragging? true
                      :start-pos {:x x :y y}
                      :current-pos {:x x :y y})))

           :on-mouse-move
           (fn [e]
             (when (:dragging? @state)
               (let [rect (.getBoundingClientRect (.-target e))
                     x (- (.-clientX e) (.-left rect))
                     y (- (.-clientY e) (.-top rect))]
                 (swap! state assoc :current-pos {:x x :y y})
                 ;; Redraw canvas with ROI rectangle
                 (when @canvas-ref
                   (let [image-data (if (instance? js/ImageData video-frame)
                                     video-frame
                                     (js/ImageData. video-frame width height))]
                     (draw-roi-overlay @canvas-ref image-data @state))))))

           :on-mouse-up
           (fn [e]
             (when (:dragging? @state)
               (let [start (:start-pos @state)
                     end (:current-pos @state)
                     roi {:x (min (:x start) (:x end))
                          :y (min (:y start) (:y end))
                          :width (js/Math.abs (- (:x end) (:x start)))
                          :height (js/Math.abs (- (:y end) (:y start)))}]
                 (swap! state assoc :dragging? false :roi roi)
                 (rf/dispatch [::state/magnification/roi-selected roi])
                 ;; Redraw with final ROI
                 (when @canvas-ref
                   (let [image-data (if (instance? js/ImageData video-frame)
                                     video-frame
                                     (js/ImageData. video-frame width height))]
                     (draw-roi-overlay @canvas-ref image-data @state))))))}]

         (when-let [roi (:roi @state)]
           [:div.roi-info
            {:style {:margin-top "10px"
                     :font-family "monospace"
                     :font-size "14px"}}
            [:p {:style {:margin "5px 0"}}
             "ROI: " [:strong (str (:x roi) ", " (:y roi))]
             " | Size: " [:strong (str (:width roi) " × " (:height roi))]]])])})))

;; ============================================================================
;; Progress Indicator
;; ============================================================================

(defn magnification-progress
  "Progress indicator for magnification processing.

  Subscribes to ::state/magnification/progress re-frame state."
  []
  (let [progress @(rf/subscribe [::state/magnification/progress])
        processing? @(rf/subscribe [::state/magnification/processing?])]
    (when processing?
      [:div.magnification-progress
       {:style {:margin "20px 0"
                :padding "20px"
                :background "#f5f5f5"
                :border-radius "8px"}}

       [:div {:style {:margin-bottom "10px"
                      :font-weight "bold"}}
        (cond
          (< progress 0.5) "Decoding video..."
          (< progress 1.0) "Magnifying frames..."
          :else "Complete!")]

       [:div.progress-bar
        {:style {:width "100%"
                 :height "30px"
                 :background "#e0e0e0"
                 :border-radius "15px"
                 :overflow "hidden"}}
        [:div.progress-fill
         {:style {:width (str (* 100 progress) "%")
                  :height "100%"
                  :background "linear-gradient(90deg, #4CAF50, #8BC34A)"
                  :transition "width 0.3s ease"}}]]

       [:p {:style {:margin "10px 0 0 0"
                    :text-align "center"
                    :font-size "14px"}}
        (str (js/Math.round (* 100 progress)) "%")]])))

;; ============================================================================
;; Video Playback
;; ============================================================================

(defn frame-display
  "Display a single frame from Uint8ClampedArray.

  Args:
    frame: Uint8ClampedArray (RGBA pixels)
    width: Frame width
    height: Frame height
    label: Display label"
  [frame width height label]
  (let [canvas-ref (atom nil)]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (when @canvas-ref
          (let [ctx (.getContext @canvas-ref "2d")
                image-data (js/ImageData. frame width height)]
            (.putImageData ctx image-data 0 0))))

      :component-did-update
      (fn [this]
        (when @canvas-ref
          (let [ctx (.getContext @canvas-ref "2d")
                image-data (js/ImageData. frame width height)]
            (.putImageData ctx image-data 0 0))))

      :reagent-render
      (fn [frame width height label]
        [:div.frame-display
         {:style {:display "inline-block"
                  :margin "10px"
                  :text-align "center"}}
         [:div {:style {:font-weight "bold"
                        :margin-bottom "5px"}}
          label]
         [:canvas
          {:ref #(reset! canvas-ref %)
           :width width
           :height height
           :style {:border "1px solid #ccc"
                   :display "block"}}]])})))

(defn side-by-side-player
  "Side-by-side video player showing original vs. magnified.

  Subscribes to:
    ::state/magnification/original-frames
    ::state/magnification/magnified-frames
    ::state/magnification/playback-state
    ::state/magnification/metadata

  Controls:
    - Play/Pause
    - Frame slider
    - Toggle view mode (original/magnified/side-by-side)"
  []
  (let [original-frames @(rf/subscribe [::state/magnification/original-frames])
        magnified-frames @(rf/subscribe [::state/magnification/magnified-frames])
        playback @(rf/subscribe [::state/magnification/playback-state])
        metadata @(rf/subscribe [::state/magnification/metadata])]

    (when (and original-frames magnified-frames metadata playback)
      (let [{:keys [playing? current-frame-index view-mode]} playback
            {:keys [width height]} metadata]
        [:div.side-by-side-player
         {:style {:padding "20px"
                  :background "#fafafa"
                  :border-radius "8px"}}

         [:div.video-controls
          {:style {:margin-bottom "20px"
                   :display "flex"
                   :gap "10px"
                   :align-items "center"}}

          ;; Play/Pause button
          [:button
           {:on-click #(rf/dispatch [::state/magnification/toggle-playback])
            :style {:padding "10px 20px"
                    :font-size "16px"
                    :cursor "pointer"}}
           (if playing? "⏸ Pause" "▶ Play")]

          ;; View mode toggle
          [:select
           {:value (name view-mode)
            :on-change #(rf/dispatch [::state/magnification/set-view-mode
                                      (keyword (.-value (.-target %)))])
            :style {:padding "10px"
                    :font-size "14px"}}
           [:option {:value "original"} "Original Only"]
           [:option {:value "magnified"} "Magnified Only"]
           [:option {:value "side-by-side"} "Side-by-Side"]]

          ;; Frame counter
          [:div {:style {:margin-left "auto"
                         :font-family "monospace"}}
           (str "Frame " (inc current-frame-index) " / " (count original-frames))]]

         ;; Frame slider
         [:input
          {:type "range"
           :min 0
           :max (dec (count original-frames))
           :value current-frame-index
           :on-change #(rf/dispatch [::state/magnification/seek-frame
                                     (js/parseInt (.-value (.-target %)))])
           :style {:width "100%"
                   :margin-bottom "20px"}}]

         ;; Video display
         [:div.video-display
          {:style {:display "flex"
                   :justify-content "center"
                   :gap "20px"}}

          (when (or (= view-mode :original) (= view-mode :side-by-side))
            [frame-display
             (nth original-frames current-frame-index)
             width height
             "Original"])

          (when (or (= view-mode :magnified) (= view-mode :side-by-side))
            [frame-display
             (nth magnified-frames current-frame-index)
             width height
             "Magnified (25×)"])]]))))

;; ============================================================================
;; Main Magnification View
;; ============================================================================

(defn magnification-view
  "Main magnification view.

  Workflow:
  1. Load first frame for ROI selection
  2. Select ROI on first frame
  3. Click 'Start Magnification' to begin processing
  4. Show progress
  5. Display side-by-side comparison"
  []
  (let [session @(rf/subscribe [::state/current-session])
        first-frame-data @(rf/subscribe [::state/magnification/first-frame])
        roi @(rf/subscribe [::state/magnification/roi])
        processing? @(rf/subscribe [::state/magnification/processing?])
        magnified-frames @(rf/subscribe [::state/magnification/magnified-frames])
        error @(rf/subscribe [::state/magnification/error])]

    [:div.magnification-view
     {:style {:padding "20px"}}

     [:h2 "Eulerian Video Magnification"]

     [:p {:style {:color "#666"
                  :margin-bottom "20px"}}
      "Amplify subtle breathing motion to make it visible."]

     ;; Error display
     (when error
       [:div.error-message
        {:style {:padding "15px"
                 :margin-bottom "20px"
                 :background "#ffebee"
                 :border "1px solid #f44336"
                 :border-radius "4px"
                 :color "#c62828"}}
        [:strong "Error: "] error])

     ;; Load first frame button
     (when (and session (not first-frame-data))
       [:div.load-frame
        {:style {:margin-bottom "30px"}}
        [:button
         {:on-click #(rf/dispatch [::state/magnification/load-first-frame
                                   (:session/id session)])
          :style {:padding "15px 30px"
                  :font-size "16px"
                  :background "#2196F3"
                  :color "white"
                  :border "none"
                  :border-radius "4px"
                  :cursor "pointer"}}
         "📹 Load Video"]])

     ;; Step 1: ROI Selection
     (when (and first-frame-data (not magnified-frames))
       (let [{:keys [frame width height]} first-frame-data]
         [:div.roi-selection
          {:style {:margin-bottom "30px"}}

          [:h3 "Step 1: Select Region of Interest"]
          [:p {:style {:color "#666"
                       :margin-bottom "10px"}}
           "Drag a rectangle around your chest area."]

          [roi-selector frame width height]

          (when roi
            [:button
             {:on-click #(rf/dispatch [::state/magnification/start
                                       (:session/id session)
                                       25.0 ;; gain
                                       false]) ;; blur?
              :disabled processing?
              :style {:margin-top "20px"
                      :padding "15px 30px"
                      :font-size "16px"
                      :background "#4CAF50"
                      :color "white"
                      :border "none"
                      :border-radius "4px"
                      :cursor (if processing? "not-allowed" "pointer")
                      :opacity (if processing? 0.5 1)}}
             "🔬 Start Magnification"])]))

     ;; Step 2: Processing
     (when processing?
       [:div.processing
        {:style {:margin "30px 0"}}
        [magnification-progress]])

     ;; Step 3: Results
     (when magnified-frames
       [:div.results
        {:style {:margin-top "30px"}}

        [:h3 "Results"]
        [:p {:style {:color "#666"
                     :margin-bottom "20px"}}
         "Compare original vs. magnified breathing motion."]

        [side-by-side-player]

        ;; Reset button
        [:button
         {:on-click #(rf/dispatch [::state/magnification/reset])
          :style {:margin-top "20px"
                  :padding "10px 20px"
                  :font-size "14px"
                  :background "#757575"
                  :color "white"
                  :border "none"
                  :border-radius "4px"
                  :cursor "pointer"}}
         "🔄 Start New Magnification"]])]))
