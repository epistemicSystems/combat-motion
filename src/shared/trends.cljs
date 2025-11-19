(ns combatsys.trends
  "Pure functions for trend analysis and linear regression.

   Philosophy (Rich Hickey):
   - Simple math: Ordinary Least Squares (OLS) is sufficient
   - Pure functions: sessions in → trend data out
   - Explicit results: return slope, intercept, R² for transparency

   Philosophy (John Carmack):
   - Performance: O(n) regression for n < 100 is trivial
   - No premature optimization: simple reduce operations are fast enough"
  (:require [clojure.spec.alpha :as s]))

;; ============================================================================
;; LINEAR REGRESSION
;; ============================================================================

(defn fit-linear-regression
  "Fit y = mx + b to data points using Ordinary Least Squares.

  Pure function.

  Args:
    values: Vector of y-values (x-values are indices 0, 1, 2, ...)

  Returns:
    {:m slope
     :b intercept
     :r2 r-squared (coefficient of determination)}

  Mathematical Note:
    Uses OLS formulas:
    - m = (n*Σxy - Σx*Σy) / (n*Σxx - (Σx)²)
    - b = (Σy - m*Σx) / n
    - R² = 1 - (SS_res / SS_tot)

  Example:
    (fit-linear-regression [1 2 3 4 5])
    => {:m 1.0 :b 1.0 :r2 1.0}  ;; Perfect linear fit"
  [values]
  (when (seq values)
    (let [n (count values)
          xs (range n)
          ys values

          ;; Compute sums
          sum-x (reduce + xs)
          sum-y (reduce + ys)
          sum-xx (reduce + (map * xs xs))
          sum-xy (reduce + (map * xs ys))

          ;; Compute slope and intercept
          m (/ (- (* n sum-xy) (* sum-x sum-y))
               (- (* n sum-xx) (* sum-x sum-x)))
          b (/ (- sum-y (* m sum-x)) n)

          ;; Compute R² (coefficient of determination)
          mean-y (/ sum-y n)
          ss-tot (reduce + (map #(let [diff (- % mean-y)]
                                  (* diff diff))
                               ys))
          ss-res (reduce + (map (fn [i y]
                                  (let [predicted (+ (* m i) b)
                                        diff (- y predicted)]
                                    (* diff diff)))
                               xs ys))
          r2 (if (zero? ss-tot)
               1.0  ;; All values are the same (no variation)
               (- 1 (/ ss-res ss-tot)))]

      {:m m
       :b b
       :r2 r2})))

;; ============================================================================
;; TREND COMPUTATION
;; ============================================================================

(defn compute-trend
  "Compute trend for a specific metric across sessions.

  Pure function.

  Args:
    sessions: Vector of sessions (sorted by date, oldest to newest)
    metric-path: Path to metric, e.g. [:session/analysis :breathing :rate-bpm]

  Returns:
    {:metric-name keyword
     :values vector of numbers
     :timestamps vector of ISO date strings
     :trend-direction :improving | :declining | :stable
     :slope number
     :intercept number
     :r2 number}

  Trend Classification:
    - slope > 0.05 → :improving
    - slope < -0.05 → :declining
    - else → :stable

  Example:
    (compute-trend sessions [:session/analysis :breathing :rate-bpm])
    => {:metric-name :rate-bpm
        :values [20 21 22 23 24]
        :trend-direction :improving
        :slope 1.0
        :r2 0.95}"
  [sessions metric-path]
  (when (seq sessions)
    (let [values (keep #(get-in % metric-path) sessions)
          timestamps (map :session/created-at sessions)]

      (when (seq values)
        (let [regression (fit-linear-regression values)

              ;; Classify trend direction (threshold: 5% per session)
              slope-threshold 0.05
              direction (cond
                          (> (:m regression) slope-threshold) :improving
                          (< (:m regression) (- slope-threshold)) :declining
                          :else :stable)]

          {:metric-name (last metric-path)
           :values (vec values)
           :timestamps (vec timestamps)
           :trend-direction direction
           :slope (:m regression)
           :intercept (:b regression)
           :r2 (:r2 regression)})))))

(defn compute-trend-analysis
  "Compute trend analysis for all key metrics.

  Pure function.

  Args:
    sessions: Vector of sessions (sorted by date)

  Returns:
    {:session-count number
     :date-range {:start-date string :end-date string}
     :trends {:breathing-rate trend-data
              :breathing-depth trend-data
              :posture-score trend-data
              :forward-head trend-data}}

  Note:
    Requires at least 3 sessions for meaningful regression.
    Returns nil if insufficient data."
  [sessions]
  (when (>= (count sessions) 3)
    (let [breathing-rate-trend (compute-trend sessions
                                              [:session/analysis :breathing :rate-bpm])
          breathing-depth-trend (compute-trend sessions
                                               [:session/analysis :breathing :depth-score])
          posture-score-trend (compute-trend sessions
                                            [:session/analysis :posture :overall-score])
          forward-head-trend (compute-trend sessions
                                           [:session/analysis :posture :head-forward-cm])]

      {:session-count (count sessions)
       :date-range {:start-date (:session/created-at (first sessions))
                    :end-date (:session/created-at (last sessions))}
       :trends (cond-> {}
                 breathing-rate-trend (assoc :breathing-rate breathing-rate-trend)
                 breathing-depth-trend (assoc :breathing-depth breathing-depth-trend)
                 posture-score-trend (assoc :posture-score posture-score-trend)
                 forward-head-trend (assoc :forward-head forward-head-trend))})))

;; ============================================================================
;; SPECS
;; ============================================================================

(s/def ::m number?)
(s/def ::b number?)
(s/def ::r2 number?)

(s/def ::regression-result
  (s/keys :req-un [::m ::b ::r2]))

(s/def ::trend-direction #{:improving :declining :stable})

(s/def ::trend-data
  (s/keys :req-un [::metric-name ::values ::timestamps
                   ::trend-direction ::slope ::intercept ::r2]))

(s/fdef fit-linear-regression
  :args (s/cat :values (s/coll-of number?))
  :ret ::regression-result)

(s/fdef compute-trend
  :args (s/cat :sessions vector? :metric-path vector?)
  :ret (s/nilable ::trend-data))
