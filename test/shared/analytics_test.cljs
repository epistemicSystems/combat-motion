(ns combatsys.analytics-test
  "Unit tests for session analytics functions."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [combatsys.analytics :as analytics]))

;; ============================================================================
;; Mock Data
;; ============================================================================

(def mock-sessions
  "Mock session metadata for testing."
  [{:session/id #uuid "00000000-0000-0000-0000-000000000001"
    :session/name "Morning Training"
    :session/created-at "2025-11-15T08:00:00Z"
    :session/duration-ms 30000
    :session/frame-count 450
    :session/tags ["breathing"]
    :session/notes "Good session"
    :session/summary-stats {:avg-breathing-rate 21.5
                            :avg-posture-score 0.84}}
   {:session/id #uuid "00000000-0000-0000-0000-000000000002"
    :session/name "Evening Practice"
    :session/created-at "2025-11-18T18:00:00Z"
    :session/duration-ms 45000
    :session/frame-count 675
    :session/tags ["posture"]
    :session/notes "Felt tired"
    :session/summary-stats {:avg-breathing-rate 19.2
                            :avg-posture-score 0.78}}
   {:session/id #uuid "00000000-0000-0000-0000-000000000003"
    :session/name "Afternoon Session"
    :session/created-at "2025-11-20T14:00:00Z"
    :session/duration-ms 60000
    :session/frame-count 900
    :session/tags ["breathing" "posture"]
    :session/notes "Very productive"
    :session/summary-stats {:avg-breathing-rate 22.8
                            :avg-posture-score 0.91}}])

;; ============================================================================
;; Filter Tests
;; ============================================================================

(deftest test-filter-by-date-range
  (testing "Filter sessions by date range"
    (testing "with start date only"
      (let [result (analytics/filter-sessions-by-date-range
                    mock-sessions
                    "2025-11-16T00:00:00Z"
                    nil)]
        (is (= 2 (count result)))
        (is (= "Evening Practice" (:session/name (first result))))
        (is (= "Afternoon Session" (:session/name (second result))))))

    (testing "with end date only"
      (let [result (analytics/filter-sessions-by-date-range
                    mock-sessions
                    nil
                    "2025-11-16T00:00:00Z")]
        (is (= 1 (count result)))
        (is (= "Morning Training" (:session/name (first result))))))

    (testing "with both start and end date"
      (let [result (analytics/filter-sessions-by-date-range
                    mock-sessions
                    "2025-11-16T00:00:00Z"
                    "2025-11-19T00:00:00Z")]
        (is (= 1 (count result)))
        (is (= "Evening Practice" (:session/name (first result))))))

    (testing "with no date filters"
      (let [result (analytics/filter-sessions-by-date-range
                    mock-sessions
                    nil
                    nil)]
        (is (= 3 (count result)))))))

(deftest test-filter-by-search
  (testing "Filter sessions by search text"
    (testing "case-insensitive name search"
      (let [result (analytics/filter-sessions-by-search
                    mock-sessions
                    "morning")]
        (is (= 1 (count result)))
        (is (= "Morning Training" (:session/name (first result))))))

    (testing "case-insensitive notes search"
      (let [result (analytics/filter-sessions-by-search
                    mock-sessions
                    "tired")]
        (is (= 1 (count result)))
        (is (= "Evening Practice" (:session/name (first result))))))

    (testing "partial word match"
      (let [result (analytics/filter-sessions-by-search
                    mock-sessions
                    "session")]
        (is (= 2 (count result)))
        (is (some #(= "Morning Training" (:session/name %)) result))
        (is (some #(= "Afternoon Session" (:session/name %)) result))))

    (testing "empty search returns all"
      (let [result (analytics/filter-sessions-by-search
                    mock-sessions
                    "")]
        (is (= 3 (count result)))))

    (testing "nil search returns all"
      (let [result (analytics/filter-sessions-by-search
                    mock-sessions
                    nil)]
        (is (= 3 (count result)))))

    (testing "no matches returns empty"
      (let [result (analytics/filter-sessions-by-search
                    mock-sessions
                    "nonexistent")]
        (is (= 0 (count result)))))))

(deftest test-filter-by-tag
  (testing "Filter sessions by tag"
    (testing "single tag match"
      (let [result (analytics/filter-sessions-by-tag
                    mock-sessions
                    "breathing")]
        (is (= 2 (count result)))
        (is (some #(= "Morning Training" (:session/name %)) result))
        (is (some #(= "Afternoon Session" (:session/name %)) result))))

    (testing "different tag"
      (let [result (analytics/filter-sessions-by-tag
                    mock-sessions
                    "posture")]
        (is (= 2 (count result)))
        (is (some #(= "Evening Practice" (:session/name %)) result))
        (is (some #(= "Afternoon Session" (:session/name %)) result))))

    (testing "no matches"
      (let [result (analytics/filter-sessions-by-tag
                    mock-sessions
                    "nonexistent")]
        (is (= 0 (count result)))))

    (testing "nil tag returns all"
      (let [result (analytics/filter-sessions-by-tag
                    mock-sessions
                    nil)]
        (is (= 3 (count result)))))

    (testing "empty tag returns all"
      (let [result (analytics/filter-sessions-by-tag
                    mock-sessions
                    "")]
        (is (= 3 (count result)))))))

;; ============================================================================
;; Sort Tests
;; ============================================================================

(deftest test-sort-sessions
  (testing "Sort sessions by different keys"
    (testing "sort by date ascending"
      (let [result (analytics/sort-sessions mock-sessions :date true)]
        (is (= "Morning Training" (:session/name (first result))))
        (is (= "Afternoon Session" (:session/name (last result))))))

    (testing "sort by date descending"
      (let [result (analytics/sort-sessions mock-sessions :date false)]
        (is (= "Afternoon Session" (:session/name (first result))))
        (is (= "Morning Training" (:session/name (last result))))))

    (testing "sort by duration ascending"
      (let [result (analytics/sort-sessions mock-sessions :duration true)]
        (is (= 30000 (:session/duration-ms (first result))))
        (is (= 60000 (:session/duration-ms (last result))))))

    (testing "sort by duration descending"
      (let [result (analytics/sort-sessions mock-sessions :duration false)]
        (is (= 60000 (:session/duration-ms (first result))))
        (is (= 30000 (:session/duration-ms (last result))))))

    (testing "sort by name ascending"
      (let [result (analytics/sort-sessions mock-sessions :name true)]
        (is (= "Afternoon Session" (:session/name (first result))))
        (is (= "Morning Training" (:session/name (last result))))))

    (testing "sort by name descending"
      (let [result (analytics/sort-sessions mock-sessions :name false)]
        (is (= "Morning Training" (:session/name (first result))))
        (is (= "Afternoon Session" (:session/name (last result))))))))

;; ============================================================================
;; Aggregate Statistics Tests
;; ============================================================================

(deftest test-aggregate-stats
  (testing "Compute aggregate statistics"
    (let [stats (analytics/compute-aggregate-stats mock-sessions)]
      (testing "total sessions"
        (is (= 3 (:total-sessions stats))))

      (testing "total duration"
        (is (= 135000 (:total-duration-ms stats)))
        (is (= 0.0375 (:total-duration-hours stats))))

      (testing "total frames"
        (is (= 2025 (:total-frames stats))))

      (testing "average session duration"
        (is (= 45000 (:avg-session-duration-ms stats))))

      (testing "average breathing rate"
        (is (> (:avg-breathing-rate stats) 21.1))
        (is (< (:avg-breathing-rate stats) 21.2)))

      (testing "average posture score"
        (is (> (:avg-posture-score stats) 0.84))
        (is (< (:avg-posture-score stats) 0.85)))))

  (testing "empty session list"
    (let [stats (analytics/compute-aggregate-stats [])]
      (is (= 0 (:total-sessions stats)))
      (is (= 0 (:total-duration-ms stats)))
      (is (= 0 (:avg-session-duration-ms stats)))
      (is (= 0 (:avg-breathing-rate stats)))))

  (testing "sessions with missing analysis"
    (let [sessions-no-stats [{:session/id #uuid "00000000-0000-0000-0000-000000000001"
                              :session/duration-ms 10000
                              :session/frame-count 150}]
          stats (analytics/compute-aggregate-stats sessions-no-stats)]
      (is (= 1 (:total-sessions stats)))
      (is (= 10000 (:total-duration-ms stats)))
      (is (= 0 (:avg-breathing-rate stats)))
      (is (= 0 (:avg-posture-score stats))))))

;; ============================================================================
;; Best/Worst Session Tests
;; ============================================================================

(deftest test-find-best-session
  (testing "Find session with best metric"
    (testing "best breathing rate"
      (let [best (analytics/find-best-session mock-sessions [:avg-breathing-rate])]
        (is (= "Afternoon Session" (:session/name best)))
        (is (= 22.8 (get-in best [:session/summary-stats :avg-breathing-rate])))))

    (testing "best posture score"
      (let [best (analytics/find-best-session mock-sessions [:avg-posture-score])]
        (is (= "Afternoon Session" (:session/name best)))
        (is (= 0.91 (get-in best [:session/summary-stats :avg-posture-score])))))

    (testing "empty session list"
      (let [best (analytics/find-best-session [] [:avg-breathing-rate])]
        (is (nil? best))))

    (testing "metric not present"
      (let [sessions-no-metric [{:session/id #uuid "00000000-0000-0000-0000-000000000001"
                                 :session/summary-stats {}}]
            best (analytics/find-best-session sessions-no-metric [:avg-breathing-rate])]
        (is (nil? best))))))

(deftest test-find-worst-session
  (testing "Find session with worst metric"
    (testing "worst breathing rate"
      (let [worst (analytics/find-worst-session mock-sessions [:avg-breathing-rate])]
        (is (= "Evening Practice" (:session/name worst)))
        (is (= 19.2 (get-in worst [:session/summary-stats :avg-breathing-rate])))))

    (testing "worst posture score"
      (let [worst (analytics/find-worst-session mock-sessions [:avg-posture-score])]
        (is (= "Evening Practice" (:session/name worst)))
        (is (= 0.78 (get-in worst [:session/summary-stats :avg-posture-score])))))

    (testing "empty session list"
      (let [worst (analytics/find-worst-session [] [:avg-breathing-rate])]
        (is (nil? worst))))))

;; ============================================================================
;; Variance Tests
;; ============================================================================

(deftest test-metric-variance
  (testing "Compute metric variance"
    (testing "breathing rate variance"
      (let [variance (analytics/compute-metric-variance mock-sessions [:avg-breathing-rate])]
        (is (= 3 (:n variance)))
        (is (> (:mean variance) 21.1))
        (is (< (:mean variance) 21.2))
        (is (> (:variance variance) 0))
        (is (> (:stddev variance) 0))))

    (testing "posture score variance"
      (let [variance (analytics/compute-metric-variance mock-sessions [:avg-posture-score])]
        (is (= 3 (:n variance)))
        (is (> (:mean variance) 0.84))
        (is (< (:mean variance) 0.85))
        (is (> (:variance variance) 0))
        (is (> (:stddev variance) 0))))

    (testing "empty session list"
      (let [variance (analytics/compute-metric-variance [] [:avg-breathing-rate])]
        (is (= 0 (:n variance)))
        (is (nil? (:mean variance)))
        (is (nil? (:variance variance)))
        (is (nil? (:stddev variance)))))

    (testing "sessions with missing metric"
      (let [sessions-partial [{:session/summary-stats {:avg-breathing-rate 20.0}}
                              {:session/summary-stats {}}
                              {:session/summary-stats {:avg-breathing-rate 22.0}}]
            variance (analytics/compute-metric-variance sessions-partial [:avg-breathing-rate])]
        (is (= 2 (:n variance)))
        (is (= 21.0 (:mean variance)))))))

;; ============================================================================
;; Date Utility Tests
;; ============================================================================

(deftest test-date-utilities
  (testing "Get date N days ago"
    (let [date-7-days-ago (analytics/get-date-n-days-ago 7)
          date-30-days-ago (analytics/get-date-n-days-ago 30)]
      (testing "returns ISO string"
        (is (string? date-7-days-ago))
        (is (re-matches #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z" date-7-days-ago)))

      (testing "7 days ago is before 30 days ago"
        (is (> date-7-days-ago date-30-days-ago)))

      (testing "today is 0 days ago"
        (let [today (analytics/get-date-n-days-ago 0)
              now (.toISOString (js/Date.))]
          ;; Should be within 1 second
          (is (< (js/Math.abs (- (.getTime (js/Date. today))
                                 (.getTime (js/Date. now))))
                 1000)))))))

;; ============================================================================
;; Integration Tests (Chained Operations)
;; ============================================================================

(deftest test-chained-operations
  (testing "Filter then sort"
    (let [filtered (analytics/filter-sessions-by-tag mock-sessions "breathing")
          sorted (analytics/sort-sessions filtered :date false)]
      (is (= 2 (count sorted)))
      (is (= "Afternoon Session" (:session/name (first sorted))))
      (is (= "Morning Training" (:session/name (second sorted))))))

  (testing "Search then sort then aggregate"
    (let [searched (analytics/filter-sessions-by-search mock-sessions "session")
          sorted (analytics/sort-sessions searched :duration true)
          stats (analytics/compute-aggregate-stats sorted)]
      (is (= 2 (:total-sessions stats)))
      (is (= 90000 (:total-duration-ms stats))))))
