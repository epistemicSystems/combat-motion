(ns combatsys.schema-test
  (:require [cljs.test :refer [deftest is testing]]
            [combatsys.schema :as schema]
            [clojure.spec.alpha :as s]))

;; ============================================================
;; USER PROFILE VALIDATION TESTS
;; ============================================================

(deftest test-valid-minimal-user-profile
  (testing "Valid minimal user profile conforms to spec"
    (let [profile {:user-id (random-uuid)
                   :height-cm 178
                   :baseline-pose {:landmarks [] :joint-distances {}}
                   :learned-thresholds
                   {:breathing-thresholds {:fatigue-threshold 0.4
                                           :rate-alert-threshold 4.0}
                    :posture-thresholds {:forward-head-alert-cm 5.5
                                         :shoulder-imbalance-alert-deg 3.0}
                    :balance-thresholds {:stability-alert-threshold 0.6}}
                   :last-calibration-date (js/Date.)
                   :calibration-count 1}]
      (is (s/valid? ::schema/user-profile profile))
      (is (schema/valid-user-profile? profile))
      (is (nil? (schema/explain-user-profile profile))))))

(deftest test-valid-profile-with-optional-fields
  (testing "Valid profile with all optional fields"
    (let [profile {:user-id (random-uuid)
                   :height-cm 175
                   :baseline-pose {:landmarks [] :joint-distances {:shoulder-width-cm 42.5}}
                   :learned-thresholds
                   {:breathing-thresholds {:fatigue-threshold 0.4
                                           :rate-alert-threshold 4.0}
                    :posture-thresholds {:forward-head-alert-cm 5.5
                                         :shoulder-imbalance-alert-deg 3.0}
                    :balance-thresholds {:stability-alert-threshold 0.6}}
                   :last-calibration-date (js/Date.)
                   :calibration-count 3
                   ;; Optional fields:
                   :breathing-baseline {:typical-rate-bpm 21.5
                                        :typical-depth 0.82
                                        :typical-rhythm-regularity 0.89}
                   :posture-baseline {:typical-forward-head-cm 3.8
                                      :typical-shoulder-imbalance-deg 1.2}
                   :rom-ranges {:left-elbow [0 145] :right-shoulder [0 168]}}]
      (is (s/valid? ::schema/user-profile profile))
      (is (schema/valid-user-profile? profile)))))

(deftest test-invalid-user-id
  (testing "Invalid user-id (not UUID) is rejected"
    (let [bad-profile {:user-id "not-a-uuid"  ;; Invalid
                       :height-cm 175
                       :baseline-pose {:landmarks [] :joint-distances {}}
                       :learned-thresholds
                       {:breathing-thresholds {:fatigue-threshold 0.4
                                               :rate-alert-threshold 4.0}
                        :posture-thresholds {:forward-head-alert-cm 5.5
                                             :shoulder-imbalance-alert-deg 3.0}
                        :balance-thresholds {:stability-alert-threshold 0.6}}
                       :last-calibration-date (js/Date.)
                       :calibration-count 1}]
      (is (not (s/valid? ::schema/user-profile bad-profile)))
      (is (not (schema/valid-user-profile? bad-profile)))
      (is (string? (schema/explain-user-profile bad-profile))))))

(deftest test-invalid-height
  (testing "Invalid height (negative) is rejected"
    (let [bad-profile {:user-id (random-uuid)
                       :height-cm -10  ;; Invalid
                       :baseline-pose {:landmarks [] :joint-distances {}}
                       :learned-thresholds
                       {:breathing-thresholds {:fatigue-threshold 0.4
                                               :rate-alert-threshold 4.0}
                        :posture-thresholds {:forward-head-alert-cm 5.5
                                             :shoulder-imbalance-alert-deg 3.0}
                        :balance-thresholds {:stability-alert-threshold 0.6}}
                       :last-calibration-date (js/Date.)
                       :calibration-count 1}]
      (is (not (s/valid? ::schema/user-profile bad-profile))))))

(deftest test-missing-required-field
  (testing "Missing required field (baseline-pose) is rejected"
    (let [bad-profile {:user-id (random-uuid)
                       :height-cm 175
                       ;; Missing :baseline-pose
                       :learned-thresholds
                       {:breathing-thresholds {:fatigue-threshold 0.4
                                               :rate-alert-threshold 4.0}
                        :posture-thresholds {:forward-head-alert-cm 5.5
                                             :shoulder-imbalance-alert-deg 3.0}
                        :balance-thresholds {:stability-alert-threshold 0.6}}
                       :last-calibration-date (js/Date.)
                       :calibration-count 1}]
      (is (not (s/valid? ::schema/user-profile bad-profile))))))

(deftest test-invalid-breathing-baseline
  (testing "Invalid breathing baseline (out of range depth) is rejected"
    (let [bad-profile {:user-id (random-uuid)
                       :height-cm 178
                       :baseline-pose {:landmarks [] :joint-distances {}}
                       :learned-thresholds
                       {:breathing-thresholds {:fatigue-threshold 0.4
                                               :rate-alert-threshold 4.0}
                        :posture-thresholds {:forward-head-alert-cm 5.5
                                             :shoulder-imbalance-alert-deg 3.0}
                        :balance-thresholds {:stability-alert-threshold 0.6}}
                       :last-calibration-date (js/Date.)
                       :calibration-count 1
                       :breathing-baseline {:typical-rate-bpm 21.5
                                            :typical-depth 1.5  ;; Invalid (> 1.0)
                                            :typical-rhythm-regularity 0.89}}]
      (is (not (s/valid? ::schema/user-profile bad-profile))))))

;; ============================================================
;; CALIBRATION SESSION VALIDATION TESTS
;; ============================================================

(deftest test-valid-calibration-sessions
  (testing "Valid set of 3 calibration sessions"
    (let [sessions [{:calibration-type :t-pose
                     :session-id (random-uuid)
                     :created-at (js/Date.)
                     :duration-ms 10000
                     :timeline []}
                    {:calibration-type :breathing
                     :session-id (random-uuid)
                     :created-at (js/Date.)
                     :duration-ms 60000
                     :timeline []}
                    {:calibration-type :movement
                     :session-id (random-uuid)
                     :created-at (js/Date.)
                     :duration-ms 60000
                     :timeline []}]]
      (is (schema/validate-calibration-sessions sessions)))))

(deftest test-missing-calibration-type
  (testing "Missing a calibration type is invalid"
    (let [sessions [{:calibration-type :t-pose
                     :session-id (random-uuid)
                     :created-at (js/Date.)
                     :duration-ms 10000
                     :timeline []}
                    {:calibration-type :breathing
                     :session-id (random-uuid)
                     :created-at (js/Date.)
                     :duration-ms 60000
                     :timeline []}]]
      (is (not (schema/validate-calibration-sessions sessions))))))

(deftest test-duplicate-calibration-types
  (testing "Duplicate calibration types allowed (use latest)"
    (let [sessions [{:calibration-type :t-pose
                     :session-id (random-uuid)
                     :created-at (js/Date.)
                     :duration-ms 10000
                     :timeline []}
                    {:calibration-type :breathing
                     :session-id (random-uuid)
                     :created-at (js/Date.)
                     :duration-ms 60000
                     :timeline []}
                    {:calibration-type :movement
                     :session-id (random-uuid)
                     :created-at (js/Date.)
                     :duration-ms 60000
                     :timeline []}
                    {:calibration-type :t-pose  ;; Duplicate
                     :session-id (random-uuid)
                     :created-at (js/Date.)
                     :duration-ms 10000
                     :timeline []}]]
      (is (schema/validate-calibration-sessions sessions)))))

;; ============================================================
;; CONSTRUCTOR FUNCTION TESTS
;; ============================================================

(deftest test-new-user-profile-constructor
  (testing "Constructor creates valid profile"
    (let [user-id (random-uuid)
          height-cm 178
          profile (schema/new-user-profile user-id height-cm)]
      (is (s/valid? ::schema/user-profile profile))
      (is (= user-id (:user-id profile)))
      (is (= height-cm (:height-cm profile)))
      (is (= 0 (:calibration-count profile)))
      (is (inst? (:last-calibration-date profile)))
      (is (map? (:learned-thresholds profile)))
      ;; Check threshold computation
      (is (= (* 0.03 height-cm)
             (get-in profile [:learned-thresholds
                              :posture-thresholds
                              :forward-head-alert-cm]))))))

(deftest test-constructor-with-invalid-height
  (testing "Constructor rejects invalid height"
    (let [user-id (random-uuid)]
      (is (thrown? js/Error
                   (schema/new-user-profile user-id -10))))))

;; ============================================================
;; HELPER FUNCTION TESTS
;; ============================================================

(deftest test-get-breathing-fatigue-threshold
  (testing "Extract breathing fatigue threshold from profile"
    (let [profile {:learned-thresholds
                   {:breathing-thresholds {:fatigue-threshold 0.42}}}]
      (is (= 0.42 (schema/get-breathing-fatigue-threshold profile)))))

  (testing "Returns default when profile is nil"
    (is (= 0.3 (schema/get-breathing-fatigue-threshold nil))))

  (testing "Returns default when threshold is missing"
    (let [profile {:learned-thresholds {}}]
      (is (= 0.3 (schema/get-breathing-fatigue-threshold profile))))))

(deftest test-get-posture-forward-head-alert
  (testing "Extract forward head alert from profile"
    (let [profile {:learned-thresholds
                   {:posture-thresholds {:forward-head-alert-cm 6.5}}}]
      (is (= 6.5 (schema/get-posture-forward-head-alert profile)))))

  (testing "Returns default when profile is nil"
    (is (= 5.0 (schema/get-posture-forward-head-alert nil))))

  (testing "Returns default when threshold is missing"
    (let [profile {:learned-thresholds {}}]
      (is (= 5.0 (schema/get-posture-forward-head-alert profile))))))

;; ============================================================
;; SPEC-SPECIFIC TESTS
;; ============================================================

(deftest test-breathing-baseline-spec
  (testing "Valid breathing baseline"
    (let [baseline {:typical-rate-bpm 21.5
                    :typical-depth 0.82
                    :typical-rhythm-regularity 0.89}]
      (is (s/valid? ::schema/breathing-baseline baseline))))

  (testing "Invalid rate (negative)"
    (let [baseline {:typical-rate-bpm -5
                    :typical-depth 0.82
                    :typical-rhythm-regularity 0.89}]
      (is (not (s/valid? ::schema/breathing-baseline baseline))))))

(deftest test-posture-baseline-spec
  (testing "Valid posture baseline"
    (let [baseline {:typical-forward-head-cm 3.8
                    :typical-shoulder-imbalance-deg 1.2}]
      (is (s/valid? ::schema/posture-baseline baseline))))

  (testing "Forward head can be negative (head behind shoulders)"
    (let [baseline {:typical-forward-head-cm -2.0
                    :typical-shoulder-imbalance-deg 0.5}]
      (is (s/valid? ::schema/posture-baseline baseline)))))

(deftest test-learned-thresholds-spec
  (testing "Valid learned thresholds"
    (let [thresholds {:breathing-thresholds {:fatigue-threshold 0.4
                                             :rate-alert-threshold 4.0}
                      :posture-thresholds {:forward-head-alert-cm 5.5
                                           :shoulder-imbalance-alert-deg 3.0}
                      :balance-thresholds {:stability-alert-threshold 0.6}}]
      (is (s/valid? ::schema/learned-thresholds thresholds))))

  (testing "Missing balance thresholds is invalid"
    (let [thresholds {:breathing-thresholds {:fatigue-threshold 0.4
                                             :rate-alert-threshold 4.0}
                      :posture-thresholds {:forward-head-alert-cm 5.5
                                           :shoulder-imbalance-alert-deg 3.0}}]
      (is (not (s/valid? ::schema/learned-thresholds thresholds))))))

(deftest test-rom-ranges-spec
  (testing "Valid ROM ranges"
    (let [rom {:left-elbow [0 145]
               :right-shoulder [10 175]
               :hip-flexion [0 120]}]
      (is (s/valid? ::schema/rom-ranges rom))))

  (testing "Invalid ROM range (not a tuple)"
    (let [rom {:left-elbow [0 145 200]}]  ;; 3 elements instead of 2
      (is (not (s/valid? ::schema/rom-ranges rom))))))
