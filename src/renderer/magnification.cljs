(ns combatsys.renderer.magnification
  "Eulerian video magnification pipeline.

  High-level API for GPU-accelerated motion magnification.

  Algorithm (Simplified for LOD 3):
  1. Compute temporal mean (average frame across time)
  2. For each frame:
     a. Gaussian blur (spatial smoothing)
     b. Subtract temporal mean (isolate motion)
     c. Amplify motion (multiply by gain)
     d. Clamp to valid range
  3. Return magnified frames

  Simplified vs. Full Eulerian:
  - Full: IIR bandpass filter (frequency-selective)
  - Ours: Temporal mean subtraction (simpler, fast)
  - Trade-off: May amplify all frequencies, but works well for breathing

  Future enhancements:
  - Add Laplacian pyramid (multi-scale processing)
  - Add IIR bandpass filter (frequency selectivity)
  - Add phase-based motion (better for rotational motion)"
  (:require [combatsys.renderer.gpu :as gpu]))

;; ============================================================================
;; Temporal Mean Computation (CPU)
;; ============================================================================

(defn compute-temporal-mean
  "Compute temporal mean (average frame across all frames).

  This captures the static background, allowing us to isolate motion
  by subtracting the mean from each frame.

  Args:
    frames: Vector of Uint8ClampedArrays (RGBA pixels)
    width: Frame width
    height: Frame height

  Returns:
    Uint8ClampedArray - Temporal mean frame (RGBA pixels)

  Algorithm:
    For each pixel (x, y):
      mean[x,y] = average(frame[t][x,y]) for all t

  Performance: O(frames × pixels)
    For 900 frames × 512×512 = 236M operations
    On CPU: ~50ms (fast enough for offline processing)"
  [frames width height]
  (let [pixel-count (* width height 4) ;; RGBA = 4 bytes per pixel
        mean (js/Uint8ClampedArray. pixel-count)
        frame-count (count frames)]

    ;; Sum all frames
    (doseq [frame frames]
      (dotimes [i pixel-count]
        (aset mean i (+ (aget mean i) (aget frame i)))))

    ;; Divide by frame count to get average
    (dotimes [i pixel-count]
      (aset mean i (/ (aget mean i) frame-count)))

    mean))

;; ============================================================================
;; GPU Pipeline Orchestration
;; ============================================================================

(defn- load-shader
  "Load shader source from file.

  Note: In browser/Electron, we need to bundle shaders or use fetch.
  For now, we inline the shader source."
  [shader-name]
  (case shader-name
    :gaussian-blur
    (slurp "resources/shaders/gaussian_blur.wgsl")

    :amplify
    (slurp "resources/shaders/amplify.wgsl")

    (throw (ex-info "Unknown shader" {:shader shader-name}))))

(defn create-uniform-buffer
  "Create uniform buffer for shader parameters.

  Args:
    ctx: GPU context
    data: Float32Array of parameters

  Returns:
    GPU buffer handle"
  [ctx data]
  (let [buffer (gpu/create-buffer! ctx
                                   (.-byteLength data)
                                   [:uniform :copy-dst])]
    (gpu/upload-buffer! ctx buffer data)
    buffer))

(defn apply-gaussian-blur!
  "Apply separable Gaussian blur to texture.

  Args:
    ctx: GPU context
    input-texture: Input texture
    width: Texture width
    height: Texture height

  Returns:
    Output texture (blurred)

  Process:
    1. Horizontal blur: input → temp
    2. Vertical blur: temp → output"
  [ctx input-texture width height]
  (let [;; Create intermediate and output textures
        temp-texture (gpu/create-texture! ctx width height :rgba8unorm
                                          [:storage :copy-dst :texture])
        output-texture (gpu/create-texture! ctx width height :rgba8unorm
                                            [:storage :copy-dst :copy-src :texture])

        ;; Load and compile shader
        shader-source (load-shader :gaussian-blur)
        shader (gpu/compile-shader! ctx shader-source)

        ;; Create bind group layout
        device (:device ctx)
        layout (.createBindGroupLayout
                device
                #js {:entries #js [#js {:binding 0
                                        :visibility js/GPUShaderStage.COMPUTE
                                        :texture #js {:sampleType "float"}}
                                   #js {:binding 1
                                        :visibility js/GPUShaderStage.COMPUTE
                                        :storageTexture #js {:access "write-only"
                                                            :format "rgba8unorm"}}]})

        ;; Create compute pipelines
        h-pipeline (gpu/create-compute-pipeline! ctx shader "horizontal_blur" layout)
        v-pipeline (gpu/create-compute-pipeline! ctx shader "vertical_blur" layout)

        ;; Create bind groups
        h-bind-group (.createBindGroup
                      device
                      #js {:layout layout
                           :entries #js [#js {:binding 0 :resource (.createView input-texture)}
                                        #js {:binding 1 :resource (.createView temp-texture)}]})

        v-bind-group (.createBindGroup
                      device
                      #js {:layout layout
                           :entries #js [#js {:binding 0 :resource (.createView temp-texture)}
                                        #js {:binding 1 :resource (.createView output-texture)}]})

        ;; Create command encoder
        encoder (.createCommandEncoder device)

        ;; Horizontal blur pass
        h-pass (.beginComputePass encoder)
        _ (.setPipeline h-pass h-pipeline)
        _ (.setBindGroup h-pass 0 h-bind-group)
        _ (.dispatchWorkgroups h-pass
                              (js/Math.ceil (/ width 8))
                              (js/Math.ceil (/ height 8)))
        _ (.end h-pass)

        ;; Vertical blur pass
        v-pass (.beginComputePass encoder)
        _ (.setPipeline v-pass v-pipeline)
        _ (.setBindGroup v-pass 0 v-bind-group)
        _ (.dispatchWorkgroups v-pass
                              (js/Math.ceil (/ width 8))
                              (js/Math.ceil (/ height 8)))
        _ (.end v-pass)]

    ;; Submit commands
    (.submit (:queue ctx) #js [(.finish encoder)])

    output-texture))

(defn apply-subtract-mean!
  "Subtract temporal mean from frame to isolate motion.

  Args:
    ctx: GPU context
    frame-texture: Current frame texture
    mean-texture: Temporal mean texture
    width: Texture width
    height: Texture height

  Returns:
    Motion texture (shifted to [0, 1])"
  [ctx frame-texture mean-texture width height]
  (let [;; Create output texture
        output-texture (gpu/create-texture! ctx width height :rgba8unorm
                                            [:storage :copy-dst :copy-src :texture])

        ;; Load and compile shader
        shader-source (load-shader :amplify)
        shader (gpu/compile-shader! ctx shader-source)

        ;; Create bind group layout
        device (:device ctx)
        layout (.createBindGroupLayout
                device
                #js {:entries #js [#js {:binding 0
                                        :visibility js/GPUShaderStage.COMPUTE
                                        :texture #js {:sampleType "float"}}
                                   #js {:binding 1
                                        :visibility js/GPUShaderStage.COMPUTE
                                        :texture #js {:sampleType "float"}}
                                   #js {:binding 2
                                        :visibility js/GPUShaderStage.COMPUTE
                                        :storageTexture #js {:access "write-only"
                                                            :format "rgba8unorm"}}]})

        ;; Create compute pipeline
        pipeline (gpu/create-compute-pipeline! ctx shader "subtract_mean" layout)

        ;; Create bind group
        bind-group (.createBindGroup
                    device
                    #js {:layout layout
                         :entries #js [#js {:binding 0 :resource (.createView frame-texture)}
                                      #js {:binding 1 :resource (.createView mean-texture)}
                                      #js {:binding 2 :resource (.createView output-texture)}]})

        ;; Create command encoder
        encoder (.createCommandEncoder device)

        ;; Compute pass
        pass (.beginComputePass encoder)
        _ (.setPipeline pass pipeline)
        _ (.setBindGroup pass 0 bind-group)
        _ (.dispatchWorkgroups pass
                              (js/Math.ceil (/ width 8))
                              (js/Math.ceil (/ height 8)))
        _ (.end pass)]

    ;; Submit commands
    (.submit (:queue ctx) #js [(.finish encoder)])

    output-texture))

(defn apply-amplification!
  "Amplify motion and add back to original frame.

  Args:
    ctx: GPU context
    original-texture: Original frame texture
    motion-texture: Motion texture (shifted to [0, 1])
    gain: Amplification factor (e.g., 25.0)
    width: Texture width
    height: Texture height

  Returns:
    Magnified texture"
  [ctx original-texture motion-texture gain width height]
  (let [;; Create output texture
        output-texture (gpu/create-texture! ctx width height :rgba8unorm
                                            [:storage :copy-dst :copy-src :texture])

        ;; Create uniform buffer for parameters
        params (js/Float32Array. #js [gain])
        params-buffer (create-uniform-buffer ctx params)

        ;; Load and compile shader
        shader-source (load-shader :amplify)
        shader (gpu/compile-shader! ctx shader-source)

        ;; Create bind group layouts
        device (:device ctx)
        texture-layout (.createBindGroupLayout
                        device
                        #js {:entries #js [#js {:binding 0
                                                :visibility js/GPUShaderStage.COMPUTE
                                                :texture #js {:sampleType "float"}}
                                           #js {:binding 1
                                                :visibility js/GPUShaderStage.COMPUTE
                                                :texture #js {:sampleType "float"}}
                                           #js {:binding 2
                                                :visibility js/GPUShaderStage.COMPUTE
                                                :storageTexture #js {:access "write-only"
                                                                    :format "rgba8unorm"}}]})

        uniform-layout (.createBindGroupLayout
                        device
                        #js {:entries #js [#js {:binding 0
                                                :visibility js/GPUShaderStage.COMPUTE
                                                :buffer #js {:type "uniform"}}]})

        ;; Create pipeline layout
        pipeline-layout (.createPipelineLayout
                         device
                         #js {:bindGroupLayouts #js [texture-layout uniform-layout]})

        ;; Create compute pipeline
        pipeline (.createComputePipeline
                  device
                  #js {:layout pipeline-layout
                       :compute #js {:module shader
                                     :entryPoint "amplify"}})

        ;; Create bind groups
        texture-bind-group (.createBindGroup
                            device
                            #js {:layout texture-layout
                                 :entries #js [#js {:binding 0 :resource (.createView original-texture)}
                                              #js {:binding 1 :resource (.createView motion-texture)}
                                              #js {:binding 2 :resource (.createView output-texture)}]})

        uniform-bind-group (.createBindGroup
                            device
                            #js {:layout uniform-layout
                                 :entries #js [#js {:binding 0
                                                    :resource #js {:buffer params-buffer}}]})

        ;; Create command encoder
        encoder (.createCommandEncoder device)

        ;; Compute pass
        pass (.beginComputePass encoder)
        _ (.setPipeline pass pipeline)
        _ (.setBindGroup pass 0 texture-bind-group)
        _ (.setBindGroup pass 1 uniform-bind-group)
        _ (.dispatchWorkgroups pass
                              (js/Math.ceil (/ width 8))
                              (js/Math.ceil (/ height 8)))
        _ (.end pass)]

    ;; Submit commands
    (.submit (:queue ctx) #js [(.finish encoder)])

    output-texture))

;; ============================================================================
;; High-Level API
;; ============================================================================

(defn magnify-frames!
  "Apply Eulerian magnification to video frames.

  Simplified algorithm (LOD 3 MVP):
  1. Compute temporal mean (CPU)
  2. Upload mean to GPU
  3. For each frame:
     a. Upload frame
     b. Gaussian blur (optional, helps reduce noise)
     c. Subtract temporal mean (isolate motion)
     d. Amplify motion
     e. Download result
  4. Return magnified frames

  Args:
    ctx: GPU context
    frames: Vector of Uint8ClampedArrays (RGBA pixels)
    width: Frame width (pixels)
    height: Frame height (pixels)
    options: Map with keys:
      :gain - Amplification factor (default 25.0)
      :blur? - Apply Gaussian blur? (default false for speed)

  Returns:
    Promise<Vector of Uint8ClampedArrays> - Magnified frames

  Example:
    (-> (magnify-frames! ctx frames 512 512 {:gain 25 :blur? false})
        (.then (fn [magnified]
                 (js/console.log \"Magnified\" (count magnified) \"frames\"))))"
  [ctx frames width height {:keys [gain blur?]
                            :or {gain 25.0
                                 blur? false}}]

  (js/Promise.
   (fn [resolve reject]
     (try
       (js/console.log "Starting Eulerian magnification..."
                       "Frames:" (count frames)
                       "Size:" width "×" height
                       "Gain:" gain)

       ;; Step 1: Compute temporal mean (CPU)
       (js/console.log "Computing temporal mean...")
       (let [temporal-mean (compute-temporal-mean frames width height)

             ;; Step 2: Upload temporal mean to GPU
             mean-texture (gpu/create-texture! ctx width height :rgba8unorm
                                               [:storage :copy-dst :texture])
             _ (gpu/upload-texture! ctx mean-texture width height temporal-mean)

             ;; Step 3: Process each frame (returns vector of Promises)
             magnified-promises
             (vec
              (map-indexed
               (fn [i frame]
                 (when (zero? (mod i 100))
                   (js/console.log "Processing frame" i "/" (count frames)))

                 ;; Upload frame
                 (let [frame-texture (gpu/create-texture! ctx width height :rgba8unorm
                                                          [:storage :copy-dst :texture])
                       _ (gpu/upload-texture! ctx frame-texture width height frame)

                       ;; Optional: Blur
                       frame-texture (if blur?
                                       (apply-gaussian-blur! ctx frame-texture width height)
                                       frame-texture)

                       ;; Subtract mean (compute motion)
                       motion-texture (apply-subtract-mean! ctx frame-texture mean-texture
                                                           width height)

                       ;; Amplify motion
                       magnified-texture (apply-amplification! ctx frame-texture motion-texture
                                                              gain width height)]

                   ;; Download result (returns Promise)
                   (gpu/download-texture! ctx magnified-texture width height)))
               frames))]

         ;; Wait for all downloads to complete
         (-> (js/Promise.all (clj->js magnified-promises))
             (.then (fn [results]
                      (js/console.log "Magnification complete!")
                      (resolve (js->clj results))))))

       (catch js/Error e
         (js/console.error "Magnification failed:" (.-message e))
         (reject e))))))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn generate-test-frames
  "Generate synthetic test frames for validation.

  Creates frames with a moving circle to test motion amplification.

  Args:
    frame-count: Number of frames to generate
    width: Frame width
    height: Frame height
    options: Map with keys:
      :amplitude - Circle oscillation amplitude in pixels (default 2)
      :frequency - Oscillation frequency in Hz (default 0.3, ~breathing rate)
      :fps - Frames per second (default 15)

  Returns:
    Vector of Uint8ClampedArrays (RGBA pixels)

  Example:
    (def frames (generate-test-frames 60 256 256 {:amplitude 2 :frequency 0.3}))
    ;; Circle moves ±2 pixels at 0.3 Hz (18 bpm)"
  [frame-count width height {:keys [amplitude frequency fps]
                             :or {amplitude 2
                                  frequency 0.3
                                  fps 15}}]
  (vec
   (for [t (range frame-count)]
     (let [;; Create blank frame
           pixels (js/Uint8ClampedArray. (* width height 4))

           ;; Circle position (oscillates sinusoidally)
           time-s (/ t fps)
           offset (* amplitude (js/Math.sin (* 2 js/Math.PI frequency time-s)))
           cx (+ (/ width 2) offset)
           cy (/ height 2)
           radius 20]

       ;; Draw white circle on black background
       (dotimes [y height]
         (dotimes [x width]
           (let [dx (- x cx)
                 dy (- y cy)
                 dist (js/Math.sqrt (+ (* dx dx) (* dy dy)))
                 inside? (< dist radius)
                 idx (* (+ (* y width) x) 4)]

             ;; RGBA
             (aset pixels idx (if inside? 255 0))       ;; R
             (aset pixels (+ idx 1) (if inside? 255 0)) ;; G
             (aset pixels (+ idx 2) (if inside? 255 0)) ;; B
             (aset pixels (+ idx 3) 255))))             ;; A

       pixels))))
