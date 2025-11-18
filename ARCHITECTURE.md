# CombatSys Motion Analysis Platform - Architecture Overview
**Version:** 1.0.0  
**Last Updated:** 2025-11-17

---

## Table of Contents
1. [Philosophy](#philosophy)
2. [System Context](#system-context)
3. [Component Architecture](#component-architecture)
4. [Data Flow](#data-flow)
5. [Technology Stack](#technology-stack)
6. [Design Patterns](#design-patterns)
7. [Performance Considerations](#performance-considerations)
8. [Security & Privacy](#security--privacy)

---

## Philosophy

### Rich Hickey: Simplicity & Data Orientation

**Core Tenets:**
- **Simple > Easy**: Choose simple solutions even if they require more upfront thought
- **Data is King**: Everything is data (EDN), not objects or classes
- **Pure Functions**: 90% of the codebase has no side effects
- **Immutability**: State is time-indexed values, never mutated

**Application:**
```clojure
;; Bad: Object-oriented, stateful
class Session {
  constructor() { this.frames = []; }
  addFrame(frame) { this.frames.push(frame); }  // Mutation!
  getAnalysis() { /* complex stateful logic */ }
}

;; Good: Data-oriented, immutable
(defn add-frame [session frame]
  (update session :session/timeline conj frame))

(defn analyze-session [session]
  (-> session
      breathing/analyze
      posture/analyze
      gait/analyze))
```

### John Carmack: Pragmatic Performance

**Core Tenets:**
- **Profile First**: Never optimize without measurement
- **Critical Path Focus**: Optimize the 5% that matters
- **Understand the Machine**: Memory layout, cache, GPU architecture
- **Working Code Wins**: Elegant theory must prove itself

**Application:**
```clojure
;; Profile the main loop
(defn profile-fn [f label]
  (let [start (js/performance.now)
        result (f)
        end (js/performance.now)]
    (when (> (- end start) 10)  ;; Warn if >10ms
      (println (str "[SLOW] " label ": " (- end start) "ms")))
    result))

;; Optimize hotspots
(defn process-pose [raw-pose]
  ;; If profiling shows this is slow, optimize:
  ;; - Use transducers for collection processing
  ;; - Pre-compute lookup tables
  ;; - Batch operations
  ...)
```

### Paul Graham: Continuous Working Prototypes

**Core Tenets:**
- **Always Shippable**: Every commit runs end-to-end
- **Vertical Slices**: Thin features across full stack
- **Fast Iteration**: <2 second edit-compile-run cycle
- **User-Facing Value**: Each milestone demonstrates capability

**Application:**
- LOD 0 (Day 1): Mocks everywhere, but the entire pipeline runs
- LOD 1 (Day 4): Real camera, still mock analysis, but you can record sessions
- LOD 2 (Day 7): Real breathing analysis, but posture is still mock
- Every stage is demonstrable to users

---

## System Context

### High-Level Architecture

```
┌────────────────────────────────────────────────────────────┐
│                      USER                                   │
│  (Athlete, Coach, Movement Researcher)                     │
└────────────────┬───────────────────────────────────────────┘
                 │
                 │ Camera, Keyboard, Mouse
                 ↓
┌────────────────────────────────────────────────────────────┐
│            COMBATSYS MOTION DESKTOP APP                     │
│                    (Electron)                               │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  UI Layer (Reagent/React)                            │ │
│  │  • Timeline viewer • Skeleton overlay • Analysis UI  │ │
│  └──────────────────┬───────────────────────────────────┘ │
│                     │                                       │
│  ┌──────────────────┴───────────────────────────────────┐ │
│  │  State Management (re-frame)                         │ │
│  │  • Single app-state atom                             │ │
│  │  • Event handlers, subscriptions                     │ │
│  └──────────────────┬───────────────────────────────────┘ │
│                     │                                       │
│  ┌──────────────────┴───────────────────────────────────┐ │
│  │  Imperative Shell                                    │ │
│  │  • Camera I/O • File system • GPU • Audio            │ │
│  └──────────────────┬───────────────────────────────────┘ │
│                     │                                       │
│  ┌──────────────────┴───────────────────────────────────┐ │
│  │  Functional Core (Pure ClojureScript)                │ │
│  │  • Pose processing • Analytics • Insights            │ │
│  └──────────────────────────────────────────────────────┘ │
└────────────────┬───────────────────────────────────────────┘
                 │
                 │ File system I/O
                 ↓
┌────────────────────────────────────────────────────────────┐
│                  LOCAL STORAGE                             │
│  • Sessions (EDN + video files)                            │
│  • User profiles • Configuration                           │
└────────────────────────────────────────────────────────────┘
```

### External Dependencies

```
┌─────────────────┐
│   MediaPipe     │  ← Pose estimation (Google)
│ (TensorFlow.js) │
└─────────────────┘

┌─────────────────┐
│     WebGPU      │  ← GPU compute (W3C standard)
└─────────────────┘

┌─────────────────┐
│    Electron     │  ← Desktop app framework
└─────────────────┘

┌─────────────────┐
│  getUserMedia   │  ← Camera access (Web API)
└─────────────────┘
```

---

## Component Architecture

### 1. Presentation Layer (Reagent/React)

**Responsibilities:**
- Render UI based on app state
- Handle user interactions (clicks, keyboard, mouse)
- Display real-time feedback (skeleton overlay, metrics)

**Key Components:**
```clojure
[app-root]
├─ [navigation-bar]
├─ [live-view]
│  ├─ [camera-feed]
│  ├─ [skeleton-overlay]
│  ├─ [metrics-panel]
│  └─ [recording-controls]
├─ [session-browser]
│  ├─ [session-list]
│  └─ [session-card]
├─ [analysis-view]
│  ├─ [video-player]
│  ├─ [timeline-scrubber]
│  ├─ [metrics-graphs]
│  └─ [insights-panel]
└─ [comparison-view]
   ├─ [dual-video-player]
   └─ [diff-report]
```

**Data Flow:**
```
User Action → Dispatch Event → Update State → Re-render Components
```

---

### 2. State Management (re-frame)

**App State Structure:**
```clojure
{:ui
 {:current-view :live | :browser | :analysis | :comparison
  :selected-session-id #uuid "..."
  :timeline-position-ms 0
  :overlays #{:skeleton :metrics :breathing}}
 
 :capture
 {:mode :idle | :recording | :processing
  :camera-stream camera-handle
  :current-frame frame-data
  :buffer [frame frame ...]  ;; Last 30 frames
  :recording-start-time timestamp}
 
 :sessions
 {#uuid "session-1" {:session/id ... :session/timeline ...}
  #uuid "session-2" {...}}
 
 :current-session-id #uuid "..."
 
 :user-profile
 {:user/id #uuid "..."
  :user/height-cm 175
  :user/baseline-pose {...}
  :user/learned-thresholds {...}}
 
 :feedback
 {:cues-queue [cue cue ...]
  :recent-events [event event ...]
  :alerts [alert alert ...]}}
```

**Event Handlers:**
```clojure
;; All state updates are pure functions
(rf/reg-event-db
 :start-recording
 (fn [db _]
   (-> db
       (assoc-in [:capture :mode] :recording)
       (assoc-in [:capture :recording-start-time] (js/Date.now))
       (assoc :current-session-id (random-uuid)))))

(rf/reg-event-db
 :append-frame
 (fn [db [_ frame]]
   (let [session-id (:current-session-id db)
         processed (process-pose frame)]
     (update-in db [:sessions session-id :session/timeline] conj processed))))
```

**Subscriptions:**
```clojure
;; Derived data (computed from app state)
(rf/reg-sub
 :current-session
 (fn [db _]
   (get-in db [:sessions (:current-session-id db)])))

(rf/reg-sub
 :breathing-rate
 (fn [db _]
   (get-in db [:sessions (:current-session-id db) 
                     :session/analysis :breathing :rate-bpm])))
```

---

### 3. Imperative Shell

**Responsibilities:**
- Interact with external world (camera, file system, GPU)
- Coordinate side effects
- Bridge between pure functions and I/O

**Modules:**

#### 3.1 Camera Module (`combatsys.camera`)
```clojure
(defn init-camera [config]
  ;; Side effect: access webcam
  ;; Returns: camera-handle
  ...)

(defn capture-frame [camera-handle]
  ;; Side effect: read from camera
  ;; Returns: frame-data
  ...)
```

#### 3.2 Storage Module (`combatsys.storage`)
```clojure
(defn save-session [session]
  ;; Side effect: write to file system
  ;; Returns: filepath
  ...)

(defn load-session [filepath]
  ;; Side effect: read from file system
  ;; Returns: session
  ...)
```

#### 3.3 GPU Module (`combatsys.gpu`)
```clojure
(defn init-gpu []
  ;; Side effect: initialize WebGPU context
  ;; Returns: gpu-context
  ...)

(defn dispatch-compute [shader input-buffers output-buffers]
  ;; Side effect: execute GPU compute shader
  ;; Returns: nil (results written to output-buffers)
  ...)
```

#### 3.4 Audio Module (`combatsys.audio`)
```clojure
(defn play-cue [cue-type]
  ;; Side effect: play audio file
  ;; Returns: nil
  ...)
```

---

### 4. Functional Core

**Responsibilities:**
- Transform data (pure functions)
- Compute analytics, metrics, insights
- No side effects, 100% testable

**Modules:**

#### 4.1 Pose Processing (`combatsys.pose`)
```clojure
;; Pure: landmarks → processed-frame
(defn process-pose [raw-landmarks]
  (-> raw-landmarks
      normalize-coordinates
      compute-angles
      compute-velocities
      validate-plausibility))

;; Pure: sequence of poses → smoothed sequence
(defn temporal-smooth [poses window-size]
  (->> poses
       (partition window-size 1)
       (map average-pose)))
```

#### 4.2 Breathing Analyzer (`combatsys.breathing`)
```clojure
;; Pure: timeline → breathing-analysis
(defn analyze [timeline]
  (let [signal (extract-torso-motion timeline)
        rate (detect-breathing-rate signal)
        windows (detect-fatigue-windows signal)]
    {:rate-bpm rate
     :fatigue-windows windows
     :insights (generate-insights rate windows)}))
```

#### 4.3 Posture Analyzer (`combatsys.posture`)
```clojure
;; Pure: timeline → posture-analysis
(defn analyze [timeline]
  (let [head-forward (compute-head-forward timeline)
        shoulder-imbalance (compute-shoulder-imbalance timeline)]
    {:head-forward-cm head-forward
     :shoulder-imbalance-deg shoulder-imbalance
     :insights (generate-insights head-forward shoulder-imbalance)}))
```

#### 4.4 Comparison Engine (`combatsys.comparison`)
```clojure
;; Pure: session × session → diff-report
(defn compare-sessions [session-a session-b]
  (let [breathing-a (get-in session-a [:session/analysis :breathing])
        breathing-b (get-in session-b [:session/analysis :breathing])]
    {:breathing
     {:delta-rate-bpm (- (:rate-bpm breathing-b) (:rate-bpm breathing-a))
      :improvement? (> (:rate-bpm breathing-b) (:rate-bpm breathing-a))}}))
```

---

## Data Flow

### Real-Time Loop (Live View)

```
┌──────────────┐
│ Camera Frame │
└──────┬───────┘
       │
       ↓
┌──────────────────────┐
│ MediaPipe (GPU)      │  ← 20-30ms
│ Pose Estimation      │
└──────┬───────────────┘
       │
       ↓ Raw Landmarks
┌──────────────────────┐
│ Pose Processing      │  ← <5ms (pure fn)
│ (angles, velocities) │
└──────┬───────────────┘
       │
       ↓ Processed Frame
┌──────────────────────┐
│ State Update         │  ← <1ms
│ (swap! app-state ...) │
└──────┬───────────────┘
       │
       ↓ New State
┌──────────────────────┐
│ React Re-render      │  ← <16ms
│ (UI updates)         │
└──────────────────────┘
```

**Total latency**: ~46ms (22fps) on mid-range hardware

---

### Offline Analysis Pipeline

```
┌──────────────────┐
│ Recorded Session │
│ (EDN + video)    │
└────────┬─────────┘
         │
         ↓
┌───────────────────────┐
│ Load Session          │
│ (parse EDN, ref video)│
└────────┬──────────────┘
         │
         ↓ Session with Timeline
┌───────────────────────┐
│ Run Analyzers         │  ← 10-30s for 2min session
│ • breathing/analyze   │
│ • posture/analyze     │
│ • gait/analyze        │
└────────┬──────────────┘
         │
         ↓ Session with Analysis
┌───────────────────────┐
│ Generate Insights     │  ← <1s
│ (coaching language)   │
└────────┬──────────────┘
         │
         ↓ Session with Insights
┌───────────────────────┐
│ Save Updated Session  │
│ (write EDN)           │
└───────────────────────┘
```

---

### Magnification Pipeline (GPU)

```
┌──────────────────┐
│ Video File       │
└────────┬─────────┘
         │
         ↓
┌───────────────────────┐
│ Decode to Frames      │  ← 5-10s
│ (ffmpeg or browser)   │
└────────┬──────────────┘
         │
         ↓ Frame Buffers
┌───────────────────────┐
│ Upload to GPU         │  ← 2-5s
│ (WebGPU buffers)      │
└────────┬──────────────┘
         │
         ↓ GPU Buffers
┌───────────────────────┐
│ Compute Shader        │  ← 30-60s (depends on gain)
│ (Eulerian mag)        │
└────────┬──────────────┘
         │
         ↓ Magnified Buffers
┌───────────────────────┐
│ Download from GPU     │  ← 2-5s
│ (to CPU memory)       │
└────────┬──────────────┘
         │
         ↓ Magnified Frames
┌───────────────────────┐
│ Encode to Video       │  ← 5-10s
│ (webm or mp4)         │
└────────┬──────────────┘
         │
         ↓
┌──────────────────┐
│ Magnified Video  │
└──────────────────┘
```

**Total time**: ~60-120s for 30s video

---

## Technology Stack

### Language & Runtime
- **ClojureScript**: Compiles to JavaScript, runs in Electron
- **Node.js**: 18.x+ (Electron runtime)
- **Java**: OpenJDK 11+ (for ClojureScript REPL)

### Frameworks
- **Electron**: Desktop app framework (cross-platform)
- **Reagent**: ClojureScript wrapper for React
- **re-frame**: State management (inspired by Flux/Redux)

### Build Tools
- **shadow-cljs**: ClojureScript compiler and build tool
- **npm**: Package manager for JavaScript dependencies

### ML/CV Libraries
- **TensorFlow.js**: Run ML models in browser/Node.js
- **MediaPipe**: Pose estimation (via tfjs-models)

### GPU Compute
- **WebGPU**: Modern GPU compute API (successor to WebGL)

### Video Processing
- **ffmpeg**: Video encoding/decoding (via child_process)
- **MediaRecorder API**: Browser video recording

### Development Tools
- **REPL**: Interactive development (connect to running app)
- **shadow-cljs devtools**: Hot reload, source maps

---

## Design Patterns

### 1. Functional Core / Imperative Shell

**Pattern:**
```
┌────────────────────────────────┐
│   Imperative Shell             │  ← Thin layer
│   (side effects)               │
├────────────────────────────────┤
│   Functional Core              │  ← Thick layer
│   (pure functions)             │
└────────────────────────────────┘
```

**Benefits:**
- 90% of code is pure (easy to test, reason about)
- Side effects are isolated and explicit
- Functions compose naturally

---

### 2. Data-Oriented Architecture

**Pattern:**
```clojure
;; Everything is data (not objects)
session = {:session/id ...
           :session/timeline [...]
           :session/analysis {...}}

;; Transform with pure functions
(-> session
    breathing/analyze
    posture/analyze
    insights/generate)
```

**Benefits:**
- Data is simple (maps, vectors, primitives)
- Generic operations work on any data
- Easy to serialize, transmit, persist

---

### 3. Single Atom State (Game Loop)

**Pattern:**
```clojure
(def app-state (atom {...}))

(defn main-loop []
  (js/requestAnimationFrame
   (fn [timestamp]
     ;; 1. Read inputs (side effect)
     (let [frame (capture-frame)]
       
       ;; 2. Update state (pure)
       (swap! app-state update-fn frame)
       
       ;; 3. Render (side effect)
       (render-ui @app-state))
     
     (main-loop))))
```

**Benefits:**
- Single source of truth
- Easy to inspect state (REPL-friendly)
- Time-travel debugging possible

---

### 4. Pipeline Composition

**Pattern:**
```clojure
;; Each stage is a pure function: data → data
(defn process-session [session]
  (-> session
      load-video
      extract-poses
      smooth-poses
      compute-angles
      compute-velocities
      detect-events
      analyze-breathing
      analyze-posture
      generate-insights
      save-results))
```

**Benefits:**
- Each stage is independently testable
- Easy to add/remove stages
- Clear data flow

---

## Performance Considerations

### Critical Path: Real-Time Loop

**Target**: 30fps (33ms budget per frame)

**Breakdown**:
| Operation | Budget | Typical | Optimization |
|-----------|--------|---------|--------------|
| Camera capture | 5ms | 5ms | Hardware-dependent |
| Pose estimation | 25ms | 30ms | Reduce resolution if needed |
| State update | 1ms | <1ms | Use transients for large updates |
| UI render | 10ms | 10ms | React.memo, shouldComponentUpdate |
| **Total** | **41ms** | **46ms** | **Process every 2nd frame if needed** |

**Optimizations**:
1. **Skip frames**: Process every 2nd or 3rd frame (10-15fps pose updates)
2. **Reduce resolution**: 960x540 instead of 1920x1080
3. **Use MediaPipe Lite**: Lighter model, faster inference
4. **Batch updates**: Update state every 3 frames instead of every frame

---

### Offline Processing: Analyzers

**Target**: <30s for 2-minute session

**Breakdown**:
| Analyzer | Time | Optimization |
|----------|------|--------------|
| Breathing | 5s | Parallelize FFT across segments |
| Posture | 2s | Vectorize operations |
| Gait | 10s | Pre-compute step detection |
| Insights | 1s | Template-based generation |
| **Total** | **18s** | **Acceptable** |

---

### GPU Processing: Magnification

**Target**: <2 minutes for 30s video

**Breakdown**:
| Operation | Time | Optimization |
|-----------|------|--------------|
| Decode video | 5s | Use hardware decoder |
| Upload to GPU | 3s | Stream upload (don't wait for all frames) |
| Compute shader | 60s | Optimize shader (reduce pyramid levels) |
| Download from GPU | 3s | Stream download |
| Encode video | 10s | Use hardware encoder |
| **Total** | **81s** | **Acceptable** |

---

## Security & Privacy

### Data Privacy

**Principles:**
- **Local-First**: All data stored locally by default
- **No Cloud**: No automatic uploads to cloud services
- **User Control**: User explicitly controls all exports

**Implementation:**
```clojure
;; Sessions stored locally
~/.combatsys-motion/sessions/
  ├─ 2025-11-17_10-30-00/
  │  ├─ session.edn
  │  └─ video.webm
  └─ ...

;; Optional encryption at rest
(defn save-session-encrypted [session password]
  (let [encrypted (encrypt session password)]
    (spit filepath encrypted)))
```

---

### Camera Permissions

**UI Flow:**
1. App requests camera access
2. OS prompts user for permission
3. User grants/denies
4. App displays visual indicator when camera is active
5. Quick disable button always visible

**Code:**
```clojure
(defn request-camera-permission []
  (-> (js/navigator.mediaDevices.getUserMedia #js {:video true})
      (.then (fn [stream]
               (rf/dispatch [:camera-granted stream])))
      (.catch (fn [error]
                (rf/dispatch [:camera-denied error])))))
```

---

### Export Control

**Principle**: User must explicitly approve all exports

**Implementation:**
```clojure
(defn export-session [session format]
  ;; Show dialog: "Export session to PDF? This will save data to disk."
  (when (confirm-dialog "Export session?")
    (case format
      :pdf (export-to-pdf session)
      :csv (export-to-csv session)
      :json (export-to-json session))))
```

---

## Appendix: Architectural Decisions

### Why ClojureScript?
- **Immutable data**: Natural fit for data-oriented architecture
- **Functional**: Compose pure functions, easy to reason about
- **REPL**: Interactive development, fast iteration
- **Ecosystem**: Good interop with JavaScript (npm packages)

### Why Electron?
- **Cross-platform**: macOS, Windows, Linux from single codebase
- **Mature**: Battle-tested, large ecosystem
- **Camera access**: Native APIs for webcam
- **GPU**: WebGPU support via Chromium

### Why WebGPU?
- **Modern**: Successor to WebGL, better compute support
- **Cross-platform**: Works on all major OSes
- **Performance**: Native GPU compute performance

### Why re-frame?
- **Functional**: Pure functions for state updates
- **Time-travel**: Debug by replaying events
- **Subscriptions**: Derived data, reactive UI

---

**Document Status**: DRAFT v1.0  
**Next Review**: After LOD 2 completion  
**Owner**: CombatSys Architecture Team
