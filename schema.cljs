(ns combatsys.schema
  "Core EDN schema definitions for the motion analysis IR.
   All data in the system conforms to these structures.
   
   Philosophy (Rich Hickey):
   - Data is separate from identity
   - Schemas are documentation and validation
   - Simple, composable structures")

;; ============================================================
;; IDENTIFIERS & PRIMITIVES
;; ============================================================

(def landmark-ids
  "MediaPipe-style body landmark identifiers"
  #{:nose
    :left-eye :right-eye
    :left-ear :right-ear
    :mouth-left :mouth-right
    :left-shoulder :right-shoulder
    :left-elbow :right-elbow
    :left-wrist :right-wrist
    :left-pinky :right-pinky
    :left-index :right-index
    :left-thumb :right-thumb
    :left-hip :right-hip
    :left-knee :right-knee
    :left-ankle :right-ankle
    :left-heel :right-heel
    :left-foot-index :right-foot-index})

(def session-statuses
  #{:idle           ;; Not started
    :recording      ;; Currently capturing
    :processing     ;; Analyzing recorded data
    :complete       ;; Analysis finished
    :error})        ;; Something went wrong

;; ============================================================
;; LANDMARK: A single body keypoint
;; ============================================================

(defn landmark
  "Create a landmark map"
  [id x y z visibility]
  {:landmark/id id
   :landmark/x x          ;; Normalized 0-1 or world coords
   :landmark/y y
   :landmark/z z          ;; Depth (may be estimated)
   :landmark/visibility visibility}) ;; 0-1 confidence

;; ============================================================
;; POSE: Collection of landmarks at a point in time
;; ============================================================

(defn pose
  "Create a pose map from landmarks"
  [landmarks confidence]
  {:pose/landmarks landmarks
   :pose/confidence confidence
   :pose/timestamp-ms (.now js/Date)})

;; ============================================================
;; FRAME: A single timestep with pose and derived metrics
;; ============================================================

(defn frame
  "Create a frame map"
  [index timestamp-ms pose-data]
  {:frame/index index
   :frame/timestamp-ms timestamp-ms
   :frame/pose pose-data
   :frame/derived {}      ;; Computed angles, velocities, etc.
   :frame/events []})     ;; Detected events at this frame

;; ============================================================
;; SESSION: The top-level container
;; ============================================================

(defn new-session
  "Create a new session with default values"
  [user-id]
  {:session/id (random-uuid)
   :session/user-id user-id
   :session/created-at (js/Date.)
   :session/status :idle
   :session/duration-ms 0
   
   :session/camera
   {:camera/id "webcam-0"
    :camera/resolution [1920 1080]
    :camera/fps 30}
   
   :session/user
   {:user/height-cm 175
    :user/baseline-pose nil
    :user/learned-thresholds {}}
   
   :session/timeline []
   
   :session/analysis
   {:breathing nil
    :gait nil
    :posture nil
    :balance nil}})

;; ============================================================
;; ANALYSIS: Results from various analyzers
;; ============================================================

(defn breathing-analysis
  "Schema for breathing analysis results"
  [rate-bpm depth-score fatigue-windows]
  {:rate-bpm rate-bpm
   :depth-score depth-score
   :rhythm-regularity 0.88
   :fatigue-windows fatigue-windows
   :insights []})

(defn gait-analysis
  "Schema for gait analysis results"
  [step-length cadence symmetry]
  {:step-length-m step-length
   :cadence-steps-per-min cadence
   :symmetry-score symmetry
   :hip-knee-ankle-alignment :good
   :events []})

(defn posture-analysis
  "Schema for posture analysis results"
  [head-forward shoulder-imbalance spine-alignment]
  {:head-forward-cm head-forward
   :shoulder-imbalance-deg shoulder-imbalance
   :spine-alignment spine-alignment
   :overall-score 0.84})

;; ============================================================
;; EVENTS: Detected patterns or violations
;; ============================================================

(defn event
  "Create an event map"
  [type severity message]
  {:event/type type
   :event/severity severity
   :event/message message
   :event/timestamp-ms (.now js/Date)})

;; ============================================================
;; VALIDATION HELPERS
;; ============================================================

(defn valid-landmark? [lm]
  (and (contains? landmark-ids (:landmark/id lm))
       (number? (:landmark/x lm))
       (number? (:landmark/y lm))
       (number? (:landmark/z lm))
       (<= 0 (:landmark/visibility lm) 1)))

(defn valid-pose? [pose]
  (and (every? valid-landmark? (:pose/landmarks pose))
       (<= 0 (:pose/confidence pose) 1)))

(defn valid-frame? [frame]
  (and (integer? (:frame/index frame))
       (integer? (:frame/timestamp-ms frame))
       (valid-pose? (:frame/pose frame))))

(defn valid-session? [session]
  (and (uuid? (:session/id session))
       (contains? session-statuses (:session/status session))
       (vector? (:session/timeline session))
       (every? valid-frame? (:session/timeline session))))
