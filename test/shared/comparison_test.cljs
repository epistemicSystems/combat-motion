(ns combatsys.comparison-test
  "Unit tests for session comparison logic."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [combatsys.comparison :as comp]))

;; ============================================================================
;; MOCK DATA
;; ============================================================================

(def mock-session-a
  "Mock session with baseline metrics"
  {:session/id #uuid "00000000-0000-0000-0000-000000000001"
   :session/name "Morning Training Session"
   :session/created-at "2025-11-01T08:00:00Z"
   :session/duration-ms 30000
   :session/frame-count 450
   :session/analysis
   {:breathing {:rate-bpm 20
                :depth-score 0.75
                :fatigue-windows [{:start-ms 5000 :end-ms 8000}
                                  {:start-ms 15000 :end-ms 18000}]}
    :posture {:overall-score 0.78
              :head-forward-cm 5.0
              :shoulder-imbalance-deg 2.0}}})

(def mock-session-b-improved
  "Mock session with improved metrics"
  {:session/id #uuid "00000000-0000-0000-0000-000000000002"
   :session/name "Evening Training Session"
   :session/created-at "2025-11-15T18:00:00Z"
   :session/duration-ms 45000
   :session/frame-count 675
   :session/analysis
   {:breathing {:rate-bpm 22
                :depth-score 0.82
                :fatigue-windows [{:start-ms 20000 :end-ms 23000}]}
    :posture {:overall-score 0.84
              :head-forward-cm 3.5
              :shoulder-imbalance-deg 1.5}}})

(def mock-session-c-declined
  "Mock session with declined metrics"
  {:session/id #uuid "00000000-0000-0000-0000-000000000003"
   :session/name "Late Night Session"
   :session/created-at "2025-11-20T22:00:00Z"
   :session/duration-ms 20000
   :session/frame-count 300
   :session/analysis
   {:breathing {:rate-bpm 18
                :depth-score 0.68
                :fatigue-windows [{:start-ms 2000 :end-ms 5000}
                                  {:start-ms 8000 :end-ms 11000}
                                  {:start-ms 14000 :end-ms 17000}]}
    :posture {:overall-score 0.72
              :head-forward-cm 6.5
              :shoulder-imbalance-deg 3.2}}})

;; ============================================================================
;; METRIC COMPARISON TESTS
;; ============================================================================

(deftest test-compare-metric-increase-higher-better
  (testing "Compare metric with increase (higher is better)"
    (let [result (comp/compare-metric 20 22 true)]
      (is (= 20 (:metric-a result)))
      (is (= 22 (:metric-b result)))
      (is (= 2 (:delta result)))
      (is (= 10.0 (:pct-change result)))
      (is (= :increased (:direction result)))
      (is (true? (:improvement? result))))))

(deftest test-compare-metric-decrease-lower-better
  (testing "Compare metric with decrease (lower is better)"
    (let [result (comp/compare-metric 5.0 3.0 false)]
      (is (= 5.0 (:metric-a result)))
      (is (= 3.0 (:metric-b result)))
      (is (= -2.0 (:delta result)))
      (is (= -40.0 (:pct-change result)))
      (is (= :decreased (:direction result)))
      (is (true? (:improvement? result))))))  ;; Decreased, but improvement!

(deftest test-compare-metric-increase-lower-better
  (testing "Compare metric with increase (lower is better) - decline"
    (let [result (comp/compare-metric 3.0 5.0 false)]
      (is (= 3.0 (:metric-a result)))
      (is (= 5.0 (:metric-b result)))
      (is (> (:delta result) 0))
      (is (= :increased (:direction result)))
      (is (false? (:improvement? result))))))  ;; Increased, but NOT improvement

(deftest test-compare-metric-unchanged
  (testing "Compare metric with small change (unchanged)"
    (let [result (comp/compare-metric 100 103 true)]
      (is (= 3 (:delta result)))
      (is (= 3.0 (:pct-change result)))  ;; 3% is below 5% threshold
      (is (= :unchanged (:direction result)))
      (is (false? (:improvement? result))))))

(deftest test-compare-metric-zero-baseline
  (testing "Compare metric with zero baseline"
    (let [result (comp/compare-metric 0 10 true)]
      (is (= 10 (:delta result)))
      (is (= 0 (:pct-change result)))  ;; Can't divide by zero
      (is (= :increased (:direction result))))))

;; ============================================================================
;; BREATHING COMPARISON TESTS
;; ============================================================================

(deftest test-compare-breathing
  (testing "Compare breathing analysis"
    (let [breathing-a (get-in mock-session-a [:session/analysis :breathing])
          breathing-b (get-in mock-session-b-improved [:session/analysis :breathing])
          result (comp/compare-breathing breathing-a breathing-b)]

      (testing "rate comparison"
        (is (= 20 (get-in result [:rate-comparison :metric-a])))
        (is (= 22 (get-in result [:rate-comparison :metric-b])))
        (is (true? (get-in result [:rate-comparison :improvement?]))))

      (testing "depth comparison"
        (is (= 0.75 (get-in result [:depth-comparison :metric-a])))
        (is (= 0.82 (get-in result [:depth-comparison :metric-b])))
        (is (true? (get-in result [:depth-comparison :improvement?]))))

      (testing "fatigue comparison"
        (is (= 2 (get-in result [:fatigue-comparison :count-a])))
        (is (= 1 (get-in result [:fatigue-comparison :count-b])))
        (is (= -1 (get-in result [:fatigue-comparison :delta])))
        (is (true? (get-in result [:fatigue-comparison :improvement?])))))))

(deftest test-compare-breathing-nil-handling
  (testing "Compare breathing handles nil gracefully"
    (let [result (comp/compare-breathing nil {:rate-bpm 20})]
      (is (nil? result)))))

;; ============================================================================
;; POSTURE COMPARISON TESTS
;; ============================================================================

(deftest test-compare-posture
  (testing "Compare posture analysis"
    (let [posture-a (get-in mock-session-a [:session/analysis :posture])
          posture-b (get-in mock-session-b-improved [:session/analysis :posture])
          result (comp/compare-posture posture-a posture-b)]

      (testing "overall score comparison"
        (is (= 0.78 (get-in result [:overall-score-comparison :metric-a])))
        (is (= 0.84 (get-in result [:overall-score-comparison :metric-b])))
        (is (true? (get-in result [:overall-score-comparison :improvement?]))))

      (testing "forward head comparison"
        (is (= 5.0 (get-in result [:forward-head-comparison :metric-a])))
        (is (= 3.5 (get-in result [:forward-head-comparison :metric-b])))
        (is (true? (get-in result [:forward-head-comparison :improvement?]))))  ;; Lower is better

      (testing "shoulder imbalance comparison"
        (is (= 2.0 (get-in result [:shoulder-imbalance-comparison :metric-a])))
        (is (= 1.5 (get-in result [:shoulder-imbalance-comparison :metric-b])))
        (is (true? (get-in result [:shoulder-imbalance-comparison :improvement?])))))))  ;; Lower is better

(deftest test-compare-posture-nil-handling
  (testing "Compare posture handles nil gracefully"
    (let [result (comp/compare-posture nil {:overall-score 0.8})]
      (is (nil? result)))))

;; ============================================================================
;; OVERALL ASSESSMENT TESTS
;; ============================================================================

(deftest test-assess-overall-change-significant-improvement
  (testing "Assess overall change - significant improvement"
    (let [breathing-a (get-in mock-session-a [:session/analysis :breathing])
          breathing-b (get-in mock-session-b-improved [:session/analysis :breathing])
          posture-a (get-in mock-session-a [:session/analysis :posture])
          posture-b (get-in mock-session-b-improved [:session/analysis :posture])
          breathing-comp (comp/compare-breathing breathing-a breathing-b)
          posture-comp (comp/compare-posture posture-a posture-b)
          assessment (comp/assess-overall-change breathing-comp posture-comp)]
      ;; All metrics improved, should be significant
      (is (contains? #{:significant-improvement :slight-improvement} assessment)))))

(deftest test-assess-overall-change-significant-decline
  (testing "Assess overall change - significant decline"
    (let [breathing-a (get-in mock-session-a [:session/analysis :breathing])
          breathing-c (get-in mock-session-c-declined [:session/analysis :breathing])
          posture-a (get-in mock-session-a [:session/analysis :posture])
          posture-c (get-in mock-session-c-declined [:session/analysis :posture])
          breathing-comp (comp/compare-breathing breathing-a breathing-c)
          posture-comp (comp/compare-posture posture-a posture-c)
          assessment (comp/assess-overall-change breathing-comp posture-comp)]
      ;; All metrics declined, should be decline
      (is (contains? #{:significant-decline :slight-decline} assessment)))))

;; ============================================================================
;; INSIGHTS GENERATION TESTS
;; ============================================================================

(deftest test-generate-comparison-insights
  (testing "Generate comparison insights"
    (let [breathing-a (get-in mock-session-a [:session/analysis :breathing])
          breathing-b (get-in mock-session-b-improved [:session/analysis :breathing])
          posture-a (get-in mock-session-a [:session/analysis :posture])
          posture-b (get-in mock-session-b-improved [:session/analysis :posture])
          breathing-comp (comp/compare-breathing breathing-a breathing-b)
          posture-comp (comp/compare-posture posture-a posture-b)
          insights (comp/generate-comparison-insights breathing-comp posture-comp)]

      (testing "insights are generated"
        (is (vector? insights))
        (is (> (count insights) 0)))

      (testing "insights have required keys"
        (doseq [insight insights]
          (is (contains? insight :title))
          (is (contains? insight :description))
          (is (contains? insight :severity))))

      (testing "insights include breathing improvements"
        (is (some #(clojure.string/includes? (:title %) "Breathing") insights)))

      (testing "insights include posture improvements"
        (is (some #(clojure.string/includes? (:title %) "posture") insights))))))

(deftest test-generate-insights-negative
  (testing "Generate insights for declines"
    (let [breathing-a (get-in mock-session-a [:session/analysis :breathing])
          breathing-c (get-in mock-session-c-declined [:session/analysis :breathing])
          posture-a (get-in mock-session-a [:session/analysis :posture])
          posture-c (get-in mock-session-c-declined [:session/analysis :posture])
          breathing-comp (comp/compare-breathing breathing-a breathing-c)
          posture-comp (comp/compare-posture posture-a posture-c)
          insights (comp/generate-comparison-insights breathing-comp posture-comp)]

      (testing "negative insights have negative severity"
        (is (some #(= :negative (:severity %)) insights))))))

;; ============================================================================
;; FULL SESSION COMPARISON TESTS
;; ============================================================================

(deftest test-compare-sessions-full
  (testing "Full session comparison"
    (let [comparison (comp/compare-sessions mock-session-a mock-session-b-improved)]

      (testing "session metadata is preserved"
        (is (= (:session/id mock-session-a) (:session-a-id comparison)))
        (is (= (:session/id mock-session-b-improved) (:session-b-id comparison)))
        (is (= (:session/name mock-session-a) (:session-a-name comparison)))
        (is (= (:session/name mock-session-b-improved) (:session-b-name comparison)))
        (is (= (:session/created-at mock-session-a) (:session-a-date comparison)))
        (is (= (:session/created-at mock-session-b-improved) (:session-b-date comparison))))

      (testing "breathing comparison exists"
        (is (some? (:breathing-comparison comparison)))
        (is (true? (get-in comparison [:breathing-comparison
                                        :rate-comparison
                                        :improvement?]))))

      (testing "posture comparison exists"
        (is (some? (:posture-comparison comparison)))
        (is (true? (get-in comparison [:posture-comparison
                                        :forward-head-comparison
                                        :improvement?]))))

      (testing "overall assessment exists"
        (is (some? (:overall-assessment comparison)))
        (is (contains? #{:significant-improvement :slight-improvement :stable
                         :slight-decline :significant-decline}
                       (:overall-assessment comparison))))

      (testing "insights are generated"
        (is (vector? (:insights comparison)))
        (is (> (count (:insights comparison)) 0))))))

(deftest test-compare-sessions-declined
  (testing "Full session comparison with decline"
    (let [comparison (comp/compare-sessions mock-session-a mock-session-c-declined)]

      (testing "overall assessment reflects decline"
        (is (contains? #{:significant-decline :slight-decline}
                       (:overall-assessment comparison))))

      (testing "negative insights are present"
        (is (some #(= :negative (:severity %)) (:insights comparison)))))))

;; ============================================================================
;; EDGE CASES
;; ============================================================================

(deftest test-compare-sessions-same-session
  (testing "Compare session with itself"
    (let [comparison (comp/compare-sessions mock-session-a mock-session-a)]

      (testing "all deltas are zero"
        (is (= 0 (get-in comparison [:breathing-comparison
                                      :rate-comparison
                                      :delta])))
        (is (= 0 (get-in comparison [:posture-comparison
                                      :overall-score-comparison
                                      :delta]))))

      (testing "overall assessment is stable"
        (is (= :stable (:overall-assessment comparison)))))))

(deftest test-compare-metric-negative-values
  (testing "Compare metric with negative values"
    (let [result (comp/compare-metric -5 -3 true)]
      (is (= -5 (:metric-a result)))
      (is (= -3 (:metric-b result)))
      (is (= 2 (:delta result)))
      (is (= :increased (:direction result))))))
