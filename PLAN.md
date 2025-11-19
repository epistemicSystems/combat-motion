# COMBATSYS MOTION ANALYSIS: DEVELOPMENT PLAN
## LOD-Based Roadmap with Claude Code Tasks

---

## OVERVIEW: THE LOD STRATEGY

Like GPU texture mipmaps, we build at **increasing levels of detail**:

```
LOD 0 (Day 1):   Mock data → Everything connected → IT RUNS
LOD 1 (Days 2-4): Real camera → MediaPipe poses → IT RUNS  
LOD 2 (Days 5-7): Real breathing analysis → IT RUNS
LOD 3 (Days 8-10): Eulerian magnification → IT RUNS
LOD 4 (Days 11-13): Second analyzer (posture) → IT RUNS
LOD 5 (Days 14-16): Personalization → IT RUNS
LOD 6 (Days 17-18): Multi-session analytics → IT RUNS
```

**Every stage produces a shippable artifact.** No "under construction" placeholders.

---

## LOD 0: SYSTEM SKELETON (Day 1, 6-8 hours)

### Goal
Prove the architecture. Everything connected end-to-end with mock data.

### What Works at End of Day 1
- ✅ Electron app launches
- ✅ See mock timeline with 30 frames
- ✅ See skeleton overlay (fake but drawn)
- ✅ See mock breathing rate: "22 bpm"
- ✅ All data flows through EDN IR
- ✅ State management works (single atom)

### Claude Code Tasks

#### Task 1.1: Project Scaffolding (2 hours)
```
FILE: shadow-cljs.edn, package.json, project structure

DESCRIPTION:
Create complete ClojureScript project structure with Electron setup.

DELIVERABLES:
- shadow-cljs.edn with :main and :renderer builds
- package.json with electron and shadow-cljs deps
- src/ directory structure (main/, renderer/, shared/)
- test/ directory structure
- resources/ for shaders and models
- Basic README.md with setup instructions

SUCCESS CRITERIA:
- Run `npm install` → no errors
- Run `npx shadow-cljs watch main renderer` → compiles successfully
- Run `npm start` → Electron window opens (blank is OK)

HINTS:
- Use shadow-cljs :node-script target for main process
- Use shadow-cljs :browser target for renderer
- Set up hot reload for renderer (:after-load hook)
```

#### Task 1.2: EDN Schema Definitions (2 hours)
```
FILE: src/shared/schema.cljs

DESCRIPTION:
Define complete EDN schema using clojure.spec.alpha.
Copy from SPEC.md sections I and II.

DELIVERABLES:
- All spec definitions for session, frame, pose, analysis
- Validation functions: (valid-session? data), etc.
- Generator functions for testing: (gen-session), etc.

SUCCESS CRITERIA:
- (s/valid? ::session (mock-session)) → true
- Can generate random valid data via (s/gen ::session)
- All specs have docstrings

HINTS:
- Use s/keys for maps, s/coll-of for collections
- Use s/double-in for confidence scores (0.0-1.0)
- Add :opt-un for optional keys
```

#### Task 1.3: Mock Data Generators (1 hour)
```
FILE: src/shared/mocks.cljs

DESCRIPTION:
Create mock data generators for testing.

DELIVERABLES:
(defn mock-session [])            → Complete fake session
(defn mock-timeline [n])          → n frames of fake data
(defn mock-breathing-session [bpm duration]) → Breathing session
(defn mock-pose [])               → Single fake pose with 33 landmarks

SUCCESS CRITERIA:
- All mocks conform to schema (validate with spec)
- Mocks are realistic (e.g., landmarks in reasonable positions)
- Can generate variable data (different rates, durations)

HINTS:
- Use predictable patterns (sine waves for breathing)
- Ensure landmarks form plausible human shape
- Add noise for realism but keep it valid
```

#### Task 1.4: Stub Analyzers (1 hour)
```
FILE: src/shared/pose.cljs
FILE: src/shared/breathing.cljs
FILE: src/shared/posture.cljs

DESCRIPTION:
Create stub analyzers that return hardcoded but realistic results.

DELIVERABLES:
(defn combatsys.pose/process-pose [raw-pose])
  → {:angles {:left-elbow 110 ...}}
  
(defn combatsys.breathing/analyze [timeline])
  → {:rate-bpm 22 :depth-score 0.75 :fatigue-windows []}
  
(defn combatsys.posture/analyze [timeline])
  → {:head-forward-cm 4.2 :overall-score 0.84}

SUCCESS CRITERIA:
- All functions compile
- All functions return data conforming to schema
- Docstrings explain they're stubs

HINTS:
- Use (constantly ...) for truly static returns
- Or add minimal randomness for variety
- Mark with ;; STUB: Replace in LOD N
```

#### Task 1.5: Basic UI Scaffold (2 hours)
```
FILE: src/renderer/core.cljs
FILE: src/renderer/views.cljs

DESCRIPTION:
Create basic Reagent UI that displays mock data.

DELIVERABLES:
- Main app component with three views: live-feed, session-browser, analysis
- Timeline viewer (scrollable list of frames)
- Skeleton overlay (canvas with stick figure)
- Metrics panel (displays mock breathing rate)
- Navigation (buttons to switch views)

SUCCESS CRITERIA:
- Electron opens to live-feed view
- Can navigate between views
- Timeline shows 30 mock frames
- Skeleton overlay draws (even if fake)
- Metrics panel shows "Breathing: 22 bpm"

HINTS:
- Use Reagent/React for components
- Use HTML5 canvas for skeleton
- Use simple CSS flexbox for layout
- Don't worry about styling yet (functionality first)
```

#### Task 1.6: State Management (1 hour)
```
FILE: src/renderer/state.cljs
FILE: src/renderer/events.cljs
FILE: src/renderer/subs.cljs

DESCRIPTION:
Set up central state atom with pure update functions.

DELIVERABLES:
- app-state atom with structure from SPEC.md
- State update functions (start-recording, append-frame, etc.)
- re-frame events (:camera/start-recording, etc.)
- re-frame subscriptions (:current-session, :is-recording?, etc.)

SUCCESS CRITERIA:
- (swap! app-state start-recording) updates state correctly
- Events dispatch successfully
- Subscriptions react to state changes
- UI updates when state changes

HINTS:
- Keep update functions pure (input state → output state)
- Use assoc-in/update-in for nested updates
- Test state updates in REPL before wiring to UI
```

### End of LOD 0 Demo
```bash
$ npm start

# Electron window opens
# Shows "Live Feed" view
# Timeline displays 30 mock frames
# Skeleton overlay shows stick figure (fake)
# Metrics panel shows "Breathing: 22 bpm (mock)"
# Can click "Start Recording" (does nothing yet)
# Can switch to "Session Browser" (shows mock sessions)
```

**Success**: The entire pipeline is wired. Now we replace stubs with real implementations.

---

## LOD 1: REAL CAMERA + POSE (Days 2-4, 3 days)

### Goal
Real data flowing through the system. MediaPipe produces actual poses.

### What Works at End of Day 4
- ✅ Live webcam feed visible
- ✅ Real-time skeleton overlay (MediaPipe)
- ✅ Joint angles computed and displayed
- ✅ Recording saves session to disk
- ✅ Playback loads session and replays

### Claude Code Tasks

#### Task 2.1: Camera Integration (8 hours)
```
FILE: src/main/camera.cljs

DESCRIPTION:
Integrate webcam capture using Electron's desktopCapturer or getUserMedia.

DELIVERABLES:
(defn init-camera! [camera-id resolution fps])
  → camera-handle or nil
  
(defn capture-frame! [camera-handle])
  → {:frame-data ArrayBuffer :timestamp-ms int} or nil
  
(defn release-camera! [camera-handle])
  → nil

REQUIREMENTS:
- Detect available cameras
- Configure resolution (default 1920x1080)
- Configure fps (default 30)
- Return frames as ArrayBuffer (image data)
- Handle errors gracefully (log, return nil)

SUCCESS CRITERIA:
- Can list cameras: (list-cameras!)
- Can capture frames at 30fps
- Frames have correct resolution
- No memory leaks (run for 5 minutes, check memory)

HINTS:
- Use Electron's desktopCapturer API
- Or use getUserMedia in renderer, send to main via IPC
- Consider using node-webcam or similar library
- Test on macOS first, then cross-platform
```

#### Task 2.2: MediaPipe Integration (10 hours)
```
FILE: src/main/mediapipe.cljs

DESCRIPTION:
Integrate MediaPipe pose estimation model.

DELIVERABLES:
(defn init-pose-detector! [model-path])
  → detector-handle
  
(defn estimate-pose! [detector-handle frame-data])
  → {:pose {:landmarks [...] :confidence 0.94}} or nil

REQUIREMENTS:
- Use MediaPipe Pose via tfjs-models
- Or use @mediapipe/pose npm package
- Return 33 landmarks in MediaPipe format
- Return world coordinates if available
- Process at 30fps (aim for <30ms per frame)

SUCCESS CRITERIA:
- Pose estimation returns valid landmarks
- Landmarks conform to schema (33 points)
- Performance: <50ms per frame on decent laptop
- Confidence scores are reasonable (>0.8 for clear view)

HINTS:
- Install @mediapipe/pose or @tensorflow-models/pose-detection
- Use "BlazePose" model (balance of speed/accuracy)
- Consider model complexity: Lite vs Full (Lite is faster)
- Profile with console.time/timeEnd
- Can use GPU acceleration via WebGL

EXAMPLE:
const pose = await poseDetector.estimatePoses(imageData);
// Convert to ClojureScript EDN format
```

#### Task 2.3: Pose Processing (6 hours)
```
FILE: src/shared/pose.cljs

DESCRIPTION:
Replace stub with real pose processing functions.

DELIVERABLES:
(defn normalize-landmarks [landmarks])
  → Normalized landmarks (coordinates in [-1, 1])
  
(defn compute-angle [p1 p2 p3])
  → Angle in degrees formed by three points
  
(defn extract-angles [pose])
  → {:left-elbow 110 :right-knee 85 ...}
  
(defn temporal-smooth [poses window-size])
  → Smoothed pose sequence

REQUIREMENTS:
- Pure functions (no side effects)
- Handle missing landmarks (confidence < 0.5)
- Smooth using sliding window average
- Validate output (e.g., elbow angle 0-180°)

SUCCESS CRITERIA:
- (compute-angle [0 0 0] [1 0 0] [1 1 0]) → 90.0
- extract-angles returns all major joint angles
- temporal-smooth reduces jitter in test data
- All functions have unit tests

HINTS:
- Use vector math for angle calculation
- Filter low-confidence landmarks before smoothing
- Consider median filter for outlier rejection
- Test with mock data first, then real poses
```

#### Task 2.4: Session Recording & Playback (8 hours)
```
FILE: src/main/files.cljs

DESCRIPTION:
Save and load sessions from disk.

DELIVERABLES:
(defn save-session! [session output-dir])
  → {:edn-path "..." :video-path "..."}
  
(defn load-session! [session-id data-dir])
  → Full session map with timeline
  
(defn list-sessions! [data-dir])
  → Seq of session metadata

REQUIREMENTS:
- Save session as EDN file: {session-id}.edn
- Optionally save raw video: {session-id}.mp4
- Load session and validate against schema
- Handle missing/corrupted files gracefully

SUCCESS CRITERIA:
- Record 30s session → saved to disk
- Load saved session → timeline has all frames
- EDN file is human-readable (pretty-printed)
- Can list all sessions in directory

HINTS:
- Use clojure.edn/write for saving
- Use clojure.edn/read-string for loading
- For video: use ffmpeg or MediaRecorder API
- Store sessions in ~/CombatSys/sessions/
```

### End of LOD 1 Demo
```bash
$ npm start

# Webcam feed appears (real, not mock)
# Skeleton overlay tracks user in real-time
# Joint angles displayed: "Left elbow: 112°"
# Click "Start Recording" → records 30s
# Session saved to disk
# Switch to "Session Browser"
# See saved session
# Click session → replay with skeleton overlay
```

---

## LOD 2: BREATHING ANALYSIS (Days 5-7, 3 days)

### Goal
Real breathing analysis from torso motion. First vertical slice complete.

### What Works at End of Day 7
- ✅ Record breathing session
- ✅ Offline analysis computes breathing rate
- ✅ Timeline shows breathing waveform
- ✅ Detects fatigue windows (breath holds)
- ✅ Insights generated in coaching language

### Claude Code Tasks

#### Task 3.1: Torso Motion Extraction (6 hours)
```
FILE: src/shared/breathing.cljs

DESCRIPTION:
Extract magnitude of torso landmark motion over time.

DELIVERABLES:
(defn extract-torso-motion [timeline])
  → Vector of motion magnitudes (one per frame)
  
(defn smooth-signal [signal window-size])
  → Smoothed signal

REQUIREMENTS:
- Select torso landmarks: shoulders, chest, upper abdomen
- Compute average motion magnitude per frame
- Smooth with sliding window (reduce noise)
- Handle missing/occluded landmarks

SUCCESS CRITERIA:
- Motion signal correlates with breathing (visual inspection)
- Smooth signal reduces high-frequency noise
- Works on real recorded sessions
- Pure functions with unit tests

HINTS:
- Use landmarks: left-shoulder, right-shoulder, left-hip, right-hip
- Compute frame-to-frame distance: sqrt((x2-x1)² + (y2-y1)²)
- Average across selected landmarks
- Apply moving average or Gaussian filter
```

#### Task 3.2: FFT & Breathing Rate (8 hours)
```
FILE: src/shared/fourier.cljs

DESCRIPTION:
Implement FFT for frequency analysis of breathing signal.

DELIVERABLES:
(defn fft-transform [signal])
  → Frequency domain representation
  
(defn find-peak [freq-domain freq-min freq-max])
  → {:frequency Hz :magnitude float}
  
(defn detect-breathing-rate [signal])
  → Breathing rate in bpm

REQUIREMENTS:
- Implement or wrap FFT library (e.g., fft-js)
- Find dominant frequency in breathing range (0.1-0.5 Hz)
- Convert frequency to bpm: bpm = frequency × 60
- Return confidence based on peak magnitude

SUCCESS CRITERIA:
- Breathing rate within ±2 bpm of manual count
- Detects breathing in 30-60s windows
- Works on various breathing patterns (fast, slow, irregular)
- Unit tests with synthetic signals

HINTS:
- Use fft-js or similar JavaScript library
- Apply windowing (Hann or Hamming) before FFT
- Breathing range: 6-30 bpm (0.1-0.5 Hz)
- Peak magnitude correlates with signal quality
```

#### Task 3.3: Fatigue Window Detection (6 hours)
```
FILE: src/shared/breathing.cljs

DESCRIPTION:
Detect periods where breathing stops or becomes shallow.

DELIVERABLES:
(defn detect-fatigue-windows [signal threshold])
  → [{:start-ms int :end-ms int :severity float}]
  
(defn analyze [timeline])
  → Complete breathing analysis conforming to schema

REQUIREMENTS:
- Detect when signal amplitude drops below threshold
- Merge adjacent low-amplitude periods (within 2s)
- Compute severity: 1.0 = complete stop, 0.5 = shallow
- Generate insights from fatigue windows

SUCCESS CRITERIA:
- Detects intentional breath holds (e.g., user holds for 10s)
- Doesn't false-positive on normal variation
- Severity matches subjective assessment
- Insights are actionable coaching language

HINTS:
- Threshold = mean(signal) * 0.3
- Use hysteresis to avoid flicker (different thresholds for start/end)
- Minimum duration: 2 seconds (avoid noise)
- Generate insight: "Breathing stopped for X seconds during Y"
```

### End of LOD 2 Demo
```bash
# Record 2-minute breathing session (user varies breathing)
# Click "Analyze" → breathing analysis runs
# See results:
#   - Breathing rate: 22 bpm
#   - Depth score: 0.75
#   - Fatigue windows: 2 detected
# Timeline shows breathing waveform overlay
# Insight cards:
#   - "Breathing window: 38 seconds"
#   - "Breath hold detected at 1:15 (4 seconds)"
```

---

## LOD 3: EULERIAN MAGNIFICATION (Days 8-10, 3 days)

### Goal
GPU-accelerated video magnification to visualize subtle motion.

### What Works at End of Day 10
- ✅ Select ROI (chest region) in UI
- ✅ Click "Magnify" → generates magnified video
- ✅ Side-by-side playback: original vs magnified
- ✅ Breathing motion visibly amplified

### Claude Code Tasks

#### Task 4.1: WebGPU Setup (6 hours)
```
FILE: src/renderer/gpu.cljs

DESCRIPTION:
Initialize WebGPU context and setup compute pipeline.

DELIVERABLES:
(defn init-gpu! [])
  → GPU context handle
  
(defn compile-shader! [gpu-context shader-source])
  → Compiled shader handle
  
(defn create-buffer! [gpu-context data])
  → GPU buffer

REQUIREMENTS:
- Check WebGPU support (fallback message if unavailable)
- Create GPU device and queue
- Compile WGSL compute shaders
- Manage buffers (upload/download)

SUCCESS CRITERIA:
- GPU initializes on modern browsers/Electron
- Can compile simple compute shader
- Can upload data to GPU and read back
- No memory leaks (test with repeated uploads)

HINTS:
- Use navigator.gpu.requestAdapter()
- Check for WebGPU feature support
- Use staging buffers for CPU↔GPU transfer
- Profile with performance.now()
```

#### Task 4.2: Eulerian Magnification Shader (10 hours)
```
FILE: resources/shaders/eulerian.wgsl

DESCRIPTION:
Implement Eulerian video magnification as WebGPU compute shader.

ALGORITHM:
1. Spatial filtering (Gaussian pyramid)
2. Temporal filtering (bandpass in breathing freq range)
3. Amplify temporal component
4. Reconstruct and add to original

DELIVERABLES:
WGSL compute shader implementing Eulerian magnification

REQUIREMENTS:
- Input: video frame sequence as GPU texture array
- Parameters: gain (20-30), freq_min (0.1 Hz), freq_max (0.5 Hz)
- Output: magnified frame sequence
- Performance: <2s per second of video on decent GPU

SUCCESS CRITERIA:
- Breathing motion visibly amplified (20-30x)
- No visible artifacts or instability
- Runs in reasonable time (<2 min for 30s video)
- Adjustable parameters (gain, freq range)

HINTS:
- Use separable Gaussian filter for spatial
- Use IIR filter for temporal bandpass
- Clamp amplified values to avoid overflow
- Process in tiles to reduce memory
- Reference: "Eulerian Video Magnification" (MIT, 2012)

PSEUDOCODE:
for each frame:
  1. Gaussian blur(frame) → spatial_filtered
  2. Temporal filter(spatial_filtered) → temporal_component
  3. magnified = frame + gain * temporal_component
  4. clamp(magnified, 0, 255)
```

#### Task 4.3: ROI Selection & Magnification Pipeline (6 hours)
```
FILE: src/renderer/magnification.cljs

DESCRIPTION:
UI for selecting ROI and triggering magnification.

DELIVERABLES:
- Canvas overlay for drawing ROI rectangle
- Button to trigger magnification
- Progress indicator during processing
- Side-by-side video player (original vs magnified)

REQUIREMENTS:
- Let user draw rectangle on first frame
- Pass ROI to GPU shader (crop before processing)
- Show progress (processed frames / total frames)
- Save magnified video to disk

SUCCESS CRITERIA:
- Can select chest area visually
- Magnification completes in <2 min for 30s video
- Magnified video plays smoothly
- Can toggle between original and magnified

HINTS:
- Use mouse events on canvas for ROI drawing
- Convert ROI pixels to texture coordinates
- Process in batches (e.g., 90 frames at once)
- Save as MP4 using MediaRecorder or ffmpeg
```

### End of LOD 3 Demo
```bash
# Load recorded session
# Click "Select ROI"
# Draw rectangle around chest
# Click "Magnify" (gain: 25x, freq: 0.1-0.5 Hz)
# Progress bar: "Processing... 45%"
# After 90 seconds: "Magnification complete"
# Side-by-side player:
#   - Left: Original video
#   - Right: Magnified video (breathing motion exaggerated)
# Breathing motion is CLEARLY visible
```

---

## LOD 4: SECOND ANALYZER — POSTURE (Days 11-13, 3 days)

### Goal
Prove modularity. Add posture analysis without touching breathing code.

### What Works at End of Day 13
- ✅ Posture analysis runs on recorded sessions
- ✅ UI shows posture metrics alongside breathing
- ✅ Insights: "Forward head: 4.2cm"
- ✅ Multiple analyzers coexist peacefully

### Claude Code Tasks

#### Task 5.1: Posture Analyzer Implementation (12 hours)
```
FILE: src/shared/posture.cljs

DESCRIPTION:
Implement posture assessment from pose sequence.

DELIVERABLES:
(defn measure-forward-head [pose])
  → Distance in cm that head is forward of shoulders
  
(defn measure-shoulder-imbalance [pose])
  → Angle difference between left and right shoulders
  
(defn assess-spine-alignment [poses])
  → :neutral | :kyphotic | :lordotic | :scoliotic
  
(defn analyze [timeline])
  → Complete posture analysis conforming to schema

REQUIREMENTS:
- Forward head: horizontal distance (nose to shoulder line)
- Shoulder imbalance: angle between shoulder-hip lines
- Spine alignment: classify based on curve of spine landmarks
- Average over timeline (reduce noise)

SUCCESS CRITERIA:
- Forward head measurement matches manual measurement (±1cm)
- Detects obvious shoulder imbalance (>5° difference)
- Spine classification matches visual inspection
- Works on variety of postures (standing, sitting, moving)

HINTS:
- Use nose, shoulders, hips for forward head
- Project nose onto vertical plane through shoulders
- For spine: fit curve to shoulder-hip-knee landmarks
- For sitting vs standing: detect based on hip-knee-ankle angles
```

#### Task 5.2: Multi-Analysis UI (8 hours)
```
FILE: src/renderer/views.cljs

DESCRIPTION:
Update analysis view to show multiple analyzers.

DELIVERABLES:
- Tabbed or paneled layout for different analyses
- Each analysis has its own metrics panel
- Each analysis has its own insights cards
- Can toggle visibility of different overlays

REQUIREMENTS:
- Breathing tab: rate, depth, fatigue windows
- Posture tab: forward head, shoulders, spine
- Future-proof: easy to add new analyzers
- State management handles multiple analyses

SUCCESS CRITERIA:
- Can switch between breathing and posture tabs
- Each tab shows relevant metrics
- Insights are organized by analyzer
- UI doesn't feel cluttered

HINTS:
- Use Reagent components: [tab-panel "Breathing" [...]]
- Store analyses in session: {:analysis {:breathing {...} :posture {...}}}
- Use CSS grid or flexbox for layout
- Consider accordion or collapsible panels
```

### End of LOD 4 Demo
```bash
# Load session
# Click "Analyze All" → both breathing and posture run
# Switch to "Breathing" tab:
#   - Rate: 22 bpm
#   - Depth: 0.75
#   - Insights: 2 fatigue windows
# Switch to "Posture" tab:
#   - Forward head: 4.2cm
#   - Shoulder imbalance: 3.5°
#   - Spine: neutral
#   - Insight: "Slight forward head posture detected"
```

---

## LOD 5: USER CALIBRATION ✅ **COMPLETE** (Days 14-16, 3 days)

### Goal
Learn from the user. Build personalized thresholds.

### Status
**✅ SHIPPED** - All 6 tasks complete (Tasks 6.1-6.6)
- 4 commits created (local - pending network sync)
- ~24 hours implementation time
- Full personalization system functional

### What Works at End of Day 16
- ✅ Onboarding flow: "Record 3 calibration sessions"
- ✅ System learns user-specific ranges
- ✅ Real-time feedback uses personalized thresholds
- ✅ Thresholds stored in user profile

### Claude Code Tasks

#### Task 6.1: Calibration Flow (10 hours)
```
FILE: src/renderer/calibration.cljs
FILE: src/shared/calibration.cljs

DESCRIPTION:
Guide user through calibration process and extract baseline.

DELIVERABLES:
UI:
- Step 1: Record T-pose (10 seconds)
- Step 2: Record normal breathing (60 seconds)
- Step 3: Record movement sample (60 seconds)

ANALYSIS:
(defn extract-baseline-pose [t-pose-session])
  → Canonical pose with limb lengths
  
(defn compute-rom-ranges [movement-session])
  → {:left-elbow [min max] :right-knee [min max] ...}
  
(defn compute-breathing-baseline [breathing-session])
  → {:typical-rate bpm :typical-depth float}

REQUIREMENTS:
- Guide user through each step with clear instructions
- Validate each recording (e.g., T-pose has arms extended)
- Compute and store baseline in user profile
- Use baseline for future threshold adjustments

SUCCESS CRITERIA:
- User can complete calibration in <5 minutes
- Baseline pose is anatomically valid
- ROM ranges match user's actual flexibility
- Breathing baseline matches manual observation

HINTS:
- Use clear visual cues (stick figure showing T-pose)
- Provide real-time feedback ("Arms extended: ✓")
- Average T-pose over 10 seconds (reduce noise)
- Store in ~/.CombatSys/users/{user-id}/profile.edn
```

#### Task 6.2: Personalized Thresholds (8 hours)
```
FILE: src/shared/thresholds.cljs

DESCRIPTION:
Compute personalized thresholds from calibration data.

DELIVERABLES:
(defn compute-breathing-threshold [baseline-breathing])
  → Threshold for fatigue detection
  
(defn compute-posture-thresholds [baseline-pose rom-ranges])
  → Thresholds for posture alerts
  
(defn apply-personalization [analysis user-profile])
  → Analysis with personalized insights

REQUIREMENTS:
- Breathing: set fatigue threshold relative to typical depth
- Posture: set alert thresholds relative to baseline
- ROM: flag movements beyond typical range
- Adapt over time (incremental updates)

SUCCESS CRITERIA:
- False positive rate drops (fewer spurious alerts)
- True positive rate maintained (still catches real issues)
- Insights reference personal baseline ("15% below your baseline")
- Thresholds evolve as user trains (optional: learning rate)

HINTS:
- Breathing threshold = 0.5 * typical-depth
- Posture threshold = baseline ± 2*stddev
- ROM: flag if > 110% of observed max
- Store thresholds in user profile, update periodically
```

### End of LOD 5 Demo
```bash
# First-time user flow:
# "Welcome! Let's calibrate your baseline."
# Step 1: T-pose for 10 seconds → ✓ Complete
# Step 2: Normal breathing for 60 seconds → ✓ Complete
# Step 3: Movement sample → ✓ Complete
# "Calibration complete! Your baseline is saved."

# Later session:
# Breathing analysis shows: "Rate: 20 bpm (10% below your baseline of 22)"
# Posture shows: "Forward head: 3.1cm (within your typical range of 2.5-4.2cm)"
```

---

## LOD 6: MULTI-SESSION ANALYTICS ✅ **COMPLETE** (Days 17-18, 2 days)

### Goal
Compare sessions, show trends, surface long-term insights.

**✅ SHIPPED** - All 5 tasks complete (Tasks 7.1-7.5)

### What Works at End of Day 18
- ✅ Session browser shows timeline of past sessions
- ✅ Select two sessions → comparison report
- ✅ Trend graphs: breathing rate over last 10 sessions
- ✅ Insights: "You improved by 15%"
- ✅ Fast metadata indexing (<100ms for 100 sessions)
- ✅ Linear regression trend analysis with R² goodness-of-fit
- ✅ Interactive SVG charts with trend lines

### Claude Code Tasks

#### Task 7.1: Session Browser (8 hours)
```
FILE: src/renderer/session_browser.cljs

DESCRIPTION:
UI for browsing and filtering past sessions.

DELIVERABLES:
- List view: all sessions sorted by date
- Filters: date range, analysis type, tags
- Search: by notes or insights
- Session cards with preview and key metrics

REQUIREMENTS:
- Load session metadata (no full timeline yet)
- Sort by date (newest first)
- Click session → load full session for analysis
- Show thumbnails (first frame or key frame)

SUCCESS CRITERIA:
- Can list 100+ sessions without lag
- Can filter by date: "Last 7 days"
- Can search for keyword in notes
- Session cards show summary: date, duration, key metrics

HINTS:
- Load metadata lazily (don't load full timelines)
- Use virtualized list for large session counts
- Store metadata in index file or DB
- Thumbnail: save first frame as thumbnail.jpg during recording
```

#### Task 7.2: Session Comparison (8 hours)
```
FILE: src/shared/comparison.cljs

DESCRIPTION:
Compare two sessions and generate diff report.

DELIVERABLES:
(defn compare-sessions [session-a session-b])
  → {:breathing {:delta-rate-bpm float :improvement? bool}
     :posture {:delta-head-cm float}
     :overall {:trend :improving | :declining | :stable}}
  
(defn compute-trend [sessions])
  → Trend analysis over multiple sessions

REQUIREMENTS:
- Compute deltas for all key metrics
- Classify as improvement, decline, or stable
- Generate insights: "Breathing rate improved by 15%"
- Visualize trends: line graph of metric over time

SUCCESS CRITERIA:
- Comparison report is actionable
- Trends are visually clear (graphs)
- Can compare any two sessions
- Can view trends over last N sessions (5, 10, 20)

HINTS:
- Delta = (session-b-metric - session-a-metric)
- Improvement = delta in desired direction (e.g., higher breathing depth)
- Use chart library: vega-lite or plotly.js
- Store comparison reports for quick access
```

### End of LOD 6 Demo
```bash
# Session browser:
# - List of 20 sessions
# - Filter: "Last 30 days" → shows 15 sessions
# - Click session-001 and session-020
# - "Compare" button appears
# - Click "Compare"

# Comparison report:
# Breathing:
#   - Rate: 20 bpm → 22 bpm (+10%)
#   - Depth: 0.70 → 0.78 (+11%)
#   - ✓ Improved
# Posture:
#   - Forward head: 5.1cm → 4.2cm (-18%)
#   - ✓ Improved
# Overall: ✓ You're improving!

# Trend view:
# Graph showing breathing rate over last 10 sessions
# Upward trend: +12% over 30 days
```

---

## TASK DEPENDENCIES & PARALLELIZATION

### Week 1: Foundation

```
Day 1 (LOD 0): All tasks sequential (setup, schema, mocks, UI)
  1.1 → 1.2 → 1.3 → 1.4 → 1.5 → 1.6

Days 2-4 (LOD 1): Some parallelization possible
  2.1 (Camera) → 2.4 (Recording)
  2.2 (MediaPipe) → 2.3 (Pose) → 2.4 (Recording)
  
  Parallel:
  - 2.1 and 2.2 can start together
  - 2.3 needs 2.2 (MediaPipe format)
  - 2.4 needs 2.3 (processed poses)
```

### Week 2: Analyzers

```
Days 5-7 (LOD 2): Sequential breathing tasks
  3.1 (Torso extraction) → 3.2 (FFT) → 3.3 (Fatigue detection)

Days 8-10 (LOD 3): Magnification
  4.1 (WebGPU setup) → 4.2 (Shader) → 4.3 (UI)

Days 11-13 (LOD 4): Parallel posture + UI
  5.1 (Posture analyzer) ┐
  5.2 (Multi-analysis UI) ┘→ Can work in parallel
```

### Week 3: Personalization & Polish

```
Days 14-16 (LOD 5): Calibration
  6.1 (Calibration flow) → 6.2 (Thresholds)

Days 17-18 (LOD 6): Analytics
  7.1 (Browser) ┐
  7.2 (Comparison) ┘→ Can work in parallel
```

---

## TESTING STRATEGY PER LOD

### LOD 0
- Manual: Does the app start?
- Manual: Can I navigate between views?

### LOD 1
- Manual: Does camera capture work?
- Automated: Do poses conform to schema?
- Manual: Can I record and replay a session?

### LOD 2
- Automated: Does breathing rate match manual count?
- Automated: Are fatigue windows detected correctly?
- Manual: Do insights make sense?

### LOD 3
- Manual: Is breathing motion visibly magnified?
- Performance: Does processing finish in <2 min?

### LOD 4
- Automated: Do both analyzers produce valid output?
- Manual: Is UI organized and usable?

### LOD 5
- Manual: Is calibration flow clear?
- Automated: Do personalized thresholds reduce false positives?

### LOD 6
- Manual: Can I compare two sessions?
- Automated: Are trend calculations correct?

---

## SUCCESS METRICS (OVERALL)

At the end of 18 days:

### Technical
- ✅ All analyzers produce spec-conforming output
- ✅ Real-time path runs at 15+ fps
- ✅ Offline analysis completes in <2 min per minute of footage
- ✅ App doesn't crash during 1-hour session

### User Experience
- ✅ User can record a session without help
- ✅ Analysis results are immediately understandable
- ✅ Insights are actionable (user knows what to improve)
- ✅ No unexpected behavior or UI bugs

### Code Quality
- ✅ 90% of code is pure functions
- ✅ All pure functions have unit tests
- ✅ No warnings during compilation
- ✅ Code follows Clojure idioms (threading, destructuring, etc.)

---

## WHAT COMES AFTER (LOD 7+)

### Week 4: Refinement
- Performance optimization (profile and optimize slowest 5%)
- UI polish (animations, transitions, better layouts)
- Error handling (graceful degradation, helpful error messages)

### Week 5: New Verticals
- Gait analyzer (step detection, symmetry)
- Lifting analyzer (squat depth, bar path)
- Dance analyzer (beat alignment, smoothness)

### Week 6: Advanced Features
- Multi-session montage (show best/worst moments)
- Export reports (PDF, video clips)
- Cloud sync (optional: upload sessions)

---

## DAILY STANDUP FORMAT

Each day, report:

```
DATE: November 17, 2025

COMPLETED (Yesterday):
  ✅ Task 1.1: Project scaffolding
  ✅ Task 1.2: Schema definitions

IN PROGRESS (Today):
  ⏳ Task 1.3: Mock data generators

BLOCKERS:
  ⚠ None currently

NEXT 24 HOURS:
  → Finish mock generators
  → Start stub analyzers

DEMO READY?
  ✓ Yes. Run: npm start
```

---

This plan gives Claude Code a clear roadmap with measurable milestones, concrete tasks, and well-defined success criteria. Each LOD stage is independently shippable, enabling fast iteration and continuous user feedback.

**Start with Task 1.1 when ready.**
