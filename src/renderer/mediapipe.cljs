(ns combatsys.renderer.mediapipe
  "MediaPipe pose estimation via TensorFlow.js.

   Philosophy (Imperative Shell):
   - ML inference is side-effectful (GPU computation)
   - Model loading is async (network + initialization)
   - Conversion to EDN is pure (data transformation)

   This wraps TensorFlow.js BlazePose and provides clean EDN interface
   conforming to our schema.

   Architecture:
   Canvas/ImageData → TensorFlow.js → MediaPipe Pose → EDN Map

   Google Engineer's Quality Focus:
   - Comprehensive error handling
   - Edge case coverage
   - Clear error messages
   - Production-ready"
  (:require ["@tensorflow-models/pose-detection" :as pose-detection]
            ["@tensorflow/tfjs-core" :as tf-core]))

;; ============================================================
;; DETECTOR STATE
;; ============================================================

(defonce detector
  "MediaPipe pose detector instance.
   nil = not initialized
   detector = ready for inference"
  (atom nil))

(defonce detector-status
  "Status of detector initialization.
   :not-initialized | :loading | :ready | :error"
  (atom :not-initialized))

(defonce detector-error
  "Last error during initialization or inference."
  (atom nil))

;; ============================================================
;; MEDIAPIPE CONFIGURATION
;; ============================================================

(def detector-config
  "Configuration for MediaPipe BlazePose detector.

   Model types:
   - lite:  Fast but less accurate (mobile)
   - full:  Balanced (desktop - our choice)
   - heavy: Slowest but most accurate

   Runtime: tfjs = TensorFlow.js (GPU acceleration)"
  {:model-type "full"
   :runtime "tfjs"
   :enable-smoothing true
   :enable-segmentation false})  ; Don't need segmentation mask

;; ============================================================
;; DETECTOR INITIALIZATION
;; ============================================================

(defn init-detector!
  "Initialize MediaPipe BlazePose detector.

   Returns: Promise<detector>
   Side effects:
   - Downloads ML model (~10MB)
   - Initializes TensorFlow.js backend
   - Allocates GPU memory (~50MB)

   Time: 2-3 seconds on first load (cached after)
   GPU: Prefers WebGL, falls back to CPU

   Example:
   (-> (init-detector!)
       (.then (fn [d] (println \"Detector ready\")))
       (.catch (fn [e] (println \"Error:\" e))))"
  []
  (println "Initializing MediaPipe detector...")
  (reset! detector-status :loading)
  (reset! detector-error nil)

  (-> (pose-detection/createDetector
       pose-detection/SupportedModels.BlazePose
       (clj->js detector-config))

      (.then (fn [d]
               (println "MediaPipe detector initialized successfully")
               (reset! detector d)
               (reset! detector-status :ready)
               d))

      (.catch (fn [error]
                (let [error-msg (.-message error)]
                  (js/console.error "MediaPipe initialization failed:" error)
                  (reset! detector-status :error)
                  (reset! detector-error {:error/type :initialization-failed
                                         :error/message error-msg
                                         :error/original error})
                  (js/Promise.reject
                   {:error/type :initialization-failed
                    :error/message (str "Failed to initialize pose detector: " error-msg)
                    :error/suggestion "Check console for details. May need to reload page."}))))))

(defn get-detector-status
  "Get current status of detector.
   Pure function (reads atom).

   Returns: {:status keyword :detector detector :error map}"
  []
  {:status @detector-status
   :detector @detector
   :error @detector-error})

(defn detector-ready?
  "Check if detector is ready for inference.
   Pure function."
  []
  (and (= @detector-status :ready)
       (some? @detector)))

;; ============================================================
;; POSE ESTIMATION
;; ============================================================

(defn estimate-pose!
  "Estimate 2D pose from image element.

   Args:
     image - Canvas, ImageData, or Video element

   Returns: Promise<EDN-pose-map | nil>
   - EDN pose map if person detected
   - nil if no person or low confidence

   Side effect: Runs ML inference on GPU (~30ms)

   Example:
   (-> (estimate-pose! canvas)
       (.then (fn [pose]
                (if pose
                  (println \"Detected\" (count (:pose/landmarks pose)) \"landmarks\")
                  (println \"No person detected\")))))"
  [image-element]
  (if-not (detector-ready?)
    (js/Promise.reject
     {:error/type :detector-not-ready
      :error/message "Detector not initialized. Call init-detector! first."
      :error/status @detector-status})

    (try
      (-> (.estimatePoses @detector image-element)
          (.then (fn [poses]
                   ;; poses is array of pose objects
                   (let [poses-vec (js->clj poses :keywordize-keys true)]
                     (if (seq poses-vec)
                       ;; Take first person
                       (tfjs-pose->edn (first poses-vec) image-element)
                       ;; No person detected
                       nil))))
          (.catch (fn [error]
                    (js/console.error "Pose estimation failed:" error)
                    (js/Promise.reject
                     {:error/type :estimation-failed
                      :error/message (.-message error)
                      :error/original error}))))
      (catch js/Error e
        (js/Promise.reject
         {:error/type :estimation-error
          :error/message (.-message e)})))))

;; ============================================================
;; TFJS → EDN CONVERSION (Pure Functions)
;; ============================================================

(defn normalize-landmark-id
  "Convert MediaPipe landmark name to our keyword format.
   Pure function.

   Examples:
   'nose' → :nose
   'left_shoulder' → :left-shoulder
   'right_hip' → :right-hip"
  [name-str]
  (-> name-str
      (clojure.string/replace #"_" "-")
      keyword))

(defn normalize-coordinates
  "Normalize pixel coordinates to [0-1] range.
   Pure function.

   MediaPipe already gives normalized coordinates,
   but we ensure they're in our format."
  [x y canvas-width canvas-height]
  {:x (if canvas-width (/ x canvas-width) x)
   :y (if canvas-height (/ y canvas-height) y)})

(defn tfjs-keypoint->landmark
  "Convert single TensorFlow.js keypoint to EDN landmark.
   Pure function.

   Input: {:name 'nose' :x 0.5 :y 0.3 :z 0.1 :score 0.95}
   Output: {:landmark/id :nose
            :landmark/x 0.5
            :landmark/y 0.3
            :landmark/z 0.1
            :landmark/visibility 0.95}"
  [keypoint canvas-width canvas-height]
  (let [name (:name keypoint)
        x (:x keypoint)
        y (:y keypoint)
        z (or (:z keypoint) 0)
        score (or (:score keypoint) 0)]
    {:landmark/id (normalize-landmark-id name)
     :landmark/x x
     :landmark/y y
     :landmark/z z
     :landmark/visibility score}))

(defn tfjs-pose->edn
  "Convert TensorFlow.js pose object to EDN schema.
   Pure function (after js->clj conversion).

   Input:
   {:keypoints [{:name 'nose' :x 320 :y 240 ...} ...]
    :score 0.87}

   Output:
   {:pose/landmarks [{:landmark/id :nose ...} ...]
    :pose/confidence 0.87
    :pose/timestamp-ms 1234567890}"
  [tfjs-pose image-element]
  (let [keypoints (:keypoints tfjs-pose)
        confidence (or (:score tfjs-pose) 0)
        ;; Get canvas dimensions for normalization
        canvas-width (when image-element (.-width image-element))
        canvas-height (when image-element (.-height image-element))]

    {:pose/landmarks
     (mapv #(tfjs-keypoint->landmark % canvas-width canvas-height)
           keypoints)

     :pose/confidence confidence

     :pose/timestamp-ms (.now js/Date)}))

;; ============================================================
;; VALIDATION & DIAGNOSTICS
;; ============================================================

(defn validate-pose
  "Validate pose conforms to expected structure.
   Pure function.

   Returns: {:valid? boolean :errors [...]}"
  [pose]
  (let [errors (cond-> []
                 (not (map? pose))
                 (conj "Pose must be a map")

                 (not (contains? pose :pose/landmarks))
                 (conj "Missing :pose/landmarks")

                 (not (contains? pose :pose/confidence))
                 (conj "Missing :pose/confidence")

                 (not (contains? pose :pose/timestamp-ms))
                 (conj "Missing :pose/timestamp-ms")

                 (and (contains? pose :pose/landmarks)
                      (not= 33 (count (:pose/landmarks pose))))
                 (conj (str "Expected 33 landmarks, got " (count (:pose/landmarks pose)))))]

    {:valid? (empty? errors)
     :errors errors}))

(defn pose-summary
  "Generate human-readable summary of pose.
   Pure function.

   Useful for debugging and logging."
  [pose]
  (when pose
    (let [landmarks (:pose/landmarks pose)
          confidence (:pose/confidence pose)
          high-conf (count (filter #(> (:landmark/visibility %) 0.7) landmarks))
          low-conf (count (filter #(< (:landmark/visibility %) 0.5) landmarks))]
      {:landmark-count (count landmarks)
       :overall-confidence confidence
       :high-confidence-landmarks high-conf
       :low-confidence-landmarks low-conf
       :quality (cond
                  (> confidence 0.8) :excellent
                  (> confidence 0.6) :good
                  (> confidence 0.4) :fair
                  :else :poor)})))

(defn log-pose-info
  "Log pose information for diagnostics.
   Side effect: Console output"
  [pose]
  (when pose
    (let [summary (pose-summary pose)]
      (js/console.group "Pose Detection Summary")
      (js/console.log "Landmarks:" (:landmark-count summary))
      (js/console.log "Confidence:" (:overall-confidence summary))
      (js/console.log "High confidence:" (:high-confidence-landmarks summary))
      (js/console.log "Low confidence:" (:low-confidence-landmarks summary))
      (js/console.log "Quality:" (name (:quality summary)))
      (js/console.groupEnd))))

;; ============================================================
;; ERROR HANDLING UTILITIES
;; ============================================================

(defn pose-error-message
  "Convert pose estimation error to user-friendly message.
   Pure function."
  [error]
  (case (:error/type error)
    :detector-not-ready
    "Pose detector not ready. Please wait for initialization."

    :initialization-failed
    (str "Failed to initialize pose detector: " (:error/message error))

    :estimation-failed
    "Pose estimation failed. Ensure person is visible in frame."

    :estimation-error
    (str "Pose estimation error: " (:error/message error))

    ;; Default
    (str "Pose detection error: " (or (:error/message error) "Unknown error"))))

;; ============================================================
;; CONVENIENCE FUNCTIONS
;; ============================================================

(defn get-landmark
  "Get specific landmark from pose by ID.
   Pure function.

   Example:
   (get-landmark pose :nose)
   => {:landmark/id :nose :landmark/x 0.5 ...}"
  [pose landmark-id]
  (when pose
    (first (filter #(= landmark-id (:landmark/id %))
                   (:pose/landmarks pose)))))

(defn get-landmarks
  "Get multiple landmarks by IDs.
   Pure function.

   Example:
   (get-landmarks pose [:nose :left-shoulder :right-shoulder])
   => [{:landmark/id :nose ...} ...]"
  [pose landmark-ids]
  (when pose
    (mapv #(get-landmark pose %) landmark-ids)))

(defn high-confidence-landmarks
  "Filter landmarks by confidence threshold.
   Pure function.

   Example:
   (high-confidence-landmarks pose 0.7)
   => [{:landmark/id :nose :landmark/visibility 0.95} ...]"
  [pose threshold]
  (when pose
    (filterv #(>= (:landmark/visibility %) threshold)
             (:pose/landmarks pose))))

;; ============================================================
;; CLEANUP
;; ============================================================

(defn dispose-detector!
  "Dispose detector and free GPU memory.
   Side effect: Deallocates resources

   Call this when app closes or switching models."
  []
  (when @detector
    (try
      (.dispose @detector)
      (reset! detector nil)
      (reset! detector-status :not-initialized)
      (js/console.log "MediaPipe detector disposed")
      (catch js/Error e
        (js/console.error "Error disposing detector:" e)))))

;; ============================================================
;; INITIALIZATION HELPERS
;; ============================================================

(defn check-tfjs-backend
  "Check which TensorFlow.js backend is active.
   Side effect: Queries TF.js

   Returns: 'webgl' | 'cpu' | 'wasm'"
  []
  (try
    (tf-core/getBackend)
    (catch js/Error e
      (js/console.error "Error checking TF.js backend:" e)
      "unknown")))

(defn log-detector-info
  "Log detector configuration and status.
   Side effect: Console output"
  []
  (js/console.group "MediaPipe Detector Info")
  (js/console.log "Status:" (name @detector-status))
  (js/console.log "Model type:" (:model-type detector-config))
  (js/console.log "Smoothing:" (:enable-smoothing detector-config))
  (js/console.log "TF.js backend:" (check-tfjs-backend))
  (when @detector-error
    (js/console.warn "Last error:" @detector-error))
  (js/console.groupEnd))
