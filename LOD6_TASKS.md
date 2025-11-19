# LOD 6 TASKS: MULTI-SESSION ANALYTICS IMPLEMENTATION
## Detailed Task Breakdown with Deliverables

---

## TASK OVERVIEW

**Total Estimated Time**: 16-20 hours
**Tasks**: 5 major tasks
**Files to Create**: 6 new files
**Files to Modify**: 3 existing files
**Tests**: 10 unit tests, 2 integration tests

---

## TASK DEPENDENCY GRAPH

```
Task 7.1 (Session Index & Metadata)
    ↓
Task 7.2 (Session Browser UI) ─┬─→ Task 7.4 (Comparison UI)
    ↓                          │
Task 7.3 (Comparison Logic)────┘
    ↓
Task 7.5 (Trend Analysis & Graphs)
```

**Parallelization opportunities**:
- Tasks 7.2 and 7.3 can be done in parallel after 7.1 completes
- Task 7.5 depends on all previous tasks

---

## TASK 7.1: SESSION INDEX & METADATA EXTRACTION
**Priority**: 🔴 Critical
**Estimated Time**: 3 hours
**Files**: `src/renderer/persistence.cljs` (modify), `src/shared/analytics.cljs` (create new)

### Objective
Implement session indexing for fast loading of session metadata without loading full timelines.

### Deliverables

#### 1. Modify `src/renderer/persistence.cljs`

Add session index management:

```clojure
;; ============================================================================
;; SESSION INDEX (LOD 6)
;; ============================================================================

(def index-file-path "sessions/index.edn")

(defn extract-session-metadata
  "Extract lightweight metadata from full session.

  Pure function.

  Args:
    session: Full session map with timeline

  Returns:
    Metadata map without timeline (for fast loading)"
  [session]
  {:session/id (:session/id session)
   :session/name (:session/name session)
   :session/created-at (:session/created-at session)
   :session/duration-ms (:session/duration-ms session)
   :session/frame-count (:session/frame-count session)
   :session/tags (get-in session [:session/metadata :tags] [])
   :session/notes (get-in session [:session/metadata :notes] "")
   :session/summary-stats (extract-summary-stats session)})

(defn extract-summary-stats
  "Extract summary statistics from analyzed session.

  Pure function.

  Args:
    session: Session with :session/analysis

  Returns:
    Map of key metrics for quick display"
  [session]
  (let [breathing (get-in session [:session/analysis :breathing])
        posture (get-in session [:session/analysis :posture])]
    {:avg-breathing-rate (:rate-bpm breathing)
     :avg-breathing-depth (:depth-score breathing)
     :avg-posture-score (:overall-score posture)
     :forward-head-cm (:head-forward-cm posture)
     :total-fatigue-windows (count (:fatigue-windows breathing))}))

;; ============================================================================
;; INDEX FILE I/O (Side Effects)
;; ============================================================================

(defn load-session-index!
  "Load session index from disk.

  Side effect: Reads from file system.

  Returns:
    Vector of session metadata, or empty vector if index doesn't exist"
  []
  (try
    (if (file-exists? index-file-path)
      (let [edn-str (read-file! index-file-path)]
        (edn-string->session-index edn-str))
      [])
    (catch js/Error e
      (js/console.error "Error loading session index:" e)
      [])))

(defn save-session-index!
  "Save session index to disk.

  Side effect: Writes to file system.

  Args:
    index: Vector of session metadata maps"
  [index]
  (try
    (let [edn-str (pr-str index)]
      (write-file! index-file-path edn-str)
      (js/console.log "Session index saved:" (count index) "sessions"))
    (catch js/Error e
      (js/console.error "Error saving session index:" e))))

(defn update-session-in-index!
  "Add or update session in index.

  Side effect: Modifies index file.

  Args:
    session: Full session map"
  [session]
  (let [metadata (extract-session-metadata session)
        index (load-session-index!)

        ;; Remove old entry if exists
        index-without (remove #(= (:session/id %)
                                  (:session/id metadata))
                              index)

        ;; Add new entry (sorted by date, newest first)
        updated-index (sort-by :session/created-at
                               #(compare %2 %1)  ;; Descending
                               (conj index-without metadata))]
    (save-session-index! updated-index)))

(defn remove-session-from-index!
  "Remove session from index.

  Side effect: Modifies index file.

  Args:
    session-id: UUID of session to remove"
  [session-id]
  (let [index (load-session-index!)
        updated-index (remove #(= (:session/id %) session-id) index)]
    (save-session-index! updated-index)))

;; ============================================================================
;; MODIFIED: Save Session (Now Updates Index)
;; ============================================================================

;; Update existing save-session! to also update index
(defn save-session!
  "Save session to disk AND update index.

  Side effect: Writes session file and updates index.

  Args:
    session: Session map

  Returns:
    File path where session was saved"
  [session]
  (let [session-id (:session/id session)
        path (get-session-path session-id)
        edn-str (session->edn-string session)]

    ;; Ensure sessions directory exists
    (ensure-dir-exists! "sessions")

    ;; Write session file
    (write-file! path edn-str)

    ;; Update index
    (update-session-in-index! session)

    (js/console.log "Session saved:" path)
    path))
```

#### 2. Create `src/shared/analytics.cljs`

Pure analytics helper functions:

```clojure
(ns combatsys.analytics
  "Pure functions for session analytics and aggregation"
  (:require [combatsys.schema :as schema]
            [clojure.spec.alpha :as s]))

;; ============================================================================
;; SESSION FILTERING & SORTING
;; ============================================================================

(defn filter-sessions-by-date-range
  "Filter sessions within date range.

  Pure function.

  Args:
    sessions: Vector of session metadata
    start-date: ISO date string or nil (no start filter)
    end-date: ISO date string or nil (no end filter)

  Returns:
    Filtered vector of sessions"
  [sessions start-date end-date]
  (cond->> sessions
    start-date (filter #(>= (:session/created-at %)
                            start-date))
    end-date (filter #(<= (:session/created-at %)
                          end-date))))

(defn filter-sessions-by-search
  "Filter sessions by search text (matches name or notes).

  Pure function.

  Args:
    sessions: Vector of session metadata
    search-text: String to search for (case-insensitive)

  Returns:
    Filtered vector of sessions"
  [sessions search-text]
  (if (empty? search-text)
    sessions
    (let [search-lower (clojure.string/lower-case search-text)]
      (filter (fn [session]
                (or (clojure.string/includes?
                     (clojure.string/lower-case (:session/name session))
                     search-lower)
                    (clojure.string/includes?
                     (clojure.string/lower-case (or (:session/notes session) ""))
                     search-lower)))
              sessions))))

(defn sort-sessions
  "Sort sessions by specified key.

  Pure function.

  Args:
    sessions: Vector of session metadata
    sort-key: Keyword to sort by (:date, :duration, :name)
    ascending?: Boolean, true for ascending sort

  Returns:
    Sorted vector of sessions"
  [sessions sort-key ascending?]
  (let [comparator-fn (case sort-key
                        :date :session/created-at
                        :duration :session/duration-ms
                        :name :session/name
                        :session/created-at)]
    (sort-by comparator-fn
             (if ascending? compare #(compare %2 %1))
             sessions)))

;; ============================================================================
;; AGGREGATE STATISTICS
;; ============================================================================

(defn compute-aggregate-stats
  "Compute aggregate stats across all sessions.

  Pure function.

  Args:
    session-metadata: Vector of session metadata

  Returns:
    Map of aggregate statistics"
  [session-metadata]
  (let [count (count session-metadata)
        total-duration-ms (reduce + (map :session/duration-ms session-metadata))
        total-frames (reduce + (map :session/frame-count session-metadata))

        ;; Average breathing rate across sessions
        breathing-rates (keep #(get-in % [:session/summary-stats :avg-breathing-rate])
                              session-metadata)
        avg-breathing-rate (if (seq breathing-rates)
                             (/ (reduce + breathing-rates) (count breathing-rates))
                             0)

        ;; Average posture score
        posture-scores (keep #(get-in % [:session/summary-stats :avg-posture-score])
                             session-metadata)
        avg-posture-score (if (seq posture-scores)
                            (/ (reduce + posture-scores) (count posture-scores))
                            0)]

    {:total-sessions count
     :total-duration-ms total-duration-ms
     :total-duration-hours (/ total-duration-ms 1000 60 60)
     :total-frames total-frames
     :avg-session-duration-ms (if (pos? count) (/ total-duration-ms count) 0)
     :avg-breathing-rate avg-breathing-rate
     :avg-posture-score avg-posture-score}))

(defn find-best-session
  "Find session with best value for given metric.

  Pure function.

  Args:
    session-metadata: Vector of session metadata
    metric-path: Path to metric in :session/summary-stats

  Returns:
    Best session metadata, or nil if none found"
  [session-metadata metric-path]
  (when (seq session-metadata)
    (apply max-key #(get-in % (concat [:session/summary-stats] metric-path) 0)
           session-metadata)))

;; ============================================================================
;; SPECS
;; ============================================================================

(s/fdef filter-sessions-by-date-range
  :args (s/cat :sessions (s/coll-of ::schema/session-metadata)
               :start-date (s/nilable string?)
               :end-date (s/nilable string?))
  :ret (s/coll-of ::schema/session-metadata))

(s/fdef compute-aggregate-stats
  :args (s/cat :session-metadata (s/coll-of ::schema/session-metadata))
  :ret map?)
```

### Tests

#### `test/shared/analytics_test.cljs`

```clojure
(ns combatsys.analytics-test
  (:require [clojure.test :refer [deftest is testing]]
            [combatsys.analytics :as analytics]))

(deftest test-filter-by-date-range
  (testing "Filters sessions within date range"
    (let [sessions [{:session/id (random-uuid)
                     :session/created-at "2025-11-01T10:00:00Z"}
                    {:session/id (random-uuid)
                     :session/created-at "2025-11-15T10:00:00Z"}
                    {:session/id (random-uuid)
                     :session/created-at "2025-11-30T10:00:00Z"}]

          result (analytics/filter-sessions-by-date-range
                  sessions
                  "2025-11-10T00:00:00Z"
                  "2025-11-20T00:00:00Z")]

      (is (= 1 (count result)))
      (is (= "2025-11-15T10:00:00Z" (:session/created-at (first result)))))))

(deftest test-aggregate-stats
  (testing "Computes aggregate statistics correctly"
    (let [sessions [{:session/duration-ms 30000
                     :session/frame-count 450
                     :session/summary-stats {:avg-breathing-rate 20}}
                    {:session/duration-ms 45000
                     :session/frame-count 675
                     :session/summary-stats {:avg-breathing-rate 22}}]

          stats (analytics/compute-aggregate-stats sessions)]

      (is (= 2 (:total-sessions stats)))
      (is (= 75000 (:total-duration-ms stats)))
      (is (= 1125 (:total-frames stats)))
      (is (= 21.0 (:avg-breathing-rate stats))))))  ;; (20 + 22) / 2
```

### Acceptance Criteria

- [ ] Session index file is created on first save
- [ ] Index loads <100ms for 100 sessions
- [ ] Metadata extraction excludes timeline (smaller file)
- [ ] Filter and sort functions work correctly
- [ ] All tests pass

---

## TASK 7.2: SESSION BROWSER UI
**Priority**: 🔴 Critical
**Estimated Time**: 5 hours
**Files**: `src/renderer/session_browser.cljs` (create new)

### Objective
Create UI for browsing, filtering, and selecting sessions.

### Deliverables

#### File: `src/renderer/session_browser.cljs`

```clojure
(ns combatsys.renderer.session-browser
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [combatsys.analytics :as analytics]))

;; ============================================================================
;; COMPONENTS
;; ============================================================================

(defn browser-header []
  (let [session-count (rf/subscribe [:session-browser/total-count])
        aggregate-stats (rf/subscribe [:session-browser/aggregate-stats])]
    [:div.browser-header
     [:h1 "Your Sessions " [:span.count "(" @session-count ")"]]
     [:div.aggregate-stats
      [:div.stat
       [:span.label "Total Training Time"]
       [:span.value (.toFixed (:total-duration-hours @aggregate-stats) 1) " hours"]]
      [:div.stat
       [:span.label "Avg Session"]
       [:span.value (.toFixed (/ (:avg-session-duration-ms @aggregate-stats) 1000) 0) "s"]]
      [:div.stat
       [:span.label "Avg Breathing"]
       [:span.value (.toFixed (:avg-breathing-rate @aggregate-stats) 1) " bpm"]]]]))

(defn filter-controls []
  (let [search-text (rf/subscribe [:session-browser/search-text])
        sort-by (rf/subscribe [:session-browser/sort-by])
        date-filter (rf/subscribe [:session-browser/date-filter])]
    [:div.filter-controls
     [:div.search-box
      [:input {:type "text"
               :placeholder "Search sessions..."
               :value @search-text
               :on-change #(rf/dispatch [:session-browser/set-search
                                         (-> % .-target .-value)])}]]

     [:div.sort-dropdown
      [:label "Sort by:"]
      [:select {:value @sort-by
                :on-change #(rf/dispatch [:session-browser/set-sort
                                          (keyword (-> % .-target .-value))])}
       [:option {:value "date"} "Date (Newest First)"]
       [:option {:value "duration"} "Duration"]
       [:option {:value "name"} "Name"]]]

     [:div.date-filter
      [:label "Date Range:"]
      [:select {:value @date-filter
                :on-change #(rf/dispatch [:session-browser/set-date-filter
                                          (keyword (-> % .-target .-value))])}
       [:option {:value "all"} "All Time"]
       [:option {:value "last-7-days"} "Last 7 Days"]
       [:option {:value "last-30-days"} "Last 30 Days"]
       [:option {:value "last-90-days"} "Last 90 Days"]]]]))

(defn session-card [session selected?]
  [:div.session-card
   {:class (when selected? "selected")
    :on-click #(rf/dispatch [:session-browser/toggle-selection
                             (:session/id session)])}
   [:div.card-header
    [:input.checkbox
     {:type "checkbox"
      :checked selected?
      :on-click #(.stopPropagation %)}]
    [:div.session-name (:session/name session)]
    [:div.session-date (format-date (:session/created-at session))]]

   [:div.card-metrics
    [:div.metric
     [:span.label "Duration"]
     [:span.value (.toFixed (/ (:session/duration-ms session) 1000) 0) "s"]]
    [:div.metric
     [:span.label "Breathing"]
     [:span.value (get-in session [:session/summary-stats :avg-breathing-rate]) " bpm"]]
    [:div.metric
     [:span.label "Posture"]
     [:span.value (.toFixed (* 100 (get-in session [:session/summary-stats :avg-posture-score])) 0) "%"]]]

   [:div.card-actions
    [:button.btn-small
     {:on-click #(do (.stopPropagation %)
                     (rf/dispatch [:session/load-and-view (:session/id session)]))}
     "View"]
    [:button.btn-small
     {:on-click #(do (.stopPropagation %)
                     (rf/dispatch [:session/delete (:session/id session)]))}
     "Delete"]]])

(defn session-list []
  (let [filtered-sessions (rf/subscribe [:session-browser/filtered-sessions])
        selected-ids (rf/subscribe [:session-browser/selected-ids])]
    [:div.session-list
     (if (seq @filtered-sessions)
       (for [session @filtered-sessions]
         ^{:key (:session/id session)}
         [session-card session (contains? (set @selected-ids) (:session/id session))])

       [:div.empty-state
        [:p "No sessions found"]
        [:button.btn-primary
         {:on-click #(rf/dispatch [:ui/set-view :live-feed])}
         "Record Your First Session"]])]))

(defn action-bar []
  (let [selected-count (rf/subscribe [:session-browser/selected-count])]
    [:div.action-bar
     (when (= 2 @selected-count)
       [:button.btn-primary
        {:on-click #(rf/dispatch [:analytics/compare-selected-sessions])}
        "Compare Selected (2)"])

     (when (> @selected-count 0)
       [:button.btn-secondary
        {:on-click #(rf/dispatch [:session-browser/clear-selection])}
        (str "Clear Selection (" @selected-count ")")])]))

(defn session-browser-view []
  [:div.session-browser
   [browser-header]
   [filter-controls]
   [session-list]
   [action-bar]])

;; ============================================================================
;; HELPERS
;; ============================================================================

(defn format-date [iso-string]
  "Format ISO date string to readable format"
  (let [date (js/Date. iso-string)]
    (.toLocaleDateString date "en-US"
                         #js {:month "short"
                              :day "numeric"
                              :year "numeric"
                              :hour "numeric"
                              :minute "2-digit"})))
```

#### re-frame Events & Subscriptions

Add to `src/renderer/state.cljs`:

```clojure
;; ============================================================================
;; SESSION BROWSER STATE
;; ============================================================================

(rf/reg-event-db
 :session-browser/init
 (fn [db _]
   ;; Load session index from disk
   (let [index (persistence/load-session-index!)]
     (assoc db :session-browser
            {:index index
             :search-text ""
             :sort-by :date
             :date-filter :all
             :selected-ids []}))))

(rf/reg-event-db
 :session-browser/set-search
 (fn [db [_ search-text]]
   (assoc-in db [:session-browser :search-text] search-text)))

(rf/reg-event-db
 :session-browser/set-sort
 (fn [db [_ sort-key]]
   (assoc-in db [:session-browser :sort-by] sort-key)))

(rf/reg-event-db
 :session-browser/set-date-filter
 (fn [db [_ filter-key]]
   (assoc-in db [:session-browser :date-filter] filter-key)))

(rf/reg-event-db
 :session-browser/toggle-selection
 (fn [db [_ session-id]]
   (let [selected-ids (get-in db [:session-browser :selected-ids])
         already-selected? (some #(= % session-id) selected-ids)
         updated-ids (if already-selected?
                       (remove #(= % session-id) selected-ids)
                       (conj selected-ids session-id))]
     ;; Limit to 2 selections for comparison
     (assoc-in db [:session-browser :selected-ids]
               (take 2 updated-ids)))))

(rf/reg-event-db
 :session-browser/clear-selection
 (fn [db _]
   (assoc-in db [:session-browser :selected-ids] [])))

;; ============================================================================
;; SUBSCRIPTIONS
;; ============================================================================

(rf/reg-sub
 :session-browser/index
 (fn [db _]
   (get-in db [:session-browser :index])))

(rf/reg-sub
 :session-browser/filtered-sessions
 (fn [db _]
   (let [index (get-in db [:session-browser :index])
         search-text (get-in db [:session-browser :search-text])
         sort-by (get-in db [:session-browser :sort-by])
         date-filter (get-in db [:session-browser :date-filter])

         ;; Apply date filter
         date-filtered (case date-filter
                         :last-7-days (analytics/filter-sessions-by-date-range
                                       index
                                       (get-date-n-days-ago 7)
                                       nil)
                         :last-30-days (analytics/filter-sessions-by-date-range
                                        index
                                        (get-date-n-days-ago 30)
                                        nil)
                         :last-90-days (analytics/filter-sessions-by-date-range
                                        index
                                        (get-date-n-days-ago 90)
                                        nil)
                         :all index
                         index)

         ;; Apply search filter
         search-filtered (analytics/filter-sessions-by-search
                          date-filtered
                          search-text)

         ;; Apply sort
         sorted (analytics/sort-sessions search-filtered sort-by false)]

     sorted)))

(rf/reg-sub
 :session-browser/selected-ids
 (fn [db _]
   (get-in db [:session-browser :selected-ids])))

(rf/reg-sub
 :session-browser/selected-count
 :<- [:session-browser/selected-ids]
 (fn [selected-ids _]
   (count selected-ids)))

(rf/reg-sub
 :session-browser/total-count
 :<- [:session-browser/index]
 (fn [index _]
   (count index)))

(rf/reg-sub
 :session-browser/aggregate-stats
 :<- [:session-browser/index]
 (fn [index _]
   (analytics/compute-aggregate-stats index)))
```

### Acceptance Criteria

- [ ] Session list loads from index (<200ms for 100 sessions)
- [ ] Search filters sessions by name/notes
- [ ] Sort works (date, duration, name)
- [ ] Date range filter works
- [ ] Can select up to 2 sessions for comparison
- [ ] Aggregate stats display correctly

---

## TASK 7.3: SESSION COMPARISON LOGIC
**Priority**: 🔴 Critical
**Estimated Time**: 4 hours
**Files**: `src/shared/comparison.cljs` (create new)

### Objective
Implement pure functions for comparing two sessions across all metrics.

### Deliverables

#### File: `src/shared/comparison.cljs`

```clojure
(ns combatsys.comparison
  "Pure functions for session comparison and trend analysis"
  (:require [combatsys.schema :as schema]
            [clojure.spec.alpha :as s]))

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
     :improvement? boolean}"
  [metric-a metric-b higher-is-better?]
  (let [delta (- metric-b metric-a)
        pct-change (if (zero? metric-a)
                     0
                     (* 100 (/ delta metric-a)))

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

  Pure function."
  [breathing-a breathing-b]
  {:rate-comparison (compare-metric
                     (:rate-bpm breathing-a)
                     (:rate-bpm breathing-b)
                     true)  ;; Higher breathing rate = better

   :depth-comparison (compare-metric
                      (:depth-score breathing-a)
                      (:depth-score breathing-b)
                      true)  ;; Higher depth = better

   :fatigue-comparison {:count-a (count (:fatigue-windows breathing-a))
                        :count-b (count (:fatigue-windows breathing-b))
                        :delta (- (count (:fatigue-windows breathing-b))
                                  (count (:fatigue-windows breathing-a)))
                        :improvement? (< (count (:fatigue-windows breathing-b))
                                         (count (:fatigue-windows breathing-a)))}})

(defn compare-posture
  "Compare posture analysis between two sessions.

  Pure function."
  [posture-a posture-b]
  {:overall-score-comparison (compare-metric
                              (:overall-score posture-a)
                              (:overall-score posture-b)
                              true)  ;; Higher score = better

   :forward-head-comparison (compare-metric
                             (:head-forward-cm posture-a)
                             (:head-forward-cm posture-b)
                             false)  ;; Lower forward head = better

   :shoulder-imbalance-comparison (compare-metric
                                   (Math/abs (:shoulder-imbalance-deg posture-a))
                                   (Math/abs (:shoulder-imbalance-deg posture-b))
                                   false)})  ;; Lower imbalance = better

(defn assess-overall-change
  "Assess overall improvement/decline across all metrics.

  Pure function.

  Returns:
    :significant-improvement | :slight-improvement | :stable
    | :slight-decline | :significant-decline"
  [breathing-comp posture-comp]
  (let [improvements (count (filter :improvement?
                                    [(:rate-comparison breathing-comp)
                                     (:depth-comparison breathing-comp)
                                     (:fatigue-comparison breathing-comp)
                                     (:overall-score-comparison posture-comp)
                                     (:forward-head-comparison posture-comp)]))

        declines (- 5 improvements)]  ;; Total metrics - improvements

    (cond
      (>= improvements 4) :significant-improvement
      (>= improvements 3) :slight-improvement
      (>= declines 4) :significant-decline
      (>= declines 3) :slight-decline
      :else :stable)))

(defn compare-sessions
  "Compare two complete sessions.

  Pure function.

  Args:
    session-a: First session map (must have :session/analysis)
    session-b: Second session map (must have :session/analysis)

  Returns:
    Comparison report conforming to ::schema/comparison-report"
  [session-a session-b]
  (let [breathing-a (get-in session-a [:session/analysis :breathing])
        breathing-b (get-in session-b [:session/analysis :breathing])
        posture-a (get-in session-a [:session/analysis :posture])
        posture-b (get-in session-b [:session/analysis :posture])

        breathing-comp (compare-breathing breathing-a breathing-b)
        posture-comp (compare-posture posture-a posture-b)]

    {:session-a-id (:session/id session-a)
     :session-b-id (:session/id session-b)
     :session-a-name (:session/name session-a)
     :session-b-name (:session/name session-b)
     :session-a-date (:session/created-at session-a)
     :session-b-date (:session/created-at session-b)
     :timestamp-compared (js/Date.)

     :breathing-comparison breathing-comp
     :posture-comparison posture-comp

     :overall-assessment (assess-overall-change breathing-comp posture-comp)

     :insights (generate-comparison-insights breathing-comp posture-comp)}))

(defn generate-comparison-insights
  "Generate natural language insights from comparison.

  Pure function."
  [breathing-comp posture-comp]
  (let [insights []]
    (cond-> insights
      ;; Breathing improvements
      (get-in breathing-comp [:rate-comparison :improvement?])
      (conj {:title "Breathing rate improved"
             :description (str "Rate increased by "
                               (.toFixed (Math/abs (get-in breathing-comp
                                                           [:rate-comparison :pct-change])) 1)
                               "%")
             :severity :positive})

      ;; Posture improvements
      (get-in posture-comp [:forward-head-comparison :improvement?])
      (conj {:title "Forward head posture improved"
             :description (str "Reduced by "
                               (.toFixed (Math/abs (get-in posture-comp
                                                           [:forward-head-comparison :delta])) 1)
                               " cm")
             :severity :positive})

      ;; Add more insights as needed
      )))

;; ============================================================================
;; SPECS
;; ============================================================================

(s/fdef compare-metric
  :args (s/cat :metric-a number? :metric-b number? :higher-is-better? boolean?)
  :ret map?)

(s/fdef compare-sessions
  :args (s/cat :session-a ::schema/session :session-b ::schema/session)
  :ret ::schema/comparison-report)
```

### Tests

#### `test/shared/comparison_test.cljs`

```clojure
(ns combatsys.comparison-test
  (:require [clojure.test :refer [deftest is testing]]
            [combatsys.comparison :as comp]))

(deftest test-compare-metric
  (testing "Compares metrics correctly"
    (let [result (comp/compare-metric 20 22 true)]
      (is (= 2 (:delta result)))
      (is (= 10.0 (:pct-change result)))
      (is (= :increased (:direction result)))
      (is (true? (:improvement? result)))))

  (testing "Handles improvement direction correctly"
    (let [result (comp/compare-metric 5.0 3.0 false)]  ;; Lower is better
      (is (= -2.0 (:delta result)))
      (is (= :decreased (:direction result)))
      (is (true? (:improvement? result))))))  ;; Decreased, but improvement!

(deftest test-compare-sessions
  (testing "Full session comparison"
    (let [session-a {:session/id (random-uuid)
                     :session/name "Session A"
                     :session/created-at "2025-11-01T10:00:00Z"
                     :session/analysis
                     {:breathing {:rate-bpm 20 :depth-score 0.75
                                  :fatigue-windows []}
                      :posture {:overall-score 0.78
                                :head-forward-cm 5.0
                                :shoulder-imbalance-deg 2.0}}}

          session-b {:session/id (random-uuid)
                     :session/name "Session B"
                     :session/created-at "2025-11-15T10:00:00Z"
                     :session/analysis
                     {:breathing {:rate-bpm 22 :depth-score 0.82
                                  :fatigue-windows []}
                      :posture {:overall-score 0.84
                                :head-forward-cm 3.5
                                :shoulder-imbalance-deg 1.5}}}

          comparison (comp/compare-sessions session-a session-b)]

      (is (= (:session-a-id comparison) (:session/id session-a)))
      (is (= (:session-b-id comparison) (:session/id session-b)))

      ;; Breathing improved
      (is (true? (get-in comparison [:breathing-comparison
                                      :rate-comparison
                                      :improvement?])))

      ;; Posture improved
      (is (true? (get-in comparison [:posture-comparison
                                      :forward-head-comparison
                                      :improvement?])))

      ;; Overall assessment
      (is (contains? #{:significant-improvement :slight-improvement}
                     (:overall-assessment comparison))))))
```

### Acceptance Criteria

- [ ] compare-metric handles positive/negative deltas correctly
- [ ] Improvement direction respects "higher is better" vs "lower is better"
- [ ] compare-sessions produces valid comparison report
- [ ] Insights are generated for key improvements/declines
- [ ] All tests pass

---

## TASK 7.4: COMPARISON UI
**Priority**: 🔴 Critical
**Estimated Time**: 4 hours
**Files**: `src/renderer/comparison_view.cljs` (create new)

### Objective
Create side-by-side comparison view for two sessions.

### Deliverables

#### File: `src/renderer/comparison_view.cljs`

```clojure
(ns combatsys.renderer.comparison-view
  (:require [reagent.core :as r]
            [re-frame.core :as rf]))

;; ============================================================================
;; COMPONENTS
;; ============================================================================

(defn comparison-header [comparison]
  [:div.comparison-header
   [:h1 "Session Comparison"]
   [:div.session-info
    [:div.session-a
     [:h3 (:session-a-name comparison)]
     [:p.date (format-date (:session-a-date comparison))]]
    [:div.arrow "→"]
    [:div.session-b
     [:h3 (:session-b-name comparison)]
     [:p.date (format-date (:session-b-date comparison))]]]

   [:div.overall-assessment
    {:class (name (:overall-assessment comparison))}
    (case (:overall-assessment comparison)
      :significant-improvement "📈 Significant Improvement"
      :slight-improvement "↗ Slight Improvement"
      :stable "→ Stable"
      :slight-decline "↘ Slight Decline"
      :significant-decline "📉 Significant Decline")]])

(defn metric-comparison-row [label comparison higher-is-better?]
  (let [{:keys [metric-a metric-b delta pct-change direction improvement?]} comparison]
    [:tr.metric-row
     {:class (name direction)}
     [:td.label label]
     [:td.value-a (format-number metric-a)]
     [:td.arrow "→"]
     [:td.value-b (format-number metric-b)]
     [:td.delta
      [:span.delta-value
       (if (pos? delta) "+" "")
       (format-number delta)]
      [:span.pct-change
       "(" (if (pos? pct-change) "+" "")
       (.toFixed pct-change 1) "%)"]
      [:span.indicator
       (if improvement? " ✓" "")]]]))

(defn breathing-comparison-table [breathing-comp]
  [:div.comparison-section
   [:h2 "Breathing Analysis"]
   [:table.comparison-table
    [:thead
     [:tr
      [:th "Metric"]
      [:th "Session A"]
      [:th ""]
      [:th "Session B"]
      [:th "Change"]]]
    [:tbody
     [metric-comparison-row "Rate (bpm)"
      (:rate-comparison breathing-comp)
      true]
     [metric-comparison-row "Depth Score"
      (:depth-comparison breathing-comp)
      true]
     [:tr
      [:td.label "Fatigue Windows"]
      [:td.value-a (:count-a (:fatigue-comparison breathing-comp))]
      [:td.arrow "→"]
      [:td.value-b (:count-b (:fatigue-comparison breathing-comp))]
      [:td.delta
       [:span.delta-value
        (let [delta (:delta (:fatigue-comparison breathing-comp))]
          (str (if (pos? delta) "+" "") delta))]
       [:span.indicator
        (if (:improvement? (:fatigue-comparison breathing-comp)) " ✓" "")]]]]]])

(defn posture-comparison-table [posture-comp]
  [:div.comparison-section
   [:h2 "Posture Analysis"]
   [:table.comparison-table
    [:thead
     [:tr
      [:th "Metric"]
      [:th "Session A"]
      [:th ""]
      [:th "Session B"]
      [:th "Change"]]]
    [:tbody
     [metric-comparison-row "Overall Score"
      (:overall-score-comparison posture-comp)
      true]
     [metric-comparison-row "Forward Head (cm)"
      (:forward-head-comparison posture-comp)
      false]
     [metric-comparison-row "Shoulder Imbalance (°)"
      (:shoulder-imbalance-comparison posture-comp)
      false]]]])

(defn insights-panel [insights]
  [:div.insights-panel
   [:h2 "Key Insights"]
   (if (seq insights)
     (for [insight insights]
       ^{:key (:title insight)}
       [:div.insight-card
        {:class (name (:severity insight))}
        [:h4 (:title insight)]
        [:p (:description insight)]])

     [:p.no-insights "No significant changes detected"])])

(defn comparison-view []
  (let [comparison (rf/subscribe [:analytics/comparison-report])]
    (fn []
      (if @comparison
        [:div.comparison-view
         [comparison-header @comparison]
         [breathing-comparison-table (:breathing-comparison @comparison)]
         [posture-comparison-table (:posture-comparison @comparison)]
         [insights-panel (:insights @comparison)]

         [:div.actions
          [:button.btn-primary
           {:on-click #(rf/dispatch [:ui/set-view :session-browser])}
           "Back to Sessions"]
          [:button.btn-secondary
           {:on-click #(rf/dispatch [:analytics/export-comparison])}
           "Export Report"]]]

        [:div.loading "Loading comparison..."]))))

;; ============================================================================
;; HELPERS
;; ============================================================================

(defn format-number [n]
  (if (number? n)
    (.toFixed n 2)
    "N/A"))

(defn format-date [iso-string]
  (let [date (js/Date. iso-string)]
    (.toLocaleDateString date "en-US"
                         #js {:month "short"
                              :day "numeric"
                              :year "numeric"})))
```

#### re-frame Events

Add to `src/renderer/state.cljs`:

```clojure
(rf/reg-event-fx
 :analytics/compare-selected-sessions
 (fn [{:keys [db]} _]
   (let [selected-ids (get-in db [:session-browser :selected-ids])
         session-a-id (first selected-ids)
         session-b-id (second selected-ids)]

     ;; Load both sessions (if not already loaded)
     (when (and session-a-id session-b-id)
       (let [session-a (or (get-in db [:sessions session-a-id])
                           (persistence/load-session! session-a-id))
             session-b (or (get-in db [:sessions session-b-id])
                           (persistence/load-session! session-b-id))

             comparison (comparison/compare-sessions session-a session-b)]

         {:db (assoc-in db [:analytics :comparison-report] comparison)
          :dispatch [:ui/set-view :comparison]})))))

(rf/reg-sub
 :analytics/comparison-report
 (fn [db _]
   (get-in db [:analytics :comparison-report])))
```

### Acceptance Criteria

- [ ] Comparison view loads when 2 sessions selected
- [ ] Side-by-side metrics display correctly
- [ ] Delta and percentage change calculated correctly
- [ ] Improvement indicators (✓) show for positive changes
- [ ] Overall assessment displays with appropriate emoji
- [ ] Insights panel shows key changes

---

## TASK 7.5: TREND ANALYSIS & GRAPHS
**Priority**: 🟡 Important
**Estimated Time**: 4-6 hours
**Files**: `src/shared/trends.cljs` (create new), `src/renderer/analytics_view.cljs` (create new), `src/renderer/charts.cljs` (create new)

### Objective
Implement trend analysis with linear regression and create visual graph components.

### Deliverables

#### File 1: `src/shared/trends.cljs`

```clojure
(ns combatsys.trends
  "Pure functions for trend analysis and linear regression"
  (:require [combatsys.schema :as schema]
            [clojure.spec.alpha :as s]))

;; ============================================================================
;; LINEAR REGRESSION
;; ============================================================================

(defn fit-linear-regression
  "Fit y = mx + b to data points.

  Pure function. Uses ordinary least squares.

  Args:
    values: Vector of y-values (x-values are indices 0, 1, 2, ...)

  Returns:
    {:m slope :b intercept :r2 r-squared}"
  [values]
  (let [n (count values)
        xs (range n)
        ys values

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
        ss-tot (reduce + (map #(* (- % mean-y) (- % mean-y)) ys))
        ss-res (reduce + (map (fn [i y]
                                (let [predicted (+ (* m i) b)]
                                  (* (- y predicted) (- y predicted))))
                              xs ys))
        r2 (if (zero? ss-tot)
             1.0
             (- 1 (/ ss-res ss-tot)))]

    {:m m :b b :r2 r2}))

;; ============================================================================
;; TREND COMPUTATION
;; ============================================================================

(defn compute-trend
  "Compute trend for a specific metric across sessions.

  Pure function.

  Args:
    sessions: Vector of sessions (should be sorted by date)
    metric-path: Path to metric, e.g. [:session/analysis :breathing :rate-bpm]

  Returns:
    Trend data conforming to ::schema/trend-data"
  [sessions metric-path]
  (let [values (map #(get-in % metric-path) sessions)
        timestamps (map :session/created-at sessions)
        regression (fit-linear-regression values)

        ;; Classify trend direction
        slope-threshold 0.05
        direction (cond
                    (> (:m regression) slope-threshold) :improving
                    (< (:m regression) (- slope-threshold)) :declining
                    :else :stable)]

    {:metric-name (last metric-path)
     :values values
     :timestamps timestamps
     :trend-direction direction
     :slope (:m regression)
     :intercept (:b regression)
     :r2 (:r2 regression)}))

(defn compute-trend-analysis
  "Compute trend analysis for all key metrics.

  Pure function.

  Args:
    sessions: Vector of sessions (sorted by date)

  Returns:
    Trend analysis conforming to ::schema/trend-analysis"
  [sessions]
  (when (seq sessions)
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
       :trends {:breathing-rate breathing-rate-trend
                :breathing-depth breathing-depth-trend
                :posture-score posture-score-trend
                :forward-head forward-head-trend}})))

;; ============================================================================
;; SPECS
;; ============================================================================

(s/fdef fit-linear-regression
  :args (s/cat :values (s/coll-of number?))
  :ret (s/keys :req-un [::m ::b ::r2]))

(s/fdef compute-trend
  :args (s/cat :sessions (s/coll-of ::schema/session)
               :metric-path vector?)
  :ret ::schema/trend-data)
```

#### File 2: `src/renderer/charts.cljs` (Simple line chart component)

```clojure
(ns combatsys.renderer.charts
  "Simple chart components using SVG"
  (:require [reagent.core :as r]))

(defn line-chart
  "Simple SVG line chart.

  Props:
    :data - Vector of y-values
    :labels - Vector of x-axis labels
    :width - Chart width (default 600)
    :height - Chart height (default 300)
    :title - Chart title
    :y-label - Y-axis label
    :trend-line - (optional) Regression line {:m slope :b intercept}"
  [{:keys [data labels width height title y-label trend-line]
    :or {width 600 height 300}}]
  (let [padding 50
        chart-width (- width (* 2 padding))
        chart-height (- height (* 2 padding))

        min-y (apply min data)
        max-y (apply max data)
        y-range (- max-y min-y)
        y-scale (/ chart-height y-range)

        points (map-indexed
                (fn [i y]
                  (let [x (+ padding (* i (/ chart-width (dec (count data)))))
                        y-coord (+ padding (- chart-height (* (- y min-y) y-scale)))]
                    [x y-coord]))
                data)

        line-path (apply str "M "
                         (interpose " L "
                                    (map (fn [[x y]] (str x " " y)) points)))]

    [:svg.line-chart {:width width :height height}
     ;; Title
     [:text {:x (/ width 2) :y 20 :text-anchor "middle" :class "chart-title"}
      title]

     ;; Y-axis
     [:line {:x1 padding :y1 padding
             :x2 padding :y2 (+ padding chart-height)
             :stroke "#333" :stroke-width 1}]

     ;; X-axis
     [:line {:x1 padding :y1 (+ padding chart-height)
             :x2 (+ padding chart-width) :y2 (+ padding chart-height)
             :stroke "#333" :stroke-width 1}]

     ;; Y-axis label
     [:text {:x 15 :y (/ height 2) :text-anchor "middle"
             :transform (str "rotate(-90 15 " (/ height 2) ")")}
      y-label]

     ;; Data line
     [:path {:d line-path
             :stroke "#4A90E2"
             :stroke-width 2
             :fill "none"}]

     ;; Data points
     (for [[i [x y]] (map-indexed vector points)]
       ^{:key i}
       [:circle {:cx x :cy y :r 4 :fill "#4A90E2"}])

     ;; Trend line (if provided)
     (when trend-line
       (let [{:keys [m b]} trend-line
             x1 padding
             y1 (+ padding (- chart-height (* (- b min-y) y-scale)))
             x2 (+ padding chart-width)
             y2 (+ padding (- chart-height (* (- (+ (* m (dec (count data))) b) min-y) y-scale)))]
         [:line {:x1 x1 :y1 y1 :x2 x2 :y2 y2
                 :stroke "#FF6B6B" :stroke-width 1
                 :stroke-dasharray "5,5"}]))

     ;; X-axis labels
     (for [[i label] (map-indexed vector labels)]
       (let [x (+ padding (* i (/ chart-width (dec (count labels)))))]
         ^{:key i}
         [:text {:x x :y (+ padding chart-height 20)
                 :text-anchor "middle" :font-size 10}
          label]))]))
```

#### File 3: `src/renderer/analytics_view.cljs`

```clojure
(ns combatsys.renderer.analytics-view
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [combatsys.renderer.charts :as charts]))

(defn analytics-header []
  (let [session-count (rf/subscribe [:analytics/session-count])]
    [:div.analytics-header
     [:h1 "Analytics Dashboard"]
     [:p.subtitle (str "Trends based on " @session-count " sessions")]]))

(defn trend-chart-panel [trend-data title y-label]
  [:div.trend-chart-panel
   [charts/line-chart
    {:data (:values trend-data)
     :labels (map format-date (:timestamps trend-data))
     :title title
     :y-label y-label
     :width 700
     :height 350
     :trend-line {:m (:slope trend-data)
                  :b (:intercept trend-data)}}]

   [:div.trend-stats
    [:div.stat
     [:span.label "Trend"]
     [:span.value
      {:class (name (:trend-direction trend-data))}
      (case (:trend-direction trend-data)
        :improving "↗ Improving"
        :declining "↘ Declining"
        :stable "→ Stable")]]
    [:div.stat
     [:span.label "R²"]
     [:span.value (.toFixed (:r2 trend-data) 2)]]
    [:div.stat
     [:span.label "Slope"]
     [:span.value (.toFixed (:slope trend-data) 3)]]]])

(defn analytics-dashboard []
  (let [trend-analysis (rf/subscribe [:analytics/trend-analysis])]
    (fn []
      (if @trend-analysis
        [:div.analytics-dashboard
         [analytics-header]

         [trend-chart-panel
          (get-in @trend-analysis [:trends :breathing-rate])
          "Breathing Rate Over Time"
          "Rate (bpm)"]

         [trend-chart-panel
          (get-in @trend-analysis [:trends :posture-score])
          "Posture Score Over Time"
          "Score"]

         ;; Add more charts as needed
         ]

        [:div.insufficient-data
         [:h2 "Insufficient Data"]
         [:p "Record at least 5 sessions to see trends"]
         [:button.btn-primary
          {:on-click #(rf/dispatch [:ui/set-view :live-feed])}
          "Record Session"]]))))

(defn format-date [iso-string]
  (let [date (js/Date. iso-string)]
    (str (.getMonth date) "/" (.getDate date))))
```

### Acceptance Criteria

- [ ] Linear regression computes correct slope and R²
- [ ] Trend direction classified correctly (:improving/:declining/:stable)
- [ ] Line charts render correctly
- [ ] Trend line overlays on data points
- [ ] Analytics view shows all key metric trends
- [ ] Displays "insufficient data" message for <5 sessions

---

## INTEGRATION TESTING

### End-to-End Test Scenario

```
1. Record 10 sessions with varying metrics
   → Sessions 1-5: Breathing rate 18-20 bpm
   → Sessions 6-10: Breathing rate 21-23 bpm (improving trend)

2. Navigate to "Sessions" tab
   → See list of 10 sessions
   → Aggregate stats show correct totals
   → Can filter by date range

3. Select Sessions 1 and 10, click "Compare"
   → Comparison view loads
   → Shows improvement: 18 bpm → 23 bpm (+28%)
   → Overall assessment: "Significant Improvement"
   → Insights mention breathing improvement

4. Navigate to "Analytics" tab
   → Breathing rate trend chart shows upward trend
   → Trend line has positive slope
   → R² > 0.7 (good fit)
   → Posture score chart also displayed

5. Filter sessions to "Last 30 Days"
   → Charts update to show only recent data
   → Trend recalculates
```

---

## FINAL CHECKLIST

### Code Quality
- [ ] All functions compile without warnings
- [ ] All unit tests pass (10 tests)
- [ ] All integration tests pass (2 tests)
- [ ] Code follows ClojureScript idioms
- [ ] All functions have docstrings

### Functionality
- [ ] Session index loads fast (<100ms for 100 sessions)
- [ ] Browser UI filters and sorts correctly
- [ ] Can select and compare any 2 sessions
- [ ] Comparison report shows accurate deltas
- [ ] Trend analysis computes regression correctly
- [ ] Charts render smoothly

### User Experience
- [ ] Session browser is intuitive
- [ ] Comparison view is easy to read
- [ ] Trend graphs tell a clear story
- [ ] No console errors
- [ ] Responsive UI (handles 100+ sessions)

### Documentation
- [ ] README updated with analytics features
- [ ] PLAN.md marked LOD 6 complete
- [ ] Code comments explain algorithms

---

## TIME TRACKING

| Task | Estimated | Actual | Status |
|------|-----------|--------|--------|
| 7.1 Session Index | 3h | | [ ] |
| 7.2 Session Browser | 5h | | [ ] |
| 7.3 Comparison Logic | 4h | | [ ] |
| 7.4 Comparison UI | 4h | | [ ] |
| 7.5 Trend Analysis | 4-6h | | [ ] |
| **Total** | **20-22h** | | |

---

**Ready to ship LOD 6? Let's make progress visible.** 📈
