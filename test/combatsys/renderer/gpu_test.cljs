(ns combatsys.renderer.gpu-test
  "Tests for WebGPU wrapper functions.

  Run with: npx shadow-cljs compile test
  Then check console for results

  Note: These tests require WebGPU support in the browser/Electron environment."
  (:require [combatsys.renderer.gpu :as gpu]))

;; ============================================================================
;; Test Helpers
;; ============================================================================

(defn print-test-result
  "Print test result to console."
  [test-name passed? details]
  (if passed?
    (js/console.log "✅" test-name "PASSED" (when details (str "- " details)))
    (js/console.error "❌" test-name "FAILED" (when details (str "- " details)))))

(defn run-async-test
  "Run async test and handle Promise."
  [test-name test-fn]
  (-> (test-fn)
      (.then (fn [result]
               (print-test-result test-name true result)))
      (.catch (fn [error]
                (print-test-result test-name false (str "Error: " (.-message error)))))))

;; ============================================================================
;; Tests
;; ============================================================================

(defn test-gpu-available
  "Test WebGPU availability check."
  []
  (let [available? (gpu/gpu-available?)
        result (boolean? available?)]
    (print-test-result "gpu-available?" result
                       (str "WebGPU " (if available? "is" "is not") " available"))
    result))

(defn test-gpu-initialization
  "Test GPU context initialization."
  []
  (js/Promise.
   (fn [resolve reject]
     (-> (gpu/init-gpu!)
         (.then (fn [ctx]
                  (let [has-adapter? (some? (:adapter ctx))
                        has-device? (some? (:device ctx))
                        has-queue? (some? (:queue ctx))
                        is-ready? (= :ready (:status ctx))
                        all-good? (and has-adapter? has-device? has-queue? is-ready?)]
                    (print-test-result "init-gpu!" all-good?
                                       (str "Context: " (if all-good? "complete" "incomplete")))
                    (if all-good?
                      (resolve ctx) ;; Return context for next tests
                      (reject (js/Error. "Context incomplete"))))))
         (.catch (fn [error]
                   (print-test-result "init-gpu!" false (str "Error: " (.-message error)))
                   (reject error)))))))

(defn test-shader-compilation
  "Test WGSL shader compilation."
  [ctx]
  (js/Promise.
   (fn [resolve reject]
     (try
       ;; Simplest possible shader
       (let [shader-source "@compute @workgroup_size(8, 8)
                            fn main() { }"
             shader (gpu/compile-shader! ctx shader-source)
             success? (some? shader)]
         (print-test-result "compile-shader!" success?
                            (str "Shader: " (if success? "compiled" "failed")))
         (if success?
           (resolve ctx)
           (reject (js/Error. "Shader compilation returned nil"))))
       (catch js/Error e
         (print-test-result "compile-shader!" false (str "Error: " (.-message e)))
         (reject e))))))

(defn test-buffer-creation
  "Test GPU buffer creation."
  [ctx]
  (js/Promise.
   (fn [resolve reject]
     (try
       (let [buffer (gpu/create-buffer! ctx 1024 [:storage :copy-src :copy-dst])
             success? (some? buffer)]
         (print-test-result "create-buffer!" success?
                            (str "Buffer: " (if success? "created (1024 bytes)" "failed")))
         (if success?
           (resolve [ctx buffer])
           (reject (js/Error. "Buffer creation returned nil"))))
       (catch js/Error e
         (print-test-result "create-buffer!" false (str "Error: " (.-message e)))
         (reject e))))))

(defn test-buffer-upload
  "Test data upload to GPU buffer."
  [[ctx buffer]]
  (js/Promise.
   (fn [resolve reject]
     (try
       (let [test-data (js/Float32Array. #js [1.0 2.0 3.0 4.0])]
         (gpu/upload-buffer! ctx buffer test-data)
         ;; Upload is async but returns quickly, assume success if no error
         (print-test-result "upload-buffer!" true "Data uploaded (4 floats)")
         (resolve [ctx buffer]))
       (catch js/Error e
         (print-test-result "upload-buffer!" false (str "Error: " (.-message e)))
         (reject e))))))

(defn test-buffer-download
  "Test data download from GPU buffer."
  [[ctx buffer]]
  (-> (gpu/download-buffer! ctx buffer 16) ;; 4 floats × 4 bytes = 16 bytes
      (.then (fn [result]
               (let [floats (js/Float32Array. result)
                     val0 (aget floats 0)
                     val1 (aget floats 1)
                     val2 (aget floats 2)
                     val3 (aget floats 3)
                     correct? (and (= 1.0 val0) (= 2.0 val1) (= 3.0 val2) (= 4.0 val3))]
                 (print-test-result "download-buffer!" correct?
                                    (str "Data: [" val0 " " val1 " " val2 " " val3 "]"))
                 (if correct?
                   [ctx buffer]
                   (throw (js/Error. "Data mismatch"))))))
      (.catch (fn [error]
                (print-test-result "download-buffer!" false (str "Error: " (.-message error)))
                (throw error)))))

(defn test-buffer-roundtrip
  "Test complete round-trip: upload → download → verify."
  [[ctx buffer]]
  (js/Promise.
   (fn [resolve reject]
     (let [original-data (js/Float32Array. #js [42.0 84.0 126.0 168.0])]
       (gpu/upload-buffer! ctx buffer original-data)
       (-> (gpu/download-buffer! ctx buffer 16)
           (.then (fn [result]
                    (let [downloaded (js/Float32Array. result)
                          matches? (every? (fn [i]
                                            (= (aget original-data i)
                                               (aget downloaded i)))
                                          (range 4))]
                      (print-test-result "buffer-roundtrip" matches?
                                         (str "Integrity: " (if matches? "preserved" "corrupted")))
                      (if matches?
                        (resolve [ctx buffer])
                        (reject (js/Error. "Data integrity check failed"))))))
           (.catch reject))))))

(defn test-texture-creation
  "Test GPU texture creation."
  [[ctx buffer]]
  (js/Promise.
   (fn [resolve reject]
     (try
       (let [texture (gpu/create-texture! ctx 256 256 :rgba8unorm
                                          [:storage :copy-dst :copy-src])
             success? (some? texture)]
         (print-test-result "create-texture!" success?
                            (str "Texture: " (if success? "created (256×256 RGBA)" "failed")))
         (if success?
           (resolve [ctx texture])
           (reject (js/Error. "Texture creation returned nil"))))
       (catch js/Error e
         (print-test-result "create-texture!" false (str "Error: " (.-message e)))
         (reject e))))))

(defn test-texture-upload
  "Test image data upload to GPU texture."
  [[ctx texture]]
  (js/Promise.
   (fn [resolve reject]
     (try
       ;; Create 2×2 test image (red pixels)
       (let [pixels (js/Uint8Array. #js [255 0 0 255  ; Red pixel (top-left)
                                         255 0 0 255  ; Red pixel (top-right)
                                         255 0 0 255  ; Red pixel (bottom-left)
                                         255 0 0 255])] ; Red pixel (bottom-right)
         (gpu/upload-texture! ctx texture 2 2 pixels)
         ;; Upload is async but returns quickly, assume success if no error
         (print-test-result "upload-texture!" true "Image uploaded (2×2 red)")
         (resolve [ctx texture]))
       (catch js/Error e
         (print-test-result "upload-texture!" false (str "Error: " (.-message e)))
         (reject e))))))

(defn test-identity-shader-load
  "Test loading identity shader from file."
  [ctx]
  (js/Promise.
   (fn [resolve reject]
     (try
       ;; Try to load identity shader
       (let [shader-path "resources/shaders/identity.wgsl"]
         ;; Note: In browser, we'd need to fetch this. For now, test with inline shader.
         (let [shader-source "@group(0) @binding(0) var input_texture: texture_2d<f32>;
                              @group(0) @binding(1) var output_texture: texture_storage_2d<rgba8unorm, write>;
                              @compute @workgroup_size(8, 8)
                              fn main(@builtin(global_invocation_id) global_id: vec3<u32>) {
                                  let coords = vec2<i32>(global_id.xy);
                                  let color = textureLoad(input_texture, coords, 0);
                                  textureStore(output_texture, coords, color);
                              }"
               shader (gpu/compile-shader! ctx shader-source)
               success? (some? shader)]
           (print-test-result "identity-shader-load" success?
                              (str "Identity shader: " (if success? "compiled" "failed")))
           (if success?
             (resolve ctx)
             (reject (js/Error. "Identity shader compilation failed")))))
       (catch js/Error e
         (print-test-result "identity-shader-load" false (str "Error: " (.-message e)))
         (reject e))))))

;; ============================================================================
;; Test Runner
;; ============================================================================

(defn run-all-tests!
  "Run all GPU tests in sequence.

  Tests are chained because they depend on GPU context."
  []
  (js/console.log "")
  (js/console.log "🧪 Running GPU Tests...")
  (js/console.log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

  ;; Check availability first (sync)
  (test-gpu-available)

  ;; Run async tests in sequence
  (-> (test-gpu-initialization)
      (.then test-shader-compilation)
      (.then test-buffer-creation)
      (.then test-buffer-upload)
      (.then test-buffer-download)
      (.then test-buffer-roundtrip)
      (.then test-texture-creation)
      (.then test-texture-upload)
      (.then test-identity-shader-load)
      (.then (fn [ctx]
               (js/console.log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
               (js/console.log "✅ All GPU tests completed!")
               (gpu/release-gpu! ctx)))
      (.catch (fn [error]
                (js/console.log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                (js/console.error "❌ Test suite failed:" (.-message error))))))

;; Auto-run tests when loaded
(defonce tests-run (run-all-tests!))
