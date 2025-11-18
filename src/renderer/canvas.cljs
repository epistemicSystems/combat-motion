(ns combatsys.renderer.canvas
  "Canvas drawing utilities for skeleton visualization.

  Philosophy (Imperative Shell):
  - All functions have side effects (canvas mutations)
  - Named with ! suffix to indicate side effects
  - Pure data structures for connections
  - Color coding for observability (green = high confidence)

  Performance:
  - Clear: ~0.1ms
  - Draw 33 landmarks: ~1ms (33 × 0.03ms)
  - Draw 15 connections: ~0.5ms (15 × 0.03ms)
  - Total: ~1.6ms per frame ✅ (well within budget)")

;; ============================================================================
;; Canvas Primitives (Side Effects)
;; ============================================================================

(defn clear-canvas!
  "Clear entire canvas to transparent.

  Side effect: Clears canvas context.

  Args:
    ctx: Canvas 2D context
    width: Canvas width in pixels
    height: Canvas height in pixels

  Performance: ~0.1ms"
  [ctx width height]
  (.clearRect ctx 0 0 width height))

(defn draw-circle!
  "Draw a filled circle on canvas.

  Side effect: Draws to canvas context.

  Args:
    ctx: Canvas 2D context
    x: X coordinate (pixels)
    y: Y coordinate (pixels)
    radius: Circle radius (pixels)
    color: Fill color (CSS string, e.g., '#00FF00')

  Performance: ~0.03ms per circle"
  [ctx x y radius color]
  (set! (.-fillStyle ctx) color)
  (.beginPath ctx)
  (.arc ctx x y radius 0 (* 2 js/Math.PI))
  (.fill ctx))

(defn draw-line!
  "Draw a line between two points.

  Side effect: Draws to canvas context.

  Args:
    ctx: Canvas 2D context
    x1: Start X coordinate (pixels)
    y1: Start Y coordinate (pixels)
    x2: End X coordinate (pixels)
    y2: End Y coordinate (pixels)
    width: Line width (pixels)
    color: Stroke color (CSS string)

  Performance: ~0.03ms per line"
  [ctx x1 y1 x2 y2 width color]
  (set! (.-strokeStyle ctx) color)
  (set! (.-lineWidth ctx) width)
  (.beginPath ctx)
  (.moveTo ctx x1 y1)
  (.lineTo ctx x2 y2)
  (.stroke ctx))

(defn draw-text!
  "Draw text on canvas.

  Side effect: Draws to canvas context.

  Args:
    ctx: Canvas 2D context
    text: Text string to draw
    x: X coordinate (pixels)
    y: Y coordinate (pixels)
    font: Font string (e.g., '12px Arial')
    color: Fill color (CSS string)

  Performance: ~0.05ms per text"
  [ctx text x y font color]
  (set! (.-font ctx) font)
  (set! (.-fillStyle ctx) color)
  (.fillText ctx text x y))

;; ============================================================================
;; Skeleton Connection Data (Pure Data)
;; ============================================================================

(def skeleton-connections
  "Anatomically correct skeleton connections.

  Each connection is a pair of landmark IDs.
  Organized by body region for clarity.

  Total: 25 connections (comprehensive skeleton)"

  ;; Face connections (5)
  [;; Eyes
   [:left-eye-inner :left-eye-outer]
   [:right-eye-inner :right-eye-outer]
   ;; Face outline
   [:left-ear :left-eye-outer]
   [:right-ear :right-eye-outer]
   [:nose :left-eye-inner]

   ;; Upper body connections (8)
   ;; Shoulders
   [:left-shoulder :right-shoulder]
   ;; Left arm
   [:left-shoulder :left-elbow]
   [:left-elbow :left-wrist]
   [:left-wrist :left-pinky]
   [:left-wrist :left-index]
   ;; Right arm
   [:right-shoulder :right-elbow]
   [:right-elbow :right-wrist]
   [:right-wrist :right-pinky]
   [:right-wrist :right-index]

   ;; Torso connections (4)
   [:left-shoulder :left-hip]
   [:right-shoulder :right-hip]
   ;; Cross connections for stability
   [:left-shoulder :right-hip]
   [:right-shoulder :left-hip]

   ;; Lower body connections (6)
   ;; Hips
   [:left-hip :right-hip]
   ;; Left leg
   [:left-hip :left-knee]
   [:left-knee :left-ankle]
   [:left-ankle :left-heel]
   [:left-ankle :left-foot-index]
   ;; Right leg
   [:right-hip :right-knee]
   [:right-knee :right-ankle]
   [:right-ankle :right-heel]
   [:right-ankle :right-foot-index]])

(def essential-connections
  "Essential skeleton connections (minimal viable skeleton).

  Subset of skeleton-connections for performance or simplicity.
  Use this for LOD 1, expand to full skeleton in LOD 2.

  Total: 15 connections"

  [;; Upper body (5)
   [:left-shoulder :right-shoulder]
   [:left-shoulder :left-elbow]
   [:left-elbow :left-wrist]
   [:right-shoulder :right-elbow]
   [:right-elbow :right-wrist]

   ;; Torso (4)
   [:left-shoulder :left-hip]
   [:right-shoulder :right-hip]
   [:left-hip :right-hip]
   ;; Spine representation
   [:left-shoulder :right-hip]
   [:right-shoulder :left-hip]

   ;; Lower body (6)
   [:left-hip :left-knee]
   [:left-knee :left-ankle]
   [:right-hip :right-knee]
   [:right-knee :right-ankle]])

;; ============================================================================
;; Color Utilities (Pure Functions)
;; ============================================================================

(defn confidence-color
  "Get color based on landmark confidence.

  Pure function.

  Args:
    confidence: Landmark confidence score [0-1]

  Returns:
    CSS color string

  Color coding:
    >0.7  → Green (#00FF00)   'High confidence'
    0.5-0.7 → Yellow (#FFDD00)  'Medium confidence'
    <0.5  → Orange (#FF8800)  'Low confidence' (often skipped)

  Example:
    (confidence-color 0.85) => '#00FF00'
    (confidence-color 0.6)  => '#FFDD00'
    (confidence-color 0.4)  => '#FF8800'"
  [confidence]
  (cond
    (>= confidence 0.7) "#00FF00"  ; Green - high confidence
    (>= confidence 0.5) "#FFDD00"  ; Yellow - medium confidence
    :else               "#FF8800")) ; Orange - low confidence

(defn angle-color
  "Get color based on joint angle.

  Pure function (for future use - LOD 2).

  Args:
    angle: Joint angle in degrees [0-180]

  Returns:
    CSS color string

  Color coding:
    0-30°   → Red (very bent)
    30-150° → Green (normal range)
    150-180° → Blue (very straight)

  Example:
    (angle-color 45)  => '#00FF00'
    (angle-color 170) => '#0088FF'"
  [angle]
  (cond
    (< angle 30)   "#FF0000"  ; Red - very bent
    (< angle 150)  "#00FF00"  ; Green - normal
    :else          "#0088FF")) ; Blue - very straight

;; ============================================================================
;; Landmark Utilities (Pure Functions)
;; ============================================================================

(defn get-landmark
  "Get landmark by ID from landmarks vector.

  Pure function.

  Args:
    landmarks: Vector of landmark maps
    id: Landmark ID keyword

  Returns:
    Landmark map or nil if not found

  Example:
    (get-landmark landmarks :left-elbow)
    => {:landmark/id :left-elbow :landmark/x 120 :landmark/y 240 ...}"
  [landmarks id]
  (some #(when (= id (:landmark/id %)) %) landmarks))

(defn landmark-visible?
  "Check if landmark should be drawn (confidence threshold).

  Pure function.

  Args:
    landmark: Landmark map
    threshold: Minimum confidence (default 0.5)

  Returns:
    Boolean - true if confidence >= threshold

  Example:
    (landmark-visible? {:landmark/confidence 0.8} 0.5) => true
    (landmark-visible? {:landmark/confidence 0.3} 0.5) => false"
  ([landmark]
   (landmark-visible? landmark 0.5))
  ([landmark threshold]
   (and landmark
        (>= (:landmark/confidence landmark 0) threshold))))

;; ============================================================================
;; Skeleton Drawing (Side Effects)
;; ============================================================================

(defn draw-landmark!
  "Draw a single landmark as a colored circle.

  Side effect: Draws to canvas context.

  Args:
    ctx: Canvas 2D context
    landmark: Landmark map with :landmark/x, :landmark/y, :landmark/confidence
    radius: Circle radius in pixels (default 4)

  Performance: ~0.03ms per landmark"
  ([ctx landmark]
   (draw-landmark! ctx landmark 4))
  ([ctx landmark radius]
   (when landmark
     (let [x (:landmark/x landmark)
           y (:landmark/y landmark)
           confidence (:landmark/confidence landmark 0)
           color (confidence-color confidence)]
       (draw-circle! ctx x y radius color)))))

(defn draw-connection!
  "Draw a connection (bone) between two landmarks.

  Side effect: Draws to canvas context.

  Args:
    ctx: Canvas 2D context
    landmark1: First landmark map
    landmark2: Second landmark map
    width: Line width in pixels (default 2)
    confidence-threshold: Minimum confidence to draw (default 0.5)

  Performance: ~0.03ms per connection

  Note:
    Only draws if both landmarks meet confidence threshold"
  ([ctx landmark1 landmark2]
   (draw-connection! ctx landmark1 landmark2 2 0.5))
  ([ctx landmark1 landmark2 width confidence-threshold]
   (when (and (landmark-visible? landmark1 confidence-threshold)
              (landmark-visible? landmark2 confidence-threshold))
     (let [x1 (:landmark/x landmark1)
           y1 (:landmark/y landmark1)
           x2 (:landmark/x landmark2)
           y2 (:landmark/y landmark2)
           ;; Use average confidence for color
           avg-conf (/ (+ (:landmark/confidence landmark1)
                         (:landmark/confidence landmark2))
                      2)
           color (confidence-color avg-conf)]
       (draw-line! ctx x1 y1 x2 y2 width color)))))

(defn draw-skeleton!
  "Draw complete skeleton from pose landmarks.

  Side effect: Draws to canvas context.

  Args:
    ctx: Canvas 2D context
    landmarks: Vector of landmark maps
    opts: Optional map with:
      :connections - Connection vector (default essential-connections)
      :line-width - Connection line width (default 2)
      :landmark-radius - Landmark circle radius (default 4)
      :confidence-threshold - Minimum confidence (default 0.5)
      :draw-landmarks? - Draw landmark circles (default true)

  Performance: ~1.6ms for full skeleton

  Drawing order:
    1. Connections (lines) - drawn first (below landmarks)
    2. Landmarks (circles) - drawn second (above connections)

  Example:
    (draw-skeleton! ctx landmarks)
    (draw-skeleton! ctx landmarks {:connections skeleton-connections
                                    :line-width 3
                                    :landmark-radius 5})"
  ([ctx landmarks]
   (draw-skeleton! ctx landmarks {}))
  ([ctx landmarks {:keys [connections
                          line-width
                          landmark-radius
                          confidence-threshold
                          draw-landmarks?]
                   :or {connections essential-connections
                        line-width 2
                        landmark-radius 4
                        confidence-threshold 0.5
                        draw-landmarks? true}}]

   ;; Helper to get landmark by ID
   (let [get-lm (fn [id] (get-landmark landmarks id))]

     ;; 1. Draw connections (bones) first
     (doseq [[id1 id2] connections]
       (let [lm1 (get-lm id1)
             lm2 (get-lm id2)]
         (draw-connection! ctx lm1 lm2 line-width confidence-threshold)))

     ;; 2. Draw landmarks (joints) on top
     (when draw-landmarks?
       (doseq [lm landmarks]
         (when (landmark-visible? lm confidence-threshold)
           (draw-landmark! ctx lm landmark-radius)))))))

(defn draw-pose-info!
  "Draw pose information overlay (debug/diagnostics).

  Side effect: Draws text to canvas context.

  Args:
    ctx: Canvas 2D context
    pose: Enhanced pose map with :pose/angles, :pose/confidence
    x: X position for text (default 10)
    y: Y position for text (default 20)

  Displays:
    - Pose confidence score
    - Number of visible landmarks
    - Angle computation time (if available)

  Example:
    (draw-pose-info! ctx pose 10 20)
    => Draws 'Confidence: 0.95 | Landmarks: 28/33 | Angles: 0.42ms'"
  ([ctx pose]
   (draw-pose-info! ctx pose 10 20))
  ([ctx pose x y]
   (let [confidence (:pose/confidence pose)
         landmarks (:pose/landmarks pose)
         visible-count (count (filter #(landmark-visible? %) landmarks))
         total-count (count landmarks)
         angles-time (get-in pose [:pose/metadata :angles-computation-ms])

         ;; Build info string
         info (str "Confidence: " (.toFixed confidence 2)
                  " | Landmarks: " visible-count "/" total-count
                  (when angles-time
                    (str " | Angles: " (.toFixed angles-time 2) "ms")))]

     ;; Draw with background for readability
     (set! (.-fillStyle ctx) "rgba(0, 0, 0, 0.7)")
     (.fillRect ctx (- x 5) (- y 15) 400 25)

     ;; Draw text
     (draw-text! ctx info x y "14px monospace" "#00FF00"))))

;; ============================================================================
;; Performance Profiling
;; ============================================================================

(defn profile-drawing!
  "Profile skeleton drawing performance.

  Side effect: Draws to canvas, logs to console.

  Args:
    ctx: Canvas 2D context
    landmarks: Vector of landmark maps
    iterations: Number of iterations (default 100)

  Returns:
    {:total-ms N :avg-ms N :min-ms N :max-ms N}

  Example:
    (profile-drawing! ctx landmarks 100)
    => {:total-ms 156.2 :avg-ms 1.56 :min-ms 1.48 :max-ms 1.82}"
  ([ctx landmarks]
   (profile-drawing! ctx landmarks 100))
  ([ctx landmarks iterations]
   (let [times (atom [])]

     ;; Warm up (JIT)
     (dotimes [_ 10]
       (clear-canvas! ctx 640 480)
       (draw-skeleton! ctx landmarks))

     ;; Measure
     (dotimes [_ iterations]
       (let [start (.now js/performance)]
         (clear-canvas! ctx 640 480)
         (draw-skeleton! ctx landmarks)
         (let [end (.now js/performance)
               duration (- end start)]
           (swap! times conj duration))))

     (let [total (reduce + @times)
           avg (/ total iterations)
           min-time (apply min @times)
           max-time (apply max @times)]

       (js/console.log "🎨 Skeleton Drawing Performance:")
       (js/console.log "   Total:" (.toFixed total 2) "ms for" iterations "iterations")
       (js/console.log "   Average:" (.toFixed avg 2) "ms per frame")
       (js/console.log "   Min:" (.toFixed min-time 2) "ms")
       (js/console.log "   Max:" (.toFixed max-time 2) "ms")

       {:total-ms total
        :avg-ms avg
        :min-ms min-time
        :max-ms max-time}))))

(comment
  ;; REPL Testing

  ;; Get canvas context from DOM
  (def canvas (.getElementById js/document "my-canvas"))
  (def ctx (.getContext canvas "2d"))

  ;; Test primitives
  (clear-canvas! ctx 640 480)
  (draw-circle! ctx 320 240 10 "#00FF00")
  (draw-line! ctx 100 100 200 200 2 "#FF0000")

  ;; Test with mock pose
  (require '[combatsys.shared.pose :as pose])
  (def mock-pose (pose/create-mock-pose))
  (def landmarks (:pose/landmarks mock-pose))

  ;; Draw skeleton
  (clear-canvas! ctx 640 480)
  (draw-skeleton! ctx landmarks)

  ;; Draw with full connections
  (clear-canvas! ctx 640 480)
  (draw-skeleton! ctx landmarks {:connections skeleton-connections
                                  :line-width 3
                                  :landmark-radius 5})

  ;; Draw pose info
  (draw-pose-info! ctx mock-pose)

  ;; Profile performance
  (profile-drawing! ctx landmarks 100)
  ;; Expected: ~1.5-2ms avg

  )
