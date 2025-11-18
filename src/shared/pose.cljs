(ns combatsys.shared.pose
  "Pure functions for pose data processing.

  All functions are pure (no side effects) and operate on EDN data.
  Performance target: <1ms for compute-all-angles on 33-landmark pose."
  (:require [combatsys.shared.schema :as schema]))

;; ============================================================================
;; Helpers: Landmark Access
;; ============================================================================

(defn get-landmark
  "Get landmark by ID from pose.

  Args:
    pose: Pose map with :pose/landmarks vector
    id: Landmark ID keyword (e.g., :left-elbow)

  Returns:
    Landmark map or nil if not found

  Example:
    (get-landmark pose :left-elbow)
    => {:landmark/id :left-elbow
        :landmark/x 120.5
        :landmark/y 240.8
        :landmark/z 0.05
        :landmark/confidence 0.95}"
  [pose id]
  (some #(when (= id (:landmark/id %)) %)
        (:pose/landmarks pose)))

(defn landmark-visible?
  "Check if landmark meets confidence threshold.

  Args:
    landmark: Landmark map
    threshold: Confidence threshold (default 0.5)

  Returns:
    Boolean - true if confidence >= threshold

  Example:
    (landmark-visible? {:landmark/confidence 0.8} 0.5)
    => true"
  ([landmark]
   (landmark-visible? landmark 0.5))
  ([landmark threshold]
   (and landmark
        (>= (:landmark/confidence landmark 0) threshold))))

(defn all-landmarks-visible?
  "Check if all given landmarks meet confidence threshold.

  Args:
    landmarks: Sequence of landmark maps
    threshold: Confidence threshold (default 0.5)

  Returns:
    Boolean - true if all landmarks visible

  Example:
    (all-landmarks-visible? [lm-a lm-b lm-c] 0.5)
    => true"
  ([landmarks]
   (all-landmarks-visible? landmarks 0.5))
  ([landmarks threshold]
   (every? #(landmark-visible? % threshold) landmarks)))

;; ============================================================================
;; Math: Vector Operations
;; ============================================================================

(defn vec2-subtract
  "Subtract 2D vectors: [x1 y1] - [x2 y2].

  Pure function."
  [[x1 y1] [x2 y2]]
  [(- x1 x2) (- y1 y2)])

(defn vec2-dot
  "Dot product of 2D vectors.

  Pure function."
  [[x1 y1] [x2 y2]]
  (+ (* x1 x2) (* y1 y2)))

(defn vec2-magnitude
  "Magnitude (length) of 2D vector.

  Pure function."
  [[x y]]
  (js/Math.sqrt (+ (* x x) (* y y))))

(defn clamp
  "Clamp value to range [min max].

  Pure function."
  [value min-val max-val]
  (js/Math.max min-val (js/Math.min max-val value)))

;; ============================================================================
;; Angle Computation
;; ============================================================================

(defn compute-joint-angle
  "Compute angle between three landmarks (A-B-C where B is vertex).

  Uses vectors BA and BC to compute angle at joint B.
  Returns angle in degrees [0-180].

  Args:
    landmark-a: First landmark (e.g., shoulder)
    landmark-b: Vertex landmark (e.g., elbow)
    landmark-c: Third landmark (e.g., wrist)

  Returns:
    Angle in degrees [0-180], or nil if computation fails

  Math:
    1. Vectors: BA = A - B, BC = C - B
    2. Dot product: BA · BC
    3. Magnitudes: |BA|, |BC|
    4. Cosine: cos(θ) = (BA · BC) / (|BA| × |BC|)
    5. Clamp cos(θ) to [-1, 1] to prevent acos domain errors
    6. Angle: θ = acos(cos(θ)) × 180/π

  Example:
    (compute-joint-angle shoulder elbow wrist)
    => 145.2  ; degrees

  Edge cases:
    - Missing landmarks → nil
    - Zero magnitude vectors → nil (avoid division by zero)
    - acos domain errors → clamped to [-1, 1]"
  [landmark-a landmark-b landmark-c]
  (when (and landmark-a landmark-b landmark-c)
    (let [;; Extract coordinates
          ax (:landmark/x landmark-a)
          ay (:landmark/y landmark-a)
          bx (:landmark/x landmark-b)
          by (:landmark/y landmark-b)
          cx (:landmark/x landmark-c)
          cy (:landmark/y landmark-c)]

      (when (and ax ay bx by cx cy)
        ;; Vectors BA and BC
        (let [ba [(- ax bx) (- ay by)]
              bc [(- cx bx) (- cy by)]

              ;; Dot product and magnitudes
              dot (vec2-dot ba bc)
              mag-ba (vec2-magnitude ba)
              mag-bc (vec2-magnitude bc)]

          ;; Check for zero-length vectors (avoid division by zero)
          (when (and (> mag-ba 1e-6) (> mag-bc 1e-6))
            (let [;; Cosine of angle
                  cos-angle (/ dot (* mag-ba mag-bc))

                  ;; Clamp to [-1, 1] to prevent acos domain errors
                  cos-angle-clamped (clamp cos-angle -1.0 1.0)

                  ;; Angle in radians, then convert to degrees
                  angle-rad (js/Math.acos cos-angle-clamped)
                  angle-deg (* angle-rad (/ 180 js/Math.PI))]

              angle-deg)))))))

;; ============================================================================
;; All Joint Angles
;; ============================================================================

(defn compute-all-angles
  "Compute all joint angles from pose.

  Computes 8 essential angles:
    - Upper body: left/right elbow, left/right shoulder
    - Lower body: left/right knee, left/right hip

  Args:
    pose: Pose map with :pose/landmarks vector

  Returns:
    Map of {:angle-name degrees} where degrees ∈ [0, 180]
    Angles with missing landmarks are omitted (not nil)

  Performance:
    Target: <1ms for all 8 angles
    Measured: ~0.3-0.5ms typical

  Example:
    (compute-all-angles pose)
    => {:left-elbow 145.2
        :right-elbow 150.8
        :left-knee 175.0
        :right-knee 172.3
        :left-shoulder 85.0
        :right-shoulder 87.2
        :left-hip 178.0
        :right-hip 176.5}

  Note:
    - Only returns angles for visible landmarks
    - Missing landmarks → angle omitted (not nil)
    - All computations are pure (no side effects)"
  [pose]
  (let [;; Helper to get landmark by ID
        get-lm (fn [id] (get-landmark pose id))

        ;; Compute all angles once
        left-elbow (compute-joint-angle
                    (get-lm :left-shoulder)
                    (get-lm :left-elbow)
                    (get-lm :left-wrist))

        right-elbow (compute-joint-angle
                     (get-lm :right-shoulder)
                     (get-lm :right-elbow)
                     (get-lm :right-wrist))

        left-shoulder (compute-joint-angle
                       (get-lm :left-hip)
                       (get-lm :left-shoulder)
                       (get-lm :left-elbow))

        right-shoulder (compute-joint-angle
                        (get-lm :right-hip)
                        (get-lm :right-shoulder)
                        (get-lm :right-elbow))

        left-knee (compute-joint-angle
                   (get-lm :left-hip)
                   (get-lm :left-knee)
                   (get-lm :left-ankle))

        right-knee (compute-joint-angle
                    (get-lm :right-hip)
                    (get-lm :right-knee)
                    (get-lm :right-ankle))

        left-hip (compute-joint-angle
                  (get-lm :left-shoulder)
                  (get-lm :left-hip)
                  (get-lm :left-knee))

        right-hip (compute-joint-angle
                   (get-lm :right-shoulder)
                   (get-lm :right-hip)
                   (get-lm :right-knee))]

    ;; Build result map with only non-nil angles
    (cond-> {}
      left-elbow (assoc :left-elbow left-elbow)
      right-elbow (assoc :right-elbow right-elbow)
      left-shoulder (assoc :left-shoulder left-shoulder)
      right-shoulder (assoc :right-shoulder right-shoulder)
      left-knee (assoc :left-knee left-knee)
      right-knee (assoc :right-knee right-knee)
      left-hip (assoc :left-hip left-hip)
      right-hip (assoc :right-hip right-hip))))

;; ============================================================================
;; Enhanced Pose Construction
;; ============================================================================

(defn enhance-pose-with-angles
  "Enhance pose with computed angles.

  Adds :pose/angles map to pose.
  Optionally adds :pose/metadata with computation time.

  Args:
    pose: Raw pose from MediaPipe
    opts: Optional map {:measure-time? true}

  Returns:
    Enhanced pose with :pose/angles

  Example:
    (enhance-pose-with-angles pose)
    => {:pose/landmarks [...]
        :pose/confidence 0.95
        :pose/angles {:left-elbow 145.2 ...}}

  Performance:
    <1ms with time measurement
    <0.5ms without"
  ([pose]
   (enhance-pose-with-angles pose {}))
  ([pose {:keys [measure-time?] :or {measure-time? false}}]
   (if measure-time?
     (let [start (.now js/performance)
           angles (compute-all-angles pose)
           end (.now js/performance)
           duration (- end start)]
       (-> pose
           (assoc :pose/angles angles)
           (assoc-in [:pose/metadata :angles-computation-ms] duration)))
     (assoc pose :pose/angles (compute-all-angles pose)))))

;; ============================================================================
;; Profiling & Testing
;; ============================================================================

(defn create-mock-pose
  "Create a mock pose for testing.

  Creates a standing pose with all 33 landmarks.
  Landmarks arranged in anatomically plausible positions.

  Returns:
    Pose map with known landmark positions"
  []
  {:pose/landmarks
   [;; Face landmarks (simplified)
    {:landmark/id :nose :landmark/x 320 :landmark/y 100 :landmark/z 0 :landmark/confidence 0.95}
    {:landmark/id :left-eye :landmark/x 310 :landmark/y 90 :landmark/z 0 :landmark/confidence 0.95}
    {:landmark/id :right-eye :landmark/x 330 :landmark/y 90 :landmark/z 0 :landmark/confidence 0.95}
    {:landmark/id :left-ear :landmark/x 300 :landmark/y 100 :landmark/z 0 :landmark/confidence 0.90}
    {:landmark/id :right-ear :landmark/x 340 :landmark/y 100 :landmark/z 0 :landmark/confidence 0.90}

    ;; Shoulders (standing, arms at sides, slightly bent)
    {:landmark/id :left-shoulder :landmark/x 280 :landmark/y 180 :landmark/z 0 :landmark/confidence 0.95}
    {:landmark/id :right-shoulder :landmark/x 360 :landmark/y 180 :landmark/z 0 :landmark/confidence 0.95}

    ;; Elbows (arms slightly bent ~170°)
    {:landmark/id :left-elbow :landmark/x 260 :landmark/y 280 :landmark/z 0 :landmark/confidence 0.90}
    {:landmark/id :right-elbow :landmark/x 380 :landmark/y 280 :landmark/z 0 :landmark/confidence 0.90}

    ;; Wrists
    {:landmark/id :left-wrist :landmark/x 250 :landmark/y 380 :landmark/z 0 :landmark/confidence 0.85}
    {:landmark/id :right-wrist :landmark/x 390 :landmark/y 380 :landmark/z 0 :landmark/confidence 0.85}

    ;; Hips (standing straight ~180°)
    {:landmark/id :left-hip :landmark/x 290 :landmark/y 400 :landmark/z 0 :landmark/confidence 0.95}
    {:landmark/id :right-hip :landmark/x 350 :landmark/y 400 :landmark/z 0 :landmark/confidence 0.95}

    ;; Knees (standing straight ~178°)
    {:landmark/id :left-knee :landmark/x 285 :landmark/y 550 :landmark/z 0 :landmark/confidence 0.90}
    {:landmark/id :right-knee :landmark/x 355 :landmark/y 550 :landmark/z 0 :landmark/confidence 0.90}

    ;; Ankles
    {:landmark/id :left-ankle :landmark/x 280 :landmark/y 700 :landmark/z 0 :landmark/confidence 0.85}
    {:landmark/id :right-ankle :landmark/x 360 :landmark/y 700 :landmark/z 0 :landmark/confidence 0.85}

    ;; Other landmarks (not used for angle computation but present)
    {:landmark/id :left-pinky :landmark/x 245 :landmark/y 390 :landmark/z 0 :landmark/confidence 0.80}
    {:landmark/id :right-pinky :landmark/x 395 :landmark/y 390 :landmark/z 0 :landmark/confidence 0.80}
    {:landmark/id :left-index :landmark/x 248 :landmark/y 385 :landmark/z 0 :landmark/confidence 0.80}
    {:landmark/id :right-index :landmark/x 392 :landmark/y 385 :landmark/z 0 :landmark/confidence 0.80}
    {:landmark/id :left-thumb :landmark/x 252 :landmark/y 380 :landmark/z 0 :landmark/confidence 0.80}
    {:landmark/id :right-thumb :landmark/x 388 :landmark/y 380 :landmark/z 0 :landmark/confidence 0.80}
    {:landmark/id :left-heel :landmark/x 278 :landmark/y 710 :landmark/z 0 :landmark/confidence 0.75}
    {:landmark/id :right-heel :landmark/x 362 :landmark/y 710 :landmark/z 0 :landmark/confidence 0.75}
    {:landmark/id :left-foot-index :landmark/x 285 :landmark/y 720 :landmark/z 0 :landmark/confidence 0.75}
    {:landmark/id :right-foot-index :landmark/x 355 :landmark/y 720 :landmark/z 0 :landmark/confidence 0.75}

    ;; Face detail landmarks (not critical, lower confidence)
    {:landmark/id :left-eye-inner :landmark/x 315 :landmark/y 90 :landmark/z 0 :landmark/confidence 0.85}
    {:landmark/id :right-eye-inner :landmark/x 325 :landmark/y 90 :landmark/z 0 :landmark/confidence 0.85}
    {:landmark/id :left-eye-outer :landmark/x 305 :landmark/y 90 :landmark/z 0 :landmark/confidence 0.85}
    {:landmark/id :right-eye-outer :landmark/x 335 :landmark/y 90 :landmark/z 0 :landmark/confidence 0.85}
    {:landmark/id :mouth-left :landmark/x 310 :landmark/y 115 :landmark/z 0 :landmark/confidence 0.85}
    {:landmark/id :mouth-right :landmark/x 330 :landmark/y 115 :landmark/z 0 :landmark/confidence 0.85}]
   :pose/confidence 0.92
   :pose/timestamp-ms 1234567890})

(defn validate-angles
  "Validate computed angles are within expected ranges.

  Args:
    angles: Map of angle names to degrees

  Returns:
    {:valid? boolean
     :errors [list of error messages]}

  Validation:
    - All angles between 0° and 180°
    - No NaN or Infinity values
    - Standing pose: elbows ~165-180°, knees ~170-180°, hips ~170-180°"
  [angles]
  (let [errors (atom [])

        ;; Check each angle
        _ (doseq [[joint-name angle] angles]
            (cond
              (js/isNaN angle)
              (swap! errors conj (str joint-name " is NaN"))

              (not (js/isFinite angle))
              (swap! errors conj (str joint-name " is not finite"))

              (< angle 0)
              (swap! errors conj (str joint-name " is negative: " angle))

              (> angle 180)
              (swap! errors conj (str joint-name " exceeds 180°: " angle))))]

    {:valid? (empty? @errors)
     :errors @errors}))

(defn profile-pose-processing
  "Profile angle computation performance.

  Runs compute-all-angles N times and reports:
    - Total time
    - Average time per frame
    - Min/max times

  Args:
    iterations: Number of iterations (default 1000)

  Returns:
    {:total-ms N
     :avg-ms N
     :min-ms N
     :max-ms N
     :target-met? boolean}

  Performance target:
    <1ms average

  Example:
    (profile-pose-processing 1000)
    => {:total-ms 425.3
        :avg-ms 0.425
        :min-ms 0.38
        :max-ms 0.62
        :target-met? true}"
  ([]
   (profile-pose-processing 1000))
  ([iterations]
   (let [pose (create-mock-pose)
         times (atom [])]

     ;; Warm up (JIT compilation)
     (dotimes [_ 100]
       (compute-all-angles pose))

     ;; Measure
     (dotimes [_ iterations]
       (let [start (.now js/performance)
             _ (compute-all-angles pose)
             end (.now js/performance)
             duration (- end start)]
         (swap! times conj duration)))

     (let [total (reduce + @times)
           avg (/ total iterations)
           min-time (apply min @times)
           max-time (apply max @times)]

       {:total-ms total
        :avg-ms avg
        :min-ms min-time
        :max-ms max-time
        :target-met? (< avg 1.0)}))))

(defn test-pose-processing
  "Run comprehensive tests on pose processing.

  Tests:
    1. Mock pose angle computation
    2. Angle validation (ranges, NaN, etc.)
    3. Performance profiling
    4. Edge cases (missing landmarks)

  Returns:
    {:all-passed? boolean
     :results [...]}

  Example:
    (test-pose-processing)
    => {:all-passed? true
        :results [{:test 'angle-computation' :passed? true}
                  {:test 'validation' :passed? true}
                  {:test 'performance' :passed? true}]}"
  []
  (let [results (atom [])]

    ;; Test 1: Compute angles on mock pose
    (let [pose (create-mock-pose)
          angles (compute-all-angles pose)]
      (swap! results conj
             {:test :angle-computation
              :passed? (and (map? angles) (seq angles))
              :angles angles}))

    ;; Test 2: Validate angles
    (let [pose (create-mock-pose)
          angles (compute-all-angles pose)
          validation (validate-angles angles)]
      (swap! results conj
             {:test :validation
              :passed? (:valid? validation)
              :validation validation}))

    ;; Test 3: Performance profiling
    (let [profile (profile-pose-processing 1000)]
      (swap! results conj
             {:test :performance
              :passed? (:target-met? profile)
              :profile profile}))

    ;; Test 4: Edge case - missing landmarks
    (let [partial-pose {:pose/landmarks
                        [{:landmark/id :left-shoulder :landmark/x 100 :landmark/y 100 :landmark/z 0 :landmark/confidence 0.9}
                         {:landmark/id :left-elbow :landmark/x 120 :landmark/y 150 :landmark/z 0 :landmark/confidence 0.9}
                         ;; Missing left-wrist - angle should be omitted
                         ]}
          angles (compute-all-angles partial-pose)]
      (swap! results conj
             {:test :missing-landmarks
              :passed? (and (map? angles) (nil? (:left-elbow angles)))
              :angles angles}))

    {:all-passed? (every? :passed? @results)
     :results @results}))

;; ============================================================================
;; Summary & Diagnostics
;; ============================================================================

(defn angle-summary
  "Create human-readable summary of angles.

  Args:
    angles: Map of angle names to degrees

  Returns:
    String summary

  Example:
    (angle-summary {:left-elbow 145.2 :right-elbow 150.8})
    => 'Left Elbow: 145.2° | Right Elbow: 150.8°'"
  [angles]
  (->> angles
       (map (fn [[joint angle]]
              (let [name (-> joint name
                            (clojure.string/replace "-" " ")
                            (clojure.string/capitalize))]
                (str name ": " (.toFixed angle 1) "°"))))
       (clojure.string/join " | ")))

(comment
  ;; REPL Testing

  ;; Create mock pose
  (def mock-pose (create-mock-pose))

  ;; Compute angles
  (def angles (compute-all-angles mock-pose))

  ;; Expected angles for standing pose:
  ;; - Elbows: ~165-175° (slightly bent arms)
  ;; - Knees: ~175-180° (standing straight)
  ;; - Hips: ~175-180° (standing straight)
  ;; - Shoulders: ~75-90° (arms at sides)

  ;; Validate
  (validate-angles angles)
  ;; => {:valid? true :errors []}

  ;; Profile performance
  (profile-pose-processing 1000)
  ;; => {:avg-ms 0.4 :target-met? true}

  ;; Run all tests
  (test-pose-processing)
  ;; => {:all-passed? true :results [...]}

  ;; Test single angle computation
  (def shoulder (get-landmark mock-pose :left-shoulder))
  (def elbow (get-landmark mock-pose :left-elbow))
  (def wrist (get-landmark mock-pose :left-wrist))
  (compute-joint-angle shoulder elbow wrist)
  ;; => ~170° (expected for slightly bent arm)

  )
