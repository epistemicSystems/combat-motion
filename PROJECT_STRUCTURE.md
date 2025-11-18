# CombatSys Motion Analysis - Project Structure
**Last Updated:** 2025-11-17  
**Purpose:** Complete file organization and module overview

---

## Directory Layout

```
combatsys-motion/
├── docs/                           # Documentation
│   ├── SPEC.md                    # Technical specification
│   ├── PLAN.md                    # Development plan (LOD stages)
│   ├── TODO.md                    # Task list for Claude Code
│   ├── CHANGELOG.md               # Version history
│   ├── ARCHITECTURE.md            # System architecture
│   ├── PROMPT_GUIDE.md            # How to use Claude Code
│   ├── PROJECT_STRUCTURE.md       # This file
│   ├── USER_GUIDE.md              # End-user documentation (LOD 7)
│   └── TROUBLESHOOTING.md         # Common issues (LOD 7)
│
├── src/                           # Source code
│   ├── main/                      # Electron main process (Node.js)
│   │   ├── core.cljs             # App lifecycle, initialization
│   │   ├── camera.cljs           # Webcam access (getUserMedia)
│   │   ├── mediapipe.cljs        # Pose estimation integration
│   │   ├── storage.cljs          # File system operations
│   │   ├── gpu.cljs              # WebGPU compute dispatch
│   │   └── audio.cljs            # Audio feedback (cues)
│   │
│   ├── renderer/                  # Electron renderer (Browser)
│   │   ├── core.cljs             # App entry point, root component
│   │   ├── state.cljs            # re-frame state management
│   │   ├── events.cljs           # re-frame event handlers
│   │   ├── subs.cljs             # re-frame subscriptions
│   │   ├── views.cljs            # Main UI views
│   │   ├── components/           # Reusable UI components
│   │   │   ├── navigation.cljs
│   │   │   ├── timeline.cljs
│   │   │   ├── skeleton.cljs
│   │   │   ├── metrics.cljs
│   │   │   ├── insights.cljs
│   │   │   └── video_player.cljs
│   │   ├── live.cljs             # Live view (real-time capture)
│   │   ├── browser.cljs          # Session browser view
│   │   ├── analysis.cljs         # Analysis view (playback + insights)
│   │   ├── comparison.cljs       # Comparison view (side-by-side)
│   │   ├── onboarding.cljs       # User calibration flow
│   │   ├── settings.cljs         # Settings view
│   │   └── magnification.cljs    # Magnification UI (LOD 3)
│   │
│   └── shared/                    # Pure ClojureScript (no platform deps)
│       ├── schema.cljs           # EDN schema definitions
│       ├── mocks.cljs            # Mock data generators
│       ├── pose.cljs             # Pose processing (angles, velocities)
│       ├── breathing.cljs        # Breathing analyzer
│       ├── posture.cljs          # Posture analyzer
│       ├── gait.cljs             # Gait analyzer (future)
│       ├── balance.cljs          # Balance analyzer (future)
│       ├── fourier.cljs          # FFT utilities
│       ├── smoothing.cljs        # Signal smoothing utilities
│       ├── geometry.cljs         # Geometric calculations (angles, distances)
│       ├── insights.cljs         # Insight generation (coaching language)
│       ├── comparison.cljs       # Session comparison logic
│       ├── personalization.cljs  # User profile, learned thresholds
│       └── utils.cljs            # General utilities
│
├── test/                          # Tests
│   ├── shared/                   # Tests for pure functions
│   │   ├── schema_test.cljs
│   │   ├── pose_test.cljs
│   │   ├── breathing_test.cljs
│   │   ├── posture_test.cljs
│   │   └── comparison_test.cljs
│   └── integration/              # Integration tests
│       ├── recording_test.cljs
│       ├── analysis_test.cljs
│       └── ui_test.cljs
│
├── resources/                     # Static resources
│   ├── public/                   # Files copied to build output
│   │   ├── index.html           # Electron renderer HTML
│   │   ├── styles.css           # Global styles
│   │   └── icons/               # App icons
│   ├── shaders/                 # WebGPU compute shaders
│   │   ├── eulerian.wgsl       # Eulerian magnification shader
│   │   └── phase_based.wgsl    # Phase-based magnification (future)
│   └── models/                  # ML models (if needed)
│       └── mediapipe-pose/     # MediaPipe model files
│
├── dev/                          # Development utilities
│   ├── user.cljs               # REPL utilities
│   └── build.clj               # Build scripts
│
├── scripts/                      # Build and deployment scripts
│   ├── build.sh                # Build script
│   ├── package.sh              # Packaging script (LOD 7)
│   └── test.sh                 # Test runner
│
├── config/                       # Configuration files
│   ├── electron.json           # Electron configuration
│   └── app-defaults.edn        # Default app settings
│
├── .github/                      # GitHub config (if using)
│   └── workflows/
│       └── ci.yml              # Continuous integration
│
├── shadow-cljs.edn              # ClojureScript build config
├── package.json                 # npm dependencies and scripts
├── package-lock.json            # npm lock file
├── .gitignore                   # Git ignore rules
└── README.md                    # Project overview

```

---

## Module Dependencies

### Dependency Graph

```
┌─────────────────────────────────────────────────────────┐
│ UI Layer (renderer/)                                     │
│ ├─ views.cljs                                           │
│ ├─ live.cljs                                            │
│ ├─ browser.cljs                                         │
│ └─ analysis.cljs                                        │
└─────────────┬───────────────────────────────────────────┘
              │
              ↓ subscribes to / dispatches events
┌─────────────────────────────────────────────────────────┐
│ State Management (renderer/)                            │
│ ├─ state.cljs (app-state atom)                         │
│ ├─ events.cljs (event handlers)                        │
│ └─ subs.cljs (subscriptions)                           │
└─────────────┬───────────────────────────────────────────┘
              │
              ↓ calls
┌─────────────────────────────────────────────────────────┐
│ Imperative Shell (main/)                                │
│ ├─ camera.cljs                                          │
│ ├─ storage.cljs                                         │
│ ├─ gpu.cljs                                             │
│ └─ audio.cljs                                           │
└─────────────┬───────────────────────────────────────────┘
              │
              ↓ transforms data with
┌─────────────────────────────────────────────────────────┐
│ Functional Core (shared/)                               │
│ ├─ pose.cljs                                            │
│ ├─ breathing.cljs                                       │
│ ├─ posture.cljs                                         │
│ ├─ comparison.cljs                                      │
│ └─ insights.cljs                                        │
└─────────────────────────────────────────────────────────┘
```

### Inter-Module Dependencies

```
schema.cljs  →  [used by all modules for validation]
   ↓
mocks.cljs   →  [uses schema, provides test data]
   ↓
pose.cljs    →  [uses schema, geometry]
   ↓
breathing.cljs → [uses pose, fourier, smoothing]
posture.cljs   → [uses pose, geometry]
gait.cljs      → [uses pose, geometry]
   ↓
comparison.cljs → [uses all analyzers]
insights.cljs   → [uses all analyzers]
   ↓
events.cljs → [uses all analyzers, insights]
   ↓
views.cljs  → [displays results]
```

---

## File Descriptions

### Core Application Files

#### `shadow-cljs.edn`
ClojureScript build configuration.

```clojure
{:source-paths ["src/main" "src/renderer" "src/shared"]
 
 :dependencies
 [[reagent "1.2.0"]
  [re-frame "1.4.0"]]
 
 :builds
 {:main
  {:target :node-script
   :main combatsys.main.core/init
   :output-to "out/main.js"}
  
  :renderer
  {:target :browser
   :modules {:app {:init-fn combatsys.renderer.core/init}}
   :devtools {:after-load combatsys.renderer.core/reload}}}}
```

#### `package.json`
npm dependencies and scripts.

```json
{
  "name": "combatsys-motion",
  "version": "1.0.0",
  "main": "out/main.js",
  "scripts": {
    "dev": "shadow-cljs watch main renderer",
    "build": "shadow-cljs release main renderer",
    "start": "electron .",
    "test": "shadow-cljs compile test && node out/test.js"
  },
  "dependencies": {
    "electron": "^27.0.0",
    "@tensorflow/tfjs": "^4.13.0",
    "@tensorflow-models/pose-detection": "^2.1.1"
  }
}
```

---

### Main Process (Node.js Environment)

#### `src/main/core.cljs`
**Purpose**: Electron app lifecycle, window management.

**Key Functions**:
```clojure
(defn init []
  ;; Called when Electron app starts
  ;; Create main window, set up IPC, etc.
  ...)

(defn -main [& args]
  ;; Entry point
  (init))
```

---

#### `src/main/camera.cljs`
**Purpose**: Webcam access and frame capture.

**Key Functions**:
```clojure
(defn init-camera [config] → camera-handle)
(defn capture-frame [camera-handle] → frame-data)
(defn release-camera [camera-handle] → nil)
```

**Dependencies**: Electron, getUserMedia API

---

#### `src/main/mediapipe.cljs`
**Purpose**: Pose estimation using MediaPipe.

**Key Functions**:
```clojure
(defn load-model [] → model-handle)
(defn estimate-pose [model frame-buffer] → landmarks)
```

**Dependencies**: @tensorflow-models/pose-detection

---

#### `src/main/storage.cljs`
**Purpose**: File system operations for sessions.

**Key Functions**:
```clojure
(defn save-session [session] → filepath)
(defn load-session [filepath] → session)
(defn list-sessions [] → [session-metadata])
(defn delete-session [session-id] → boolean)
```

**Dependencies**: Node.js fs, path modules

---

#### `src/main/gpu.cljs`
**Purpose**: WebGPU compute shader dispatch.

**Key Functions**:
```clojure
(defn init-gpu [] → gpu-context)
(defn compile-shader [shader-source] → shader-handle)
(defn dispatch-compute [shader input-buffers] → output-buffers)
(defn download-results [output-buffers] → data)
```

**Dependencies**: WebGPU API (via Electron/Chromium)

---

### Renderer Process (Browser Environment)

#### `src/renderer/core.cljs`
**Purpose**: App initialization, root component.

**Key Functions**:
```clojure
(defn init []
  ;; Initialize re-frame, mount React root
  (rf/dispatch-sync [:initialize])
  (reagent/render [app-root] (.getElementById js/document "app")))

(defn reload []
  ;; Hot reload (called by shadow-cljs devtools)
  (init))
```

---

#### `src/renderer/state.cljs`
**Purpose**: re-frame app-state atom and initialization.

**App State Structure**:
```clojure
(def default-db
  {:ui {:current-view :live}
   :capture {:mode :idle}
   :sessions {}
   :current-session-id nil
   :user-profile nil
   :feedback {:cues-queue [] :recent-events []}})

(rf/reg-event-db
 :initialize
 (fn [_ _]
   default-db))
```

---

#### `src/renderer/events.cljs`
**Purpose**: re-frame event handlers (state updates).

**Example Events**:
```clojure
(rf/reg-event-db
 :start-recording
 (fn [db _]
   (-> db
       (assoc-in [:capture :mode] :recording)
       (assoc :current-session-id (random-uuid)))))

(rf/reg-event-db
 :append-frame
 (fn [db [_ frame]]
   (let [session-id (:current-session-id db)]
     (update-in db [:sessions session-id :session/timeline] conj frame))))
```

---

#### `src/renderer/subs.cljs`
**Purpose**: re-frame subscriptions (derived data).

**Example Subscriptions**:
```clojure
(rf/reg-sub
 :current-session
 (fn [db _]
   (get-in db [:sessions (:current-session-id db)])))

(rf/reg-sub
 :breathing-rate
 :<- [:current-session]
 (fn [session _]
   (get-in session [:session/analysis :breathing :rate-bpm])))
```

---

#### `src/renderer/views.cljs`
**Purpose**: Main UI view routing.

**Key Components**:
```clojure
(defn app-root []
  (let [current-view @(rf/subscribe [:current-view])]
    [:div.app
     [navigation-bar]
     (case current-view
       :live [live-view]
       :browser [session-browser]
       :analysis [analysis-view]
       :comparison [comparison-view]
       [live-view])]))
```

---

### Shared (Pure ClojureScript)

#### `src/shared/schema.cljs`
**Purpose**: EDN schema definitions using clojure.spec.

**Example Schemas**:
```clojure
(s/def ::session/id uuid?)
(s/def ::session/timeline (s/coll-of ::frame))
(s/def ::session (s/keys :req [::session/id ::session/timeline]))

(s/def ::frame/index nat-int?)
(s/def ::frame/timestamp-ms nat-int?)
(s/def ::frame (s/keys :req [::frame/index ::frame/timestamp-ms]))
```

---

#### `src/shared/mocks.cljs`
**Purpose**: Mock data generators for testing.

**Key Functions**:
```clojure
(defn mock-session [] → session)
(defn mock-breathing-session [bpm duration-s] → session)
(defn mock-gait-session [] → session)
```

---

#### `src/shared/pose.cljs`
**Purpose**: Pose processing (angles, velocities).

**Key Functions**:
```clojure
(defn process-pose [raw-landmarks] → processed-frame)
(defn compute-angles [landmarks] → angle-map)
(defn compute-velocities [current previous] → velocity-map)
(defn validate-plausibility [pose] → validation-result)
```

---

#### `src/shared/breathing.cljs`
**Purpose**: Breathing analysis from torso motion.

**Key Functions**:
```clojure
(defn analyze [timeline] → breathing-analysis)
(defn extract-torso-motion [timeline] → signal)
(defn detect-breathing-rate [signal] → bpm)
(defn detect-fatigue-windows [signal] → windows)
(defn generate-insights [rate windows] → insights)
```

---

#### `src/shared/posture.cljs`
**Purpose**: Posture analysis.

**Key Functions**:
```clojure
(defn analyze [timeline] → posture-analysis)
(defn compute-head-forward [timeline] → distance-cm)
(defn compute-shoulder-imbalance [timeline] → degrees)
(defn assess-spine-alignment [timeline] → quality)
```

---

#### `src/shared/comparison.cljs`
**Purpose**: Session comparison and trend analysis.

**Key Functions**:
```clojure
(defn compare-sessions [session-a session-b] → diff-report)
(defn analyze-trend [sessions] → trend-analysis)
```

---

#### `src/shared/insights.cljs`
**Purpose**: Generate coaching insights (natural language).

**Key Functions**:
```clojure
(defn generate-insights [analysis] → insights)
(defn insight-template [type data] → insight-map)
```

---

## Build Artifacts

After building, the `out/` directory contains:

```
out/
├── main.js                    # Electron main process (compiled)
├── renderer.js                # Electron renderer (compiled)
├── public/                    # Static files (copied from resources/)
│   ├── index.html
│   └── styles.css
└── manifest.json              # Build manifest
```

After packaging (LOD 7), the `dist/` directory contains:

```
dist/
├── CombatSys-Motion-1.0.0.dmg      # macOS installer
├── CombatSys-Motion-1.0.0.exe      # Windows installer
└── CombatSys-Motion-1.0.0.AppImage # Linux installer
```

---

## User Data Directory

At runtime, user data is stored in:

**macOS**: `~/Library/Application Support/CombatSys-Motion/`  
**Windows**: `%APPDATA%/CombatSys-Motion/`  
**Linux**: `~/.config/CombatSys-Motion/`

Structure:
```
CombatSys-Motion/
├── sessions/                  # Recorded sessions
│   ├── 2025-11-17_10-30-00/
│   │   ├── session.edn       # Session IR
│   │   ├── video.webm        # Recorded video
│   │   └── magnified.webm    # Magnified video (if generated)
│   └── ...
├── user-profiles/            # User calibration data
│   └── user-001.edn
├── config.edn                # User settings
└── logs/                     # Application logs
    └── app.log
```

---

## Development Workflow

### Setup
```bash
# Install dependencies
npm install

# Start development (watch mode)
npm run dev

# In another terminal, start Electron
npm start

# Connect REPL (in editor or terminal)
npx shadow-cljs cljs-repl renderer
```

### Testing
```bash
# Run all tests
npm test

# Run specific test
npx shadow-cljs compile test
node out/test.js
```

### Building
```bash
# Build optimized
npm run build

# Package for distribution
npm run package
```

---

## Adding New Features

### Adding a New Analyzer

1. Create `src/shared/new_analyzer.cljs`
2. Implement `(analyze [timeline] → analysis-map)`
3. Add tests in `test/shared/new_analyzer_test.cljs`
4. Register analyzer in `src/renderer/events.cljs`
5. Add UI in `src/renderer/analysis.cljs`

### Adding a New UI View

1. Create `src/renderer/new_view.cljs`
2. Define Reagent components
3. Add route in `src/renderer/views.cljs`
4. Add navigation in `src/renderer/components/navigation.cljs`

### Adding a New Shader

1. Create `resources/shaders/new_shader.wgsl`
2. Add shader loading in `src/main/gpu.cljs`
3. Add UI controls in `src/renderer/magnification.cljs`

---

## Code Style Guide

### Naming Conventions

- **Namespaces**: `kebab-case` (e.g., `combatsys.breathing-analyzer`)
- **Functions**: `kebab-case` (e.g., `detect-breathing-rate`)
- **Side effects**: append `!` (e.g., `save-session!`)
- **Keywords**: namespaced (e.g., `:session/id`, `:frame/pose`)
- **Constants**: `SCREAMING_SNAKE_CASE` (rare, use sparingly)

### Function Signatures

```clojure
;; Pure function: input → output
(defn process-pose
  "Process raw MediaPipe landmarks into angles and velocities."
  [raw-landmarks]
  ...)

;; Side effect: input → side-effect-description
(defn save-session!
  "Save session to disk. Returns filepath."
  [session]
  ...)
```

### Docstrings

All public functions must have docstrings.

```clojure
(defn analyze
  "Analyze breathing patterns from pose timeline.
  
  Input: timeline (vector of frames)
  Output: breathing-analysis map with :rate-bpm, :fatigue-windows, :insights
  
  Example:
    (def result (analyze timeline))
    (:rate-bpm result)
    ;; => 22.5"
  [timeline]
  ...)
```

---

**Document Status**: LIVING DOCUMENT  
**Next Review**: After each LOD milestone  
**Owner**: Engineering Team
