(ns combatsys.renderer.camera
  "Camera capture via getUserMedia API.

   Philosophy (Imperative Shell):
   - All camera I/O side effects isolated here
   - Returns promises for async operations
   - Graceful error handling
   - Clear function naming with ! suffix for side effects

   This is the boundary between hardware and our functional core."
  (:require [clojure.string :as str]))

;; ============================================================
;; CAMERA ENUMERATION
;; ============================================================

(defn list-cameras!
  "Enumerate available video input devices.

   Returns: Promise<[{:device-id string :label string}]>
   Side effect: Accesses hardware via navigator.mediaDevices

   Example:
   (-> (list-cameras!)
       (.then (fn [cameras]
                (println \"Found cameras:\" cameras))))"
  []
  (-> js/navigator.mediaDevices
      .enumerateDevices
      (.then (fn [devices]
               (->> (array-seq devices)
                    (filter #(= (.-kind %) "videoinput"))
                    (mapv (fn [device]
                            {:device-id (.-deviceId device)
                             :label (.-label device)
                             :kind (.-kind device)})))))))

;; ============================================================
;; CAMERA INITIALIZATION
;; ============================================================

(defn init-camera!
  "Initialize camera with specified constraints.

   Args:
     constraints - Map with optional keys:
       :device-id - Specific camera ID (from list-cameras!)
       :width     - Preferred width (default 640)
       :height    - Preferred height (default 480)
       :fps       - Preferred frame rate (default 30)

   Returns: Promise<{:stream MediaStream :video-element DOMElement}>
   Side effect: Requests camera permission, accesses hardware

   Example:
   (-> (init-camera! {:width 1280 :height 720 :fps 30})
       (.then (fn [camera-handle]
                (println \"Camera ready\")))
       (.catch (fn [error]
                 (println \"Camera error:\" error))))"
  ([]
   (init-camera! {}))

  ([{:keys [device-id width height fps]
     :or {width 640 height 480 fps 30}}]
   (let [constraints (clj->js
                      {:video (cond-> {:width {:ideal width}
                                      :height {:ideal height}
                                      :frameRate {:ideal fps}}
                                device-id
                                (assoc :deviceId {:exact device-id}))})]
     (-> js/navigator.mediaDevices
         (.getUserMedia constraints)
         (.then (fn [stream]
                  ;; Create video element to receive stream
                  (let [video (.createElement js/document "video")]
                    (set! (.-srcObject video) stream)
                    (set! (.-autoplay video) true)
                    (set! (.-playsInline video) true) ;; iOS support

                    ;; Wait for video to be ready
                    (js/Promise.
                     (fn [resolve reject]
                       (.addEventListener video "loadedmetadata"
                                        (fn []
                                          (.play video)
                                          (resolve {:stream stream
                                                   :video-element video
                                                   :width (.-videoWidth video)
                                                   :height (.-videoHeight video)
                                                   :fps fps}))))))))
         (.catch (fn [error]
                   (js/console.error "Camera initialization failed:" error)
                   (js/Promise.reject
                    {:error/type :camera-init-failed
                     :error/message (.-message error)
                     :error/name (.-name error)})))))))

;; ============================================================
;; FRAME CAPTURE
;; ============================================================

(defn capture-frame!
  "Capture current frame from video element to canvas.

   Args:
     video-element - DOM video element from init-camera!

   Returns: {:canvas DOMElement
             :image-data ImageData
             :timestamp-ms number
             :width number
             :height number}

   Side effect: Creates canvas, draws video frame

   Note: ImageData is mutable and large. Don't store directly in state.
         Use canvas as reference or extract pose immediately."
  [video-element]
  (when video-element
    (try
      (let [width (.-videoWidth video-element)
            height (.-videoHeight video-element)
            canvas (.createElement js/document "canvas")
            ctx (.getContext canvas "2d")]

        ;; Set canvas size
        (set! (.-width canvas) width)
        (set! (.-height canvas) height)

        ;; Draw current video frame
        (.drawImage ctx video-element 0 0 width height)

        ;; Get image data
        (let [image-data (.getImageData ctx 0 0 width height)]
          {:canvas canvas
           :image-data image-data
           :timestamp-ms (.now js/Date)
           :width width
           :height height}))

      (catch js/Error e
        (js/console.error "Frame capture failed:" e)
        nil))))

;; ============================================================
;; CAMERA RELEASE
;; ============================================================

(defn release-camera!
  "Release camera resources and stop all tracks.

   Args:
     camera-handle - Handle returned from init-camera!

   Returns: nil
   Side effect: Stops camera stream, releases hardware

   Always call this when done with camera to free resources."
  [{:keys [stream video-element]}]
  (when stream
    (try
      ;; Stop all tracks
      (doseq [track (array-seq (.getTracks stream))]
        (.stop track))

      ;; Clear video element
      (when video-element
        (set! (.-srcObject video-element) nil))

      (js/console.log "Camera released")

      (catch js/Error e
        (js/console.error "Camera release failed:" e))))
  nil)

;; ============================================================
;; CAMERA CAPABILITIES
;; ============================================================

(defn get-camera-capabilities
  "Get capabilities of the active camera.

   Args:
     camera-handle - Handle from init-camera!

   Returns: Map with camera capabilities (resolution, fps, etc.)
   Side effect: Queries hardware capabilities"
  [{:keys [stream]}]
  (when stream
    (try
      (let [video-track (first (array-seq (.getVideoTracks stream)))
            capabilities (when video-track
                          (.getCapabilities video-track))
            settings (when video-track
                      (.getSettings video-track))]
        {:capabilities (js->clj capabilities :keywordize-keys true)
         :settings (js->clj settings :keywordize-keys true)})
      (catch js/Error e
        (js/console.error "Failed to get capabilities:" e)
        nil))))

;; ============================================================
;; ERROR HANDLING HELPERS
;; ============================================================

(defn camera-error-message
  "Convert camera error to user-friendly message.
   Pure function."
  [error]
  (case (:error/name error)
    "NotAllowedError"
    "Camera permission denied. Please allow camera access in your browser settings."

    "NotFoundError"
    "No camera found. Please connect a camera and try again."

    "NotReadableError"
    "Camera is in use by another application. Please close other apps using the camera."

    "OverconstrainedError"
    "Camera doesn't support the requested resolution or frame rate."

    "TypeError"
    "Camera API not available. Please use a modern browser."

    ;; Default
    (str "Camera error: " (or (:error/message error) "Unknown error"))))

;; ============================================================
;; DIAGNOSTIC UTILITIES
;; ============================================================

(defn check-camera-support
  "Check if camera API is supported in this environment.
   Pure function (checks global object)."
  []
  (and (exists? js/navigator)
       (exists? js/navigator.mediaDevices)
       (exists? js/navigator.mediaDevices.getUserMedia)))

(defn log-camera-info
  "Log camera handle information for debugging.
   Side effect: Console output"
  [camera-handle]
  (when camera-handle
    (js/console.group "Camera Info")
    (js/console.log "Width:" (:width camera-handle))
    (js/console.log "Height:" (:height camera-handle))
    (js/console.log "FPS:" (:fps camera-handle))
    (js/console.log "Video element:" (:video-element camera-handle))
    (when-let [caps (get-camera-capabilities camera-handle)]
      (js/console.log "Capabilities:" (clj->js caps)))
    (js/console.groupEnd)))
