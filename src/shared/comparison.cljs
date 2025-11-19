(ns combatsys.comparison
  "Pure functions for session comparison and trend analysis.

   Philosophy (Rich Hickey):
   - Pure functions: data in → data out
   - Explicit semantics: higher-is-better? makes improvement clear
   - Simple data structures: just maps with deltas and percentages"
  (:require [clojure.spec.alpha :as s]))

;; ============================================================================
;; METRIC COMPARISON
;; ============================================================================

(defn compare-metric
  "Compare two numeric metrics.

  Pure function.

  Args:
    metric-a: First metric value (number)
    metric-b: Second metric value (number)
    higher-is-better?: Boolean, true if higher value is improvement

  Returns:
    {:metric-a number
     :metric-b number
     :delta number
     :pct-change number
     :direction :increased | :decreased | :unchanged
     :improvement? boolean}

  Example:
    (compare-metric 20 22 true)
    => {:metric-a 20
        :metric-b 22
        :delta 2
        :pct-change 10.0
        :direction :increased
        :improvement? true}"
  [metric-a metric-b higher-is-better?]
  (let [delta (- metric-b metric-a)
        pct-change (if (zero? metric-a)
                     0
                     (* 100 (/ delta metric-a)))

        ;; Threshold: >5% = increased, <-5% = decreased, else unchanged
        direction (cond
                    (> pct-change 5) :increased
                    (< pct-change -5) :decreased
                    :else :unchanged)

        improvement? (case direction
                       :increased higher-is-better?
                       :decreased (not higher-is-better?)
                       :unchanged false)]

    {:metric-a metric-a
     :metric-b metric-b
     :delta delta
     :pct-change pct-change
     :direction direction
     :improvement? improvement?}))

;; ============================================================================
;; SESSION COMPARISON
;; ============================================================================

(defn compare-breathing
  "Compare breathing analysis between two sessions.

  Pure function.

  Args:
    breathing-a: Breathing analysis map from session A
    breathing-b: Breathing analysis map from session B

  Returns:
    {:rate-comparison {...}
     :depth-comparison {...}
     :fatigue-comparison {...}}"
  [breathing-a breathing-b]
  (when (and breathing-a breathing-b)
    {:rate-comparison (compare-metric
                       (:rate-bpm breathing-a)
                       (:rate-bpm breathing-b)
                       true)  ;; Higher breathing rate = better cardio capacity

     :depth-comparison (compare-metric
                        (:depth-score breathing-a)
                        (:depth-score breathing-b)
                        true)  ;; Higher depth = better breathing quality

     :fatigue-comparison {:count-a (count (:fatigue-windows breathing-a []))
                          :count-b (count (:fatigue-windows breathing-b []))
                          :delta (- (count (:fatigue-windows breathing-b []))
                                   (count (:fatigue-windows breathing-a [])))
                          :improvement? (< (count (:fatigue-windows breathing-b []))
                                          (count (:fatigue-windows breathing-a [])))}}))

(defn compare-posture
  "Compare posture analysis between two sessions.

  Pure function.

  Args:
    posture-a: Posture analysis map from session A
    posture-b: Posture analysis map from session B

  Returns:
    {:overall-score-comparison {...}
     :forward-head-comparison {...}
     :shoulder-imbalance-comparison {...}}"
  [posture-a posture-b]
  (when (and posture-a posture-b)
    {:overall-score-comparison (compare-metric
                                (:overall-score posture-a)
                                (:overall-score posture-b)
                                true)  ;; Higher score = better

     :forward-head-comparison (compare-metric
                               (:head-forward-cm posture-a)
                               (:head-forward-cm posture-b)
                               false)  ;; Lower forward head = better

     :shoulder-imbalance-comparison (compare-metric
                                     (js/Math.abs (:shoulder-imbalance-deg posture-a))
                                     (js/Math.abs (:shoulder-imbalance-deg posture-b))
                                     false)}))  ;; Lower imbalance = better

(defn assess-overall-change
  "Assess overall improvement/decline across all metrics.

  Pure function.

  Args:
    breathing-comp: Breathing comparison map
    posture-comp: Posture comparison map

  Returns:
    :significant-improvement | :slight-improvement | :stable
    | :slight-decline | :significant-decline"
  [breathing-comp posture-comp]
  (let [;; Collect all improvement flags
        improvements (filter identity
                            [(get-in breathing-comp [:rate-comparison :improvement?])
                             (get-in breathing-comp [:depth-comparison :improvement?])
                             (get-in breathing-comp [:fatigue-comparison :improvement?])
                             (get-in posture-comp [:overall-score-comparison :improvement?])
                             (get-in posture-comp [:forward-head-comparison :improvement?])
                             (get-in posture-comp [:shoulder-imbalance-comparison :improvement?])])

        improvement-count (count improvements)
        total-metrics 6
        decline-count (- total-metrics improvement-count)]

    (cond
      (>= improvement-count 5) :significant-improvement
      (>= improvement-count 4) :slight-improvement
      (>= decline-count 5) :significant-decline
      (>= decline-count 4) :slight-decline
      :else :stable)))

(defn generate-comparison-insights
  "Generate natural language insights from comparison.

  Pure function.

  Args:
    breathing-comp: Breathing comparison map
    posture-comp: Posture comparison map

  Returns:
    Vector of insight maps with :title, :description, :severity"
  [breathing-comp posture-comp]
  (let [insights []]
    (cond-> insights
      ;; Breathing rate improved
      (get-in breathing-comp [:rate-comparison :improvement?])
      (conj {:title "Breathing rate improved"
             :description (str "Rate increased by "
                               (.toFixed (js/Math.abs (get-in breathing-comp
                                                              [:rate-comparison :pct-change])) 1)
                               "% (from "
                               (.toFixed (get-in breathing-comp [:rate-comparison :metric-a]) 1)
                               " to "
                               (.toFixed (get-in breathing-comp [:rate-comparison :metric-b]) 1)
                               " bpm)")
             :severity :positive})

      ;; Breathing rate declined
      (and (not (get-in breathing-comp [:rate-comparison :improvement?]))
           (= :decreased (get-in breathing-comp [:rate-comparison :direction])))
      (conj {:title "Breathing rate declined"
             :description (str "Rate decreased by "
                               (.toFixed (js/Math.abs (get-in breathing-comp
                                                              [:rate-comparison :pct-change])) 1)
                               "% (from "
                               (.toFixed (get-in breathing-comp [:rate-comparison :metric-a]) 1)
                               " to "
                               (.toFixed (get-in breathing-comp [:rate-comparison :metric-b]) 1)
                               " bpm)")
             :severity :negative})

      ;; Breathing depth improved
      (get-in breathing-comp [:depth-comparison :improvement?])
      (conj {:title "Breathing depth improved"
             :description (str "Depth score increased by "
                               (.toFixed (js/Math.abs (get-in breathing-comp
                                                              [:depth-comparison :pct-change])) 1)
                               "%")
             :severity :positive})

      ;; Fatigue windows improved
      (get-in breathing-comp [:fatigue-comparison :improvement?])
      (conj {:title "Fewer fatigue windows"
             :description (str "Reduced from "
                               (get-in breathing-comp [:fatigue-comparison :count-a])
                               " to "
                               (get-in breathing-comp [:fatigue-comparison :count-b])
                               " fatigue episodes")
             :severity :positive})

      ;; Fatigue windows declined
      (and (not (get-in breathing-comp [:fatigue-comparison :improvement?]))
           (> (get-in breathing-comp [:fatigue-comparison :delta]) 0))
      (conj {:title "More fatigue windows"
             :description (str "Increased from "
                               (get-in breathing-comp [:fatigue-comparison :count-a])
                               " to "
                               (get-in breathing-comp [:fatigue-comparison :count-b])
                               " fatigue episodes")
             :severity :negative})

      ;; Posture score improved
      (get-in posture-comp [:overall-score-comparison :improvement?])
      (conj {:title "Overall posture improved"
             :description (str "Score increased by "
                               (.toFixed (js/Math.abs (get-in posture-comp
                                                              [:overall-score-comparison :pct-change])) 1)
                               "% (from "
                               (.toFixed (* 100 (get-in posture-comp [:overall-score-comparison :metric-a])) 0)
                               "% to "
                               (.toFixed (* 100 (get-in posture-comp [:overall-score-comparison :metric-b])) 0)
                               "%)")
             :severity :positive})

      ;; Forward head improved
      (get-in posture-comp [:forward-head-comparison :improvement?])
      (conj {:title "Forward head posture improved"
             :description (str "Reduced by "
                               (.toFixed (js/Math.abs (get-in posture-comp
                                                              [:forward-head-comparison :delta])) 1)
                               " cm (from "
                               (.toFixed (get-in posture-comp [:forward-head-comparison :metric-a]) 1)
                               " to "
                               (.toFixed (get-in posture-comp [:forward-head-comparison :metric-b]) 1)
                               " cm)")
             :severity :positive})

      ;; Forward head declined
      (and (not (get-in posture-comp [:forward-head-comparison :improvement?]))
           (= :increased (get-in posture-comp [:forward-head-comparison :direction])))
      (conj {:title "Forward head posture declined"
             :description (str "Increased by "
                               (.toFixed (js/Math.abs (get-in posture-comp
                                                              [:forward-head-comparison :delta])) 1)
                               " cm")
             :severity :negative})

      ;; Shoulder imbalance improved
      (get-in posture-comp [:shoulder-imbalance-comparison :improvement?])
      (conj {:title "Shoulder balance improved"
             :description (str "Imbalance reduced by "
                               (.toFixed (js/Math.abs (get-in posture-comp
                                                              [:shoulder-imbalance-comparison :delta])) 1)
                               "°")
             :severity :positive})

      ;; Shoulder imbalance declined
      (and (not (get-in posture-comp [:shoulder-imbalance-comparison :improvement?]))
           (= :increased (get-in posture-comp [:shoulder-imbalance-comparison :direction])))
      (conj {:title "Shoulder imbalance increased"
             :description (str "Imbalance increased by "
                               (.toFixed (js/Math.abs (get-in posture-comp
                                                              [:shoulder-imbalance-comparison :delta])) 1)
                               "°")
             :severity :negative}))))

(defn compare-sessions
  "Compare two complete sessions.

  Pure function.

  Args:
    session-a: First session map (must have :session/analysis)
    session-b: Second session map (must have :session/analysis)

  Returns:
    Comparison report with:
    - Session metadata (IDs, names, dates)
    - Breathing comparison
    - Posture comparison
    - Overall assessment
    - Natural language insights

  Example:
    (compare-sessions session-1 session-2)
    => {:session-a-id #uuid ...
        :session-b-id #uuid ...
        :breathing-comparison {...}
        :posture-comparison {...}
        :overall-assessment :slight-improvement
        :insights [{:title \"Breathing rate improved\" ...}]}"
  [session-a session-b]
  (let [breathing-a (get-in session-a [:session/analysis :breathing])
        breathing-b (get-in session-b [:session/analysis :breathing])
        posture-a (get-in session-a [:session/analysis :posture])
        posture-b (get-in session-b [:session/analysis :posture])

        breathing-comp (compare-breathing breathing-a breathing-b)
        posture-comp (compare-posture posture-a posture-b)

        overall-assessment (assess-overall-change breathing-comp posture-comp)
        insights (generate-comparison-insights breathing-comp posture-comp)]

    {:session-a-id (:session/id session-a)
     :session-b-id (:session/id session-b)
     :session-a-name (:session/name session-a)
     :session-b-name (:session/name session-b)
     :session-a-date (:session/created-at session-a)
     :session-b-date (:session/created-at session-b)
     :timestamp-compared (.toISOString (js/Date.))

     :breathing-comparison breathing-comp
     :posture-comparison posture-comp

     :overall-assessment overall-assessment

     :insights insights}))

;; ============================================================================
;; SPECS
;; ============================================================================

(s/def ::metric-comparison
  (s/keys :req-un [::metric-a ::metric-b ::delta ::pct-change ::direction ::improvement?]))

(s/def ::comparison-report
  (s/keys :req-un [::session-a-id ::session-b-id
                   ::session-a-name ::session-b-name
                   ::session-a-date ::session-b-date
                   ::breathing-comparison ::posture-comparison
                   ::overall-assessment ::insights]))

(s/fdef compare-metric
  :args (s/cat :metric-a number? :metric-b number? :higher-is-better? boolean?)
  :ret ::metric-comparison)

(s/fdef compare-sessions
  :args (s/cat :session-a map? :session-b map?)
  :ret ::comparison-report)
