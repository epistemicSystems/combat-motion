# LOD 4: POSTURE ANALYZER - CONTEXT DOCUMENT
**Phase**: Days 11-13 of Development Roadmap
**Status**: Ready to Begin
**Prerequisites**: ✅ LOD 0 Complete, ✅ LOD 1 Complete, ✅ LOD 2 Complete, ✅ LOD 3 Complete
**Date Prepared**: 2025-11-18

---

## 🎯 MISSION STATEMENT

**Objective**: Prove the **modularity** of the analysis architecture by adding a second analyzer (posture assessment) without touching existing breathing code, demonstrating that multiple independent analyzers can coexist and compose cleanly.

**Success Criteria**: At the end of LOD 4, a user can:
1. Record a session and analyze BOTH breathing AND posture
2. See breathing metrics in one tab, posture metrics in another
3. Understand their postural alignment through clear metrics and insights
4. Navigate between multiple analysis types seamlessly
5. Verify that adding posture didn't break breathing analysis

**The "Aha!" Moment**: User realizes "This isn't just a breathing tool—it's a complete motion analysis platform!"

---

## 📊 CURRENT PROJECT STATE

### What We Have (LOD 0 + LOD 1 + LOD 2 + LOD 3)

#### ✅ Complete Pipeline
```
Camera (30fps) → MediaPipe (15fps) → Poses (33 landmarks)
                                        ↓
                              Recorded Sessions (EDN)
                                        ↓
                    ┌───────────────────┴───────────────────┐
                    ↓                                       ↓
          Breathing Analysis                    Eulerian Magnification
          (FFT, Fatigue Detection)              (GPU-Accelerated)
                    ↓                                       ↓
          Results: Rate, Depth, Insights        Side-by-side Video
```

#### ✅ Code Base Status
- **Production Code**: ~6,500 lines ClojureScript
- **Test Code**: ~800 lines
- **Documentation**: ~12,000 lines (15 comprehensive docs)
- **Architecture**: Functional core / Imperative shell ✅
- **Performance**: Real-time pose @ 15fps ✅
- **Offline Analysis**: Breathing analysis <1s for 60s footage ✅
- **GPU Compute**: Eulerian magnification <2min for 60s video ✅

#### ✅ Key Capabilities
```clojure
;; We can already:
;; 1. Capture and record video sessions
(def session (record-session 60)) ;; 60 seconds

;; 2. Analyze breathing
(def breathing-result (breathing/analyze session))
(:rate-bpm breathing-result) ;; => 21.8 bpm

;; 3. Magnify subtle breathing motion
(def magnified (magnification/magnify-frames! frames roi 25))

;; 4. Store all data in pure EDN
(:session/analysis session)
;; => {:breathing {:rate-bpm 21.8 :fatigue-windows [...]}
;;     :posture nil}  ← We're adding this!
```

#### ⚠️ What's Missing (LOD 4)

**The Second Analyzer**: We need to prove the architecture scales to multiple analyzers.

**Current Limitation**:
- Only ONE analyzer (breathing) exists
- UI is hardcoded for breathing metrics only
- No demonstration of modularity

**Solution**: Add Posture Analyzer
- Independent module (doesn't depend on breathing)
- Shares same data pipeline (pose landmarks)
- Produces same schema (conforms to `::posture-analysis`)
- Composes with breathing analysis seamlessly

---

## 🧠 POSTURE ANALYSIS: THE SCIENCE

### What is Posture Assessment?

**Posture** is the alignment of body segments in space. Good posture means:
- Head aligned over shoulders
- Shoulders level (no tilt)
- Spine maintains natural curves
- Weight distributed evenly

**Camera-based posture assessment** uses 2D/3D landmarks to measure:
1. **Forward head posture** (FHP)
2. **Shoulder imbalance** (left vs right height)
3. **Spine alignment** (kyphotic, lordotic, scoliotic, or neutral)

### Key Posture Metrics

#### 1. Forward Head Posture (FHP)

**Definition**: Horizontal distance from head center to vertical line through shoulders.

**Why it matters**:
- Common in desk workers, gamers, phone users
- "Text neck" syndrome
- Leads to neck pain, headaches, reduced lung capacity

**Measurement**:
```
Vertical line through shoulders
        ↓
        |
        |  ← Head forward by X cm
    👤 |
   ╱|╲ |
     |
```

**Calculation**:
```clojure
(defn compute-forward-head [nose-pos shoulders-midpoint]
  ;; Project nose onto vertical plane through shoulders
  ;; FHP = horizontal distance (in cm)
  (let [nose-x (:x nose-pos)
        shoulder-x (average (:x left-shoulder) (:x right-shoulder))
        pixel-to-cm-ratio 0.2] ;; Calibrated from user height
    (* (abs (- nose-x shoulder-x)) pixel-to-cm-ratio)))
```

**Normal Range**: 0-3 cm (minimal forward head)
**Alert Threshold**: >5 cm (significant FHP)

#### 2. Shoulder Imbalance

**Definition**: Vertical difference between left and right shoulder heights.

**Why it matters**:
- Indicates muscle imbalance
- Can lead to scoliosis, chronic pain
- Common in athletes with asymmetric sports (tennis, golf)

**Measurement**:
```
Left shoulder higher by Y degrees:

  ╱ ← Y degrees tilt
 ╱
👤
|
```

**Calculation**:
```clojure
(defn compute-shoulder-imbalance [left-shoulder right-shoulder]
  ;; Compute angle of line connecting shoulders
  (let [dy (- (:y right-shoulder) (:y left-shoulder))
        dx (- (:x right-shoulder) (:x left-shoulder))
        angle-rad (Math/atan2 dy dx)
        angle-deg (* angle-rad (/ 180 Math/PI))]
    angle-deg)) ;; 0° = level, >0° = right higher, <0° = left higher
```

**Normal Range**: -3° to +3° (shoulders nearly level)
**Alert Threshold**: >5° (significant imbalance)

#### 3. Spine Alignment

**Definition**: Classification of spine curvature based on landmarks.

**Categories**:
- **Neutral**: Natural S-curve (ideal)
- **Kyphotic**: Excessive upper back rounding (hunched)
- **Lordotic**: Excessive lower back curve (swayback)
- **Scoliotic**: Lateral curvature (sideways)

**Measurement**:
```
Side view (sagittal plane):

Neutral:     Kyphotic:    Lordotic:
   |            /             \
   S           C              C
   |          /                \
```

**Calculation**:
```clojure
(defn assess-spine-alignment [shoulders hips knees]
  ;; Fit curve through shoulder-hip-knee
  ;; Classify based on curvature direction
  (let [shoulder-to-hip (vector-subtract hips shoulders)
        hip-to-knee (vector-subtract knees hips)
        cross-product (cross shoulder-to-hip hip-to-knee)
        curvature (dot cross-product [0 0 1])] ;; z-axis
    (cond
      (< (abs curvature) 0.1) :neutral
      (> curvature 0.3) :kyphotic
      (< curvature -0.3) :lordotic
      :else :neutral)))
```

### Scientific Validation

**Clinical Standards**:
1. **Craniovertebral Angle (CVA)**: Standard FHP metric (we approximate)
2. **Shoulder Alignment**: Physical therapy standard (we use 2D approximation)
3. **Spine Curvature**: Based on Cobb angle (we use simplified classification)

**Our Approach**: Simplified but clinically meaningful
- Camera-only (no markers)
- 2D approximation (with depth hints from MediaPipe)
- Fast computation (<1ms per frame)
- User-friendly insights (not medical diagnosis)

---

## 📐 IMPLEMENTATION STRATEGY

### Three-Phase Approach

```
Phase 1: Core Posture Functions (Task 5.1.1)
  → Extract relevant landmarks
  → Compute geometric metrics
  → Generate insights
  → Pure functions, fully tested

Phase 2: Integration with Session Pipeline (Task 5.1.2)
  → Add posture analysis to session workflow
  → Validate against schema
  → Test alongside breathing analysis
  → Verify independence (no coupling)

Phase 3: Multi-Analyzer UI (Task 5.2)
  → Tabbed interface (Breathing | Posture)
  → Metrics display per analyzer
  → Insight cards per analyzer
  → State management for multiple analyses
```

### Architectural Principles (Hickey's Guidance)

**Data-Centric Design**:
```clojure
;; Session schema accommodates multiple analyzers
{:session/id #uuid "..."
 :session/timeline [...]
 :session/analysis
 {:breathing {...}  ;; Already exists
  :posture {...}}}  ;; We're adding this
```

**Composable Functions**:
```clojure
;; Each analyzer is a pure function: session → session'
(defn analyze-session [session]
  (-> session
      breathing/analyze
      posture/analyze
      gait/analyze))     ;; Future analyzer (LOD 5+)
```

**No Coupling**:
- Posture analyzer doesn't import breathing namespace
- Breathing analyzer doesn't know posture exists
- Both operate on same session data structure
- UI coordinates them via re-frame

---

## 🔧 POSTURE ANALYZER ALGORITHM

### Step-by-Step Breakdown

#### Input: Session with Timeline
```clojure
{:session/timeline
 [{:frame/pose {:landmarks [...]}}
  {:frame/pose {:landmarks [...]}}
  ...]}
```

#### Step 1: Landmark Extraction
```clojure
(defn extract-relevant-landmarks [timeline]
  "Extract nose, shoulders, hips, knees from each frame.
   Average over timeline to reduce noise."
  (let [frames timeline]
    {:nose (average-landmark frames :nose)
     :left-shoulder (average-landmark frames :left-shoulder)
     :right-shoulder (average-landmark frames :right-shoulder)
     :left-hip (average-landmark frames :left-hip)
     :right-hip (average-landmark frames :right-hip)
     :left-knee (average-landmark frames :left-knee)
     :right-knee (average-landmark frames :right-knee)}))
```

**Why Average?**
- Reduce noise from jitter, occlusion
- Posture is quasi-static (doesn't change rapidly)
- User's typical posture is the median over session

#### Step 2: Forward Head Computation
```clojure
(defn measure-forward-head [nose shoulders]
  "Compute horizontal distance from nose to shoulder midpoint.

   Returns: Distance in centimeters"
  (let [shoulder-midpoint (midpoint (:left shoulders) (:right shoulders))
        nose-x (:x nose)
        shoulder-x (:x shoulder-midpoint)
        pixel-distance (abs (- nose-x shoulder-x))

        ;; Convert pixels to cm (requires calibration)
        ;; Assume user height known from onboarding
        cm-per-pixel 0.2] ;; Rough estimate

    (* pixel-distance cm-per-pixel)))
```

**Calibration** (for pixel → cm conversion):
```clojure
(defn calibrate-pixel-to-cm [user-height-cm landmarks]
  "Compute pixel-to-cm ratio based on known user height.

   Uses full-body height in pixels vs. known height in cm."
  (let [head-y (:y (:nose landmarks))
        ankle-y (average (:y (:left-ankle landmarks))
                         (:y (:right-ankle landmarks)))
        height-pixels (abs (- head-y ankle-y))
        cm-per-pixel (/ user-height-cm height-pixels)]
    cm-per-pixel))
```

#### Step 3: Shoulder Imbalance Computation
```clojure
(defn measure-shoulder-imbalance [left-shoulder right-shoulder]
  "Compute angle of shoulder line relative to horizontal.

   Returns: Angle in degrees (0° = level, positive = right higher)"
  (let [dy (- (:y right-shoulder) (:y left-shoulder))
        dx (- (:x right-shoulder) (:x left-shoulder))
        angle-rad (Math/atan2 dy dx)
        angle-deg (* angle-rad (/ 180 Math/PI))]

    ;; Normalize to [-90, +90] range
    (cond
      (> angle-deg 90) (- angle-deg 180)
      (< angle-deg -90) (+ angle-deg 180)
      :else angle-deg)))
```

#### Step 4: Spine Alignment Assessment
```clojure
(defn assess-spine-alignment [shoulders hips knees]
  "Classify spine alignment based on landmark positions.

   Returns: :neutral | :kyphotic | :lordotic | :scoliotic"
  (let [;; Compute vectors
        shoulder-center (midpoint (:left shoulders) (:right shoulders))
        hip-center (midpoint (:left hips) (:right hips))
        knee-center (midpoint (:left knees) (:right knees))

        ;; Upper body vector (shoulders → hips)
        upper-vec {:x (- (:x hip-center) (:x shoulder-center))
                   :y (- (:y hip-center) (:y shoulder-center))}

        ;; Lower body vector (hips → knees)
        lower-vec {:x (- (:x knee-center) (:x hip-center))
                   :y (- (:y knee-center) (:y hip-center))}

        ;; Compute angle between vectors (deviation from straight line)
        angle (angle-between upper-vec lower-vec)]

    (cond
      ;; Straight alignment
      (< (abs angle) 10) :neutral

      ;; Forward lean (hunched)
      (and (> angle 10) (> (:x upper-vec) 0)) :kyphotic

      ;; Backward lean (swayback)
      (and (> angle 10) (< (:x upper-vec) 0)) :lordotic

      ;; Default
      :else :neutral)))
```

#### Step 5: Overall Score Computation
```clojure
(defn compute-overall-score [fhp shoulder-imbalance spine]
  "Combine metrics into overall posture score (0-1 scale).

   Scoring:
   - FHP: 0cm = 1.0, 10cm = 0.0
   - Shoulder: 0° = 1.0, 15° = 0.0
   - Spine: :neutral = 1.0, :kyphotic/:lordotic = 0.7"
  (let [fhp-score (max 0 (- 1.0 (/ fhp 10.0)))
        shoulder-score (max 0 (- 1.0 (/ (abs shoulder-imbalance) 15.0)))
        spine-score (case spine
                      :neutral 1.0
                      :kyphotic 0.7
                      :lordotic 0.7
                      :scoliotic 0.5)]

    ;; Weighted average
    (/ (+ (* 0.4 fhp-score)
          (* 0.3 shoulder-score)
          (* 0.3 spine-score))
       1.0)))
```

#### Step 6: Insight Generation
```clojure
(defn generate-insights [fhp shoulder-imbalance spine overall-score]
  "Convert posture metrics into coaching language.

   Returns: Vector of insight maps"
  (let [insights []]

    ;; FHP insight
    (when (> fhp 5.0)
      (conj insights
            {:insight/title "Forward head posture detected"
             :insight/description (str "Your head is " (format "%.1f" fhp)
                                      " cm forward of your shoulders. "
                                      "This can lead to neck strain.")
             :insight/severity (if (> fhp 8.0) :high :medium)
             :insight/recommendation
             "Practice chin tucks: gently pull chin back toward neck. "
             "Hold 5 seconds, repeat 10 times, 3× daily."}))

    ;; Shoulder imbalance insight
    (when (> (abs shoulder-imbalance) 5.0)
      (let [higher-side (if (> shoulder-imbalance 0) "right" "left")]
        (conj insights
              {:insight/title "Shoulder imbalance detected"
               :insight/description (str "Your " higher-side " shoulder is "
                                        (format "%.1f" (abs shoulder-imbalance))
                                        "° higher than the other.")
               :insight/severity :medium
               :insight/recommendation
               (str "Stretch your " higher-side " side regularly. "
                    "Consider strengthening the opposite side.")})))

    ;; Spine alignment insight
    (when (not= spine :neutral)
      (conj insights
            {:insight/title (str (name spine) " spine detected")
             :insight/description "Your spine shows deviation from neutral alignment."
             :insight/severity :medium
             :insight/recommendation
             "Practice core strengthening exercises and consult a professional "
             "if pain persists."}))

    ;; Overall score insight
    (when (< overall-score 0.7)
      (conj insights
            {:insight/title "Posture needs improvement"
             :insight/description (str "Overall posture score: "
                                      (format "%.0f" (* 100 overall-score))
                                      "/100")
             :insight/severity :low
             :insight/recommendation
             "Regular posture checks and targeted exercises can help improve "
             "your alignment over time."}))

    insights))
```

### Complete Analysis Function

```clojure
(defn analyze
  "Main entry point: analyze posture in a session.

   Pure function: session → session'

   Args:
     session: Session map with timeline

   Returns:
     Session map with :posture added to :session/analysis

   Example:
     (def session (load-session \"my-session\"))
     (def analyzed (posture/analyze session))
     (get-in analyzed [:session/analysis :posture :head-forward-cm])
     ;; => 4.2"
  [session]
  (let [timeline (:session/timeline session)

        ;; Extract landmarks
        landmarks (extract-relevant-landmarks timeline)

        ;; Compute metrics
        fhp (measure-forward-head (:nose landmarks)
                                  landmarks)
        shoulder-imbalance (measure-shoulder-imbalance
                            (:left-shoulder landmarks)
                            (:right-shoulder landmarks))
        spine (assess-spine-alignment landmarks landmarks landmarks)
        overall-score (compute-overall-score fhp shoulder-imbalance spine)

        ;; Generate insights
        insights (generate-insights fhp shoulder-imbalance spine overall-score)]

    ;; Return session with posture analysis
    (assoc-in session
              [:session/analysis :posture]
              {:head-forward-cm fhp
               :shoulder-imbalance-deg shoulder-imbalance
               :spine-alignment spine
               :overall-score overall-score
               :insights insights
               :method :geometric-2d
               :confidence 0.85
               :source-frames (mapv :frame/index timeline)})))
```

---

## 🎨 MULTI-ANALYZER UI DESIGN

### UI Architecture

**Goal**: Display multiple analyses without cluttering the interface.

**Pattern**: Tabbed interface with shared timeline.

```
┌─────────────────────────────────────────────────┐
│ Session: 2025-11-18 Breathing Practice         │
│ Duration: 60s | Analyzed: Breathing, Posture   │
├─────────────────────────────────────────────────┤
│                                                 │
│  [Breathing]  [Posture]  (Gait)  (Balance)     │ ← Tabs
│  ────────────                                   │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │ Breathing Rate: 21.8 bpm                │   │
│  │ Depth Score: 0.75                       │   │
│  │ Fatigue Windows: 2 detected             │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │ 📊 Breathing Rate Timeline              │   │
│  │     [Graph showing rate over time]      │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  💡 Insights:                                   │
│  ┌─────────────────────────────────────────┐   │
│  │ Breathing window shortened              │   │
│  │ Practice nasal breathing...             │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
└─────────────────────────────────────────────────┘

(Click [Posture] tab →)

┌─────────────────────────────────────────────────┐
│ Session: 2025-11-18 Breathing Practice         │
│ Duration: 60s | Analyzed: Breathing, Posture   │
├─────────────────────────────────────────────────┤
│                                                 │
│  [Breathing]  [Posture]  (Gait)  (Balance)     │
│               ──────────                        │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │ Forward Head: 4.2 cm                    │   │
│  │ Shoulder Imbalance: 3.5°                │   │
│  │ Spine Alignment: Neutral                │   │
│  │ Overall Score: 84/100                   │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │ 🧍 Posture Visualization                │   │
│  │     [Skeleton overlay with highlights]  │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  💡 Insights:                                   │
│  ┌─────────────────────────────────────────┐   │
│  │ Forward head posture detected           │   │
│  │ Practice chin tucks...                  │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
└─────────────────────────────────────────────────┘
```

### Component Hierarchy

```clojure
[analysis-view session]
  ├─ [analysis-tabs current-tab]
  │    ├─ [tab-button :breathing]
  │    ├─ [tab-button :posture]
  │    └─ [tab-button :gait] (disabled, future)
  │
  ├─ (case current-tab
  │    :breathing [breathing-panel analysis]
  │    :posture [posture-panel analysis])
  │
  └─ [timeline-scrubber session] (shared across all tabs)
```

### State Management

```clojure
;; App state structure
{:ui {:current-view :analysis
      :analysis-tab :breathing} ;; or :posture

 :sessions
 {#uuid "..."
  {:session/id #uuid "..."
   :session/analysis
   {:breathing {...}    ;; From LOD 2
    :posture {...}}}}}  ;; From LOD 4
```

---

## 🧪 TESTING STRATEGY

### Unit Tests (Pure Functions)

```clojure
(ns combatsys.posture-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [combatsys.posture :as posture]))

(deftest test-forward-head-measurement
  (testing "Forward head distance calculation"
    (let [nose {:x 0.55 :y 0.3}
          shoulders {:left {:x 0.42 :y 0.42}
                     :right {:x 0.58 :y 0.42}}
          fhp (posture/measure-forward-head nose shoulders)]

      ;; Should be positive (head forward)
      (is (> fhp 0))

      ;; Should be reasonable (0-15 cm range)
      (is (< fhp 15)))))

(deftest test-shoulder-imbalance
  (testing "Level shoulders return 0°"
    (let [left {:x 0.4 :y 0.5}
          right {:x 0.6 :y 0.5}
          imbalance (posture/measure-shoulder-imbalance left right)]
      (is (< (abs imbalance) 1)))) ;; Nearly 0°

  (testing "Right shoulder higher returns positive angle"
    (let [left {:x 0.4 :y 0.55}
          right {:x 0.6 :y 0.45} ;; Higher (lower y value)
          imbalance (posture/measure-shoulder-imbalance left right)]
      (is (> imbalance 0)))))

(deftest test-spine-classification
  (testing "Straight alignment classified as neutral"
    (let [shoulders {:left {:x 0.4 :y 0.4} :right {:x 0.6 :y 0.4}}
          hips {:left {:x 0.42 :y 0.6} :right {:x 0.58 :y 0.6}}
          knees {:left {:x 0.43 :y 0.8} :right {:x 0.57 :y 0.8}}
          spine (posture/assess-spine-alignment shoulders hips knees)]
      (is (= :neutral spine)))))
```

### Integration Tests

```clojure
(deftest test-posture-analyzer-integration
  (testing "Posture analysis adds to session without breaking breathing"
    (let [session (mocks/mock-breathing-session 22 60)

          ;; Analyze breathing first
          with-breathing (breathing/analyze session)

          ;; Then analyze posture
          with-both (posture/analyze with-breathing)]

      ;; Both analyses present
      (is (some? (get-in with-both [:session/analysis :breathing])))
      (is (some? (get-in with-both [:session/analysis :posture])))

      ;; Breathing analysis unchanged
      (is (= (get-in with-breathing [:session/analysis :breathing])
             (get-in with-both [:session/analysis :breathing])))

      ;; Posture analysis has required keys
      (let [posture-result (get-in with-both [:session/analysis :posture])]
        (is (number? (:head-forward-cm posture-result)))
        (is (number? (:shoulder-imbalance-deg posture-result)))
        (is (keyword? (:spine-alignment posture-result)))
        (is (number? (:overall-score posture-result)))
        (is (vector? (:insights posture-result)))))))
```

### Manual Test Procedures

**Test 1: Good Posture Baseline**
```
1. User sits with excellent posture:
   - Head aligned over shoulders
   - Shoulders level
   - Back straight
2. Record 30s session
3. Run posture analysis
4. Verify:
   ✓ FHP < 3 cm
   ✓ Shoulder imbalance < 3°
   ✓ Spine: :neutral
   ✓ Overall score > 0.9
```

**Test 2: Forward Head Detection**
```
1. User intentionally leans head forward (text neck)
2. Record 30s session
3. Run posture analysis
4. Verify:
   ✓ FHP > 5 cm
   ✓ Insight generated about FHP
   ✓ Recommendation includes "chin tucks"
```

**Test 3: Multi-Analyzer Workflow**
```
1. User records breathing session
2. Click "Analyze All"
3. Verify:
   ✓ Both breathing and posture tabs appear
   ✓ Breathing tab shows rate, depth, fatigue
   ✓ Posture tab shows FHP, shoulder, spine
   ✓ Can switch between tabs without lag
   ✓ Insights are separated by analyzer
```

---

## 📋 DEFINITION OF DONE

### Task 5.1: Posture Analyzer Implementation ✅

- [ ] Core functions implemented and pure:
  - [ ] `extract-relevant-landmarks`
  - [ ] `measure-forward-head`
  - [ ] `measure-shoulder-imbalance`
  - [ ] `assess-spine-alignment`
  - [ ] `compute-overall-score`
  - [ ] `generate-insights`
  - [ ] `analyze` (main entry point)

- [ ] All functions have docstrings with examples
- [ ] Unit tests pass (>90% coverage)
- [ ] Integration test passes (both analyzers coexist)
- [ ] Manual tests pass (good posture, FHP detection)
- [ ] Code compiles without warnings
- [ ] Conforms to schema (validated with spec)

### Task 5.2: Multi-Analyzer UI ✅

- [ ] Tabbed interface implemented
  - [ ] Tab buttons for each analyzer
  - [ ] Active tab highlighted
  - [ ] Tab content changes on click

- [ ] Breathing panel displays:
  - [ ] Rate, depth, fatigue windows
  - [ ] Timeline graph
  - [ ] Insights cards

- [ ] Posture panel displays:
  - [ ] FHP, shoulder imbalance, spine, score
  - [ ] Posture visualization (skeleton with highlights)
  - [ ] Insights cards

- [ ] State management handles multiple analyses
  - [ ] Re-frame events for tab switching
  - [ ] Subscriptions for each analyzer's data
  - [ ] No coupling between analyzer UIs

- [ ] UI is responsive and intuitive
  - [ ] Tab switching <100ms
  - [ ] Insights clearly separated
  - [ ] Metrics easy to understand

### Integration ✅

- [ ] End-to-end test passes
  - [ ] Load session → Analyze all → View both tabs → Success

- [ ] Regression test passes
  - [ ] Breathing analysis still works after adding posture
  - [ ] No broken UI from previous LODs

- [ ] Code quality
  - [ ] No warnings
  - [ ] Clean separation of concerns
  - [ ] Documentation complete

---

## 🏁 NEXT STEPS AFTER LOD 4

### LOD 5: User Calibration (Days 14-16)
- Baseline pose capture (T-pose)
- Personalized thresholds
- Adaptive insights based on user profile

### LOD 6: Multi-Session Analytics (Days 17-18)
- Session comparison (trend analysis)
- Progress visualization
- Long-term insights

### LOD 7+: Additional Analyzers
- Gait analyzer (step detection, symmetry)
- Balance analyzer (COM, stability)
- Combat technique analyzer (stance, guard)

---

## 💡 PHILOSOPHICAL REMINDERS

### Rich Hickey: Composable Functions
> "Posture analyzer is a pure function. Breathing analyzer is a pure function. Composition is trivial: `(-> session breathing/analyze posture/analyze)`"

### John Carmack: Profile First
> "Posture analysis should be <5ms. If it's slow, profile. But test with real data first."

### Brett Victor: Make Insights Visible
> "Don't just show numbers. Show the user what 4.2cm forward head looks like. Visualize it on the skeleton."

### Paul Graham: Ship Incrementally
> "This LOD proves the architecture. If it works cleanly, we can add 10 more analyzers easily."

---

## 📞 GETTING HELP

If you get stuck:

1. **Check schema**: Does posture analysis conform to `::posture-analysis` spec?
2. **Test in REPL**: Verify each function works in isolation
3. **Read LOD 2**: Breathing analyzer is the reference implementation
4. **Simplify**: Start with hardcoded values, then add real computation
5. **Measure**: Profile if slow, but test correctness first

---

**Document Owner**: The 10X Team
**Last Updated**: 2025-11-18
**Status**: Ready for LOD 4 Implementation 🚀
