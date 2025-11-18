(ns combatsys.calibration
  "Pure functions for analyzing calibration sessions.

   Extracts baseline measurements from three types of sessions:
   1. T-pose: Body proportions, joint distances
   2. Breathing: Typical respiratory baseline
   3. Movement: Range of motion (ROM) for joints

   Philosophy (Rich Hickey):
   - Pure data transformation: calibration-sessions → baselines
   - No side effects, no mutation
   - 100% testable in REPL

   Performance (John Carmack):
   - Offline processing (not real-time critical)
   - Can take 1-5 seconds per session
   - No GPU optimization needed for LOD 5.0

   Example:
     (def sessions {:t-pose t-pose-timeline
                    :breathing breathing-timeline
                    :movement movement-timeline})
     (analyze-t-pose-session (:t-pose sessions) 178)
     ;; => {:height-cm 178
     ;;     :baseline-pose {...}
     ;;     :joint-distances {...}}"
  (:require [combatsys.schema :as schema]
            [combatsys.pose :as pose]
            [combatsys.posture :as posture]
            [combatsys.breathing :as breathing]))

;; ============================================================
;; HELPER FUNCTIONS
;; ============================================================

(defn average-poses
  "Average multiple poses into a single consensus pose.

   Takes the mean position of each landmark across all poses.
   Filters out low-visibility landmarks (<0.5 confidence).

   Args:
     poses: Vector of pose maps (from :frame/pose)

   Returns:
     Single averaged pose map with :pose/landmarks and :pose/confidence

   Example:
     (average-poses [pose1 pose2 pose3])
     ;; => {:pose/landmarks [...33 averaged landmarks...]
     ;;     :pose/confidence 0.92}"
  [poses]
  (let [;; Extract all landmark IDs from schema
        landmark-ids schema/landmark-ids

        ;; Average each landmark
        averaged-landmarks
        (vec
         (for [lm-id landmark-ids]
           (let [;; Extract this landmark from all poses
                 lms (->> poses
                          (map #(pose/get-landmark % lm-id))
                          (filter some?)
                          (filter #(> (:landmark/visibility %) 0.5)))

                 n (count lms)]

             (if (pos? n)
               {:landmark/id lm-id
                :landmark/x (/ (reduce + (map :landmark/x lms)) n)
                :landmark/y (/ (reduce + (map :landmark/y lms)) n)
                :landmark/z (/ (reduce + (map :landmark/z lms)) n)
                :landmark/visibility (/ (reduce + (map :landmark/visibility lms)) n)}
               ;; Missing landmark - use zero position with low visibility
               {:landmark/id lm-id
                :landmark/x 0.0
                :landmark/y 0.0
                :landmark/z 0.0
                :landmark/visibility 0.0}))))

        ;; Average confidence across all poses
        avg-confidence (if (seq poses)
                         (/ (reduce + (map :pose/confidence poses)) (count poses))
                         0.0)]

    {:pose/landmarks averaged-landmarks
     :pose/confidence avg-confidence}))

(defn compute-scale-factor
  "Compute pixel-to-cm scale factor from user height.

   Uses nose-to-ankle distance as proxy for height.

   Args:
     pose: Averaged pose from T-pose session
     user-height-cm: User's actual height in cm

   Returns:
     Scale factor (cm per normalized unit)

   Algorithm:
     1. Measure vertical distance from nose to average ankle (normalized [0,1])
     2. Divide user height by this distance
     3. Result is cm per normalized unit

   Example:
     (compute-scale-factor baseline-pose 178)
     ;; => 185.4 (cm per normalized unit)"
  [pose user-height-cm]
  (let [nose (pose/get-landmark pose :nose)
        left-ankle (pose/get-landmark pose :left-ankle)
        right-ankle (pose/get-landmark pose :right-ankle)

        ;; Average ankle position
        ankle-y (if (and left-ankle right-ankle)
                  (/ (+ (:landmark/y left-ankle)
                        (:landmark/y right-ankle)) 2)
                  (or (:landmark/y left-ankle)
                      (:landmark/y right-ankle)
                      1.0))

        ;; Vertical distance (normalized coordinates)
        height-normalized (js/Math.abs (- (:landmark/y nose) ankle-y))]

    (if (pos? height-normalized)
      (/ user-height-cm height-normalized)
      user-height-cm))) ;; Fallback if measurement fails

(defn compute-rhythm-regularity
  "Compute breathing rhythm regularity score.

   Uses coefficient of variation (CV) of inter-breath intervals.
   Lower CV = more regular rhythm.

   Args:
     breath-intervals: Vector of inter-breath intervals in seconds

   Returns:
     Regularity score from 0.0 (irregular) to 1.0 (perfect regularity)

   Algorithm:
     1. Compute mean and std dev of intervals
     2. CV = std_dev / mean
     3. Regularity = max(0, 1 - CV)

   Example:
     (compute-rhythm-regularity [2.8 2.9 2.7 2.8 2.9])
     ;; => 0.95 (very regular)"
  [breath-intervals]
  (if (< (count breath-intervals) 2)
    0.5 ;; Not enough data - return neutral score
    (let [n (count breath-intervals)
          mean (/ (reduce + breath-intervals) n)

          ;; Standard deviation
          variance (/ (reduce + (map #(let [diff (- % mean)]
                                        (* diff diff))
                                     breath-intervals))
                      n)
          std-dev (js/Math.sqrt variance)

          ;; Coefficient of variation
          cv (if (pos? mean)
               (/ std-dev mean)
               0.0)

          ;; Convert to 0-1 regularity score
          ;; CV of 0.0 = perfect (score 1.0)
          ;; CV of 0.5 = moderate (score 0.5)
          ;; CV of 1.0+ = irregular (score 0.0)
          regularity (js/Math.max 0.0 (js/Math.min 1.0 (- 1.0 cv)))]

      regularity)))

;; ============================================================
;; T-POSE ANALYSIS
;; ============================================================

(defn analyze-t-pose-session
  "Extract body measurements from T-pose calibration session.

   T-pose = user standing with arms extended horizontally.
   Used to measure body proportions and joint distances.

   Args:
     timeline: Vector of frame maps from T-pose session
     user-height-cm: User-provided height in centimeters

   Returns:
     Map with:
     - :height-cm - User height (as provided)
     - :baseline-pose - Averaged pose (33 landmarks)
     - :joint-distances - Map of measured distances in cm

   Joint distances measured:
   - :shoulder-width-cm - Distance between shoulders
   - :arm-span-cm - Full arm span (left wrist to right wrist)
   - :torso-length-cm - Shoulder center to hip center
   - :upper-arm-length-cm - Average shoulder to elbow
   - :forearm-length-cm - Average elbow to wrist

   Example:
     (analyze-t-pose-session t-pose-timeline 178)
     ;; => {:height-cm 178
     ;;     :baseline-pose {:pose/landmarks [...] :pose/confidence 0.94}
     ;;     :joint-distances {:shoulder-width-cm 42.5
     ;;                       :arm-span-cm 182.0
     ;;                       :torso-length-cm 54.3
     ;;                       :upper-arm-length-cm 32.1
     ;;                       :forearm-length-cm 28.4}}"
  [timeline user-height-cm]
  (let [;; Extract poses from timeline
        poses (map :frame/pose timeline)

        ;; Average all poses to get baseline
        baseline-pose (average-poses poses)

        ;; Compute scale factor (normalized → cm)
        scale (compute-scale-factor baseline-pose user-height-cm)

        ;; Extract key landmarks from baseline
        left-shoulder (pose/get-landmark baseline-pose :left-shoulder)
        right-shoulder (pose/get-landmark baseline-pose :right-shoulder)
        left-elbow (pose/get-landmark baseline-pose :left-elbow)
        right-elbow (pose/get-landmark baseline-pose :right-elbow)
        left-wrist (pose/get-landmark baseline-pose :left-wrist)
        right-wrist (pose/get-landmark baseline-pose :right-wrist)
        left-hip (pose/get-landmark baseline-pose :left-hip)
        right-hip (pose/get-landmark baseline-pose :right-hip)

        ;; Compute joint distances (normalized units)
        shoulder-width (pose/compute-distance left-shoulder right-shoulder)
        arm-span (pose/compute-distance left-wrist right-wrist)

        ;; Torso length (shoulder center to hip center)
        shoulder-center {:landmark/x (/ (+ (:landmark/x left-shoulder)
                                           (:landmark/x right-shoulder)) 2)
                         :landmark/y (/ (+ (:landmark/y left-shoulder)
                                           (:landmark/y right-shoulder)) 2)
                         :landmark/z (/ (+ (:landmark/z left-shoulder)
                                           (:landmark/z right-shoulder)) 2)}
        hip-center {:landmark/x (/ (+ (:landmark/x left-hip)
                                      (:landmark/x right-hip)) 2)
                    :landmark/y (/ (+ (:landmark/y left-hip)
                                      (:landmark/y right-hip)) 2)
                    :landmark/z (/ (+ (:landmark/z left-hip)
                                      (:landmark/z right-hip)) 2)}
        torso-length (pose/compute-distance shoulder-center hip-center)

        ;; Arm segments
        left-upper-arm (pose/compute-distance left-shoulder left-elbow)
        right-upper-arm (pose/compute-distance right-shoulder right-elbow)
        upper-arm-length (/ (+ left-upper-arm right-upper-arm) 2)

        left-forearm (pose/compute-distance left-elbow left-wrist)
        right-forearm (pose/compute-distance right-elbow right-wrist)
        forearm-length (/ (+ left-forearm right-forearm) 2)

        ;; Convert to centimeters
        joint-distances
        {:shoulder-width-cm (* shoulder-width scale)
         :arm-span-cm (* arm-span scale)
         :torso-length-cm (* torso-length scale)
         :upper-arm-length-cm (* upper-arm-length scale)
         :forearm-length-cm (* forearm-length scale)}]

    {:height-cm user-height-cm
     :baseline-pose baseline-pose
     :joint-distances joint-distances}))

;; ============================================================
;; BREATHING ANALYSIS
;; ============================================================

(defn analyze-breathing-session
  "Extract breathing baseline from breathing calibration session.

   User breathes normally for 60 seconds while standing still.

   Args:
     timeline: Vector of frame maps from breathing session

   Returns:
     Map with:
     - :typical-rate-bpm - Average breathing rate
     - :typical-depth - Average breath depth (0-1 scale)
     - :typical-rhythm-regularity - Rhythm consistency (0-1 scale)

   Algorithm:
     1. Reuse existing breathing.cljs analysis
     2. Extract rate, depth from results
     3. Use default rhythm regularity (LOD 5.0 simplification)

   Example:
     (analyze-breathing-session breathing-timeline)
     ;; => {:typical-rate-bpm 21.5
     ;;     :typical-depth 0.82
     ;;     :typical-rhythm-regularity 0.85}"
  [timeline]
  (let [;; Extract torso motion signal
        torso-signal (breathing/extract-torso-motion timeline)

        ;; Detect breathing rate using existing FFT-based detection
        ;; Returns {:rate-bpm N :depth-score D :confidence C :method M}
        rate-analysis (breathing/detect-breathing-rate torso-signal)

        ;; Extract rate and depth from analysis
        rate-bpm (:rate-bpm rate-analysis)
        depth-score (:depth-score rate-analysis)

        ;; Compute rhythm regularity
        ;; (Simplified for LOD 5.0 - use default value)
        ;; Full implementation would extract actual peak timestamps and compute CV
        rhythm-regularity 0.85]

    {:typical-rate-bpm rate-bpm
     :typical-depth depth-score
     :typical-rhythm-regularity rhythm-regularity}))

;; ============================================================
;; MOVEMENT / ROM ANALYSIS
;; ============================================================

(defn compute-rom-ranges
  "Compute range of motion (ROM) for all joints in timeline.

   Tracks min and max angles for each joint across all frames.

   Args:
     timeline: Vector of frame maps from movement session

   Returns:
     Map of joint-id → [min-degrees max-degrees]

   Example:
     (compute-rom-ranges movement-timeline)
     ;; => {:left-elbow [5.2 142.8]
     ;;     :right-elbow [3.1 145.6]
     ;;     :left-shoulder [12.4 168.2]
     ;;     :right-shoulder [10.8 172.1]
     ;;     :left-knee [2.5 128.4]
     ;;     :right-knee [1.8 131.2]
     ;;     :left-hip [8.2 115.6]
     ;;     :right-hip [7.4 118.3]}"
  [timeline]
  (let [;; All joint angle keys we track (from pose/compute-all-angles)
        joint-keys [:left-elbow :right-elbow
                    :left-shoulder :right-shoulder
                    :left-knee :right-knee
                    :left-hip :right-hip]

        ;; Extract angles from all frames
        all-angles (map (fn [frame]
                          (pose/compute-all-angles (:frame/pose frame)))
                        timeline)

        ;; For each joint, find min and max
        rom-ranges
        (into {}
              (for [joint-key joint-keys]
                (let [angles (->> all-angles
                                  (map joint-key)
                                  (filter some?)
                                  (filter (complement js/isNaN)))

                      min-angle (when (seq angles) (apply min angles))
                      max-angle (when (seq angles) (apply max angles))]

                  (when (and min-angle max-angle)
                    [joint-key [min-angle max-angle]]))))]

    rom-ranges))

(defn analyze-movement-session
  "Extract ROM ranges from movement calibration session.

   User performs various movements (arm circles, squats, etc.) for 60 seconds.

   Args:
     timeline: Vector of frame maps from movement session

   Returns:
     Map with:
     - :rom-ranges - Map of joint-id → [min-degrees max-degrees]

   Example:
     (analyze-movement-session movement-timeline)
     ;; => {:rom-ranges {:left-elbow [5.2 142.8]
     ;;                  :right-elbow [3.1 145.6]
     ;;                  ...}}"
  [timeline]
  {:rom-ranges (compute-rom-ranges timeline)})
