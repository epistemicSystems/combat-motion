# TASKS.md - Granular Task Breakdown

## Role-Play Context

You are orchestrating a team of:
- **Rich Hickey** (data-first design)
- **John Carmack** (performance pragmatism)
- **10x Y-Combinator hackers** (ship fast)
- **10x Google engineers** (production quality)

For each task, channel the appropriate team member's expertise.

---

## LOD 1: Real Camera + MediaPipe (Days 2-4)

**Goal**: Replace mock data with real camera capture and pose estimation

### Task 1.1: Camera Integration (6-8 hours)

**Assignee**: Y-Combinator Hacker (ship fast, iterate)

**Objective**: Get camera frames into the app

**Files to create/modify**:
- `src/renderer/camera.cljs` (new)
- `src/renderer/state.cljs` (modify event handlers)

**Steps**:
1. Research: How does getUserMedia work in Electron?
2. Plan: Where does camera I/O fit in our architecture?
3. Implement:
   ```clojure
   (ns combatsys.renderer.camera
     "Camera capture via getUserMedia.
      
      Imperative shell - side effects isolated here.")
   
   (defn init-camera!
     "Initialize webcam. Returns promise of video element.
      Side effect: Accesses navigator.mediaDevices"
     []
     (js/Promise.
      (fn [resolve reject]
        (-> js/navigator
            .-mediaDevices
            (.getUserMedia #js {:video true})
            (.then (fn [stream]
                     (let [video (js/document.createElement "video")]
                       (set! (.-srcObject video) stream)
                       (.play video)
                       (resolve video))))
            (.catch reject)))))
   
   (defn capture-frame
     "Capture current frame from video element to canvas.
      Returns canvas element with frame data."
     [video-element]
     (let [canvas (js/document.createElement "canvas")
           ctx (.getContext canvas "2d")]
       (set! (.-width canvas) (.-videoWidth video-element))
       (set! (.-height canvas) (.-videoHeight video-element))
       (.drawImage ctx video-element 0 0)
       canvas))
   ```

4. Test in REPL:
   ```clojure
   (def video (atom nil))
   (-> (camera/init-camera!)
       (.then (fn [v] (reset! video v))))
   ;; Check @video in browser console
   ```

5. Wire into state:
   ```clojure
   ;; In state.cljs
   (rf/reg-event-fx
    ::start-camera
    (fn [{:keys [db]} _]
      {:camera/init nil  ; Effect handler
       :db (assoc-in db [:capture :mode] :initializing)}))
   ```

**Success criteria**:
- ✅ Click button → camera permission dialog
- ✅ Grant permission → video stream starts
- ✅ Capture frame → canvas has image data

**Time estimate**: 6-8 hours

---

### Task 1.2: MediaPipe Integration (6-8 hours)

**Assignee**: Google Engineer (production quality, handle edge cases)

**Objective**: Extract 2D pose landmarks from camera frames

**Files to create/modify**:
- `src/renderer/mediapipe.cljs` (new)
- `src/shared/schema.cljs` (add MediaPipe → EDN conversion)

**Steps**:
1. Research: Read MediaPipe pose-detection docs
2. Plan: How do we convert TensorFlow.js objects to EDN?
3. Install dependency:
   ```bash
   npm install @tensorflow-models/pose-detection
   ```

4. Implement:
   ```clojure
   (ns combatsys.renderer.mediapipe
     "MediaPipe pose estimation via TensorFlow.js."
     (:require ["@tensorflow-models/pose-detection" :as pose]))
   
   (defonce detector (atom nil))
   
   (defn init-detector!
     "Initialize MediaPipe detector. Returns promise.
      Side effect: Loads ML model."
     []
     (-> (pose/createDetector
          pose/SupportedModels.BlazePose
          #js {:runtime "tfjs"
               :modelType "full"
               :enableSmoothing true})
         (.then (fn [d]
                  (reset! detector d)
                  d))))
   
   (defn estimate-pose
     "Estimate pose from image element. Returns promise of EDN pose.
      Side effect: Runs ML inference."
     [image-element]
     (-> (.estimatePoses @detector image-element)
         (.then (fn [poses]
                  (when-let [pose (first poses)]
                    (tfjs-pose->edn pose))))))
   
   (defn tfjs-pose->edn
     "Convert TensorFlow.js pose to EDN schema.
      Pure function."
     [tfjs-pose]
     {:pose/landmarks
      (mapv (fn [kp]
              {:landmark/id (keyword (.-name kp))
               :landmark/x (.-x kp)
               :landmark/y (.-y kp)
               :landmark/z (or (.-z kp) 0)
               :landmark/visibility (or (.-score kp) 0)})
            (.-keypoints tfjs-pose))
      :pose/confidence (.-score tfjs-pose)
      :pose/timestamp-ms (.now js/Date)})
   ```

5. Test in REPL:
   ```clojure
   ;; Initialize
   (-> (mediapipe/init-detector!)
       (.then (fn [_] (println "Detector ready"))))
   
   ;; Capture frame and estimate pose
   (let [canvas (camera/capture-frame @video)]
     (-> (mediapipe/estimate-pose canvas)
         (.then (fn [pose]
                  (println "Detected pose:" pose)
                  (println "Num landmarks:" (count (:pose/landmarks pose)))))))
   ```

6. Validate schema conformance:
   ```clojure
   (defn validate-mediapipe-output [pose]
     (schema/valid-pose? pose))
   ```

**Success criteria**:
- ✅ Detector initializes without errors
- ✅ Pose estimation returns 33 landmarks
- ✅ Output conforms to EDN schema
- ✅ Confidence scores reasonable (>0.7 for visible landmarks)

**Edge cases to handle**:
- No person in frame → return nil gracefully
- Multiple people → take first person only
- Low confidence landmarks → mark as low visibility

**Time estimate**: 6-8 hours

---

### Task 1.3: Real-Time Pose Processing (4-6 hours)

**Assignee**: John Carmack (performance-aware implementation)

**Objective**: Process poses at 30fps without blocking UI

**Files to create/modify**:
- `src/shared/pose.cljs` (new - pure functions)
- `src/renderer/state.cljs` (add pose processing events)

**Steps**:
1. **Think hard**: How do we achieve 30fps with 30ms pose estimation?
   - Option A: Process every frame (risky, might miss budget)
   - Option B: Skip frames (process every 2nd/3rd frame)
   - Option C: Async queue (process frames as fast as possible)
   
   **Decision**: Start with Option B (skip frames), measure, optimize later.

2. Implement pure pose processing:
   ```clojure
   (ns combatsys.shared.pose
     "Pure functions for pose data processing.")
   
   (defn normalize-coordinates
     "Convert pixel coordinates to normalized [0-1] space.
      Pure function."
     [landmarks width height]
     (mapv (fn [lm]
             (-> lm
                 (update :landmark/x #(/ % width))
                 (update :landmark/y #(/ % height))))
           landmarks))
   
   (defn compute-joint-angle
     "Compute angle between three landmarks (e.g., elbow angle).
      Returns angle in degrees.
      Pure function."
     [landmark-a landmark-b landmark-c]
     (let [ba [(- (:landmark/x landmark-a) (:landmark/x landmark-b))
               (- (:landmark/y landmark-a) (:landmark/y landmark-b))]
           bc [(- (:landmark/x landmark-c) (:landmark/x landmark-b))
               (- (:landmark/y landmark-c) (:landmark/y landmark-b))]
           dot (+ (* (first ba) (first bc))
                  (* (second ba) (second bc)))
           mag-ba (Math/sqrt (+ (* (first ba) (first ba))
                               (* (second ba) (second ba))))
           mag-bc (Math/sqrt (+ (* (first bc) (first bc))
                               (* (second bc) (second bc))))
           cos-angle (/ dot (* mag-ba mag-bc))
           angle-rad (Math/acos cos-angle)]
       (* angle-rad (/ 180 Math/PI))))
   
   (defn compute-all-angles
     "Compute all joint angles from pose.
      Returns map of {:left-elbow angle :right-knee angle ...}
      Pure function."
     [pose]
     (let [landmarks (:pose/landmarks pose)
           get-lm (fn [id] (first (filter #(= id (:landmark/id %)) landmarks)))]
       {:left-elbow
        (compute-joint-angle
         (get-lm :left-shoulder)
         (get-lm :left-elbow)
         (get-lm :left-wrist))
        
        :right-elbow
        (compute-joint-angle
         (get-lm :right-shoulder)
         (get-lm :right-elbow)
         (get-lm :right-wrist))
        
        ;; Add more angles as needed
        }))
   ```

3. Add to frame processing:
   ```clojure
   (defn process-frame
     "Process raw camera frame → EDN frame with derived metrics.
      Coordinates computation, angle extraction.
      Pure function."
     [frame-index timestamp-ms pose]
     (schema/frame
      frame-index
      timestamp-ms
      (assoc pose :derived {:angles (compute-all-angles pose)})))
   ```

4. Profile performance:
   ```clojure
   (defn profile-pose-processing []
     (let [pose (mocks/mock-standing-pose)
           start (.now js/performance)]
       (dotimes [_ 1000]
         (compute-all-angles pose))
       (let [end (.now js/performance)]
         (println "1000 angle computations:" (- end start) "ms")
         (println "Per-frame:" (/ (- end start) 1000) "ms"))))
   ```

**Success criteria**:
- ✅ Angle computation takes <1ms per frame
- ✅ Normalized coordinates correct
- ✅ Angles match manual verification (±5 degrees)

**Performance targets**:
- Frame processing: <1ms
- Total pipeline: <50ms (including MediaPipe)

**Time estimate**: 4-6 hours

---

### Task 1.4: Skeleton Visualization (4-6 hours)

**Assignee**: Y-Combinator Hacker (make it visual, iterate fast)

**Objective**: Draw live skeleton overlay on video feed

**Files to create/modify**:
- `src/renderer/canvas.cljs` (new)
- `src/renderer/views.cljs` (enhance skeleton-canvas component)

**Steps**:
1. Implement canvas drawing utils:
   ```clojure
   (ns combatsys.renderer.canvas
     "Canvas drawing utilities for skeleton visualization.")
   
   (defn draw-landmark
     "Draw a single landmark as a circle.
      Side effect: Mutates canvas context."
     [ctx x y radius color]
     (set! (.-fillStyle ctx) color)
     (.beginPath ctx)
     (.arc ctx x y radius 0 (* 2 Math/PI))
     (.fill ctx))
   
   (defn draw-line
     "Draw a line between two points.
      Side effect: Mutates canvas context."
     [ctx x1 y1 x2 y2 width color]
     (set! (.-strokeStyle ctx) color)
     (set! (.-lineWidth ctx) width)
     (.beginPath ctx)
     (.moveTo ctx x1 y1)
     (.lineTo ctx x2 y2)
     (.stroke ctx))
   
   (defn draw-skeleton
     "Draw full skeleton from pose landmarks.
      Side effect: Mutates canvas context."
     [ctx landmarks canvas-width canvas-height]
     (let [get-lm (fn [id]
                    (first (filter #(= id (:landmark/id %)) landmarks)))
           to-pixel (fn [lm]
                      [(* (:landmark/x lm) canvas-width)
                       (* (:landmark/y lm) canvas-height)])]
       
       ;; Draw landmarks
       (doseq [lm landmarks]
         (let [[x y] (to-pixel lm)
               color (if (> (:landmark/visibility lm) 0.7)
                      "#00FF00"  ; Green for high confidence
                      "#FFAA00")] ; Orange for low confidence
           (draw-landmark ctx x y 5 color)))
       
       ;; Draw connections (bones)
       (let [connections
             [[:left-shoulder :left-elbow]
              [:left-elbow :left-wrist]
              [:right-shoulder :right-elbow]
              [:right-elbow :right-wrist]
              [:left-hip :left-knee]
              [:left-knee :left-ankle]
              [:right-hip :right-knee]
              [:right-knee :right-ankle]
              [:left-shoulder :right-shoulder]
              [:left-hip :right-hip]]]
         (doseq [[id1 id2] connections]
           (let [lm1 (get-lm id1)
                 lm2 (get-lm id2)]
             (when (and lm1 lm2
                       (> (:landmark/visibility lm1) 0.5)
                       (> (:landmark/visibility lm2) 0.5))
               (let [[x1 y1] (to-pixel lm1)
                     [x2 y2] (to-pixel lm2)]
                 (draw-line ctx x1 y1 x2 y2 2 "#00FF00"))))))))
   ```

2. Wire into UI component:
   ```clojure
   ;; In views.cljs
   (defn skeleton-canvas []
     (let [video-ref (atom nil)
           canvas-ref (atom nil)
           frame-count (atom 0)]
       (r/create-class
        {:component-did-mount
         (fn [this]
           (reset! canvas-ref (r/dom-node this))
           (start-video-capture! video-ref canvas-ref frame-count))
         
         :reagent-render
         (fn []
           [:canvas
            {:width 640
             :height 480
             :style {:border "2px solid #333"
                     :background-color "#000"}}])})))
   
   (defn start-video-capture!
     "Start camera and draw loop.
      Side effects: Camera I/O, canvas drawing, state updates."
     [video-ref canvas-ref frame-count]
     (-> (camera/init-camera!)
         (.then (fn [video]
                  (reset! video-ref video)
                  (mediapipe/init-detector!)))
         (.then (fn [_]
                  (capture-loop! video-ref canvas-ref frame-count)))))
   
   (defn capture-loop!
     "Main capture loop: frame → pose → draw → state update.
      Runs at ~15fps (process every 2nd frame)."
     [video-ref canvas-ref frame-count]
     (let [video @video-ref
           canvas @canvas-ref
           ctx (.getContext canvas "2d")]
       
       ;; Draw video frame to canvas
       (.drawImage ctx video 0 0 640 480)
       
       ;; Process every 2nd frame
       (when (zero? (mod @frame-count 2))
         (let [temp-canvas (camera/capture-frame video)]
           (-> (mediapipe/estimate-pose temp-canvas)
               (.then (fn [pose]
                        (when pose
                          ;; Draw skeleton overlay
                          (canvas/draw-skeleton
                           ctx
                           (:pose/landmarks pose)
                           640 480)
                          
                          ;; Update state
                          (rf/dispatch [::state/append-frame
                                       (process-frame @frame-count
                                                     (* @frame-count 33)
                                                     pose)])))))))
       
       (swap! frame-count inc)
       (js/requestAnimationFrame #(capture-loop! video-ref canvas-ref frame-count))))
   ```

**Success criteria**:
- ✅ Video feed displays in canvas
- ✅ Skeleton overlay draws on top of video
- ✅ Green lines for high-confidence landmarks
- ✅ Orange lines for low-confidence
- ✅ Runs smoothly at ~15fps

**Time estimate**: 4-6 hours

---

### Task 1.5: Session Recording (4-6 hours)

**Assignee**: Google Engineer (robust file I/O, error handling)

**Objective**: Save sessions to disk for offline analysis

**Files to create/modify**:
- `src/renderer/persistence.cljs` (new)
- `src/main/files.cljs` (new - Electron file I/O)

**Steps**:
1. Implement session serialization:
   ```clojure
   (ns combatsys.renderer.persistence
     "Session persistence to/from file system.")
   
   (defn session->edn-string
     "Convert session map to EDN string.
      Pure function."
     [session]
     (pr-str session))
   
   (defn edn-string->session
     "Parse EDN string to session map.
      Pure function."
     [edn-str]
     (cljs.reader/read-string edn-str))
   ```

2. Implement file I/O (Electron main process):
   ```clojure
   ;; In src/main/files.cljs
   (ns combatsys.main.files
     "File system operations via Electron."
     (:require ["fs" :as fs]
               ["path" :as path]))
   
   (defonce sessions-dir
     (path/join (js/require "electron").app.getPath "userData")
                "sessions"))
   
   (defn ensure-sessions-dir!
     "Create sessions directory if it doesn't exist.
      Side effect: File system write."
     []
     (when-not (fs/existsSync sessions-dir)
       (fs/mkdirSync sessions-dir #js {:recursive true})))
   
   (defn save-session!
     "Save session to disk as EDN file.
      Side effect: File system write."
     [session]
     (ensure-sessions-dir!)
     (let [session-id (str (:session/id session))
           filename (str session-id ".edn")
           filepath (path/join sessions-dir filename)
           content (pr-str session)]
       (fs/writeFileSync filepath content)))
   
   (defn load-session!
     "Load session from disk by ID.
      Side effect: File system read."
     [session-id]
     (let [filename (str session-id ".edn")
           filepath (path/join sessions-dir filename)]
       (when (fs/existsSync filepath)
         (let [content (fs/readFileSync filepath "utf8")]
           (cljs.reader/read-string content)))))
   
   (defn list-sessions!
     "List all saved session IDs.
      Side effect: File system read."
     []
     (ensure-sessions-dir!)
     (let [files (fs/readdirSync sessions-dir)]
       (mapv #(subs % 0 (- (count %) 4))  ; Remove .edn extension
             (filter #(.endsWith % ".edn") files))))
   ```

3. Wire into UI:
   ```clojure
   ;; Add save button
   [button {:label "Save Session"
            :on-click #(rf/dispatch [::state/save-current-session])}]
   
   ;; Event handler
   (rf/reg-event-fx
    ::save-current-session
    (fn [{:keys [db]} _]
      (let [session-id (:current-session-id db)
            session (get-in db [:sessions session-id])]
        {:file/save-session session})))  ; Effect handler calls main process
   ```

**Success criteria**:
- ✅ Record 30s session → click "Save" → file appears in sessions dir
- ✅ Load saved session → timeline and analysis restored
- ✅ Session browser shows all saved sessions
- ✅ File size reasonable (<10MB for 30s @ 15fps)

**Error handling**:
- Disk full → show error dialog
- Corrupted EDN → graceful failure with error message
- Permissions error → suggest fix

**Time estimate**: 4-6 hours

---

## LOD 1 Integration & Testing (Day 4)

### Task 1.6: End-to-End Integration Test (4 hours)

**Assignee**: Full team (integration testing)

**Objective**: Verify complete LOD 1 pipeline works

**Test script**:
```bash
# 1. Start app
npm start

# 2. Manual test checklist
✅ Camera permission dialog appears
✅ Grant permission → video feed starts
✅ Skeleton overlay appears within 5 seconds
✅ Skeleton tracks body movements accurately
✅ Click "Start Recording" → timer starts
✅ Record for 30 seconds
✅ Click "Stop Recording" → session saved
✅ Session appears in sidebar
✅ Click session → timeline shows 450+ frames (30s @ 15fps)
✅ Drag timeline scrubber → skeleton updates
✅ Close app → restart → session still available
```

**Performance validation**:
```clojure
;; In REPL
(require '[combatsys.renderer.state :as state])

;; Check frame rate
(let [session @(rf/subscribe [::state/current-session])
      timeline (:session/timeline session)
      duration (:session/duration-ms session)
      fps (/ (count timeline) (/ duration 1000))]
  (println "Actual FPS:" fps))
;; Expected: 13-18 fps (we skip frames)

;; Check pose quality
(let [session @(rf/subscribe [::state/current-session])
      timeline (:session/timeline session)
      avg-confidence (/ (reduce + (map #(get-in % [:frame/pose :pose/confidence]) timeline))
                       (count timeline))]
  (println "Avg pose confidence:" avg-confidence))
;; Expected: >0.8
```

**Success criteria**:
- ✅ All manual tests pass
- ✅ FPS >13
- ✅ Average pose confidence >0.8
- ✅ No crashes or errors during 5-minute session

**Time estimate**: 4 hours

---

## Summary: LOD 1 Timeline

| Task | Assignee | Hours | Day |
|------|----------|-------|-----|
| 1.1 Camera Integration | YC Hacker | 6-8 | 2 |
| 1.2 MediaPipe Integration | Google Eng | 6-8 | 2-3 |
| 1.3 Pose Processing | Carmack | 4-6 | 3 |
| 1.4 Skeleton Visualization | YC Hacker | 4-6 | 3 |
| 1.5 Session Recording | Google Eng | 4-6 | 4 |
| 1.6 Integration Testing | Full Team | 4 | 4 |
| **Total** | | **28-38 hours** | **3 days** |

---

## Next: LOD 2 Tasks (Coming Soon)

- Torso motion extraction (breathing analysis)
- FFT-based breathing rate detection
- Fatigue window detection
- Coaching insights generation

See PLAN.md for LOD 2-6 roadmap.

---

**Remember**: Use "think hard" before complex implementations. Ask clarifying questions. Test in REPL continuously.
