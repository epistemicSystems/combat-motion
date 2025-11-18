# PROJECT CONTEXT: COMBATSYS MOTION ANALYSIS
## Full Design Philosophy & Requirements

---

## DOCUMENT PURPOSE

This file consolidates the vision, requirements, and design philosophy from:
1. **North Star Document**: High-level vision and opportunity space
2. **PRD (Product Requirements)**: What we're building and why
3. **TDD (Technical Design)**: How we're building it
4. **Plan**: When and in what order

**Read this FIRST** before starting any Claude Code task.

---

## I. THE VISION (North Star)

### What We're Building
A **camera-only, personalized movement lab** for a single recurring athlete in a fixed space.

**Not**: A generic SaaS tool for gyms or clinics
**Yes**: A deeply personal training companion for one user in their home dojo

### Core Philosophy
- **Personalization through repetition**: Same body, same room, months of data → strong priors
- **Post-session insight**: Real-time is for feedback, offline is for truth
- **Observable everything**: Every metric explains itself, every decision has a path
- **Functional purity**: 90% pure functions, 10% imperative shell

### Target Domains
1. **Breathing & circular breathing**: Eulerian magnification + phase detection
2. **Gait analysis**: Step symmetry, alignment, cadence
3. **Posture assessment**: Spine, head, shoulder alignment  
4. **Combat sports technique**: Stance, guard, movement quality (BJJ, Muay Thai)
5. **Dance & lifting**: Timing, alignment, ROM, symmetry

---

## II. PRODUCT REQUIREMENTS (PRD Synthesis)

### The Real Problem
Combat sports coaches have **pattern intuition** but lack **pattern evidence**. They see a thousand details but can't quantify which matter.

### Our Solution
Compress post-session footage into **5-10 key insight moments** with quantified metrics that explain *why* they matter.

### Insight Hierarchy (What Matters Most)
1. **Mechanical efficiency** (80% of coaching value): Is the athlete using structure or just muscling?
2. **Breathing/fatigue correlation** (15%): When does oxygenation break? Form collapse?
3. **Pattern replay** (5%): Show this sequence 5 times side-by-side

### Key Constraints
- **v1**: Camera only (no wearables)
- **Environment**: Fixed, known space (can be calibrated once)
- **Subject**: Single user (the athlete), alone in frame
- **Compute**: Desktop app (not cloud-dependent)

### Pareto Principle
We optimize for **insight capture per engineering hour**, not completeness:
- Week 1: Can we see skeletons? (foundational)
- Week 2: Can we detect breathing? (first vertical)
- Week 3: Can we compare sessions? (longitudinal value)

---

## III. TECHNICAL ARCHITECTURE (TDD Synthesis)

### The Three Voices Guiding Design

#### Rich Hickey (Functional Purity)
- **Data-centric**: Everything is EDN, not objects
- **Pure functions**: Input data → output data, no side effects
- **Explicit time**: State is time-indexed values, not mutations
- **Simplicity**: "Simple is not easy. We choose simple."

#### John Carmack (Pragmatic Performance)
- **Critical path optimization**: Profile first, optimize the slowest 5%
- **GPU for heavy work**: WebGPU for Eulerian magnification
- **Working code wins**: Ship early, measure, then refine
- **Understand the machine**: Frame budgets, memory layout, cache

#### Brett Victor (Observable Systems)
- **Make invisible visible**: Every metric has a visualization
- **Immediate feedback**: <1 second action-to-result gap
- **Medium shapes thought**: UI isn't decoration, it's the idea
- **Explain decisions**: "Why 22 bpm?" → Show frequency peak

### Architectural Pattern: Functional Core / Imperative Shell

```
┌─────────────────────────────────────┐
│   IMPERATIVE SHELL (Thin)          │
│   • Camera I/O, GPU, file system    │
│   • Centralized state atom          │
│   • Main loop (game engine style)   │
├─────────────────────────────────────┤
│   FUNCTIONAL CORE (Thick)           │
│   • Pure functions over EDN         │
│   • Analyzers, metrics, insights    │
│   • 100% testable without I/O       │
└─────────────────────────────────────┘
```

**Key principle**: The shell is 10% of the codebase. Core is 90%.

### The EDN IR (Canonical Data Model)

Everything flows through an immutable EDN structure:

```clojure
{:session/id #uuid "..."
 :session/user-id #uuid "..."
 :session/camera {...}
 :session/timeline
 [{:frame/index 0
   :frame/pose {:landmarks [...]}
   :frame/derived {:angles {...}}
   :frame/events [{:type :breathing-pause}]}]
 :session/analysis
 {:breathing {:rate-bpm 22 :fatigue-windows [...]}
  :posture {:head-forward-cm 4.2}}}
```

**Why EDN?**
- Homoiconic (code and data are the same)
- Human-readable (debugging is trivial)
- Composable (functions transform session → session')
- Versioned (old sessions remain interpretable)

### State Management (Game Loop Style)

```clojure
(def app-state (atom {...}))

(defn main-loop []
  (js/requestAnimationFrame
   (fn [timestamp]
     ;; 1. Capture (imperative)
     (when-let [frame (capture-frame)]
       ;; 2. Update (pure)
       (swap! app-state append-frame frame))
     ;; 3. Render (imperative)
     (render-ui @app-state)
     (main-loop))))
```

**Single source of truth**: One atom. All state visible. REPL-inspectable.

### Module Boundaries (For Claude Code)

```
src/
├── main/           # Electron main (Node.js, imperative)
│   ├── camera.cljs       # Camera I/O
│   ├── mediapipe.cljs    # Pose estimation
│   └── files.cljs        # Session persistence
│
├── renderer/       # Electron renderer (Browser, mostly pure)
│   ├── core.cljs         # Entry point
│   ├── state.cljs        # State management
│   ├── views.cljs        # UI components (Reagent)
│   └── gpu.cljs          # WebGPU compute
│
└── shared/         # Pure ClojureScript (zero platform deps)
    ├── schema.cljs       # EDN specs
    ├── pose.cljs         # Pose processing
    ├── breathing.cljs    # Breathing analyzer
    ├── gait.cljs         # Gait analyzer
    ├── posture.cljs      # Posture analyzer
    └── insights.cljs     # Coaching language
```

**Rule**: `shared/` has ZERO dependencies on `main/` or `renderer/`. It's purely functional.

---

## IV. DEVELOPMENT PHILOSOPHY (Plan Synthesis)

### LOD-Based Development (Mipmapped Texture Strategy)

Like GPU textures, we build at increasing levels of detail:

```
LOD 0 (Day 1):   Mock data → Stubs → Basic UI → IT RUNS
LOD 1 (Days 2-4): Real camera → MediaPipe → IT RUNS
LOD 2 (Days 5-7): Real breathing → IT RUNS
LOD 3 (Days 8-10): Eulerian magnification → IT RUNS
LOD 4 (Days 11-13): Posture analyzer → IT RUNS
LOD 5 (Days 14-16): Personalization → IT RUNS
LOD 6 (Days 17-18): Multi-session analytics → IT RUNS
```

**Every stage is shippable.** No "under construction" placeholders.

### Paul Graham's Principle: Always Working
- At any moment, `npm start` → something runs
- Each LOD adds one vertical slice (not horizontal layers)
- Fast iteration: 2-second edit-compile-run cycle
- User-facing value: each milestone demonstrates capability

### Carmack's Pragmatism: Measure, Then Optimize
```clojure
;; Don't optimize prematurely
;; But when you do, go deep

(defn profile-fn [f label]
  (let [start (js/performance.now)
        result (f)
        end (js/performance.now)]
    (when (> (- end start) 16) ;; >1 frame at 60fps
      (js/console.warn label "took" (- end start) "ms"))
    result))
```

**Real-time path must hit 30fps.** Offline can take minutes.

---

## V. KEY TECHNICAL DECISIONS

### Why ClojureScript?
- **Functional purity**: Natural fit for our architecture
- **REPL-driven development**: Live coding, instant feedback
- **Data-centric**: EDN is first-class
- **JavaScript interop**: Can use any JS library (MediaPipe, tfjs, WebGPU)
- **Small, expressive**: Less code to maintain

### Why Electron?
- **Cross-platform**: macOS, Windows, Linux
- **Mature ecosystem**: Camera access, file I/O, GPU
- **No backend needed**: All processing local (privacy, speed)
- **Familiar**: Standard web tech (HTML, CSS, JS/CLJS)

### Why MediaPipe for Pose?
- **Fast**: 30fps on CPU, faster on GPU
- **Accurate**: State-of-art for 2D/3D pose
- **Well-supported**: Google maintains, active community
- **Multiple models**: Lite (fast) vs Full (accurate)

### Why WebGPU for Magnification?
- **Modern**: Successor to WebGL, better abstraction
- **Compute shaders**: Perfect for video processing
- **Cross-platform**: Works in Electron/Chrome
- **Performance**: Near-native GPU performance

---

## VI. PERFORMANCE TARGETS

### Real-Time Path (Critical)
| Operation | Target | Max |
|-----------|--------|-----|
| Frame capture | <10ms | 15ms |
| Pose estimation | <30ms | 50ms |
| State update | <1ms | 5ms |
| Render | <5ms | 10ms |
| **Total** | **<50ms (20fps)** | **60ms (16fps)** |

**If we can't hit this**: Process every 2nd-3rd frame (10-15fps pose, 60fps render).

### Offline Path (Not Critical)
| Operation | Target | Max |
|-----------|--------|-----|
| Breathing analysis | <5s/min | 15s |
| Gait analysis | <10s/min | 30s |
| Eulerian magnification | <30s/min | 2min |

---

## VII. DATA FLOW: CAPTURE → INSIGHT

```
CAPTURE
  ↓ Camera (getUserMedia)
  ↓ Frame buffer (ArrayBuffer)

POSE ESTIMATION
  ↓ MediaPipe (tfjs or native)
  ↓ 33 landmarks with confidence

PROCESSING (Pure Functions)
  ↓ normalize-landmarks
  ↓ compute-angles
  ↓ temporal-smooth
  ↓ detect-events

ANALYSIS (Pure Functions)
  ↓ extract-torso-motion
  ↓ fft-transform
  ↓ detect-breathing-rate
  ↓ detect-fatigue-windows

INSIGHTS (Pure Functions)
  ↓ generate-coaching-language
  ↓ compare-to-baseline
  ↓ format-for-display

UI (Reagent/React)
  ↓ Timeline viewer
  ↓ Skeleton overlay
  ↓ Metrics panel
  ↓ Insight cards
```

**Every arrow is a pure function** (except I/O at top/bottom).

---

## VIII. OBSERVABILITY: BRETT VICTOR'S LENS

### Every Metric Has Metadata

```clojure
{:rate-bpm 22
 :confidence 0.94
 :method :fft-peak-detection
 :frequency-hz 0.367
 :source-frames [120 121 ... 180]
 :explanation "Detected dominant frequency at 0.37 Hz from torso motion"}
```

**UI principle**: Hover over "22 bpm" → tooltip shows why.

### Every Decision Has a Path

```clojure
;; Breathing rate detection
{:signal [0.1 0.2 0.15 ...]     ;; Input
 :fft-output [...]               ;; Frequency domain
 :peak {:freq 0.367 :mag 0.8}   ;; Detected peak
 :rate-bpm 22                    ;; Derived rate
 :alternatives-considered [
   {:freq 0.5 :mag 0.3 :rejected "Outside breathing range"}
 ]}
```

**Coach can inspect**: "Why 22 bpm and not 30?" → See the frequency spectrum.

---

## IX. TESTING PHILOSOPHY

### Pure Functions → Easy Tests

```clojure
(deftest test-breathing-rate
  (let [signal (mock-breathing-signal 22 60)
        result (detect-breathing-rate signal)]
    (is (< 20 (:rate-bpm result) 24))))
```

**No mocks needed.** Just data in, data out.

### Test Pyramid
```
         E2E (Manual)
       ─────────────
      Integration (Few)
    ───────────────────
   Unit Tests (Many, Fast)
  ─────────────────────────
```

**90% unit tests**, 9% integration, 1% E2E.

---

## X. ITERATION CADENCE

### Daily
- Work on 1-2 Claude Code tasks
- Each task: 4-8 hours
- End of day: `npm start` → something works

### Weekly
- Complete 1 LOD stage
- Ship a demo-able feature
- Stakeholder review (optional)

### Sprint (3 weeks)
- LOD 0-6: Complete MVP
- All core features working
- Ready for extended user testing

---

## XI. SUCCESS CRITERIA (18 Days)

### Technical
- ✅ All analyzers produce spec-conforming output
- ✅ Real-time runs at 15+ fps
- ✅ Offline completes in <2 min per minute of footage
- ✅ No crashes during 1-hour session

### User Experience
- ✅ User can record without help
- ✅ Results are immediately understandable
- ✅ Insights are actionable
- ✅ No unexpected behavior

### Code Quality
- ✅ 90% pure functions
- ✅ All pure functions have unit tests
- ✅ No compilation warnings
- ✅ Follows Clojure idioms

---

## XII. WHAT HAPPENS AFTER MVP (Week 4+)

### Refinement
- Profile and optimize slowest 5%
- UI polish (animations, better layouts)
- Error handling (graceful degradation)

### New Verticals
- Gait analyzer (step detection)
- Lifting analyzer (squat depth, bar path)
- Dance analyzer (beat alignment)

### Advanced Features
- Multi-session montage
- Export reports (PDF, video)
- Cloud sync (optional)

---

## XIII. PHILOSOPHICAL COMMITMENTS

### From Hickey
**Simplicity over ease**: The right abstraction may be harder today but easier forever.

**Data over objects**: If it's not data, it's hiding something.

**Time is explicit**: State is a series of values, not a mutable thing.

### From Carmack
**Working code over perfect code**: Ship it, measure it, then optimize.

**Understand the machine**: Know your memory model, frame budget, GPU.

**Profile first**: Intuition lies. Benchmarks don't.

### From Victor
**Make invisible visible**: Every system should explain itself.

**Immediate feedback**: Gap between action and result <1 second.

**Medium shapes thought**: Interface isn't decoration, it's the idea.

### From Graham
**Always shippable**: Every commit produces something runnable.

**Vertical slices**: Thin features across full stack beat horizontal layers.

**Users over architecture**: If it doesn't help a user, it's not a feature.

---

## XIV. ANTI-PATTERNS TO AVOID

### Don't
- ❌ Mutate state (except in imperative shell)
- ❌ Mix I/O with logic
- ❌ Hide decisions in black boxes
- ❌ Optimize before measuring
- ❌ Build horizontal layers (all UI, then all backend)
- ❌ Delay shipping until "perfect"

### Do
- ✅ Transform data with pure functions
- ✅ Isolate side effects to shell
- ✅ Explain every metric
- ✅ Profile, then optimize
- ✅ Build vertical slices (one feature, end-to-end)
- ✅ Ship working prototypes daily

---

## XV. CRITICAL REMINDERS

1. **The IR is the truth**: Everything flows through EDN. If it's not in the IR, it doesn't exist.

2. **The core is pure**: 90% of code should be testable without I/O. If you're adding side effects, you're in the wrong place.

3. **Every LOD stage runs**: No "TODO" or "Coming soon". If it's not working, it's not done.

4. **Observability is not optional**: Every metric, every decision, every insight must explain itself.

5. **The user is one person**: We're building a deeply personal tool, not a generic SaaS. Embrace that constraint.

---

## XVI. EXAMPLE DEVELOPMENT SESSION

```
# Morning standup (5 min)
- Yesterday: Completed Task 3.1 (torso motion extraction)
- Today: Task 3.2 (FFT & breathing rate)
- Blockers: None

# Work (6-8 hours)
- Read task description in PLAN.md
- Check schema in SPEC.md
- Review examples in EXAMPLES.md
- Write code in src/shared/fourier.cljs
- Write tests in test/shared/fourier_test.cljs
- Run tests: npx shadow-cljs test
- Verify in REPL
- Commit

# End of day demo (5 min)
- npm start
- Load recorded session
- Click "Analyze"
- See breathing rate: 22 bpm
- Show FFT spectrum in console
- ✅ Task complete
```

---

## XVII. NEXT STEPS

Once you've read this document:

1. **Read SPEC.md**: Understand the data schemas and APIs
2. **Read PLAN.md**: See the roadmap and task breakdown
3. **Read EXAMPLES.md**: Study code patterns and idioms
4. **Start Task 1.1**: Project scaffolding (LOD 0)

**Remember**: You're not one developer. You're a team of legends (Hickey, Carmack, Victor, Graham). Channel their wisdom.

**Now go build something incredible.**
