# COMBATSYS MOTION ANALYSIS: TECHNICAL SPECIFICATION
## Data Schemas, APIs, and Contracts

---

## I. EDN SCHEMA DEFINITIONS

All data structures are EDN maps with namespaced keywords. This section defines the canonical schema.

### Session Schema

```clojure
(ns combatsys.schema
  (:require [clojure.spec.alpha :as s]))

;; ============================================================
;; PRIMITIVES
;; ============================================================

(s/def ::uuid uuid?)
(s/def ::instant inst?)
(s/def ::ms int?)
(s/def ::fps (s/and int? pos?))
(s/def ::confidence (s/double-in :min 0.0 :max 1.0))

;; ============================================================
;; GEOMETRY
;; ============================================================

(s/def ::x number?)
(s/def ::y number?)
(s/def ::z number?)

(s/def ::point-2d
  (s/keys :req-un [::x ::y]))

(s/def ::point-3d
  (s/keys :req-un [::x ::y ::z]))

(s/def ::landmark-id
  #{:nose :left-eye :right-eye :left-ear :right-ear
    :left-shoulder :right-shoulder :left-elbow :right-elbow
    :left-wrist :right-wrist :left-hip :right-hip
    :left-knee :right-knee :left-ankle :right-ankle
    ;; MediaPipe 33-point model
    :left-pinky :right-pinky :left-index :right-index
    :left-thumb :right-thumb :left-heel :right-heel
    :left-foot-index :right-foot-index})

(s/def ::landmark
  (s/keys :req-un [::landmark-id ::x ::y ::z ::visibility]
          :opt-un [::confidence]))

(s/def ::visibility ::confidence)

;; ============================================================
;; CAMERA
;; ============================================================

(s/def ::camera-id string?)
(s/def ::resolution (s/tuple pos-int? pos-int?))
(s/def ::intrinsics
  (s/keys :req-un [::fx ::fy ::cx ::cy]))

(s/def ::fx number?)
(s/def ::fy number?)
(s/def ::cx number?)
(s/def ::cy number?)

(s/def ::position ::point-3d)
(s/def ::orientation ::point-3d) ;; euler angles

(s/def ::camera
  (s/keys :req-un [::camera-id ::resolution ::fps]
          :opt-un [::intrinsics ::position ::orientation]))

;; ============================================================
;; USER PROFILE
;; ============================================================

(s/def ::user-id ::uuid)
(s/def ::height-cm pos-int?)
(s/def ::baseline-pose map?) ;; T-pose landmarks
(s/def ::learned-thresholds map?) ;; Personalized thresholds

(s/def ::user
  (s/keys :req-un [::user-id ::height-cm]
          :opt-un [::baseline-pose ::learned-thresholds]))

;; ============================================================
;; POSE
;; ============================================================

(s/def ::landmarks (s/coll-of ::landmark :min-count 33 :max-count 33))
(s/def ::world-coords (s/coll-of ::point-3d))

(s/def ::pose
  (s/keys :req-un [::landmarks ::confidence]
          :opt-un [::world-coords]))

;; ============================================================
;; DERIVED METRICS
;; ============================================================

(s/def ::angle number?) ;; degrees
(s/def ::velocity ::point-3d) ;; m/s
(s/def ::acceleration ::point-3d) ;; m/s²

(s/def ::angles
  (s/map-of keyword? ::angle))

(s/def ::velocities
  (s/map-of ::landmark-id ::velocity))

(s/def ::com-position ::point-3d)
(s/def ::support-polygon (s/coll-of ::point-2d))
(s/def ::stability-score ::confidence)

(s/def ::balance
  (s/keys :req-un [::com-position ::stability-score]
          :opt-un [::support-polygon]))

(s/def ::derived
  (s/keys :req-un [::angles]
          :opt-un [::velocities ::balance]))

;; ============================================================
;; EVENTS
;; ============================================================

(s/def ::event-type
  #{:breathing-pause :balance-loss :high-force :posture-alert})

(s/def ::severity ::confidence)
(s/def ::message string?)

(s/def ::event
  (s/keys :req-un [::event-type ::severity]
          :opt-un [::message]))

;; ============================================================
;; FRAME
;; ============================================================

(s/def ::frame-index nat-int?)
(s/def ::timestamp-ms ::ms)

(s/def ::frame
  (s/keys :req-un [::frame-index ::timestamp-ms ::pose]
          :opt-un [::derived ::events]))

(s/def ::timeline
  (s/coll-of ::frame))

;; ============================================================
;; ANALYSIS: BREATHING
;; ============================================================

(s/def ::rate-bpm pos?)
(s/def ::depth-score ::confidence)
(s/def ::rhythm-regularity ::confidence)

(s/def ::start-ms ::ms)
(s/def ::end-ms ::ms)

(s/def ::fatigue-window
  (s/keys :req-un [::start-ms ::end-ms ::severity]))

(s/def ::fatigue-windows
  (s/coll-of ::fatigue-window))

(s/def ::method keyword?)
(s/def ::source-frames (s/coll-of ::frame-index))
(s/def ::explanation string?)

(s/def ::breathing-analysis
  (s/keys :req-un [::rate-bpm ::depth-score]
          :opt-un [::rhythm-regularity ::fatigue-windows
                   ::method ::confidence ::source-frames ::explanation]))

;; ============================================================
;; ANALYSIS: GAIT
;; ============================================================

(s/def ::step-length-m pos?)
(s/def ::cadence-steps-per-min pos?)
(s/def ::symmetry-score ::confidence)
(s/def ::alignment #{:good :valgus :varus :neutral})

(s/def ::gait-analysis
  (s/keys :req-un [::step-length-m ::cadence-steps-per-min ::symmetry-score]
          :opt-un [::alignment ::events]))

;; ============================================================
;; ANALYSIS: POSTURE
;; ============================================================

(s/def ::head-forward-cm number?)
(s/def ::shoulder-imbalance-deg number?)
(s/def ::spine-alignment #{:neutral :kyphotic :lordotic :scoliotic})
(s/def ::overall-score ::confidence)

(s/def ::posture-analysis
  (s/keys :req-un [::overall-score]
          :opt-un [::head-forward-cm ::shoulder-imbalance-deg ::spine-alignment]))

;; ============================================================
;; ANALYSIS: AGGREGATE
;; ============================================================

(s/def ::analysis
  (s/keys :opt-un [::breathing-analysis ::gait-analysis ::posture-analysis]))

;; ============================================================
;; SESSION (TOP-LEVEL)
;; ============================================================

(s/def ::session-id ::uuid)
(s/def ::created-at ::instant)
(s/def ::duration-ms ::ms)
(s/def ::status #{:recording :processing :complete :error})

(s/def ::session
  (s/keys :req-un [::session-id ::user-id ::created-at ::duration-ms ::status ::camera ::timeline]
          :opt-un [::analysis]))
```

---

## II. FUNCTION SIGNATURES (API CONTRACT)

### Core Pure Functions

Every module exposes pure functions with well-defined contracts:

#### combatsys.pose

```clojure
(ns combatsys.pose
  "Pure functions for pose processing")

(s/fdef normalize-landmarks
  :args (s/cat :landmarks ::landmarks)
  :ret ::landmarks)

(defn normalize-landmarks
  "Normalize landmark coordinates to [-1, 1] range"
  [landmarks]
  ,,,)

(s/fdef compute-angle
  :args (s/cat :p1 ::point-3d :p2 ::point-3d :p3 ::point-3d)
  :ret ::angle)

(defn compute-angle
  "Compute angle (degrees) formed by three points (p1-p2-p3)"
  [p1 p2 p3]
  ,,,)

(s/fdef extract-angles
  :args (s/cat :pose ::pose)
  :ret ::angles)

(defn extract-angles
  "Extract all joint angles from pose"
  [pose]
  ,,,)

(s/fdef temporal-smooth
  :args (s/cat :poses (s/coll-of ::pose) :window-size pos-int?)
  :ret (s/coll-of ::pose))

(defn temporal-smooth
  "Smooth pose sequence with sliding window average"
  [poses window-size]
  ,,,)
```

#### combatsys.breathing

```clojure
(ns combatsys.breathing
  "Breathing analysis from pose motion")

(s/fdef extract-torso-motion
  :args (s/cat :timeline ::timeline)
  :ret (s/coll-of number?))

(defn extract-torso-motion
  "Extract magnitude of torso landmark motion over time"
  [timeline]
  ,,,)

(s/fdef detect-breathing-rate
  :args (s/cat :signal (s/coll-of number?))
  :ret ::rate-bpm)

(defn detect-breathing-rate
  "Detect breathing rate (bpm) via FFT peak in 0.1-0.5 Hz"
  [signal]
  ,,,)

(s/fdef detect-fatigue-windows
  :args (s/cat :signal (s/coll-of number?) :threshold number?)
  :ret ::fatigue-windows)

(defn detect-fatigue-windows
  "Detect periods where breathing signal drops below threshold"
  [signal threshold]
  ,,,)

(s/fdef analyze
  :args (s/cat :timeline ::timeline)
  :ret ::breathing-analysis)

(defn analyze
  "Full breathing analysis from timeline"
  [timeline]
  ,,,)
```

#### combatsys.gait

```clojure
(ns combatsys.gait
  "Gait analysis from pose sequence")

(s/fdef detect-steps
  :args (s/cat :timeline ::timeline)
  :ret (s/coll-of {:frame-index ::frame-index :foot ::landmark-id}))

(defn detect-steps
  "Detect heel-strike events in timeline"
  [timeline]
  ,,,)

(s/fdef compute-step-length
  :args (s/cat :steps (s/coll-of map?))
  :ret ::step-length-m)

(defn compute-step-length
  "Compute average step length from detected steps"
  [steps]
  ,,,)

(s/fdef compute-symmetry
  :args (s/cat :left-steps (s/coll-of map?) :right-steps (s/coll-of map?))
  :ret ::symmetry-score)

(defn compute-symmetry
  "Compute left-right symmetry score (1.0 = perfect symmetry)"
  [left-steps right-steps]
  ,,,)

(s/fdef analyze
  :args (s/cat :timeline ::timeline)
  :ret ::gait-analysis)

(defn analyze
  "Full gait analysis from timeline"
  [timeline]
  ,,,)
```

#### combatsys.posture

```clojure
(ns combatsys.posture
  "Posture assessment from pose")

(s/fdef measure-forward-head
  :args (s/cat :pose ::pose)
  :ret ::head-forward-cm)

(defn measure-forward-head
  "Measure forward head posture (cm ahead of shoulders)"
  [pose]
  ,,,)

(s/fdef assess-spine-alignment
  :args (s/cat :pose ::pose)
  :ret ::spine-alignment)

(defn assess-spine-alignment
  "Classify spine alignment (neutral, kyphotic, lordotic, scoliotic)"
  [pose]
  ,,,)

(s/fdef analyze
  :args (s/cat :timeline ::timeline)
  :ret ::posture-analysis)

(defn analyze
  "Full posture analysis (averaged over timeline)"
  [timeline]
  ,,,)
```

#### combatsys.insights

```clojure
(ns combatsys.insights
  "Generate coaching language from analysis")

(s/fdef generate-breathing-insights
  :args (s/cat :analysis ::breathing-analysis)
  :ret (s/coll-of string?))

(defn generate-breathing-insights
  "Convert breathing analysis to human-readable insights"
  [analysis]
  ,,,)

(s/fdef compare-sessions
  :args (s/cat :session-a ::session :session-b ::session)
  :ret map?)

(defn compare-sessions
  "Generate comparison report between two sessions"
  [session-a session-b]
  ,,,)
```

---

## III. IMPERATIVE SHELL APIS

### Camera Capture (Main Process)

```clojure
(ns combatsys.main.camera
  "Imperative camera capture (side effects)")

(defn init-camera!
  "Initialize camera stream. Returns camera handle or nil.
  Side effect: Accesses hardware"
  [camera-id resolution fps]
  ,,,)

(defn capture-frame!
  "Capture single frame from camera. Returns frame data or nil.
  Side effect: Reads from camera buffer"
  [camera-handle]
  ,,,)

(defn release-camera!
  "Release camera resources.
  Side effect: Frees hardware"
  [camera-handle]
  ,,,)
```

### Pose Estimation (Main Process)

```clojure
(ns combatsys.main.mediapipe
  "MediaPipe pose estimation wrapper")

(defn init-pose-detector!
  "Initialize MediaPipe pose detector. Returns detector handle.
  Side effect: Loads ML model into memory"
  [model-path]
  ,,,)

(defn estimate-pose!
  "Estimate pose from frame. Returns pose or nil.
  Side effect: Runs inference (CPU/GPU)"
  [detector-handle frame-data]
  ,,,)
```

### File I/O (Main Process)

```clojure
(ns combatsys.main.files
  "Session persistence")

(defn save-session!
  "Save session to disk. Returns file path.
  Side effect: Writes to file system"
  [session output-dir]
  ,,,)

(defn load-session!
  "Load session from disk. Returns session map.
  Side effect: Reads from file system"
  [session-id data-dir]
  ,,,)

(defn list-sessions!
  "List all sessions in data directory. Returns seq of session metadata.
  Side effect: Scans file system"
  [data-dir]
  ,,,)
```

### GPU Compute (Renderer Process)

```clojure
(ns combatsys.renderer.gpu
  "WebGPU compute for video processing")

(defn init-gpu!
  "Initialize WebGPU context. Returns GPU handle.
  Side effect: Acquires GPU device"
  []
  ,,,)

(defn compile-shader!
  "Compile WGSL shader. Returns shader handle.
  Side effect: GPU compilation"
  [gpu-handle shader-source]
  ,,,)

(defn dispatch-compute!
  "Dispatch compute shader. Returns promise of result.
  Side effect: GPU execution"
  [gpu-handle shader-handle input-buffers params]
  ,,,)
```

---

## IV. STATE MANAGEMENT

### App State Structure

```clojure
(def app-state
  "Single atom holding all application state"
  (atom
   {:ui
    {:current-view :live-feed | :session-browser | :analysis-view
     :selected-session-id nil
     :timeline-position-ms 0
     :overlays #{:skeleton :metrics :breathing}
     :settings {:camera-id "default"
                :resolution [1920 1080]
                :fps 30}}
    
    :capture
    {:mode :idle | :recording | :paused
     :camera-handle nil
     :pose-detector nil
     :current-frame nil
     :buffer [] ;; Last 30 frames for real-time analysis
     :recording-start-time nil}
    
    :sessions
    {#uuid "..." {:session/id #uuid "..." :session/status :complete ...}
     #uuid "..." {:session/id #uuid "..." :session/status :recording ...}}
    
    :current-session-id nil
    
    :feedback
    {:cues-queue []
     :recent-events []
     :alerts []}}))
```

### State Update Functions

```clojure
(ns combatsys.renderer.state
  "Pure state update functions")

(defn start-recording
  "Transition to recording mode. Pure function."
  [state]
  (-> state
      (assoc-in [:capture :mode] :recording)
      (assoc-in [:capture :recording-start-time] (js/Date.now))
      (assoc :current-session-id (random-uuid))
      (assoc-in [:sessions (get state :current-session-id)]
                (new-session-template))))

(defn append-frame
  "Append frame to current session. Pure function."
  [state frame]
  (let [session-id (:current-session-id state)]
    (-> state
        (update-in [:sessions session-id :session/timeline] conj frame)
        (assoc-in [:capture :current-frame] frame)
        (update-in [:capture :buffer] #(take-last 30 (conj % frame))))))

(defn stop-recording
  "Transition to idle mode. Pure function."
  [state]
  (-> state
      (assoc-in [:capture :mode] :idle)
      (update-in [:sessions (:current-session-id state) :session/status]
                 (constantly :processing))))
```

---

## V. EVENT HANDLING (re-frame style)

### Events

```clojure
(ns combatsys.renderer.events
  (:require [re-frame.core :as rf]))

;; Camera control
(rf/reg-event-db
 :camera/start-recording
 (fn [db _]
   (combatsys.renderer.state/start-recording db)))

(rf/reg-event-db
 :camera/stop-recording
 (fn [db _]
   (combatsys.renderer.state/stop-recording db)))

(rf/reg-event-db
 :camera/append-frame
 (fn [db [_ frame]]
   (combatsys.renderer.state/append-frame db frame)))

;; Session management
(rf/reg-event-db
 :session/select
 (fn [db [_ session-id]]
   (assoc db :current-session-id session-id)))

(rf/reg-event-db
 :session/analyze
 (fn [db [_ session-id analysis-type]]
   ;; Trigger async analysis, return db unchanged
   (js/setTimeout #(analyze-session-async session-id analysis-type) 0)
   db))

;; UI control
(rf/reg-event-db
 :ui/set-view
 (fn [db [_ view]]
   (assoc-in db [:ui :current-view] view)))

(rf/reg-event-db
 :ui/toggle-overlay
 (fn [db [_ overlay-key]]
   (update-in db [:ui :overlays]
              (fn [overlays]
                (if (overlays overlay-key)
                  (disj overlays overlay-key)
                  (conj overlays overlay-key))))))
```

### Subscriptions

```clojure
(ns combatsys.renderer.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 :current-view
 (fn [db _]
   (get-in db [:ui :current-view])))

(rf/reg-sub
 :current-session
 (fn [db _]
   (when-let [id (:current-session-id db)]
     (get-in db [:sessions id]))))

(rf/reg-sub
 :recent-frames
 (fn [db _]
   (get-in db [:capture :buffer])))

(rf/reg-sub
 :is-recording?
 (fn [db _]
   (= :recording (get-in db [:capture :mode]))))
```

---

## VI. UI COMPONENT STRUCTURE

### Component Hierarchy

```clojure
(ns combatsys.renderer.views
  (:require [reagent.core :as r]
            [re-frame.core :as rf]))

;; Root component
(defn app []
  (let [current-view @(rf/subscribe [:current-view])]
    [:div.app
     [navbar]
     (case current-view
       :live-feed [live-feed-view]
       :session-browser [session-browser-view]
       :analysis-view [analysis-view]
       [live-feed-view])]))

;; Live feed with skeleton overlay
(defn live-feed-view []
  (let [current-frame @(rf/subscribe [:current-frame])
        overlays @(rf/subscribe [:overlays])]
    [:div.live-feed
     [video-canvas current-frame]
     (when (:skeleton overlays)
       [skeleton-overlay current-frame])
     [recording-controls]
     [real-time-metrics]]))

;; Session browser
(defn session-browser-view []
  (let [sessions @(rf/subscribe [:all-sessions])]
    [:div.session-browser
     [:h2 "Past Sessions"]
     (for [session sessions]
       ^{:key (:session/id session)}
       [session-card session])]))

;; Detailed analysis view
(defn analysis-view []
  (let [session @(rf/subscribe [:current-session])]
    [:div.analysis-view
     [timeline-scrubber session]
     [skeleton-replay session]
     [metrics-panel session]
     [insights-panel session]]))
```

---

## VII. PERFORMANCE TARGETS

### Real-Time Path (Critical)

| Operation | Target | Max Acceptable |
|-----------|--------|----------------|
| Frame capture | <10ms | 15ms |
| Pose estimation | <30ms | 50ms |
| State update | <1ms | 5ms |
| Render | <5ms | 10ms |
| **Total frame time** | **<50ms (20fps)** | **60ms (16fps)** |

**Optimization strategy**: If we can't hit 30fps, process every 2nd or 3rd frame (10-15fps pose updates, 60fps rendering).

### Offline Path (Not Critical)

| Operation | Target | Max Acceptable |
|-----------|--------|----------------|
| Breathing analysis | <5s per minute of footage | 15s |
| Gait analysis | <10s per minute | 30s |
| Eulerian magnification | <30s per minute | 2 minutes |

**Optimization strategy**: Use WebGPU for magnification. Consider WebWorkers for CPU-bound analysis.

---

## VIII. ERROR HANDLING

### Pure Functions

```clojure
;; Return nil or default value for invalid input
(defn detect-breathing-rate [signal]
  (when (and (seq signal) (> (count signal) 30))
    (try
      (let [freq-domain (fft/transform signal)]
        (or (find-peak freq-domain 0.1 0.5)
            {:rate-bpm nil :confidence 0.0}))
      (catch js/Error e
        (js/console.error "FFT failed:" e)
        {:rate-bpm nil :confidence 0.0 :error (.getMessage e)}))))
```

### Imperative Shell

```clojure
;; Log errors, don't crash
(defn capture-frame! [camera-handle]
  (try
    (when camera-handle
      (.captureFrame camera-handle))
    (catch js/Error e
      (js/console.error "Frame capture failed:" e)
      nil)))
```

---

## IX. TESTING REQUIREMENTS

### Every Pure Function Must Have:

1. **Happy path test**
```clojure
(deftest test-breathing-rate-normal
  (let [signal (mock-breathing-signal 22 60) ;; 22 bpm, 60s
        result (detect-breathing-rate signal)]
    (is (< 20 (:rate-bpm result) 24))))
```

2. **Edge case test**
```clojure
(deftest test-breathing-rate-empty-signal
  (is (nil? (:rate-bpm (detect-breathing-rate [])))))
```

3. **Error handling test**
```clojure
(deftest test-breathing-rate-noisy
  (let [signal (mock-noisy-signal)
        result (detect-breathing-rate signal)]
    (is (or (number? (:rate-bpm result))
            (nil? (:rate-bpm result))))))
```

---

## X. FILE ORGANIZATION

```
combatsys-motion/
├── src/
│   ├── main/                      # Electron main process (Node.js)
│   │   ├── core.cljs             # App lifecycle
│   │   ├── camera.cljs           # Camera I/O
│   │   ├── mediapipe.cljs        # Pose estimation
│   │   └── files.cljs            # Session persistence
│   │
│   ├── renderer/                  # Electron renderer (Browser)
│   │   ├── core.cljs             # Entry point
│   │   ├── state.cljs            # State management
│   │   ├── events.cljs           # re-frame events
│   │   ├── subs.cljs             # re-frame subscriptions
│   │   ├── views.cljs            # Reagent components
│   │   └── gpu.cljs              # WebGPU compute
│   │
│   └── shared/                    # Pure ClojureScript (no platform deps)
│       ├── schema.cljs           # EDN schema (spec)
│       ├── pose.cljs             # Pose processing
│       ├── breathing.cljs        # Breathing analyzer
│       ├── gait.cljs             # Gait analyzer
│       ├── posture.cljs          # Posture analyzer
│       ├── insights.cljs         # Insight generation
│       ├── fourier.cljs          # FFT utilities
│       └── smoothing.cljs        # Signal smoothing
│
├── test/
│   └── shared/                    # Tests mirror src/shared/
│       ├── pose_test.cljs
│       ├── breathing_test.cljs
│       └── ...
│
├── resources/
│   ├── shaders/
│   │   └── eulerian.wgsl         # Eulerian magnification shader
│   └── models/
│       └── mediapipe-pose/       # Pose estimation model files
│
└── dev/
    └── user.cljs                  # REPL utilities
```

---

## XI. DEPENDENCIES

### package.json

```json
{
  "name": "combatsys-motion",
  "version": "1.0.0",
  "main": "out/main.js",
  "scripts": {
    "dev": "shadow-cljs watch main renderer",
    "build": "shadow-cljs release main renderer",
    "test": "shadow-cljs compile test && node out/test.js",
    "start": "electron ."
  },
  "dependencies": {
    "electron": "^27.0.0"
  },
  "devDependencies": {
    "shadow-cljs": "^2.26.0"
  }
}
```

### shadow-cljs.edn

```clojure
{:source-paths ["src/shared" "src/main" "src/renderer" "test"]
 
 :dependencies
 [[reagent "1.2.0"]
  [re-frame "1.4.0"]
  [org.clojure/test.check "1.1.1"]]
 
 :builds
 {:main
  {:target :node-script
   :main combatsys.main.core/init
   :output-to "out/main.js"}
  
  :renderer
  {:target :browser
   :modules {:app {:init-fn combatsys.renderer.core/init}}
   :dev {:http-port 8080
         :http-root "public"}
   :devtools {:after-load combatsys.renderer.core/reload}}
  
  :test
  {:target :node-test
   :output-to "out/test.js"
   :ns-regexp "-test$"}}}
```

---

This spec provides the complete contract for all code in the system. Every function, every data structure, every state transition is defined here.

**Use this as the single source of truth when implementing features.**
