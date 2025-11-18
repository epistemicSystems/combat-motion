# LOD 2: BREATHING ANALYSIS - CONTEXT DOCUMENT
**Phase**: Days 5-7 of Development Roadmap
**Status**: Ready to Begin
**Prerequisites**: ✅ LOD 0 Complete, ✅ LOD 1 Complete
**Date Prepared**: 2025-11-18

---

## 🎯 MISSION STATEMENT

**Objective**: Transform CombatSys from a pose tracking tool into a **breathing analysis platform** by implementing real-time torso motion extraction, FFT-based breathing rate detection, and fatigue window identification.

**Success Criteria**: At the end of LOD 2, a user can:
1. Record a 60-second breathing session
2. See real breathing rate detected (within ±2 bpm of manual count)
3. View breathing waveform visualization
4. Receive actionable coaching insights about breathing patterns
5. Understand when breathing stopped or became shallow

---

## 📊 CURRENT PROJECT STATE

### What We Have (LOD 0 + LOD 1)

#### ✅ Working Infrastructure
```
Camera Capture (30fps) → getUserMedia API
  ↓
MediaPipe Pose (15fps) → 33 landmarks with confidence scores
  ↓
Joint Angles (8 angles) → Pure functions, <1ms compute
  ↓
Skeleton Visualization → Green overlay, real-time
  ↓
Session Recording → EDN format to disk
  ↓
Session Persistence → ~/.config/CombatSys/sessions/
```

#### ✅ Code Base Status
- **Production Code**: ~3,000 lines ClojureScript
- **Test Code**: ~350 lines
- **Documentation**: ~4,000 lines (9 comprehensive docs)
- **Architecture**: Functional core / Imperative shell ✅
- **Performance**: 29% headroom in frame budget ✅
- **Technical Debt**: 0 ✅

#### ✅ Key Files Implemented
```
src/shared/
  ├── schema.cljs          ✅ EDN schemas (spec-compliant)
  ├── mocks.cljs           ✅ Mock data generators
  ├── pose.cljs            ✅ Angle computation (pure functions)
  ├── breathing.cljs       ⚠️  STUB (needs implementation)
  └── posture.cljs         ⚠️  STUB (future)

src/renderer/
  ├── camera.cljs          ✅ Camera capture
  ├── mediapipe.cljs       ✅ Pose detection
  ├── canvas.cljs          ✅ Skeleton drawing
  ├── persistence.cljs     ✅ EDN serialization
  ├── files.cljs           ✅ File I/O
  ├── state.cljs           ✅ Re-frame state management
  └── views.cljs           ✅ UI components

src/main/
  └── core.cljs            ✅ Electron main process
```

#### ⚠️ What Needs Implementation (LOD 2)

**File**: `src/shared/breathing.cljs` (currently stub - 135 lines)

**Current State**: Returns hardcoded values
```clojure
(defn extract-torso-motion [timeline]
  ;; Stub: returns fake sine wave
  (mapv (fn [frame] {:torso-motion (Math/sin ...)}) timeline))

(defn detect-breathing-rate [signal]
  ;; Stub: always returns 22 bpm
  22)

(defn detect-fatigue-windows [signal]
  ;; Stub: returns fake windows
  [{:start-ms 30000 :end-ms 33000 :severity 0.8}])
```

**LOD 2 Goal**: Replace all stubs with real implementations.

---

## 🧠 TECHNICAL APPROACH

### The Breathing Analysis Pipeline

```clojure
;; Input: Session with timeline of poses
{:session/timeline
 [{:frame/index 0
   :frame/timestamp-ms 0
   :frame/pose {:landmarks [...] :confidence 0.95}}
  {:frame/index 1
   :frame/timestamp-ms 33
   :frame/pose {:landmarks [...] :confidence 0.94}}
  ...]}

;; Step 1: Extract torso motion (Task 3.1)
↓ (extract-torso-motion timeline)

;; Signal: Vector of motion magnitudes
[0.012 0.015 0.018 0.022 0.024 0.023 0.019 0.014 ...]
;; Length: (count timeline) frames
;; Units: Arbitrary (normalized pixel distance)

;; Step 2: Detect breathing rate (Task 3.2)
↓ (detect-breathing-rate signal)

;; Output: Breathing rate + metadata
{:rate-bpm 21.8
 :confidence 0.94
 :method :fft-peak-detection
 :frequency-hz 0.363
 :depth-score 0.78}

;; Step 3: Detect fatigue windows (Task 3.3)
↓ (detect-fatigue-windows signal threshold)

;; Output: Fatigue periods + insights
{:fatigue-windows
 [{:start-ms 45000 :end-ms 48000 :severity 0.85}
  {:start-ms 92000 :end-ms 95000 :severity 0.72}]
 :insights
 [{:title "Breathing window shortened"
   :description "Your aerobic capacity decreased during round 2"
   :recommendation "Practice nasal breathing during warm-up"}]}
```

### Algorithm Details

#### Task 3.1: Torso Motion Extraction

**Scientific Basis**: Torso expansion/contraction during breathing causes visible displacement of shoulder, chest, and abdomen landmarks.

**Implementation**:
```clojure
(defn extract-torso-motion [timeline]
  (let [;; Select torso landmarks (shoulders, hips)
        torso-ids [:left-shoulder :right-shoulder
                   :left-hip :right-hip]

        ;; Extract landmark positions per frame
        positions (mapv #(select-landmarks % torso-ids) timeline)

        ;; Compute centroid per frame
        centroids (mapv compute-centroid positions)

        ;; Compute frame-to-frame motion magnitude
        motions (frame-to-frame-distance centroids)

        ;; Smooth signal (reduce noise)
        smoothed (moving-average motions 5)]

    smoothed))
```

**Key Functions Needed**:
- `select-landmarks`: Extract specific landmarks from pose
- `compute-centroid`: Average position of landmarks
- `frame-to-frame-distance`: Euclidean distance between consecutive frames
- `moving-average`: Sliding window smoothing

#### Task 3.2: FFT & Breathing Rate

**Scientific Basis**: Breathing is periodic motion. FFT converts time-domain signal to frequency domain, revealing dominant periodicity (breathing rate).

**Implementation**:
```clojure
(defn detect-breathing-rate [signal]
  (let [;; Apply FFT (use JS library via interop)
        freq-domain (fft-transform signal)

        ;; Breathing range: 6-30 bpm = 0.1-0.5 Hz
        breathing-range [0.1 0.5]

        ;; Find peak frequency in range
        peak (find-peak-in-range freq-domain breathing-range)

        ;; Convert Hz to BPM
        bpm (* (:frequency peak) 60)]

    {:rate-bpm bpm
     :confidence (:magnitude peak)
     :frequency-hz (:frequency peak)
     :method :fft-peak-detection}))
```

**Key Functions Needed**:
- `fft-transform`: Wrapper for JavaScript FFT library (fft-js)
- `find-peak-in-range`: Identify maximum magnitude in frequency range
- Signal preprocessing: Windowing (Hann/Hamming) to reduce spectral leakage

#### Task 3.3: Fatigue Window Detection

**Scientific Basis**: Breath holding or shallow breathing indicates fatigue or high cognitive load.

**Implementation**:
```clojure
(defn detect-fatigue-windows [signal]
  (let [;; Compute dynamic threshold (30% of mean amplitude)
        threshold (* 0.3 (mean signal))

        ;; Find regions below threshold
        low-regions (find-below-threshold signal threshold)

        ;; Merge adjacent regions (within 2 seconds)
        merged (merge-close-windows low-regions 60) ;; 60 frames at 30fps = 2s

        ;; Compute severity for each window
        windows (mapv #(assoc % :severity (compute-severity % signal threshold))
                      merged)]

    windows))
```

**Key Functions Needed**:
- `find-below-threshold`: Identify contiguous regions where signal < threshold
- `merge-close-windows`: Combine windows separated by small gaps
- `compute-severity`: How much below threshold (0.0 = at threshold, 1.0 = complete stop)

---

## 📐 SCHEMAS & CONTRACTS

### Input Schema (Already Defined in schema.cljs)

```clojure
;; Timeline (vector of frames)
(s/def ::timeline
  (s/coll-of ::frame))

;; Frame
(s/def ::frame
  (s/keys :req-un [::frame-index ::timestamp-ms ::pose]
          :opt-un [::derived ::events]))

;; Pose
(s/def ::pose
  (s/keys :req-un [::landmarks ::confidence]
          :opt-un [::world-coords]))
```

### Output Schema (Already Defined in schema.cljs)

```clojure
;; Breathing Analysis
(s/def ::breathing-analysis
  (s/keys :req-un [::rate-bpm ::depth-score]
          :opt-un [::rhythm-regularity ::fatigue-windows
                   ::method ::confidence ::source-frames ::explanation]))

;; Fatigue Window
(s/def ::fatigue-window
  (s/keys :req-un [::start-ms ::end-ms ::severity]))

;; Rate (BPM)
(s/def ::rate-bpm pos?)

;; Depth Score (0.0-1.0)
(s/def ::depth-score (s/double-in :min 0.0 :max 1.0))
```

### Function Signatures

```clojure
(s/fdef extract-torso-motion
  :args (s/cat :timeline ::timeline)
  :ret (s/coll-of number?))

(s/fdef detect-breathing-rate
  :args (s/cat :signal (s/coll-of number?))
  :ret (s/keys :req-un [::rate-bpm ::confidence ::frequency-hz]))

(s/fdef detect-fatigue-windows
  :args (s/cat :signal (s/coll-of number?))
  :ret (s/coll-of ::fatigue-window))

(s/fdef analyze
  :args (s/cat :session ::session)
  :ret ::session) ;; Returns session with :session/analysis populated
```

---

## 🧪 TESTING STRATEGY

### Unit Tests (Pure Functions)

```clojure
(ns combatsys.breathing-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [combatsys.breathing :as breathing]
            [combatsys.mocks :as mocks]))

;; Test 1: Torso motion extraction returns correct length
(deftest test-torso-motion-length
  (let [timeline (mocks/mock-timeline 90) ;; 90 frames
        signal (breathing/extract-torso-motion timeline)]
    (is (= 90 (count signal)))
    (is (every? number? signal))))

;; Test 2: Breathing rate detection (synthetic signal)
(deftest test-breathing-rate-synthetic
  (let [;; Generate synthetic breathing signal at 20 bpm
        signal (breathing/generate-synthetic-breathing 20 60) ;; 60s
        result (breathing/detect-breathing-rate signal)
        rate (:rate-bpm result)]
    ;; Allow ±2 bpm error
    (is (< 18 rate 22))
    (is (> (:confidence result) 0.7))))

;; Test 3: Fatigue window detection
(deftest test-fatigue-windows
  (let [;; Signal with intentional drop
        signal (concat (repeat 50 0.5) ;; Normal breathing
                       (repeat 20 0.05) ;; Breath hold
                       (repeat 50 0.5)) ;; Resume
        windows (breathing/detect-fatigue-windows signal)]
    (is (= 1 (count windows)))
    (is (> (:severity (first windows)) 0.5))))
```

### Integration Tests (End-to-End)

```clojure
;; Test 4: Full analysis pipeline
(deftest test-full-analysis
  (let [;; Use mock session with known breathing pattern
        session (mocks/mock-breathing-session 60 22) ;; 60s, 22 bpm
        analyzed (breathing/analyze session)
        analysis (get-in analyzed [:session/analysis :breathing])]

    ;; Verify rate detected
    (is (< 20 (:rate-bpm analysis) 24))

    ;; Verify depth score present
    (is (number? (:depth-score analysis)))
    (is (<= 0.0 (:depth-score analysis) 1.0))

    ;; Verify insights generated
    (is (vector? (:insights analysis)))
    (is (pos? (count (:insights analysis))))))
```

### Manual Verification Tests

```
Test 5: Real Recording
1. User records 60-second breathing session
2. User breathes at known rate (count manually: ~15 breaths/min)
3. System analyzes session
4. Verify detected rate within ±2 bpm of manual count

Test 6: Breath Hold
1. User records session
2. User intentionally holds breath for 5-10 seconds
3. System analyzes session
4. Verify fatigue window detected at correct timestamp

Test 7: Varied Breathing
1. User records session with varied breathing:
   - 20s normal breathing (12-15 bpm)
   - 20s fast breathing (20-25 bpm)
   - 20s slow breathing (8-10 bpm)
2. System analyzes session
3. Verify rate changes detected (graph should show variation)
```

---

## 🎨 UI/UX DESIGN (Future)

**Note**: LOD 2 focuses on **algorithm implementation**. UI visualization comes in later stages. However, we design with UI in mind.

### Planned Visualizations (LOD 3+)

```
┌─────────────────────────────────────────────────────┐
│ Breathing Analysis                                   │
├─────────────────────────────────────────────────────┤
│                                                       │
│  Rate: 21.8 bpm  ⚪ Depth: 0.78  ⚪ Regularity: 0.88 │
│                                                       │
│  Waveform:                                           │
│  ┌───────────────────────────────────────────────┐  │
│  │    /\      /\      /\      /\      /\        │  │
│  │   /  \    /  \    /  \    /  \    /  \       │  │
│  │  /    \  /    \  /    \  /    \  /    \      │  │
│  │ /      \/      \/      \/      \/      \     │  │
│  └───────────────────────────────────────────────┘  │
│  0s                 30s                    60s       │
│                                                       │
│  Fatigue Windows:                                    │
│  ⚠️  45-48s: Breathing stopped (severity: 0.85)     │
│  ⚠️  92-95s: Shallow breathing (severity: 0.72)     │
│                                                       │
│  Insights:                                           │
│  💡 Breathing window shortened during round 2        │
│     → Practice nasal breathing during warm-up        │
│                                                       │
└─────────────────────────────────────────────────────┘
```

### Data Requirements for UI

```clojure
;; For waveform graph: time series of motion magnitudes
{:waveform-data
 [{:timestamp-ms 0 :magnitude 0.012}
  {:timestamp-ms 33 :magnitude 0.015}
  ...]}

;; For fatigue markers: time ranges
{:fatigue-markers
 [{:start-ms 45000 :end-ms 48000 :color "red"}
  {:start-ms 92000 :end-ms 95000 :color "orange"}]}

;; For insights: structured coaching data
{:insights
 [{:icon "warning"
   :title "Breathing window shortened"
   :description "..."
   :recommendation "..."
   :timestamp-ms 45000}]}
```

**Implementation Strategy**:
- LOD 2: Generate all data structures (pure functions)
- LOD 3: Add UI components to visualize data
- LOD 4: Add interactivity (hover, zoom, etc.)

---

## ⚡ PERFORMANCE TARGETS

### Time Budget (Offline Analysis)

**Context**: LOD 2 analysis is **offline** (after recording), not real-time. User can wait a few seconds for results.

| Operation | Target | Max Acceptable |
|-----------|--------|----------------|
| Torso motion extraction | <0.5s per minute of footage | 2s |
| FFT computation | <0.1s per window | 0.5s |
| Fatigue detection | <0.2s per minute | 1s |
| Insight generation | <0.1s | 0.5s |
| **Total (60s recording)** | **<1s** | **5s** |

**Rationale**: User tolerance for analysis is ~3-5s. Target <1s for instant feel.

### Memory Budget

| Data Structure | Size Estimate |
|----------------|---------------|
| Timeline (60s @ 15fps) | ~900 frames × 5KB/frame = 4.5 MB |
| Motion signal | ~900 numbers × 8 bytes = 7.2 KB |
| FFT frequency domain | ~450 complex numbers = 7.2 KB |
| Fatigue windows | <10 windows × 100 bytes = 1 KB |
| **Total** | **~4.5 MB** |

**Verdict**: Memory is not a constraint. Optimize for clarity, not memory.

### Algorithm Complexity

```clojure
;; extract-torso-motion: O(n) where n = frame count
;; - Single pass through timeline
;; - Moving average: O(n)
;; Total: O(n)

;; detect-breathing-rate: O(n log n) due to FFT
;; - FFT: O(n log n)
;; - Peak finding: O(n)
;; Total: O(n log n)

;; detect-fatigue-windows: O(n)
;; - Single pass to find regions
;; - Single pass to merge
;; Total: O(n)

;; Overall: O(n log n) dominated by FFT
;; For n=900 frames: ~9000 operations → <1ms on modern CPU
```

**Verdict**: Performance will not be an issue. Focus on correctness.

---

## 📚 DEPENDENCIES & LIBRARIES

### JavaScript FFT Library

**Recommendation**: `fft-js` (MIT license, 5KB, no dependencies)

```bash
npm install fft-js --save
```

**Alternative**: `dsp.js` (more features, but heavier)

**ClojureScript Interop**:
```clojure
(ns combatsys.fourier
  "FFT wrapper using fft-js via JavaScript interop"
  (:require ["fft-js" :as fft-js]))

(defn fft-transform
  "Apply FFT to real-valued signal.
   Returns frequency domain (complex numbers as pairs [real imag])"
  [signal]
  (let [;; fft-js expects plain JS array
        js-signal (clj->js signal)

        ;; Compute FFT
        phasors (fft-js/fft js-signal)

        ;; Convert back to ClojureScript
        freqs (js->clj phasors)]

    freqs))
```

### No Other External Dependencies Required

- **Geometry**: Already implemented in `pose.cljs`
- **Signal Processing**: Simple functions (moving average, thresholding)
- **Data Structures**: Native ClojureScript vectors/maps

---

## 🔄 INTEGRATION POINTS

### How LOD 2 Fits Into Existing System

#### 1. Re-frame Events (New)

```clojure
;; src/renderer/state.cljs

(rf/reg-event-fx
 :session/analyze-breathing
 (fn [{:keys [db]} [_ session-id]]
   (let [session (get-in db [:sessions session-id])
         ;; Call pure function (no side effects in event)
         analyzed (breathing/analyze session)]
     {:db (assoc-in db [:sessions session-id] analyzed)})))
```

#### 2. UI Trigger (New Button)

```clojure
;; src/renderer/views.cljs

(defn session-view []
  (let [session @(rf/subscribe [:current-session])]
    [:div.session-view
     [:h2 "Session " (:session/id session)]
     [:button {:on-click #(rf/dispatch [:session/analyze-breathing
                                         (:session/id session)])}
      "🔬 Analyze Breathing"]

     ;; Display analysis results (if present)
     (when-let [analysis (get-in session [:session/analysis :breathing])]
       [:div.analysis-results
        [:p "Rate: " (:rate-bpm analysis) " bpm"]
        [:p "Depth: " (:depth-score analysis)]
        [:p "Fatigue Windows: " (count (:fatigue-windows analysis))]])]))
```

#### 3. Session Schema (Already Compatible)

```clojure
;; Existing session structure
{:session/id #uuid "..."
 :session/timeline [...]
 :session/analysis nil} ;; ← LOD 0/1

;; After LOD 2 analysis
{:session/id #uuid "..."
 :session/timeline [...]
 :session/analysis
 {:breathing {:rate-bpm 21.8
              :depth-score 0.78
              :fatigue-windows [...]
              :insights [...]}}} ;; ← Populated by LOD 2
```

**Key Point**: No schema changes needed. Just populate `:session/analysis`.

---

## 🎓 LEARNING RESOURCES

### FFT & Signal Processing

1. **Understanding FFT**: https://betterexplained.com/articles/an-interactive-guide-to-the-fourier-transform/
2. **Breathing Rate Detection**: "Remote measurement of heart rate and blood oxygen saturation using ambient light" (MIT, 2013)
3. **Signal Smoothing**: Moving average vs. Gaussian filter comparison

### Relevant Papers

1. **Eulerian Video Magnification** (MIT, 2012) - Foundation for future LOD 3
2. **Motion Magnification for Video Diagnosis of Machinery** - Similar signal processing techniques

### Code Examples

```clojure
;; Example: Moving average (signal smoothing)
(defn moving-average [signal window-size]
  (let [half-window (quot window-size 2)]
    (mapv (fn [i]
            (let [start (max 0 (- i half-window))
                  end (min (count signal) (+ i half-window 1))
                  window (subvec signal start end)]
              (/ (reduce + window) (count window))))
          (range (count signal)))))

;; Example: Find peaks in signal
(defn find-peaks [signal]
  (keep-indexed
   (fn [i val]
     (when (and (> i 0)
                (< i (dec (count signal)))
                (> val (nth signal (dec i)))
                (> val (nth signal (inc i))))
       {:index i :value val}))
   signal))
```

---

## 🚨 RISKS & MITIGATION

### Risk 1: FFT Accuracy on Short Windows
**Problem**: 60s @ 15fps = 900 samples. FFT may have low frequency resolution.
**Mitigation**: Use zero-padding to increase resolution. Validate with synthetic signals.

### Risk 2: Noisy Pose Data
**Problem**: MediaPipe landmarks jitter, affecting motion signal quality.
**Mitigation**: Already smoothed in `pose.cljs`. Add additional smoothing in torso extraction if needed.

### Risk 3: Varying Camera Distance
**Problem**: User closer/farther from camera → different motion magnitudes.
**Mitigation**: Normalize by landmark scale (e.g., shoulder width). Make relative, not absolute.

### Risk 4: FFT Library Compatibility
**Problem**: `fft-js` may not work in ClojureScript build.
**Mitigation**: Test interop early. If issues, use alternative (`dsp.js`) or pure JS implementation.

---

## 📝 DEFINITION OF DONE

### Task 3.1: Torso Motion Extraction ✅

- [ ] `extract-torso-motion` function implemented
- [ ] Selects torso landmarks (shoulders, hips)
- [ ] Computes centroid per frame
- [ ] Computes frame-to-frame distance
- [ ] Applies moving average smoothing
- [ ] Returns vector of motion magnitudes
- [ ] Unit tests pass (signal length, all numbers)
- [ ] Docstring complete with example

### Task 3.2: FFT & Breathing Rate ✅

- [ ] `fft-transform` wrapper implemented (uses fft-js)
- [ ] `find-peak-in-range` function implemented
- [ ] `detect-breathing-rate` function implemented
- [ ] Returns `{:rate-bpm X :confidence Y :frequency-hz Z}`
- [ ] Tested with synthetic signals (known frequencies)
- [ ] Accuracy within ±2 bpm on test data
- [ ] Unit tests pass
- [ ] Docstring complete

### Task 3.3: Fatigue Window Detection ✅

- [ ] `find-below-threshold` function implemented
- [ ] `merge-close-windows` function implemented
- [ ] `compute-severity` function implemented
- [ ] `detect-fatigue-windows` returns vector of windows
- [ ] Each window has `:start-ms`, `:end-ms`, `:severity`
- [ ] `generate-insights` creates coaching language
- [ ] Unit tests pass (synthetic fatigue signal)
- [ ] Docstring complete

### Integration ✅

- [ ] All stub functions in `breathing.cljs` replaced
- [ ] `analyze` function works end-to-end
- [ ] Integration test passes (mock session → analyzed session)
- [ ] Re-frame event `:session/analyze-breathing` implemented
- [ ] UI button to trigger analysis
- [ ] Analysis results display in UI
- [ ] Manual test with real recording succeeds

---

## 🏁 NEXT STEPS AFTER LOD 2

### LOD 3: Eulerian Magnification (Days 8-10)
- WebGPU shader for video magnification
- Visualize breathing motion (amplified 20x)
- Side-by-side comparison (original vs. magnified)

### LOD 4: Posture Analyzer (Days 11-13)
- Forward head measurement
- Shoulder imbalance detection
- Spine alignment classification

### LOD 5: User Calibration (Days 14-16)
- Personalized baseline recording
- Adaptive thresholds

### LOD 6: Multi-Session Analytics (Days 17-18)
- Session comparison
- Trend analysis

---

## 💡 PHILOSOPHICAL REMINDERS

### Rich Hickey: Simplicity
> "What's the data? Timeline → signal → rate. Pure functions. No state."

### John Carmack: Performance
> "Offline analysis. User can wait 1-2 seconds. Don't prematurely optimize."

### Brett Victor: Observability
> "Every rate detection should show WHY. Frequency peak, confidence, waveform."

### Paul Graham: Shipping
> "Ship breathing rate detection first. Fatigue windows second. Insights last."

---

## 📞 GETTING HELP

If you get stuck:

1. **Check existing code**: `pose.cljs` has vector math examples
2. **Check specs**: `schema.cljs` has all data structures
3. **Check docs**: `SPEC.md` has function signatures
4. **Test in REPL**: All functions are pure, easy to test interactively
5. **Consult papers**: FFT basics are well-documented online

---

**Document Owner**: The 10X Team
**Last Updated**: 2025-11-18
**Status**: Ready for LOD 2 Implementation 🚀
