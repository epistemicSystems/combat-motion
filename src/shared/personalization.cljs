(ns combatsys.personalization
  "Pure functions for creating personalized user profiles.

   Takes calibration baselines and computes adaptive thresholds.
   Assembles complete user profiles from calibration sessions.

   Philosophy (Rich Hickey):
   - Pure data transformation: baselines → thresholds → profile
   - No side effects, no mutation
   - Simple math, clear heuristics

   Threshold Computation Strategy:
   - Breathing fatigue: 30% below typical depth
   - Breathing rate alert: ±25% from typical rate (converted to absolute deviation)
   - Forward head alert: 3% of user height OR typical + 2cm (whichever is larger)
   - Shoulder imbalance alert: typical + 2° (or 5° minimum)
   - Balance stability alert: 0.6 (fixed for LOD 5.0 - no balance baseline yet)

   Example:
     (def t-pose-result (calibration/analyze-t-pose-session ...))
     (def breathing-result (calibration/analyze-breathing-session ...))
     (def movement-result (calibration/analyze-movement-session ...))

     (create-user-profile user-id [t-pose-session breathing-session movement-session])
     ;; => {:user-id #uuid \"...\"
     ;;     :height-cm 178
     ;;     :baseline-pose {...}
     ;;     :learned-thresholds {...}
     ;;     :breathing-baseline {...}
     ;;     :posture-baseline {...}
     ;;     :rom-ranges {...}
     ;;     :last-calibration-date #inst \"...\"
     ;;     :calibration-count 3}"
  (:require [combatsys.schema :as schema]
            [combatsys.calibration :as calibration]))

;; ============================================================
;; THRESHOLD COMPUTATION
;; ============================================================

(defn compute-breathing-thresholds
  "Compute personalized breathing alert thresholds.

   Args:
     breathing-baseline: Map from analyze-breathing-session
       {:typical-rate-bpm N :typical-depth D :typical-rhythm-regularity R}

   Returns:
     Map with:
     - :fatigue-threshold - Depth below which fatigue is suspected (0-1)
     - :rate-alert-threshold - Deviation from typical rate (bpm)

   Algorithm:
     - Fatigue threshold = 70% of typical depth (30% drop indicates fatigue)
     - Rate alert = 25% deviation from typical rate (e.g., 21 bpm → alert if <15.75 or >26.25)

   Example:
     (compute-breathing-thresholds {:typical-rate-bpm 21.5
                                     :typical-depth 0.82
                                     :typical-rhythm-regularity 0.89})
     ;; => {:fatigue-threshold 0.574  ;; 70% of 0.82
     ;;     :rate-alert-threshold 5.375}  ;; 25% of 21.5"
  [breathing-baseline]
  (let [typical-rate (:typical-rate-bpm breathing-baseline)
        typical-depth (:typical-depth breathing-baseline)

        ;; Fatigue = 30% drop from typical depth
        fatigue-threshold (* 0.7 typical-depth)

        ;; Rate alert = 25% deviation from typical
        rate-alert-threshold (* 0.25 typical-rate)]

    {:fatigue-threshold fatigue-threshold
     :rate-alert-threshold rate-alert-threshold}))

(defn compute-posture-thresholds
  "Compute personalized posture alert thresholds.

   Args:
     height-cm: User height in centimeters
     posture-baseline: Optional map from posture analysis
       {:typical-forward-head-cm N :typical-shoulder-imbalance-deg D}

   Returns:
     Map with:
     - :forward-head-alert-cm - Alert threshold for forward head (cm)
     - :shoulder-imbalance-alert-deg - Alert threshold for shoulder tilt (degrees)

   Algorithm (if baseline provided):
     - Forward head alert = max(typical + 2cm, 3% of height)
     - Shoulder imbalance alert = max(|typical| + 2°, 5°)

   Algorithm (if no baseline):
     - Forward head alert = 3% of height (generic)
     - Shoulder imbalance alert = 5° (generic)

   Example (with baseline):
     (compute-posture-thresholds 178 {:typical-forward-head-cm 3.8
                                       :typical-shoulder-imbalance-deg 1.2})
     ;; => {:forward-head-alert-cm 5.8  ;; 3.8 + 2
     ;;     :shoulder-imbalance-alert-deg 5.0}  ;; max(1.2 + 2, 5) = 5

   Example (without baseline):
     (compute-posture-thresholds 178 nil)
     ;; => {:forward-head-alert-cm 5.34  ;; 3% of 178
     ;;     :shoulder-imbalance-alert-deg 5.0}"
  ([height-cm] (compute-posture-thresholds height-cm nil))
  ([height-cm posture-baseline]
   (let [;; Generic threshold (3% of height)
         generic-fhp-threshold (* 0.03 height-cm)

         ;; If we have a baseline, add 2cm margin
         fhp-alert (if posture-baseline
                     (js/Math.max
                      (+ (:typical-forward-head-cm posture-baseline) 2.0)
                      generic-fhp-threshold)
                     generic-fhp-threshold)

         ;; Shoulder imbalance: baseline + 2°, minimum 5°
         shoulder-alert (if posture-baseline
                          (js/Math.max
                           (+ (js/Math.abs (:typical-shoulder-imbalance-deg posture-baseline)) 2.0)
                           5.0)
                          5.0)]

     {:forward-head-alert-cm fhp-alert
      :shoulder-imbalance-alert-deg shoulder-alert})))

(defn compute-balance-thresholds
  "Compute personalized balance alert thresholds.

   Args:
     balance-baseline: Optional map from balance analysis (not in LOD 5.0)

   Returns:
     Map with:
     - :stability-alert-threshold - Stability score below which to alert (0-1)

   Note:
     LOD 5.0 uses fixed threshold (0.6) - no balance calibration yet.
     Future: Compute from actual balance measurements.

   Example:
     (compute-balance-thresholds nil)
     ;; => {:stability-alert-threshold 0.6}"
  [balance-baseline]
  ;; For LOD 5.0: Fixed threshold
  ;; Future: Use balance-baseline to personalize
  {:stability-alert-threshold 0.6})

;; ============================================================
;; USER PROFILE CREATION
;; ============================================================

(defn create-user-profile
  "Create complete user profile from calibration sessions.

   Main entry point for personalization system.

   Args:
     user-id: UUID
     calibration-sessions: Vector of 3 calibration-session maps
       Must include: :t-pose, :breathing, :movement types
     user-height-cm: User-provided height

   Returns:
     Complete user-profile map conforming to ::schema/user-profile

   Algorithm:
     1. Validate we have all 3 session types
     2. Analyze each session type
     3. Compute personalized thresholds
     4. Assemble profile
     5. Validate against schema

   Example:
     (create-user-profile
       user-id
       [{:calibration-type :t-pose :timeline [...]}
        {:calibration-type :breathing :timeline [...]}
        {:calibration-type :movement :timeline [...]}]
       178)
     ;; => {:user-id #uuid \"...\"
     ;;     :height-cm 178
     ;;     :baseline-pose {...}
     ;;     :learned-thresholds {...}
     ;;     :breathing-baseline {...}
     ;;     :rom-ranges {...}
     ;;     :last-calibration-date #inst \"...\"
     ;;     :calibration-count 3}"
  [user-id calibration-sessions user-height-cm]
  {:pre [(schema/validate-calibration-sessions calibration-sessions)]}

  ;; Extract sessions by type
  (let [session-by-type (group-by :calibration-type calibration-sessions)

        ;; Get the most recent of each type (in case of duplicates)
        t-pose-session (->> (:t-pose session-by-type)
                            (sort-by :created-at)
                            last)
        breathing-session (->> (:breathing session-by-type)
                               (sort-by :created-at)
                               last)
        movement-session (->> (:movement session-by-type)
                              (sort-by :created-at)
                              last)

        ;; Analyze each session type
        t-pose-result (calibration/analyze-t-pose-session
                       (:timeline t-pose-session)
                       user-height-cm)

        breathing-result (calibration/analyze-breathing-session
                          (:timeline breathing-session))

        movement-result (calibration/analyze-movement-session
                         (:timeline movement-session))

        ;; Extract baselines
        baseline-pose (:baseline-pose t-pose-result)
        joint-distances (:joint-distances t-pose-result)

        breathing-baseline breathing-result
        rom-ranges (:rom-ranges movement-result)

        ;; Compute personalized thresholds
        breathing-thresholds (compute-breathing-thresholds breathing-baseline)
        posture-thresholds (compute-posture-thresholds user-height-cm nil) ;; No posture baseline in calibration yet
        balance-thresholds (compute-balance-thresholds nil)

        ;; Assemble profile
        profile {:user-id user-id
                 :height-cm user-height-cm
                 :baseline-pose {:landmarks (:pose/landmarks baseline-pose)
                                 :joint-distances joint-distances}
                 :learned-thresholds {:breathing-thresholds breathing-thresholds
                                      :posture-thresholds posture-thresholds
                                      :balance-thresholds balance-thresholds}
                 :last-calibration-date (js/Date.)
                 :calibration-count (count calibration-sessions)

                 ;; Optional fields (present in LOD 5)
                 :breathing-baseline breathing-baseline
                 :rom-ranges rom-ranges}]

    ;; Validate profile conforms to schema
    (assert (schema/valid-user-profile? profile)
            (str "Profile validation failed: "
                 (schema/explain-user-profile profile)))

    profile))

(defn update-user-profile
  "Update existing user profile with new calibration sessions.

   Increments calibration-count, updates last-calibration-date,
   recomputes baselines and thresholds.

   Args:
     existing-profile: Current user profile map
     new-calibration-sessions: Vector of new calibration sessions
     user-height-cm: User height (can be updated)

   Returns:
     Updated user profile map

   Example:
     (update-user-profile
       existing-profile
       [{:calibration-type :breathing :timeline [...]}]
       178)
     ;; => Updated profile with new breathing baseline"
  [existing-profile new-calibration-sessions user-height-cm]
  (let [;; Create new profile from sessions
        new-profile (create-user-profile
                     (:user-id existing-profile)
                     new-calibration-sessions
                     user-height-cm)

        ;; Merge with existing, updating calibration count
        updated-profile
        (merge existing-profile
               new-profile
               {:calibration-count (+ (:calibration-count existing-profile)
                                      (count new-calibration-sessions))
                :last-calibration-date (js/Date.)})]

    ;; Validate
    (assert (schema/valid-user-profile? updated-profile)
            (str "Updated profile validation failed: "
                 (schema/explain-user-profile updated-profile)))

    updated-profile))
