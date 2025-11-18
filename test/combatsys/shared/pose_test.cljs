(ns combatsys.shared.pose-test
  "Tests for pose processing functions.

  Run with: npx shadow-cljs compile test
  Then check console for results"
  (:require [combatsys.shared.pose :as pose]))

;; ============================================================================
;; Test Helpers
;; ============================================================================

(defn print-test-result
  "Print test result to console."
  [test-name passed? details]
  (if passed?
    (js/console.log "✅" test-name "PASSED" (when details (str "- " details)))
    (js/console.error "❌" test-name "FAILED" (when details (str "- " details)))))

(defn close-enough?
  "Check if two numbers are within tolerance."
  [a b tolerance]
  (<= (js/Math.abs (- a b)) tolerance))

;; ============================================================================
;; Tests
;; ============================================================================

(defn test-get-landmark
  "Test landmark lookup by ID."
  []
  (let [pose {:pose/landmarks
              [{:landmark/id :left-elbow :landmark/x 100 :landmark/y 200}
               {:landmark/id :right-elbow :landmark/x 300 :landmark/y 200}]}

        found (pose/get-landmark pose :left-elbow)
        not-found (pose/get-landmark pose :nose)]

    (print-test-result
     "get-landmark: found"
     (= (:landmark/id found) :left-elbow)
     (str "x=" (:landmark/x found) " y=" (:landmark/y found)))

    (print-test-result
     "get-landmark: not found"
     (nil? not-found)
     "returns nil for missing landmark")))

(defn test-landmark-visible
  "Test visibility threshold checking."
  []
  (let [high-conf {:landmark/confidence 0.9}
        low-conf {:landmark/confidence 0.3}
        no-conf {}]

    (print-test-result
     "landmark-visible?: high confidence"
     (pose/landmark-visible? high-conf 0.5)
     "0.9 >= 0.5")

    (print-test-result
     "landmark-visible?: low confidence"
     (not (pose/landmark-visible? low-conf 0.5))
     "0.3 < 0.5")

    (print-test-result
     "landmark-visible?: no confidence"
     (not (pose/landmark-visible? no-conf 0.5))
     "missing confidence treated as 0")))

(defn test-vector-operations
  "Test 2D vector math operations."
  []
  (let [v1 [3 4]
        v2 [1 2]]

    ;; Subtraction
    (let [result (pose/vec2-subtract v1 v2)]
      (print-test-result
       "vec2-subtract"
       (and (= (first result) 2) (= (second result) 2))
       "[3,4] - [1,2] = [2,2]"))

    ;; Dot product
    (let [result (pose/vec2-dot v1 v2)]
      (print-test-result
       "vec2-dot"
       (= result 11)
       "3*1 + 4*2 = 11"))

    ;; Magnitude
    (let [result (pose/vec2-magnitude v1)]
      (print-test-result
       "vec2-magnitude"
       (close-enough? result 5.0 0.001)
       "sqrt(3^2 + 4^2) = 5"))

    ;; Clamp
    (print-test-result
     "clamp: within range"
     (= (pose/clamp 0.5 0 1) 0.5)
     "0.5 ∈ [0,1]")

    (print-test-result
     "clamp: above max"
     (= (pose/clamp 1.5 0 1) 1.0)
     "1.5 clamped to 1.0")

    (print-test-result
     "clamp: below min"
     (= (pose/clamp -0.5 0 1) 0.0)
     "-0.5 clamped to 0.0")))

(defn test-joint-angle-computation
  "Test angle computation between three points."
  []
  ;; Test 90-degree angle (right angle)
  (let [a {:landmark/x 0 :landmark/y 0}
        b {:landmark/x 0 :landmark/y 10}
        c {:landmark/x 10 :landmark/y 10}
        angle (pose/compute-joint-angle a b c)]

    (print-test-result
     "compute-joint-angle: 90 degrees"
     (close-enough? angle 90.0 0.5)
     (str "expected ~90°, got " (.toFixed angle 2) "°")))

  ;; Test 180-degree angle (straight line)
  (let [a {:landmark/x 0 :landmark/y 0}
        b {:landmark/x 10 :landmark/y 0}
        c {:landmark/x 20 :landmark/y 0}
        angle (pose/compute-joint-angle a b c)]

    (print-test-result
     "compute-joint-angle: 180 degrees"
     (close-enough? angle 180.0 0.5)
     (str "expected ~180°, got " (.toFixed angle 2) "°")))

  ;; Test 45-degree angle
  (let [a {:landmark/x 0 :landmark/y 0}
        b {:landmark/x 0 :landmark/y 10}
        c {:landmark/x 10 :landmark/y 0}
        angle (pose/compute-joint-angle a b c)]

    (print-test-result
     "compute-joint-angle: 45 degrees"
     (close-enough? angle 45.0 0.5)
     (str "expected ~45°, got " (.toFixed angle 2) "°")))

  ;; Test missing landmark
  (let [a {:landmark/x 0 :landmark/y 0}
        b {:landmark/x 10 :landmark/y 10}
        c nil
        angle (pose/compute-joint-angle a b c)]

    (print-test-result
     "compute-joint-angle: missing landmark"
     (nil? angle)
     "returns nil when landmark missing")))

(defn test-compute-all-angles
  "Test computation of all joint angles."
  []
  (let [pose (pose/create-mock-pose)
        angles (pose/compute-all-angles pose)]

    (print-test-result
     "compute-all-angles: returns map"
     (map? angles)
     (str "got " (count angles) " angles"))

    (print-test-result
     "compute-all-angles: has elbow angles"
     (and (contains? angles :left-elbow)
          (contains? angles :right-elbow))
     "left-elbow and right-elbow present")

    (print-test-result
     "compute-all-angles: has knee angles"
     (and (contains? angles :left-knee)
          (contains? angles :right-knee))
     "left-knee and right-knee present")

    ;; Check angle ranges (0-180)
    (let [all-valid? (every? (fn [[_ angle]]
                               (and (>= angle 0) (<= angle 180)))
                            angles)]
      (print-test-result
       "compute-all-angles: angles in valid range"
       all-valid?
       "all angles ∈ [0°, 180°]"))

    ;; Log angles for inspection
    (js/console.log "📐 Computed angles:" (clj->js angles))))

(defn test-angle-validation
  "Test angle validation function."
  []
  (let [valid-angles {:left-elbow 145.0
                      :right-elbow 150.0
                      :left-knee 175.0}
        validation (pose/validate-angles valid-angles)]

    (print-test-result
     "validate-angles: valid angles pass"
     (:valid? validation)
     (str "errors: " (vec (:errors validation)))))

  ;; Test invalid angles
  (let [invalid-angles {:left-elbow js/NaN
                        :right-elbow 200.0
                        :left-knee -10.0}
        validation (pose/validate-angles invalid-angles)]

    (print-test-result
     "validate-angles: invalid angles caught"
     (not (:valid? validation))
     (str (count (:errors validation)) " errors detected"))))

(defn test-enhance-pose-with-angles
  "Test pose enhancement with angle computation."
  []
  (let [raw-pose (pose/create-mock-pose)
        enhanced (pose/enhance-pose-with-angles raw-pose {:measure-time? true})]

    (print-test-result
     "enhance-pose: adds :pose/angles"
     (contains? enhanced :pose/angles)
     (str (count (:pose/angles enhanced)) " angles"))

    (print-test-result
     "enhance-pose: preserves original data"
     (and (= (:pose/landmarks enhanced) (:pose/landmarks raw-pose))
          (= (:pose/confidence enhanced) (:pose/confidence raw-pose)))
     "landmarks and confidence preserved")

    (print-test-result
     "enhance-pose: adds metadata"
     (contains? (:pose/metadata enhanced) :angles-computation-ms)
     (str "took " (.toFixed (get-in enhanced [:pose/metadata :angles-computation-ms]) 2) "ms"))))

(defn test-performance
  "Test angle computation performance."
  []
  (js/console.log "\n🏎️  Performance Profiling...")
  (let [result (pose/profile-pose-processing 1000)]

    (js/console.log "   Total time:" (.toFixed (:total-ms result) 2) "ms for 1000 iterations")
    (js/console.log "   Average per frame:" (.toFixed (:avg-ms result) 3) "ms")
    (js/console.log "   Min:" (.toFixed (:min-ms result) 3) "ms")
    (js/console.log "   Max:" (.toFixed (:max-ms result) 3) "ms")

    (print-test-result
     "performance: <1ms target met"
     (:target-met? result)
     (str "avg=" (.toFixed (:avg-ms result) 3) "ms"))))

;; ============================================================================
;; Run All Tests
;; ============================================================================

(defn run-all-tests
  "Run all pose processing tests."
  []
  (js/console.log "\n╔════════════════════════════════════════╗")
  (js/console.log "║  Pose Processing Test Suite          ║")
  (js/console.log "╚════════════════════════════════════════╝\n")

  (js/console.log "🧪 Testing Helper Functions...")
  (test-get-landmark)
  (test-landmark-visible)

  (js/console.log "\n🧪 Testing Vector Math...")
  (test-vector-operations)

  (js/console.log "\n🧪 Testing Angle Computation...")
  (test-joint-angle-computation)
  (test-compute-all-angles)

  (js/console.log "\n🧪 Testing Validation...")
  (test-angle-validation)

  (js/console.log "\n🧪 Testing Pose Enhancement...")
  (test-enhance-pose-with-angles)

  (js/console.log "\n🧪 Testing Performance...")
  (test-performance)

  (js/console.log "\n✨ Test suite complete!\n"))

;; Run tests automatically when file is loaded
(run-all-tests)
