(ns combatsys.renderer.video-capture
  "Video capture and display component with MediaPipe pose estimation.

   Philosophy (Reagent + Side Effects):
   - Lifecycle methods for camera init/cleanup
   - Frame capture loop using requestAnimationFrame
   - MediaPipe pose estimation integrated into capture loop
   - FPS monitoring for performance diagnostics
   - Clean separation: rendering (pure) vs capture (imperative)"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [combatsys.renderer.camera :as camera]
            [combatsys.renderer.mediapipe :as mediapipe]
            [combatsys.renderer.state :as state]
            [combatsys.shared.pose :as pose]))

;; ============================================================
;; FPS COUNTER (Pure)
;; ============================================================

(defn calculate-fps
  "Calculate FPS from frame timestamps.
   Pure function."
  [frame-times]
  (if (< (count frame-times) 2)
    0
    (let [time-span (- (last frame-times) (first frame-times))
          frame-count (dec (count frame-times))
          fps (if (pos? time-span)
                (/ (* frame-count 1000) time-span)
                0)]
      (.round js/Math fps))))

;; ============================================================
;; VIDEO DISPLAY COMPONENT
;; ============================================================

(defn video-feed
  "Live video feed component with camera controls.

   Props:
     :on-frame-captured - Callback fn when frame is captured
     :capture-enabled?  - Boolean, should we capture frames?
     :target-fps        - Target frame rate for capture (default 15)"
  [{:keys [on-frame-captured capture-enabled? target-fps]
    :or {target-fps 15}}]

  (let [;; Local state
        video-ref (r/atom nil)
        canvas-ref (r/atom nil)
        camera-handle (r/atom nil)
        frame-count (r/atom 0)
        frame-times (r/atom [])
        current-fps (r/atom 0)
        error-msg (r/atom nil)
        camera-active? (r/atom false)

        ;; Frame skip calculation (process every Nth frame)
        frame-skip (atom (max 1 (quot 30 target-fps)))

        ;; Cleanup function
        cleanup-camera! (fn []
                         (when @camera-handle
                           (camera/release-camera! @camera-handle)
                           (reset! camera-handle nil)
                           (reset! camera-active? false)
                           (js/console.log "Camera cleaned up")))

        ;; Camera initialization with MediaPipe detector
        start-camera! (fn []
                       (-> (camera/init-camera! {:width 640 :height 480 :fps 30})
                           (.then (fn [handle]
                                    (reset! camera-handle handle)
                                    (reset! camera-active? true)
                                    (reset! error-msg nil)

                                    ;; Mount video to display
                                    (when @video-ref
                                      (set! (.-srcObject @video-ref)
                                            (:stream handle)))

                                    ;; Log camera info
                                    (camera/log-camera-info handle)

                                    ;; Dispatch camera event
                                    (rf/dispatch [::state/camera-started handle])

                                    ;; Initialize MediaPipe detector
                                    (rf/dispatch [::state/detector-initializing])
                                    (mediapipe/init-detector!)))

                           (.then (fn [detector]
                                    (js/console.log "MediaPipe detector ready")
                                    (rf/dispatch [::state/detector-ready])
                                    (mediapipe/log-detector-info)))

                           (.catch (fn [error]
                                     (let [msg (if (= (:error/type error) :initialization-failed)
                                                (mediapipe/pose-error-message error)
                                                (camera/camera-error-message error))]
                                       (reset! error-msg msg)
                                       (js/console.error "Startup failed:" msg)
                                       (if (= (:error/type error) :initialization-failed)
                                         (rf/dispatch [::state/detector-error error])
                                         (rf/dispatch [::state/camera-error error])))))))

        ;; Frame capture loop with pose estimation
        capture-loop! (fn capture-loop! []
                       (when @camera-active?
                         ;; Update FPS counter
                         (let [now (.now js/performance)]
                           (swap! frame-times #(take-last 30 (conj % now)))
                           (reset! current-fps (calculate-fps @frame-times)))

                         ;; Capture frame and estimate pose (with frame skipping)
                         (when (and capture-enabled?
                                   (zero? (mod @frame-count @frame-skip)))
                           (when-let [video-elem @video-ref]
                             (when-let [frame (camera/capture-frame! video-elem)]
                               ;; Estimate pose from frame
                               (when (mediapipe/detector-ready?)
                                 (-> (mediapipe/estimate-pose! (:canvas frame))
                                     (.then (fn [raw-pose]
                                              (if raw-pose
                                                (let [;; Enhance pose with angles (pure, <1ms)
                                                      enhanced-pose (pose/enhance-pose-with-angles
                                                                     raw-pose
                                                                     {:measure-time? true})]
                                                  ;; Pose detected - dispatch enhanced pose to state
                                                  (rf/dispatch [::state/pose-detected enhanced-pose])
                                                  ;; Call callback with frame + enhanced pose
                                                  (when on-frame-captured
                                                    (on-frame-captured (assoc frame :pose enhanced-pose))))
                                                ;; No pose detected
                                                (do
                                                  (rf/dispatch [::state/no-pose-detected])
                                                  ;; Still call callback with frame
                                                  (when on-frame-captured
                                                    (on-frame-captured frame))))))
                                     (.catch (fn [error]
                                               (js/console.warn "Pose estimation error:" error)))))

                               ;; Dispatch frame captured
                               (rf/dispatch [::state/camera-frame-captured frame]))))

                         ;; Increment counter
                         (swap! frame-count inc)

                         ;; Continue loop
                         (js/requestAnimationFrame capture-loop!)))]

    ;; Reagent component with lifecycle
    (r/create-class
     {:display-name "video-feed"

      :component-did-mount
      (fn [this]
        (js/console.log "Video feed component mounted")
        ;; Video element is managed by Reagent, just store ref
        (let [node (r/dom-node this)
              video-elem (.querySelector node "video")]
          (reset! video-ref video-elem)))

      :component-will-unmount
      (fn []
        (js/console.log "Video feed component unmounting")
        (cleanup-camera!))

      :reagent-render
      (fn [{:keys [on-frame-captured capture-enabled? target-fps]}]
        [:div {:style {:display "flex"
                       :flex-direction "column"
                       :gap "10px"}}

         ;; Error message
         (when @error-msg
           [:div {:style {:padding "10px"
                         :background-color "#ffebee"
                         :color "#c62828"
                         :border-radius "4px"
                         :border "1px solid #ef5350"}}
            [:strong "Camera Error: "]
            @error-msg])

         ;; Camera controls
         [:div {:style {:display "flex"
                       :gap "10px"
                       :align-items "center"}}

          (if @camera-active?
            [:button
             {:on-click cleanup-camera!
              :style {:padding "8px 16px"
                     :background-color "#f44336"
                     :color "white"
                     :border "none"
                     :border-radius "4px"
                     :cursor "pointer"}}
             "Stop Camera"]

            [:button
             {:on-click (fn []
                         (start-camera!)
                         (js/setTimeout #(capture-loop!) 100))
              :style {:padding "8px 16px"
                     :background-color "#4CAF50"
                     :color "white"
                     :border "none"
                     :border-radius "4px"
                     :cursor "pointer"}}
             "Start Camera"])

          ;; FPS display
          [:div {:style {:font-size "12px"
                        :color "#666"}}
           (str "FPS: " @current-fps " | Frames: " @frame-count)]

          ;; Pose detector status
          (let [detector-status @(rf/subscribe [::state/detector-status])
                pose-count @(rf/subscribe [::state/pose-count])]
            [:div {:style {:font-size "11px"
                          :color (case detector-status
                                   :ready "#4CAF50"
                                   :loading "#FF9800"
                                   :error "#f44336"
                                   "#999")
                          :font-weight "bold"}}
             (str "Pose: " (name detector-status)
                  (when (= detector-status :ready)
                    (str " (" pose-count ")")))])

          ;; Capture indicator
          (when (and capture-enabled? @camera-active?)
            [:div {:style {:width "12px"
                          :height "12px"
                          :border-radius "50%"
                          :background-color "#f44336"
                          :animation "blink 1s infinite"}}])]

         ;; Video element
         [:div {:style {:position "relative"
                       :background-color "#000"
                       :border-radius "8px"
                       :overflow "hidden"
                       :width "640px"
                       :height "480px"}}

          [:video
           {:autoPlay true
            :playsInline true
            :muted true
            :style {:width "100%"
                   :height "100%"
                   :object-fit "cover"}}]

          ;; Overlay for skeleton (future)
          [:canvas
           {:ref #(reset! canvas-ref %)
            :width 640
            :height 480
            :style {:position "absolute"
                   :top 0
                   :left 0
                   :pointer-events "none"}}]]

         ;; Camera info
         (when (and @camera-active? @camera-handle)
           [:div {:style {:font-size "12px"
                         :color "#666"
                         :padding "8px"
                         :background-color "#f5f5f5"
                         :border-radius "4px"}}
            [:div (str "Resolution: " (:width @camera-handle) "x" (:height @camera-handle))]
            [:div (str "Capture: " (if capture-enabled? "Enabled" "Paused"))]])])})))

;; ============================================================
;; CAMERA SELECTOR COMPONENT
;; ============================================================

(defn camera-selector
  "Dropdown to select from available cameras."
  [{:keys [on-camera-selected]}]
  (let [cameras (r/atom [])
        selected-id (r/atom nil)
        loading? (r/atom true)]

    ;; Load cameras on mount
    (r/create-class
     {:component-did-mount
      (fn []
        (-> (camera/list-cameras!)
            (.then (fn [camera-list]
                     (reset! cameras camera-list)
                     (reset! loading? false)
                     (when (seq camera-list)
                       (reset! selected-id (:device-id (first camera-list))))))))

      :reagent-render
      (fn [{:keys [on-camera-selected]}]
        [:div {:style {:display "flex"
                      :gap "10px"
                      :align-items "center"}}
         [:label {:style {:font-size "14px"
                         :font-weight "bold"}}
          "Camera:"]

         (if @loading?
           [:span {:style {:font-size "12px" :color "#666"}}
            "Loading cameras..."]

           (if (empty? @cameras)
             [:span {:style {:font-size "12px" :color "#f44336"}}
              "No cameras found"]

             [:select
              {:value (or @selected-id "")
               :on-change (fn [e]
                           (let [id (.. e -target -value)]
                             (reset! selected-id id)
                             (when on-camera-selected
                               (on-camera-selected id))))
               :style {:padding "6px 12px"
                      :border "1px solid #ddd"
                      :border-radius "4px"
                      :font-size "14px"}}

              (for [cam @cameras]
                ^{:key (:device-id cam)}
                [:option
                 {:value (:device-id cam)}
                 (or (:label cam) "Unknown Camera")])]))])})))
