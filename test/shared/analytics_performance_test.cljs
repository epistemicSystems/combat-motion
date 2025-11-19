(ns combatsys.analytics-performance-test
  "Performance tests for session analytics with large datasets.

  Performance Targets (from LOD6_PREP_SUMMARY.md):
  - Session index load: <100ms for 100 sessions
  - Filter/sort: <50ms to update view
  - Memory: <50MB for browser state

  Run these tests manually to verify performance on target hardware."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [combatsys.analytics :as analytics]
            [combatsys.renderer.persistence :as persist]))

;; ============================================================================
;; Mock Data Generator
;; ============================================================================

(defn random-uuid-str
  "Generate random UUID string for testing."
  []
  (str (random-uuid)))

(defn random-date-in-range
  "Generate random ISO date between start and end dates."
  [start-days-ago end-days-ago]
  (let [now (js/Date.)
        ms-per-day (* 24 60 60 1000)
        random-days (+ end-days-ago (rand-int (- start-days-ago end-days-ago)))
        target-ms (- (.getTime now) (* random-days ms-per-day))
        target-date (js/Date. target-ms)]
    (.toISOString target-date)))

(defn generate-mock-session
  "Generate mock session metadata for performance testing."
  [index]
  {:session/id (random-uuid)
   :session/name (str "Training Session " index)
   :session/created-at (random-date-in-range 90 0)  ; Last 90 days
   :session/duration-ms (* 1000 (+ 20000 (rand-int 40000)))  ; 20-60 seconds
   :session/frame-count (+ 300 (rand-int 600))  ; 300-900 frames
   :session/tags (rand-nth [["breathing"]
                            ["posture"]
                            ["breathing" "posture"]
                            ["gait"]
                            []])
   :session/notes (rand-nth ["Great session!"
                             "Felt good"
                             "Room for improvement"
                             "Very productive"
                             ""])
   :session/summary-stats {:avg-breathing-rate (+ 18.0 (rand 8.0))  ; 18-26 bpm
                           :avg-posture-score (+ 0.7 (rand 0.25))  ; 0.7-0.95
                           :avg-breathing-depth (+ 0.6 (rand 0.35))}})  ; 0.6-0.95

(defn generate-mock-sessions
  "Generate N mock sessions for performance testing."
  [n]
  (vec (for [i (range n)]
         (generate-mock-session i))))

;; ============================================================================
;; Performance Measurement Utilities
;; ============================================================================

(defn measure-time
  "Measure execution time of function f in milliseconds."
  [f label]
  (let [start (js/performance.now)
        result (f)
        end (js/performance.now)
        duration (- end start)]
    (js/console.log (str label ": " (.toFixed duration 2) "ms"))
    {:result result
     :duration-ms duration}))

(defn measure-memory
  "Estimate memory usage of data structure in bytes (approximation)."
  [data]
  (let [json-str (js/JSON.stringify (clj->js data))
        bytes (.-length json-str)]
    (js/console.log (str "Estimated memory: " (.toFixed (/ bytes 1024 1024) 2) " MB"))
    bytes))

;; ============================================================================
;; Performance Tests
;; ============================================================================

(deftest test-filter-performance-100-sessions
  (testing "Filter performance with 100 sessions"
    (let [sessions (generate-mock-sessions 100)]
      (js/console.log "\n=== Filter Performance (100 sessions) ===")

      (testing "date range filter"
        (let [{:keys [duration-ms]} (measure-time
                                      #(analytics/filter-sessions-by-date-range
                                        sessions
                                        (analytics/get-date-n-days-ago 30)
                                        nil)
                                      "Date range filter")]
          (is (< duration-ms 50) "Should filter <50ms")))

      (testing "search filter"
        (let [{:keys [duration-ms]} (measure-time
                                      #(analytics/filter-sessions-by-search
                                        sessions
                                        "session")
                                      "Search filter")]
          (is (< duration-ms 50) "Should filter <50ms")))

      (testing "tag filter"
        (let [{:keys [duration-ms]} (measure-time
                                      #(analytics/filter-sessions-by-tag
                                        sessions
                                        "breathing")
                                      "Tag filter")]
          (is (< duration-ms 50) "Should filter <50ms"))))))

(deftest test-sort-performance-100-sessions
  (testing "Sort performance with 100 sessions"
    (let [sessions (generate-mock-sessions 100)]
      (js/console.log "\n=== Sort Performance (100 sessions) ===")

      (testing "sort by date"
        (let [{:keys [duration-ms]} (measure-time
                                      #(analytics/sort-sessions sessions :date false)
                                      "Sort by date")]
          (is (< duration-ms 50) "Should sort <50ms")))

      (testing "sort by duration"
        (let [{:keys [duration-ms]} (measure-time
                                      #(analytics/sort-sessions sessions :duration true)
                                      "Sort by duration")]
          (is (< duration-ms 50) "Should sort <50ms")))

      (testing "sort by name"
        (let [{:keys [duration-ms]} (measure-time
                                      #(analytics/sort-sessions sessions :name true)
                                      "Sort by name")]
          (is (< duration-ms 50) "Should sort <50ms"))))))

(deftest test-aggregate-performance-100-sessions
  (testing "Aggregate stats performance with 100 sessions"
    (let [sessions (generate-mock-sessions 100)]
      (js/console.log "\n=== Aggregate Performance (100 sessions) ===")

      (let [{:keys [duration-ms result]} (measure-time
                                           #(analytics/compute-aggregate-stats sessions)
                                           "Compute aggregate stats")]
        (is (< duration-ms 10) "Should compute <10ms")
        (is (= 100 (:total-sessions result)))))))

(deftest test-chained-operations-performance
  (testing "Chained operations performance (filter + sort + aggregate)"
    (let [sessions (generate-mock-sessions 100)]
      (js/console.log "\n=== Chained Operations Performance ===")

      (let [{:keys [duration-ms result]} (measure-time
                                           #(-> sessions
                                                (analytics/filter-sessions-by-date-range
                                                 (analytics/get-date-n-days-ago 30)
                                                 nil)
                                                (analytics/filter-sessions-by-tag "breathing")
                                                (analytics/sort-sessions :date false)
                                                (analytics/compute-aggregate-stats))
                                           "Filter + Sort + Aggregate")]
        (is (< duration-ms 100) "Should complete chain <100ms")))))

(deftest test-metadata-extraction-performance
  (testing "Metadata extraction performance"
    (js/console.log "\n=== Metadata Extraction Performance ===")

    ;; Create a full session with timeline
    (let [full-session {:session/id (random-uuid)
                        :session/name "Full Session"
                        :session/created-at (.toISOString (js/Date.))
                        :session/duration-ms 30000
                        :session/frame-count 450
                        :session/timeline (vec (for [i (range 450)]
                                                 {:frame/index i
                                                  :frame/pose {:landmarks []}}))
                        :session/metadata {:tags ["breathing"]
                                          :notes "Test session"}
                        :session/analysis {:breathing {:rate-bpm 22}
                                          :posture {:overall-score 0.85}}}]

      ;; Measure metadata extraction
      (let [{:keys [duration-ms result]} (measure-time
                                           #(persist/extract-session-metadata full-session)
                                           "Extract metadata")]
        (is (< duration-ms 5) "Should extract <5ms")

        ;; Verify metadata is lightweight
        (let [full-size (measure-memory full-session)
              metadata-size (measure-memory result)
              reduction-ratio (/ full-size metadata-size)]
          (js/console.log (str "Size reduction: " (.toFixed reduction-ratio 0) "x"))
          (is (> reduction-ratio 100) "Metadata should be >100x smaller"))))))

(deftest test-memory-usage-100-sessions
  (testing "Memory usage with 100 sessions"
    (js/console.log "\n=== Memory Usage (100 sessions) ===")

    (let [sessions (generate-mock-sessions 100)
          bytes (measure-memory sessions)
          mb (/ bytes 1024 1024)]
      (is (< mb 1) "100 session metadata should be <1MB")
      (js/console.log (str "Actual size: " (.toFixed (/ bytes 1024) 2) " KB")))))

(deftest test-scalability-1000-sessions
  (testing "Scalability test with 1000 sessions"
    (js/console.log "\n=== Scalability Test (1000 sessions) ===")

    (let [sessions (generate-mock-sessions 1000)]
      (testing "filter performance"
        (let [{:keys [duration-ms]} (measure-time
                                      #(analytics/filter-sessions-by-search sessions "session")
                                      "Filter 1000 sessions")]
          (js/console.log (str "Filter time: " (.toFixed duration-ms 2) "ms"))
          (is (< duration-ms 200) "Should filter 1000 sessions <200ms")))

      (testing "sort performance"
        (let [{:keys [duration-ms]} (measure-time
                                      #(analytics/sort-sessions sessions :date false)
                                      "Sort 1000 sessions")]
          (js/console.log (str "Sort time: " (.toFixed duration-ms 2) "ms"))
          (is (< duration-ms 200) "Should sort 1000 sessions <200ms")))

      (testing "aggregate performance"
        (let [{:keys [duration-ms]} (measure-time
                                      #(analytics/compute-aggregate-stats sessions)
                                      "Aggregate 1000 sessions")]
          (js/console.log (str "Aggregate time: " (.toFixed duration-ms 2) "ms"))
          (is (< duration-ms 50) "Should aggregate 1000 sessions <50ms")))

      (testing "memory usage"
        (let [bytes (measure-memory sessions)
              mb (/ bytes 1024 1024)]
          (is (< mb 10) "1000 session metadata should be <10MB"))))))

;; ============================================================================
;; Manual Performance Verification
;; ============================================================================

(comment
  ;; Run these in REPL to verify performance manually

  ;; Generate test data
  (def test-sessions-100 (generate-mock-sessions 100))
  (def test-sessions-1000 (generate-mock-sessions 1000))

  ;; Test filtering
  (measure-time
   #(analytics/filter-sessions-by-date-range
     test-sessions-100
     (analytics/get-date-n-days-ago 30)
     nil)
   "Filter 100 sessions by date")

  ;; Test sorting
  (measure-time
   #(analytics/sort-sessions test-sessions-100 :date false)
   "Sort 100 sessions by date")

  ;; Test aggregation
  (measure-time
   #(analytics/compute-aggregate-stats test-sessions-100)
   "Aggregate 100 sessions")

  ;; Test scalability
  (measure-time
   #(-> test-sessions-1000
        (analytics/filter-sessions-by-tag "breathing")
        (analytics/sort-sessions :date false)
        (analytics/compute-aggregate-stats))
   "Full pipeline on 1000 sessions")

  ;; Verify memory usage
  (measure-memory test-sessions-100)
  (measure-memory test-sessions-1000)
  )
