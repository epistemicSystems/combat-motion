# GPU REPL VERIFICATION SCRIPT
**Purpose**: Interactive testing of WebGPU wrapper functions
**Date**: 2025-11-18
**Task**: LOD 3 - Task 4.1 (WebGPU Setup)

---

## 🎯 QUICK START

**Prerequisites**:
1. Start shadow-cljs: `npx shadow-cljs watch renderer`
2. Open Electron app or browser console
3. Open ClojureScript REPL (connect to shadow-cljs)
4. Copy-paste commands below

---

## ✅ STEP 1: CHECK WEBGPU AVAILABILITY

```clojure
(require '[combatsys.renderer.gpu :as gpu])

;; Check if WebGPU is supported
(gpu/gpu-available?)
;; => true (if supported) or false (if not)
```

**Expected**: `true` on Chrome 113+, Electron 25+, modern browsers

**If false**:
- Update browser/Electron
- Check GPU hardware available
- Enable WebGPU flags: `chrome://flags/#enable-unsafe-webgpu`

---

## ✅ STEP 2: INITIALIZE GPU CONTEXT

```clojure
;; Initialize GPU (returns Promise)
(def ctx-promise (gpu/init-gpu!))

;; Wait for initialization
(.then ctx-promise
       (fn [ctx]
         (js/console.log "GPU initialized:" ctx)
         (def ctx ctx)))

;; Or in REPL (if await supported):
;; (def ctx (js-await (gpu/init-gpu!)))
```

**Expected**:
```clojure
{:adapter #object[GPUAdapter]
 :device #object[GPUDevice]
 :queue #object[GPUQueue]
 :status :ready}
```

**Verify**:
```clojure
(:status ctx)
;; => :ready

(some? (:device ctx))
;; => true
```

---

## ✅ STEP 3: COMPILE SHADER

```clojure
;; Simplest possible shader
(def shader-source
  "@compute @workgroup_size(8, 8)
   fn main() { }")

;; Compile
(def shader (gpu/compile-shader! ctx shader-source))

;; Verify
(some? shader)
;; => true
```

**Expected**: No errors, shader object returned

**If error**: Check WGSL syntax, shader must have `@compute` and `fn main()`

---

## ✅ STEP 4: CREATE BUFFER

```clojure
;; Create 1KB buffer (storage, copy-src, copy-dst)
(def buffer (gpu/create-buffer! ctx 1024 [:storage :copy-src :copy-dst]))

;; Verify
(some? buffer)
;; => true
```

**Usage flags**:
- `:storage` - Shader can read/write
- `:copy-src` - Can copy FROM this buffer
- `:copy-dst` - Can copy TO this buffer
- `:uniform` - Uniform buffer
- `:map-read` - CPU can map (requires staging)

---

## ✅ STEP 5: UPLOAD DATA TO BUFFER

```clojure
;; Create test data (4 floats)
(def test-data (js/Float32Array. #js [1.0 2.0 3.0 4.0]))

;; Upload to GPU
(gpu/upload-buffer! ctx buffer test-data)

;; Verify (no return value, but no error = success)
;; Upload is queued, returns immediately
```

**Expected**: No errors, returns `nil` (side effect only)

---

## ✅ STEP 6: DOWNLOAD DATA FROM BUFFER

```clojure
;; Download 16 bytes (4 floats × 4 bytes/float)
(def download-promise (gpu/download-buffer! ctx buffer 16))

;; Wait for download
(.then download-promise
       (fn [result]
         (let [floats (js/Float32Array. result)]
           (js/console.log "Downloaded data:" floats)
           (def downloaded-data floats))))

;; Check values
(aget downloaded-data 0)
;; => 1.0

(aget downloaded-data 1)
;; => 2.0

(aget downloaded-data 2)
;; => 3.0

(aget downloaded-data 3)
;; => 4.0
```

**Expected**: Downloaded data matches uploaded data [1.0, 2.0, 3.0, 4.0]

**This is the "GREEN TRIANGLE" moment for WebGPU!** 🎉

---

## ✅ STEP 7: ROUND-TRIP INTEGRITY TEST

```clojure
;; Upload new data
(def original (js/Float32Array. #js [42.0 84.0 126.0 168.0]))
(gpu/upload-buffer! ctx buffer original)

;; Download and verify
(.then (gpu/download-buffer! ctx buffer 16)
       (fn [result]
         (let [downloaded (js/Float32Array. result)
               matches? (every? (fn [i]
                                  (= (aget original i)
                                     (aget downloaded i)))
                                (range 4))]
           (if matches?
             (js/console.log "✅ Round-trip test PASSED: Data integrity preserved")
             (js/console.error "❌ Round-trip test FAILED: Data corrupted"))
           matches?)))
```

**Expected**: `✅ Round-trip test PASSED`

---

## ✅ STEP 8: CREATE TEXTURE

```clojure
;; Create 256×256 RGBA texture
(def texture (gpu/create-texture! ctx 256 256 :rgba8unorm
                                  [:storage :copy-dst :copy-src]))

;; Verify
(some? texture)
;; => true
```

**Texture formats**:
- `:rgba8unorm` - 8-bit RGBA (0-255 per channel)
- `:r32float` - 32-bit float (single channel)
- `:rg32float` - 32-bit float (two channels)
- `:rgba16float` - 16-bit float (four channels)

---

## ✅ STEP 9: UPLOAD IMAGE TO TEXTURE

```clojure
;; Create 2×2 red image (RGBA format: 4 bytes per pixel)
(def red-pixels (js/Uint8Array. #js [255 0 0 255  ; Top-left: Red
                                     255 0 0 255  ; Top-right: Red
                                     255 0 0 255  ; Bottom-left: Red
                                     255 0 0 255])) ; Bottom-right: Red

;; Upload to texture
(gpu/upload-texture! ctx texture 2 2 red-pixels)

;; Verify (no error = success)
```

**Expected**: No errors, texture now contains red image

---

## ✅ STEP 10: COMPILE IDENTITY SHADER

```clojure
;; Identity shader (copies input → output)
(def identity-shader-source
  "@group(0) @binding(0) var input_texture: texture_2d<f32>;
   @group(0) @binding(1) var output_texture: texture_storage_2d<rgba8unorm, write>;

   @compute @workgroup_size(8, 8)
   fn main(@builtin(global_invocation_id) global_id: vec3<u32>) {
       let coords = vec2<i32>(global_id.xy);
       let dimensions = textureDimensions(input_texture);

       if (coords.x >= dimensions.x || coords.y >= dimensions.y) {
           return;
       }

       let color = textureLoad(input_texture, coords, 0);
       textureStore(output_texture, coords, color);
   }")

;; Compile
(def identity-shader (gpu/compile-shader! ctx identity-shader-source))

;; Verify
(some? identity-shader)
;; => true
```

**Expected**: Shader compiles successfully

---

## ✅ STEP 11: CLEANUP

```clojure
;; Release GPU resources
(gpu/release-gpu! ctx)

;; Verify (device destroyed)
;; Attempting to use ctx after this will throw errors
```

**Expected**: No errors, resources freed

---

## 🎯 SUCCESS CRITERIA

If all steps above work without errors:
- ✅ WebGPU is available
- ✅ GPU context initializes
- ✅ Shaders compile
- ✅ Buffers work (create, upload, download)
- ✅ Textures work (create, upload)
- ✅ Data integrity preserved (round-trip test)
- ✅ Identity shader compiles

**Task 4.1 Complete!** 🎉

---

## 🚨 TROUBLESHOOTING

### Error: "WebGPU not supported"
- Update browser to Chrome 113+ or Electron 25+
- Check GPU hardware present
- Enable flags: `chrome://flags/#enable-unsafe-webgpu`

### Error: "No GPU adapter found"
- GPU hardware not available
- Drivers not installed
- Try different browser

### Error: "Shader compilation failed"
- Check WGSL syntax
- Ensure `@compute` and `fn main()` present
- Check workgroup size (must be power of 2, ≤256)

### Error: "Cannot copy from buffer"
- Buffer missing `:copy-src` usage flag
- Recreate buffer with correct flags

### Download returns wrong data
- Check upload called before download
- Verify size-bytes matches data size
- Ensure buffer has [:copy-src :copy-dst] flags

### Texture upload fails
- Verify dimensions match data size (width × height × 4)
- Check format is :rgba8unorm
- Ensure texture has :copy-dst flag

---

## 📊 PERFORMANCE NOTES

**Typical timings**:
- Init GPU: ~100ms (one-time)
- Compile shader: ~50ms (cached)
- Buffer upload (1MB): ~5ms
- Buffer download (1MB): ~20ms (slow due to staging)

**Memory**:
- Buffer (1KB): 1,024 bytes GPU RAM
- Texture (256×256 RGBA): 262,144 bytes (~256KB)

---

## 🎓 LEARNING NOTES

**GPU Memory Model**:
```
CPU Heap          GPU VRAM
────────          ────────
ArrayBuffer  →    GPU Buffer (STORAGE)
                  GPU Buffer (STAGING)  ← For CPU readback
                  GPU Texture
```

**Why download is slow**:
1. Create staging buffer (MAP_READ)
2. Copy GPU → staging (async)
3. Map staging to CPU (async wait)
4. Copy to result ArrayBuffer
5. Destroy staging

**Usage flags are critical**:
- Wrong flags = runtime error
- `:copy-src` required for download
- `:copy-dst` required for upload
- `:storage` required for shader read/write

---

**Document Owner**: The 10X Team
**Last Updated**: 2025-11-18
**Status**: Ready for REPL Testing 🚀
