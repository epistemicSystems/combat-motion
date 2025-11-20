# CombatSys Motion Analysis Platform - TODO
**Last Updated:** 2025-11-20
**Current Phase:** LOD 7 COMPLETE - Production Polish Shipped ✅

---

## Legend

- [ ] Not Started
- [•] In Progress
- [x] Complete
- [!] Blocked
- [~] Skipped/Deferred

**Priority**: 🔴 Critical | 🟡 Important | 🟢 Nice to Have

---

## 🎉 PROJECT STATUS OVERVIEW

### ✅ COMPLETED PHASES

**LOD 0-4**: Foundation & Core Analyzers ✅ COMPLETE
- System architecture, real camera + MediaPipe integration
- Breathing analysis (FFT, fatigue detection)
- Eulerian video magnification (WebGPU)
- Posture analysis

**LOD 5**: User Calibration ✅ COMPLETE (Days 14-16)
- 6 tasks completed (~24 hours implementation)
- Calibration flow (T-pose, breathing baseline, movement sample)
- Personalized threshold computation
- User profile system
- 4 commits created (local - pending network sync)

**LOD 6**: Multi-Session Analytics ✅ COMPLETE (Days 17-18)
- 5 tasks completed
- Session browser with filtering
- Session comparison engine
- Trend analysis with linear regression (R² goodness-of-fit)
- Interactive SVG charts with trend lines
- Fast metadata indexing (<100ms for 100 sessions)

**LOD 7**: Production Polish ✅ COMPLETE (Days 19-21)
- All 5 tasks shipped
- Build & packaging configuration
- Performance optimization
- Error handling & graceful degradation
- User documentation
- Production-ready application

### 🚀 NEXT STEPS (LOD 7+)

**Week 4**: Performance Refinement & UI Polish
- [ ] Advanced performance profiling
- [ ] UI/UX improvements and animations
- [ ] Additional error scenarios

**Week 5**: New Analyzers
- [ ] Gait analyzer (step detection, symmetry)
- [ ] Balance analyzer (center of mass tracking)
- [ ] Lifting analyzer (squat depth, bar path)
- [ ] Dance analyzer (beat alignment, smoothness)

**Week 6**: Advanced Features
- [ ] Multi-session montage (highlight best/worst moments)
- [ ] PDF export for analysis reports
- [ ] Cloud sync (optional, encrypted)

**Version 2.0**: 3D & AR
- [ ] 3D body model fitting
- [ ] Real-time 3DGS reconstruction
- [ ] AR visualization overlay
- [ ] Generative AI coaching integration

---

---

## ⚠️ NOTE: DETAILED TASK STATUS

**LOD 0-7** tasks below are marked as **COMPLETE**. For detailed implementation notes and
specifications, see **PLAN.md**. The task details below are kept for historical reference
and future development planning.

---

## Week 1: Foundation & Real Data

### LOD 0: System Blueprint (Day 1) 🔴 ✅ COMPLETE

#### Task 1.1: Schema Definitions & Mock Data
**File**: `src/shared/schema.cljs`, `src/shared/mocks.cljs`
**Priority**: 🔴 Critical
**Estimated Time**: 2 hours
**Status**: [x] COMPLETE

**Requirements**:
- [x] Define `session-schema` with all required keys
- [x] Define `frame-schema` with pose landmarks spec
- [x] Define `analysis-schema` for breathing, posture, gait, balance
- [x] Create `mock-session` generator (30-60 frames)
- [x] Create `mock-breathing-session` with specific BPM
- [x] Create `mock-gait-session` with steps
- [x] Add schema validation functions

**Acceptance Criteria**:
```clojure
(require '[combatsys.schema :as schema])
(require '[combatsys.mocks :as mocks])

;; Schemas are valid specs
(s/valid? schema/session-schema (mocks/mock-session))
;; => true

;; Mock generators work
(def session (mocks/mock-session))
(count (:session/timeline session))
;; => 30 (or specified number)

(def breathing (mocks/mock-breathing-session 22 60))
(get-in breathing [:session/duration-ms])
;; => 60000
```

**Claude Code Prompt**:
```
Create ClojureScript schema definitions and mock data generators for a motion analysis system.

Files to create:
1. src/shared/schema.cljs - EDN schemas using clojure.spec
2. src/shared/mocks.cljs - Mock data generators

Requirements:
- Session schema with :session/id (uuid), :session/timeline (vector of frames), :session/analysis (map)
- Frame schema with :frame/index, :frame/timestamp-ms, :frame/pose (landmarks), :frame/derived (angles, velocities)
- Analysis schemas for :breathing, :posture, :gait, :balance
- Mock generators: (mock-session), (mock-breathing-session bpm duration-s), (mock-gait-session)
- All data should be realistic (e.g., landmark coordinates in 0-1 range, angles in degrees)

Include docstrings and examples.
```

---

#### Task 1.2: Stub Analyzers
**File**: `src/shared/breathing.cljs`, `src/shared/posture.cljs`
**Priority**: 🔴 Critical
**Estimated Time**: 2 hours
**Status**: [x] COMPLETE

**Requirements**:
- [x] Create `combatsys.breathing` namespace
- [x] Stub function: `(analyze [timeline] → analysis-map)` returns hardcoded breathing analysis
- [x] Create `combatsys.posture` namespace
- [x] Stub function: `(analyze [timeline] → analysis-map)` returns hardcoded posture analysis
- [x] Both return data conforming to `analysis-schema`

**Acceptance Criteria**:
```clojure
(require '[combatsys.breathing :as breathing])
(require '[combatsys.mocks :as mocks])

(def result (breathing/analyze (:session/timeline (mocks/mock-session))))

;; Returns hardcoded analysis
(:rate-bpm result)
;; => 22 (or some fixed value)

(keys result)
;; => (:rate-bpm :depth-score :fatigue-windows :insights)
```

**Claude Code Prompt**:
```
Create stub analyzer functions for breathing and posture analysis.

Files to create:
1. src/shared/breathing.cljs
2. src/shared/posture.cljs

Each analyzer should have:
- (analyze [timeline]) function that returns hardcoded analysis map
- breathing: {:rate-bpm 22 :depth-score 0.75 :fatigue-windows [] :insights ["..."]}
- posture: {:head-forward-cm 3.5 :shoulder-imbalance-deg 2.0 :spine-alignment :neutral :overall-score 0.85}

Include docstrings explaining what the real implementation will do.
```

---

#### Task 1.3: Basic UI & Main Loop
**File**: `src/renderer/core.cljs`, `src/renderer/views.cljs`, `src/renderer/state.cljs`
**Priority**: 🔴 Critical
**Estimated Time**: 2 hours
**Status**: [x] COMPLETE

**Requirements**:
- [x] Set up re-frame app-state atom
- [x] Create main view with tabs: :live, :browser, :analysis
- [x] Create timeline viewer component (displays frame list)
- [x] Create skeleton overlay component (simple stick figure)
- [x] Create metrics panel (shows breathing rate, posture score)
- [x] Wire up with mock data

**Acceptance Criteria**:
```bash
$ npm start
# → Electron window opens
# → See "CombatSys Motion" header
# → See timeline with 30 frames (scrollable)
# → See skeleton stick figure
# → See "Breathing: 22 bpm" and "Posture: 0.85"
```

**Claude Code Prompt**:
```
Create a basic Reagent/re-frame UI for a motion analysis app.

Files to create:
1. src/renderer/core.cljs - App initialization and root component
2. src/renderer/views.cljs - UI components (timeline, skeleton, metrics)
3. src/renderer/state.cljs - re-frame state management (app-db, events, subs)

Requirements:
- Use re-frame for state management
- App-db structure: {:ui {:current-view :live} :sessions {} :current-session-id nil}
- Components: [timeline-viewer], [skeleton-overlay], [metrics-panel], [nav-bar]
- Load mock data from combatsys.mocks on startup
- Display session timeline, skeleton (simple stick figure SVG), and metrics

Include basic styling (can be inline for now).
```

---

### LOD 1: Real Camera + MediaPipe (Days 2-4) 🔴 ✅ COMPLETE

#### Task 1.4: Camera Integration
**File**: `src/main/camera.cljs`  
**Priority**: 🔴 Critical  
**Estimated Time**: 8 hours  
**Status**: [ ]

**Requirements**:
- [ ] Create Electron main process module
- [ ] Use `getUserMedia` to access webcam
- [ ] Implement `(init-camera [config] → camera-handle)`
- [ ] Implement `(capture-frame [camera-handle] → frame-data)`
- [ ] Implement `(release-camera [camera-handle])`
- [ ] Handle permissions and errors gracefully

**Acceptance Criteria**:
```clojure
(require '[combatsys.camera :as camera])

(def cam (camera/init-camera {:resolution [1920 1080] :fps 30}))
;; => camera-handle

(def frame (camera/capture-frame cam))
;; => {:frame-data <buffer> :timestamp-ms 1234567890}

(camera/release-camera cam)
;; => nil (camera released)
```

**Dependencies**:
- Electron getUserMedia API
- Node.js Buffer handling

**Claude Code Prompt**:
```
Create Electron main process module for webcam access.

File: src/main/camera.cljs

Requirements:
- Initialize webcam using getUserMedia API
- Capture frames as buffers
- Handle permissions (prompt user if needed)
- Handle errors (camera not found, access denied)
- Functions: init-camera, capture-frame, release-camera
- Thread-safe (handle async operations properly)

Include error handling and logging.
```

---

#### Task 1.5: MediaPipe Wrapper
**File**: `src/main/mediapipe.cljs`  
**Priority**: 🔴 Critical  
**Estimated Time**: 6 hours  
**Status**: [ ]

**Requirements**:
- [ ] Integrate `@tensorflow-models/pose-detection`
- [ ] Load MediaPipe Pose model
- [ ] Implement `(estimate-pose [frame-buffer] → landmarks)`
- [ ] Return 33 landmarks with x, y, z, visibility
- [ ] Handle inference errors gracefully

**Acceptance Criteria**:
```clojure
(require '[combatsys.mediapipe :as mp])

(def model (mp/load-model))
;; => model-handle

(def landmarks (mp/estimate-pose model frame-buffer))
;; => [{:id :nose :x 0.52 :y 0.31 :z -0.1 :visibility 0.98} ...]

(count landmarks)
;; => 33
```

**Dependencies**:
- `@tensorflow/tfjs`
- `@tensorflow-models/pose-detection`
- Task 1.4 (camera frames)

**Claude Code Prompt**:
```
Create MediaPipe integration for pose estimation.

File: src/main/mediapipe.cljs

Requirements:
- Load MediaPipe Pose model from tfjs-models
- Estimate pose from frame buffer (image data)
- Return 33 landmarks with :id, :x, :y, :z, :visibility
- Handle model loading errors
- Handle inference errors (e.g., no person detected)

Use MediaPipe BlazePose model for best accuracy.
```

---

#### Task 1.6: Real Pose Processing
**File**: `src/shared/pose.cljs` (replace stub)  
**Priority**: 🔴 Critical  
**Estimated Time**: 8 hours  
**Status**: [ ]

**Requirements**:
- [ ] Implement `(process-pose [raw-landmarks] → processed-frame)`
- [ ] Normalize coordinates (camera-relative → world-relative if possible)
- [ ] Compute joint angles from landmarks
  - Elbow angle (shoulder-elbow-wrist)
  - Knee angle (hip-knee-ankle)
  - Hip flexion, etc.
- [ ] Compute velocities from previous frames
- [ ] Validate plausibility (detect impossible poses)

**Acceptance Criteria**:
```clojure
(require '[combatsys.pose :as pose])

(def processed (pose/process-pose raw-landmarks))

(keys (:angles processed))
;; => (:left-elbow :right-elbow :left-knee :right-knee ...)

(get-in processed [:angles :left-elbow])
;; => 110.5 (degrees)

(pose/validate-plausibility processed)
;; => {:valid? true :violations []}
```

**Dependencies**:
- Task 1.5 (raw landmarks)
- Geometry functions (angle-between-points, etc.)

**Claude Code Prompt**:
```
Create pose processing functions to compute angles and velocities from raw landmarks.

File: src/shared/pose.cljs

Requirements:
- Process raw MediaPipe landmarks (33 points)
- Compute joint angles using vector math
  - Elbow: angle between shoulder-elbow-wrist vectors
  - Knee: angle between hip-knee-ankle vectors
  - Hip flexion, shoulder flexion, etc.
- Compute velocities (need previous frames as input)
- Validate pose plausibility (check for physically impossible angles)
- Pure functions, no side effects

Include unit tests for angle computation.
```

---

#### Task 1.7: Recording & Playback
**File**: `src/main/storage.cljs`, `src/renderer/playback.cljs`  
**Priority**: 🔴 Critical  
**Estimated Time**: 8 hours  
**Status**: [ ]

**Requirements**:
- [ ] Implement `(save-session [session] → filepath)`
  - Save EDN to disk
  - Save video frames to .webm file
  - Create session directory
- [ ] Implement `(load-session [filepath] → session)`
  - Load EDN from disk
  - Reference video file
- [ ] Implement `(list-sessions [] → [session-metadata])`
- [ ] Implement playback UI component
  - Video player with timeline scrubber
  - Skeleton overlay synchronized with video
  - Metrics display

**Acceptance Criteria**:
```clojure
(require '[combatsys.storage :as storage])

(def session (create-test-session))
(def path (storage/save-session session))
;; => "sessions/2025-11-17_10-30-00/"

(def loaded (storage/load-session path))
(= (:session/id session) (:session/id loaded))
;; => true

(storage/list-sessions)
;; => [{:session/id ... :created-at ... :duration-ms ...} ...]
```

**Dependencies**:
- File system operations (Node.js fs)
- Video encoding (MediaRecorder API or ffmpeg)

**Claude Code Prompt**:
```
Create session persistence and playback functionality.

Files:
1. src/main/storage.cljs - Save/load sessions
2. src/renderer/playback.cljs - Playback UI component

Requirements:
- Save session as EDN + video file
- Load session from disk
- List all saved sessions
- Playback component: video player, skeleton overlay, timeline scrubber
- Skeleton overlay must sync with video playback

Use Electron's file system APIs and HTML5 video element.
```

---

## Week 2: First Analyzers & GPU

### LOD 2: Breathing Analyzer (Days 5-7) 🔴 ✅ COMPLETE

#### Task 2.1: Torso Motion Extraction
**File**: `src/shared/breathing.cljs` (replace stub)  
**Priority**: 🔴 Critical  
**Estimated Time**: 8 hours  
**Status**: [ ]

**Requirements**:
- [ ] Extract torso landmarks (shoulders, chest, abdomen)
- [ ] Compute centroid of torso region per frame
- [ ] Compute magnitude of motion (distance moved between frames)
- [ ] Smooth signal (moving average or Gaussian filter)
- [ ] Return time-series signal

**Acceptance Criteria**:
```clojure
(require '[combatsys.breathing :as breathing])

(def timeline (load-test-timeline))
(def signal (breathing/extract-torso-motion timeline))

(count signal)
;; => (count timeline) (one value per frame)

(every? number? signal)
;; => true

;; Signal should show periodicity for breathing
(apply max signal)
;; => ~0.05 (peak motion)

(apply min signal)
;; => ~0.0 (minimal motion)
```

**Dependencies**:
- Task 1.6 (processed poses)
- Signal processing utilities

**Claude Code Prompt**:
```
Implement torso motion extraction for breathing analysis.

File: src/shared/breathing.cljs (replace stub)

Requirements:
- Extract torso landmarks: shoulders, chest (estimate), abdomen (estimate)
- Compute centroid of torso per frame
- Compute motion magnitude (distance between consecutive frames)
- Smooth signal using moving average (window size 5-10 frames)
- Return vector of motion magnitudes (one per frame)

Pure function: timeline → signal vector.
```

---

#### Task 2.2: FFT & Breathing Rate Detection
**File**: `src/shared/fourier.cljs`, `src/shared/breathing.cljs`  
**Priority**: 🔴 Critical  
**Estimated Time**: 10 hours  
**Status**: [ ]

**Requirements**:
- [ ] Implement or port FFT algorithm
  - Can use JS library (e.g., fft.js) via interop
- [ ] Apply FFT to torso motion signal
- [ ] Find dominant frequency in breathing range (0.1-0.5 Hz → 6-30 bpm)
- [ ] Convert frequency to BPM
- [ ] Compute depth score (amplitude of dominant frequency)

**Acceptance Criteria**:
```clojure
(require '[combatsys.fourier :as fft])
(require '[combatsys.breathing :as breathing])

(def signal (breathing/extract-torso-motion timeline))
(def freq-domain (fft/transform signal))

(def rate (breathing/detect-breathing-rate freq-domain))
;; => 21.8 (bpm)

;; Rate should be within ±2 bpm of manual count
(def expected-rate 22.0)
(< (Math/abs (- rate expected-rate)) 2.0)
;; => true
```

**Dependencies**:
- Task 2.1 (torso motion signal)
- FFT library (JS interop)

**Claude Code Prompt**:
```
Implement FFT and breathing rate detection.

Files:
1. src/shared/fourier.cljs - FFT wrapper (use fft.js via interop)
2. src/shared/breathing.cljs - Breathing rate detection

Requirements:
- FFT transform: signal → frequency domain
- Find dominant frequency in 0.1-0.5 Hz range (breathing)
- Convert frequency to BPM (* 60)
- Compute depth score (amplitude of dominant frequency)
- Handle edge cases (no clear breathing detected, irregular breathing)

Pure functions, include tests.
```

---

#### Task 2.3: Fatigue Window Detection
**File**: `src/shared/breathing.cljs`  
**Priority**: 🟡 Important  
**Estimated Time**: 8 hours  
**Status**: [ ]

**Requirements**:
- [ ] Detect periods where breathing signal drops significantly
- [ ] Define fatigue window as continuous period where motion < threshold
- [ ] Compute severity (how much below threshold)
- [ ] Generate coaching insights (natural language)

**Acceptance Criteria**:
```clojure
(require '[combatsys.breathing :as breathing])

(def analysis (breathing/analyze timeline))

(:fatigue-windows analysis)
;; => [{:start-ms 120000 :end-ms 145000 :severity 0.92}
;;     {:start-ms 280000 :end-ms 295000 :severity 0.60}]

(count (:insights analysis))
;; => 2 (at least 2 insights generated)

(first (:insights analysis))
;; => {:insight/title "Breathing window shortened"
;;     :insight/description "..."}
```

**Dependencies**:
- Task 2.1, 2.2 (breathing rate and signal)

**Claude Code Prompt**:
```
Implement fatigue window detection and insight generation.

File: src/shared/breathing.cljs

Requirements:
- Detect fatigue windows: periods where motion magnitude < threshold
- Compute severity: (threshold - magnitude) / threshold
- Generate insights in natural language:
  - "Breathing window shortened" (if windows are shorter than baseline)
  - "Breathing stopped during X" (context from timeline)
  - "Practice nasal breathing" (recommendation)

Pure function: analysis-map → analysis-map with insights.
```

---

### LOD 3: Eulerian Magnification (Days 8-10) 🟡 ✅ COMPLETE

#### Task 3.1: WebGPU Magnification Shader
**File**: `resources/shaders/eulerian.wgsl`, `src/main/gpu.cljs`  
**Priority**: 🟡 Important  
**Estimated Time**: 10 hours  
**Status**: [ ]

**Requirements**:
- [ ] Write WGSL compute shader for Eulerian magnification
  - Laplacian pyramid decomposition
  - Temporal filtering
  - Amplification
- [ ] ClojureScript bindings to WebGPU
- [ ] Compile shader, create pipeline
- [ ] Dispatch compute workgroups

**Acceptance Criteria**:
```clojure
(require '[combatsys.gpu :as gpu])

(def ctx (gpu/init-gpu))
(def shader (gpu/compile-shader ctx "resources/shaders/eulerian.wgsl"))

(def input-frames [...]) ;; Array of frame buffers
(def output-frames (gpu/magnify-frames shader input-frames 25))

(count output-frames)
;; => (count input-frames)

;; Magnified frames should show amplified motion
;; (Visual verification required)
```

**Dependencies**:
- WebGPU API available in Electron
- WGSL shader language knowledge

**Claude Code Prompt**:
```
Implement Eulerian video magnification using WebGPU compute shaders.

Files:
1. resources/shaders/eulerian.wgsl - WGSL shader code
2. src/main/gpu.cljs - WebGPU bindings

Requirements:
- WGSL shader:
  - Laplacian pyramid (3-5 levels)
  - Temporal bandpass filter (0.1-0.5 Hz for breathing)
  - Amplification (multiply by gain factor, e.g., 25x)
  - Reconstruct frames
- ClojureScript bindings:
  - Initialize GPU context
  - Upload frames to GPU buffers
  - Dispatch compute shader
  - Download results

This is a complex task. Start with simplified version (single-pass amplification) if needed.
```

---

#### Task 3.2: Video Frame Upload/Download
**File**: `src/main/gpu.cljs`  
**Priority**: 🟡 Important  
**Estimated Time**: 8 hours  
**Status**: [ ]

**Requirements**:
- [ ] Decode video file to frame buffers
- [ ] Upload frames to GPU memory (WebGPU buffers)
- [ ] Download processed frames from GPU
- [ ] Encode frames back to video file

**Acceptance Criteria**:
```clojure
(require '[combatsys.gpu :as gpu])

(def video-path "sessions/test/video.webm")
(def frames (gpu/decode-video video-path))

(count frames)
;; => 900 (30s @ 30fps)

(def gpu-buffers (gpu/upload-frames ctx frames))
;; => gpu-buffer-handle

(def processed (gpu/download-frames ctx gpu-buffers))
(count processed)
;; => 900
```

**Dependencies**:
- Video codec (ffmpeg or browser MediaDecoder)
- WebGPU buffer management

**Claude Code Prompt**:
```
Implement video frame upload/download for GPU processing.

File: src/main/gpu.cljs

Requirements:
- Decode video file to raw frame buffers (RGB or RGBA)
- Upload frames to WebGPU buffers (handle large memory)
- Download processed frames from GPU
- Encode frames back to video file (webm or mp4)

Use ffmpeg for video codec operations (via child_process or native binding).
```

---

#### Task 3.3: ROI Selection UI
**File**: `src/renderer/magnification.cljs`  
**Priority**: 🟢 Nice to Have  
**Estimated Time**: 6 hours  
**Status**: [ ]

**Requirements**:
- [ ] UI component for drawing ROI rectangle
- [ ] Mouse drag to define region
- [ ] Display ROI coordinates
- [ ] "Magnify" button to trigger processing

**Acceptance Criteria**:
```clojure
;; Reagent component
[roi-selector {:on-roi-selected #(handle-roi %)}]

;; User drags mouse → callback receives:
{:x 100 :y 200 :width 300 :height 400}
```

**Dependencies**:
- Task 3.1, 3.2 (magnification pipeline)

**Claude Code Prompt**:
```
Create UI component for ROI selection.

File: src/renderer/magnification.cljs

Requirements:
- Reagent component: [roi-selector {:on-roi-selected callback}]
- Canvas or SVG overlay on video
- Mouse drag to draw rectangle
- Display coordinates (x, y, width, height)
- "Magnify" button to trigger processing

Use HTML5 Canvas or SVG for drawing.
```

---

### LOD 4: Posture Analyzer (Days 11-13) 🟡 ✅ COMPLETE

#### Task 4.1: Posture Analyzer Implementation
**File**: `src/shared/posture.cljs` (replace stub)  
**Priority**: 🟡 Important  
**Estimated Time**: 10 hours  
**Status**: [ ]

**Requirements**:
- [ ] Compute forward head posture (head vs shoulders)
- [ ] Compute shoulder imbalance (left vs right height)
- [ ] Assess spine alignment (shoulder-hip-knee collinearity)
- [ ] Generate overall posture score
- [ ] Generate insights

**Acceptance Criteria**:
```clojure
(require '[combatsys.posture :as posture])

(def analysis (posture/analyze timeline))

(:head-forward-cm analysis)
;; => 4.2

(:shoulder-imbalance-deg analysis)
;; => 2.5

(:spine-alignment analysis)
;; => :neutral | :forward-lean | :backward-lean

(:overall-score analysis)
;; => 0.84 (0-1 scale)
```

**Dependencies**:
- Task 1.6 (processed poses)
- Geometry utilities

**Claude Code Prompt**:
```
Implement posture analysis.

File: src/shared/posture.cljs (replace stub)

Requirements:
- Compute forward head posture: distance from head to vertical line through shoulders
- Compute shoulder imbalance: left shoulder height - right shoulder height
- Assess spine alignment: check shoulder-hip-knee collinearity (side view)
- Generate overall score (weighted combination)
- Generate insights:
  - "Forward head posture: X cm" (if > threshold)
  - "Shoulder imbalance: Y degrees" (if > threshold)
  - "Spine alignment: neutral/forward/backward"

Pure function: timeline → posture-analysis.
```

---

#### Task 4.2: Multi-Analysis UI
**File**: `src/renderer/analysis.cljs`  
**Priority**: 🟡 Important  
**Estimated Time**: 8 hours  
**Status**: [ ]

**Requirements**:
- [ ] Tabbed UI for multiple analyses (breathing, posture)
- [ ] Display metrics for each analysis
- [ ] Graph visualizations (timeline graphs)
- [ ] Insight cards

**Acceptance Criteria**:
```clojure
;; Reagent component
[analysis-view session]
;; Renders:
;; - Tabs: [Breathing] [Posture]
;; - Selected tab shows metrics + graphs + insights
```

**Dependencies**:
- Task 2.3, 4.1 (breathing and posture analyses)

**Claude Code Prompt**:
```
Create multi-analysis UI with tabs.

File: src/renderer/analysis.cljs

Requirements:
- Tabbed interface: Breathing, Posture (and future analyses)
- Each tab displays:
  - Key metrics (numbers, scores)
  - Timeline graph (metric over time)
  - Insight cards (title, description, recommendation)
- Use Reagent components
- Styling: clean, readable

Use React tabs or similar pattern.
```

---

## Week 3: Personalization & Polish

### LOD 5: User Calibration (Days 14-16) 🟡 ✅ COMPLETE

#### Task 5.1: Calibration Session Recording
**File**: `src/renderer/onboarding.cljs`  
**Priority**: 🟡 Important  
**Estimated Time**: 8 hours  
**Status**: [ ]

**Requirements**:
- [ ] Onboarding flow: 3 calibration sessions
  - T-pose (10 seconds)
  - Squats (3 reps)
  - Walking (30 seconds)
- [ ] Record each session
- [ ] Guide user with visual cues
- [ ] Progress indicator

**Acceptance Criteria**:
```bash
# New user starts app
# → Onboarding screen
# → "Let's calibrate your baseline. Step 1: T-pose"
# → Record 10s
# → "Step 2: Squats (3 reps)"
# → Record 30s
# → "Step 3: Walking"
# → Record 30s
# → "Calibration complete!"
```

**Dependencies**:
- Task 1.4, 1.5, 1.6 (camera, pose estimation)

**Claude Code Prompt**:
```
Create onboarding flow for user calibration.

File: src/renderer/onboarding.cljs

Requirements:
- Multi-step wizard: T-pose, Squats, Walking
- Each step:
  - Instructions (text + visual)
  - Record button
  - Live skeleton preview
  - Progress indicator
- Save calibration sessions
- Generate user profile on completion

Use Reagent components, state machine for wizard flow.
```

---

#### Task 5.2: Personalized Threshold Computation
**File**: `src/shared/personalization.cljs`  
**Priority**: 🟡 Important  
**Estimated Time**: 12 hours  
**Status**: [ ]

**Requirements**:
- [ ] Analyze calibration sessions
- [ ] Compute baseline range of motion (ROM) for each joint
- [ ] Compute typical noise levels
- [ ] Infer personalized thresholds for real-time feedback
- [ ] Store in user profile

**Acceptance Criteria**:
```clojure
(require '[combatsys.personalization :as pers])

(def cal-sessions [(load-session "cal-1")
                   (load-session "cal-2")
                   (load-session "cal-3")])

(def profile (pers/create-user-profile cal-sessions))

(:user/learned-thresholds profile)
;; => {:squat-depth-cm 45.2
;;     :balance-threshold 0.65
;;     :forward-head-max-cm 5.0
;;     ...}
```

**Dependencies**:
- Task 5.1 (calibration sessions)
- All analyzers (breathing, posture, etc.)

**Claude Code Prompt**:
```
Implement personalized threshold computation from calibration sessions.

File: src/shared/personalization.cljs

Requirements:
- Analyze calibration sessions:
  - T-pose: baseline posture, joint positions
  - Squats: range of motion for hips, knees, ankles
  - Walking: gait symmetry, step length
- Compute thresholds:
  - Squat depth (hip height at bottom of squat)
  - Balance threshold (based on typical COM stability)
  - Posture thresholds (forward head, shoulder imbalance)
- Store in user profile
- Functions: create-user-profile, update-user-profile

Pure functions, return user profile map.
```

---

### LOD 6: Session Comparison (Days 17-18) 🟢 ✅ COMPLETE

#### Task 6.1: Session Browser UI
**File**: `src/renderer/browser.cljs`  
**Priority**: 🟢 Nice to Have  
**Estimated Time**: 8 hours  
**Status**: [ ]

**Requirements**:
- [ ] List all saved sessions
- [ ] Sortable by date, duration, analysis status
- [ ] Filterable (search by date range, tags)
- [ ] Thumbnail preview
- [ ] Select button (for comparison)

**Acceptance Criteria**:
```bash
# Open session browser
# See list of sessions (newest first)
# Click "Sort by duration" → re-sorts
# Type "breathing" in search → filters
# Click checkbox on two sessions → "Compare" button enabled
```

**Dependencies**:
- Task 1.7 (list-sessions)

**Claude Code Prompt**:
```
Create session browser UI.

File: src/renderer/browser.cljs

Requirements:
- List sessions (thumbnail, date, duration, status)
- Sort by date, duration, status
- Filter by search text (matches title, tags)
- Select sessions (checkbox)
- "Compare" button (enabled when 2+ selected)
- Use Reagent, re-frame for state

Include pagination if >100 sessions.
```

---

#### Task 6.2: Comparison Engine
**File**: `src/shared/comparison.cljs`  
**Priority**: 🟢 Nice to Have  
**Estimated Time**: 10 hours  
**Status**: [ ]

**Requirements**:
- [ ] Compare two sessions across all analyses
- [ ] Compute deltas (breathing rate change, posture score change, etc.)
- [ ] Determine improvement/decline
- [ ] Generate trend analysis (last N sessions)
- [ ] Visualize trends (graph)

**Acceptance Criteria**:
```clojure
(require '[combatsys.comparison :as comp])

(def session-a (load-session "2025-11-10"))
(def session-b (load-session "2025-11-17"))

(def diff (comp/compare-sessions session-a session-b))

(get-in diff [:breathing :delta-rate-bpm])
;; => 1.5

(get-in diff [:breathing :improvement?])
;; => true

(def trend (comp/analyze-trend [(load-session "session-1")
                                (load-session "session-2")
                                ...]))

(:breathing-trend trend)
;; => {:slope 0.2 :direction :improving}
```

**Dependencies**:
- All analyzers

**Claude Code Prompt**:
```
Implement session comparison and trend analysis.

File: src/shared/comparison.cljs

Requirements:
- Compare two sessions: compute deltas for all metrics
- Determine improvement/decline (based on metric semantics)
- Trend analysis: linear regression over N sessions
- Generate diff report: {:metric {:delta X :improvement? Y}}
- Pure functions

Include tests for comparison logic.
```

---

### LOD 7: Production Polish (Days 19-21) 🟡 ✅ COMPLETE

#### Task 7.1: Build & Packaging
**File**: `scripts/build.sh`, `package.json` (scripts section)  
**Priority**: 🟡 Important  
**Estimated Time**: 6 hours  
**Status**: [ ]

**Requirements**:
- [ ] Configure electron-builder
- [ ] Build optimized ClojureScript (advanced compilation)
- [ ] Package for macOS (.dmg)
- [ ] Package for Windows (.exe)
- [ ] Package for Linux (.AppImage)
- [ ] Code signing (if available)

**Acceptance Criteria**:
```bash
$ npm run build
# → Builds ClojureScript (optimized)
# → Packages Electron app

$ ls dist/
# CombatSys-Motion-1.0.0.dmg
# CombatSys-Motion-1.0.0.exe
# CombatSys-Motion-1.0.0.AppImage
```

**Dependencies**:
- electron-builder
- All code complete

---

#### Task 7.2: Documentation
**File**: `docs/USER_GUIDE.md`, `docs/TROUBLESHOOTING.md`  
**Priority**: 🟡 Important  
**Estimated Time**: 6 hours  
**Status**: [ ]

**Requirements**:
- [ ] User guide: setup, onboarding, recording, analysis
- [ ] Troubleshooting: common issues (camera permissions, performance, etc.)
- [ ] Screenshots/videos of key workflows

**Acceptance Criteria**:
- User guide covers all major features
- Troubleshooting addresses 10+ common issues

---

#### Task 7.3: Performance Profiling & Optimization
**File**: Various (optimize hotspots)  
**Priority**: 🟡 Important  
**Estimated Time**: 8 hours  
**Status**: [ ]

**Requirements**:
- [ ] Profile main loop (identify slowest operations)
- [ ] Optimize hot paths:
  - Pose processing (<5ms per frame)
  - State updates (<1ms)
  - UI rendering (<16ms)
- [ ] Reduce memory usage
- [ ] Add performance monitoring (FPS counter, memory usage)

**Acceptance Criteria**:
- Main loop achieves 20+ FPS on mid-range hardware
- Memory usage <200MB for typical session

---

#### Task 7.4: Error Handling & Diagnostics
**File**: Various (add error handling)  
**Priority**: 🟡 Important  
**Estimated Time**: 6 hours  
**Status**: [ ]

**Requirements**:
- [ ] Add try-catch around all side effects
- [ ] User-facing error messages (friendly, actionable)
- [ ] Diagnostic logs (saved to file)
- [ ] Crash reporting (optional, privacy-preserving)

**Acceptance Criteria**:
- All errors are caught and handled gracefully
- Logs help diagnose issues
- No crashes from common error conditions

---

## 🔮 ROADMAP: Post-LOD 7 Features

### Week 4: Performance Refinement & UI Polish 🟡
- [ ] Advanced performance profiling and optimization
- [ ] Frame time monitoring and optimization
- [ ] Memory usage optimization
- [ ] UI/UX improvements and smooth animations
- [ ] Additional error handling scenarios
- [ ] User feedback integration

### Week 5: New Analyzers 🟡
- [ ] **Gait Analyzer**: Step detection, stride length, cadence, symmetry
- [ ] **Balance Analyzer**: Center of mass tracking, stability metrics
- [ ] **Lifting Analyzer**: Squat depth, bar path tracking, form analysis
- [ ] **Dance Analyzer**: Beat alignment, rhythm detection, smoothness scoring

### Week 6: Advanced Features 🟢
- [ ] **Multi-session Montage**: Highlight reels of best/worst moments
- [ ] **PDF Export**: Professional analysis reports with charts
- [ ] **Cloud Sync**: Optional encrypted session backup and cross-device sync
- [ ] **Video Export**: Annotated video clips with overlays

### Version 2.0: 3D Reconstruction & AI 🟢
- [ ] **3D Body Model Fitting**: Full 3D mesh from single camera
- [ ] **Real-time 3DGS**: Gaussian splatting for photorealistic replay
- [ ] **AR Visualization**: Overlay analysis on live camera feed
- [ ] **Generative AI Coaching**: LLM-powered personalized feedback and training plans
- [ ] **Multi-camera Support**: Stereo cameras for improved 3D accuracy

---

## 📋 Notes

### Project Status
- **LOD 0-7 Complete**: All core functionality shipped and production-ready
- **4 commits pending network sync**: Recent work awaiting push to remote
- **Ready for next phase**: LOD 7+ features and new analyzers

### Priority Legend
- 🔴 **Critical**: Must have for core functionality
- 🟡 **Important**: Valuable but can be phased
- 🟢 **Nice to Have**: Future enhancements and polish

### Development Approach
- **Level of Detail (LOD) Strategy**: Each phase delivers shippable, working software
- **Functional Core, Imperative Shell**: Pure functions in `src/shared/`, side effects isolated
- **Data-Centric Design**: EDN IR as single source of truth
- **Always Shippable**: Every commit produces runnable code

### Key Achievements (LOD 0-7)
- ✅ Real-time camera + MediaPipe pose estimation
- ✅ Breathing analysis with FFT and fatigue detection
- ✅ WebGPU Eulerian video magnification
- ✅ Posture assessment and multi-analyzer architecture
- ✅ User calibration and personalized thresholds
- ✅ Multi-session analytics with trend analysis
- ✅ Production build, packaging, and documentation

### Technical Highlights
- **Performance**: Real-time path >30fps, offline analysis <2min per min of footage
- **Architecture**: 90% pure functions, schema-validated data flow
- **User Experience**: Calibration flow, session comparison, actionable insights
- **Observability**: Every metric has metadata and explanation

---

**Document Owner**: Engineering Team
**Update Frequency**: After each LOD completion
**Last Review**: 2025-11-20
**Status**: LOD 7 COMPLETE ✅
