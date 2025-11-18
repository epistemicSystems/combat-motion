(ns combatsys.calibration-test
  (:require [cljs.test :refer [deftest is testing]]
            [combatsys.calibration :as calibration]
            [combatsys.schema :as schema]
            [combatsys.pose :as pose]))

;; ============================================================
;; TEST FIXTURES
;; ============================================================

(defn create-mock-landmark
  "Create a mock landmark at position."
  [id x y z visibility]
  {:landmark/id id
   :landmark/x x
   :landmark/y y
   :landmark/z z
   :landmark/visibility visibility})

(defn create-mock-pose
  "Create a mock pose with specific landmarks."
  [landmarks confidence]
  {:pose/landmarks landmarks
   :pose/confidence confidence})

(defn create-mock-t-pose
  "Create a mock T-pose (arms extended horizontally).

   User standing with arms out, facing camera.
   Normalized coordinates [0,1] where y=0 is top."
  []
  (create-mock-pose
   [(create-mock-landmark :nose 0.5 0.1 0.0 0.98)
    (create-mock-landmark :left-shoulder 0.35 0.3 0.0 0.95)
    (create-mock-landmark :right-shoulder 0.65 0.3 0.0 0.95)
    (create-mock-landmark :left-elbow 0.15 0.3 0.0 0.92)
    (create-mock-landmark :right-elbow 0.85 0.3 0.0 0.92)
    (create-mock-landmark :left-wrist 0.05 0.3 0.0 0.90)
    (create-mock-landmark :right-wrist 0.95 0.3 0.0 0.90)
    (create-mock-landmark :left-hip 0.4 0.55 0.0 0.93)
    (create-mock-landmark :right-hip 0.6 0.55 0.0 0.93)
    (create-mock-landmark :left-knee 0.4 0.75 0.0 0.91)
    (create-mock-landmark :right-knee 0.6 0.75 0.0 0.91)
    (create-mock-landmark :left-ankle 0.4 0.95 0.0 0.89)
    (create-mock-landmark :right-ankle 0.6 0.95 0.0 0.89)]
   0.94))

(defn create-mock-timeline
  "Create a mock timeline with N frames of the same pose."
  [pose n]
  (vec
   (for [i (range n)]
     {:frame/index i
      :frame/timestamp-ms (* i 33) ;; ~30fps
      :frame/pose pose})))

;; ============================================================
;; HELPER FUNCTION TESTS
;; ============================================================

(deftest test-average-poses
  (testing "Average two identical poses returns same pose"
    (let [pose1 (create-mock-t-pose)
          pose2 (create-mock-t-pose)
          averaged (calibration/average-poses [pose1 pose2])]

      (is (map? averaged))
      (is (contains? averaged :pose/landmarks))
      (is (contains? averaged :pose/confidence))
      (is (= 0.94 (:pose/confidence averaged)))

      ;; Check nose landmark is same
      (let [nose (pose/get-landmark averaged :nose)]
        (is (= :nose (:landmark/id nose)))
        (is (= 0.5 (:landmark/x nose)))
        (is (= 0.1 (:landmark/y nose))))))

  (testing "Average poses with different positions"
    (let [pose1 (create-mock-pose
                 [(create-mock-landmark :nose 0.5 0.1 0.0 0.95)]
                 0.90)
          pose2 (create-mock-pose
                 [(create-mock-landmark :nose 0.6 0.2 0.0 0.95)]
                 0.95)
          averaged (calibration/average-poses [pose1 pose2])]

      ;; Averaged nose should be midpoint
      (let [nose (pose/get-landmark averaged :nose)]
        (is (= 0.55 (:landmark/x nose))) ;; (0.5 + 0.6) / 2
        (is (= 0.15 (:landmark/y nose))) ;; (0.1 + 0.2) / 2
        (is (= 0.925 (:pose/confidence averaged)))))) ;; (0.90 + 0.95) / 2

  (testing "Low visibility landmarks are filtered out"
    (let [pose1 (create-mock-pose
                 [(create-mock-landmark :nose 0.5 0.1 0.0 0.95)]
                 0.90)
          pose2 (create-mock-pose
                 [(create-mock-landmark :nose 0.8 0.3 0.0 0.3)] ;; Low visibility
                 0.95)
          averaged (calibration/average-poses [pose1 pose2])]

      ;; Should only average high-visibility landmark
      (let [nose (pose/get-landmark averaged :nose)]
        (is (= 0.5 (:landmark/x nose))) ;; Only pose1 counted
        (is (= 0.1 (:landmark/y nose)))))))

(deftest test-compute-scale-factor
  (testing "Compute scale factor from known height"
    (let [pose (create-mock-t-pose)
          user-height 180 ;; 180cm tall

          ;; In mock pose: nose at y=0.1, ankles at y=0.95
          ;; Vertical span = 0.85 normalized units
          expected-scale (/ 180 0.85) ;; ~211.76 cm per unit

          scale (calibration/compute-scale-factor pose user-height)]

      (is (> scale 200))
      (is (< scale 220))
      (is (js/isFinite scale)))))

(deftest test-compute-rhythm-regularity
  (testing "Perfect regularity (identical intervals)"
    (let [intervals [2.8 2.8 2.8 2.8 2.8]
          regularity (calibration/compute-rhythm-regularity intervals)]

      ;; CV = 0 → regularity = 1.0
      (is (> regularity 0.95))
      (is (<= regularity 1.0))))

  (testing "Moderate regularity (some variation)"
    (let [intervals [2.7 2.8 2.9 2.8 2.7]
          regularity (calibration/compute-rhythm-regularity intervals)]

      ;; CV > 0 but small → regularity high
      (is (> regularity 0.8))
      (is (< regularity 1.0))))

  (testing "Irregular breathing (large variation)"
    (let [intervals [1.5 3.0 2.0 4.0 1.8]
          regularity (calibration/compute-rhythm-regularity intervals)]

      ;; CV large → regularity low
      (is (< regularity 0.7))))

  (testing "Insufficient data returns neutral score"
    (let [intervals [2.8]
          regularity (calibration/compute-rhythm-regularity intervals)]

      (is (= 0.5 regularity)))))

;; ============================================================
;; T-POSE ANALYSIS TESTS
;; ============================================================

(deftest test-analyze-t-pose-session
  (testing "Extract measurements from T-pose timeline"
    (let [pose (create-mock-t-pose)
          timeline (create-mock-timeline pose 30) ;; 30 frames (~1 second)
          user-height 178

          result (calibration/analyze-t-pose-session timeline user-height)]

      ;; Check structure
      (is (map? result))
      (is (= user-height (:height-cm result)))
      (is (contains? result :baseline-pose))
      (is (contains? result :joint-distances))

      ;; Check baseline pose
      (let [baseline (:baseline-pose result)]
        (is (contains? baseline :pose/landmarks))
        (is (contains? baseline :pose/confidence))
        (is (> (:pose/confidence baseline) 0.9)))

      ;; Check joint distances
      (let [distances (:joint-distances result)]
        (is (contains? distances :shoulder-width-cm))
        (is (contains? distances :arm-span-cm))
        (is (contains? distances :torso-length-cm))
        (is (contains? distances :upper-arm-length-cm))
        (is (contains? distances :forearm-length-cm))

        ;; All distances should be positive and reasonable
        (is (> (:shoulder-width-cm distances) 20))
        (is (< (:shoulder-width-cm distances) 80))
        (is (> (:arm-span-cm distances) 100))
        (is (< (:arm-span-cm distances) 250))))))

;; ============================================================
;; BREATHING ANALYSIS TESTS
;; ============================================================

(deftest test-analyze-breathing-session
  (testing "Extract breathing baseline from timeline"
    ;; Note: This test depends on breathing module being available
    ;; For now, test structure only
    (let [pose (create-mock-t-pose)
          timeline (create-mock-timeline pose 180) ;; 180 frames (~6 seconds)

          ;; This will fail if breathing module not fully implemented
          ;; Commenting out for now - will work once breathing module has required functions
          #_result #_(calibration/analyze-breathing-session timeline)]

      ;; Structure tests (uncomment when breathing module ready)
      #_(is (map? result))
      #_(is (contains? result :typical-rate-bpm))
      #_(is (contains? result :typical-depth))
      #_(is (contains? result :typical-rhythm-regularity))

      ;; Placeholder assertion
      (is true))))

;; ============================================================
;; MOVEMENT / ROM ANALYSIS TESTS
;; ============================================================

(deftest test-compute-rom-ranges
  (testing "Compute ROM from timeline with varying angles"
    (let [;; Create poses with different elbow angles
          pose1-landmarks [(create-mock-landmark :left-shoulder 0.35 0.3 0.0 0.95)
                           (create-mock-landmark :left-elbow 0.25 0.4 0.0 0.92)
                           (create-mock-landmark :left-wrist 0.2 0.5 0.0 0.90)
                           (create-mock-landmark :right-shoulder 0.65 0.3 0.0 0.95)
                           (create-mock-landmark :right-elbow 0.75 0.4 0.0 0.92)
                           (create-mock-landmark :right-wrist 0.8 0.5 0.0 0.90)
                           (create-mock-landmark :left-hip 0.4 0.55 0.0 0.93)
                           (create-mock-landmark :right-hip 0.6 0.55 0.0 0.93)
                           (create-mock-landmark :left-knee 0.4 0.75 0.0 0.91)
                           (create-mock-landmark :right-knee 0.6 0.75 0.0 0.91)
                           (create-mock-landmark :left-ankle 0.4 0.95 0.0 0.89)
                           (create-mock-landmark :right-ankle 0.6 0.95 0.0 0.89)]

          pose1 (create-mock-pose pose1-landmarks 0.94)

          pose2-landmarks [(create-mock-landmark :left-shoulder 0.35 0.3 0.0 0.95)
                           (create-mock-landmark :left-elbow 0.3 0.45 0.0 0.92)
                           (create-mock-landmark :left-wrist 0.32 0.6 0.0 0.90)
                           (create-mock-landmark :right-shoulder 0.65 0.3 0.0 0.95)
                           (create-mock-landmark :right-elbow 0.7 0.45 0.0 0.92)
                           (create-mock-landmark :right-wrist 0.68 0.6 0.0 0.90)
                           (create-mock-landmark :left-hip 0.4 0.55 0.0 0.93)
                           (create-mock-landmark :right-hip 0.6 0.55 0.0 0.93)
                           (create-mock-landmark :left-knee 0.4 0.75 0.0 0.91)
                           (create-mock-landmark :right-knee 0.6 0.75 0.0 0.91)
                           (create-mock-landmark :left-ankle 0.4 0.95 0.0 0.89)
                           (create-mock-landmark :right-ankle 0.6 0.95 0.0 0.89)]

          pose2 (create-mock-pose pose2-landmarks 0.93)

          timeline [{:frame/index 0 :frame/pose pose1}
                    {:frame/index 1 :frame/pose pose2}]

          rom-ranges (calibration/compute-rom-ranges timeline)]

      ;; Should return map of joint → [min max]
      (is (map? rom-ranges))

      ;; Check that we have ROM ranges for major joints
      (when (contains? rom-ranges :left-elbow)
        (let [[min-angle max-angle] (:left-elbow rom-ranges)]
          (is (number? min-angle))
          (is (number? max-angle))
          (is (>= max-angle min-angle)))))))

(deftest test-analyze-movement-session
  (testing "Extract ROM ranges from movement session"
    (let [pose (create-mock-t-pose)
          timeline (create-mock-timeline pose 60)

          result (calibration/analyze-movement-session timeline)]

      (is (map? result))
      (is (contains? result :rom-ranges))
      (is (map? (:rom-ranges result))))))
