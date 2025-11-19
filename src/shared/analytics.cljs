(ns combatsys.analytics
  "Pure functions for session analytics and aggregation.

  Philosophy (Functional Core):
  - All functions are pure (no side effects)
  - Filter, sort, and aggregate over session metadata
  - Designed for fast browsing of 100+ sessions

  All functions operate on session metadata (not full sessions) for performance."
  (:require [clojure.string :as str]))

;; ============================================================================
;; SESSION FILTERING
;; ============================================================================

(defn filter-sessions-by-date-range
  "Filter sessions within date range.

  Pure function.

  Args:
    sessions: Vector of session metadata
    start-date: ISO date string or nil (no start filter)
    end-date: ISO date string or nil (no end filter)

  Returns:
    Filtered vector of sessions

  Example:
    (filter-sessions-by-date-range sessions
                                    \"2025-11-01T00:00:00Z\"
                                    \"2025-11-30T23:59:59Z\")
    => [{:session/id ... :session/created-at \"2025-11-15T...\"} ...]

  Note:
    - Uses string comparison (ISO dates sort lexicographically)
    - nil start-date = no lower bound
    - nil end-date = no upper bound"
  [sessions start-date end-date]
  (cond->> sessions
    start-date (filterv #(>= (:session/created-at %) start-date))
    end-date (filterv #(<= (:session/created-at %) end-date))))

(defn filter-sessions-by-search
  "Filter sessions by search text (matches name or notes).

  Pure function. Case-insensitive substring matching.

  Args:
    sessions: Vector of session metadata
    search-text: String to search for

  Returns:
    Filtered vector of sessions

  Example:
    (filter-sessions-by-search sessions \"morning\")
    => [{:session/name \"Morning Training\" ...}
        {:session/name \"Early Morning Practice\" ...}]

  Note:
    - Empty search-text returns all sessions
    - Searches both :session/name and :session/notes
    - Case-insensitive"
  [sessions search-text]
  (if (or (nil? search-text) (empty? search-text))
    sessions
    (let [search-lower (str/lower-case search-text)]
      (filterv (fn [session]
                 (or (str/includes? (str/lower-case (:session/name session ""))
                                    search-lower)
                     (str/includes? (str/lower-case (:session/notes session ""))
                                    search-lower)))
               sessions))))

(defn filter-sessions-by-tag
  "Filter sessions by tag.

  Pure function.

  Args:
    sessions: Vector of session metadata
    tag: Tag string to filter by

  Returns:
    Filtered vector of sessions

  Example:
    (filter-sessions-by-tag sessions \"breathing\")
    => [{:session/tags [\"breathing\" \"posture\"] ...}]"
  [sessions tag]
  (if (or (nil? tag) (empty? tag))
    sessions
    (filterv (fn [session]
               (some #(= % tag) (:session/tags session [])))
             sessions)))

;; ============================================================================
;; SESSION SORTING
;; ============================================================================

(defn sort-sessions
  "Sort sessions by specified key.

  Pure function.

  Args:
    sessions: Vector of session metadata
    sort-key: Keyword to sort by (:date, :duration, :name)
    ascending?: Boolean, true for ascending sort

  Returns:
    Sorted vector of sessions

  Example:
    (sort-sessions sessions :date false)
    => [{:session/created-at \"2025-11-22T...\" ...}  ; Newest first
        {:session/created-at \"2025-11-21T...\" ...}
        ...]

    (sort-sessions sessions :duration true)
    => [{:session/duration-ms 10000 ...}  ; Shortest first
        {:session/duration-ms 30000 ...}
        ...]

  Supported sort keys:
    :date → :session/created-at
    :duration → :session/duration-ms
    :name → :session/name
    default → :session/created-at"
  [sessions sort-key ascending?]
  (let [comparator-fn (case sort-key
                        :date :session/created-at
                        :duration :session/duration-ms
                        :name :session/name
                        :session/created-at)  ; Default
        compare-fn (if ascending? compare #(compare %2 %1))]
    (vec (sort-by comparator-fn compare-fn sessions))))

;; ============================================================================
;; AGGREGATE STATISTICS
;; ============================================================================

(defn compute-aggregate-stats
  "Compute aggregate stats across all sessions.

  Pure function.

  Args:
    session-metadata: Vector of session metadata

  Returns:
    Map of aggregate statistics

  Example:
    (compute-aggregate-stats sessions)
    => {:total-sessions 12
        :total-duration-ms 360000
        :total-duration-hours 0.1
        :total-frames 5400
        :avg-session-duration-ms 30000
        :avg-breathing-rate 21.5
        :avg-posture-score 0.84}

  Note:
    - Returns 0 for averages if no sessions
    - Skips nil values in averages (e.g., sessions without analysis)"
  [session-metadata]
  (let [count (count session-metadata)

        ;; Total duration and frames
        total-duration-ms (reduce + (map :session/duration-ms session-metadata))
        total-frames (reduce + (map :session/frame-count session-metadata))

        ;; Average breathing rate across sessions (skip nil)
        breathing-rates (keep #(get-in % [:session/summary-stats :avg-breathing-rate])
                              session-metadata)
        avg-breathing-rate (if (seq breathing-rates)
                             (/ (reduce + breathing-rates) (count breathing-rates))
                             0)

        ;; Average posture score (skip nil)
        posture-scores (keep #(get-in % [:session/summary-stats :avg-posture-score])
                             session-metadata)
        avg-posture-score (if (seq posture-scores)
                            (/ (reduce + posture-scores) (count posture-scores))
                            0)]

    {:total-sessions count
     :total-duration-ms total-duration-ms
     :total-duration-hours (/ total-duration-ms 1000.0 60.0 60.0)
     :total-frames total-frames
     :avg-session-duration-ms (if (pos? count) (/ total-duration-ms count) 0)
     :avg-breathing-rate avg-breathing-rate
     :avg-posture-score avg-posture-score}))

(defn find-best-session
  "Find session with best value for given metric.

  Pure function.

  Args:
    session-metadata: Vector of session metadata
    metric-path: Path to metric in :session/summary-stats (vector of keywords)

  Returns:
    Best session metadata, or nil if none found

  Example:
    (find-best-session sessions [:avg-breathing-rate])
    => {:session/id ... :session/summary-stats {:avg-breathing-rate 24.5 ...}}

    (find-best-session sessions [:avg-posture-score])
    => {:session/id ... :session/summary-stats {:avg-posture-score 0.92 ...}}

  Note:
    - Returns session with highest metric value
    - Returns nil if no sessions or all have nil metric
    - Use for finding 'best ever' session by metric"
  [session-metadata metric-path]
  (when (seq session-metadata)
    (let [full-path (concat [:session/summary-stats] metric-path)
          sessions-with-metric (filter #(get-in % full-path) session-metadata)]
      (when (seq sessions-with-metric)
        (apply max-key #(get-in % full-path 0) sessions-with-metric)))))

(defn find-worst-session
  "Find session with worst value for given metric.

  Pure function.

  Args:
    session-metadata: Vector of session metadata
    metric-path: Path to metric in :session/summary-stats (vector of keywords)

  Returns:
    Worst session metadata, or nil if none found

  Example:
    (find-worst-session sessions [:avg-breathing-rate])
    => {:session/id ... :session/summary-stats {:avg-breathing-rate 15.2 ...}}

  Note:
    - Returns session with lowest metric value
    - Useful for identifying problem sessions"
  [session-metadata metric-path]
  (when (seq session-metadata)
    (let [full-path (concat [:session/summary-stats] metric-path)
          sessions-with-metric (filter #(get-in % full-path) session-metadata)]
      (when (seq sessions-with-metric)
        (apply min-key #(get-in % full-path 0) sessions-with-metric)))))

(defn compute-metric-variance
  "Compute variance of a metric across sessions.

  Pure function.

  Args:
    session-metadata: Vector of session metadata
    metric-path: Path to metric in :session/summary-stats

  Returns:
    {:mean number :variance number :stddev number}

  Example:
    (compute-metric-variance sessions [:avg-breathing-rate])
    => {:mean 21.5 :variance 2.3 :stddev 1.52}

  Note:
    - Higher variance = less consistent
    - Lower variance = more consistent
    - Returns nil for mean/variance/stddev if no data"
  [session-metadata metric-path]
  (let [full-path (concat [:session/summary-stats] metric-path)
        values (keep #(get-in % full-path) session-metadata)]
    (if (seq values)
      (let [n (count values)
            mean (/ (reduce + values) n)
            squared-diffs (map #(let [diff (- % mean)]
                                  (* diff diff))
                               values)
            variance (/ (reduce + squared-diffs) n)
            stddev (Math/sqrt variance)]
        {:mean mean
         :variance variance
         :stddev stddev
         :n n})
      {:mean nil
       :variance nil
       :stddev nil
       :n 0})))

;; ============================================================================
;; DATE UTILITIES
;; ============================================================================

(defn get-date-n-days-ago
  "Get ISO date string for N days ago.

  Pure function (uses current time).

  Args:
    n: Number of days ago

  Returns:
    ISO date string

  Example:
    (get-date-n-days-ago 7)
    => \"2025-11-12T10:30:00.000Z\"  (if today is Nov 19)

  Note:
    - Used for date range filters (last 7 days, last 30 days, etc.)
    - Returns midnight UTC N days ago"
  [n]
  (let [now (js/Date.)
        ms-per-day (* 24 60 60 1000)
        target-ms (- (.getTime now) (* n ms-per-day))
        target-date (js/Date. target-ms)]
    (.toISOString target-date)))

(comment
  ;; REPL Testing

  ;; Create mock sessions
  (def mock-sessions
    [{:session/id (random-uuid)
      :session/name "Morning Training"
      :session/created-at "2025-11-15T08:00:00Z"
      :session/duration-ms 30000
      :session/frame-count 450
      :session/tags ["breathing"]
      :session/notes "Good session"
      :session/summary-stats {:avg-breathing-rate 21.5
                              :avg-posture-score 0.84}}
     {:session/id (random-uuid)
      :session/name "Evening Practice"
      :session/created-at "2025-11-18T18:00:00Z"
      :session/duration-ms 45000
      :session/frame-count 675
      :session/tags ["posture"]
      :session/notes "Felt tired"
      :session/summary-stats {:avg-breathing-rate 19.2
                              :avg-posture-score 0.78}}])

  ;; Test filtering
  (filter-sessions-by-search mock-sessions "morning")
  ;; => [{:session/name "Morning Training" ...}]

  (filter-sessions-by-date-range mock-sessions
                                  "2025-11-16T00:00:00Z"
                                  nil)
  ;; => [{:session/name "Evening Practice" ...}]

  (filter-sessions-by-tag mock-sessions "breathing")
  ;; => [{:session/name "Morning Training" ...}]

  ;; Test sorting
  (sort-sessions mock-sessions :date false)
  ;; => [evening, morning]  (newest first)

  (sort-sessions mock-sessions :duration true)
  ;; => [morning, evening]  (shortest first)

  ;; Test aggregates
  (compute-aggregate-stats mock-sessions)
  ;; => {:total-sessions 2
  ;;     :total-duration-ms 75000
  ;;     :avg-breathing-rate 20.35
  ;;     ...}

  (find-best-session mock-sessions [:avg-breathing-rate])
  ;; => {:session/name "Morning Training" ...}

  (compute-metric-variance mock-sessions [:avg-breathing-rate])
  ;; => {:mean 20.35 :variance ... :stddev ...}

  (get-date-n-days-ago 7)
  ;; => "2025-11-12T..."
  )
