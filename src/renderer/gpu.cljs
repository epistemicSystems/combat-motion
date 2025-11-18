(ns combatsys.renderer.gpu
  "WebGPU compute pipeline for video processing.

  Provides low-level GPU operations:
  - Device initialization
  - Shader compilation
  - Buffer management
  - Compute dispatch

  Philosophy:
  - Context passed explicitly (no global state)
  - Side effects marked with ! suffix
  - Async is explicit (Promises)
  - Errors are data (ex-info with maps)

  Example:
    (def ctx-promise (init-gpu!))
    (.then ctx-promise
           (fn [ctx]
             (def buffer (create-buffer! ctx 1024 [:storage :copy-dst]))
             (upload-buffer! ctx buffer (js/Float32Array. #js [1 2 3 4]))))")

;; ============================================================================
;; GPU Availability & Initialization
;; ============================================================================

(defn gpu-available?
  "Check if WebGPU is supported on this system.

  WebGPU is available in:
  - Chrome/Edge 113+
  - Electron 25+ (Chromium 114)
  - Firefox (experimental)
  - Safari (in development)

  Returns:
    Boolean - true if WebGPU available, false otherwise

  Example:
    (gpu-available?)
    ;; => true (or false if not supported)"
  []
  (some? (.-gpu js/navigator)))

(defn init-gpu!
  "Initialize WebGPU context.

  Side effect: Requests GPU adapter and device from browser/Electron.

  Returns:
    Promise<GPU context map> with keys:
      :adapter - GPU adapter handle
      :device - GPU device handle
      :queue - Command queue
      :status - :ready or :error

  Throws:
    ExceptionInfo if WebGPU not supported or initialization fails

  Example:
    (-> (init-gpu!)
        (.then (fn [ctx]
                 (js/console.log \"GPU ready:\" (:status ctx))))
        (.catch (fn [err]
                  (js/console.error \"GPU init failed:\" err))))"
  []
  (if-not (gpu-available?)
    (js/Promise.reject
     (ex-info "WebGPU not supported"
              {:error :webgpu-unavailable
               :message "Please update to Chromium 113+ or Electron 25+"
               :docs-url "https://developer.mozilla.org/en-US/docs/Web/API/WebGPU_API"}))

    (js/Promise.
     (fn [resolve reject]
       (-> (.-gpu js/navigator)
           (.requestAdapter)
           (.then (fn [adapter]
                    (if (nil? adapter)
                      (reject (ex-info "No GPU adapter found"
                                       {:error :no-adapter
                                        :message "GPU hardware not available"}))
                      (-> (.requestDevice adapter)
                          (.then (fn [device]
                                   ;; Listen for device errors
                                   (set! (.-onuncapturederror device)
                                         (fn [event]
                                           (js/console.error "GPU uncaptured error:" (.-error event))))

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

  Side effect: Destroys device and frees resources

  Example:
    (release-gpu! ctx)
    ;; GPU resources freed"
  [ctx]
  (when-let [device (:device ctx)]
    (.destroy device)))

;; ============================================================================
;; Shader Compilation
;; ============================================================================

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
    (def shader (compile-shader! ctx shader-code))
    ;; => #object[GPUShaderModule]"
  [ctx shader-source]
  (try
    (let [device (:device ctx)]
      (.createShaderModule device
                           #js {:code shader-source
                                :label "compute-shader"}))
    (catch js/Error e
      (throw (ex-info "Shader compilation failed"
                      {:error :shader-compilation-failed
                       :message (.-message e)
                       :source shader-source})))))

(defn create-compute-pipeline!
  "Create compute pipeline from compiled shader.

  Args:
    ctx: GPU context
    shader: Compiled shader module
    entry-point: Entry function name (string, default \"main\")
    bind-group-layout: Bind group layout descriptor

  Returns:
    Compute pipeline handle

  Example:
    (def pipeline
      (create-compute-pipeline! ctx shader \"main\" layout))
    ;; => #object[GPUComputePipeline]"
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

;; ============================================================================
;; Buffer Management
;; ============================================================================

(defn- usage-flags
  "Convert keyword usage flags to WebGPU bit flags.

  Args:
    keywords: Vector of usage keywords

  Returns:
    Integer bit flags

  Supported keywords:
    :storage - Shader can read/write
    :copy-src - Can copy FROM this buffer
    :copy-dst - Can copy TO this buffer
    :uniform - Can be used as uniform buffer
    :map-read - CPU can map and read (requires staging)"
  [keywords]
  (reduce bit-or 0
          (map #(case %
                  :storage js/GPUBufferUsage.STORAGE
                  :copy-src js/GPUBufferUsage.COPY_SRC
                  :copy-dst js/GPUBufferUsage.COPY_DST
                  :uniform js/GPUBufferUsage.UNIFORM
                  :map-read js/GPUBufferUsage.MAP_READ)
               keywords)))

(defn create-buffer!
  "Create GPU buffer.

  Args:
    ctx: GPU context
    size-bytes: Buffer size in bytes
    usage: Usage flags as vector of keywords

  Returns:
    GPU buffer handle

  Usage flags:
    :storage - Shader can read/write
    :copy-src - Can copy FROM this buffer
    :copy-dst - Can copy TO this buffer
    :uniform - Uniform buffer
    :map-read - CPU can map (requires staging)

  Example:
    (def buffer (create-buffer! ctx 1024 [:storage :copy-dst]))
    ;; => #object[GPUBuffer]

    ;; For round-trip (upload/download):
    (def buffer (create-buffer! ctx 256 [:storage :copy-src :copy-dst]))

    ;; For staging (GPU→CPU readback):
    (def staging (create-buffer! ctx 256 [:copy-dst :map-read]))"
  [ctx size-bytes usage]
  (let [device (:device ctx)
        usage-flags-int (usage-flags usage)]

    (.createBuffer device
                   #js {:size size-bytes
                        :usage usage-flags-int
                        :mappedAtCreation false})))

(defn upload-buffer!
  "Upload data to GPU buffer.

  Args:
    ctx: GPU context
    buffer: GPU buffer handle
    data: ArrayBuffer or TypedArray (Float32Array, Uint8Array, etc.)

  Side effect: Writes data to GPU

  Note: Operation is queued, not immediate. Returns quickly.

  Example:
    (def data (js/Float32Array. #js [1.0 2.0 3.0 4.0]))
    (upload-buffer! ctx buffer data)
    ;; Data queued for upload (async)"
  [ctx buffer data]
  (let [queue (:queue ctx)]
    (.writeBuffer queue buffer 0 data)))

(defn download-buffer!
  "Download data from GPU buffer.

  Args:
    ctx: GPU context
    buffer: GPU buffer handle (must have :copy-src usage)
    size-bytes: Number of bytes to read

  Returns:
    Promise<ArrayBuffer>

  Note: This is SLOW (~20ms for 1MB). Requires staging buffer and mapping.

  Example:
    (-> (download-buffer! ctx buffer 16)
        (.then (fn [data]
                 (let [floats (js/Float32Array. data)]
                   (js/console.log \"Downloaded:\" floats)))))"
  [ctx buffer size-bytes]
  (let [device (:device ctx)
        ;; Create staging buffer (GPU→CPU)
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
                          ;; Copy to new ArrayBuffer (staging gets destroyed)
                          result (js/ArrayBuffer. size-bytes)]
                      (js-invoke (js/Uint8Array. result) "set" (js/Uint8Array. mapped-range))
                      (.unmap staging-buffer)
                      (.destroy staging-buffer)
                      (resolve result))))
           (.catch reject))))))

;; ============================================================================
;; Texture Management
;; ============================================================================

(defn- texture-format
  "Convert keyword format to WebGPU format string."
  [format]
  (case format
    :rgba8unorm "rgba8unorm"
    :r32float "r32float"
    :rg32float "rg32float"
    :rgba16float "rgba16float"))

(defn- texture-usage-flags
  "Convert keyword usage flags to WebGPU texture usage bit flags."
  [keywords]
  (reduce bit-or 0
          (map #(case %
                  :storage js/GPUTextureUsage.STORAGE_BINDING
                  :copy-src js/GPUTextureUsage.COPY_SRC
                  :copy-dst js/GPUTextureUsage.COPY_DST
                  :texture js/GPUTextureUsage.TEXTURE_BINDING
                  :render js/GPUTextureUsage.RENDER_ATTACHMENT)
               keywords)))

(defn create-texture!
  "Create GPU texture.

  Args:
    ctx: GPU context
    width: Texture width (pixels)
    height: Texture height (pixels)
    format: Pixel format keyword
    usage: Usage flags as vector of keywords

  Returns:
    GPU texture handle

  Formats:
    :rgba8unorm - 8-bit RGBA (0-255 per channel)
    :r32float - 32-bit float (single channel)
    :rg32float - 32-bit float (two channels)
    :rgba16float - 16-bit float (four channels)

  Usage flags:
    :storage - Shader can read/write
    :copy-src - Can copy FROM this texture
    :copy-dst - Can copy TO this texture
    :texture - Can sample from shader
    :render - Can render to texture

  Example:
    (def texture (create-texture! ctx 512 512 :rgba8unorm
                                  [:storage :copy-dst :copy-src]))
    ;; => #object[GPUTexture]"
  [ctx width height format usage]
  (let [device (:device ctx)
        format-str (texture-format format)
        usage-flags-int (texture-usage-flags usage)]

    (.createTexture device
                    #js {:size #js {:width width
                                    :height height
                                    :depthOrArrayLayers 1}
                         :format format-str
                         :usage usage-flags-int
                         :dimension "2d"})))

(defn upload-texture!
  "Upload image data to GPU texture.

  Args:
    ctx: GPU context
    texture: GPU texture handle
    width: Image width (pixels)
    height: Image height (pixels)
    data: ArrayBuffer containing pixel data (RGBA format)

  Side effect: Writes to GPU texture

  Note: Data must be in row-major order, RGBA format (4 bytes per pixel).

  Example:
    ;; Create 2×2 red image
    (def pixels (js/Uint8Array. #js [255 0 0 255  ; Red pixel
                                     255 0 0 255  ; Red pixel
                                     255 0 0 255  ; Red pixel
                                     255 0 0 255])) ; Red pixel
    (upload-texture! ctx texture 2 2 pixels)
    ;; Texture now contains red image"
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

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn create-bind-group-layout!
  "Create bind group layout for shader bindings.

  Args:
    ctx: GPU context
    entries: Vector of entry descriptors

  Returns:
    Bind group layout handle

  Entry descriptor:
    {:binding 0
     :visibility :compute  ; or :vertex, :fragment
     :type :storage-buffer  ; or :uniform-buffer, :storage-texture, etc.}

  Example:
    (def layout
      (create-bind-group-layout!
       ctx
       [{:binding 0 :visibility :compute :type :storage-buffer}
        {:binding 1 :visibility :compute :type :storage-buffer}]))"
  [ctx entries]
  (let [device (:device ctx)
        js-entries (clj->js
                    (mapv (fn [entry]
                            {:binding (:binding entry)
                             :visibility (case (:visibility entry)
                                          :compute js/GPUShaderStage.COMPUTE
                                          :vertex js/GPUShaderStage.VERTEX
                                          :fragment js/GPUShaderStage.FRAGMENT)
                             :buffer (when (= :storage-buffer (:type entry))
                                      {:type "storage"})})
                          entries))]
    (.createBindGroupLayout device #js {:entries js-entries})))

(defn create-bind-group!
  "Create bind group (binds resources to shader).

  Args:
    ctx: GPU context
    layout: Bind group layout
    bindings: Vector of resource bindings

  Returns:
    Bind group handle

  Binding descriptor:
    {:binding 0 :resource buffer-or-texture}

  Example:
    (def bind-group
      (create-bind-group! ctx layout
                          [{:binding 0 :resource input-buffer}
                           {:binding 1 :resource output-buffer}]))"
  [ctx layout bindings]
  (let [device (:device ctx)
        js-bindings (clj->js
                     (mapv (fn [binding]
                             {:binding (:binding binding)
                              :resource (:resource binding)})
                           bindings))]
    (.createBindGroup device #js {:layout layout
                                   :entries js-bindings})))
