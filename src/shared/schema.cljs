(ns combatsys.schema
  "Core EDN schema definitions for the motion analysis IR.
   All data in the system conforms to these structures.

   Philosophy (Rich Hickey):
   - Data is separate from identity
   - Schemas are documentation and validation
   - Simple, composable structures

   NOTE: This system has TWO user representations:
   1. Session-embedded user (:session/user) - namespaced keywords (:user/height-cm)
   2. User profile (standalone entity) - un-namespaced keywords (:height-cm)
   This is intentional. Session-user is a cached subset for performance.
   User profile is the authoritative source, stored on disk."
  (:require [clojure.spec.alpha :as s]))

;; ============================================================
;; IDENTIFIERS & PRIMITIVES
;; ============================================================

(def landmark-ids
  "MediaPipe BlazePose 33 landmark identifiers.
   See: https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker"
  #{;; Face (5)
    :nose
    :left-eye-inner :left-eye :left-eye-outer
    :right-eye-inner :right-eye :right-eye-outer
    :left-ear :right-ear
    :mouth-left :mouth-right

    ;; Upper body (10)
    :left-shoulder :right-shoulder
    :left-elbow :right-elbow
    :left-wrist :right-wrist
    :left-pinky :right-pinky
    :left-index :right-index
    :left-thumb :right-thumb

    ;; Lower body (10)
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

;; ============================================================
;; LOD 5: USER CALIBRATION & PERSONALIZATION
;; ============================================================
;; Added: 2025-11-18
;; These schemas use UN-NAMESPACED keywords (different from session schemas)
;; Rationale: User profiles are standalone entities serialized to disk

;; -------- Calibration Session --------

(s/def ::calibration-type #{:t-pose :breathing :movement})
(s/def ::session-id uuid?)
(s/def ::created-at inst?)
(s/def ::duration-ms (s/and int? (complement neg?)))
(s/def ::timeline vector?)  ;; Vector of frames

(s/def ::calibration-session
  (s/keys :req-un [::calibration-type ::session-id ::created-at
                   ::duration-ms ::timeline]))

;; -------- Baseline Pose (from T-pose) --------

(s/def ::landmarks vector?)  ;; 33-element vector of landmarks
(s/def ::joint-distances
  (s/map-of keyword? pos?))
  ;; e.g., {:shoulder-width-cm 42.5 :arm-length-cm 68.0}

(s/def ::baseline-pose
  (s/keys :req-un [::landmarks ::joint-distances]))

;; -------- ROM Ranges --------

(s/def ::rom-range
  (s/tuple number? number?))  ;; [min-degrees max-degrees]

(s/def ::rom-ranges
  (s/map-of keyword? ::rom-range))
  ;; e.g., {:left-elbow [0 145] :right-shoulder-flexion [0 168]}

;; -------- Breathing Baseline --------

(s/def ::typical-rate-bpm (s/and number? pos?))
(s/def ::typical-depth (s/double-in :min 0.0 :max 1.0))
(s/def ::typical-rhythm-regularity (s/double-in :min 0.0 :max 1.0))

(s/def ::breathing-baseline
  (s/keys :req-un [::typical-rate-bpm ::typical-depth ::typical-rhythm-regularity]))

;; -------- Posture Baseline --------

(s/def ::typical-forward-head-cm number?)
(s/def ::typical-shoulder-imbalance-deg number?)

(s/def ::posture-baseline
  (s/keys :req-un [::typical-forward-head-cm ::typical-shoulder-imbalance-deg]))

;; -------- Learned Thresholds --------

(s/def ::fatigue-threshold (s/and number? pos?))
(s/def ::rate-alert-threshold (s/and number? pos?))

(s/def ::breathing-thresholds
  (s/keys :req-un [::fatigue-threshold ::rate-alert-threshold]))

(s/def ::forward-head-alert-cm (s/and number? pos?))
(s/def ::shoulder-imbalance-alert-deg (s/and number? pos?))

(s/def ::posture-thresholds
  (s/keys :req-un [::forward-head-alert-cm ::shoulder-imbalance-alert-deg]))

(s/def ::stability-alert-threshold (s/double-in :min 0.0 :max 1.0))

(s/def ::balance-thresholds
  (s/keys :req-un [::stability-alert-threshold]))

(s/def ::learned-thresholds
  (s/keys :req-un [::breathing-thresholds ::posture-thresholds ::balance-thresholds]))

;; -------- Complete User Profile --------

(s/def ::user-id uuid?)
(s/def ::height-cm (s/and number? pos?))
(s/def ::last-calibration-date inst?)
(s/def ::calibration-count nat-int?)

(s/def ::user-profile
  (s/keys :req-un [::user-id ::height-cm ::baseline-pose ::learned-thresholds
                   ::last-calibration-date ::calibration-count]
          :opt-un [::breathing-baseline ::posture-baseline ::rom-ranges]))

;; -------- Validation Functions --------

(defn valid-user-profile?
  "Validates user profile against spec.

  Example:
    (valid-user-profile? profile)
    => true

  Returns:
    Boolean indicating if profile conforms to ::user-profile spec"
  [profile]
  (s/valid? ::user-profile profile))

(defn validate-calibration-sessions
  "Validates that we have all 3 required calibration types.

  Args:
    sessions: Collection of calibration-session maps

  Returns:
    Boolean - true if we have at least 1 of each type (:t-pose, :breathing, :movement)

  Example:
    (validate-calibration-sessions [{:calibration-type :t-pose ...}
                                     {:calibration-type :breathing ...}
                                     {:calibration-type :movement ...}])
    => true"
  [sessions]
  (let [types (set (map :calibration-type sessions))]
    (and (>= (count sessions) 3)
         (contains? types :t-pose)
         (contains? types :breathing)
         (contains? types :movement))))

(defn explain-user-profile
  "Returns human-readable explanation of why profile is invalid.

  Args:
    profile: User profile map to validate

  Returns:
    String explanation or nil if valid

  Example:
    (explain-user-profile {:user-id \"not-uuid\"})
    => \"In: [:user-id] val: \\\"not-uuid\\\" fails spec: ::user-id\""
  [profile]
  (when-not (s/valid? ::user-profile profile)
    (s/explain-str ::user-profile profile)))

;; -------- Constructor Functions --------

(defn new-user-profile
  "Create a new user profile with default values.

  Args:
    user-id: UUID
    height-cm: User's height in centimeters

  Returns:
    User profile map conforming to ::user-profile spec"
  [user-id height-cm]
  (let [profile {:user-id user-id
                 :height-cm height-cm
                 :baseline-pose {:landmarks [] :joint-distances {}}
                 :learned-thresholds
                 {:breathing-thresholds
                  {:fatigue-threshold 0.3
                   :rate-alert-threshold 5.0}
                  :posture-thresholds
                  {:forward-head-alert-cm (* 0.03 height-cm)  ;; 3% of height
                   :shoulder-imbalance-alert-deg 5.0}
                  :balance-thresholds
                  {:stability-alert-threshold 0.6}}
                 :last-calibration-date (js/Date.)
                 :calibration-count 0}]
    ;; Validate at construction time
    (assert (s/valid? ::user-profile profile)
            (str "Constructor produced invalid profile: "
                 (s/explain-str ::user-profile profile)))
    profile))

;; -------- Helper Functions --------

(defn get-breathing-fatigue-threshold
  "Extract breathing fatigue threshold from profile with fallback.

  Args:
    profile: User profile map (may be nil)

  Returns:
    Fatigue threshold number (0.3 if profile is nil)"
  [profile]
  (get-in profile [:learned-thresholds
                   :breathing-thresholds
                   :fatigue-threshold]
          0.3))

(defn get-posture-forward-head-alert
  "Extract forward head alert threshold from profile with fallback.

  Args:
    profile: User profile map (may be nil)

  Returns:
    Forward head alert threshold in cm (5.0 if profile is nil)"
  [profile]
  (get-in profile [:learned-thresholds
                   :posture-thresholds
                   :forward-head-alert-cm]
          5.0))
