# WORKFLOWS.md - Development Workflows for Claude Code

## Workflow 1: Adding a New Pure Function Analyzer

**Use case**: Add gait analysis, balance detection, or any new analyzer module

**Team lead mindset**: Rich Hickey (data-first, pure functions)

### Steps

1. **Think hard**: What data transformations are needed?
   ```
   Input: session with timeline
   Output: session with new analysis populated
   ```

2. **Create analyzer module**:
   ```bash
   # Create file
   touch src/shared/gait.cljs
   ```

3. **Write pure function**:
   ```clojure
   (ns combatsys.gait
     "Gait analysis from pose timeline.
      
      Pure functional module - no side effects."
     (:require [combatsys.schema :as schema]))
   
   (defn extract-foot-positions
     "Extract left/right foot landmarks over time.
      Pure function."
     [timeline]
     (mapv (fn [frame]
             (let [landmarks (get-in frame [:frame/pose :pose/landmarks])
                   get-lm (fn [id]
                           (first (filter #(= id (:landmark/id %)) landmarks)))]
               {:timestamp-ms (:frame/timestamp-ms frame)
                :left-foot (get-lm :left-ankle)
                :right-foot (get-lm :right-ankle)}))
           timeline))
   
   (defn compute-step-length
     "Compute average step length from foot positions.
      Pure function."
     [foot-positions]
     ;; Implementation...
     0.68)  ; Stub: return meters
   
   (defn compute-cadence
     "Compute steps per minute from foot positions.
      Pure function."
     [foot-positions duration-ms]
     ;; Implementation...
     115)  ; Stub: return steps/min
   
   (defn analyze
     "Main entry point: analyze gait patterns.
      
      Input: session map with :session/timeline
      Output: same session with :session/analysis :gait populated
      
      Pure function - no side effects."
     [session]
     (let [timeline (:session/timeline session)
           duration (:session/duration-ms session)
           foot-positions (extract-foot-positions timeline)
           step-length (compute-step-length foot-positions)
           cadence (compute-cadence foot-positions duration)]
       
       (assoc-in session
                 [:session/analysis :gait]
                 {:step-length-m step-length
                  :cadence-steps-per-min cadence
                  :symmetry-score 0.92
                  :insights [{:insight/title "Consistent gait pattern"
                             :insight/description "Step length symmetric"
                             :insight/severity :low}]})))
   ```

4. **Test in REPL**:
   ```clojure
   (require '[combatsys.gait :as gait])
   (require '[combatsys.mocks :as mocks])
   
   ;; Generate test data
   (def session (mocks/mock-static-session 30))
   
   ;; Analyze
   (def result (gait/analyze session))
   
   ;; Validate
   (get-in result [:session/analysis :gait :step-length-m])
   ;; => 0.68
   
   (schema/valid-session? result)
   ;; => true
   ```

5. **Wire into pipeline**:
   ```clojure
   ;; In src/renderer/state.cljs
   (rf/reg-event-db
    ::analyze-session
    (fn [db [_ session-id]]
      (let [session (get-in db [:sessions session-id])
            analyzed (-> session
                         breathing/analyze
                         posture/analyze
                         gait/analyze)]  ; Add new analyzer
        (assoc-in db [:sessions session-id] analyzed))))
   ```

6. **Add subscription**:
   ```clojure
   (rf/reg-sub
    ::gait-analysis
    :<- [::current-session]
    (fn [session _]
      (get-in session [:session/analysis :gait])))
   ```

7. **Create UI panel**:
   ```clojure
   ;; In src/renderer/views.cljs
   (defn gait-metrics []
     (let [analysis @(rf/subscribe [::state/gait-analysis])]
       [panel "Gait Analysis"
        (if analysis
          [:div
           [:div [:strong "Step Length: "]
            (str (:step-length-m analysis) " m")]
           [:div [:strong "Cadence: "]
            (str (:cadence-steps-per-min analysis) " steps/min")]
           [:div [:strong "Symmetry: "]
            (str (* (:symmetry-score analysis) 100) "%")]]
          [:div "No gait analysis available."])]))
   
   ;; Add to main view
   (defn main-view []
     [:div
      ;; ... existing panels ...
      [gait-metrics]])  ; Add new panel
   ```

8. **Commit**:
   ```bash
   git add src/shared/gait.cljs src/renderer/state.cljs src/renderer/views.cljs
   git commit -m "Add gait analysis module

   - Pure functional analyzer (gait.cljs)
   - Computes step length, cadence, symmetry
   - Integrated into analysis pipeline
   - UI panel displays metrics"
   ```

**Time estimate**: 3-4 hours for simple analyzer, 6-8 hours for complex

---

## Workflow 2: Debugging State Management Issues

**Use case**: State isn't updating correctly, subscriptions not triggering

**Team lead mindset**: Rich Hickey (understand data flow) + John Carmack (pragmatic debugging)

### Steps

1. **Connect to REPL**:
   ```bash
   npx shadow-cljs cljs-repl renderer
   ```

2. **Inspect current state**:
   ```clojure
   ;; View entire app state
   @re-frame.db/app-db
   
   ;; Navigate to specific path
   (get-in @re-frame.db/app-db [:sessions])
   (get-in @re-frame.db/app-db [:current-session-id])
   ```

3. **Check event handlers**:
   ```clojure
   ;; Manually dispatch event
   (rf/dispatch [::state/load-demo-session])
   
   ;; Check if state changed
   @re-frame.db/app-db
   
   ;; If no change, check event handler implementation
   ```

4. **Check subscriptions**:
   ```clojure
   ;; Test subscription
   @(rf/subscribe [::state/current-session])
   
   ;; If nil, check:
   ;; 1. Is current-session-id set?
   (get-in @re-frame.db/app-db [:current-session-id])
   
   ;; 2. Does session exist in :sessions map?
   (get-in @re-frame.db/app-db [:sessions <session-id>])
   ```

5. **Add logging to event handlers**:
   ```clojure
   (rf/reg-event-db
    ::problematic-event
    (fn [db [_ args]]
      (println "BEFORE:" db)
      (let [new-db (transform db args)]
        (println "AFTER:" new-db)
        new-db)))
   ```

6. **Use re-frame-10x (if enabled)**:
   - Open DevTools
   - Click "10x" tab
   - View event trace, state diff, subscription graph

7. **Common issues**:
   - **Event handler not registered**: Check `(rf/reg-event-db ::name ...)`
   - **Subscription not registered**: Check `(rf/reg-sub ::name ...)`
   - **Wrong keyword**: `:session/id` vs `::session/id` (namespace matters!)
   - **Non-pure handler**: Side effect in event handler (move to effect handler)

**Time estimate**: 15-30 minutes per issue

---

## Workflow 3: Performance Optimization

**Use case**: Real-time loop is slow, dropping frames

**Team lead mindset**: John Carmack (profile first, optimize hotspots)

### Steps

1. **Measure first**:
   ```clojure
   ;; Add timing to main loop
   (defn capture-loop! []
     (let [start (.now js/performance)]
       ;; ... existing code ...
       (let [end (.now js/performance)]
         (when (> (- end start) 33)
           (println "WARNING: Frame took" (- end start) "ms")))))
   ```

2. **Profile each step**:
   ```clojure
   (defn profile-step [label f]
     (let [start (.now js/performance)
           result (f)
           end (.now js/performance)]
       (println label ":" (- end start) "ms")
       result))
   
   (defn capture-loop! []
     (profile-step "Camera capture"
       #(camera/capture-frame @video))
     
     (profile-step "Pose estimation"
       #(mediapipe/estimate-pose canvas))
     
     (profile-step "State update"
       #(rf/dispatch [::state/append-frame frame])))
   ```

3. **Identify bottleneck**:
   - **Camera capture >10ms**: Lower resolution or skip frames
   - **Pose estimation >30ms**: Use lighter model or reduce input size
   - **State update >5ms**: Check if event handler is doing too much
   - **Render >10ms**: Throttle UI updates

4. **Apply optimizations**:
   
   **Skip frames**:
   ```clojure
   (when (zero? (mod @frame-count 2))
     ;; Only process every 2nd frame
     (estimate-pose ...))
   ```
   
   **Reduce resolution**:
   ```clojure
   (set! (.-width canvas) 640)  ; Instead of 1920
   (set! (.-height canvas) 480)  ; Instead of 1080
   ```
   
   **Throttle UI updates**:
   ```clojure
   (defn throttled-dispatch [event interval-ms]
     (let [last-dispatch (atom 0)]
       (when (> (- (.now js/Date) @last-dispatch) interval-ms)
         (reset! last-dispatch (.now js/Date))
         (rf/dispatch event))))
   ```
   
   **Batch state updates**:
   ```clojure
   ;; Instead of appending frames one-by-one
   (rf/dispatch [::state/append-frame frame])
   
   ;; Batch every 10 frames
   (when (zero? (mod @frame-count 10))
     (rf/dispatch [::state/append-frames @frame-buffer])
     (reset! frame-buffer []))
   ```

5. **Measure again**:
   ```clojure
   ;; Verify improvement
   (println "Frame time: " (- end start) "ms")
   ;; Target: <33ms (30fps) or <50ms (20fps)
   ```

6. **Document optimization**:
   ```clojure
   ;; Add comment explaining why
   ;; PERFORMANCE: Skip frames to maintain 30fps target
   ;; Pose estimation takes 30ms, so process every 2nd frame
   (when (zero? (mod @frame-count 2))
     ...)
   ```

**Time estimate**: 1-2 hours per optimization cycle

---

## Workflow 4: Test-Driven Development (TDD)

**Use case**: Implementing new complex algorithm (e.g., breathing rate detection)

**Team lead mindset**: Google Engineer (test first, edge cases)

### Steps

1. **Think hard**: What are expected inputs/outputs?
   ```
   Input: Timeline of 1800 frames (60s @ 30fps)
   Output: Breathing rate 18-24 bpm
   
   Edge cases:
   - No breathing detected → 0 bpm
   - Irregular breathing → confidence score
   - Short session (<10s) → insufficient data error
   ```

2. **Write failing tests first**:
   ```clojure
   (ns combatsys.breathing-test
     (:require [cljs.test :refer-macros [deftest is testing]]
               [combatsys.breathing :as breathing]
               [combatsys.mocks :as mocks]))
   
   (deftest test-normal-breathing
     (testing "Detects normal breathing rate"
       (let [session (mocks/mock-breathing-session 60 22)
             result (breathing/analyze session)
             rate (get-in result [:session/analysis :breathing :rate-bpm])]
         (is (< 20 rate 24) "Rate should be ~22 bpm"))))
   
   (deftest test-breath-hold
     (testing "Detects breath holding"
       (let [session (mocks/mock-breathing-with-hold 60 20 30 35)
             result (breathing/analyze session)
             windows (get-in result [:session/analysis :breathing :fatigue-windows])]
         (is (= 1 (count windows)) "Should detect one hold")
         (is (< 29000 (:start-ms (first windows)) 31000)))))
   
   (deftest test-insufficient-data
     (testing "Handles short sessions gracefully"
       (let [session (mocks/mock-breathing-session 5 22)
             result (breathing/analyze session)]
         (is (nil? (get-in result [:session/analysis :breathing :rate-bpm]))
             "Should return nil for short session"))))
   ```

3. **Run tests (should fail)**:
   ```bash
   npx shadow-cljs compile test
   # Tests fail: breathing/analyze not implemented
   ```

4. **Implement just enough to pass**:
   ```clojure
   (defn analyze [session]
     (let [timeline (:session/timeline session)]
       (if (< (count timeline) 300)  ; <10 seconds
         (assoc-in session [:session/analysis :breathing] nil)
         (let [rate 22]  ; Hardcoded for now
           (assoc-in session
                     [:session/analysis :breathing]
                     {:rate-bpm rate
                      :fatigue-windows []})))))
   ```

5. **Run tests (should pass)**:
   ```bash
   npx shadow-cljs compile test
   # Tests pass: basic implementation works
   ```

6. **Refactor**:
   ```clojure
   (defn analyze [session]
     (let [timeline (:session/timeline session)]
       (if (insufficient-data? timeline)
         (assoc-in session [:session/analysis :breathing] nil)
         (let [signal (extract-breathing-signal timeline)
               rate (detect-rate signal)
               windows (detect-pauses signal)]
           (assoc-in session
                     [:session/analysis :breathing]
                     {:rate-bpm rate
                      :fatigue-windows windows})))))
   ```

7. **Run tests again (still pass)**:
   ```bash
   npx shadow-cljs compile test
   # All tests green
   ```

8. **Commit**:
   ```bash
   git add test/shared/breathing_test.cljs src/shared/breathing.cljs
   git commit -m "Implement breathing rate detection (TDD)

   - Tests cover normal breathing, breath holds, edge cases
   - All tests passing
   - Pure functional implementation"
   ```

**Time estimate**: 4-6 hours for algorithm + tests

---

## Workflow 5: Integrating External JavaScript Library

**Use case**: Adding new npm package (e.g., FFT library for breathing analysis)

**Team lead mindset**: Y-Combinator Hacker (pragmatic, get it working)

### Steps

1. **Research library**:
   ```bash
   npm search fft
   # Find: fft.js, dsp.js, etc.
   ```

2. **Install**:
   ```bash
   npm install fft.js
   ```

3. **Create ClojureScript wrapper**:
   ```clojure
   (ns combatsys.fft
     "ClojureScript wrapper for fft.js"
     (:require ["fft.js" :as FFT]))
   
   (defn create-fft
     "Create FFT calculator for given size.
      Side effect: Allocates memory."
     [size]
     (FFT. size))
   
   (defn compute-fft
     "Compute FFT of signal.
      
      Input: [float] - time-domain signal
      Output: {:real [float] :imag [float]} - frequency-domain
      
      Side effect: Mutates internal FFT buffers."
     [fft-obj signal]
     (let [out (js/Array. (count signal))]
       (.realTransform fft-obj out (clj->js signal))
       {:real (vec (.-real out))
        :imag (vec (.-imag out))}))
   
   (defn find-peak-frequency
     "Find dominant frequency in FFT output.
      Pure function."
     [fft-output sample-rate]
     (let [magnitudes (map (fn [r i]
                            (Math/sqrt (+ (* r r) (* i i))))
                          (:real fft-output)
                          (:imag fft-output))
           peak-idx (apply max-key magnitudes (range (count magnitudes)))
           frequency (* peak-idx (/ sample-rate (count magnitudes)))]
       frequency))
   ```

4. **Test in REPL**:
   ```clojure
   (require '[combatsys.fft :as fft])
   
   ;; Test with sine wave
   (defn generate-sine [freq sample-rate duration]
     (let [samples (* sample-rate duration)]
       (mapv (fn [i]
              (Math/sin (* 2 Math/PI freq (/ i sample-rate))))
            (range samples))))
   
   (def signal (generate-sine 2.0 30 5))  ; 2 Hz, 30 fps, 5 seconds
   (def fft-obj (fft/create-fft (count signal)))
   (def result (fft/compute-fft fft-obj signal))
   (def peak (fft/find-peak-frequency result 30))
   
   (println "Peak frequency:" peak "Hz")
   ;; Should be ~2 Hz
   ```

5. **Integrate into analyzer**:
   ```clojure
   (ns combatsys.breathing
     (:require [combatsys.fft :as fft]))
   
   (defn detect-breathing-rate
     "Detect breathing rate from torso motion signal.
      Returns rate in bpm."
     [signal sample-rate]
     (let [fft-obj (fft/create-fft (count signal))
           fft-result (fft/compute-fft fft-obj signal)
           freq-hz (fft/find-peak-frequency fft-result sample-rate)
           freq-bpm (* freq-hz 60)]
       freq-bpm))
   ```

6. **Test with real data**:
   ```clojure
   (let [session (mocks/mock-breathing-session 60 22)
         timeline (:session/timeline session)
         signal (extract-breathing-signal timeline)
         rate (detect-breathing-rate signal 30)]
     (println "Detected rate:" rate "bpm"))
   ```

7. **Document in README**:
   ```markdown
   ## Dependencies
   
   - **fft.js**: Fast Fourier Transform for breathing rate detection
   ```

**Time estimate**: 2-3 hours

---

## Workflow 6: Deploying to Production

**Use case**: Package app for distribution

**Team lead mindset**: Google Engineer (production quality, testing)

### Steps

1. **Test thoroughly**:
   ```bash
   # Run all tests
   npx shadow-cljs compile test
   
   # Manual testing
   npm start
   # Test all features, record video of working app
   ```

2. **Update version**:
   ```json
   // package.json
   {
     "version": "0.2.0",  // Increment version
     ...
   }
   ```

3. **Build release**:
   ```bash
   # Clean old builds
   npm run clean
   
   # Compile optimized ClojureScript
   npx shadow-cljs release main renderer
   
   # Package Electron app
   npm run package
   ```

4. **Test release build**:
   ```bash
   # Open packaged app
   open dist/CombatSys-Motion-0.2.0.dmg  # macOS
   
   # Install and test
   # Verify all features work
   ```

5. **Create release notes**:
   ```markdown
   # Release 0.2.0 - LOD 1 Complete
   
   ## New Features
   - Real camera capture via getUserMedia
   - MediaPipe pose estimation
   - Real-time skeleton overlay
   - Session recording to disk
   
   ## Performance
   - 15-18 fps pose tracking
   - <50ms frame processing
   
   ## Known Issues
   - Skeleton tracking may be less accurate with poor lighting
   - Limited to single person in frame
   
   ## Installation
   Download appropriate package for your platform
   ```

6. **Tag release**:
   ```bash
   git tag -a v0.2.0 -m "LOD 1: Real camera + MediaPipe"
   git push origin v0.2.0
   ```

7. **Distribute**:
   - Upload to GitHub Releases
   - Share download link
   - Gather user feedback

**Time estimate**: 2-3 hours

---

## Quick Reference: Common Commands

```bash
# Development
npx shadow-cljs watch main renderer  # Start compiler
npm start                             # Start Electron
npx shadow-cljs cljs-repl renderer   # Connect REPL

# Testing
npx shadow-cljs compile test         # Run tests
npm test                              # Run all tests

# Building
npx shadow-cljs release main renderer # Optimized build
npm run package                       # Package app

# Git
git status                            # Check status
git add <files>                       # Stage changes
git commit -m "message"               # Commit
git push origin main                  # Push to remote
```

---

## Remember

1. **Use "think hard"** before complex implementations
2. **Test in REPL** continuously
3. **Commit working slices** - always demonstrable
4. **Ask clarifying questions** before starting
5. **Document decisions** in code comments

**Let's build brilliantly!** 🚀
