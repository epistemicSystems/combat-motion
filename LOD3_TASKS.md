# LOD 3: EULERIAN VIDEO MAGNIFICATION - TASK BREAKDOWN
**Sprint Duration**: Days 8-10 (Estimated: 22 hours total)
**Goal**: Implement GPU-accelerated Eulerian video magnification for breathing visualization
**Status**: Ready to Begin
**Date**: 2025-11-18

---

## 📋 OVERVIEW

### The Three Core Tasks

```
Task 4.1: WebGPU Setup                    [6 hours] → GPU infrastructure
                ↓
Task 4.2: Eulerian Magnification Shader   [10 hours] → WGSL implementation
                ↓
Task 4.3: ROI Selection & Pipeline        [6 hours] → UI integration
```

**Total Estimated Time**: 22 hours
**Target Completion**: Day 10

---

## 🎯 TASK 4.1: WEBGPU SETUP

**File**: `src/renderer/gpu.cljs` (new file)
**Estimated Time**: 6 hours
**Priority**: 🔴 Critical (blocks all other tasks)
**Status**: Not Started

### Objective

Set up WebGPU infrastructure and verify GPU compute capabilities:
1. Initialize WebGPU device and adapter
2. Compile basic WGSL compute shader
3. Create GPU buffers and textures
4. Upload and download data (round-trip test)
5. Handle errors gracefully (fallback for unsupported systems)

### Prerequisites

**WebGPU Support Check**:
```javascript
// Check if WebGPU is available
if (!navigator.gpu) {
  throw new Error("WebGPU not supported");
}
```

**Electron Configuration**:
```json
// package.json (Electron flags)
{
  "scripts": {
    "start": "electron . --enable-features=Vulkan"
  }
}
```

### Target Implementation

#### 4.1.1: GPU Context Initialization

```clojure
(ns combatsys.renderer.gpu
  "WebGPU compute pipeline for video processing.

  Provides low-level GPU operations:
  - Device initialization
  - Shader compilation
  - Buffer management
  - Compute dispatch")

(defn gpu-available?
  "Check if WebGPU is supported on this system.

  Returns:
    Boolean - true if WebGPU available, false otherwise"
  []
  (some? (.-gpu js/navigator)))

(defn init-gpu!
  "Initialize WebGPU context.

  Side effect: Requests GPU adapter and device.

  Returns:
    GPU context map with keys:
      :adapter - GPU adapter handle
      :device - GPU device handle
      :queue - Command queue
      :status - :ready or :error

  Throws:
    ExceptionInfo if WebGPU not supported or initialization fails

  Example:
    (def ctx (init-gpu!))
    (:status ctx) ;; => :ready"
  []
  (if-not (gpu-available?)
    (throw (ex-info "WebGPU not supported"
                    {:error :webgpu-unavailable
                     :message "Please update to Chromium 113+ or Electron 25+"}))

    (js/Promise.
     (fn [resolve reject]
       (-> (.-gpu js/navigator)
           (.requestAdapter)
           (.then (fn [adapter]
                    (if (nil? adapter)
                      (reject (js/Error. "No GPU adapter found"))
                      (-> (.requestDevice adapter)
                          (.then (fn [device]
                                   (resolve {:adapter adapter
                                             :device device
                                             :queue (.getQueue device)
                                             :status :ready})))
                          (.catch reject)))))
           (.catch reject))))))

(defn release-gpu!
  "Release GPU resources.

  Args:
    ctx: GPU context from init-gpu!

  Side effect: Destroys device and frees resources"
  [ctx]
  (when-let [device (:device ctx)]
    (.destroy device)))
```

#### 4.1.2: Shader Compilation

```clojure
(defn compile-shader!
  "Compile WGSL shader source code.

  Args:
    ctx: GPU context
    shader-source: String containing WGSL code

  Returns:
    Shader module handle

  Throws:
    ExceptionInfo if compilation fails

  Example:
    (def shader-code
      \"@compute @workgroup_size(8, 8)
       fn main() { }\")
    (def shader (compile-shader! ctx shader-code))"
  [ctx shader-source]
  (try
    (let [device (:device ctx)]
      (.createShaderModule device
                           #js {:code shader-source
                                :label "compute-shader"}))
    (catch js/Error e
      (throw (ex-info "Shader compilation failed"
                      {:error (.getMessage e)
                       :source shader-source})))))

(defn create-compute-pipeline!
  "Create compute pipeline from compiled shader.

  Args:
    ctx: GPU context
    shader: Compiled shader module
    entry-point: Entry function name (string)
    bind-group-layout: Bind group layout descriptor

  Returns:
    Compute pipeline handle

  Example:
    (def pipeline
      (create-compute-pipeline! ctx shader \"main\" layout))"
  [ctx shader entry-point bind-group-layout]
  (let [device (:device ctx)
        pipeline-layout (.createPipelineLayout
                         device
                         #js {:bindGroupLayouts #js [bind-group-layout]})]

    (.createComputePipeline
     device
     #js {:layout pipeline-layout
          :compute #js {:module shader
                        :entryPoint entry-point}})))
```

#### 4.1.3: Buffer Management

```clojure
(defn create-buffer!
  "Create GPU buffer.

  Args:
    ctx: GPU context
    size-bytes: Buffer size in bytes
    usage: Usage flags (e.g., :storage, :copy-src, :copy-dst)

  Returns:
    GPU buffer handle

  Example:
    (def buffer (create-buffer! ctx 1024 [:storage :copy-dst]))"
  [ctx size-bytes usage]
  (let [device (:device ctx)
        usage-flags (reduce bit-or 0
                            (map #(case %
                                    :storage js/GPUBufferUsage.STORAGE
                                    :copy-src js/GPUBufferUsage.COPY_SRC
                                    :copy-dst js/GPUBufferUsage.COPY_DST
                                    :uniform js/GPUBufferUsage.UNIFORM)
                                 usage))]

    (.createBuffer device
                   #js {:size size-bytes
                        :usage usage-flags
                        :mappedAtCreation false})))

(defn upload-buffer!
  "Upload data to GPU buffer.

  Args:
    ctx: GPU context
    buffer: GPU buffer handle
    data: ArrayBuffer or TypedArray

  Side effect: Writes data to GPU

  Example:
    (def data (js/Float32Array. #js [1 2 3 4]))
    (upload-buffer! ctx buffer data)"
  [ctx buffer data]
  (let [queue (:queue ctx)]
    (.writeBuffer queue buffer 0 data)))

(defn download-buffer!
  "Download data from GPU buffer.

  Args:
    ctx: GPU context
    buffer: GPU buffer handle
    size-bytes: Number of bytes to read

  Returns:
    Promise<ArrayBuffer>

  Example:
    (-> (download-buffer! ctx buffer 16)
        (.then (fn [data]
                 (js/console.log \"Downloaded:\" data))))"
  [ctx buffer size-bytes]
  (let [device (:device ctx)
        staging-buffer (.createBuffer device
                                      #js {:size size-bytes
                                           :usage (bit-or js/GPUBufferUsage.COPY_DST
                                                         js/GPUBufferUsage.MAP_READ)})
        encoder (.createCommandEncoder device)]

    ;; Copy GPU buffer to staging buffer
    (.copyBufferToBuffer encoder buffer 0 staging-buffer 0 size-bytes)

    ;; Submit command
    (let [queue (:queue ctx)]
      (.submit queue #js [(.finish encoder)]))

    ;; Map staging buffer and read
    (js/Promise.
     (fn [resolve reject]
       (-> (.mapAsync staging-buffer js/GPUMapMode.READ)
           (.then (fn []
                    (let [mapped-range (.getMappedRange staging-buffer)
                          result (js/ArrayBuffer. size-bytes)]
                      (js-invoke (js/Uint8Array. result) "set" (js/Uint8Array. mapped-range))
                      (.unmap staging-buffer)
                      (.destroy staging-buffer)
                      (resolve result))))
           (.catch reject))))))
```

#### 4.1.4: Texture Management

```clojure
(defn create-texture!
  "Create GPU texture.

  Args:
    ctx: GPU context
    width: Texture width (pixels)
    height: Texture height (pixels)
    format: Pixel format (e.g., :rgba8unorm)
    usage: Usage flags (e.g., :storage, :copy-src)

  Returns:
    GPU texture handle

  Example:
    (def texture (create-texture! ctx 512 512 :rgba8unorm
                                  [:storage :copy-dst :copy-src]))"
  [ctx width height format usage]
  (let [device (:device ctx)
        format-str (case format
                     :rgba8unorm "rgba8unorm"
                     :r32float "r32float"
                     :rg32float "rg32float")
        usage-flags (reduce bit-or 0
                            (map #(case %
                                    :storage js/GPUTextureUsage.STORAGE_BINDING
                                    :copy-src js/GPUTextureUsage.COPY_SRC
                                    :copy-dst js/GPUTextureUsage.COPY_DST
                                    :texture js/GPUTextureUsage.TEXTURE_BINDING)
                                 usage))]

    (.createTexture device
                    #js {:size #js {:width width
                                    :height height
                                    :depthOrArrayLayers 1}
                         :format format-str
                         :usage usage-flags
                         :dimension "2d"})))

(defn upload-texture!
  "Upload image data to GPU texture.

  Args:
    ctx: GPU context
    texture: GPU texture handle
    width: Image width
    height: Image height
    data: ArrayBuffer (RGBA pixels)

  Side effect: Writes to GPU texture"
  [ctx texture width height data]
  (let [queue (:queue ctx)]
    (.writeTexture queue
                   #js {:texture texture}
                   data
                   #js {:bytesPerRow (* width 4)
                        :rowsPerImage height}
                   #js {:width width
                        :height height
                        :depthOrArrayLayers 1})))
```

#### 4.1.5: Identity Shader (Test)

```wgsl
// resources/shaders/identity.wgsl
// Simple pass-through shader for testing GPU pipeline

@group(0) @binding(0) var input_texture: texture_2d<f32>;
@group(0) @binding(1) var output_texture: texture_storage_2d<rgba8unorm, write>;

@compute @workgroup_size(8, 8)
fn main(@builtin(global_invocation_id) global_id: vec3<u32>) {
    let coords = vec2<i32>(global_id.xy);
    let dimensions = textureDimensions(input_texture);

    // Bounds check
    if (coords.x >= dimensions.x || coords.y >= dimensions.y) {
        return;
    }

    // Load pixel from input
    let color = textureLoad(input_texture, coords, 0);

    // Write to output (identity transformation)
    textureStore(output_texture, coords, color);
}
```

### Testing Strategy

#### Unit Tests

```clojure
(ns combatsys.gpu-test
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [combatsys.renderer.gpu :as gpu]))

(deftest test-gpu-available
  (testing "WebGPU availability check"
    (is (boolean? (gpu/gpu-available?)))))

(deftest test-gpu-initialization
  (testing "GPU context initialization"
    (async done
      (-> (gpu/init-gpu!)
          (.then (fn [ctx]
                   (is (some? ctx))
                   (is (= :ready (:status ctx)))
                   (is (some? (:device ctx)))
                   (is (some? (:queue ctx)))
                   (gpu/release-gpu! ctx)
                   (done)))
          (.catch (fn [error]
                    (js/console.error "GPU init failed:" error)
                    (done)))))))

(deftest test-shader-compilation
  (testing "Compile simple shader"
    (async done
      (-> (gpu/init-gpu!)
          (.then (fn [ctx]
                   (let [shader-source "@compute @workgroup_size(8, 8)
                                        fn main() { }"
                         shader (gpu/compile-shader! ctx shader-source)]
                     (is (some? shader))
                     (gpu/release-gpu! ctx)
                     (done))))
          (.catch (fn [error]
                    (js/console.error "Shader compilation failed:" error)
                    (done)))))))

(deftest test-buffer-roundtrip
  (testing "Upload and download buffer data"
    (async done
      (-> (gpu/init-gpu!)
          (.then (fn [ctx]
                   (let [test-data (js/Float32Array. #js [1.0 2.0 3.0 4.0])
                         buffer (gpu/create-buffer! ctx 16 [:storage :copy-src :copy-dst])]

                     ;; Upload
                     (gpu/upload-buffer! ctx buffer test-data)

                     ;; Download
                     (-> (gpu/download-buffer! ctx buffer 16)
                         (.then (fn [downloaded]
                                  (let [result (js/Float32Array. downloaded)]
                                    (is (= 1.0 (aget result 0)))
                                    (is (= 2.0 (aget result 1)))
                                    (is (= 3.0 (aget result 2)))
                                    (is (= 4.0 (aget result 3)))
                                    (gpu/release-gpu! ctx)
                                    (done))))))))
          (.catch (fn [error]
                    (js/console.error "Buffer test failed:" error)
                    (done)))))))
```

#### REPL Verification

```clojure
(require '[combatsys.renderer.gpu :as gpu])

;; 1. Check WebGPU availability
(gpu/gpu-available?)
;; => true (or false if not supported)

;; 2. Initialize GPU
(def ctx-promise (gpu/init-gpu!))

;; 3. Wait for initialization
(.then ctx-promise
       (fn [ctx]
         (js/console.log "GPU initialized:" ctx)
         (def ctx ctx)))

;; 4. Compile shader
(def shader-source (slurp "resources/shaders/identity.wgsl"))
(def shader (gpu/compile-shader! ctx shader-source))

;; 5. Create buffers
(def buffer (gpu/create-buffer! ctx 256 [:storage :copy-src :copy-dst]))

;; 6. Test upload/download
(def test-data (js/Uint8Array. (range 256)))
(gpu/upload-buffer! ctx buffer test-data)
(.then (gpu/download-buffer! ctx buffer 256)
       (fn [result]
         (js/console.log "Downloaded:" (js/Uint8Array. result))))

;; 7. Cleanup
(gpu/release-gpu! ctx)
```

### Acceptance Criteria

- [ ] `gpu-available?` returns correct result
- [ ] `init-gpu!` initializes context successfully
- [ ] Can compile WGSL shader (identity shader)
- [ ] Can create GPU buffers
- [ ] Can upload data to buffer
- [ ] Can download data from buffer
- [ ] Round-trip test passes (data integrity verified)
- [ ] Can create textures
- [ ] Can upload image data to texture
- [ ] Error handling works (throws on unsupported GPU)
- [ ] Unit tests pass
- [ ] REPL verification succeeds
- [ ] Code compiles without warnings
- [ ] Documentation complete with examples

---

## 🎯 TASK 4.2: EULERIAN MAGNIFICATION SHADER

**File**: `resources/shaders/eulerian.wgsl`, `src/renderer/magnification.cljs`
**Estimated Time**: 10 hours
**Priority**: 🔴 Critical
**Status**: Blocked by Task 4.1

### Objective

Implement Eulerian video magnification algorithm in WGSL:
1. Gaussian pyramid decomposition (spatial filtering)
2. Temporal IIR bandpass filter (isolate breathing frequency)
3. Amplification (multiply by gain factor)
4. Pyramid reconstruction (inverse of decomposition)
5. Integrate with ClojureScript wrapper

### Algorithm Phases

```
Phase 1: Spatial Decomposition
  Input: Frame sequence [F0, F1, ..., FN]
  Output: Pyramid levels [L0, L1, L2] for each frame

Phase 2: Temporal Filtering
  Input: Temporal signal for each pixel
  Output: Bandpass-filtered signal

Phase 3: Amplification
  Input: Filtered signal
  Output: Amplified signal (multiply by gain)

Phase 4: Reconstruction
  Input: Amplified pyramid
  Output: Magnified frame sequence
```

### WGSL Implementation

#### 4.2.1: Gaussian Blur (Spatial Filter)

```wgsl
// resources/shaders/gaussian_blur.wgsl
// Separable Gaussian blur (horizontal and vertical passes)

@group(0) @binding(0) var input_texture: texture_2d<f32>;
@group(0) @binding(1) var output_texture: texture_storage_2d<rgba8unorm, write>;

// Gaussian kernel (5-tap, sigma=1.0)
const KERNEL: array<f32, 5> = array<f32, 5>(
    0.06136, 0.24477, 0.38774, 0.24477, 0.06136
);

@compute @workgroup_size(8, 8)
fn horizontal_blur(@builtin(global_invocation_id) global_id: vec3<u32>) {
    let coords = vec2<i32>(global_id.xy);
    let dims = textureDimensions(input_texture);

    if (coords.x >= dims.x || coords.y >= dims.y) {
        return;
    }

    var sum = vec4<f32>(0.0);

    // Apply horizontal kernel
    for (var i = 0; i < 5; i++) {
        let offset = i - 2; // Center kernel at current pixel
        let sample_x = clamp(coords.x + offset, 0, dims.x - 1);
        let color = textureLoad(input_texture, vec2<i32>(sample_x, coords.y), 0);
        sum += color * KERNEL[i];
    }

    textureStore(output_texture, coords, sum);
}

@compute @workgroup_size(8, 8)
fn vertical_blur(@builtin(global_invocation_id) global_id: vec3<u32>) {
    let coords = vec2<i32>(global_id.xy);
    let dims = textureDimensions(input_texture);

    if (coords.x >= dims.x || coords.y >= dims.y) {
        return;
    }

    var sum = vec4<f32>(0.0);

    // Apply vertical kernel
    for (var i = 0; i < 5; i++) {
        let offset = i - 2;
        let sample_y = clamp(coords.y + offset, 0, dims.y - 1);
        let color = textureLoad(input_texture, vec2<i32>(coords.x, sample_y), 0);
        sum += color * KERNEL[i];
    }

    textureStore(output_texture, coords, sum);
}
```

#### 4.2.2: Pyramid Decomposition

```wgsl
// resources/shaders/pyramid.wgsl
// Build Laplacian pyramid

@group(0) @binding(0) var input_texture: texture_2d<f32>;
@group(0) @binding(1) var output_gaussian: texture_storage_2d<rgba8unorm, write>;
@group(0) @binding(2) var output_laplacian: texture_storage_2d<rgba8unorm, write>;

@compute @workgroup_size(8, 8)
fn downsample(@builtin(global_invocation_id) global_id: vec3<u32>) {
    // Output coordinates (half resolution)
    let out_coords = vec2<i32>(global_id.xy);
    let out_dims = textureDimensions(output_gaussian);

    if (out_coords.x >= out_dims.x || out_coords.y >= out_dims.y) {
        return;
    }

    // Input coordinates (full resolution)
    let in_coords = out_coords * 2;

    // Sample 2×2 region and average
    var sum = vec4<f32>(0.0);
    for (var dy = 0; dy < 2; dy++) {
        for (var dx = 0; dx < 2; dx++) {
            let sample_pos = in_coords + vec2<i32>(dx, dy);
            sum += textureLoad(input_texture, sample_pos, 0);
        }
    }
    let avg = sum / 4.0;

    textureStore(output_gaussian, out_coords, avg);
}

@compute @workgroup_size(8, 8)
fn compute_laplacian(@builtin(global_invocation_id) global_id: vec3<u32>) {
    let coords = vec2<i32>(global_id.xy);
    let dims = textureDimensions(input_texture);

    if (coords.x >= dims.x || coords.y >= dims.y) {
        return;
    }

    // Laplacian = Original - Gaussian
    let original = textureLoad(input_texture, coords, 0);

    // Upsample Gaussian level
    let gaussian_coords = coords / 2;
    let gaussian = textureLoad(output_gaussian, gaussian_coords, 0);

    // Compute difference
    let laplacian = original - gaussian;

    // Store (shifted to [0,1] range)
    textureStore(output_laplacian, coords, laplacian * 0.5 + 0.5);
}
```

#### 4.2.3: Temporal Bandpass Filter

```wgsl
// resources/shaders/temporal_filter.wgsl
// IIR bandpass filter (simple implementation)

struct FilterState {
    low_freq: vec4<f32>,
    high_freq: vec4<f32>,
}

@group(0) @binding(0) var<storage, read> input_frames: array<vec4<f32>>;
@group(0) @binding(1) var<storage, read_write> filter_state: array<FilterState>;
@group(0) @binding(2) var<storage, write> output_frames: array<vec4<f32>>;

@group(1) @binding(0) var<uniform> params: FilterParams;

struct FilterParams {
    frame_count: u32,
    freq_min: f32,  // 0.1 Hz (6 bpm)
    freq_max: f32,  // 0.5 Hz (30 bpm)
    sampling_rate: f32, // 15 fps
}

// IIR filter coefficients (computed from freq_min, freq_max)
fn compute_filter_coeff(freq: f32, fs: f32) -> f32 {
    return exp(-2.0 * 3.14159 * freq / fs);
}

@compute @workgroup_size(64)
fn filter(@builtin(global_invocation_id) global_id: vec3<u32>) {
    let pixel_idx = global_id.x;
    let total_pixels = arrayLength(&input_frames) / params.frame_count;

    if (pixel_idx >= total_pixels) {
        return;
    }

    // Coefficients for low and high cutoff
    let alpha_low = compute_filter_coeff(params.freq_min, params.sampling_rate);
    let alpha_high = compute_filter_coeff(params.freq_max, params.sampling_rate);

    var state = filter_state[pixel_idx];

    // Process frames for this pixel
    for (var t = 0u; t < params.frame_count; t++) {
        let idx = pixel_idx * params.frame_count + t;
        let input = input_frames[idx];

        // Low-pass filter (removes high frequencies)
        state.low_freq = alpha_low * state.low_freq + (1.0 - alpha_low) * input;

        // High-pass filter (removes low frequencies)
        state.high_freq = alpha_high * state.high_freq + (1.0 - alpha_high) * input;

        // Bandpass = low_pass - high_pass
        let bandpass = state.low_freq - state.high_freq;

        output_frames[idx] = bandpass;
    }

    // Save state
    filter_state[pixel_idx] = state;
}
```

#### 4.2.4: Amplification & Reconstruction

```wgsl
// resources/shaders/amplify.wgsl
// Amplify filtered signal and reconstruct

@group(0) @binding(0) var original_texture: texture_2d<f32>;
@group(0) @binding(1) var filtered_texture: texture_2d<f32>;
@group(0) @binding(2) var output_texture: texture_storage_2d<rgba8unorm, write>;

@group(1) @binding(0) var<uniform> params: AmplifyParams;

struct AmplifyParams {
    gain: f32,
}

@compute @workgroup_size(8, 8)
fn amplify(@builtin(global_invocation_id) global_id: vec3<u32>) {
    let coords = vec2<i32>(global_id.xy);
    let dims = textureDimensions(original_texture);

    if (coords.x >= dims.x || coords.y >= dims.y) {
        return;
    }

    // Load original and filtered values
    let original = textureLoad(original_texture, coords, 0);
    let filtered = textureLoad(filtered_texture, coords, 0);

    // Amplify: original + gain * filtered
    let amplified = original + params.gain * (filtered - 0.5); // Shift from [0,1] to [-0.5,0.5]

    // Clamp to valid range
    let clamped = clamp(amplified, vec4<f32>(0.0), vec4<f32>(1.0));

    textureStore(output_texture, coords, clamped);
}
```

### ClojureScript Wrapper

```clojure
(ns combatsys.renderer.magnification
  "Eulerian video magnification pipeline.

  High-level API for GPU-accelerated motion magnification."
  (:require [combatsys.renderer.gpu :as gpu]))

(defn magnify-frames!
  "Apply Eulerian magnification to video frames.

  Algorithm:
  1. Build Laplacian pyramid (3 levels) for each frame
  2. Apply temporal bandpass filter (isolate breathing)
  3. Amplify filtered signal
  4. Reconstruct magnified frames

  Args:
    ctx: GPU context
    frames: Vector of ArrayBuffers (RGBA pixels)
    width: Frame width (pixels)
    height: Frame height (pixels)
    options: Map with keys:
      :gain - Amplification factor (default 25)
      :freq-min - Min frequency Hz (default 0.1)
      :freq-max - Max frequency Hz (default 0.5)
      :roi - Region of interest {:x :y :width :height}

  Returns:
    Promise<Vector of ArrayBuffers> - Magnified frames

  Example:
    (-> (magnify-frames! ctx frames 512 512
                         {:gain 25 :freq-min 0.1 :freq-max 0.5})
        (.then (fn [magnified]
                 (js/console.log \"Processed\" (count magnified) \"frames\"))))"
  [ctx frames width height {:keys [gain freq-min freq-max roi]
                            :or {gain 25
                                 freq-min 0.1
                                 freq-max 0.5
                                 roi {:x 0 :y 0 :width width :height height}}}]

  (js/Promise.
   (fn [resolve reject]
     (try
       ;; Step 1: Create textures for frame sequence
       (let [input-texture (gpu/create-texture! ctx width height :rgba8unorm
                                                [:storage :copy-dst])
             output-texture (gpu/create-texture! ctx width height :rgba8unorm
                                                 [:storage :copy-src])

             ;; Step 2: Upload frames
             _ (doseq [[i frame] (map-indexed vector frames)]
                 (gpu/upload-texture! ctx input-texture width height frame))

             ;; Step 3: Load shaders
             blur-shader (gpu/compile-shader! ctx (slurp "resources/shaders/gaussian_blur.wgsl"))
             pyramid-shader (gpu/compile-shader! ctx (slurp "resources/shaders/pyramid.wgsl"))
             filter-shader (gpu/compile-shader! ctx (slurp "resources/shaders/temporal_filter.wgsl"))
             amplify-shader (gpu/compile-shader! ctx (slurp "resources/shaders/amplify.wgsl"))

             ;; Step 4: Create compute pipelines
             ;; (TODO: Create bind groups, dispatch compute passes)

             ;; Step 5: Download results
             result-frames (vec (repeatedly (count frames)
                                            #(gpu/download-texture! ctx output-texture
                                                                   width height)))]

         (js/Promise.all (clj->js result-frames))
         (.then resolve))

       (catch js/Error e
         (reject e))))))
```

### Testing Strategy

```clojure
(deftest test-identity-magnification
  (testing "Magnify with gain=1 should return ~original"
    (async done
      (let [frames (generate-test-frames 10 256 256)]
        (-> (magnification/magnify-frames! ctx frames 256 256 {:gain 1.0})
            (.then (fn [result]
                     (is (= 10 (count result)))
                     ;; Verify pixel similarity (allow small error from filtering)
                     (done))))))))

(deftest test-motion-amplification
  (testing "Motion should be amplified by gain factor"
    (async done
      (let [;; Generate frames with small circular motion
            frames (generate-oscillating-circle-frames 60 256 256
                                                       {:amplitude 2}) ;; 2 pixels
            ctx (gpu/init-gpu!)]
        (-> (magnification/magnify-frames! ctx frames 256 256 {:gain 20})
            (.then (fn [result]
                     ;; Measure motion in result
                     (let [motion (compute-motion-magnitude result)]
                       ;; Should be ~20x original (2px → ~40px)
                       (is (< 35 motion 45))
                       (done)))))))))
```

### Acceptance Criteria

- [ ] Gaussian blur shader compiles and runs
- [ ] Pyramid decomposition creates 3 levels
- [ ] Temporal filter isolates breathing range (0.1-0.5 Hz)
- [ ] Amplification multiplies signal by gain
- [ ] Reconstruction produces valid frames
- [ ] Identity test passes (gain=1 → no change)
- [ ] Motion amplification test passes (measured motion ~= gain × original)
- [ ] No visual artifacts (excessive ringing, color shifts)
- [ ] Processes 60s video in <2 minutes
- [ ] Code documented with algorithm explanation
- [ ] WGSL shaders have comments explaining each step

---

## 🎯 TASK 4.3: ROI SELECTION & PIPELINE INTEGRATION

**File**: `src/renderer/views_magnification.cljs` (new), `src/renderer/video.cljs` (new)
**Estimated Time**: 6 hours
**Priority**: 🟡 Important
**Status**: Blocked by Task 4.1, 4.2

### Objective

Build UI and integrate magnification into application:
1. ROI selection component (mouse drag on canvas)
2. Video decoding (WebM → frames)
3. Progress indicator during processing
4. Side-by-side video playback
5. Save magnified video to disk

### Implementation

#### 4.3.1: ROI Selection Component

```clojure
(ns combatsys.renderer.views-magnification
  "UI components for Eulerian magnification."
  (:require [reagent.core :as r]
            [re-frame.core :as rf]))

(defn roi-selector
  "Canvas overlay for selecting region of interest.

  User drags mouse to draw rectangle.
  On release, dispatches :magnification/roi-selected event.

  Args:
    video-frame: First frame of video (for preview)
    width: Canvas width
    height: Canvas height

  Component state:
    dragging? - Boolean
    start-pos - {:x X :y Y}
    current-pos - {:x X :y Y}"
  [video-frame width height]
  (let [state (r/atom {:dragging? false
                       :start-pos nil
                       :current-pos nil
                       :roi nil})
        canvas-ref (atom nil)]

    (fn [video-frame width height]
      [:div.roi-selector
       [:canvas
        {:ref #(reset! canvas-ref %)
         :width width
         :height height
         :style {:border "2px solid #333"
                 :cursor "crosshair"}

         :on-mouse-down
         (fn [e]
           (let [rect (.-boundingClientRect (.getBoundingClientRect (.-target e)))
                 x (- (.-clientX e) (.-left rect))
                 y (- (.-clientY e) (.-top rect))]
             (swap! state assoc
                    :dragging? true
                    :start-pos {:x x :y y}
                    :current-pos {:x x :y y})))

         :on-mouse-move
         (fn [e]
           (when (:dragging? @state)
             (let [rect (.-boundingClientRect (.getBoundingClientRect (.-target e)))
                   x (- (.-clientX e) (.-left rect))
                   y (- (.-clientY e) (.-top rect))]
               (swap! state assoc :current-pos {:x x :y y})
               ;; Redraw canvas with ROI rectangle
               (draw-roi @canvas-ref video-frame @state))))

         :on-mouse-up
         (fn [e]
           (when (:dragging? @state)
             (let [start (:start-pos @state)
                   end (:current-pos @state)
                   roi {:x (min (:x start) (:x end))
                        :y (min (:y start) (:y end))
                        :width (Math/abs (- (:x end) (:x start)))
                        :height (Math/abs (- (:y end) (:y start)))}]
               (swap! state assoc :dragging? false :roi roi)
               (rf/dispatch [:magnification/roi-selected roi]))))}]

       (when-let [roi (:roi @state)]
         [:div.roi-info
          [:p "ROI: " (:x roi) "," (:y roi)
           " (" (:width roi) "×" (:height roi) ")"]])])))

(defn draw-roi
  "Draw video frame and ROI rectangle on canvas."
  [canvas video-frame state]
  (when canvas
    (let [ctx (.getContext canvas "2d")]
      ;; Draw video frame
      (.putImageData ctx video-frame 0 0)

      ;; Draw ROI rectangle (if dragging)
      (when (:dragging? state)
        (let [start (:start-pos state)
              end (:current-pos state)]
          (set! (.-strokeStyle ctx) "#00ff00")
          (set! (.-lineWidth ctx) 2)
          (.strokeRect ctx
                       (:x start) (:y start)
                       (- (:x end) (:x start))
                       (- (:y end) (:y start))))))))
```

#### 4.3.2: Video Processing Pipeline

```clojure
(ns combatsys.renderer.video
  "Video encoding/decoding utilities."
  (:require [combatsys.renderer.gpu :as gpu]
            [combatsys.renderer.magnification :as magnification]))

(defn decode-video!
  "Decode video file to frame sequence.

  Args:
    video-path: Path to video file (WebM, MP4)

  Returns:
    Promise<{:frames Vector :width Int :height Int :fps Int}>

  Implementation:
    Uses HTML5 VideoElement to decode frames"
  [video-path]
  (js/Promise.
   (fn [resolve reject]
     (let [video (js/document.createElement "video")
           canvas (js/document.createElement "canvas")
           ctx (.getContext canvas "2d")
           frames (atom [])]

       ;; Load video
       (set! (.-src video) video-path)

       ;; Wait for metadata
       (.addEventListener video "loadedmetadata"
                          (fn []
                            (let [width (.-videoWidth video)
                                  height (.-videoHeight video)
                                  duration (.-duration video)
                                  fps 15]

                              (set! (.-width canvas) width)
                              (set! (.-height canvas) height)

                              ;; Seek to each frame and capture
                              (capture-frame! video canvas ctx frames 0 duration fps
                                              (fn []
                                                (resolve {:frames @frames
                                                          :width width
                                                          :height height
                                                          :fps fps}))))))

       (.addEventListener video "error"
                          (fn [e]
                            (reject (js/Error. (str "Video decode error: " e)))))))))

(defn capture-frame!
  "Recursively capture frames from video."
  [video canvas ctx frames-atom current-time duration fps callback]
  (if (>= current-time duration)
    (callback) ;; Done

    (do
      ;; Seek to time
      (set! (.-currentTime video) current-time)

      ;; Wait for seek complete
      (.addEventListener video "seeked"
                         (fn []
                           ;; Draw frame to canvas
                           (.drawImage ctx video 0 0)

                           ;; Get pixel data
                           (let [image-data (.getImageData ctx 0 0
                                                          (.-width canvas)
                                                          (.-height canvas))
                                 buffer (.-data image-data)]

                             ;; Store frame
                             (swap! frames-atom conj buffer)

                             ;; Next frame
                             (capture-frame! video canvas ctx frames-atom
                                            (+ current-time (/ 1.0 fps))
                                            duration fps callback)))
                         #js {:once true}))))

(defn encode-video!
  "Encode frame sequence to video file.

  Args:
    frames: Vector of ArrayBuffers (RGBA)
    width: Frame width
    height: Frame height
    fps: Frames per second
    output-path: Output file path

  Returns:
    Promise<String> - Path to encoded video

  Implementation:
    Uses MediaRecorder API or ffmpeg"
  [frames width height fps output-path]
  ;; TODO: Implement using MediaRecorder or ffmpeg
  (js/Promise.resolve output-path))
```

#### 4.3.3: Progress Indicator

```clojure
(defn magnification-progress
  "Progress indicator for magnification processing.

  Subscribes to :magnification/progress re-frame state."
  []
  (let [progress @(rf/subscribe [:magnification/progress])]
    (when progress
      [:div.magnification-progress
       [:div.progress-bar
        [:div.progress-fill
         {:style {:width (str (* 100 progress) "%")}}]]
       [:p (str "Processing: " (int (* 100 progress)) "%")]])))
```

#### 4.3.4: Re-frame Events

```clojure
(ns combatsys.renderer.state
  (:require [re-frame.core :as rf]
            [combatsys.renderer.magnification :as magnification]
            [combatsys.renderer.video :as video]
            [combatsys.renderer.gpu :as gpu]))

(rf/reg-event-fx
 :magnification/start
 (fn [{:keys [db]} [_ session-id roi]]
   (let [session (get-in db [:sessions session-id])
         video-path (:session/video-path session)]

     ;; Start async processing
     (js/Promise.
      (fn [resolve reject]
        (-> (video/decode-video! video-path)
            (.then (fn [{:keys [frames width height fps]}]
                     (-> (gpu/init-gpu!)
                         (.then (fn [ctx]
                                  (magnification/magnify-frames!
                                   ctx frames width height
                                   {:gain 25
                                    :freq-min 0.1
                                    :freq-max 0.5
                                    :roi roi})))
                         (.then (fn [magnified-frames]
                                  (video/encode-video! magnified-frames width height fps
                                                      (str video-path ".magnified.webm"))))
                         (.then resolve)
                         (.catch reject))))
            (.catch reject))))

     ;; Return empty effect (processing happens async)
     {})))

(rf/reg-event-db
 :magnification/complete
 (fn [db [_ session-id magnified-path]]
   (-> db
       (assoc-in [:sessions session-id :session/magnified-path] magnified-path)
       (assoc-in [:ui :magnification-progress] nil))))
```

### Acceptance Criteria

- [ ] ROI selector allows mouse drag
- [ ] ROI coordinates captured correctly
- [ ] Video decoding extracts all frames
- [ ] Progress indicator updates during processing
- [ ] Magnified video saved to disk
- [ ] Side-by-side playback works
- [ ] Can toggle original/magnified/side-by-side
- [ ] UI remains responsive (doesn't freeze)
- [ ] Manual test: select chest ROI, see breathing amplified
- [ ] Code documented

---

## ✅ COMPLETION CHECKLIST

### Task 4.1: WebGPU Setup
- [ ] GPU context initialization works
- [ ] Shader compilation works (identity shader)
- [ ] Buffer upload/download works
- [ ] Texture upload works
- [ ] Round-trip test passes
- [ ] Error handling for unsupported GPU
- [ ] Unit tests pass
- [ ] Documentation complete

### Task 4.2: Eulerian Shader
- [ ] Gaussian blur shader compiles
- [ ] Pyramid decomposition implemented (3 levels)
- [ ] Temporal bandpass filter implemented
- [ ] Amplification shader works
- [ ] Reconstruction produces valid frames
- [ ] Identity test passes (gain=1)
- [ ] Motion amplification test passes
- [ ] No major visual artifacts
- [ ] Processes 60s video in <2 minutes
- [ ] Code documented with algorithm details

### Task 4.3: ROI & Integration
- [ ] ROI selector works (mouse drag)
- [ ] Video decoding extracts frames
- [ ] GPU pipeline integrated
- [ ] Progress indicator works
- [ ] Magnified video encoded to disk
- [ ] Side-by-side playback implemented
- [ ] UI responsive during processing
- [ ] Manual test succeeds (breathing visible)

### End-to-End
- [ ] Load session → Select ROI → Magnify → Playback works
- [ ] User can clearly see breathing motion (20-30x)
- [ ] Processing completes in <2 minutes for 60s video
- [ ] No memory leaks (can process multiple sessions)
- [ ] Error handling graceful (GPU errors, decode errors)

### Documentation
- [ ] All functions have docstrings
- [ ] WGSL shaders have comments
- [ ] Algorithm explained in comments
- [ ] REPL examples provided
- [ ] Manual test procedures documented

---

## 🚀 READY TO SHIP

When all checkboxes complete, LOD 3 is done!

**Success Criteria**:
```
User can:
✅ Load recorded breathing session
✅ Select ROI on their torso (mouse drag)
✅ Click "Magnify" button
✅ See progress indicator (% complete)
✅ Wait <2 minutes for processing
✅ Watch side-by-side comparison
✅ CLEARLY SEE breathing motion (20-30x amplified)
✅ Toggle between original/magnified/side-by-side views
```

**The "Aha!" Moment**: User says "Wow, I can actually see my breathing!"

---

**Document Owner**: The 10X Team
**Last Updated**: 2025-11-18
**Status**: Ready for Implementation 🎯
