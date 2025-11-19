# LOD 6 CONTEXT: MULTI-SESSION ANALYTICS
## From Individual Sessions to Long-Term Insights

---

## WHAT YOU'VE BUILT SO FAR

You've reached a major milestone: CombatSys can now capture, analyze, and personalize feedback. Here's the foundation:

### ✅ LOD 0-1: Foundation (Complete)
- ClojureScript/Electron app with hot reload
- EDN data schemas with clojure.spec validation
- Camera capture + MediaPipe pose estimation
- Real-time skeleton overlay
- Session recording and playback to disk

### ✅ LOD 2: Breathing Analyzer (Complete)
- Torso motion extraction from pose landmarks
- FFT-based breathing rate detection (6-30 bpm range)
- Fatigue window detection (breath holds, shallow breathing)
- Insight generation in coaching language

### ✅ LOD 3: Eulerian Magnification (Complete)
- WebGPU compute pipeline
- Laplacian pyramid decomposition
- Temporal bandpass filtering (0.1-0.5 Hz)
- 20-30x motion amplification for visualization

### ✅ LOD 4: Posture Analyzer (Complete)
- Forward head measurement (cm ahead of shoulders)
- Shoulder imbalance detection (left/right height delta)
- Spine alignment classification
- Multi-analyzer tabbed UI

### ✅ LOD 5: User Calibration (Complete)
- 3-step calibration wizard (T-pose, breathing, movement)
- Personalized threshold computation
- User profile storage & persistence
- Analyzers use personal baselines
- Personalized insights ("15% below your baseline")

---

## THE PROBLEM LOD 6 SOLVES

**Current limitation**: Each session is analyzed in **isolation**. Users get insights about their current state, but no **longitudinal perspective**:

- "My breathing rate is 18 bpm today" → But what was it yesterday? Last week?
- "Forward head: 4.2cm" → Is that improving or getting worse?
- "Posture score: 0.84" → Is this my best session? My worst?
- "I feel like I'm improving" → Can the data confirm this?

**Why this fails**:
- No historical context for metrics
- Can't track progress over weeks/months
- Can't identify patterns (e.g., "I breathe worse in the evening")
- Can't compare "before training" vs "after 30 days of practice"
- Motivation suffers without visible progress

**LOD 6 solution**: Aggregate sessions into **timeline views**, enable **session comparison**, and surface **trend insights** that show improvement over time.

---

## THE VISION: WHAT LOD 6 DELIVERS

By end of LOD 6, the system:

1. **Session Browser**: "See all your training history"
   - Timeline view: All sessions sorted by date
   - Filter by date range, analysis type, tags
   - Search by notes/keywords
   - Quick stats: session count, total training time, avg metrics

2. **Session Comparison**: "How did Session A compare to Session B?"
   - Select any 2 sessions
   - Side-by-side metric comparison
   - Delta report: "Breathing rate +3 bpm, Posture score +12%"
   - Visual diff: overlaid skeleton replays, synchronized timeline

3. **Trend Analysis**: "Am I improving?"
   - Graph metrics over last N sessions (5, 10, 20, all)
   - Linear regression trend lines
   - Identify improvements, declines, plateaus
   - Aggregate insights: "Your breathing depth improved 15% over 10 sessions"

4. **Analytics Dashboard**: "What are my patterns?"
   - Best session ever (by metric)
   - Most consistent metric
   - Day-of-week patterns (if enough data)
   - Time-of-day patterns

---

## ARCHITECTURAL PHILOSOPHY (The Team Weighs In)

### Rich Hickey's Perspective: "Aggregation is Just Data Transformation"

```clojure
;; Sessions are just a collection of data points
;; Comparison is a pure function: [session-a session-b] → diff-map

(defn compare-sessions [session-a session-b]
  (let [breathing-a (get-in session-a [:session/analysis :breathing])
        breathing-b (get-in session-b [:session/analysis :breathing])

        delta-rate (- (:rate-bpm breathing-b) (:rate-bpm breathing-a))
        pct-change (* 100 (/ delta-rate (:rate-bpm breathing-a)))]

    {:breathing {:metric-a (:rate-bpm breathing-a)
                 :metric-b (:rate-bpm breathing-b)
                 :delta delta-rate
                 :pct-change pct-change
                 :direction (if (pos? delta-rate) :increased :decreased)}}))

;; Trend analysis: just a reduce over sessions
(defn compute-trend [sessions metric-path]
  (let [values (map #(get-in % metric-path) sessions)
        ;; Fit linear regression
        trend-line (fit-linear-regression values)]
    {:slope (:m trend-line)
     :direction (if (pos? (:m trend-line)) :improving :declining)
     :values values}))
```

**Key insight**: Analytics don't require new data structures—just new **views** of existing data.

---

### John Carmack's Perspective: "Optimize Metadata Loading"

**Performance consideration**: With 100+ sessions, we can't load all timelines into memory.

**Strategy**:
- **Session index file**: Store lightweight metadata separately
  ```clojure
  ;; sessions/index.edn (fast to load)
  [{:session/id #uuid "..."
    :session/name "Morning Training"
    :session/created-at "2025-11-18T10:30:00Z"
    :session/duration-ms 30000
    :session/frame-count 450
    :session/summary-stats {:avg-breathing-rate 21.5
                            :avg-posture-score 0.84}}
   ...]
  ```

- **Lazy session loading**: Load full timelines only when needed
- **Browser UI**: Render list from index (fast), load details on click

**Performance targets**:
- Load index file: <100ms for 100 sessions
- Render browser list: <200ms (virtualized if >100 sessions)
- Load single session for comparison: <500ms

---

### Brett Victor's Perspective: "Make Progress Visible"

**Visualization principles**:

1. **Timeline graphs show the story**:
   ```
   Breathing Rate Over Time
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   24 bpm ┤                                    ●
          │                              ●   ●
   22 bpm ┤                        ●   ●
          │                  ●   ●
   20 bpm ┤            ●   ●
          │      ●   ●
   18 bpm ┤●   ●
          └────────────────────────────────────────→
          Nov 1        Nov 8        Nov 15   Nov 22

   Trend: ↗ Improving (+12% over 3 weeks)
   ```

2. **Comparison view shows delta clearly**:
   ```
   Session A (Nov 1)       Session B (Nov 22)      Delta
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   Breathing Rate
   18 bpm                  22 bpm                  +4 bpm (+22%) ✓

   Posture Score
   0.78                    0.89                    +0.11 (+14%) ✓

   Forward Head
   5.2cm                   3.8cm                   -1.4cm (-27%) ✓
   ```

3. **Dashboard shows highlights**:
   ```
   ┌─────────────────────────────────────────┐
   │  Your Progress                          │
   │  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
   │  🏆 Best Session                        │
   │     Nov 22: Posture Score 0.89          │
   │                                         │
   │  📈 Biggest Improvement                 │
   │     Forward Head: -27% in 3 weeks       │
   │                                         │
   │  🎯 Most Consistent                     │
   │     Breathing Rate: ±1.2 bpm            │
   │                                         │
   │  ⏱ Total Training Time                  │
   │     12 sessions, 6h 15m total           │
   └─────────────────────────────────────────┘
   ```

**UI principle**: Every graph tells a story. Every comparison highlights change.

---

### Paul Graham's Perspective: "Ship the Essential, Defer the Rest"

**LOD 6.0 (Minimum Viable Analytics)**:
- Session browser: list all sessions, filter by date
- Session comparison: pick 2, see side-by-side metrics
- Trend graphs: line chart for 1 metric over N sessions
- Basic insights: "improving", "declining", "stable"

**What we DON'T ship in LOD 6**:
- ❌ Advanced statistics (correlation, statistical significance) → LOD 6.1
- ❌ Multi-user leaderboards → Future
- ❌ Export to PDF/CSV → LOD 7
- ❌ Cloud sync for analytics → Future
- ❌ Predictive modeling ("You'll hit 25 bpm by Dec 1") → Future
- ❌ Day-of-week/time-of-day patterns (requires >30 sessions) → LOD 6.2

**Success criterion**: After 10+ recorded sessions, user can browse history, compare any 2 sessions, and see trend graphs that show improvement.

---

## DATA MODEL EXTENSIONS

### New Schema Definitions

```clojure
(ns combatsys.schema
  (:require [clojure.spec.alpha :as s]))

;; ============================================================
;; SESSION INDEX (LOD 6)
;; ============================================================

(s/def ::session-metadata
  "Lightweight session metadata for fast loading"
  (s/keys :req-un [::session-id ::session-name ::created-at
                   ::duration-ms ::frame-count]
          :opt-un [::summary-stats ::tags ::notes]))

(s/def ::summary-stats
  "Precomputed aggregate stats for quick display"
  (s/keys :opt-un [::avg-breathing-rate ::avg-posture-score
                   ::avg-confidence ::total-fatigue-windows]))

(s/def ::tags (s/coll-of string?))
(s/def ::notes string?)

(s/def ::session-index
  "Collection of session metadata"
  (s/coll-of ::session-metadata))

;; ============================================================
;; SESSION COMPARISON (LOD 6)
;; ============================================================

(s/def ::metric-comparison
  (s/keys :req-un [::metric-a ::metric-b ::delta ::pct-change ::direction]))

(s/def ::metric-a number?)
(s/def ::metric-b number?)
(s/def ::delta number?)
(s/def ::pct-change number?)
(s/def ::direction #{:increased :decreased :unchanged})

(s/def ::comparison-report
  "Result of comparing two sessions"
  (s/keys :req-un [::session-a-id ::session-b-id ::timestamp-compared]
          :opt-un [::breathing-comparison ::posture-comparison
                   ::overall-assessment ::insights]))

(s/def ::breathing-comparison
  (s/keys :req-un [::rate-comparison ::depth-comparison]))

(s/def ::posture-comparison
  (s/keys :req-un [::forward-head-comparison ::shoulder-imbalance-comparison]))

(s/def ::overall-assessment
  #{:significant-improvement :slight-improvement :stable
    :slight-decline :significant-decline})

;; ============================================================
;; TREND ANALYSIS (LOD 6)
;; ============================================================

(s/def ::trend-data
  (s/keys :req-un [::metric-name ::values ::timestamps
                   ::trend-direction ::slope]))

(s/def ::metric-name keyword?)
(s/def ::values (s/coll-of number?))
(s/def ::timestamps (s/coll-of ::instant))
(s/def ::trend-direction #{:improving :declining :stable})
(s/def ::slope number?)

(s/def ::trend-analysis
  "Trend analysis over multiple sessions"
  (s/keys :req-un [::session-count ::date-range ::trends]))

(s/def ::trends
  "Map of metric-name → trend-data"
  (s/map-of keyword? ::trend-data))

(s/def ::date-range
  (s/keys :req-un [::start-date ::end-date]))

(s/def ::start-date ::instant)
(s/def ::end-date ::instant)
```

---

## INTEGRATION POINTS: WHERE EXISTING CODE CHANGES

### 1. Session Persistence (Add Indexing)

**Before LOD 6**:
```clojure
;; Just save individual sessions
(defn save-session! [session]
  (let [path (get-session-path (:session/id session))
        edn-str (pr-str session)]
    (write-file! path edn-str)))
```

**After LOD 6**:
```clojure
;; Save session AND update index
(defn save-session! [session]
  (let [path (get-session-path (:session/id session))
        edn-str (pr-str session)]
    (write-file! path edn-str)

    ;; Update index
    (update-session-index! session)))

(defn update-session-index! [session]
  "Add session metadata to index file for fast loading"
  (let [metadata (extract-session-metadata session)
        index (load-session-index!)
        updated-index (conj index metadata)]
    (save-session-index! updated-index)))
```

**Files to modify**:
- `src/renderer/persistence.cljs` - Add index management

### 2. State Management (Add Analytics State)

**src/renderer/state.cljs**:
```clojure
(def app-state
  (atom
   {:ui {...}
    :capture {...}
    :sessions {...}  ;; Full sessions (loaded on demand)
    :session-index nil  ;; NEW: Lightweight metadata for all sessions
    :analytics {  ;; NEW: Analytics state
      :selected-session-ids []  ;; For comparison (max 2)
      :trend-config {:metric :breathing-rate :session-count 10}
      :comparison-report nil
      :trend-data nil}
    :user-profile {...}}))
```

### 3. New Views

**src/renderer/views.cljs** - Add navigation:
```clojure
(defn navbar []
  [:nav.navbar
   [:button {:on-click #(rf/dispatch [:ui/set-view :live-feed])} "Live"]
   [:button {:on-click #(rf/dispatch [:ui/set-view :session-browser])} "Sessions"]  ;; NEW
   [:button {:on-click #(rf/dispatch [:ui/set-view :analytics])} "Analytics"]  ;; NEW
   [:button {:on-click #(rf/dispatch [:ui/set-view :profile])} "Profile"]])
```

---

## FILE STRUCTURE (New files for LOD 6)

```
src/
├── shared/
│   ├── comparison.cljs       # NEW: Session comparison logic
│   ├── trends.cljs           # NEW: Trend analysis & regression
│   └── analytics.cljs        # NEW: Aggregate stats & insights
│
├── renderer/
│   ├── session_browser.cljs  # NEW: Session list UI
│   ├── comparison_view.cljs  # NEW: Side-by-side comparison UI
│   ├── analytics_view.cljs   # NEW: Trend graphs & dashboard
│   └── charts.cljs           # NEW: Reusable chart components
│
└── main/
    └── (no new files)
```

---

## CORE ALGORITHMS

### 1. Session Comparison Algorithm

```clojure
(defn compare-metric [metric-a metric-b]
  "Compare two numeric metrics and classify change"
  (let [delta (- metric-b metric-a)
        pct-change (if (zero? metric-a)
                     0
                     (* 100 (/ delta metric-a)))

        direction (cond
                    (> pct-change 5) :increased
                    (< pct-change -5) :decreased
                    :else :unchanged)]

    {:metric-a metric-a
     :metric-b metric-b
     :delta delta
     :pct-change pct-change
     :direction direction}))

(defn compare-sessions [session-a session-b]
  "Full comparison between two sessions"
  (let [breathing-a (get-in session-a [:session/analysis :breathing])
        breathing-b (get-in session-b [:session/analysis :breathing])
        posture-a (get-in session-a [:session/analysis :posture])
        posture-b (get-in session-b [:session/analysis :posture])]

    {:session-a-id (:session/id session-a)
     :session-b-id (:session/id session-b)
     :timestamp-compared (js/Date.)

     :breathing-comparison
     {:rate-comparison (compare-metric
                        (:rate-bpm breathing-a)
                        (:rate-bpm breathing-b))
      :depth-comparison (compare-metric
                         (:depth-score breathing-a)
                         (:depth-score breathing-b))}

     :posture-comparison
     {:forward-head-comparison (compare-metric
                                (:head-forward-cm posture-a)
                                (:head-forward-cm posture-b))
      :shoulder-imbalance-comparison (compare-metric
                                      (:shoulder-imbalance-deg posture-a)
                                      (:shoulder-imbalance-deg posture-b))}

     :overall-assessment (assess-overall-change session-a session-b)}))
```

### 2. Trend Analysis (Linear Regression)

```clojure
(defn fit-linear-regression [values]
  "Fit y = mx + b to data points.

  Args:
    values: Vector of y-values (x-values are indices 0, 1, 2, ...)

  Returns:
    {:m slope :b intercept :r2 r-squared}"
  (let [n (count values)
        xs (range n)
        ys values

        sum-x (reduce + xs)
        sum-y (reduce + ys)
        sum-xx (reduce + (map * xs xs))
        sum-xy (reduce + (map * xs ys))

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
        r2 (- 1 (/ ss-res ss-tot))]

    {:m m :b b :r2 r2}))

(defn compute-trend [sessions metric-path]
  "Compute trend for a specific metric across sessions.

  Args:
    sessions: Vector of sessions (sorted by date)
    metric-path: Path to metric, e.g. [:session/analysis :breathing :rate-bpm]

  Returns:
    Trend data with slope, direction, and values"
  (let [values (map #(get-in % metric-path) sessions)
        timestamps (map :session/created-at sessions)
        regression (fit-linear-regression values)

        direction (cond
                    (> (:m regression) 0.1) :improving
                    (< (:m regression) -0.1) :declining
                    :else :stable)]

    {:metric-name (last metric-path)
     :values values
     :timestamps timestamps
     :trend-direction direction
     :slope (:m regression)
     :r2 (:r2 regression)}))
```

### 3. Aggregate Insights Generation

```clojure
(defn generate-aggregate-insights [sessions]
  "Generate high-level insights from session collection"
  (let [breathing-trend (compute-trend sessions
                                       [:session/analysis :breathing :rate-bpm])
        posture-trend (compute-trend sessions
                                     [:session/analysis :posture :overall-score])

        insights []]

    (cond-> insights
      ;; Breathing improvement
      (= :improving (:trend-direction breathing-trend))
      (conj {:title "Breathing capacity improving"
             :description (str "Your breathing rate increased by "
                               (.toFixed (* (:slope breathing-trend)
                                            (count sessions)) 1)
                               " bpm over " (count sessions) " sessions")
             :severity :positive})

      ;; Posture improvement
      (= :improving (:trend-direction posture-trend))
      (conj {:title "Posture improving"
             :description (str "Your posture score improved by "
                               (.toFixed (* 100 (* (:slope posture-trend)
                                                   (count sessions))) 1)
                               "% over " (count sessions) " sessions")
             :severity :positive})

      ;; Consistency
      (and (< (count sessions) 5)
           (> (count sessions) 0))
      (conj {:title "Build consistency"
             :description (str "You've recorded " (count sessions)
                               " sessions. Record 10+ for trend analysis.")
             :severity :info}))))
```

---

## TESTING STRATEGY

### Unit Tests (Pure Functions)

```clojure
(ns combatsys.comparison-test
  (:require [clojure.test :refer [deftest is testing]]
            [combatsys.comparison :as comp]))

(deftest test-compare-metric
  (testing "Metric comparison computes delta correctly"
    (let [result (comp/compare-metric 20 22)]
      (is (= 2 (:delta result)))
      (is (= 10.0 (:pct-change result)))
      (is (= :increased (:direction result))))))

(deftest test-linear-regression
  (testing "Linear regression fits line to data"
    (let [values [10 12 14 16 18]  ;; Perfect line: y = 2x + 10
          result (comp/fit-linear-regression values)]
      (is (< 1.9 (:m result) 2.1))  ;; Slope ≈ 2
      (is (< 9.5 (:b result) 10.5))  ;; Intercept ≈ 10
      (is (> (:r2 result) 0.99)))))  ;; Perfect fit
```

### Integration Tests

```clojure
(deftest test-session-comparison-flow
  (testing "Full session comparison works end-to-end"
    (let [session-a (mocks/mock-session-with-analysis 20 0.78)
          session-b (mocks/mock-session-with-analysis 22 0.84)

          comparison (comp/compare-sessions session-a session-b)]

      ;; Comparison is valid
      (is (s/valid? ::schema/comparison-report comparison))

      ;; Breathing rate increased
      (is (= :increased
             (get-in comparison [:breathing-comparison
                                 :rate-comparison
                                 :direction])))

      ;; Posture score improved
      (is (pos? (get-in comparison [:posture-comparison
                                    :overall-score-comparison
                                    :delta]))))))
```

---

## SUCCESS CRITERIA FOR LOD 6

At the end of LOD 6, the following demo works flawlessly:

```bash
$ npm start

# User has 12 recorded sessions

# Navigate to "Sessions" tab
┌─────────────────────────────────────────────────────────────┐
│  Your Sessions (12)                    [Sort: Date ▼] [Filter]│
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                                             │
│  ☐ Nov 22  Evening Training    30s  22 bpm  0.89          │
│  ☐ Nov 21  Morning Practice    45s  21 bpm  0.86          │
│  ☐ Nov 20  Quick Check         25s  20 bpm  0.84          │
│  ...                                                        │
│  ☐ Nov 1   Baseline             60s  18 bpm  0.78          │
│                                                             │
│  [Compare Selected (2)]                                     │
└─────────────────────────────────────────────────────────────┘

# Select Nov 1 and Nov 22, click "Compare Selected"
┌─────────────────────────────────────────────────────────────┐
│  Comparison: Nov 1 → Nov 22                                 │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                                             │
│  Breathing                                                  │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│  Rate:        18 bpm → 22 bpm       +4 bpm (+22%)  ✓      │
│  Depth:       0.75   → 0.82         +0.07 (+9%)    ✓      │
│                                                             │
│  Posture                                                    │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│  Score:       0.78   → 0.89         +0.11 (+14%)   ✓      │
│  Fwd Head:    5.2cm  → 3.8cm        -1.4cm (-27%)  ✓      │
│                                                             │
│  Overall: 📈 Significant Improvement                        │
└─────────────────────────────────────────────────────────────┘

# Navigate to "Analytics" tab
┌─────────────────────────────────────────────────────────────┐
│  Analytics Dashboard          [Last 10 Sessions ▼]          │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                                             │
│  Breathing Rate Trend                                       │
│  24 bpm ┤                                    ●              │
│         │                              ●   ●                │
│  22 bpm ┤                        ●   ●                      │
│         │                  ●   ●                            │
│  20 bpm ┤            ●   ●                                  │
│         │      ●   ●                                        │
│  18 bpm ┤●   ●                                              │
│         └────────────────────────────────────────────→      │
│         Nov 1        Nov 8        Nov 15        Nov 22      │
│                                                             │
│  Trend: ↗ Improving (+12% over 3 weeks)                    │
│  R² = 0.92 (strong linear trend)                           │
│                                                             │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                                             │
│  🏆 Best Session: Nov 22 (Posture Score 0.89)              │
│  📈 Biggest Gain: Forward Head -27% in 3 weeks             │
│  🎯 Most Consistent: Breathing Rate (±1.2 bpm)             │
└─────────────────────────────────────────────────────────────┘
```

---

## YOUR MISSION FOR LOD 6

Transform CombatSys from a **session-by-session tool** into a **longitudinal training companion** that shows users their progress over time.

You're not just building analytics. You're building **motivation**—the user sees their improvement and keeps training.

**Channel the team**:
- **Hickey**: Keep it pure. Analytics are just data transformations.
- **Carmack**: Optimize for browsing 100+ sessions. Lazy load timelines.
- **Victor**: Make trends visible. Every graph tells a story.
- **Graham**: Ship the essentials first. Advanced stats can wait.

**Now go make progress visible.** 📈
