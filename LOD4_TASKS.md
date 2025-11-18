# LOD 4: POSTURE ANALYZER - TASK BREAKDOWN
**Sprint Duration**: Days 11-13 (Estimated: 20 hours total)
**Goal**: Implement posture assessment and prove multi-analyzer architecture
**Status**: Ready to Begin
**Date**: 2025-11-18

---

## 📋 OVERVIEW

### The Two Core Tasks

```
Task 5.1: Posture Analyzer Implementation    [12 hours] → Core analysis module
                ↓
Task 5.2: Multi-Analysis UI                  [8 hours] → Tabbed interface
```

**Total Estimated Time**: 20 hours
**Target Completion**: Day 13

**Success Metric**: User can analyze BOTH breathing AND posture in the same session, viewing results in separate tabs.

---

## 🎯 TASK 5.1: POSTURE ANALYZER IMPLEMENTATION

**File**: `src/shared/posture.cljs` (replace stub)
**Estimated Time**: 12 hours
**Priority**: 🔴 Critical
**Status**: Not Started

### Objective

Implement complete posture assessment module as a pure functional analyzer that:
1. Extracts relevant pose landmarks from session timeline
2. Computes geometric posture metrics (forward head, shoulder imbalance, spine alignment)
3. Generates overall posture score
4. Produces coaching insights in natural language
5. Integrates seamlessly with existing session pipeline
6. Operates independently (no coupling to breathing analyzer)

### Prerequisites

**Existing Code to Understand**:
```clojure
;; 1. Schema definitions (what we must conform to)
(require '[combatsys.schema :as schema])
(s/def ::posture-analysis ...)

;; 2. Breathing analyzer (reference implementation)
(require '[combatsys.breathing :as breathing])
;; Study how it processes timeline → analysis

;; 3. Pose utilities
(require '[combatsys.pose :as pose])
;; May have helper functions for landmark extraction
```

**Mathematical Dependencies**:
- Vector operations (dot product, cross product, magnitude)
- Angle calculations (atan2, angle between vectors)
- Statistical functions (mean, median for averaging)

---

### Implementation Breakdown

#### 5.1.1: Landmark Extraction & Averaging (2 hours)

**Goal**: Extract relevant landmarks and average over timeline to reduce noise.

**Why Average?** Posture is quasi-static (changes slowly). Averaging over 30-60 frames reduces jitter from MediaPipe detection.

```clojure
(ns combatsys.posture
  "Posture analysis from pose timeline.

   Pure functional module - analyzes body alignment from camera poses.

   Key Metrics:
   - Forward head posture (cm)
   - Shoulder imbalance (degrees)
   - Spine alignment (classification)
   - Overall posture score (0-1 scale)

   Algorithm:
   1. Extract landmarks (nose, shoulders, hips, knees)
   2. Average over timeline (reduce noise)
   3. Compute geometric metrics
   4. Generate insights

   Pure functions: session → session' (no side effects)"
  (:require [combatsys.schema :as schema]
            [clojure.spec.alpha :as s]))

;; ============================================================
;; HELPER FUNCTIONS
;; ============================================================

(defn get-landmark
  "Extract specific landmark from pose.

   Args:
     pose: Pose map with :landmarks vector
     landmark-id: Keyword (e.g., :nose, :left-shoulder)

   Returns:
     Landmark map with :x, :y, :z, :visibility or nil if not found

   Example:
     (get-landmark pose :nose)
     ;; => {:x 0.52 :y 0.31 :z -0.05 :visibility 0.98}"
  [pose landmark-id]
  (->> (:landmarks pose)
       (filter #(= landmark-id (:landmark-id %)))
       first))

(defn average-landmarks
  "Average landmark position over multiple frames.

   Args:
     frames: Vector of frame maps
     landmark-id: Keyword identifying landmark

   Returns:
     Averaged landmark position {:x :y :z :visibility}

   Example:
     (average-landmarks timeline :nose)
     ;; => {:x 0.521 :y 0.308 :z -0.048 :visibility 0.95}"
  [frames landmark-id]
  (let [landmarks (->> frames
                       (map :frame/pose)
                       (map #(get-landmark % landmark-id))
                       (filter some?) ;; Remove nil (missing landmarks)
                       (filter #(> (:visibility %) 0.5))) ;; Only high confidence

        n (count landmarks)]

    (when (pos? n)
      {:x (/ (reduce + (map :x landmarks)) n)
       :y (/ (reduce + (map :y landmarks)) n)
       :z (/ (reduce + (map :z landmarks)) n)
       :visibility (/ (reduce + (map :visibility landmarks)) n)})))

(defn midpoint
  "Compute midpoint between two landmarks.

   Args:
     lm1, lm2: Landmark maps with :x, :y, :z

   Returns:
     Midpoint {:x :y :z}

   Example:
     (midpoint {:x 0.4 :y 0.5 :z 0} {:x 0.6 :y 0.5 :z 0})
     ;; => {:x 0.5 :y 0.5 :z 0}"
  [lm1 lm2]
  {:x (/ (+ (:x lm1) (:x lm2)) 2)
   :y (/ (+ (:y lm1) (:y lm2)) 2)
   :z (/ (+ (:z lm1) (:z lm2)) 2)})

;; ============================================================
;; LANDMARK EXTRACTION
;; ============================================================

(defn extract-relevant-landmarks
  "Extract and average all landmarks needed for posture analysis.

   Args:
     timeline: Vector of frame maps

   Returns:
     Map of landmark-id → averaged position

   Example:
     (extract-relevant-landmarks timeline)
     ;; => {:nose {...}
     ;;     :left-shoulder {...}
     ;;     :right-shoulder {...}
     ;;     :left-hip {...}
     ;;     :right-hip {...}
     ;;     :left-knee {...}
     ;;     :right-knee {...}}"
  [timeline]
  {:nose (average-landmarks timeline :nose)
   :left-shoulder (average-landmarks timeline :left-shoulder)
   :right-shoulder (average-landmarks timeline :right-shoulder)
   :left-hip (average-landmarks timeline :left-hip)
   :right-hip (average-landmarks timeline :right-hip)
   :left-knee (average-landmarks timeline :left-knee)
   :right-knee (average-landmarks timeline :right-knee)})
```

**Testing**:
```clojure
(deftest test-landmark-extraction
  (let [timeline (mocks/mock-session)
        landmarks (posture/extract-relevant-landmarks timeline)]

    ;; All landmarks extracted
    (is (some? (:nose landmarks)))
    (is (some? (:left-shoulder landmarks)))
    (is (some? (:right-shoulder landmarks)))

    ;; Averaged positions are in valid range [0, 1]
    (is (< 0 (:x (:nose landmarks)) 1))
    (is (< 0 (:y (:nose landmarks)) 1))))
```

---

#### 5.1.2: Forward Head Posture Measurement (3 hours)

**Goal**: Measure horizontal distance from head to shoulder vertical line.

**Clinical Context**: Forward head posture (FHP) is extremely common in modern life (desk work, phones). Every inch of FHP adds ~10 lbs of load on neck muscles.

```clojure
;; ============================================================
;; FORWARD HEAD POSTURE (FHP)
;; ============================================================

(defn calibrate-pixel-to-cm
  "Compute pixel-to-cm conversion ratio.

   Uses known user height to calibrate camera distance.

   Args:
     user-height-cm: User's height in centimeters
     landmarks: Map of averaged landmarks

   Returns:
     Ratio (cm per pixel) as float

   Example:
     (calibrate-pixel-to-cm 175 landmarks)
     ;; => 0.185 (cm per pixel)"
  [user-height-cm landmarks]
  (let [;; Measure height in pixels (nose to ankle)
        nose-y (:y (:nose landmarks))
        left-ankle-y (:y (get landmarks :left-ankle {:y 1.0}))
        right-ankle-y (:y (get landmarks :right-ankle {:y 1.0}))
        ankle-y (/ (+ left-ankle-y right-ankle-y) 2)

        height-pixels (abs (- nose-y ankle-y))]

    (if (pos? height-pixels)
      (/ user-height-cm height-pixels)
      0.2))) ;; Default fallback (rough estimate)

(defn measure-forward-head
  "Measure forward head posture distance.

   Computes horizontal distance from nose to vertical line through shoulders.

   Args:
     landmarks: Map of averaged landmarks
     user-height-cm: User height for calibration (optional, default 170cm)

   Returns:
     Distance in centimeters (float)

   Algorithm:
   1. Find shoulder midpoint
   2. Measure horizontal distance from nose to shoulder midpoint
   3. Convert pixels to cm using calibration

   Normal Range: 0-3 cm
   Alert Threshold: >5 cm

   Example:
     (measure-forward-head landmarks 175)
     ;; => 4.2 (4.2cm forward)"
  ([landmarks] (measure-forward-head landmarks 170))
  ([landmarks user-height-cm]
   (let [nose (:nose landmarks)
         left-shoulder (:left-shoulder landmarks)
         right-shoulder (:right-shoulder landmarks)

         ;; Shoulder midpoint
         shoulder-center (midpoint left-shoulder right-shoulder)

         ;; Horizontal distance (x-axis, normalized [0,1])
         nose-x (:x nose)
         shoulder-x (:x shoulder-center)
         pixel-distance (abs (- nose-x shoulder-x))

         ;; Calibrate to cm
         cm-per-pixel (calibrate-pixel-to-cm user-height-cm landmarks)
         distance-cm (* pixel-distance cm-per-pixel)]

     distance-cm)))
```

**Testing**:
```clojure
(deftest test-forward-head-measurement
  (testing "Aligned head returns minimal FHP"
    (let [landmarks {:nose {:x 0.5 :y 0.3}
                     :left-shoulder {:x 0.42 :y 0.42}
                     :right-shoulder {:x 0.58 :y 0.42}}
          fhp (posture/measure-forward-head landmarks)]
      ;; Should be minimal (<2cm)
      (is (< fhp 2))))

  (testing "Forward head returns measurable FHP"
    (let [landmarks {:nose {:x 0.65 :y 0.3} ;; Head forward
                     :left-shoulder {:x 0.42 :y 0.42}
                     :right-shoulder {:x 0.58 :y 0.42}}
          fhp (posture/measure-forward-head landmarks)]
      ;; Should detect forward position
      (is (> fhp 3)))))
```

---

#### 5.1.3: Shoulder Imbalance Detection (2 hours)

**Goal**: Measure angle of shoulder line relative to horizontal.

**Clinical Context**: Shoulder imbalance indicates muscle weakness, scoliosis, or habitual asymmetry (e.g., carrying bag on one shoulder).

```clojure
;; ============================================================
;; SHOULDER IMBALANCE
;; ============================================================

(defn measure-shoulder-imbalance
  "Measure shoulder height imbalance.

   Computes angle of line connecting shoulders relative to horizontal.

   Args:
     landmarks: Map of averaged landmarks

   Returns:
     Angle in degrees (float)
     - 0° = perfectly level
     - Positive = right shoulder higher
     - Negative = left shoulder higher

   Normal Range: -3° to +3°
   Alert Threshold: >5° (absolute value)

   Example:
     (measure-shoulder-imbalance landmarks)
     ;; => 3.5 (right shoulder 3.5° higher)"
  [landmarks]
  (let [left-shoulder (:left-shoulder landmarks)
        right-shoulder (:right-shoulder landmarks)

        ;; Compute vector from left to right shoulder
        dx (- (:x right-shoulder) (:x left-shoulder))
        dy (- (:y right-shoulder) (:y left-shoulder))

        ;; Compute angle (atan2 returns radians)
        angle-rad (Math/atan2 dy dx)

        ;; Convert to degrees
        angle-deg (* angle-rad (/ 180 Math/PI))]

    ;; Normalize to [-90, 90] range
    (cond
      (> angle-deg 90) (- angle-deg 180)
      (< angle-deg -90) (+ angle-deg 180)
      :else angle-deg)))
```

**Testing**:
```clojure
(deftest test-shoulder-imbalance
  (testing "Level shoulders return near-zero angle"
    (let [landmarks {:left-shoulder {:x 0.4 :y 0.5}
                     :right-shoulder {:x 0.6 :y 0.5}}
          imbalance (posture/measure-shoulder-imbalance landmarks)]
      (is (< (abs imbalance) 1))))

  (testing "Right shoulder higher returns positive angle"
    (let [landmarks {:left-shoulder {:x 0.4 :y 0.55}
                     :right-shoulder {:x 0.6 :y 0.45}} ;; Higher (lower y)
          imbalance (posture/measure-shoulder-imbalance landmarks)]
      (is (> imbalance 0)))))
```

---

#### 5.1.4: Spine Alignment Assessment (3 hours)

**Goal**: Classify spine alignment based on shoulder-hip-knee alignment.

**Clinical Context**: Spine deviations (kyphosis, lordosis, scoliosis) affect balance, breathing, and long-term health.

```clojure
;; ============================================================
;; SPINE ALIGNMENT
;; ============================================================

(defn vector-subtract
  "Subtract two 3D vectors.

   Args:
     v1, v2: Maps with :x, :y, :z

   Returns:
     Difference vector {:x :y :z}

   Example:
     (vector-subtract {:x 1 :y 2 :z 0} {:x 0.5 :y 1 :z 0})
     ;; => {:x 0.5 :y 1 :z 0}"
  [v1 v2]
  {:x (- (:x v1) (:x v2))
   :y (- (:y v1) (:y v2))
   :z (- (:z v1) (:z v2))})

(defn angle-between-vectors
  "Compute angle between two 3D vectors.

   Args:
     v1, v2: Vectors as maps with :x, :y, :z

   Returns:
     Angle in degrees (float)

   Example:
     (angle-between-vectors {:x 1 :y 0 :z 0} {:x 0 :y 1 :z 0})
     ;; => 90.0"
  [v1 v2]
  (let [;; Dot product
        dot (+ (* (:x v1) (:x v2))
               (* (:y v1) (:y v2))
               (* (:z v1) (:z v2)))

        ;; Magnitudes
        mag1 (Math/sqrt (+ (* (:x v1) (:x v1))
                          (* (:y v1) (:y v1))
                          (* (:z v1) (:z v1))))
        mag2 (Math/sqrt (+ (* (:x v2) (:x v2))
                          (* (:y v2) (:y v2))
                          (* (:z v2) (:z v2))))

        ;; Angle (acos of normalized dot product)
        angle-rad (if (and (pos? mag1) (pos? mag2))
                    (Math/acos (/ dot (* mag1 mag2)))
                    0)]

    ;; Convert to degrees
    (* angle-rad (/ 180 Math/PI))))

(defn assess-spine-alignment
  "Classify spine alignment from body landmarks.

   Analyzes shoulder-hip-knee alignment to detect postural deviations.

   Args:
     landmarks: Map of averaged landmarks

   Returns:
     Keyword classification:
     - :neutral (straight, ideal)
     - :kyphotic (upper back rounded, hunched)
     - :lordotic (lower back curved, swayback)

   Algorithm:
   1. Compute vectors: shoulder→hip, hip→knee
   2. Measure angle between vectors
   3. Classify based on angle and direction

   Example:
     (assess-spine-alignment landmarks)
     ;; => :neutral"
  [landmarks]
  (let [;; Center points
        shoulder-center (midpoint (:left-shoulder landmarks)
                                 (:right-shoulder landmarks))
        hip-center (midpoint (:left-hip landmarks)
                            (:right-hip landmarks))
        knee-center (midpoint (:left-knee landmarks)
                             (:right-knee landmarks))

        ;; Vectors
        shoulder-to-hip (vector-subtract hip-center shoulder-center)
        hip-to-knee (vector-subtract knee-center hip-center)

        ;; Angle between upper and lower body
        angle (angle-between-vectors shoulder-to-hip hip-to-knee)

        ;; Check if leaning forward or backward (x-axis)
        forward-lean? (> (:x shoulder-to-hip) 0.05)]

    (cond
      ;; Nearly straight (small angle)
      (< angle 15) :neutral

      ;; Hunched forward (kyphotic)
      (and (> angle 15) forward-lean?) :kyphotic

      ;; Leaning backward (lordotic)
      (and (> angle 15) (not forward-lean?)) :lordotic

      ;; Default
      :else :neutral)))
```

**Testing**:
```clojure
(deftest test-spine-alignment
  (testing "Straight posture classified as neutral"
    (let [landmarks {:left-shoulder {:x 0.4 :y 0.4 :z 0}
                     :right-shoulder {:x 0.6 :y 0.4 :z 0}
                     :left-hip {:x 0.42 :y 0.6 :z 0}
                     :right-hip {:x 0.58 :y 0.6 :z 0}
                     :left-knee {:x 0.43 :y 0.8 :z 0}
                     :right-knee {:x 0.57 :y 0.8 :z 0}}
          spine (posture/assess-spine-alignment landmarks)]
      (is (= :neutral spine)))))
```

---

#### 5.1.5: Overall Score & Insights (2 hours)

**Goal**: Combine metrics into overall score and generate actionable insights.

```clojure
;; ============================================================
;; SCORING & INSIGHTS
;; ============================================================

(defn compute-overall-score
  "Compute overall posture quality score.

   Combines forward head, shoulder imbalance, and spine alignment.

   Args:
     fhp: Forward head distance (cm)
     shoulder-imbalance: Shoulder angle (degrees)
     spine: Spine classification keyword

   Returns:
     Score from 0.0 (poor) to 1.0 (excellent)

   Scoring Formula:
   - FHP component: 1.0 at 0cm, 0.0 at 10cm (linear)
   - Shoulder component: 1.0 at 0°, 0.0 at 15° (linear)
   - Spine component: 1.0 if :neutral, 0.7 if :kyphotic/:lordotic
   - Weighted average: 40% FHP, 30% shoulder, 30% spine

   Example:
     (compute-overall-score 4.2 3.5 :neutral)
     ;; => 0.84"
  [fhp shoulder-imbalance spine]
  (let [;; Component scores (0-1 scale)
        fhp-score (max 0.0 (min 1.0 (- 1.0 (/ fhp 10.0))))
        shoulder-score (max 0.0 (min 1.0 (- 1.0 (/ (abs shoulder-imbalance) 15.0))))
        spine-score (case spine
                      :neutral 1.0
                      :kyphotic 0.7
                      :lordotic 0.7
                      0.5)

        ;; Weighted average
        overall (* (+ (* 0.4 fhp-score)
                      (* 0.3 shoulder-score)
                      (* 0.3 spine-score))
                   1.0)]

    overall))

(defn generate-insights
  "Generate coaching insights from posture metrics.

   Converts numbers into actionable recommendations in natural language.

   Args:
     fhp: Forward head distance (cm)
     shoulder-imbalance: Shoulder angle (degrees)
     spine: Spine classification
     overall-score: Overall score (0-1)

   Returns:
     Vector of insight maps with keys:
     - :insight/title (string)
     - :insight/description (string)
     - :insight/severity (:low | :medium | :high)
     - :insight/recommendation (string)

   Example:
     (generate-insights 4.2 3.5 :neutral 0.84)
     ;; => [{:insight/title \"Forward head posture detected\" ...}]"
  [fhp shoulder-imbalance spine overall-score]
  (let [insights []]

    ;; Forward head insight
    (when (> fhp 5.0)
      (conj! (transient insights)
             {:insight/title "Forward head posture detected"
              :insight/description
              (str "Your head is " (format "%.1f" fhp)
                   " cm forward of your shoulders. This can lead to neck "
                   "strain and headaches.")
              :insight/severity (if (> fhp 8.0) :high :medium)
              :insight/recommendation
              "Practice chin tucks: Gently pull your chin back toward your neck, "
              "keeping eyes level. Hold for 5 seconds. Repeat 10 times, 3× daily. "
              "Also check your desk and screen height."}))

    ;; Shoulder imbalance insight
    (when (> (abs shoulder-imbalance) 5.0)
      (let [higher-side (if (> shoulder-imbalance 0) "right" "left")
            lower-side (if (> shoulder-imbalance 0) "left" "right")]
        (conj! (transient insights)
               {:insight/title "Shoulder imbalance detected"
                :insight/description
                (str "Your " higher-side " shoulder is "
                     (format "%.1f" (abs shoulder-imbalance))
                     "° higher than your " lower-side " shoulder.")
                :insight/severity :medium
                :insight/recommendation
                (str "Stretch your " higher-side " side regularly: "
                     "Side bend away from the higher shoulder, holding 20-30 seconds. "
                     "Strengthen the " lower-side " side with targeted exercises.")})))

    ;; Spine alignment insight
    (when (not= spine :neutral)
      (let [spine-name (name spine)
            description (case spine
                          :kyphotic "Your upper back shows excessive rounding (hunched posture)."
                          :lordotic "Your lower back shows excessive curvature (swayback)."
                          "Your spine shows deviation from neutral alignment.")]
        (conj! (transient insights)
               {:insight/title (str (clojure.string/capitalize spine-name) " posture detected")
                :insight/description description
                :insight/severity :medium
                :insight/recommendation
                (case spine
                  :kyphotic "Practice chest opening exercises: doorway stretches, "
                            "wall angels, and thoracic extensions. Strengthen mid-back muscles."
                  :lordotic "Strengthen your core: planks, dead bugs, and pelvic tilts. "
                            "Stretch hip flexors and hamstrings."
                  "Consult a physical therapist for personalized assessment.")})))

    ;; Overall encouragement or warning
    (cond
      (< overall-score 0.6)
      (conj! (transient insights)
             {:insight/title "Posture needs significant improvement"
              :insight/description
              (str "Overall posture score: " (format "%.0f" (* 100 overall-score)) "/100. "
                   "Multiple postural issues detected.")
              :insight/severity :high
              :insight/recommendation
              "Consider consulting a physical therapist or posture specialist for "
              "a comprehensive assessment. Regular awareness and targeted exercises "
              "can make a big difference."})

      (< overall-score 0.8)
      (conj! (transient insights)
             {:insight/title "Room for posture improvement"
              :insight/description
              (str "Overall posture score: " (format "%.0f" (* 100 overall-score)) "/100. "
                   "Some postural habits to address.")
              :insight/severity :low
              :insight/recommendation
              "Focus on the specific recommendations above. Set reminders to check "
              "your posture throughout the day."})

      :else
      (conj! (transient insights)
             {:insight/title "Good posture!"
              :insight/description
              (str "Overall posture score: " (format "%.0f" (* 100 overall-score)) "/100. "
                   "Your posture is well-aligned.")
              :insight/severity :low
              :insight/recommendation
              "Maintain your current awareness. Continue regular movement and stretching."}))

    (persistent! insights)))
```

---

#### 5.1.6: Main Analyzer Function (1 hour)

**Goal**: Tie everything together into main `analyze` function.

```clojure
;; ============================================================
;; MAIN ANALYZER
;; ============================================================

(defn analyze
  "Analyze posture in a session.

   Pure function: session → session'

   Extracts pose landmarks, computes posture metrics, generates insights.

   Args:
     session: Session map with :session/timeline

   Returns:
     Session map with :posture added to [:session/analysis :posture]

   Schema:
     Conforms to ::schema/posture-analysis

   Performance:
     <5ms for 60-frame timeline

   Example:
     (def session (load-session \"my-session\"))
     (def analyzed (posture/analyze session))
     (get-in analyzed [:session/analysis :posture :head-forward-cm])
     ;; => 4.2

     (get-in analyzed [:session/analysis :posture :insights])
     ;; => [{:insight/title \"Forward head posture detected\" ...}]"
  [session]
  (let [timeline (:session/timeline session)

        ;; Step 1: Extract and average landmarks
        landmarks (extract-relevant-landmarks timeline)

        ;; Step 2: Compute metrics
        fhp (measure-forward-head landmarks)
        shoulder-imbalance (measure-shoulder-imbalance landmarks)
        spine (assess-spine-alignment landmarks)
        overall-score (compute-overall-score fhp shoulder-imbalance spine)

        ;; Step 3: Generate insights
        insights (generate-insights fhp shoulder-imbalance spine overall-score)

        ;; Step 4: Build analysis result
        posture-analysis
        {:head-forward-cm fhp
         :shoulder-imbalance-deg shoulder-imbalance
         :spine-alignment spine
         :overall-score overall-score
         :insights insights

         ;; Metadata
         :method :geometric-2d
         :confidence 0.85
         :source-frames (mapv :frame/index timeline)}]

    ;; Return session with posture analysis added
    (assoc-in session [:session/analysis :posture] posture-analysis)))
```

**Testing**:
```clojure
(deftest test-posture-analyzer-complete
  (testing "Full posture analysis pipeline"
    (let [session (mocks/mock-session)
          analyzed (posture/analyze session)]

      ;; Analysis added to session
      (is (some? (get-in analyzed [:session/analysis :posture])))

      ;; All required keys present
      (let [posture (get-in analyzed [:session/analysis :posture])]
        (is (number? (:head-forward-cm posture)))
        (is (number? (:shoulder-imbalance-deg posture)))
        (is (keyword? (:spine-alignment posture)))
        (is (number? (:overall-score posture)))
        (is (vector? (:insights posture)))

        ;; Conforms to schema
        (is (s/valid? ::schema/posture-analysis posture))))))
```

---

### Acceptance Criteria (Task 5.1)

- [ ] All core functions implemented and pure
- [ ] `extract-relevant-landmarks` extracts and averages correctly
- [ ] `measure-forward-head` computes FHP in cm
- [ ] `measure-shoulder-imbalance` computes angle in degrees
- [ ] `assess-spine-alignment` classifies correctly
- [ ] `compute-overall-score` combines metrics (0-1 scale)
- [ ] `generate-insights` produces actionable recommendations
- [ ] `analyze` returns session with posture analysis
- [ ] All functions have comprehensive docstrings
- [ ] Unit tests pass (>90% coverage)
- [ ] Integration test passes (works alongside breathing)
- [ ] Conforms to schema (validated with spec)
- [ ] Performance <5ms for 60-frame timeline
- [ ] Code compiles without warnings

---

## 🎯 TASK 5.2: MULTI-ANALYSIS UI

**File**: `src/renderer/views.cljs` (modify), `src/renderer/state.cljs` (modify)
**Estimated Time**: 8 hours
**Priority**: 🟡 Important
**Status**: Blocked by Task 5.1

### Objective

Update UI to support multiple analyzers:
1. Implement tabbed interface for switching between analyses
2. Create posture panel with metrics and insights
3. Update state management to handle multiple analyses
4. Ensure breathing panel still works (regression test)
5. Make UI extensible (easy to add future analyzers)

---

### Implementation Breakdown

#### 5.2.1: Re-frame State Updates (1 hour)

**Goal**: Update app state structure and events to support multiple analyses.

```clojure
(ns combatsys.renderer.state
  (:require [re-frame.core :as rf]
            [combatsys.breathing :as breathing]
            [combatsys.posture :as posture]))

;; ============================================================
;; APP STATE STRUCTURE
;; ============================================================

;; Current state (LOD 3):
;; {:sessions {<uuid> {:session/analysis {:breathing {...}}}}}

;; New state (LOD 4):
;; {:sessions {<uuid> {:session/analysis {:breathing {...}
;;                                         :posture {...}}}}
;;  :ui {:analysis-tab :breathing}} ;; or :posture

;; ============================================================
;; EVENTS
;; ============================================================

(rf/reg-event-fx
 :analysis/run-all
 (fn [{:keys [db]} [_ session-id]]
   "Run all available analyzers on a session."
   (let [session (get-in db [:sessions session-id])

         ;; Run analyzers (pure functions)
         with-breathing (breathing/analyze session)
         with-both (posture/analyze with-breathing)]

     ;; Update session in db
     {:db (assoc-in db [:sessions session-id] with-both)})))

(rf/reg-event-db
 :ui/set-analysis-tab
 (fn [db [_ tab]]
   "Switch analysis tab (:breathing | :posture)."
   (assoc-in db [:ui :analysis-tab] tab)))

;; ============================================================
;; SUBSCRIPTIONS
;; ============================================================

(rf/reg-sub
 :analysis/current-tab
 (fn [db _]
   (get-in db [:ui :analysis-tab] :breathing))) ;; Default to breathing

(rf/reg-sub
 :analysis/breathing
 (fn [db [_ session-id]]
   (get-in db [:sessions session-id :session/analysis :breathing])))

(rf/reg-sub
 :analysis/posture
 (fn [db [_ session-id]]
   (get-in db [:sessions session-id :session/analysis :posture])))

(rf/reg-sub
 :analysis/available-tabs
 (fn [db [_ session-id]]
   "Return vector of available analysis tabs for session."
   (let [analysis (get-in db [:sessions session-id :session/analysis])]
     (cond-> []
       (some? (:breathing analysis)) (conj :breathing)
       (some? (:posture analysis)) (conj :posture)))))
```

---

#### 5.2.2: Tabbed Interface Component (2 hours)

**Goal**: Create tab buttons for switching between analyzers.

```clojure
(ns combatsys.renderer.views
  (:require [reagent.core :as r]
            [re-frame.core :as rf]))

;; ============================================================
;; TAB COMPONENT
;; ============================================================

(defn analysis-tab-button
  "Single tab button.

   Args:
     tab-id: Keyword (:breathing, :posture, etc.)
     label: Display string (\"Breathing\", \"Posture\", etc.)
     active?: Boolean - is this the active tab?
     on-click: Callback function"
  [tab-id label active? on-click]
  [:button.analysis-tab
   {:class (when active? "active")
    :on-click on-click}
   label])

(defn analysis-tabs
  "Tab bar for switching between analyses.

   Args:
     session-id: UUID of current session

   Subscribes to:
     :analysis/current-tab - Active tab
     :analysis/available-tabs - Which tabs to show"
  [session-id]
  (let [current-tab @(rf/subscribe [:analysis/current-tab])
        available-tabs @(rf/subscribe [:analysis/available-tabs session-id])]

    [:div.analysis-tabs
     ;; Breathing tab
     (when (some #(= % :breathing) available-tabs)
       [analysis-tab-button
        :breathing
        "Breathing"
        (= current-tab :breathing)
        #(rf/dispatch [:ui/set-analysis-tab :breathing])])

     ;; Posture tab
     (when (some #(= % :posture) available-tabs)
       [analysis-tab-button
        :posture
        "Posture"
        (= current-tab :posture)
        #(rf/dispatch [:ui/set-analysis-tab :posture])])

     ;; Future tabs (disabled placeholders)
     [:button.analysis-tab.disabled
      {:disabled true}
      "Gait (Coming Soon)"]
     [:button.analysis-tab.disabled
      {:disabled true}
      "Balance (Coming Soon)"]]))
```

**CSS** (for styling):
```css
.analysis-tabs {
  display: flex;
  gap: 8px;
  border-bottom: 2px solid #333;
  margin-bottom: 20px;
}

.analysis-tab {
  padding: 10px 20px;
  background: #222;
  color: #aaa;
  border: none;
  border-bottom: 3px solid transparent;
  cursor: pointer;
  font-size: 16px;
  transition: all 0.2s;
}

.analysis-tab:hover:not(.disabled) {
  color: #fff;
  background: #2a2a2a;
}

.analysis-tab.active {
  color: #0f0;
  border-bottom-color: #0f0;
}

.analysis-tab.disabled {
  color: #555;
  cursor: not-allowed;
}
```

---

#### 5.2.3: Posture Panel Component (3 hours)

**Goal**: Display posture metrics, visualization, and insights.

```clojure
;; ============================================================
;; POSTURE PANEL
;; ============================================================

(defn posture-metrics-card
  "Display key posture metrics.

   Args:
     posture: Posture analysis map"
  [posture]
  [:div.metrics-card
   [:h3 "Posture Metrics"]
   [:div.metrics-grid
    ;; Forward head
    [:div.metric
     [:label "Forward Head"]
     [:div.value (str (format "%.1f" (:head-forward-cm posture)) " cm")]
     [:div.status (if (< (:head-forward-cm posture) 5)
                    [:span.good "✓ Good"]
                    [:span.warning "⚠ Attention"])]]

    ;; Shoulder imbalance
    [:div.metric
     [:label "Shoulder Imbalance"]
     [:div.value (str (format "%.1f" (abs (:shoulder-imbalance-deg posture))) "°")]
     [:div.status (if (< (abs (:shoulder-imbalance-deg posture)) 5)
                    [:span.good "✓ Level"]
                    [:span.warning "⚠ Imbalance"])]]

    ;; Spine alignment
    [:div.metric
     [:label "Spine Alignment"]
     [:div.value (clojure.string/capitalize (name (:spine-alignment posture)))]
     [:div.status (if (= :neutral (:spine-alignment posture))
                    [:span.good "✓ Neutral"]
                    [:span.warning "⚠ Deviation"])]]

    ;; Overall score
    [:div.metric.overall
     [:label "Overall Score"]
     [:div.value (str (format "%.0f" (* 100 (:overall-score posture))) "/100")]
     [:div.status
      (cond
        (>= (:overall-score posture) 0.8) [:span.good "✓ Good"]
        (>= (:overall-score posture) 0.6) [:span.warning "⚠ Fair"]
        :else [:span.error "✗ Poor"])]]]])

(defn posture-visualization
  "Skeleton visualization with posture highlights.

   Args:
     session: Full session with timeline and analysis"
  [session]
  (let [posture (get-in session [:session/analysis :posture])
        timeline (:session/timeline session)
        first-frame (first timeline)]

    [:div.posture-viz
     [:h3 "Posture Visualization"]
     ;; TODO: Reuse skeleton canvas component
     ;; Highlight nose, shoulders, spine based on metrics
     [:canvas#posture-skeleton
      {:width 400
       :height 600}]
     [:div.viz-legend
      [:span.fhp "Red: Forward head"]
      [:span.shoulder "Yellow: Shoulder imbalance"]
      [:span.spine "Blue: Spine alignment"]]]))

(defn posture-insights
  "Display posture insights as cards.

   Args:
     insights: Vector of insight maps"
  [insights]
  [:div.insights-section
   [:h3 "💡 Posture Insights"]
   (for [insight insights]
     ^{:key (:insight/title insight)}
     [:div.insight-card
      {:class (name (:insight/severity insight))}
      [:div.insight-header
       [:h4 (:insight/title insight)]
       [:span.severity (name (:insight/severity insight))]]
      [:p.description (:insight/description insight)]
      [:div.recommendation
       [:strong "Recommendation: "]
       [:span (:insight/recommendation insight)]]])])

(defn posture-panel
  "Complete posture analysis panel.

   Args:
     session-id: UUID of session"
  [session-id]
  (let [session @(rf/subscribe [:session/by-id session-id])
        posture @(rf/subscribe [:analysis/posture session-id])]

    (if posture
      [:div.posture-panel
       [posture-metrics-card posture]
       [posture-visualization session]
       [posture-insights (:insights posture)]]

      ;; Not analyzed yet
      [:div.analysis-empty
       [:p "Posture analysis not run yet."]
       [:button
        {:on-click #(rf/dispatch [:analysis/run-all session-id])}
        "Analyze Posture"]])))
```

---

#### 5.2.4: Updated Analysis View (2 hours)

**Goal**: Integrate tabs and panels into main analysis view.

```clojure
;; ============================================================
;; ANALYSIS VIEW (Updated)
;; ============================================================

(defn analysis-view
  "Main analysis view with tabbed interface.

   Displays different analysis panels based on selected tab."
  []
  (let [session-id @(rf/subscribe [:ui/current-session-id])
        current-tab @(rf/subscribe [:analysis/current-tab])]

    [:div.analysis-view
     ;; Header
     [:div.analysis-header
      [:h2 "Session Analysis"]
      [:button.analyze-all
       {:on-click #(rf/dispatch [:analysis/run-all session-id])}
       "Analyze All"]]

     ;; Tabs
     [analysis-tabs session-id]

     ;; Tab content
     [:div.tab-content
      (case current-tab
        :breathing [breathing-panel session-id] ;; Existing from LOD 2
        :posture [posture-panel session-id]     ;; New in LOD 4
        [:div "Unknown tab"])]

     ;; Shared timeline scrubber (below tabs)
     [timeline-scrubber session-id]]))
```

---

### Acceptance Criteria (Task 5.2)

- [ ] Tabbed interface displays correctly
- [ ] Tab buttons switch active tab
- [ ] Active tab highlighted visually
- [ ] Breathing panel still works (regression)
- [ ] Posture panel displays all metrics
- [ ] Posture insights render as cards
- [ ] "Analyze All" button runs both analyzers
- [ ] State management handles multiple analyses
- [ ] Can switch tabs without data loss
- [ ] UI responsive (<100ms tab switch)
- [ ] Future tabs shown as disabled (Gait, Balance)
- [ ] CSS styling clean and professional
- [ ] No console errors

---

## ✅ COMPLETION CHECKLIST

### Task 5.1: Posture Analyzer
- [ ] Landmark extraction implemented
- [ ] Forward head measurement works
- [ ] Shoulder imbalance detection works
- [ ] Spine alignment classification works
- [ ] Overall score computed correctly
- [ ] Insights generated in natural language
- [ ] Main `analyze` function complete
- [ ] All helper functions pure and tested
- [ ] Unit tests pass
- [ ] Integration test passes
- [ ] Conforms to schema
- [ ] Performance <5ms
- [ ] Documentation complete

### Task 5.2: Multi-Analysis UI
- [ ] Tabbed interface implemented
- [ ] Tab switching works
- [ ] Breathing panel unchanged (regression)
- [ ] Posture panel displays metrics
- [ ] Posture insights render correctly
- [ ] State management updated
- [ ] "Analyze All" button works
- [ ] CSS styling complete
- [ ] No console errors
- [ ] Manual test passes

### End-to-End
- [ ] Record session → Analyze All → Both tabs work
- [ ] Breathing and posture coexist peacefully
- [ ] No coupling between analyzers
- [ ] Easy to add future analyzers (demonstrated by disabled tabs)
- [ ] User understands both analyses clearly

### Documentation
- [ ] All functions have docstrings
- [ ] Code has inline comments where needed
- [ ] Manual test procedures documented
- [ ] README updated with LOD 4 completion

---

## 🚀 READY TO SHIP

When all checkboxes complete, LOD 4 is done!

**Success Criteria**:
```
User can:
✅ Record a session
✅ Click "Analyze All"
✅ See breathing analysis in "Breathing" tab
✅ Switch to "Posture" tab
✅ See forward head, shoulder, spine metrics
✅ Read actionable insights for both analyses
✅ Understand their posture quality (score out of 100)
```

**The "Aha!" Moment**: "This isn't just a breathing tool—I can track ALL my movement quality!"

---

**Document Owner**: The 10X Team
**Last Updated**: 2025-11-18
**Status**: Ready for Implementation 🎯
