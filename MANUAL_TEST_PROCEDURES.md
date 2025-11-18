# LOD 3: EULERIAN VIDEO MAGNIFICATION - MANUAL TEST PROCEDURES

**Document Version**: 1.0
**Last Updated**: 2025-11-18
**Feature**: GPU-Accelerated Breathing Motion Magnification
**Status**: Ready for Testing

---

## 📋 OVERVIEW

This document provides step-by-step procedures for manually testing the Eulerian Video Magnification feature (LOD 3). Follow these procedures to verify that the implementation works correctly and meets acceptance criteria.

**What This Feature Does:**
- Amplifies subtle breathing motion by 20-30× to make it clearly visible
- Uses GPU-accelerated WebGPU compute shaders for fast processing
- Provides side-by-side comparison of original vs. magnified video

**Expected User Experience:**
1. Load a recorded breathing session (with video)
2. Select chest region with mouse drag (ROI)
3. Click "Start Magnification" button
4. Watch progress indicator (0-100%)
5. View side-by-side comparison with playback controls
6. **"Aha!" Moment**: User clearly sees breathing motion that was invisible before

---

## 🔧 PREREQUISITES

### System Requirements

**WebGPU Support:**
- Chrome/Edge 113+ OR
- Electron 25+ (Chromium 114+)
- Check support: Navigate to `chrome://gpu` and verify "WebGPU: Hardware accelerated"

**Test Data:**
- Recorded breathing session with video file (WebM or MP4)
- Video should show torso/chest area
- Duration: 30-60 seconds recommended for first test
- Resolution: 512×512 or higher

**How to Create Test Video:**
1. Run CombatSys application
2. Start new session recording
3. Stand in front of camera (torso visible)
4. Breathe normally for 60 seconds
5. Stop recording
6. Verify session has `:session/video-path` field

---

## 🧪 TEST PROCEDURE 1: BASIC WORKFLOW

### Objective
Verify end-to-end workflow from session load to magnified playback.

### Steps

#### 1. Launch Application

```bash
cd /path/to/combat-motion
npm start
```

**Expected Result:**
- Application window opens
- Main UI displays without errors
- Check browser console for initialization messages

---

#### 2. Load Session with Video

**Action:**
1. Navigate to "Session Browser" view
2. Select a saved session from the list
3. Verify session has video path: `(:session/video-path session)`
4. Click session to load it

**Expected Result:**
- Session loads successfully
- Timeline displays with frames
- Session metadata shows video path
- Console log: `"Session loaded: <session-id>"`

**Verification:**
```clojure
;; In REPL:
(require '[re-frame.core :as rf])
@(rf/subscribe [:combatsys.renderer.state/current-session])
;; Should show session map with :session/video-path key
```

**Troubleshooting:**
- **Session has no video**: Record a new session with video enabled
- **Video path is nil**: Check that recording includes video capture
- **Session not found**: Verify session was saved to disk with `files/list-session-ids!`

---

#### 3. Navigate to Magnification View

**Action:**
1. Click "Magnification" tab/button in UI
2. OR dispatch event: `(rf/dispatch [:combatsys.renderer.state/set-view :magnification])`

**Expected Result:**
- Magnification view renders
- Title: "Eulerian Video Magnification"
- Description: "Amplify subtle breathing motion to make it visible."
- "Load Video" button visible

**Console Output:**
```
Navigating to magnification view...
```

---

#### 4. Load First Frame for ROI Selection

**Action:**
1. Click "📹 Load Video" button
2. Wait for first frame to load

**Expected Result:**
- Button changes state (loading indicator or disabled)
- First frame appears on canvas after 1-2 seconds
- Canvas shows clear image of recorded video
- Canvas has crosshair cursor

**Console Output:**
```
Loading first frame for ROI selection...
First frame loaded: 640 × 480
```

**Verification:**
```clojure
;; In REPL:
@(rf/subscribe [:combatsys.renderer.state/magnification/first-frame])
;; Should return: {:frame #js[Uint8ClampedArray] :width 640 :height 480}
```

**Troubleshooting:**
- **Frame doesn't load**: Check video path is valid, video file exists
- **Canvas is blank**: Check browser console for video decode errors
- **"Failed to load video" error**: Verify video format (WebM/MP4), check file permissions

---

#### 5. Select Region of Interest (ROI)

**Action:**
1. Position mouse over chest area in video frame
2. Click and drag to draw rectangle around torso/chest
3. Release mouse button

**Visual Guidance:**
- **Good ROI**: Covers upper chest and lower neck (where breathing motion is visible)
- **ROI Size**: At least 200×200 pixels, centered on sternum
- **Avoid**: Including face, arms, or background

**Expected Result:**
- Green rectangle appears while dragging
- Rectangle follows mouse cursor
- On release, final ROI rectangle drawn with thicker border (3px)
- ROI info displays below canvas: `"ROI: 150, 200 | Size: 250 × 300"`
- "Start Magnification" button becomes visible

**Console Output:**
```
ROI selected: {:x 150, :y 200, :width 250, :height 300}
```

**Verification:**
```clojure
@(rf/subscribe [:combatsys.renderer.state/magnification/roi])
;; Should return: {:x 150 :y 200 :width 250 :height 300}
```

**Troubleshooting:**
- **Rectangle not drawing**: Check mouse events are firing (console.log in handlers)
- **Rectangle jumps around**: Canvas position calculation issue, check `.getBoundingClientRect`
- **ROI not saved**: Verify dispatch `::magnification/roi-selected` fires on mouse-up

---

#### 6. Start Magnification Processing

**Action:**
1. Click "🔬 Start Magnification" button
2. Observe progress indicator

**Expected Result:**
- Button becomes disabled (grayed out)
- Progress indicator appears with green gradient bar
- Progress text starts at "0%"
- Initial status: "Decoding video..."

**Console Output (Initial):**
```
Starting magnification pipeline...
GPU initialization...
WebGPU ready
Decoding video...
Video metadata: Size: 640 × 480 Duration: 60 s Extracting 900 frames @ 15 fps
```

**Timing Expectations:**

| Phase | Duration | Progress Range | Status Message |
|-------|----------|----------------|----------------|
| GPU Init | 0.5-1s | 0% | "Starting..." |
| Video Decode | 5-15s | 0-50% | "Decoding video..." |
| Frame Magnification | 30-90s | 50-100% | "Magnifying frames..." |
| **Total** | **35-106s** | **0-100%** | "Complete!" |

**Progress Updates:**
- Progress bar animates smoothly
- Percentage updates every 100 frames
- Console logs frame processing: `"Processing frame 100 / 900"`

**Verification:**
```clojure
;; While processing:
@(rf/subscribe [:combatsys.renderer.state/magnification/processing?])
;; Should return: true

@(rf/subscribe [:combatsys.renderer.state/magnification/progress])
;; Should return: 0.0 → 0.5 → 1.0
```

**Troubleshooting:**
- **GPU init fails**: Check WebGPU support at `chrome://gpu`, update browser/Electron
- **Decode hangs**: Video format may be unsupported, try WebM instead of MP4
- **Processing very slow (>5 minutes)**: GPU may not be hardware-accelerated, check GPU settings
- **"GPU init failed" error**: WebGPU not available, see Prerequisites section

---

#### 7. Processing Complete - View Results

**Expected Result (After Processing):**
- Progress bar reaches 100%
- Progress indicator disappears
- "Results" section appears with title
- Side-by-side video player displays two canvases
- Playback controls visible:
  - Play/Pause button
  - View mode dropdown (Original Only / Magnified Only / Side-by-Side)
  - Frame slider (0 to frame count)
  - Frame counter display

**Console Output (Completion):**
```
Decoded 900 frames
Computing temporal mean...
Processing frame 0 / 900
Processing frame 100 / 900
...
Processing frame 800 / 900
Magnification complete!
Magnification complete with 900 frames
```

**Verification:**
```clojure
@(rf/subscribe [:combatsys.renderer.state/magnification/processing?])
;; Should return: false

@(rf/subscribe [:combatsys.renderer.state/magnification/magnified-frames])
;; Should return: Vector of 900 Uint8ClampedArrays

@(rf/subscribe [:combatsys.renderer.state/magnification/metadata])
;; Should return: {:width 640 :height 480 :fps 15 :frame-count 900}
```

---

#### 8. Play Side-by-Side Comparison

**Action:**
1. Ensure view mode is "Side-by-Side" (default)
2. Click "▶ Play" button
3. Watch both original and magnified videos play simultaneously

**Expected Result:**
- Both canvases update at ~15 fps
- Frame counter increments: "Frame 1 / 900" → "Frame 2 / 900" → ...
- Playback is smooth (no stuttering)
- Button changes to "⏸ Pause"

**Visual Observation:**
- **Original Video**: Shows normal breathing (barely visible or invisible)
- **Magnified Video (25×)**: Breathing motion is CLEARLY VISIBLE
  - Chest expands/contracts noticeably
  - Motion is exaggerated but realistic (not distorted)
  - Colors remain stable (no wild color shifts)

**"Aha!" Moment Verification:**
- User can see breathing motion that was previously invisible
- Motion is smooth and periodic (~12-20 breaths per minute)
- Amplification is dramatic but not artifacted

---

#### 9. Test Playback Controls

**Actions & Expected Results:**

**Pause/Play:**
- Click "⏸ Pause" → Playback stops, frame freezes
- Click "▶ Play" → Playback resumes from current frame

**Frame Slider:**
- Drag slider left → Frame index decreases, videos scrub backward
- Drag slider right → Frame index increases, videos scrub forward
- Seeking is instantaneous (no lag)

**View Mode Toggle:**
- Select "Original Only" → Only left canvas (original) displays
- Select "Magnified Only" → Only right canvas (magnified) displays
- Select "Side-by-Side" → Both canvases display

**Frame Counter:**
- Always shows current frame: `"Frame 450 / 900"`
- Updates in real-time during playback

**Verification:**
```clojure
@(rf/subscribe [:combatsys.renderer.state/magnification/playback-state])
;; Should return:
;; {:playing? false :current-frame-index 450 :view-mode :side-by-side}
```

---

#### 10. Reset and Test Again

**Action:**
1. Click "🔄 Start New Magnification" button (at bottom of results)
2. Verify state resets

**Expected Result:**
- Results section disappears
- ROI selector reappears with first frame
- Previous ROI rectangle is cleared
- Progress is reset to 0%
- All state is clean for new magnification

**Verification:**
```clojure
@(rf/subscribe [:combatsys.renderer.state/magnification/magnified-frames])
;; Should return: nil

@(rf/subscribe [:combatsys.renderer.state/magnification/roi])
;; Should return: nil
```

---

## 🧪 TEST PROCEDURE 2: ERROR HANDLING

### Objective
Verify graceful error handling and user feedback.

### Test Cases

#### 2.1: Start Magnification Without ROI

**Action:**
1. Load first frame
2. Click "Start Magnification" WITHOUT selecting ROI

**Expected Result:**
- Error message displays in red box: "Error: No ROI selected"
- Processing does NOT start
- UI remains responsive

---

#### 2.2: Load Session Without Video

**Action:**
1. Load session that has no `:session/video-path`
2. Navigate to magnification view
3. Click "Load Video"

**Expected Result:**
- Error message: "Error: No video or frames available"
- OR: Falls back to using timeline frames (if available)

---

#### 2.3: GPU Not Available

**Action:**
1. Disable WebGPU in browser flags (`chrome://flags#enable-unsafe-webgpu` → Disabled)
2. Restart browser/Electron
3. Attempt to start magnification

**Expected Result:**
- Error message: "Error: GPU init failed: WebGPU not supported"
- Console error provides details: "Please update to Chromium 113+"
- GPU resources are NOT leaked
- User can still navigate app

---

#### 2.4: Video Decode Failure

**Action:**
1. Manually corrupt video file OR point `:session/video-path` to invalid path
2. Attempt to load first frame

**Expected Result:**
- Error message: "Error: Failed to load video: [error details]"
- Processing stops gracefully
- User can reset and try different session

---

## 🧪 TEST PROCEDURE 3: PERFORMANCE VALIDATION

### Objective
Verify performance meets acceptance criteria.

### 3.1: Processing Time Benchmark

**Test Setup:**
- Video: 60 seconds @ 15 fps = 900 frames
- Resolution: 512×512 pixels
- ROI: ~300×300 pixels
- Gain: 25× (default)
- Blur: Disabled (default)

**Measurement:**
1. Note timestamp when "Start Magnification" clicked
2. Note timestamp when progress reaches 100%
3. Calculate total time

**Acceptance Criteria:**
- ✅ Total processing time < 2 minutes (120 seconds)

**Expected Breakdown:**
- Decode: 10-20 seconds
- Magnification: 40-80 seconds
- Total: 50-100 seconds

**If Slower Than Expected:**
- Check GPU hardware acceleration: `chrome://gpu`
- Verify GPU compute is enabled (not software fallback)
- Check CPU usage (should be low, GPU should be doing work)

---

### 3.2: Memory Leak Test

**Test Setup:**
1. Process 3 different sessions sequentially
2. Monitor memory usage in Task Manager / Activity Monitor

**Steps:**
1. Process Session 1 → Reset → Check memory
2. Process Session 2 → Reset → Check memory
3. Process Session 3 → Reset → Check memory

**Expected Result:**
- Memory usage increases during processing (frames stored in RAM)
- Memory usage DECREASES after reset
- After 3 cycles, memory should stabilize (not continuously increasing)
- No GPU memory leaks (check GPU memory in `chrome://gpu`)

**Acceptance Criteria:**
- ✅ Memory returns to baseline after reset
- ✅ No runaway memory growth (>500MB increase per session)

---

### 3.3: UI Responsiveness

**Test Setup:**
- Start magnification processing
- While processing is ongoing:

**Actions:**
1. Move application window
2. Resize window
3. Click other UI elements (but don't navigate away)
4. Check console for errors

**Expected Result:**
- UI remains responsive during processing
- Window operations do not freeze
- Progress indicator updates smoothly
- No "page unresponsive" warnings

---

## 🧪 TEST PROCEDURE 4: VISUAL QUALITY VALIDATION

### Objective
Verify magnified output is visually correct.

### 4.1: Motion Amplification Verification

**Visual Inspection:**
1. Play magnified video
2. Focus on chest area (ROI region)
3. Observe breathing motion

**Expected Visual Quality:**
- **Motion Visibility**: Breathing motion is CLEARLY visible (20-30× amplification)
- **Motion Smoothness**: Motion is smooth and continuous, not jerky
- **Motion Realism**: Motion looks natural, not distorted or artificial
- **Frequency**: Breathing rate is realistic (~12-20 breaths per minute)

**What to Look For:**
- ✅ Chest expands outward during inhale
- ✅ Chest contracts inward during exhale
- ✅ Motion is periodic and rhythmic
- ✅ Amplitude is exaggerated but shape is preserved

**Red Flags:**
- ❌ Motion is erratic or random (not periodic)
- ❌ Colors shift wildly (green/purple artifacts)
- ❌ Image is overly blurry or pixelated
- ❌ Motion appears in background (should be isolated to ROI)

---

### 4.2: Color Stability

**Visual Inspection:**
1. Compare original vs. magnified frame by frame
2. Check skin tone, clothing colors

**Expected Result:**
- Colors remain stable (no wild shifts)
- Slight color variation is acceptable (magnification amplifies everything)
- No extreme saturation or desaturation
- No green/purple "alien skin" effect

---

### 4.3: Edge Artifacts

**Visual Inspection:**
1. Look at edges of ROI
2. Check for ringing or halo effects around moving objects

**Expected Result:**
- Minimal ringing (acceptable: slight blur at edges)
- No hard boundaries or box outlines
- Magnification blends naturally into background

---

## 🧪 TEST PROCEDURE 5: EDGE CASES

### 5.1: Very Short Video (5 seconds)

**Action:**
- Process video with only 75 frames (5s @ 15fps)

**Expected Result:**
- Processing completes successfully
- Temporal mean computation works with small sample
- Playback works normally

---

### 5.2: Very Long Video (5 minutes)

**Action:**
- Process video with 4500 frames (5min @ 15fps)

**Expected Result:**
- Processing takes longer (~5-10 minutes) but completes
- Memory usage is high but manageable
- No out-of-memory errors
- Playback works (may be slower to seek)

---

### 5.3: Very Small ROI (50×50 pixels)

**Action:**
- Select tiny ROI on chest

**Expected Result:**
- Processing works
- Motion is visible but may be noisy
- No errors or crashes

---

### 5.4: Full Frame ROI (entire video)

**Action:**
- Select ROI covering entire frame

**Expected Result:**
- Processing works but takes longer
- Background motion is also amplified (expected)
- User can see this isn't ideal (validates need for ROI selection)

---

### 5.5: Multiple Sequential Magnifications

**Action:**
1. Process session with different ROI
2. Reset
3. Process same session with different ROI
4. Reset
5. Repeat 3× more

**Expected Result:**
- Each magnification works independently
- No state leakage between runs
- Results are consistent (same ROI → same output)

---

## ✅ ACCEPTANCE CRITERIA CHECKLIST

### End-to-End Workflow
- [ ] Load session → Select ROI → Magnify → Playback works without errors
- [ ] User can clearly see breathing motion (20-30× amplified)
- [ ] Processing completes in <2 minutes for 60s video
- [ ] UI remains responsive during processing
- [ ] Error handling is graceful (no crashes)

### Visual Quality
- [ ] Breathing motion is clearly visible
- [ ] Motion is smooth and periodic
- [ ] Colors remain stable (no wild shifts)
- [ ] Minimal artifacts (slight blur acceptable)

### Performance
- [ ] 60s video processes in <2 minutes
- [ ] Memory usage is reasonable (<2GB)
- [ ] No memory leaks (memory returns after reset)
- [ ] GPU utilization is high during processing

### Robustness
- [ ] Handles missing video gracefully
- [ ] Handles missing GPU gracefully
- [ ] Handles decode errors gracefully
- [ ] Can process multiple sessions sequentially

---

## 🐛 TROUBLESHOOTING GUIDE

### Issue: "WebGPU not supported"

**Cause:** Browser/Electron version too old OR WebGPU disabled

**Solution:**
1. Check browser version: Chrome/Edge 113+ required
2. Check Electron version: 25+ required
3. Enable WebGPU flag: `chrome://flags#enable-unsafe-webgpu` → Enabled
4. Verify at `chrome://gpu` → "WebGPU: Hardware accelerated"

---

### Issue: Processing is very slow (>5 minutes)

**Cause:** GPU not hardware-accelerated (software fallback)

**Solution:**
1. Check `chrome://gpu` for warnings
2. Update graphics drivers
3. Verify GPU supports Vulkan/Metal/DirectX 12
4. Try different GPU (integrated vs. discrete)

---

### Issue: Colors are weird (green/purple tint)

**Cause:** Temporal mean computation issue or amplification too high

**Solution:**
1. Try lower gain (15× instead of 25×)
2. Check that temporal mean is computed correctly (should be grayscale-ish)
3. Verify shader `clamp` operation is working

---

### Issue: Motion is visible everywhere (not just chest)

**Cause:** ROI not being applied to magnification

**Solution:**
1. Verify ROI is passed to `magnify-frames!` function
2. Currently: ROI is selected but not used in shader (expected for MVP)
3. Future enhancement: Mask magnification to ROI only

---

### Issue: Video won't load ("Failed to load video")

**Cause:** Video format unsupported OR file path invalid

**Solution:**
1. Check video format: WebM and MP4 (H.264) supported
2. Check file exists at path: `(:session/video-path session)`
3. Try re-recording with different codec
4. Check file permissions (readable by Electron process)

---

### Issue: Canvas is blank after loading first frame

**Cause:** Video decode worked but canvas draw failed

**Solution:**
1. Check console for Canvas API errors
2. Verify Uint8ClampedArray has correct length: `width × height × 4`
3. Try different video resolution (512×512 is well-tested)

---

### Issue: Playback stutters or freezes

**Cause:** Frame data too large OR playback loop issue

**Solution:**
1. Check frame rate: Should be ~15 fps
2. Check canvas update frequency in component
3. Reduce video resolution (640×480 instead of 1920×1080)
4. Check for JS garbage collection pauses (should be rare)

---

## 📊 EXPECTED PERFORMANCE METRICS

### Processing Time (60s video @ 15 fps, 512×512)

| Phase | Expected Time | Acceptable Range |
|-------|---------------|------------------|
| GPU Init | 0.5s | 0.2-2s |
| Video Decode | 10s | 5-20s |
| Temporal Mean | 0.5s | 0.1-2s |
| Frame Processing (900 frames) | 45s | 30-90s |
| **Total** | **56s** | **35-120s** |

### Memory Usage

| Phase | Expected RAM | Peak RAM |
|-------|--------------|----------|
| Idle | 200MB | - |
| Video Decoded | 800MB | 1.2GB |
| Processing | 1.5GB | 2GB |
| After Reset | 300MB | - |

### GPU Utilization

| Phase | GPU % | VRAM |
|-------|-------|------|
| Idle | 0% | 100MB |
| Processing | 70-90% | 500MB |

---

## 📝 TEST REPORT TEMPLATE

```markdown
## LOD 3 Manual Test Report

**Date:** YYYY-MM-DD
**Tester:** [Your Name]
**Environment:**
- OS: [Windows/macOS/Linux]
- Browser/Electron: [Version]
- GPU: [Model]
- WebGPU Support: [Yes/No]

### Test Results

#### Procedure 1: Basic Workflow
- [ ] PASS / FAIL - Step 1: Launch Application
- [ ] PASS / FAIL - Step 2: Load Session
- [ ] PASS / FAIL - Step 3: Navigate to Magnification
- [ ] PASS / FAIL - Step 4: Load First Frame
- [ ] PASS / FAIL - Step 5: Select ROI
- [ ] PASS / FAIL - Step 6: Start Processing
- [ ] PASS / FAIL - Step 7: View Results
- [ ] PASS / FAIL - Step 8: Playback
- [ ] PASS / FAIL - Step 9: Controls
- [ ] PASS / FAIL - Step 10: Reset

**Notes:** [Any observations]

#### Procedure 2: Error Handling
- [ ] PASS / FAIL - 2.1: No ROI Selected
- [ ] PASS / FAIL - 2.2: No Video
- [ ] PASS / FAIL - 2.3: GPU Not Available
- [ ] PASS / FAIL - 2.4: Decode Failure

**Notes:** [Any observations]

#### Procedure 3: Performance
- [ ] PASS / FAIL - 3.1: Processing Time < 2 min
  - Actual Time: ____ seconds
- [ ] PASS / FAIL - 3.2: No Memory Leaks
  - Memory Before: ____ MB
  - Memory After: ____ MB
- [ ] PASS / FAIL - 3.3: UI Responsive

**Notes:** [Any observations]

#### Procedure 4: Visual Quality
- [ ] PASS / FAIL - 4.1: Motion Visible & Realistic
- [ ] PASS / FAIL - 4.2: Color Stability
- [ ] PASS / FAIL - 4.3: Minimal Artifacts

**Notes:** [Describe visual quality]

#### Procedure 5: Edge Cases
- [ ] PASS / FAIL - 5.1: Short Video (5s)
- [ ] PASS / FAIL - 5.2: Long Video (5min)
- [ ] PASS / FAIL - 5.3: Small ROI
- [ ] PASS / FAIL - 5.4: Full Frame ROI
- [ ] PASS / FAIL - 5.5: Multiple Sequential

**Notes:** [Any observations]

### Overall Assessment
- [ ] PASS - All critical tests passed
- [ ] FAIL - Critical issues found

**Critical Issues:** [List any blockers]

**Non-Critical Issues:** [List minor issues]

**Recommendations:** [Next steps]
```

---

## 🎯 SUCCESS CRITERIA

**LOD 3 is COMPLETE when:**
- ✅ All tests in Procedures 1-4 PASS
- ✅ Processing time < 2 minutes (60s video)
- ✅ Breathing motion is clearly visible (user says "Wow!")
- ✅ No critical bugs or crashes
- ✅ Error handling is graceful
- ✅ Memory usage is reasonable

**Known Limitations (Acceptable for MVP):**
- ROI selection doesn't mask output (magnifies entire frame)
- No video encoding (frames only in memory)
- No frequency band selection UI (hardcoded to 0.1-0.5 Hz)
- Blur is disabled by default (performance optimization)

---

**Document Owner**: The 10X Team
**Next Steps**: Run manual tests and report results
**Questions?**: Check `LOD3_CONTEXT.md` or `LOD3_TASKS.md`
