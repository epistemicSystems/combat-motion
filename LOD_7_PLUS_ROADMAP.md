# COMBATSYS: LOD 7+ ROADMAP
## Post-MVP Development Strategy

**Last Updated:** 2025-11-19
**Current Status:** LOD 6 Complete (Multi-session analytics shipped)
**Next Target:** LOD 7 (Production Polish)

---

## PHILOSOPHY: VERTICAL SLICES OVER FEATURE CREEP

Following Paul Graham's wisdom: "Always have something working."

Each LOD stage after MVP follows the same pattern:
1. **Pick ONE user-facing capability**
2. **Build it end-to-end** (data → analysis → UI)
3. **Ship it** (it works, it's tested, it's polished)
4. **Gather feedback** before next LOD

---

## LOD 7: PRODUCTION POLISH (Week 4, Days 19-21)

### Goal
Transform prototype into production-ready v1.0. Current users can **rely on it daily** without crashes, lag, or confusion.

### What "Production Ready" Means

**Carmack's Standards:**
- Real-time path maintains 20+ FPS on mid-range hardware
- Offline analysis completes in <2 min per minute of footage
- Memory footprint <200MB for 5-minute session
- No frame drops during recording

**Victor's Standards:**
- Zero unexplained numbers (every metric has hover explanation)
- Error messages are actionable ("Camera blocked" → "Check if lens is covered")
- Loading states show progress, not spinners

**Hickey's Standards:**
- All analyzers use shared feature extraction (no duplicate work)
- State updates are pure (reproducible, testable)
- Schema validates all data flowing through system

**Graham's Standards:**
- App launches in <3 seconds
- User can complete first session in <2 minutes (onboarding → record → analyze)
- Crashes → logged → reported → fixed within 24h

### Tasks (Priority Order)

#### Task 7.1: Performance Profiling & Optimization (12 hours)
**File:** Various (optimize hotspots)
**Priority:** 🔴 Critical

**Approach:**
```clojure
;; 1. Add profiling instrumentation
(defn profile-analyzer [analyzer-fn label]
  (fn [timeline]
    (let [start (js/performance.now)
          result (analyzer-fn timeline)
          duration (- (js/performance.now) start)]
      (swap! perf-metrics assoc label duration)
      result)))

;; 2. Wrap all analyzers
(def analyze-breathing-profiled
  (profile-analyzer breathing/analyze "breathing-analyzer"))

;; 3. Run on real sessions, collect data
;; 4. Optimize slowest 5%
```

**Expected Bottlenecks:**
1. Pose processing (per-frame work)
2. FFT in breathing analysis (can optimize with windowing)
3. Timeline rendering (large session playback)
4. Session comparison (loading multiple large EDN files)

**Optimization Strategies:**
- **Shared feature extraction:** Compute angles/velocities once, reuse across analyzers
- **Lazy loading:** Don't load full timeline for session browser (metadata only)
- **Frame skipping:** Real-time path can analyze every 2nd or 3rd frame
- **Web Workers:** Move analysis off main thread (Electron renderer)

**Success Criteria:**
- [ ] Real-time path: 20+ FPS on 2019 MacBook Pro
- [ ] Breathing analysis: <30s for 5-min session
- [ ] Session browser: <500ms to list 100 sessions
- [ ] Memory usage: <200MB for 5-min session

---

#### Task 7.2: Shared Feature Extraction Pipeline (10 hours)
**File:** `src/shared/features.cljs`
**Priority:** 🔴 Critical

**Problem:** Each analyzer redundantly computes same features.

**Solution:**
```clojure
(ns combatsys.shared.features
  "Unified feature extraction for all analyzers")

(defn extract-all-features
  "Extract all features needed by any analyzer.
  Returns cached feature map to avoid redundant computation."
  [timeline]
  {:angles (extract-angles timeline)
   :velocities (extract-velocities timeline)
   :com-trajectory (extract-com timeline)
   :torso-motion (extract-torso-motion timeline)
   :foot-positions (extract-foot-positions timeline)
   :head-shoulder-alignment (extract-head-shoulder timeline)})

;; Analyzers become pure transforms over features
(defn analyze-breathing [features]
  (-> features
      :torso-motion
      smooth-signal
      detect-breathing-rate))

(defn analyze-posture [features]
  {:head-forward-cm (compute-forward-head (:head-shoulder-alignment features))
   :shoulder-imbalance (compute-imbalance (:angles features))})
```

**Benefits:**
- Performance: Compute once, analyze many times
- Maintainability: Feature extraction is centralized
- Real-time path: Incremental feature updates

**Success Criteria:**
- [ ] All analyzers use shared features (no direct timeline access)
- [ ] Benchmark shows 2-3x speedup for multi-analysis
- [ ] Features are cached and reusable

---

#### Task 7.3: Error Handling & Graceful Degradation (8 hours)
**File:** Various
**Priority:** 🔴 Critical

**Current State:** Errors crash the app or show cryptic messages.

**Target State:**
- Camera fails → Show clear message + fallback to file upload
- MediaPipe fails → Show "Pose detection unavailable" + offer CPU fallback
- Analysis fails → Show partial results + warning
- Save fails → Retry logic + temp storage

**Implementation:**
```clojure
;; Wrap all side effects with error handlers
(defn capture-frame-safe [camera-handle]
  (try
    (capture-frame! camera-handle)
    (catch js/Error e
      (js/console.error "Frame capture failed:" (.-message e))
      (swap! app-state assoc-in [:errors :camera]
             {:type :camera-error
              :message "Camera disconnected"
              :suggestion "Check camera connection and try again"
              :timestamp (js/Date.now)})
      nil)))

;; UI shows errors prominently
(defn error-banner []
  (let [errors @(rf/subscribe [:recent-errors])]
    (when (seq errors)
      [:div.error-banner
       (for [error errors]
         [:div.error-card
          [:h3 (:message error)]
          [:p (:suggestion error)]
          [:button {:on-click #(rf/dispatch [:error/dismiss (:id error)])}
           "Dismiss"]])])))
```

**Success Criteria:**
- [ ] Zero unhandled exceptions in 1-week test
- [ ] All errors have user-friendly messages
- [ ] Errors suggest next steps
- [ ] App continues running after non-fatal errors

---

#### Task 7.4: UI Polish & Animations (8 hours)
**File:** `src/renderer/views.cljs`, CSS
**Priority:** 🟡 Important

**Current State:** Functional but bare-bones UI.

**Target State:**
- Smooth transitions between views
- Loading states show progress (not just spinners)
- Skeleton overlay has smooth interpolation
- Metrics animate when updating

**Key Improvements:**
```clojure
;; Smooth timeline scrubbing
(defn timeline-scrubber [session]
  (let [position @(rf/subscribe [:timeline-position])]
    [:div.scrubber
     [:input {:type "range"
              :min 0
              :max (count (:timeline session))
              :value position
              :on-change #(rf/dispatch [:timeline/seek (-> % .-target .-value)])
              :style {:background (gradient-for-timeline session)}}]]))

;; Skeleton overlay with interpolation
(defn skeleton-overlay [frame next-frame progress]
  (let [interpolated (interpolate-pose (:pose frame) (:pose next-frame) progress)]
    [draw-skeleton interpolated]))
```

**Success Criteria:**
- [ ] View transitions use CSS transitions (300ms ease)
- [ ] Timeline scrubbing is smooth (interpolated frames)
- [ ] Metrics count up/down when changing (not jump)
- [ ] Loading states show actual progress (% complete)

---

#### Task 7.5: Observability & Explainability UI (10 hours)
**File:** `src/renderer/explain.cljs`
**Priority:** 🟡 Important

**Victor's Vision:** Every number should be explainable.

**Implementation:**
```clojure
(defn metric-with-explanation [label value analysis]
  [:div.metric
   [:span.label label]
   [:span.value value]
   [:button.explain-btn
    {:on-click #(rf/dispatch [:ui/show-explanation (:provenance analysis)])}
    "?"]])

(defn explanation-modal [provenance]
  [:div.modal
   [:h2 "How we calculated this"]
   [:div.steps
    [:div.step
     [:h3 "1. Extract torso motion"]
     [:pre (pr-str (:signal provenance))]
     [:canvas (draw-signal (:signal provenance))]]
    [:div.step
     [:h3 "2. Apply smoothing"]
     [:canvas (draw-signal (:smoothed provenance))]]
    [:div.step
     [:h3 "3. Frequency analysis (FFT)"]
     [:canvas (draw-fft (:fft-result provenance))]]
    [:div.step
     [:h3 "4. Find peak"]
     [:p (str "Dominant frequency: " (:frequency (:peak provenance)) " Hz")]
     [:p (str "→ Breathing rate: " (* 60 (:frequency (:peak provenance))) " bpm")]]]])
```

**Success Criteria:**
- [ ] Every metric has "?" button
- [ ] Clicking "?" shows derivation steps
- [ ] Derivation includes visualizations (waveforms, graphs)
- [ ] User can understand why system made each decision

---

### End of LOD 7: What We Ship

**v1.0 Release Checklist:**
- [ ] App is fast (20+ FPS, <2min analysis)
- [ ] App is stable (zero crashes in 1-week test)
- [ ] App is polished (smooth animations, clear UI)
- [ ] App is explainable (every metric has provenance)
- [ ] Documentation complete (user guide, troubleshooting)
- [ ] Build & packaging working (macOS, Windows, Linux)

**User Experience:**
```
New user downloads CombatSys v1.0
→ App launches in 3 seconds
→ Onboarding: "Let's calibrate" (5 minutes)
→ First session: Record 2-minute shadowboxing
→ Analysis completes in 90 seconds
→ See: Breathing rate, posture score, insights
→ Hover over "Breathing: 22 bpm" → See explanation
→ Click "Compare" → Load previous session → See trend
→ Export PDF report for coach
→ User is delighted, tells friends
```

---

## LOD 8: BALANCE ANALYZER (Week 5, Days 22-24)

### Goal
Add third analyzer to prove plugin architecture scales. Balance is simpler than gait (good test case).

### Why Balance Before Gait?

**Carmack's reasoning:**
- Balance uses existing pose data (no new event detection needed)
- Gait requires step detection (complex temporal logic)
- Balance proves multi-analyzer architecture works
- Gait can learn from balance implementation

### What Works at End of LOD 8

- ✅ Real-time balance score displayed during live feed
- ✅ Balance analysis runs offline alongside breathing & posture
- ✅ UI shows center of mass trajectory and support polygon
- ✅ Insights: "Balance score: 0.85" with drill-down explanation

### Tasks

#### Task 8.1: Balance Analyzer Implementation (10 hours)
**File:** `src/shared/balance.cljs`
**Priority:** 🔴 Critical

**Algorithm:**
```clojure
(defn compute-center-of-mass
  "Compute COM from pose landmarks.
  Uses weighted average of body segments."
  [pose]
  (let [segments {:head 0.08 :torso 0.50 :arms 0.10 :legs 0.32}
        weighted-positions (map #(weight-position pose %) segments)]
    (average weighted-positions)))

(defn compute-support-polygon
  "Compute polygon formed by contact points (feet)."
  [pose]
  (let [left-foot (get-landmark pose :left-foot-index)
        right-foot (get-landmark pose :right-foot-index)
        left-heel (get-landmark pose :left-heel)
        right-heel (get-landmark pose :right-heel)]
    [left-foot right-foot right-heel left-heel]))

(defn compute-stability-score
  "Score based on COM position relative to support polygon.
  1.0 = COM centered, 0.0 = COM outside support."
  [com support-polygon]
  (let [distance (distance-to-polygon com support-polygon)
        max-safe-distance (polygon-radius support-polygon)]
    (max 0.0 (- 1.0 (/ distance max-safe-distance)))))

(defn analyze
  "Full balance analysis from features."
  [features]
  (let [com-trajectory (map compute-center-of-mass (:poses features))
        support-polygons (map compute-support-polygon (:poses features))
        stability-scores (map compute-stability-score com-trajectory support-polygons)]
    {:balance/com-trajectory com-trajectory
     :balance/mean-stability (mean stability-scores)
     :balance/sway-velocity (compute-sway-velocity com-trajectory)
     :balance/unstable-moments (detect-unstable-moments stability-scores)}))
```

**Success Criteria:**
- [ ] Balance analysis conforms to schema
- [ ] Stability score correlates with subjective balance quality
- [ ] Works on standing, moving, and dynamic poses
- [ ] Unit tests for all functions

---

#### Task 8.2: Balance Visualization UI (8 hours)
**File:** `src/renderer/balance_view.cljs`
**Priority:** 🔴 Critical

**Visualizations:**
1. **COM trajectory overlay** (shows path of center of mass on video)
2. **Support polygon** (shows feet positions and safe zone)
3. **Stability score graph** (timeline of stability over session)
4. **Sway velocity** (how much COM is moving)

**Implementation:**
```clojure
(defn balance-overlay [frame analysis]
  (let [com (get-in analysis [:balance/com-trajectory (:frame/index frame)])
        polygon (get-in analysis [:balance/support-polygons (:frame/index frame)])]
    [:svg.balance-overlay
     ;; Draw support polygon
     [:polygon {:points (points->svg polygon)
                :fill "rgba(0,255,0,0.2)"
                :stroke "green"}]
     ;; Draw COM
     [:circle {:cx (:x com)
               :cy (:y com)
               :r 5
               :fill "red"}]
     ;; Draw COM trajectory (last 30 frames)
     [:path {:d (trajectory->svg-path (take-last 30 (:balance/com-trajectory analysis)))
             :stroke "red"
             :fill "none"}]]))
```

**Success Criteria:**
- [ ] COM trajectory is visible on video overlay
- [ ] Support polygon updates in real-time
- [ ] Stability graph shows clear unstable moments
- [ ] UI integrates seamlessly with existing analysis tabs

---

#### Task 8.3: Balance Insights Generation (6 hours)
**File:** `src/shared/insights.cljs`
**Priority:** 🟡 Important

**Insight Types:**
1. **Overall stability:** "Balance score: 0.85 (good)"
2. **Unstable moments:** "3 balance dips detected at 0:45, 1:20, 2:15"
3. **Sway analysis:** "High sway velocity (0.15 m/s) suggests fatigue"
4. **Recommendations:** "Practice single-leg stance to improve stability"

**Success Criteria:**
- [ ] Insights are actionable
- [ ] Insights reference specific moments in timeline
- [ ] Insights adapt to user's baseline (personalized)

---

### End of LOD 8 Demo

```bash
$ npm start

# Record 2-minute session (user doing single-leg stance)
# Analysis completes: Breathing + Posture + Balance
# Switch to "Balance" tab:
#   - Stability score: 0.78 (fair)
#   - COM trajectory shown on video
#   - Graph shows stability dropping during left-leg stance
#   - Insight: "Left leg balance is 15% weaker than right"
#   - Recommendation: "Practice tree pose on left side"
```

---

## LOD 9: EXPORT & SHARING (Week 6, Days 25-27)

### Goal
Enable users to share results with coaches, teammates, or save for records.

### What Works at End of LOD 9

- ✅ Export session as PDF report (metrics + graphs + insights)
- ✅ Export video montage (highlights + overlays)
- ✅ Generate shareable link (cloud upload, optional)
- ✅ Print-friendly layout

### Tasks

#### Task 9.1: PDF Report Generation (10 hours)
**File:** `src/main/export.cljs`
**Priority:** 🔴 Critical

**Libraries:** Use `jsPDF` or `pdfkit` via Node.js

**Report Structure:**
```
CombatSys Motion Analysis Report
================================

Session: 2025-11-19 14:30
Duration: 2 minutes 15 seconds
User: John Doe

SUMMARY
-------
Breathing Rate: 22 bpm (baseline: 20 bpm) ↑
Posture Score: 0.84 (good)
Balance Score: 0.78 (fair)

BREATHING ANALYSIS
------------------
[Graph: breathing waveform]
- Average rate: 22 bpm
- Depth score: 0.75
- Fatigue windows: 2 detected

Insights:
- Breathing window shortened during high-intensity periods
- Practice nasal breathing for better endurance

POSTURE ANALYSIS
----------------
[Graph: forward head posture over time]
- Forward head: 4.2 cm
- Shoulder imbalance: 2.5°
- Spine alignment: neutral

Insights:
- Slight forward head posture detected
- Recommendation: Chin tucks, 3 sets daily

BALANCE ANALYSIS
----------------
[Visualization: COM trajectory]
- Mean stability: 0.78
- Sway velocity: 0.12 m/s
- Unstable moments: 3

Insights:
- Left leg balance weaker than right
- Recommendation: Single-leg stance practice
```

**Success Criteria:**
- [ ] PDF includes all analyses (breathing, posture, balance)
- [ ] Graphs are embedded as images
- [ ] Report is printer-friendly (black & white works)
- [ ] Export completes in <10 seconds

---

#### Task 9.2: Video Montage Export (12 hours)
**File:** `src/main/video_export.cljs`
**Priority:** 🟡 Important

**Feature:** Generate highlight reel with overlays.

**Algorithm:**
1. User selects moments (or auto-detect interesting moments)
2. Extract clips (5-10 seconds each)
3. Add skeleton overlay + metrics overlay
4. Concatenate clips
5. Export as MP4

**Implementation:**
```clojure
(defn export-montage [session moments]
  (let [clips (map #(extract-clip session %) moments)
        overlaid (map add-overlays clips)
        concatenated (concatenate-videos overlaid)]
    (encode-mp4 concatenated "montage.mp4")))
```

**Use ffmpeg for video operations.**

**Success Criteria:**
- [ ] Can select moments manually or auto-detect
- [ ] Video includes skeleton + metrics overlay
- [ ] Export completes in <5 min for 2-min session
- [ ] Output is shareable (MP4, 1080p, <50MB)

---

#### Task 9.3: Cloud Upload & Shareable Links (8 hours)
**File:** `src/main/cloud.cljs`
**Priority:** 🟢 Nice to Have

**Feature:** Upload session to cloud, generate shareable link.

**Privacy:** User opts in, data is encrypted, links expire.

**Implementation:**
```clojure
(defn upload-session [session]
  (let [encrypted (encrypt-session session)
        upload-result (upload-to-s3 encrypted)]
    {:link (generate-share-link (:id upload-result))
     :expires-at (+ (js/Date.now) (* 7 24 60 60 1000))})) ;; 7 days
```

**Success Criteria:**
- [ ] Upload completes in <30 seconds
- [ ] Link opens web viewer (read-only)
- [ ] Links expire after 7 days (configurable)
- [ ] User can revoke link

---

### End of LOD 9 Demo

```bash
# User completes session
# Click "Export" button
# Options:
#   - [x] PDF Report
#   - [x] Video Montage
#   - [ ] Cloud Link (optional)

# PDF exports in 5 seconds → Opens in default viewer
# Video montage starts processing (2 min)
# Notification: "Montage ready: montage.mp4"

# User emails PDF to coach
# Coach sees full analysis, provides feedback
```

---

## LOD 10+: FUTURE DIRECTIONS

After LOD 9, branch based on user feedback:

### Path A: More Analyzers (Technical Depth)
- **LOD 10:** Gait analyzer (step detection, symmetry)
- **LOD 11:** Lifting analyzer (squat depth, bar path)
- **LOD 12:** Dance analyzer (beat alignment)

### Path B: Collaboration Features (Social Expansion)
- **LOD 10:** Coach dashboard (manage multiple athletes)
- **LOD 11:** Mobile companion app (view sessions on phone)
- **LOD 12:** Team features (compare with teammates)

### Path C: Advanced Tech (Research & Innovation)
- **LOD 10:** Multi-camera stereo (better 3D pose)
- **LOD 11:** 3D body model fitting (full biomechanics)
- **LOD 12:** Real-time 3DGS reconstruction

### Path D: AI & Automation (Intelligence Layer)
- **LOD 10:** Generative AI coaching (personalized advice)
- **LOD 11:** Anomaly detection (flag unusual patterns)
- **LOD 12:** Predictive modeling (injury risk, performance trends)

---

## ARCHITECTURE DECISIONS FOR FUTURE

### Decision 1: Analyzer Plugin System

**Problem:** Adding analyzers currently requires modifying core code.

**Solution:** Formalize analyzer contract.

```clojure
;; Analyzer protocol
(defprotocol Analyzer
  (analyze [this features] "Returns analysis map")
  (schema [this] "Returns spec for analysis output")
  (insights [this analysis] "Generates insights from analysis"))

;; Registration system
(def analyzers
  (atom {:breathing breathing-analyzer
         :posture posture-analyzer
         :balance balance-analyzer}))

(defn register-analyzer! [id analyzer]
  (swap! analyzers assoc id analyzer))

;; Run all analyzers
(defn analyze-session [session]
  (let [features (extract-all-features (:timeline session))]
    (reduce-kv
     (fn [result id analyzer]
       (assoc result id (analyze analyzer features)))
     {}
     @analyzers)))
```

**Benefits:**
- Third-party plugins possible
- Easier testing (mock analyzers)
- Clean separation of concerns

---

### Decision 2: Feature Extraction as First-Class Citizen

**Current:** Each analyzer extracts what it needs.
**Future:** Centralized feature extraction pipeline.

```clojure
(ns combatsys.shared.features)

(defn extract-features
  "Extract all features from timeline.
  Returns cached map to avoid recomputation."
  [timeline]
  (-> timeline
      (extract-pose-features)    ;; angles, positions
      (extract-motion-features)  ;; velocities, accelerations
      (extract-signal-features)  ;; torso motion, foot motion
      (extract-spatial-features) ;; COM, support polygon
      (cache-features)))         ;; memoize for reuse
```

---

### Decision 3: Real-Time vs Offline Modes

**Current:** All analysis is offline (after recording).
**Future:** Some analysis should be real-time (live feedback).

**Architecture:**
```clojure
;; Real-time analyzers (subset of full analysis)
(defn analyze-realtime [features]
  {:breathing (quick-breathing-estimate features)
   :posture (quick-posture-check features)
   :balance (quick-stability-score features)})

;; Offline analyzers (full detail)
(defn analyze-offline [features]
  {:breathing (full-breathing-analysis features)
   :posture (full-posture-analysis features)
   :balance (full-balance-analysis features)
   :gait (full-gait-analysis features)})
```

---

### Decision 4: Multi-Camera Support

**Current:** Single camera only.
**Future:** Stereo cameras for better 3D.

**Schema Extension:**
```clojure
;; Current
{:session/camera {:camera-id "cam-0" ...}}

;; Future
{:session/cameras [{:camera-id "cam-0" :role :primary :position [0 0 0]}
                   {:camera-id "cam-1" :role :secondary :position [2 0 0]}]
 :session/calibration {:extrinsics [...] :intrinsics [...]}}

;; Each frame has poses from multiple views
{:frame/poses [{:camera-id "cam-0" :landmarks [...]}
               {:camera-id "cam-1" :landmarks [...]}]
 :frame/fused-pose {:landmarks [...] :confidence 0.98}}
```

---

### Decision 5: Cloud Sync Architecture

**Principle:** Cloud is optional. App works offline-first.

**Architecture:**
```clojure
;; Local storage (SQLite or file-based)
(defn save-session-local [session]
  (save! (session-db-path) session))

;; Cloud storage (optional sync)
(defn sync-to-cloud [session]
  (when (user-opted-in?)
    (encrypt-and-upload session)))

;; Hybrid: Local first, cloud backup
(defn save-session [session]
  (save-session-local session)
  (when (online?) (sync-to-cloud session)))
```

---

## SUCCESS METRICS FOR LOD 7+

### Technical Metrics
- [ ] **Performance:** 30+ FPS real-time, <1 min offline per min of footage
- [ ] **Stability:** Zero crashes in 2-week test with 100+ sessions
- [ ] **Scalability:** Handles 1000+ sessions without lag

### User Metrics
- [ ] **Adoption:** 100 active users (daily recording)
- [ ] **Retention:** 60%+ return after 1 week
- [ ] **Satisfaction:** 8/10 NPS score

### Code Quality Metrics
- [ ] **Test Coverage:** 80%+ for pure functions
- [ ] **Documentation:** Every module has README
- [ ] **Compilation:** Zero warnings, optimized build

---

## CLOSING THOUGHTS

**Hickey:** "We've built a solid foundation. The data model is clean, the functions are pure. Now we refine."

**Carmack:** "LOD 7 is about measurement. Profile everything. Optimize the hottest paths. Make it fast."

**Victor:** "Make every number explainable. Users should understand *why* the system makes decisions."

**Graham:** "Ship LOD 7 as v1.0. Get it in users' hands. Let feedback guide LOD 8+."

---

**Next Steps:**
1. Complete LOD 7 performance profiling
2. Implement shared feature extraction
3. Polish UI and add explainability
4. Ship v1.0
5. Gather user feedback
6. Decide: Path A, B, C, or D for LOD 10+

**The journey continues. Every LOD makes the system better.**
