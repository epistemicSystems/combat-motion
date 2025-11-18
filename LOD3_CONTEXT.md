# LOD 3: EULERIAN VIDEO MAGNIFICATION - CONTEXT DOCUMENT
**Phase**: Days 8-10 of Development Roadmap
**Status**: Ready to Begin
**Prerequisites**: ✅ LOD 0 Complete, ✅ LOD 1 Complete, ✅ LOD 2 Complete
**Date Prepared**: 2025-11-18

---

## 🎯 MISSION STATEMENT

**Objective**: Transform subtle breathing motion into **visually obvious motion** using GPU-accelerated Eulerian video magnification, allowing users to see their breathing patterns with their own eyes.

**Success Criteria**: At the end of LOD 3, a user can:
1. Load a recorded breathing session
2. Select a region of interest (ROI) on their torso
3. Click "Magnify" and see processing complete in <2 minutes
4. Watch side-by-side comparison: original vs. magnified video
5. Clearly see breathing motion amplified 20-30x in the magnified view
6. Understand the quality of their breathing through visual feedback

**The "Aha!" Moment**: User watches magnified video and says "Wow, I can actually SEE my breathing!"

---

## 📊 CURRENT PROJECT STATE

### What We Have (LOD 0 + LOD 1 + LOD 2)

#### ✅ Complete Pipeline
```
Camera (30fps) → MediaPipe (15fps) → Poses (33 landmarks)
                                        ↓
                              Recorded Sessions (EDN)
                                        ↓
                              Breathing Analysis (FFT)
                                        ↓
                   Results: Rate (±2 bpm), Fatigue Windows, Insights
```

#### ✅ Code Base Status
- **Production Code**: ~4,500 lines ClojureScript
- **Test Code**: ~500 lines
- **Documentation**: ~8,000 lines (13 comprehensive docs)
- **Architecture**: Functional core / Imperative shell ✅
- **Performance**: Real-time pose @ 15fps ✅
- **Offline Analysis**: Breathing analysis <1s for 60s footage ✅

#### ✅ Key Capabilities
```clojure
;; We can already:
;; 1. Capture and record video sessions
(def session (record-breathing-session 60)) ;; 60 seconds

;; 2. Detect breathing rate via FFT
(def analysis (breathing/analyze session))
(:rate-bpm analysis) ;; => 21.8 bpm

;; 3. Identify fatigue windows
(:fatigue-windows analysis)
;; => [{:start-ms 45000 :end-ms 48000 :severity 0.85}]

;; 4. Generate insights
(:insights analysis)
;; => [{:title "Breathing rate elevated" :recommendation "..."}]
```

#### ⚠️ What's Missing (LOD 3)

**The Visualization Gap**: We detect breathing algorithmically, but users can't SEE it.

**Problem**: Breathing motion is too subtle to see clearly in raw video:
- Torso displacement: ~2-5 pixels per breath at typical camera distance
- Signal-to-noise ratio: Low (camera shake, lighting changes)
- Human perception: Can't easily see <5 pixel movements

**Solution**: Eulerian Video Magnification
- Amplify subtle motion 20-30x
- Make invisible breathing motion visible
- Provide visual ground truth for algorithmic detection

---

## 🧠 EULERIAN VIDEO MAGNIFICATION: THE SCIENCE

### What is Eulerian Magnification?

**Key Insight**: Video motion can be decomposed into:
1. **Lagrangian** motion: Track individual pixels (computationally expensive)
2. **Eulerian** motion: Observe fixed spatial locations over time (computationally efficient)

**Eulerian approach**: Instead of tracking pixels, we analyze **temporal changes at fixed locations**.

### The Algorithm (High-Level)

```
For each pixel position (x, y):
  1. Extract temporal signal: I(x,y,t) over time
  2. Decompose into spatial frequencies (Laplacian pyramid)
  3. Apply temporal bandpass filter (isolate breathing frequency)
  4. Amplify filtered signal (multiply by gain factor)
  5. Reconstruct image with amplified motion
```

### Visual Explanation

```
Original Frame Sequence (subtle motion):
┌─────┬─────┬─────┬─────┐
│     │     │     │     │  Chest moves 3 pixels
│  🫁 │  🫁 │  🫁 │  🫁 │  (invisible to eye)
│     │  ↑  │  ↑  │  ↓  │
└─────┴─────┴─────┴─────┘
Time:  0s    1s    2s    3s

After Eulerian Magnification (20x gain):
┌─────┬─────┬─────┬─────┐
│     │     │     │     │  Chest moves 60 pixels
│  🫁 │  🫁 │  🫁 │  🫁 │  (clearly visible!)
│     │  ↑↑↑│  ↑↑↑│  ↓↓↓│
└─────┴─────┴─────┴─────┘
Time:  0s    1s    2s    3s
```

### Mathematical Foundation

**Temporal Signal at Pixel (x,y)**:
```
I(x,y,t) = B(x,y) + M(x,y,t)

Where:
  B(x,y) = Base appearance (static)
  M(x,y,t) = Motion component (temporal variation)
```

**Frequency Decomposition**:
```
M(x,y,t) = Σ A_ω * sin(ωt + φ)

Where:
  ω = frequency (radians/second)
  A_ω = amplitude at frequency ω
```

**Bandpass Filter** (isolate breathing):
```
M_breathing(x,y,t) = Bandpass(M(x,y,t), ω_min=0.1Hz, ω_max=0.5Hz)
```

**Amplification**:
```
I_magnified(x,y,t) = B(x,y) + α * M_breathing(x,y,t)

Where:
  α = gain factor (typically 20-30)
```

### Why This Works for Breathing

1. **Periodic Motion**: Breathing is quasi-periodic (0.1-0.5 Hz range)
2. **Spatial Localization**: Motion concentrated in torso region
3. **Low Noise**: Breathing signal has high SNR after bandpass filtering
4. **Visible Amplification**: 20-30x gain makes 3px → 60-90px (clearly visible)

### Laplacian Pyramid Decomposition

**Why Pyramids?**: Different spatial frequencies require different amplification.

```
Original Image (512×512)
        ↓ Gaussian blur & downsample
Level 0: 512×512 (high frequencies)  ← Amplify less (noise)
        ↓
Level 1: 256×256 (mid frequencies)   ← Amplify more (motion)
        ↓
Level 2: 128×128 (low frequencies)   ← Amplify more (large motion)
        ↓
Level 3: 64×64 (base)                ← Amplify less (global changes)
```

**Laplacian** = Difference between Gaussian levels
- Captures bandpass-filtered spatial frequencies
- Allows frequency-specific amplification

---

## 📐 IMPLEMENTATION STRATEGY

### Three-Phase Approach

```
Phase 1: WebGPU Setup (Task 4.1)
  → Initialize GPU context
  → Compile basic compute shader
  → Upload/download buffers
  → Verify end-to-end pipeline

Phase 2: Eulerian Shader (Task 4.2)
  → Gaussian pyramid decomposition (spatial)
  → Temporal IIR bandpass filter
  → Amplification with gain control
  → Pyramid reconstruction

Phase 3: Integration (Task 4.3)
  → ROI selection UI (mouse drag)
  → Video frame extraction
  → Progress indicator
  → Side-by-side playback
```

### Simplified Algorithm (LOD 3 Version)

**Full Eulerian** (MIT paper): 6-level pyramid, sophisticated temporal filters
**Our Version** (Pragmatic): 3-level pyramid, simple IIR filter

```clojure
;; Simplified pseudocode
(defn magnify-video [frames roi gain freq-min freq-max]
  (for frame in frames
    ;; 1. Crop to ROI
    (let [cropped (crop-frame frame roi)

          ;; 2. Spatial decomposition (3 levels)
          pyramid (build-laplacian-pyramid cropped 3)

          ;; 3. Temporal filtering (per level)
          filtered (map #(temporal-bandpass % freq-min freq-max) pyramid)

          ;; 4. Amplification
          amplified (map #(* gain %) filtered)

          ;; 5. Reconstruction
          magnified (reconstruct-from-pyramid amplified)]

      magnified)))
```

### WebGPU Architecture

```
CPU (ClojureScript)                 GPU (WGSL Shader)
──────────────────                  ─────────────────
Load video frames    →  Upload to GPU texture array

Select ROI           →  Pass ROI coords as uniforms

Click "Magnify"      →  Dispatch compute shader
                        ↓
                        for each frame:
                          - Build pyramid
                          - Temporal filter
                          - Amplify
                          - Reconstruct
                        ↓
Download results     ←  Write to output buffer

Save magnified video
```

---

## 🎨 WGSL SHADER DESIGN

### Shader Pipeline (Compute)

```wgsl
// Main entry point
@compute @workgroup_size(8, 8, 1)
fn main(@builtin(global_invocation_id) global_id: vec3<u32>) {
    let pixel_coords = vec2<i32>(global_id.xy);

    // 1. Load frame sequence for this pixel
    var temporal_signal: array<f32, 128>;
    for (var t = 0u; t < frame_count; t++) {
        temporal_signal[t] = load_pixel(pixel_coords, t);
    }

    // 2. Temporal bandpass filter (IIR)
    let filtered = bandpass_filter(temporal_signal, freq_min, freq_max);

    // 3. Amplify
    let amplified = filtered * gain;

    // 4. Add back to original
    for (var t = 0u; t < frame_count; t++) {
        let original = load_pixel(pixel_coords, t);
        let magnified = original + amplified[t];
        store_pixel(pixel_coords, t, clamp(magnified, 0.0, 1.0));
    }
}
```

### Memory Layout

```
GPU Buffer Structure:
┌──────────────────────────────────┐
│ Input Texture Array (R8G8B8A8)   │  Frame 0
│                                   │  Frame 1
│                                   │  Frame 2
│                                   │  ...
│                                   │  Frame N-1
├──────────────────────────────────┤
│ Output Texture Array              │  (same size)
├──────────────────────────────────┤
│ Pyramid Level 1 (half size)      │
│ Pyramid Level 2 (quarter size)   │
│ Pyramid Level 3 (eighth size)    │
└──────────────────────────────────┘
```

---

## ⚡ PERFORMANCE ANALYSIS

### Computational Complexity

**Per-Frame Cost** (for 512×512 ROI):
```
1. Pyramid decomposition:
   - Level 0: 512×512 = 262K pixels
   - Level 1: 256×256 = 65K pixels
   - Level 2: 128×128 = 16K pixels
   Total: ~343K pixel operations

2. Temporal filtering:
   - IIR filter: O(1) per frame (2 multiply-adds)
   - For 343K pixels: ~686K ops

3. Amplification:
   - 1 multiply per pixel: 343K ops

4. Reconstruction:
   - Upsample 3 levels: ~343K ops

Total per frame: ~2M operations
```

**For 60s Video** (900 frames @ 15fps):
```
Total ops: 2M × 900 = 1.8 billion operations

GPU Performance (RTX 3060):
- 3.584 TFLOPS (FP32)
- 1.8B ops / 3.584T ops/s = 0.0005s = 0.5ms per frame
- 900 frames × 0.5ms = 450ms = 0.45 seconds

Theoretical: ~0.5 seconds
Realistic (with overhead): ~5-10 seconds for 60s video
```

### Memory Requirements

```
Input Video (60s @ 15fps):
- 900 frames × 512×512 × 4 bytes (RGBA) = 944 MB

Pyramid Levels (per frame):
- Level 0: 512×512 = 1 MB
- Level 1: 256×256 = 256 KB
- Level 2: 128×128 = 64 KB
Total per frame: ~1.3 MB
Total for 900 frames: 1.17 GB

Output Video:
- Same as input: 944 MB

Total GPU Memory: ~3 GB (well within modern GPU limits)
```

### Performance Targets

| Metric | Target | Max Acceptable |
|--------|--------|----------------|
| Processing time (60s video) | <30s | 120s (2 min) |
| Memory usage | <4 GB GPU | <8 GB |
| Frame rate during processing | Progress updates every 1s | Every 5s |
| Magnified video quality | No visible artifacts | Minor artifacts OK |

**Rationale**: User tolerance for "Magnify" operation is ~1-2 minutes. Target 30s for instant feel.

---

## 🛠️ TECHNOLOGY STACK

### WebGPU

**Why WebGPU?**
- ✅ Modern GPU API (successor to WebGL)
- ✅ Compute shaders (required for video processing)
- ✅ Available in Electron (Chromium-based)
- ✅ Better performance than WebGL
- ✅ WGSL shader language (safer than GLSL)

**Browser Support** (as of 2025):
- ✅ Chrome/Edge 113+
- ✅ Electron 25+ (Chromium 114)
- ⚠️ Firefox (experimental)
- ⚠️ Safari (in development)

**Fallback Strategy**: If WebGPU unavailable, show error message with instructions.

### WGSL (WebGPU Shading Language)

**Features**:
- Rust-inspired syntax (safe, explicit)
- Strong typing (catch errors at compile time)
- Compute shader support
- Memory safety guarantees

**Example**:
```wgsl
@group(0) @binding(0) var input_texture: texture_2d<f32>;
@group(0) @binding(1) var output_texture: texture_storage_2d<rgba8unorm, write>;

@compute @workgroup_size(8, 8)
fn main(@builtin(global_invocation_id) id: vec3<u32>) {
    let color = textureLoad(input_texture, vec2<i32>(id.xy), 0);
    textureStore(output_texture, vec2<i32>(id.xy), color);
}
```

### FFmpeg (for Video Decoding)

**Why FFmpeg?**
- Decode video files to raw frames
- Encode magnified frames back to video

**Installation**:
```bash
npm install fluent-ffmpeg --save
```

**Alternative**: Use browser's `VideoDecoder` API (WebCodecs) if available.

---

## 📚 ALGORITHM RESOURCES

### Essential Papers

1. **"Eulerian Video Magnification for Revealing Subtle Changes in the World"**
   - MIT CSAIL, 2012
   - Authors: Hao-Yu Wu, Michael Rubinstein, Eugene Shih, John Guttag, Frédo Durand, William T. Freeman
   - [Link](http://people.csail.mit.edu/mrub/evm/)
   - **Key Insights**: Eulerian framework, Laplacian pyramids, temporal filtering

2. **"Phase-Based Video Motion Processing"**
   - MIT, 2013
   - Improved version using complex steerable pyramids
   - Better for rotational motion

3. **"Real-Time Video Magnification"**
   - Google Research, 2015
   - Optimizations for GPU implementation

### Implementation References

**Open Source Projects**:
1. [eulerian-magnification](https://github.com/bamos/eulerian-magnification) (Python)
   - Good reference for algorithm steps
   - Not GPU-accelerated

2. [amplify-motion-wasm](https://github.com/tanishq-sharma/amplify-motion-wasm) (WebAssembly)
   - Real-time in browser
   - Uses simpler algorithm

3. [ShaderToy examples](https://www.shadertoy.com/results?query=magnification)
   - GLSL implementations (convertible to WGSL)

### WebGPU Learning Resources

1. [WebGPU Fundamentals](https://webgpufundamentals.org/)
2. [Learn WGSL](https://google.github.io/tour-of-wgsl/)
3. [WebGPU Best Practices](https://toji.dev/webgpu-best-practices/)

---

## 🔗 INTEGRATION POINTS

### Input: Recorded Sessions

```clojure
;; Load session from disk
(def session (files/load-session "2025-11-18_breathing-test"))

;; Extract video path
(def video-path (:session/video-path session))
;; => "~/.config/CombatSys/sessions/2025-11-18.../video.webm"

;; Extract metadata
(:session/duration-ms session) ;; => 60000
(:session/fps session) ;; => 15
```

### Processing Pipeline

```clojure
;; 1. User selects ROI (UI interaction)
(def roi {:x 200 :y 150 :width 400 :height 400})

;; 2. Decode video to frames
(def frames (video/decode video-path))
;; => Vector of 900 ArrayBuffers (RGBA pixels)

;; 3. Upload to GPU
(def gpu-buffers (gpu/upload-frames gpu-ctx frames roi))

;; 4. Run magnification shader
(def magnified-buffers
  (gpu/magnify gpu-ctx gpu-buffers
               {:gain 25
                :freq-min 0.1
                :freq-max 0.5}))

;; 5. Download results
(def magnified-frames (gpu/download-frames gpu-ctx magnified-buffers))

;; 6. Encode to video
(def magnified-path (video/encode magnified-frames "magnified.webm"))
```

### Output: Side-by-Side Playback

```clojure
;; UI state
{:session/id #uuid "..."
 :session/video-path "original.webm"
 :session/magnified-path "magnified.webm" ;; ← New field
 :ui/playback-mode :side-by-side ;; or :original or :magnified
 :ui/playback-position-ms 15000}
```

---

## 🧪 TESTING STRATEGY

### Unit Tests (GPU Functions)

```clojure
(deftest test-gpu-initialization
  (let [ctx (gpu/init-gpu!)]
    (is (some? ctx))
    (is (= :ready (:status ctx)))))

(deftest test-shader-compilation
  (let [ctx (gpu/init-gpu!)
        shader-source (slurp "resources/shaders/eulerian.wgsl")
        shader (gpu/compile-shader! ctx shader-source)]
    (is (some? shader))
    (is (= :compiled (:status shader)))))

(deftest test-buffer-upload-download
  (let [ctx (gpu/init-gpu!)
        test-data (vec (range 256))
        uploaded (gpu/upload-buffer! ctx test-data)
        downloaded (gpu/download-buffer! ctx uploaded)]
    (is (= test-data downloaded))))
```

### Integration Tests

```clojure
(deftest test-magnification-pipeline
  ;; Use synthetic video: static image with small oscillating circle
  (let [frames (generate-test-video 60 {:breathing-motion true})
        ctx (gpu/init-gpu!)
        roi {:x 100 :y 100 :width 200 :height 200}

        result (gpu/magnify-frames! ctx frames roi 20 0.1 0.5)]

    ;; Verify output shape
    (is (= (count frames) (count result)))

    ;; Verify motion amplified (compare pixel differences)
    (let [original-motion (compute-motion-magnitude frames roi)
          magnified-motion (compute-motion-magnitude result roi)]
      ;; Motion should be ~20x larger
      (is (< 15 (/ magnified-motion original-motion) 25)))))
```

### Visual Verification Tests

```
Test 1: Synthetic Breathing Video
1. Generate video: solid color background, circle that expands/contracts
2. Apply magnification (20x)
3. Verify: circle motion is clearly exaggerated

Test 2: Real Breathing Recording
1. User records 30s breathing session
2. User manually counts breaths (e.g., 10 breaths = 20 bpm)
3. Apply magnification
4. Visual inspection: can user clearly see 10 breathing cycles?

Test 3: No Motion Control
1. Record static scene (no motion)
2. Apply magnification
3. Verify: no artifacts, image remains stable
```

---

## 🚨 RISKS & MITIGATION

### Risk 1: WebGPU Not Available

**Problem**: User's system doesn't support WebGPU
**Probability**: Low (most modern systems support it)
**Mitigation**:
```clojure
(defn init-gpu! []
  (if (gpu-available?)
    (create-gpu-context)
    (throw (ex-info "WebGPU not supported"
                    {:fallback-message
                     "Eulerian magnification requires WebGPU support.
                      Please update your browser/Electron version."}))))
```

### Risk 2: Shader Artifacts (Ringing, Flickering)

**Problem**: Amplification creates visual artifacts
**Probability**: Medium (common in video magnification)
**Mitigation**:
- Limit gain factor (cap at 30x)
- Smooth temporal transitions
- Clamp pixel values to valid range [0, 1]
- User-adjustable gain slider (start conservative)

### Risk 3: Memory Overflow

**Problem**: 60s video at high resolution exceeds GPU memory
**Probability**: Low (512×512 ROI uses ~3GB, well within limits)
**Mitigation**:
- Process in batches (e.g., 10s chunks)
- Release buffers immediately after processing
- Monitor GPU memory usage, abort if approaching limit

### Risk 4: Processing Takes Too Long

**Problem**: 2-minute wait feels too long
**Probability**: Medium (depends on GPU performance)
**Mitigation**:
- Show progress indicator (% complete)
- Allow cancellation
- Cache magnified videos (don't reprocess)
- Optimize: profile and improve hotspots

### Risk 5: Complex Shader Bugs

**Problem**: WGSL shader has subtle bugs (hard to debug)
**Probability**: High (GPU debugging is challenging)
**Mitigation**:
- Start with simplest possible shader (identity function)
- Add features incrementally (pyramid, filter, amplify)
- Use GPU validation layers in development
- Extensive unit tests for each shader stage

---

## 📋 DEFINITION OF DONE

### Task 4.1: WebGPU Setup ✅

- [ ] GPU context initializes successfully
- [ ] Can compile basic WGSL shader (identity pass-through)
- [ ] Can upload ArrayBuffer to GPU texture
- [ ] Can download GPU texture to ArrayBuffer
- [ ] Round-trip test passes (upload → download → verify equality)
- [ ] Error handling for unsupported GPU
- [ ] Unit tests pass

### Task 4.2: Eulerian Magnification Shader ✅

- [ ] WGSL shader compiles without errors
- [ ] Implements Gaussian pyramid (3 levels)
- [ ] Implements IIR temporal bandpass filter
- [ ] Implements amplification with configurable gain
- [ ] Implements pyramid reconstruction
- [ ] Synthetic test passes (oscillating circle amplified correctly)
- [ ] Real breathing video shows visible magnification
- [ ] No major visual artifacts (ringing, flickering)
- [ ] Code documented with algorithm explanation

### Task 4.3: ROI Selection & Pipeline ✅

- [ ] Canvas overlay for ROI selection (mouse drag)
- [ ] ROI coordinates captured correctly
- [ ] Video decoding works (WebM → frames)
- [ ] GPU processing pipeline complete (decode → upload → magnify → download → encode)
- [ ] Progress indicator shows % complete
- [ ] Side-by-side video player works
- [ ] Can toggle between original/magnified/side-by-side
- [ ] Magnified video saved to disk
- [ ] UI is responsive (doesn't freeze during processing)

### Integration ✅

- [ ] All three tasks integrated
- [ ] End-to-end test passes (load session → select ROI → magnify → playback)
- [ ] Manual test with real breathing session succeeds
- [ ] User can clearly see breathing motion in magnified video
- [ ] Processing completes in <2 minutes for 60s video
- [ ] No memory leaks (can process multiple sessions)

---

## 🏁 NEXT STEPS AFTER LOD 3

### LOD 4: Posture Analyzer (Days 11-13)
- Forward head measurement
- Shoulder imbalance detection
- Multi-analyzer UI integration

### LOD 5: User Calibration (Days 14-16)
- Personalized baselines
- Adaptive thresholds

### LOD 6: Multi-Session Analytics (Days 17-18)
- Session comparison
- Trend visualization

---

## 💡 PHILOSOPHICAL REMINDERS

### Rich Hickey: Data-Centric Design
> "GPU shaders are pure functions: pixels in → pixels out. Same philosophy."

### John Carmack: Understand the Machine
> "Know your GPU memory model. Profile early. Batch operations."

### Brett Victor: Make Invisible Visible
> "This is THE feature that makes breathing analysis tangible. Users must SEE it."

### Paul Graham: Ship Incrementally
> "Start with identity shader. Then pyramid. Then filter. Then amplify. Each step works."

---

## 📞 GETTING HELP

If you get stuck:

1. **Check WebGPU examples**: [webgpu-samples](https://webgpu.github.io/webgpu-samples/)
2. **Read MIT paper**: Algorithm explained in detail
3. **Test incrementally**: Don't try to implement full shader at once
4. **Use GPU validation**: Enable debug layers in development
5. **Visualize intermediate steps**: Save pyramid levels, filter outputs

---

**Document Owner**: The 10X Team
**Last Updated**: 2025-11-18
**Status**: Ready for LOD 3 Implementation 🚀
