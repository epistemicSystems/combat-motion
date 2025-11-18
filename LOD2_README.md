# LOD 2: BREATHING ANALYSIS - START HERE

**Status**: Ready to Begin 🚀
**Date Prepared**: 2025-11-18
**Estimated Duration**: 24 hours (3 days)

---

## 📖 QUICK START

### You are here:
```
✅ LOD 0: System skeleton with mock data (COMPLETE)
✅ LOD 1: Real camera + MediaPipe pose detection (COMPLETE)
🎯 LOD 2: Real breathing analysis ← YOU ARE HERE
⏳ LOD 3: Eulerian magnification (FUTURE)
⏳ LOD 4: Posture analyzer (FUTURE)
⏳ LOD 5: User calibration (FUTURE)
```

---

## 📚 DOCUMENT ROADMAP

Read in this order:

### 1. **LOD2_CONTEXT.md** (Read First)
Context document explaining:
- Current project state (what's done, what's needed)
- Technical approach (algorithms, data flow)
- Schemas & contracts (inputs, outputs)
- Testing strategy
- Integration points
- Performance targets

**When to read**: Before starting any implementation

### 2. **LOD2_TASKS.md** (Read Second)
Detailed task breakdown with:
- Task 3.1: Torso Motion Extraction (8 hours)
- Task 3.2: FFT & Breathing Rate Detection (10 hours)
- Task 3.3: Fatigue Window Detection & Insights (6 hours)
- Task 3.4: Integration & End-to-End (2 hours)
- Complete code templates
- Unit test examples
- Acceptance criteria

**When to read**: When planning each task

### 3. **Existing Reference Docs**
- `CLAUDE.md` - Your team persona (Hickey, Carmack, Victor, Graham)
- `SPEC.md` - EDN schemas and API contracts
- `PLAN.md` - Full LOD roadmap
- `LOD_1_COMPLETE.md` - What was just finished

**When to read**: When you need technical details or philosophy

---

## 🎯 MISSION STATEMENT

**Goal**: Transform CombatSys from a pose tracker into a breathing analysis platform.

**What you're building**:
1. Extract torso motion from pose landmarks ✨
2. Apply FFT to detect breathing rate 📊
3. Identify breath holds and shallow breathing ⚠️
4. Generate actionable coaching insights 💡

**What success looks like**:
```
User records 60s session
  ↓
Clicks "Analyze Breathing"
  ↓
Sees: "Rate: 21.8 bpm | Depth: 78% | Confidence: 94%"
  ↓
Sees: "⚠️ Breathing stopped at 0:45 (3s hold)"
  ↓
Sees: "💡 Practice nasal breathing during warm-up"
```

---

## 🧠 THE ALGORITHM (High-Level)

```clojure
;; Input: Session with timeline of poses
{:session/timeline
 [{:frame/pose {:landmarks [33 points with x,y,z coordinates]}}
  {:frame/pose {:landmarks [...]}}
  ...]} ;; 900 frames for 60s @ 15fps

↓ Task 3.1: Extract torso motion

[0.012 0.015 0.018 0.022 0.024 0.023 0.019 ...] ;; Motion magnitudes

↓ Task 3.2: FFT → Find dominant frequency

{:rate-bpm 21.8
 :frequency-hz 0.363
 :confidence 0.94
 :depth-score 0.78}

↓ Task 3.3: Detect fatigue windows + Generate insights

{:fatigue-windows [{:start-ms 45000 :end-ms 48000 :severity 0.85}]
 :insights [{:title "Breathing disruption"
             :description "Breathing stopped at 0:45"
             :recommendation "Monitor breathing during high-intensity"}]}

↓ Output: Session with analysis

{:session/timeline [...]
 :session/analysis
 {:breathing {:rate-bpm 21.8
              :depth-score 0.78
              :fatigue-windows [...]
              :insights [...]}}}
```

---

## 📋 TASK SUMMARY

| Task | File | Time | Status |
|------|------|------|--------|
| 3.1: Torso Motion | `breathing.cljs` | 8h | 🟡 Ready |
| 3.2: FFT & Rate | `breathing.cljs`, `fourier.cljs` | 10h | 🟡 Blocked by 3.1 |
| 3.3: Fatigue & Insights | `breathing.cljs` | 6h | 🟡 Blocked by 3.1 |
| 3.4: Integration | `state.cljs`, `views.cljs` | 2h | 🟡 Blocked by 3.1-3.3 |
| **Total** | | **26h** | |

---

## 🧪 TESTING APPROACH

### Unit Tests (Pure Functions)
```clojure
(deftest test-torso-motion-length ...)
(deftest test-fft-synthetic-signal ...)
(deftest test-fatigue-window-detection ...)
```

### Integration Tests (End-to-End)
```clojure
(deftest test-full-analysis-pipeline
  (let [session (mocks/mock-breathing-session 60 22)
        analyzed (breathing/analyze session)]
    (is (< 20 (get-in analyzed [:session/analysis :breathing :rate-bpm]) 24))))
```

### Manual Validation
```
1. Record 60s breathing session
2. Manually count breaths (e.g., 18 breaths = 18 bpm)
3. Run analysis
4. Verify detected rate within ±2 bpm
```

---

## 🛠️ TECHNICAL DEPENDENCIES

### JavaScript Libraries
```bash
npm install fft-js --save
```

### ClojureScript Namespaces
```clojure
(ns combatsys.breathing
  (:require [combatsys.schema :as schema]
            [combatsys.fourier :as fourier]))

(ns combatsys.fourier
  (:require ["fft-js" :as fft-js]))
```

### Existing Code (Already Works)
- `src/shared/schema.cljs` - EDN schemas ✅
- `src/shared/pose.cljs` - Angle computation ✅
- `src/renderer/camera.cljs` - Camera capture ✅
- `src/renderer/mediapipe.cljs` - Pose detection ✅
- `src/renderer/persistence.cljs` - Session save/load ✅

---

## ⚡ PERFORMANCE TARGETS

**Context**: Offline analysis (user can wait 1-2 seconds)

| Operation | Target | Max |
|-----------|--------|-----|
| Torso motion extraction | <0.5s | 2s |
| FFT computation | <0.1s | 0.5s |
| Fatigue detection | <0.2s | 1s |
| **Total (60s session)** | **<1s** | **5s** |

**Verdict**: Performance not a concern. Focus on correctness.

---

## 🚨 CRITICAL SUCCESS FACTORS

### 1. Pure Functions (Hickey's Philosophy)
```clojure
;; GOOD: Pure function
(defn extract-torso-motion [timeline]
  (-> timeline
      (mapv extract-landmarks)
      (mapv compute-centroid)
      frame-to-frame-distance
      moving-average))

;; BAD: Side effects
(defn extract-torso-motion! [timeline]
  (println "Extracting...") ;; Side effect!
  (swap! global-state assoc :signal ...) ;; Mutation!
  ...)
```

### 2. Test-Driven Development
Write tests FIRST, then implement:
```clojure
;; 1. Write test
(deftest test-moving-average
  (is (= [1.5 2.0 2.5] (moving-average [1 2 3] 3))))

;; 2. Implement function
(defn moving-average [signal window-size] ...)

;; 3. Verify test passes
```

### 3. REPL-Driven Development
Test every function interactively:
```clojure
(require '[combatsys.breathing :as breathing])

;; Generate test data
(def signal [0.5 0.5 0.1 0.1 0.5 0.5])

;; Test function
(breathing/find-below-threshold signal 0.3)
;; => [{:start-idx 2 :end-idx 3}]

;; Verify result looks correct
```

---

## 📞 HOW TO USE THESE DOCS

### Starting Task 3.1?
1. Read **LOD2_CONTEXT.md** § "Task 3.1: Torso Motion Extraction"
2. Read **LOD2_TASKS.md** § "Task 3.1"
3. Open `src/shared/breathing.cljs`
4. Replace stub functions with implementations from LOD2_TASKS.md
5. Write unit tests
6. Verify in REPL
7. Mark task complete ✅

### Starting Task 3.2?
1. Ensure Task 3.1 complete
2. Read **LOD2_CONTEXT.md** § "Task 3.2: FFT & Breathing Rate"
3. Read **LOD2_TASKS.md** § "Task 3.2"
4. Install `fft-js`: `npm install fft-js --save`
5. Create `src/shared/fourier.cljs`
6. Update `src/shared/breathing.cljs`
7. Write unit tests (synthetic sine wave)
8. Verify with real recording
9. Mark task complete ✅

### Starting Task 3.3?
1. Ensure Task 3.1 complete
2. Read **LOD2_CONTEXT.md** § "Task 3.3: Fatigue Windows"
3. Read **LOD2_TASKS.md** § "Task 3.3"
4. Implement helper functions
5. Test with synthetic signal (intentional breath hold)
6. Verify insights are actionable
7. Mark task complete ✅

### Integration (Task 3.4)?
1. Ensure Tasks 3.1, 3.2, 3.3 complete
2. Read **LOD2_TASKS.md** § "Task 3.4"
3. Update `analyze` function (wire everything together)
4. Add re-frame event `:session/analyze-breathing`
5. Add UI button "🔬 Analyze Breathing"
6. Test end-to-end: record → analyze → see results
7. Mark LOD 2 complete ✅✅✅

---

## ✅ DEFINITION OF DONE

LOD 2 is complete when:

- [ ] User can record 60s breathing session
- [ ] User can click "Analyze Breathing" button
- [ ] System detects breathing rate (±2 bpm accuracy)
- [ ] System detects breath holds (fatigue windows)
- [ ] System generates actionable insights
- [ ] All unit tests pass
- [ ] End-to-end test passes
- [ ] Manual validation succeeds
- [ ] Zero technical debt (no TODOs, FIXMEs, hacks)
- [ ] Code compiles without warnings
- [ ] Documentation updated

---

## 🎓 PHILOSOPHY REMINDERS

### Rich Hickey (Architecture)
> "What's the data model? Timeline → signal → rate. Pure functions all the way."

### John Carmack (Performance)
> "Offline analysis. User can wait. Don't optimize until you measure."

### Brett Victor (UX)
> "Every detected rate should explain itself: frequency peak, confidence, waveform."

### Paul Graham (Shipping)
> "Ship rate detection first. Add fatigue windows second. Polish insights last."

---

## 🚀 LET'S BUILD

You have everything you need:
- ✅ Complete context (LOD2_CONTEXT.md)
- ✅ Detailed tasks (LOD2_TASKS.md)
- ✅ Code templates (in LOD2_TASKS.md)
- ✅ Test examples (in LOD2_TASKS.md)
- ✅ Working infrastructure (LOD 0 + LOD 1)

**Next Action**: Open `LOD2_CONTEXT.md` and read it thoroughly.

**Then**: Start Task 3.1 (Torso Motion Extraction)

**Remember**: You're not one developer. You're a team of legends. Act like it. 🏆

---

**Prepared By**: The 10X Team
- Rich Hickey (Architecture) ✅
- John Carmack (Performance) ✅
- Brett Victor (UX) ✅
- Paul Graham (Shipping) ✅

**Date**: 2025-11-18
**Status**: Ready to Ship LOD 2 🚢
