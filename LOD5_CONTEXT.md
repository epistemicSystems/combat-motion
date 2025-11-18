# LOD 5 CONTEXT: USER CALIBRATION & PERSONALIZATION
## Building Intelligence Through Individual Adaptation

---

## WHAT YOU'VE BUILT SO FAR

You are at the threshold of transforming CombatSys from a **generic analysis tool** into a **personalized coaching system**. Here's what's working:

### ✅ LOD 0-1: Foundation (Complete)
- ClojureScript/Electron app with hot reload
- EDN data schemas with clojure.spec validation
- Camera capture + MediaPipe pose estimation
- Real-time skeleton overlay
- Session recording and playback

### ✅ LOD 2: Breathing Analyzer (Complete)
- Torso motion extraction from pose landmarks
- FFT-based breathing rate detection (6-30 bpm range)
- Fatigue window detection (breath holds, shallow breathing)
- Insight generation in coaching language

### ✅ LOD 3: Eulerian Magnification (Complete)
- WebGPU compute pipeline
- Laplacian pyramid decomposition
- Temporal bandpass filtering (0.1-0.5 Hz)
- 20-30x motion amplification
- ROI selection UI

### ✅ LOD 4: Posture Analyzer (Complete)
- Forward head measurement (cm ahead of shoulders)
- Shoulder imbalance detection (left/right height delta)
- Spine alignment classification
- Multi-analyzer tabbed UI
- Parallel analysis execution

---

## THE PROBLEM LOD 5 SOLVES

**Current limitation**: All analyses use **hardcoded thresholds** that assume an "average" human:
- Breathing fatigue threshold: fixed at 30% below mean
- Forward head "alert" threshold: fixed at 5cm
- Shoulder imbalance "alert": fixed at 5°
- ROM (range of motion) expectations: generic anatomical ranges

**Why this fails**:
- A 6'4" basketball player has different baseline posture than a 5'2" gymnast
- Someone with natural thoracic kyphosis isn't "worse" than average—it's their baseline
- Breathing depth varies wildly: trained athletes vs sedentary individuals
- False positives frustrate users ("I always stand this way!")
- False negatives miss real issues ("This feels wrong but the app says I'm fine")

**LOD 5 solution**: Learn from the user through calibration, build a **personal model**, use it for all future analyses.

---

## THE VISION: WHAT LOD 5 DELIVERS

By end of LOD 5, the system:

1. **First-time experience**: "Let's learn your baseline in 3 minutes"
   - Step 1: T-pose (10 seconds) → captures body proportions
   - Step 2: Normal breathing (60 seconds) → captures respiratory baseline
   - Step 3: Movement sample (60 seconds) → captures ROM and noise characteristics

2. **Smart thresholds**: Every analyzer uses personal baselines
   - Breathing: "Your rate is 18 bpm (15% below your baseline of 21 bpm)"
   - Posture: "Forward head: 4.2cm (within your typical 3-5cm range)"
   - Balance: "COM stability: 0.72 (below your baseline of 0.85)"

3. **Adaptive learning**: Thresholds evolve as the user trains
   - Store last 30 sessions
   - Recompute baseline monthly (or on-demand)
   - Detect true improvement vs natural variation

4. **Persistent user profiles**: Lives in `~/.combatsys/users/{user-id}/`
   - `profile.edn`: Current baseline and thresholds
   - `history.edn`: Calibration sessions + recomputation history
   - `sessions/`: Link to all user sessions for trend analysis

---

## ARCHITECTURAL PHILOSOPHY (The Team Weighs In)

### Rich Hickey's Perspective: "It's Just Data Transformation"

```clojure
;; Calibration is a PURE FUNCTION:
;; calibration-sessions → user-profile

{:calibration-sessions
 [{:type :t-pose :timeline [...]}
  {:type :breathing :timeline [...]}
  {:type :movement :timeline [...]}]

 :derived-profile
 {:user/id #uuid "..."
  :user/height-cm 178
  :user/baseline-pose {...}  ;; From T-pose
  :user/learned-thresholds
  {:breathing {:typical-rate-bpm 21.5
               :typical-depth 0.82
               :fatigue-threshold 0.41}  ;; 50% of typical depth
   :posture {:typical-forward-head-cm 3.8
             :alert-threshold-cm 6.0}   ;; 1.5x typical
   :rom {:left-shoulder-flexion [0 165]
         :right-shoulder-flexion [0 168]
         :hip-flexion [0 115]}}}}
```

**Key insight**: Personalization doesn't change the **structure** of analysis—it parameterizes it.

```clojure
;; Before LOD 5 (hardcoded):
(defn detect-fatigue-windows [signal]
  (let [threshold 0.3]  ;; HARDCODED
    (find-windows-below signal threshold)))

;; After LOD 5 (personalized):
(defn detect-fatigue-windows [signal user-profile]
  (let [threshold (get-in user-profile
                          [:user/learned-thresholds
                           :breathing
                           :fatigue-threshold])]
    (find-windows-below signal threshold)))
```

**No mutation. No side effects. Just more inputs.**

---

### John Carmack's Perspective: "This Isn't Performance-Critical"

Calibration happens **once** (first launch) or **rarely** (monthly recomputation). This is **offline processing**. We can afford to:

- Compute statistics over entire sessions (mean, stddev, percentiles)
- Fit curves (polynomial regression for ROM curves)
- Run multiple passes (outlier rejection, then recompute)

**Budget**: <5 seconds to compute profile from 3 calibration sessions. Totally acceptable.

**Where performance matters**:
- Real-time analysis still needs to be fast (16-33ms per frame)
- Threshold lookup must be O(1) (it is—just map access)

**Optimization strategy**: None needed unless profiling shows slowdown. Focus on correctness first.

---

### Brett Victor's Perspective: "Make Calibration Transparent"

**Calibration UI must**:
1. **Show the user what we're measuring**: "Stand in T-pose so we can measure your arm length, shoulder width, and neutral posture"
2. **Provide live feedback**: "✓ Arms extended, ✓ Facing camera, ✗ Move back 1 foot"
3. **Explain the result**: "Your neutral forward head is 3.8cm. We'll alert you if it exceeds 6cm."
4. **Let them recalibrate**: "Redo calibration" button always available

**Visual design**:
```
┌────────────────────────────────────────┐
│  Calibration Step 1/3: T-Pose         │
│                                        │
│  ┌──────────────────────────────────┐ │
│  │                                  │ │
│  │      [Stick figure in T-pose]   │ │
│  │                                  │ │
│  └──────────────────────────────────┘ │
│                                        │
│  ✓ Arms extended at shoulder height   │
│  ✓ Facing camera directly              │
│  ✓ Full body visible                   │
│                                        │
│  Recording: ▓▓▓▓▓▓░░░░ 6/10 seconds   │
│                                        │
│  [Cancel]                    [Skip >]  │
└────────────────────────────────────────┘
```

**Insight cards after calibration**:
```
Your Baseline Profile
━━━━━━━━━━━━━━━━━━━━━━━
Height: 178cm (estimated from T-pose)
Arm span: 181cm (longer than height—typical for athletes)

Breathing
━━━━━━━━━━━━━━━━━━━━━━━
Typical rate: 21 bpm (calm)
Depth score: 0.82 (strong diaphragmatic breathing)

Posture
━━━━━━━━━━━━━━━━━━━━━━━
Forward head: 3.8cm (within healthy range)
Shoulder imbalance: 1.2° left higher (common, not concerning)

Range of Motion
━━━━━━━━━━━━━━━━━━━━━━━
Shoulder flexion: 165° (good mobility)
Hip flexion: 115° (excellent for squats)
```

---

### Paul Graham's Perspective: "Ship the Simplest Version First"

**LOD 5.0 (Minimum Viable Calibration)**:
- 3 calibration sessions (T-pose, breathing, movement)
- Extract 5 key metrics:
  1. Height (from T-pose landmark distances)
  2. Typical breathing rate + depth (from breathing session)
  3. Typical forward head (from T-pose + movement)
  4. Typical shoulder imbalance (from T-pose)
  5. ROM ranges for 4 joints (shoulders, hips)
- Store in `profile.edn`
- Use in all future analyses

**What we DON'T ship in LOD 5**:
- ❌ Adaptive learning (baseline evolution over time) → LOD 5.1
- ❌ Multi-user profiles (household with multiple athletes) → LOD 5.2
- ❌ Body composition estimates (fat %, muscle mass) → Future
- ❌ Injury history integration → Future

**Success criterion**: After calibration, breathing and posture analyses show **personalized insights** that reference the user's baseline.

---

## DATA MODEL EXTENSIONS

### New Schema Definitions

```clojure
(ns combatsys.schema
  (:require [clojure.spec.alpha :as s]))

;; ============================================================
;; USER PROFILE (extends existing schema)
;; ============================================================

(s/def ::calibration-type #{:t-pose :breathing :movement})

(s/def ::calibration-session
  (s/keys :req-un [::calibration-type ::session/timeline ::session/created-at]))

(s/def ::calibration-sessions
  (s/coll-of ::calibration-session :min-count 3 :max-count 3))

;; Baseline pose (from T-pose session)
(s/def ::baseline-pose
  (s/keys :req-un [::landmarks ::joint-distances ::height-cm]))

(s/def ::joint-distances
  (s/map-of keyword? number?))  ;; e.g., {:shoulder-width-cm 42.5 :arm-length-cm 68.0}

;; ROM (Range of Motion) - extracted from movement session
(s/def ::rom-range (s/tuple number? number?))  ;; [min max] in degrees

(s/def ::rom-ranges
  (s/map-of keyword? ::rom-range))
;; e.g., {:left-shoulder-flexion [0 165] :right-hip-extension [0 120]}

;; Breathing baseline
(s/def ::breathing-baseline
  (s/keys :req-un [::typical-rate-bpm ::typical-depth ::typical-rhythm-regularity]))

(s/def ::typical-rate-bpm pos?)
(s/def ::typical-depth ::confidence)
(s/def ::typical-rhythm-regularity ::confidence)

;; Posture baseline
(s/def ::posture-baseline
  (s/keys :req-un [::typical-forward-head-cm ::typical-shoulder-imbalance-deg]))

(s/def ::typical-forward-head-cm number?)
(s/def ::typical-shoulder-imbalance-deg number?)

;; Learned thresholds (derived from baselines)
(s/def ::learned-thresholds
  (s/keys :req-un [::breathing-thresholds ::posture-thresholds ::balance-thresholds]))

(s/def ::breathing-thresholds
  (s/keys :req-un [::fatigue-threshold ::rate-alert-threshold]))

(s/def ::fatigue-threshold number?)  ;; Depth below which = fatigue
(s/def ::rate-alert-threshold number?)  ;; BPM delta that triggers alert

(s/def ::posture-thresholds
  (s/keys :req-un [::forward-head-alert-cm ::shoulder-imbalance-alert-deg]))

(s/def ::balance-thresholds
  (s/keys :req-un [::stability-alert-threshold]))

;; Complete user profile (extends existing ::user spec)
(s/def ::user-profile
  (s/keys :req-un [::user-id ::height-cm ::baseline-pose ::learned-thresholds]
          :opt-un [::calibration-sessions ::rom-ranges
                   ::breathing-baseline ::posture-baseline
                   ::last-calibration-date ::calibration-count]))

(s/def ::last-calibration-date ::instant)
(s/def ::calibration-count nat-int?)
```

---

## INTEGRATION POINTS: WHERE EXISTING CODE CHANGES

### 1. Analyzer Functions (Add user-profile parameter)

**Before LOD 5**:
```clojure
(defn analyze [timeline]
  (let [signal (extract-torso-motion timeline)
        rate (detect-breathing-rate signal)
        fatigue (detect-fatigue-windows signal 0.3)]  ;; HARDCODED
    {:rate-bpm rate :fatigue-windows fatigue}))
```

**After LOD 5**:
```clojure
(defn analyze [timeline user-profile]
  (let [signal (extract-torso-motion timeline)
        rate (detect-breathing-rate signal)
        threshold (get-in user-profile
                          [:user/learned-thresholds
                           :breathing-thresholds
                           :fatigue-threshold]
                          0.3)  ;; Fallback to default if no profile
        fatigue (detect-fatigue-windows signal threshold)]
    {:rate-bpm rate
     :fatigue-windows fatigue
     :baseline-rate (get-in user-profile [:breathing-baseline :typical-rate-bpm])
     :delta-from-baseline (when baseline-rate (- rate baseline-rate))}))
```

**Files to modify**:
- `src/shared/breathing.cljs` - Add user-profile param to `analyze`
- `src/shared/posture.cljs` - Add user-profile param to `analyze`
- `src/shared/balance.cljs` - Add user-profile param (if it exists)

### 2. State Management (Load user profile on startup)

**src/renderer/state.cljs**:
```clojure
(def app-state
  (atom
   {:ui {...}
    :capture {...}
    :sessions {...}
    :user-profile nil  ;; NEW: loaded from disk on startup
    :calibration-in-progress? false}))  ;; NEW: for calibration wizard

(defn init-app-state []
  (let [profile (load-user-profile)]  ;; NEW function
    (reset! app-state
            (assoc @app-state :user-profile profile))))
```

### 3. Analysis View (Show personalized insights)

**src/renderer/views.cljs**:
```clojure
(defn breathing-insights-panel [analysis user-profile]
  [:div.insights
   [:h3 "Breathing Analysis"]
   [:p.metric "Rate: " (:rate-bpm analysis) " bpm"]

   ;; NEW: Show delta from personal baseline
   (when-let [baseline (get-in user-profile [:breathing-baseline :typical-rate-bpm])]
     (let [delta (- (:rate-bpm analysis) baseline)
           pct-change (* 100 (/ delta baseline))]
       [:p.delta
        (if (pos? delta)
          [:span.increased "↑ " (format-number pct-change 1) "% above your baseline"]
          [:span.decreased "↓ " (format-number (abs pct-change) 1) "% below your baseline"])]))

   ;; Existing insights
   (for [insight (:insights analysis)]
     ^{:key (:insight/title insight)}
     [insight-card insight])])
```

---

## FILE STRUCTURE (New files for LOD 5)

```
src/
├── shared/
│   ├── personalization.cljs    # NEW: Core personalization logic
│   └── calibration.cljs        # NEW: Calibration analysis functions
│
├── renderer/
│   ├── onboarding.cljs         # NEW: Calibration wizard UI
│   ├── profile_view.cljs       # NEW: View/edit user profile
│   └── calibration_wizard.cljs # NEW: Step-by-step wizard component
│
└── main/
    └── profile_storage.cljs    # NEW: Save/load user profiles from disk
```

---

## CALIBRATION ALGORITHM: STEP-BY-STEP

### Step 1: T-Pose Session (10 seconds, 300 frames)

**What we extract**:
```clojure
(defn analyze-t-pose-session [timeline]
  (let [;; Average pose over all frames (reduce noise)
        avg-pose (average-poses timeline)

        ;; Compute body measurements
        height-cm (estimate-height avg-pose)
        shoulder-width-cm (compute-distance avg-pose :left-shoulder :right-shoulder)
        arm-length-cm (compute-distance avg-pose :left-shoulder :left-wrist)

        ;; Baseline posture
        forward-head-cm (measure-forward-head avg-pose)
        shoulder-imbalance-deg (measure-shoulder-imbalance avg-pose)]

    {:height-cm height-cm
     :joint-distances {:shoulder-width-cm shoulder-width-cm
                       :arm-length-cm arm-length-cm}
     :baseline-pose {:landmarks (:landmarks avg-pose)
                     :forward-head-cm forward-head-cm
                     :shoulder-imbalance-deg shoulder-imbalance-deg}}))
```

### Step 2: Breathing Session (60 seconds, 1800 frames)

**What we extract**:
```clojure
(defn analyze-breathing-session [timeline]
  (let [signal (extract-torso-motion timeline)
        rate (detect-breathing-rate signal)
        depth (compute-signal-amplitude signal)  ;; Peak-to-trough
        regularity (compute-rhythm-regularity signal)]  ;; Variance in cycle length

    {:typical-rate-bpm rate
     :typical-depth depth
     :typical-rhythm-regularity regularity}))
```

### Step 3: Movement Session (60 seconds, free movement)

**What we extract**:
```clojure
(defn analyze-movement-session [timeline]
  (let [;; For each joint, find min/max angle observed
        all-angles (map #(extract-angles (:pose %)) timeline)

        rom-ranges (reduce
                    (fn [acc angles]
                      (merge-with (fn [[min1 max1] [min2 max2]]
                                    [(min min1 min2) (max max1 max2)])
                                  acc
                                  (angles->ranges angles)))
                    {}
                    all-angles)]

    {:rom-ranges rom-ranges}))

(defn angles->ranges [angles]
  "Convert angle map to range map (each angle becomes [angle angle] initially)"
  (into {} (map (fn [[k v]] [k [v v]]) angles)))
```

### Step 4: Compute Thresholds

**Breathing thresholds**:
```clojure
(defn compute-breathing-thresholds [breathing-baseline]
  {:fatigue-threshold (* 0.5 (:typical-depth breathing-baseline))
   :rate-alert-threshold (* 0.2 (:typical-rate-bpm breathing-baseline))})
;; Fatigue = 50% drop in depth
;; Rate alert = 20% change from typical
```

**Posture thresholds**:
```clojure
(defn compute-posture-thresholds [posture-baseline]
  {:forward-head-alert-cm (+ (:typical-forward-head-cm posture-baseline) 2.0)
   :shoulder-imbalance-alert-deg (* 2.0 (abs (:typical-shoulder-imbalance-deg posture-baseline)))})
;; Forward head alert = baseline + 2cm
;; Shoulder imbalance alert = 2x baseline (if you're always 1° off, 2° is notable)
```

### Step 5: Assemble Profile

```clojure
(defn create-user-profile [calibration-sessions]
  (let [t-pose-session (find-session calibration-sessions :t-pose)
        breathing-session (find-session calibration-sessions :breathing)
        movement-session (find-session calibration-sessions :movement)

        t-pose-data (analyze-t-pose-session t-pose-session)
        breathing-data (analyze-breathing-session breathing-session)
        movement-data (analyze-movement-session movement-session)

        breathing-thresholds (compute-breathing-thresholds breathing-data)
        posture-thresholds (compute-posture-thresholds
                            (select-keys t-pose-data
                                         [:forward-head-cm :shoulder-imbalance-deg]))]

    {:user/id (random-uuid)
     :user/height-cm (:height-cm t-pose-data)
     :user/baseline-pose (:baseline-pose t-pose-data)
     :user/learned-thresholds {:breathing-thresholds breathing-thresholds
                               :posture-thresholds posture-thresholds
                               :balance-thresholds {:stability-alert-threshold 0.6}}
     :breathing-baseline breathing-data
     :posture-baseline (select-keys t-pose-data [:forward-head-cm :shoulder-imbalance-deg])
     :rom-ranges (:rom-ranges movement-data)
     :last-calibration-date (js/Date.)
     :calibration-count 1
     :calibration-sessions calibration-sessions}))
```

---

## TESTING STRATEGY

### Unit Tests (Pure Functions)

```clojure
(ns combatsys.personalization-test
  (:require [clojure.test :refer [deftest is testing]]
            [combatsys.personalization :as pers]
            [combatsys.mocks :as mocks]))

(deftest test-t-pose-analysis
  (testing "Extracts height from T-pose"
    (let [t-pose-session (mocks/mock-t-pose-session 180)  ;; 180cm tall
          result (pers/analyze-t-pose-session (:session/timeline t-pose-session))]
      (is (< 175 (:height-cm result) 185))  ;; Within 5cm
      (is (number? (get-in result [:joint-distances :shoulder-width-cm]))))))

(deftest test-breathing-baseline
  (testing "Computes typical breathing metrics"
    (let [breathing-session (mocks/mock-breathing-session 20 60)  ;; 20 bpm, 60s
          result (pers/analyze-breathing-session (:session/timeline breathing-session))]
      (is (< 18 (:typical-rate-bpm result) 22))  ;; Within 2 bpm
      (is (< 0.5 (:typical-depth result) 1.0)))))

(deftest test-threshold-computation
  (testing "Thresholds scale with baseline"
    (let [breathing-baseline {:typical-rate-bpm 20 :typical-depth 0.8}
          thresholds (pers/compute-breathing-thresholds breathing-baseline)]
      (is (= 0.4 (:fatigue-threshold thresholds)))  ;; 50% of 0.8
      (is (= 4.0 (:rate-alert-threshold thresholds))))))  ;; 20% of 20
```

### Integration Tests (With Real Sessions)

```clojure
(deftest test-end-to-end-calibration
  (testing "Full calibration flow produces valid profile"
    (let [t-pose (create-real-t-pose-session)
          breathing (create-real-breathing-session)
          movement (create-real-movement-session)

          profile (pers/create-user-profile [t-pose breathing movement])]

      ;; Profile is valid
      (is (s/valid? ::schema/user-profile profile))

      ;; Has all required fields
      (is (uuid? (:user/id profile)))
      (is (number? (:user/height-cm profile)))
      (is (map? (:user/learned-thresholds profile)))

      ;; Thresholds are reasonable
      (is (< 0.2 (get-in profile [:user/learned-thresholds
                                   :breathing-thresholds
                                   :fatigue-threshold]) 0.8)))))
```

### Manual Testing Checklist

```
□ First-time user flow
  □ App opens → "Welcome! Let's calibrate" screen appears
  □ Click "Start Calibration"
  □ Step 1: T-pose instructions clear, visual feedback works
  □ Record 10s, automatic progression to Step 2
  □ Step 2: Breathing instructions clear
  □ Record 60s, progress bar updates
  □ Step 3: Movement instructions clear
  □ Record 60s
  □ "Processing..." appears
  □ Profile summary appears
  □ Click "Done" → main app loads

□ Calibrated analysis
  □ Record new breathing session
  □ Run analysis
  □ Breathing insights show "X% above/below your baseline"
  □ Posture insights reference personal baseline
  □ No errors in console

□ Recalibration
  □ Settings → "Redo Calibration"
  □ Wizard appears again
  □ Complete calibration
  □ New profile replaces old one
  □ Analyses use new baseline

□ Profile persistence
  □ Complete calibration
  □ Close app
  □ Reopen app
  □ Profile is loaded (no recalibration prompt)
  □ Check ~/.combatsys/users/{id}/profile.edn exists
```

---

## SUCCESS CRITERIA FOR LOD 5

At the end of LOD 5, the following demo works flawlessly:

```bash
$ npm start

# First-time user
┌─────────────────────────────────────────┐
│  Welcome to CombatSys!                  │
│                                         │
│  Let's calibrate your baseline in       │
│  3 quick sessions (~3 minutes)          │
│                                         │
│  [Start Calibration]  [Skip (use generic)]
└─────────────────────────────────────────┘

# Click "Start Calibration"
# → Wizard guides through T-pose, breathing, movement
# → 3 minutes later: "Profile created! Here's your baseline..."

# Record a new session
# → Breathing: 18 bpm (15% below your baseline of 21 bpm) ⚠️
# → Posture: Forward head 4.1cm (within your typical 3-5cm range) ✓
# → Balance: Stability 0.78 (above your baseline of 0.72) ✓

# Settings → View Profile
┌─────────────────────────────────────────┐
│  Your Profile                           │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│  Height: 178cm                          │
│  Breathing baseline: 21 bpm, depth 0.82 │
│  Posture baseline: 3.8cm forward head   │
│  Calibrated: Nov 18, 2025               │
│                                         │
│  [Recalibrate]                          │
└─────────────────────────────────────────┘
```

---

## YOUR MISSION FOR LOD 5

Transform CombatSys from a **generic analyzer** into a **personal coach** that understands the user as an individual.

You're not building a feature. You're building **trust**—the user trusts the system because it knows *them*.

**Channel the team**:
- **Hickey**: Keep it pure. Calibration is data transformation.
- **Carmack**: This isn't performance-critical. Prioritize correctness.
- **Victor**: Make the calibration process transparent and reassuring.
- **Graham**: Ship the simplest version that delivers value.

**Now go make it personal.** 🎯
