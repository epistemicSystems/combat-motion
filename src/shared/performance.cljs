(ns combatsys.shared.performance
  "Performance profiling and monitoring utilities.

  Provides instrumentation for measuring function execution times,
  tracking performance metrics, and identifying bottlenecks.

  Philosophy (Carmack):
  - Measure everything
  - Profile first, optimize later
  - Make performance visible
  - Flag anything >16ms (60fps budget)"
  (:require [clojure.string :as str]))

;; ============================================================
;; PERFORMANCE METRICS STORE
;; ============================================================

(defonce perf-metrics
  "Global atom storing performance measurements.

  Structure:
  {:measurements [{:label \"breathing-analysis\"
                   :duration-ms 45.2
                   :timestamp 1234567890}
                  ...]
   :stats {:breathing-analysis {:count 10
                                :total-ms 450.2
                                :avg-ms 45.02
                                :max-ms 67.3
                                :min-ms 32.1}
           ...}}"
  (atom {:measurements []
         :stats {}}))

(defn reset-metrics!
  "Clear all performance metrics. Useful for fresh profiling runs."
  []
  (reset! perf-metrics {:measurements []
                        :stats {}}))

;; ============================================================
;; MEASUREMENT FUNCTIONS
;; ============================================================

(defn now-ms
  "Get current timestamp in milliseconds.
  Uses js/performance.now() for high precision."
  []
  (if (exists? js/performance)
    (.now js/performance)
    (.getTime (js/Date.))))

(defn record-measurement!
  "Record a performance measurement.

  Args:
    label: String identifier for the operation
    duration-ms: Duration in milliseconds

  Side effect: Updates perf-metrics atom"
  [label duration-ms]
  (let [measurement {:label label
                     :duration-ms duration-ms
                     :timestamp (js/Date.now)}]
    (swap! perf-metrics
           (fn [metrics]
             (-> metrics
                 (update :measurements conj measurement)
                 (update-in [:stats label] #(update-stats % duration-ms)))))))

(defn update-stats
  "Update statistics for a given label.

  Pure function: stats → stats'"
  [stats duration-ms]
  (if stats
    (-> stats
        (update :count inc)
        (update :total-ms + duration-ms)
        (assoc :avg-ms (/ (+ (:total-ms stats) duration-ms)
                          (inc (:count stats))))
        (update :max-ms #(max (or % 0) duration-ms))
        (update :min-ms #(min (or % ##Inf) duration-ms)))
    {:count 1
     :total-ms duration-ms
     :avg-ms duration-ms
     :max-ms duration-ms
     :min-ms duration-ms}))

;; ============================================================
;; PROFILING WRAPPERS
;; ============================================================

(defn profile-fn
  "Wrap a function with performance profiling.

  Returns a new function that:
  1. Measures execution time
  2. Records measurement
  3. Logs warning if > threshold
  4. Returns original result

  Args:
    f: Function to profile
    label: String identifier
    opts: {:threshold-ms 16  ; Log warning if slower
           :warn? true}      ; Enable console warnings

  Returns:
    Profiled function with same signature as f

  Example:
    (def analyze-breathing-profiled
      (profile-fn breathing/analyze \"breathing-analyzer\"))

    (analyze-breathing-profiled timeline)
    ;; => analysis result (same as original)
    ;; Side effect: Records performance data"
  ([f label]
   (profile-fn f label {}))
  ([f label {:keys [threshold-ms warn?]
             :or {threshold-ms 16
                  warn? true}}]
   (fn [& args]
     (let [start (now-ms)
           result (apply f args)
           end (now-ms)
           duration (- end start)]

       ;; Record measurement
       (record-measurement! label duration)

       ;; Warn if over threshold
       (when (and warn? (> duration threshold-ms))
         (js/console.warn
          (str "[PERF] " label " took " (.toFixed duration 2) "ms"
               " (threshold: " threshold-ms "ms)")))

       ;; Return original result
       result))))

(defn profile-async-fn
  "Wrap an async function (returns promise) with profiling.

  Similar to profile-fn but handles promises.

  Args:
    f: Async function to profile
    label: String identifier
    opts: Same as profile-fn

  Returns:
    Profiled async function"
  ([f label]
   (profile-async-fn f label {}))
  ([f label opts]
   (fn [& args]
     (let [start (now-ms)]
       (-> (apply f args)
           (.then (fn [result]
                    (let [end (now-ms)
                          duration (- end start)]
                      (record-measurement! label duration)
                      (when (and (:warn? opts true)
                                 (> duration (:threshold-ms opts 16)))
                        (js/console.warn
                         (str "[PERF] " label " took " (.toFixed duration 2) "ms (async)")))
                      result))))))))

;; ============================================================
;; STATISTICS & REPORTING
;; ============================================================

(defn get-stats
  "Get performance statistics for a specific label or all labels.

  Args:
    label: (optional) String identifier, or nil for all stats

  Returns:
    Stats map or nil if no measurements"
  ([]
   (:stats @perf-metrics))
  ([label]
   (get-in @perf-metrics [:stats label])))

(defn get-measurements
  "Get all measurements, optionally filtered by label.

  Args:
    label: (optional) String identifier

  Returns:
    Vector of measurement maps"
  ([]
   (:measurements @perf-metrics))
  ([label]
   (filter #(= (:label %) label)
           (:measurements @perf-metrics))))

(defn get-slowest
  "Get N slowest operations across all measurements.

  Args:
    n: Number of results to return (default 10)

  Returns:
    Vector of measurement maps sorted by duration (desc)"
  ([]
   (get-slowest 10))
  ([n]
   (->> (:measurements @perf-metrics)
        (sort-by :duration-ms >)
        (take n))))

(defn get-stats-above-threshold
  "Get all stats where average exceeds threshold.

  Args:
    threshold-ms: Threshold in milliseconds (default 16)

  Returns:
    Map of label → stats for operations exceeding threshold"
  ([]
   (get-stats-above-threshold 16))
  ([threshold-ms]
   (->> (:stats @perf-metrics)
        (filter (fn [[label stats]]
                  (> (:avg-ms stats) threshold-ms)))
        (into {}))))

(defn format-stats
  "Format statistics as human-readable string.

  Args:
    stats: Stats map from get-stats

  Returns:
    Formatted string"
  [stats]
  (when stats
    (str "Count: " (:count stats)
         " | Avg: " (.toFixed (:avg-ms stats) 2) "ms"
         " | Min: " (.toFixed (:min-ms stats) 2) "ms"
         " | Max: " (.toFixed (:max-ms stats) 2) "ms"
         " | Total: " (.toFixed (:total-ms stats) 2) "ms")))

(defn print-report
  "Print performance report to console.

  Shows:
  1. All stats sorted by average duration
  2. Operations exceeding threshold
  3. Slowest individual measurements

  Side effect: Logs to console"
  []
  (let [stats (:stats @perf-metrics)
        sorted-stats (sort-by (fn [[_ s]] (:avg-ms s)) > stats)
        slow-stats (get-stats-above-threshold 16)
        slowest (get-slowest 5)]

    (js/console.log "=== PERFORMANCE REPORT ===")
    (js/console.log "")

    (js/console.log "All Operations (sorted by avg duration):")
    (doseq [[label stat] sorted-stats]
      (js/console.log (str "  " label ": " (format-stats stat))))
    (js/console.log "")

    (when (seq slow-stats)
      (js/console.log "Operations Exceeding 16ms Threshold:")
      (doseq [[label stat] slow-stats]
        (js/console.log (str "  ⚠️  " label ": " (format-stats stat))))
      (js/console.log ""))

    (when (seq slowest)
      (js/console.log "Slowest Individual Measurements:")
      (doseq [measurement slowest]
        (js/console.log (str "  " (:label measurement)
                             ": " (.toFixed (:duration-ms measurement) 2) "ms")))
      (js/console.log ""))

    (js/console.log "=== END REPORT ===")))

;; ============================================================
;; FRAME BUDGET HELPERS
;; ============================================================

(defn fps-to-frame-budget
  "Convert target FPS to frame budget in milliseconds.

  Args:
    fps: Target frames per second (default 60)

  Returns:
    Frame budget in milliseconds

  Example:
    (fps-to-frame-budget 60) => 16.67
    (fps-to-frame-budget 30) => 33.33"
  ([]
   (fps-to-frame-budget 60))
  ([fps]
   (/ 1000 fps)))

(defn within-frame-budget?
  "Check if duration is within frame budget for target FPS.

  Args:
    duration-ms: Duration in milliseconds
    fps: Target frames per second (default 60)

  Returns:
    Boolean indicating if within budget"
  ([duration-ms]
   (within-frame-budget? duration-ms 60))
  ([duration-ms fps]
   (< duration-ms (fps-to-frame-budget fps))))

;; ============================================================
;; MACRO-LIKE HELPERS (for inline profiling)
;; ============================================================

(defn with-timing
  "Execute function and return [result duration-ms].

  Pure wrapper - no side effects.

  Args:
    f: Function to time
    args: Arguments to f

  Returns:
    [result duration-ms]

  Example:
    (let [[result time] (with-timing my-fn arg1 arg2)]
      (when (> time 16)
        (js/console.warn \"Slow!\"))
      result)"
  [f & args]
  (let [start (now-ms)
        result (apply f args)
        duration (- (now-ms) start)]
    [result duration]))

;; ============================================================
;; EXPORTS
;; ============================================================

(def ^:export metrics perf-metrics)
(def ^:export profile profile-fn)
(def ^:export profileAsync profile-async-fn)
(def ^:export getStats get-stats)
(def ^:export report print-report)
(def ^:export reset reset-metrics!)
