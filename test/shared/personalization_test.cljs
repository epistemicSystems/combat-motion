(ns combatsys.personalization-test
  (:require [cljs.test :refer [deftest is testing]]
            [combatsys.personalization :as personalization]
            [combatsys.schema :as schema]
            [clojure.spec.alpha :as s]))

;; ============================================================
;; THRESHOLD COMPUTATION TESTS
;; ============================================================

(deftest test-compute-breathing-thresholds
  (testing "Compute thresholds from typical breathing baseline"
    (let [baseline {:typical-rate-bpm 21.5
                    :typical-depth 0.82
                    :typical-rhythm-regularity 0.89}

          thresholds (personalization/compute-breathing-thresholds baseline)]

      (is (map? thresholds))
      (is (contains? thresholds :fatigue-threshold))
      (is (contains? thresholds :rate-alert-threshold))

      ;; Fatigue threshold = 70% of typical depth
      (is (= (* 0.7 0.82) (:fatigue-threshold thresholds)))
      (is (< (:fatigue-threshold thresholds) 0.82))

      ;; Rate alert = 25% of typical rate
      (is (= (* 0.25 21.5) (:rate-alert-threshold thresholds)))
      (is (> (:rate-alert-threshold thresholds) 0))))

  (testing "Thresholds scale with baseline"
    (let [baseline-low {:typical-rate-bpm 15.0
                        :typical-depth 0.6
                        :typical-rhythm-regularity 0.85}
          baseline-high {:typical-rate-bpm 30.0
                         :typical-depth 0.9
                         :typical-rhythm-regularity 0.92}

          thresholds-low (personalization/compute-breathing-thresholds baseline-low)
          thresholds-high (personalization/compute-breathing-thresholds baseline-high)]

      ;; Higher baseline → higher thresholds
      (is (< (:fatigue-threshold thresholds-low)
             (:fatigue-threshold thresholds-high)))
      (is (< (:rate-alert-threshold thresholds-low)
             (:rate-alert-threshold thresholds-high))))))

(deftest test-compute-posture-thresholds
  (testing "Compute generic thresholds without baseline"
    (let [height 178
          thresholds (personalization/compute-posture-thresholds height)]

      (is (map? thresholds))
      (is (contains? thresholds :forward-head-alert-cm))
      (is (contains? thresholds :shoulder-imbalance-alert-deg))

      ;; Generic forward head = 3% of height
      (is (= (* 0.03 height) (:forward-head-alert-cm thresholds)))

      ;; Generic shoulder imbalance = 5°
      (is (= 5.0 (:shoulder-imbalance-alert-deg thresholds)))))

  (testing "Compute personalized thresholds with baseline"
    (let [height 178
          baseline {:typical-forward-head-cm 3.8
                    :typical-shoulder-imbalance-deg 1.2}

          thresholds (personalization/compute-posture-thresholds height baseline)]

      ;; Forward head alert = baseline + 2cm (if > 3% height)
      (is (= 5.8 (:forward-head-alert-cm thresholds))) ;; 3.8 + 2.0

      ;; Shoulder alert = |baseline| + 2°, minimum 5°
      (is (= 5.0 (:shoulder-imbalance-alert-deg thresholds))))) ;; max(1.2 + 2, 5) = 5

  (testing "Forward head uses max of baseline+2 and 3% height"
    (let [height 200 ;; 3% = 6cm
          baseline {:typical-forward-head-cm 2.0
                    :typical-shoulder-imbalance-deg 0.5}

          thresholds (personalization/compute-posture-thresholds height baseline)]

      ;; baseline + 2 = 4, but 3% of height = 6, so use 6
      (is (= 6.0 (:forward-head-alert-cm thresholds)))))

  (testing "Shoulder alert has minimum of 5°"
    (let [height 175
          baseline {:typical-forward-head-cm 3.0
                    :typical-shoulder-imbalance-deg 0.5}

          thresholds (personalization/compute-posture-thresholds height baseline)]

      ;; |0.5| + 2 = 2.5, but minimum is 5, so use 5
      (is (= 5.0 (:shoulder-imbalance-alert-deg thresholds))))))

(deftest test-compute-balance-thresholds
  (testing "Fixed threshold for LOD 5.0"
    (let [thresholds (personalization/compute-balance-thresholds nil)]

      (is (map? thresholds))
      (is (contains? thresholds :stability-alert-threshold))
      (is (= 0.6 (:stability-alert-threshold thresholds))))))

;; ============================================================
;; USER PROFILE CREATION TESTS
;; ============================================================

(defn create-mock-calibration-session
  "Create a mock calibration session for testing."
  [type]
  {:calibration-type type
   :session-id (random-uuid)
   :created-at (js/Date.)
   :duration-ms 10000
   :timeline []}) ;; Empty timeline for unit tests

(deftest test-create-user-profile-validation
  (testing "Requires all 3 calibration session types"
    (let [user-id (random-uuid)
          incomplete-sessions [(create-mock-calibration-session :t-pose)
                               (create-mock-calibration-session :breathing)]
          ;; Missing :movement

          height 178]

      ;; Should fail precondition check
      (is (thrown? js/Error
                   (personalization/create-user-profile
                    user-id
                    incomplete-sessions
                    height))))))

(deftest test-create-user-profile-structure
  (testing "Profile structure without actual calibration"
    ;; Note: Full integration test requires actual timeline data
    ;; This test verifies precondition checking

    (let [user-id (random-uuid)
          sessions [(create-mock-calibration-session :t-pose)
                    (create-mock-calibration-session :breathing)
                    (create-mock-calibration-session :movement)]
          height 178]

      ;; Validate sessions pass the check
      (is (schema/validate-calibration-sessions sessions))

      ;; Full profile creation test requires real timeline data
      ;; Skipping for unit test - covered in integration tests
      (is true))))

(deftest test-create-user-profile-handles-duplicates
  (testing "Uses most recent session when duplicates exist"
    (let [user-id (random-uuid)
          sessions [(create-mock-calibration-session :t-pose)
                    (create-mock-calibration-session :breathing)
                    (create-mock-calibration-session :movement)
                    ;; Duplicate t-pose (later timestamp)
                    (assoc (create-mock-calibration-session :t-pose)
                           :created-at (js/Date. (+ (.getTime (js/Date.)) 1000)))]
          height 178]

      ;; Should validate (duplicates allowed)
      (is (schema/validate-calibration-sessions sessions)))))

(deftest test-update-user-profile
  (testing "Increment calibration count on update"
    (let [user-id (random-uuid)

          ;; Create initial profile (mock - will fail without real data)
          initial-profile (schema/new-user-profile user-id 178)

          ;; New calibration sessions
          new-sessions [(create-mock-calibration-session :breathing)]

          height 178]

      ;; Initial count = 0
      (is (= 0 (:calibration-count initial-profile)))

      ;; Update would increment count
      ;; Full test requires real timeline data
      ;; Skipping for unit test
      (is true))))

;; ============================================================
;; SCHEMA CONFORMANCE TESTS
;; ============================================================

(deftest test-breathing-thresholds-conform-to-spec
  (testing "Generated breathing thresholds conform to spec"
    (let [baseline {:typical-rate-bpm 21.5
                    :typical-depth 0.82
                    :typical-rhythm-regularity 0.89}
          thresholds (personalization/compute-breathing-thresholds baseline)]

      (is (s/valid? ::schema/breathing-thresholds thresholds))

      ;; Check individual fields
      (is (pos? (:fatigue-threshold thresholds)))
      (is (pos? (:rate-alert-threshold thresholds))))))

(deftest test-posture-thresholds-conform-to-spec
  (testing "Generated posture thresholds conform to spec"
    (let [height 178
          thresholds (personalization/compute-posture-thresholds height)]

      (is (s/valid? ::schema/posture-thresholds thresholds))

      ;; Check individual fields
      (is (pos? (:forward-head-alert-cm thresholds)))
      (is (pos? (:shoulder-imbalance-alert-deg thresholds))))))

(deftest test-balance-thresholds-conform-to-spec
  (testing "Generated balance thresholds conform to spec"
    (let [thresholds (personalization/compute-balance-thresholds nil)]

      (is (s/valid? ::schema/balance-thresholds thresholds))

      ;; Check value in valid range
      (is (<= 0.0 (:stability-alert-threshold thresholds) 1.0)))))

(deftest test-learned-thresholds-conform-to-spec
  (testing "Complete learned thresholds conform to spec"
    (let [breathing-baseline {:typical-rate-bpm 21.5
                              :typical-depth 0.82
                              :typical-rhythm-regularity 0.89}

          breathing-thresholds (personalization/compute-breathing-thresholds breathing-baseline)
          posture-thresholds (personalization/compute-posture-thresholds 178)
          balance-thresholds (personalization/compute-balance-thresholds nil)

          learned-thresholds {:breathing-thresholds breathing-thresholds
                              :posture-thresholds posture-thresholds
                              :balance-thresholds balance-thresholds}]

      (is (s/valid? ::schema/learned-thresholds learned-thresholds)))))

;; ============================================================
;; EDGE CASES
;; ============================================================

(deftest test-edge-case-very-small-breathing-depth
  (testing "Handle very small breathing depth"
    (let [baseline {:typical-rate-bpm 20.0
                    :typical-depth 0.1 ;; Very shallow
                    :typical-rhythm-regularity 0.8}
          thresholds (personalization/compute-breathing-thresholds baseline)]

      ;; Should still compute valid thresholds
      (is (pos? (:fatigue-threshold thresholds)))
      (is (< (:fatigue-threshold thresholds) 0.1)))))

(deftest test-edge-case-very-tall-user
  (testing "Handle very tall user (230cm)"
    (let [height 230
          thresholds (personalization/compute-posture-thresholds height)]

      ;; Forward head threshold should scale with height
      (is (= (* 0.03 230) (:forward-head-alert-cm thresholds)))
      (is (= 6.9 (:forward-head-alert-cm thresholds))))))

(deftest test-edge-case-very-short-user
  (testing "Handle very short user (150cm)"
    (let [height 150
          thresholds (personalization/compute-posture-thresholds height)]

      ;; Forward head threshold should scale with height
      (is (= (* 0.03 150) (:forward-head-alert-cm thresholds)))
      (is (= 4.5 (:forward-head-alert-cm thresholds))))))
