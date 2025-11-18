(ns combatsys.renderer.video
  "Video decoding and encoding utilities for magnification.

  Uses HTML5 VideoElement for frame extraction.")

;; ============================================================================
;; Video Decoding (HTML5 VideoElement)
;; ============================================================================

(defn decode-video!
  "Decode video file to frame sequence.

  Uses HTML5 VideoElement to seek through video and extract frames.

  Args:
    video-path: Path to video file (WebM, MP4)
    options: Map with keys:
      :fps - Target frames per second (default 15)
      :max-frames - Maximum frames to extract (default nil = all)
      :on-progress - Callback (fn [progress]) called during decode

  Returns:
    Promise<{:frames Vector :width Int :height Int :fps Int}>

  Example:
    (-> (decode-video! \"path/to/video.webm\" {:fps 15})
        (.then (fn [{:keys [frames width height fps]}]
                 (js/console.log \"Extracted\" (count frames) \"frames\"))))"
  [video-path & [{:keys [fps max-frames on-progress]
                  :or {fps 15
                       max-frames nil
                       on-progress (fn [_])}}]]

  (js/Promise.
   (fn [resolve reject]
     (try
       (let [video (js/document.createElement "video")
             canvas (js/document.createElement "canvas")
             ctx (.getContext canvas "2d")
             frames (atom [])]

         ;; Preload video (faster seeking)
         (set! (.-preload video) "auto")

         ;; Load video
         (set! (.-src video) video-path)

         ;; Wait for metadata
         (.addEventListener
          video "loadedmetadata"
          (fn []
            (try
              (let [width (.-videoWidth video)
                    height (.-videoHeight video)
                    duration (.-duration video)
                    frame-interval (/ 1.0 fps)
                    total-frames (js/Math.floor (/ duration frame-interval))
                    frames-to-extract (if max-frames
                                       (min max-frames total-frames)
                                       total-frames)]

                (js/console.log "Video metadata:"
                               "Size:" width "×" height
                               "Duration:" duration "s"
                               "Extracting" frames-to-extract "frames @" fps "fps")

                ;; Set canvas size
                (set! (.-width canvas) width)
                (set! (.-height canvas) height)

                ;; Recursive frame capture
                (letfn [(capture-frame! [frame-index]
                          (if (>= frame-index frames-to-extract)
                            ;; Done
                            (do
                              (js/console.log "Decoding complete:" (count @frames) "frames")
                              (on-progress 1.0)
                              (resolve {:frames @frames
                                       :width width
                                       :height height
                                       :fps fps}))

                            ;; Capture this frame
                            (let [current-time (* frame-index frame-interval)]
                              ;; Update progress
                              (when (zero? (mod frame-index 100))
                                (on-progress (/ frame-index frames-to-extract)))

                              ;; Seek to time
                              (set! (.-currentTime video) current-time)

                              ;; Wait for seek complete
                              (.addEventListener
                               video "seeked"
                               (fn []
                                 (try
                                   ;; Draw frame to canvas
                                   (.drawImage ctx video 0 0)

                                   ;; Get pixel data
                                   (let [image-data (.getImageData ctx 0 0 width height)
                                         ;; Copy data (don't store ImageData reference!)
                                         pixels (js/Uint8ClampedArray. (.-data image-data))]

                                     ;; Store frame
                                     (swap! frames conj pixels)

                                     ;; Next frame
                                     (capture-frame! (inc frame-index)))

                                   (catch js/Error e
                                     (reject e))))
                               #js {:once true}))))]

                  ;; Start capture
                  (capture-frame! 0)))

              (catch js/Error e
                (reject e)))))

         ;; Error handling
         (.addEventListener
          video "error"
          (fn [e]
            (reject (ex-info "Video decode error"
                            {:error (.-error video)
                             :message (str "Failed to load video: " video-path)})))))

       (catch js/Error e
         (reject e))))))

(defn get-first-frame!
  "Extract just the first frame from video (for ROI selection).

  Args:
    video-path: Path to video file

  Returns:
    Promise<{:frame Uint8ClampedArray :width Int :height Int}>"
  [video-path]
  (js/Promise.
   (fn [resolve reject]
     (try
       (let [video (js/document.createElement "video")
             canvas (js/document.createElement "canvas")
             ctx (.getContext canvas "2d")]

         (set! (.-src video) video-path)

         (.addEventListener
          video "loadedmetadata"
          (fn []
            (try
              (let [width (.-videoWidth video)
                    height (.-videoHeight video)]

                (set! (.-width canvas) width)
                (set! (.-height canvas) height)

                ;; Seek to start
                (set! (.-currentTime video) 0)

                (.addEventListener
                 video "seeked"
                 (fn []
                   (try
                     (.drawImage ctx video 0 0)
                     (let [image-data (.getImageData ctx 0 0 width height)
                           pixels (js/Uint8ClampedArray. (.-data image-data))]
                       (resolve {:frame pixels
                                :width width
                                :height height}))
                     (catch js/Error e
                       (reject e))))
                 #js {:once true}))

              (catch js/Error e
                (reject e)))))

         (.addEventListener
          video "error"
          (fn [e]
            (reject (ex-info "Failed to load video"
                            {:path video-path})))))

       (catch js/Error e
         (reject e))))))

;; ============================================================================
;; Video Encoding (Placeholder for LOD 3)
;; ============================================================================

(defn encode-video!
  "Encode frame sequence to video file.

  NOTE: For LOD 3 MVP, this is a placeholder.
  Users can view magnified frames in UI without saving to disk.

  Future implementation options:
  - MediaRecorder API (WebM only)
  - FFmpeg.wasm (full codec support)
  - Node.js child process (ffmpeg CLI)

  Args:
    frames: Vector of Uint8ClampedArrays (RGBA)
    width: Frame width
    height: Frame height
    fps: Frames per second
    output-path: Output file path

  Returns:
    Promise<String> - Path to encoded video"
  [frames width height fps output-path]
  (js/Promise.
   (fn [resolve reject]
     (js/console.warn "Video encoding not implemented in LOD 3 MVP")
     (js/console.log "Frames available in memory for playback:"
                    (count frames) "frames @" fps "fps")
     ;; For now, just resolve with path (no actual file created)
     (resolve output-path))))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn frames-to-canvas-sequence!
  "Convert frame sequence to canvas elements for display.

  Useful for debugging/visualization.

  Args:
    frames: Vector of Uint8ClampedArrays
    width: Frame width
    height: Frame height

  Returns:
    Vector of canvas elements"
  [frames width height]
  (mapv (fn [frame]
          (let [canvas (js/document.createElement "canvas")
                ctx (.getContext canvas "2d")]
            (set! (.-width canvas) width)
            (set! (.-height canvas) height)
            (.putImageData ctx (js/ImageData. frame width height) 0 0)
            canvas))
        frames))

(defn download-frame-as-image!
  "Download a single frame as PNG image.

  Args:
    frame: Uint8ClampedArray (RGBA pixels)
    width: Frame width
    height: Frame height
    filename: Output filename (e.g., \"frame.png\")

  Side effect: Triggers browser download"
  [frame width height filename]
  (let [canvas (js/document.createElement "canvas")
        ctx (.getContext canvas "2d")]
    (set! (.-width canvas) width)
    (set! (.-height canvas) height)
    (.putImageData ctx (js/ImageData. frame width height) 0 0)

    ;; Convert to blob and download
    (.toBlob canvas
             (fn [blob]
               (let [url (.createObjectURL js/URL blob)
                     link (js/document.createElement "a")]
                 (set! (.-href link) url)
                 (set! (.-download link) filename)
                 (.click link)
                 (.revokeObjectURL js/URL url))))))
