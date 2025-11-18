# LOD 5 TASKS: USER CALIBRATION IMPLEMENTATION
## Detailed Task Breakdown with Deliverables

---

## TASK OVERVIEW

**Total Estimated Time**: 18-24 hours
**Tasks**: 6 major tasks
**Files to Create**: 5 new files
**Files to Modify**: 4 existing files
**Tests**: 12 unit tests, 3 integration tests

---

## TASK DEPENDENCY GRAPH

```
Task 6.1 (Schema Extensions)
    ↓
Task 6.2 (Calibration Analysis) ─┬─→ Task 6.4 (Profile Storage)
    ↓                            │
Task 6.3 (Calibration Wizard)────┘
    ↓
Task 6.5 (Analyzer Integration)
    ↓
Task 6.6 (Profile View & Management)
```

**Parallelization opportunities**:
- Tasks 6.3 and 6.4 can be done in parallel after 6.2 completes
- Task 6.6 can be done in parallel with 6.5

---

## TASK 6.1: SCHEMA EXTENSIONS & VALIDATION
**Priority**: 🔴 Critical
**Estimated Time**: 2 hours
**Files**: `src/shared/schema.cljs` (modify)

### Objective
Extend existing EDN schemas to support user profiles, calibration sessions, and learned thresholds.

### Deliverables

#### 1. New Spec Definitions

Add to `src/shared/schema.cljs`:

```clojure
;; ============================================================
;; CALIBRATION (LOD 5)
;; ============================================================

(s/def ::calibration-type #{:t-pose :breathing :movement})

(s/def ::calibration-session
  (s/keys :req-un [::calibration-type ::session-id ::created-at ::duration-ms ::timeline]))

(s/def ::joint-distances
  (s/map-of keyword? pos?))
;; e.g., {:shoulder-width-cm 42.5 :arm-length-cm 68.0}

(s/def ::baseline-pose
  (s/keys :req-un [::landmarks ::joint-distances]))

(s/def ::rom-range
  (s/tuple number? number?))  ;; [min-degrees max-degrees]

(s/def ::rom-ranges
  (s/map-of keyword? ::rom-range))

(s/def ::breathing-baseline
  (s/keys :req-un [::typical-rate-bpm ::typical-depth ::typical-rhythm-regularity]))

(s/def ::typical-rate-bpm pos?)
(s/def ::typical-depth (s/double-in :min 0.0 :max 1.0))
(s/def ::typical-rhythm-regularity (s/double-in :min 0.0 :max 1.0))

(s/def ::posture-baseline
  (s/keys :req-un [::typical-forward-head-cm ::typical-shoulder-imbalance-deg]))

(s/def ::typical-forward-head-cm number?)
(s/def ::typical-shoulder-imbalance-deg number?)

;; Learned thresholds
(s/def ::breathing-thresholds
  (s/keys :req-un [::fatigue-threshold ::rate-alert-threshold]))

(s/def ::fatigue-threshold pos?)
(s/def ::rate-alert-threshold pos?)

(s/def ::posture-thresholds
  (s/keys :req-un [::forward-head-alert-cm ::shoulder-imbalance-alert-deg]))

(s/def ::forward-head-alert-cm pos?)
(s/def ::shoulder-imbalance-alert-deg pos?)

(s/def ::balance-thresholds
  (s/keys :req-un [::stability-alert-threshold]))

(s/def ::stability-alert-threshold (s/double-in :min 0.0 :max 1.0))

(s/def ::learned-thresholds
  (s/keys :req-un [::breathing-thresholds ::posture-thresholds ::balance-thresholds]))

;; Complete user profile
(s/def ::user-profile
  (s/keys :req-un [::user-id ::height-cm ::baseline-pose ::learned-thresholds
                   ::last-calibration-date ::calibration-count]
          :opt-un [::breathing-baseline ::posture-baseline ::rom-ranges]))

(s/def ::last-calibration-date inst?)
(s/def ::calibration-count nat-int?)
```

#### 2. Validation Functions

```clojure
(defn valid-user-profile?
  "Validates user profile against schema"
  [profile]
  (s/valid? ::user-profile profile))

(defn validate-calibration-sessions
  "Validates that we have all 3 required calibration types"
  [sessions]
  (let [types (set (map :calibration-type sessions))]
    (and (= 3 (count sessions))
         (contains? types :t-pose)
         (contains? types :breathing)
         (contains? types :movement))))
```

### Acceptance Criteria

```clojure
(ns combatsys.schema-test
  (:require [clojure.test :refer [deftest is testing]]
            [combatsys.schema :as schema]
            [clojure.spec.alpha :as s]))

(deftest test-user-profile-spec
  (testing "Valid user profile conforms to spec"
    (let [profile {:user-id (random-uuid)
                   :height-cm 178
                   :baseline-pose {:landmarks [...] :joint-distances {:shoulder-width-cm 42.5}}
                   :learned-thresholds {:breathing-thresholds {:fatigue-threshold 0.4
                                                                :rate-alert-threshold 4.0}
                                        :posture-thresholds {:forward-head-alert-cm 5.5
                                                             :shoulder-imbalance-alert-deg 3.0}
                                        :balance-thresholds {:stability-alert-threshold 0.6}}
                   :last-calibration-date (js/Date.)
                   :calibration-count 1}]
      (is (s/valid? ::schema/user-profile profile))))

  (testing "Invalid profile rejected"
    (let [bad-profile {:user-id "not-a-uuid"  ;; Invalid
                       :height-cm -10}]       ;; Invalid
      (is (not (s/valid? ::schema/user-profile bad-profile))))))
```

---

## TASK 6.2: CALIBRATION ANALYSIS FUNCTIONS
**Priority**: 🔴 Critical
**Estimated Time**: 6 hours
**Files**: `src/shared/calibration.cljs` (create new), `src/shared/personalization.cljs` (create new)

### Objective
Implement pure functions that analyze calibration sessions and compute user profile.

### Deliverables

#### File 1: `src/shared/calibration.cljs`

```clojure
(ns combatsys.calibration
  "Calibration session analysis functions"
  (:require [combatsys.schema :as schema]
            [combatsys.pose :as pose]
            [combatsys.breathing :as breathing]
            [clojure.spec.alpha :as s]))

;; ============================================================
;; T-POSE ANALYSIS
;; ============================================================

(defn average-poses
  "Average landmarks over all frames to reduce noise"
  [timeline]
  ;; For each landmark, average x, y, z across all frames
  )

(defn estimate-height
  "Estimate height from pose (top of head to ankle distance)"
  [pose]
  ;; Compute distance from top landmark (nose or eye) to ankle landmarks
  ;; Multiply by calibration factor (camera distance, pixel-to-cm ratio)
  )

(defn compute-joint-distances
  "Compute distances between key landmarks"
  [pose]
  {:shoulder-width-cm (pose/compute-distance pose :left-shoulder :right-shoulder)
   :hip-width-cm (pose/compute-distance pose :left-hip :right-hip)
   :arm-length-left-cm (pose/compute-distance pose :left-shoulder :left-wrist)
   :arm-length-right-cm (pose/compute-distance pose :right-shoulder :right-wrist)
   :leg-length-left-cm (pose/compute-distance pose :left-hip :left-ankle)
   :leg-length-right-cm (pose/compute-distance pose :right-hip :right-ankle)})

(defn analyze-t-pose-session
  "Extract baseline pose and body measurements from T-pose session.

  Args:
    timeline: Vector of frames from T-pose calibration session

  Returns:
    {:height-cm number
     :joint-distances map
     :baseline-pose map}"
  [timeline]
  (let [avg-pose (average-poses timeline)
        height (estimate-height avg-pose)
        distances (compute-joint-distances avg-pose)
        forward-head (pose/measure-forward-head avg-pose)
        shoulder-imbalance (pose/measure-shoulder-imbalance avg-pose)]

    {:height-cm height
     :joint-distances distances
     :baseline-pose {:landmarks (:landmarks avg-pose)
                     :forward-head-cm forward-head
                     :shoulder-imbalance-deg shoulder-imbalance}}))

;; ============================================================
;; BREATHING ANALYSIS
;; ============================================================

(defn compute-signal-amplitude
  "Compute peak-to-trough amplitude of signal (breathing depth proxy)"
  [signal]
  (- (apply max signal) (apply min signal)))

(defn compute-rhythm-regularity
  "Compute regularity of breathing rhythm (lower variance = more regular).

  Returns value 0-1, where 1 = perfectly regular."
  [signal]
  ;; Detect breathing cycles, measure variance in cycle length
  ;; Return 1 - (normalized-variance)
  )

(defn analyze-breathing-session
  "Extract breathing baseline from calibration session.

  Args:
    timeline: Vector of frames from breathing session

  Returns:
    {:typical-rate-bpm number
     :typical-depth number
     :typical-rhythm-regularity number}"
  [timeline]
  (let [signal (breathing/extract-torso-motion timeline)
        rate (breathing/detect-breathing-rate signal)
        depth (compute-signal-amplitude signal)
        regularity (compute-rhythm-regularity signal)]

    {:typical-rate-bpm rate
     :typical-depth depth
     :typical-rhythm-regularity regularity}))

;; ============================================================
;; MOVEMENT/ROM ANALYSIS
;; ============================================================

(defn extract-all-angles
  "Extract joint angles from every frame in timeline"
  [timeline]
  (map #(pose/extract-angles (:pose %)) timeline))

(defn compute-rom-ranges
  "Compute ROM (range of motion) for each joint.

  Args:
    all-angles: Sequence of angle maps from timeline

  Returns:
    Map of {:joint-name [min-angle max-angle]}"
  [all-angles]
  (reduce
   (fn [acc angles]
     (merge-with
      (fn [[min1 max1] [min2 max2]]
        [(min min1 min2) (max max1 max2)])
      acc
      (into {} (map (fn [[k v]] [k [v v]]) angles))))
   {}
   all-angles))

(defn analyze-movement-session
  "Extract ROM ranges from free movement session.

  Args:
    timeline: Vector of frames from movement session

  Returns:
    {:rom-ranges map}"
  [timeline]
  (let [all-angles (extract-all-angles timeline)
        rom (compute-rom-ranges all-angles)]
    {:rom-ranges rom}))

;; ============================================================
;; SPECS
;; ============================================================

(s/fdef analyze-t-pose-session
  :args (s/cat :timeline ::schema/timeline)
  :ret (s/keys :req-un [::schema/height-cm ::schema/joint-distances ::schema/baseline-pose]))

(s/fdef analyze-breathing-session
  :args (s/cat :timeline ::schema/timeline)
  :ret ::schema/breathing-baseline)

(s/fdef analyze-movement-session
  :args (s/cat :timeline ::schema/timeline)
  :ret (s/keys :req-un [::schema/rom-ranges]))
```

#### File 2: `src/shared/personalization.cljs`

```clojure
(ns combatsys.personalization
  "Functions for creating and updating user profiles"
  (:require [combatsys.schema :as schema]
            [combatsys.calibration :as cal]
            [clojure.spec.alpha :as s]))

;; ============================================================
;; THRESHOLD COMPUTATION
;; ============================================================

(defn compute-breathing-thresholds
  "Compute personalized breathing thresholds from baseline.

  Fatigue threshold = 50% of typical depth
  Rate alert threshold = 20% of typical rate"
  [breathing-baseline]
  {:fatigue-threshold (* 0.5 (:typical-depth breathing-baseline))
   :rate-alert-threshold (* 0.2 (:typical-rate-bpm breathing-baseline))})

(defn compute-posture-thresholds
  "Compute personalized posture thresholds from baseline.

  Forward head alert = baseline + 2cm
  Shoulder imbalance alert = 2x baseline imbalance"
  [baseline-pose]
  (let [typical-fh (get-in baseline-pose [:forward-head-cm])
        typical-si (get-in baseline-pose [:shoulder-imbalance-deg])]
    {:forward-head-alert-cm (+ typical-fh 2.0)
     :shoulder-imbalance-alert-deg (* 2.0 (Math/abs typical-si))}))

(defn compute-balance-thresholds
  "Compute personalized balance thresholds.

  For LOD 5, use generic threshold. Could be personalized in future."
  []
  {:stability-alert-threshold 0.6})

;; ============================================================
;; PROFILE CREATION
;; ============================================================

(defn find-session-by-type
  "Find calibration session of given type"
  [sessions cal-type]
  (first (filter #(= cal-type (:calibration-type %)) sessions)))

(defn create-user-profile
  "Create complete user profile from 3 calibration sessions.

  Args:
    calibration-sessions: Vector of 3 calibration session maps
                          (types: :t-pose, :breathing, :movement)

  Returns:
    Complete user profile map conforming to ::schema/user-profile"
  [calibration-sessions]
  {:pre [(schema/validate-calibration-sessions calibration-sessions)]}

  (let [;; Find sessions by type
        t-pose-session (find-session-by-type calibration-sessions :t-pose)
        breathing-session (find-session-by-type calibration-sessions :breathing)
        movement-session (find-session-by-type calibration-sessions :movement)

        ;; Analyze each session
        t-pose-data (cal/analyze-t-pose-session (:timeline t-pose-session))
        breathing-data (cal/analyze-breathing-session (:timeline breathing-session))
        movement-data (cal/analyze-movement-session (:timeline movement-session))

        ;; Compute thresholds
        breathing-thresholds (compute-breathing-thresholds breathing-data)
        posture-thresholds (compute-posture-thresholds (:baseline-pose t-pose-data))
        balance-thresholds (compute-balance-thresholds)]

    ;; Assemble profile
    {:user-id (random-uuid)
     :height-cm (:height-cm t-pose-data)
     :baseline-pose (:baseline-pose t-pose-data)
     :learned-thresholds {:breathing-thresholds breathing-thresholds
                          :posture-thresholds posture-thresholds
                          :balance-thresholds balance-thresholds}
     :breathing-baseline breathing-data
     :posture-baseline (select-keys (:baseline-pose t-pose-data)
                                     [:forward-head-cm :shoulder-imbalance-deg])
     :rom-ranges (:rom-ranges movement-data)
     :last-calibration-date (js/Date.)
     :calibration-count 1}))

(defn update-user-profile
  "Update existing profile with new calibration data (future: adaptive learning)"
  [existing-profile new-calibration-sessions]
  ;; For LOD 5: just replace with new profile
  ;; For LOD 5.1+: could blend old and new baselines
  (create-user-profile new-calibration-sessions))

;; ============================================================
;; SPECS
;; ============================================================

(s/fdef create-user-profile
  :args (s/cat :calibration-sessions (s/coll-of ::schema/calibration-session :count 3))
  :ret ::schema/user-profile)

(s/fdef compute-breathing-thresholds
  :args (s/cat :breathing-baseline ::schema/breathing-baseline)
  :ret ::schema/breathing-thresholds)
```

### Tests

#### `test/shared/calibration_test.cljs`

```clojure
(ns combatsys.calibration-test
  (:require [clojure.test :refer [deftest is testing]]
            [combatsys.calibration :as cal]
            [combatsys.mocks :as mocks]))

(deftest test-t-pose-analysis
  (testing "T-pose analysis extracts body measurements"
    (let [timeline (mocks/mock-t-pose-timeline 180)  ;; 180cm height
          result (cal/analyze-t-pose-session timeline)]

      (is (number? (:height-cm result)))
      (is (< 170 (:height-cm result) 190))  ;; Within reasonable range

      (is (map? (:joint-distances result)))
      (is (number? (get-in result [:joint-distances :shoulder-width-cm])))

      (is (map? (:baseline-pose result)))
      (is (vector? (get-in result [:baseline-pose :landmarks]))))))

(deftest test-breathing-analysis
  (testing "Breathing session analysis computes baseline"
    (let [timeline (mocks/mock-breathing-timeline 20 60)  ;; 20 bpm, 60s
          result (cal/analyze-breathing-session timeline)]

      (is (number? (:typical-rate-bpm result)))
      (is (< 18 (:typical-rate-bpm result) 22))  ;; Within 2 bpm

      (is (number? (:typical-depth result)))
      (is (< 0.3 (:typical-depth result) 1.0))

      (is (number? (:typical-rhythm-regularity result)))
      (is (<= 0 (:typical-rhythm-regularity result) 1)))))

(deftest test-movement-analysis
  (testing "Movement session extracts ROM ranges"
    (let [timeline (mocks/mock-movement-timeline 60)
          result (cal/analyze-movement-session timeline)]

      (is (map? (:rom-ranges result)))
      (is (some (fn [[k [min max]]] (and (number? min) (number? max)))
                (:rom-ranges result))))))
```

#### `test/shared/personalization_test.cljs`

```clojure
(ns combatsys.personalization-test
  (:require [clojure.test :refer [deftest is testing]]
            [combatsys.personalization :as pers]
            [combatsys.schema :as schema]
            [combatsys.mocks :as mocks]
            [clojure.spec.alpha :as s]))

(deftest test-threshold-computation
  (testing "Breathing thresholds scale with baseline"
    (let [baseline {:typical-rate-bpm 20 :typical-depth 0.8 :typical-rhythm-regularity 0.9}
          thresholds (pers/compute-breathing-thresholds baseline)]

      (is (= 0.4 (:fatigue-threshold thresholds)))  ;; 50% of 0.8
      (is (= 4.0 (:rate-alert-threshold thresholds)))))  ;; 20% of 20

  (testing "Posture thresholds scale with baseline"
    (let [baseline {:forward-head-cm 3.5 :shoulder-imbalance-deg 1.2}
          thresholds (pers/compute-posture-thresholds baseline)]

      (is (= 5.5 (:forward-head-alert-cm thresholds)))  ;; 3.5 + 2.0
      (is (= 2.4 (:shoulder-imbalance-alert-deg thresholds))))))  ;; 2x 1.2

(deftest test-profile-creation
  (testing "Create profile from calibration sessions"
    (let [sessions [(mocks/mock-calibration-session :t-pose)
                    (mocks/mock-calibration-session :breathing)
                    (mocks/mock-calibration-session :movement)]
          profile (pers/create-user-profile sessions)]

      ;; Profile is valid
      (is (s/valid? ::schema/user-profile profile))

      ;; Has required fields
      (is (uuid? (:user-id profile)))
      (is (number? (:height-cm profile)))
      (is (map? (:learned-thresholds profile)))
      (is (= 1 (:calibration-count profile)))
      (is (inst? (:last-calibration-date profile))))))
```

### Acceptance Criteria

- [ ] All functions compile without warnings
- [ ] All unit tests pass
- [ ] Can create valid user profile from 3 calibration sessions
- [ ] Profile conforms to `::schema/user-profile` spec
- [ ] Thresholds scale appropriately with baselines

---

## TASK 6.3: CALIBRATION WIZARD UI
**Priority**: 🔴 Critical
**Estimated Time**: 6 hours
**Files**: `src/renderer/onboarding.cljs` (create new)

### Objective
Create step-by-step calibration wizard that guides user through recording 3 calibration sessions.

### Deliverables

#### File: `src/renderer/onboarding.cljs`

```clojure
(ns combatsys.renderer.onboarding
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [combatsys.renderer.video-capture :as video]))

;; ============================================================
;; CALIBRATION WIZARD STATE
;; ============================================================

(def wizard-steps
  [{:step :t-pose
    :title "Step 1: T-Pose"
    :description "Stand in a T-pose so we can measure your body proportions"
    :instructions ["Stand facing the camera"
                   "Extend arms horizontally at shoulder height"
                   "Keep body straight and relaxed"
                   "Hold for 10 seconds"]
    :duration-seconds 10
    :visual-guide :t-pose-diagram}

   {:step :breathing
    :title "Step 2: Normal Breathing"
    :description "Breathe naturally so we can learn your baseline"
    :instructions ["Stand or sit comfortably"
                   "Breathe normally (don't exaggerate)"
                   "Stay relaxed for 60 seconds"]
    :duration-seconds 60
    :visual-guide :breathing-diagram}

   {:step :movement
    :title "Step 3: Free Movement"
    :description "Move naturally so we can measure your range of motion"
    :instructions ["Move arms and legs through full range"
                   "Squats, arm circles, torso twists"
                   "Stay in camera view"
                   "Continue for 60 seconds"]
    :duration-seconds 60
    :visual-guide :movement-diagram}])

;; ============================================================
;; COMPONENTS
;; ============================================================

(defn progress-bar [current-step total-steps]
  [:div.calibration-progress
   (for [i (range total-steps)]
     ^{:key i}
     [:div.progress-dot
      {:class (cond
                (< i current-step) "completed"
                (= i current-step) "active"
                :else "pending")}])])

(defn instruction-list [instructions]
  [:ul.instructions
   (for [instruction instructions]
     ^{:key instruction}
     [:li instruction])])

(defn countdown-timer [seconds-remaining]
  [:div.countdown
   [:div.countdown-circle
    [:span.countdown-number seconds-remaining]]
   [:div.countdown-label "seconds remaining"]])

(defn step-view
  "Render a single calibration step"
  [{:keys [step title description instructions duration-seconds visual-guide]} recording?]
  (let [seconds-remaining (rf/subscribe [:calibration/seconds-remaining])]
    [:div.calibration-step
     [:h2 title]
     [:p.description description]

     ;; Visual guide
     [:div.visual-guide
      (case visual-guide
        :t-pose-diagram [t-pose-diagram]
        :breathing-diagram [breathing-diagram]
        :movement-diagram [movement-diagram])]

     ;; Live camera preview with skeleton overlay
     [:div.camera-preview
      [video/camera-feed-view]
      [video/skeleton-overlay-view]]

     ;; Instructions
     [instruction-list instructions]

     ;; Recording UI
     (if recording?
       [:div.recording-controls
        [:div.recording-indicator "🔴 Recording..."]
        [countdown-timer @seconds-remaining]
        [:button.btn-secondary {:on-click #(rf/dispatch [:calibration/cancel-step])}
         "Cancel"]]

       [:div.step-controls
        [:button.btn-primary {:on-click #(rf/dispatch [:calibration/start-step step])}
         (str "Start (" duration-seconds "s)")]
        [:button.btn-secondary {:on-click #(rf/dispatch [:calibration/skip-all])}
         "Skip (use generic baseline)"]])]))

(defn welcome-screen []
  [:div.calibration-welcome
   [:h1 "Welcome to CombatSys!"]
   [:p.tagline "Let's personalize your experience"]

   [:div.calibration-explanation
    [:h3 "Why calibrate?"]
    [:p "Everyone's body is different. By learning YOUR baseline, we can give you accurate, personalized feedback."]

    [:h3 "What we'll do:"]
    [:ol
     [:li "T-pose (10 seconds) - Measure your body proportions"]
     [:li "Normal breathing (60 seconds) - Learn your respiratory baseline"]
     [:li "Free movement (60 seconds) - Measure your range of motion"]]

    [:p.time-estimate "⏱ Total time: ~3 minutes"]]

   [:div.cta-buttons
    [:button.btn-primary.btn-large {:on-click #(rf/dispatch [:calibration/start])}
     "Start Calibration"]
    [:button.btn-secondary {:on-click #(rf/dispatch [:calibration/skip-all])}
     "Skip (use generic baseline)"]]])

(defn completion-screen [profile]
  [:div.calibration-complete
   [:h1 "✓ Calibration Complete!"]
   [:p "Your personalized baseline has been created."]

   [:div.profile-summary
    [:h3 "Your Profile"]
    [:table.profile-stats
     [:tbody
      [:tr
       [:td "Height"]
       [:td (str (:height-cm profile) " cm")]]
      [:tr
       [:td "Typical breathing rate"]
       [:td (str (get-in profile [:breathing-baseline :typical-rate-bpm]) " bpm")]]
      [:tr
       [:td "Typical breathing depth"]
       [:td (str (* 100 (get-in profile [:breathing-baseline :typical-depth])) "%")]]
      [:tr
       [:td "Forward head baseline"]
       [:td (str (get-in profile [:posture-baseline :typical-forward-head-cm]) " cm")]]]]]

   [:button.btn-primary.btn-large {:on-click #(rf/dispatch [:ui/set-view :live-feed])}
    "Start Using CombatSys"]])

(defn wizard-view []
  (let [current-step-idx (rf/subscribe [:calibration/current-step])
        recording? (rf/subscribe [:calibration/recording?])
        profile (rf/subscribe [:calibration/completed-profile])]

    (fn []
      [:div.calibration-wizard
       (cond
         ;; Not started
         (nil? @current-step-idx)
         [welcome-screen]

         ;; In progress
         (< @current-step-idx (count wizard-steps))
         (let [step (nth wizard-steps @current-step-idx)]
           [:div.wizard-in-progress
            [progress-bar @current-step-idx (count wizard-steps)]
            [step-view step @recording?]])

         ;; Complete
         :else
         [completion-screen @profile])])))

;; ============================================================
;; HELPER COMPONENTS
;; ============================================================

(defn t-pose-diagram []
  [:svg.diagram {:width 200 :height 300}
   ;; Simple stick figure in T-pose
   [:circle {:cx 100 :cy 50 :r 20 :fill "#333"}]  ;; Head
   [:line {:x1 100 :y1 70 :x2 100 :y2 150 :stroke "#333" :stroke-width 3}]  ;; Torso
   [:line {:x1 40 :y1 100 :x2 160 :y2 100 :stroke "#333" :stroke-width 3}]  ;; Arms
   [:line {:x1 100 :y1 150 :x2 70 :y2 250 :stroke "#333" :stroke-width 3}]  ;; Left leg
   [:line {:x1 100 :y1 150 :x2 130 :y2 250 :stroke "#333" :stroke-width 3}]])  ;; Right leg

(defn breathing-diagram []
  [:svg.diagram {:width 200 :height 200}
   ;; Simple representation of breathing
   [:circle {:cx 100 :cy 100 :r 60 :fill "none" :stroke "#4A90E2" :stroke-width 3}]
   [:path {:d "M 100 100 L 100 50 M 95 60 L 100 50 L 105 60"
           :stroke "#4A90E2" :stroke-width 2 :fill "none"}]])

(defn movement-diagram []
  [:svg.diagram {:width 200 :height 200}
   ;; Movement arrows
   [:path {:d "M 50 100 L 150 100 M 140 90 L 150 100 L 140 110"
           :stroke "#333" :stroke-width 2 :fill "none"}]
   [:path {:d "M 100 50 L 100 150 M 90 140 L 100 150 L 110 140"
           :stroke "#333" :stroke-width 2 :fill "none"}]])
```

#### re-frame Events & Subscriptions

Add to `src/renderer/state.cljs`:

```clojure
;; ============================================================
;; CALIBRATION STATE
;; ============================================================

(rf/reg-event-db
 :calibration/start
 (fn [db _]
   (assoc db
          :calibration {:current-step-idx 0
                        :recording? false
                        :sessions []
                        :seconds-remaining 0})))

(rf/reg-event-db
 :calibration/start-step
 (fn [db [_ step-type]]
   (let [step (first (filter #(= step-type (:step %)) wizard-steps))
         duration (:duration-seconds step)]
     (-> db
         (assoc-in [:calibration :recording?] true)
         (assoc-in [:calibration :seconds-remaining] duration)
         (assoc-in [:calibration :current-session-type] step-type))
     ;; TODO: Start camera recording
     )))

(rf/reg-event-db
 :calibration/tick
 (fn [db _]
   (let [remaining (get-in db [:calibration :seconds-remaining])]
     (if (> remaining 0)
       (update-in db [:calibration :seconds-remaining] dec)
       ;; Time's up - save session and move to next step
       (rf/dispatch [:calibration/complete-step])
       db))))

(rf/reg-event-db
 :calibration/complete-step
 (fn [db _]
   (let [session {:calibration-type (get-in db [:calibration :current-session-type])
                  :timeline (get-in db [:capture :recording-timeline])
                  :created-at (js/Date.)
                  :duration-ms (* 1000 (get-in db [:calibration :seconds-remaining]))}]
     (-> db
         (update-in [:calibration :sessions] conj session)
         (update-in [:calibration :current-step-idx] inc)
         (assoc-in [:calibration :recording?] false)
         ;; If all 3 steps done, create profile
         (as-> db
           (if (= 3 (count (get-in db [:calibration :sessions])))
             (let [profile (pers/create-user-profile (get-in db [:calibration :sessions]))]
               (assoc-in db [:calibration :completed-profile] profile))
             db))))))

(rf/reg-event-db
 :calibration/skip-all
 (fn [db _]
   ;; Set user profile to nil (will use generic thresholds)
   (assoc db :user-profile nil)))

(rf/reg-sub
 :calibration/current-step
 (fn [db _]
   (get-in db [:calibration :current-step-idx])))

(rf/reg-sub
 :calibration/recording?
 (fn [db _]
   (get-in db [:calibration :recording?])))

(rf/reg-sub
 :calibration/seconds-remaining
 (fn [db _]
   (get-in db [:calibration :seconds-remaining])))

(rf/reg-sub
 :calibration/completed-profile
 (fn [db _]
   (get-in db [:calibration :completed-profile])))
```

### Acceptance Criteria

- [ ] Wizard renders all 3 steps with instructions
- [ ] Can start recording for each step
- [ ] Countdown timer updates each second
- [ ] Auto-advances to next step when time expires
- [ ] Shows completion screen with profile summary
- [ ] Can skip calibration (uses generic baseline)

---

## TASK 6.4: PROFILE STORAGE & PERSISTENCE
**Priority**: 🔴 Critical
**Estimated Time**: 3 hours
**Files**: `src/renderer/persistence.cljs` (modify existing)

### Objective
Save and load user profiles from disk, with proper error handling.

### Deliverables

Add to `src/renderer/persistence.cljs`:

```clojure
;; ============================================================
;; USER PROFILE PERSISTENCE
;; ============================================================

(def profiles-dir "profiles")

(defn get-profile-path
  "Get file path for user profile"
  [user-id]
  (str profiles-dir "/" user-id ".edn"))

(defn save-user-profile!
  "Save user profile to disk.

  Args:
    profile: User profile map

  Returns:
    File path where profile was saved"
  [profile]
  (let [user-id (:user-id profile)
        path (get-profile-path user-id)
        edn-str (with-out-str (cljs.pprint/pprint profile))]

    ;; Ensure profiles directory exists
    (ensure-dir-exists! profiles-dir)

    ;; Write profile
    (write-file! path edn-str)

    (js/console.log "User profile saved:" path)
    path))

(defn load-user-profile!
  "Load user profile from disk.

  Args:
    user-id: UUID of user

  Returns:
    User profile map, or nil if not found"
  [user-id]
  (let [path (get-profile-path user-id)]
    (try
      (when (file-exists? path)
        (let [edn-str (read-file! path)
              profile (cljs.reader/read-string edn-str)]
          (if (schema/valid-user-profile? profile)
            (do
              (js/console.log "User profile loaded:" path)
              profile)
            (do
              (js/console.error "Invalid profile format:" path)
              nil))))
      (catch js/Error e
        (js/console.error "Error loading profile:" e)
        nil))))

(defn list-user-profiles!
  "List all user profiles on disk.

  Returns:
    Sequence of {:user-id UUID :last-calibration-date inst?}"
  []
  (try
    (let [files (list-files! profiles-dir ".edn")]
      (for [file files
            :let [profile (load-user-profile! (extract-user-id file))]
            :when profile]
        (select-keys profile [:user-id :last-calibration-date :height-cm])))
    (catch js/Error e
      (js/console.error "Error listing profiles:" e)
      [])))

(defn get-default-user-profile!
  "Get the default (most recently used) user profile.

  For single-user app, this is just the first profile found.
  For multi-user, could check last-used timestamp."
  []
  (let [profiles (list-user-profiles!)]
    (when (seq profiles)
      (let [user-id (:user-id (first profiles))]
        (load-user-profile! user-id)))))

(defn delete-user-profile!
  "Delete user profile from disk"
  [user-id]
  (let [path (get-profile-path user-id)]
    (when (file-exists? path)
      (delete-file! path)
      (js/console.log "User profile deleted:" path))))
```

#### Integration with App Initialization

Modify `src/renderer/core.cljs`:

```clojure
(defn init-app []
  ;; Load user profile on startup
  (let [profile (persistence/get-default-user-profile!)]
    (if profile
      (do
        (js/console.log "Found existing user profile")
        (rf/dispatch [:user-profile/loaded profile])
        (rf/dispatch [:ui/set-view :live-feed]))
      (do
        (js/console.log "No user profile found - starting calibration")
        (rf/dispatch [:ui/set-view :onboarding]))))

  ;; ... rest of init
  )
```

Add to `src/renderer/state.cljs`:

```clojure
(rf/reg-event-db
 :user-profile/loaded
 (fn [db [_ profile]]
   (assoc db :user-profile profile)))

(rf/reg-event-fx
 :user-profile/save
 (fn [{:keys [db]} [_ profile]]
   (persistence/save-user-profile! profile)
   {:db (assoc db :user-profile profile)}))
```

### Acceptance Criteria

- [ ] Can save user profile to `~/.combatsys/profiles/{user-id}.edn`
- [ ] Can load user profile on app startup
- [ ] If no profile exists, shows onboarding wizard
- [ ] If profile exists, loads it and proceeds to main app
- [ ] Handles corrupted/invalid profile files gracefully

---

## TASK 6.5: ANALYZER INTEGRATION (PERSONALIZED THRESHOLDS)
**Priority**: 🔴 Critical
**Estimated Time**: 4 hours
**Files**: `src/shared/breathing.cljs` (modify), `src/shared/posture.cljs` (modify)

### Objective
Modify existing analyzers to accept user-profile parameter and use personalized thresholds.

### Deliverables

#### Modify `src/shared/breathing.cljs`

```clojure
;; Change function signature
(defn analyze
  "Analyze breathing from timeline.

  Args:
    timeline: Vector of frames
    user-profile: (optional) User profile with learned thresholds

  Returns:
    Breathing analysis map with personalized insights"
  ([timeline] (analyze timeline nil))
  ([timeline user-profile]
   (let [signal (extract-torso-motion timeline)
         rate (detect-breathing-rate signal)
         depth (compute-signal-amplitude signal)

         ;; Get threshold from profile or use default
         fatigue-threshold (if user-profile
                             (get-in user-profile
                                     [:learned-thresholds :breathing-thresholds :fatigue-threshold])
                             0.3)  ;; Default fallback

         fatigue-windows (detect-fatigue-windows signal fatigue-threshold)

         ;; Compute delta from baseline (if profile exists)
         baseline-rate (when user-profile
                         (get-in user-profile [:breathing-baseline :typical-rate-bpm]))
         delta-rate (when baseline-rate (- rate baseline-rate))
         pct-change (when baseline-rate (* 100 (/ delta-rate baseline-rate)))]

     {:rate-bpm rate
      :depth-score depth
      :fatigue-windows fatigue-windows
      :baseline-rate baseline-rate
      :delta-from-baseline delta-rate
      :pct-change pct-change
      :insights (generate-insights rate depth fatigue-windows user-profile)})))

(defn generate-insights
  "Generate natural language insights from analysis"
  [rate depth fatigue-windows user-profile]
  (let [insights []]
    (cond-> insights
      ;; Personalized rate insight
      (and user-profile (:delta-from-baseline user-profile))
      (conj (let [delta (:delta-from-baseline user-profile)
                  pct (:pct-change user-profile)]
              {:title (if (pos? delta) "Breathing rate elevated" "Breathing rate lowered")
               :description (str "Your rate is " (Math/abs pct) "% "
                                 (if (pos? delta) "above" "below")
                                 " your baseline of " (:baseline-rate user-profile) " bpm")
               :severity (if (> (Math/abs pct) 20) :high :low)}))

      ;; Generic rate insight (no profile)
      (and (not user-profile) (or (< rate 10) (> rate 30)))
      (conj {:title "Unusual breathing rate"
             :description (str "Your rate of " rate " bpm is outside typical range (12-20 bpm)")
             :severity :medium})

      ;; Fatigue windows
      (seq fatigue-windows)
      (conj {:title "Breathing interruptions detected"
             :description (str (count fatigue-windows) " periods of shallow/stopped breathing")
             :severity :high}))))
```

#### Modify `src/shared/posture.cljs`

```clojure
(defn analyze
  "Analyze posture from timeline.

  Args:
    timeline: Vector of frames
    user-profile: (optional) User profile with learned thresholds

  Returns:
    Posture analysis map with personalized insights"
  ([timeline] (analyze timeline nil))
  ([timeline user-profile]
   (let [avg-pose (average-poses timeline)
         forward-head (measure-forward-head avg-pose)
         shoulder-imbalance (measure-shoulder-imbalance avg-pose)
         spine-alignment (assess-spine-alignment avg-pose)

         ;; Get baseline from profile
         baseline-fh (when user-profile
                       (get-in user-profile [:posture-baseline :typical-forward-head-cm]))
         baseline-si (when user-profile
                       (get-in user-profile [:posture-baseline :typical-shoulder-imbalance-deg]))

         ;; Get alert thresholds
         alert-fh (if user-profile
                    (get-in user-profile [:learned-thresholds :posture-thresholds :forward-head-alert-cm])
                    5.0)  ;; Default
         alert-si (if user-profile
                    (get-in user-profile [:learned-thresholds :posture-thresholds :shoulder-imbalance-alert-deg])
                    5.0)]  ;; Default

     {:head-forward-cm forward-head
      :shoulder-imbalance-deg shoulder-imbalance
      :spine-alignment spine-alignment
      :baseline-forward-head baseline-fh
      :baseline-shoulder-imbalance baseline-si
      :alerts (generate-posture-alerts forward-head shoulder-imbalance
                                        alert-fh alert-si)
      :insights (generate-posture-insights forward-head shoulder-imbalance
                                            spine-alignment user-profile)})))
```

#### Update UI to Pass User Profile

Modify `src/renderer/views.cljs`:

```clojure
(defn analyze-session-button []
  (let [current-session (rf/subscribe [:current-session])
        user-profile (rf/subscribe [:user-profile])]  ;; NEW
    [:button.btn-primary
     {:on-click #(do
                   ;; Pass user-profile to analyzers
                   (rf/dispatch [:session/analyze
                                 (:session-id @current-session)
                                 @user-profile]))}
     "Analyze Session"]))

;; In event handler
(rf/reg-event-db
 :session/analyze
 (fn [db [_ session-id user-profile]]
   (let [session (get-in db [:sessions session-id])
         timeline (:timeline session)

         ;; Pass user-profile to analyzers
         breathing-analysis (breathing/analyze timeline user-profile)
         posture-analysis (posture/analyze timeline user-profile)]

     (assoc-in db [:sessions session-id :analysis]
               {:breathing breathing-analysis
                :posture posture-analysis}))))
```

### Acceptance Criteria

- [ ] Analyzers accept optional user-profile parameter
- [ ] When profile provided, use personalized thresholds
- [ ] When no profile, use generic defaults
- [ ] Insights reference baseline ("15% below your baseline")
- [ ] All existing tests still pass (with nil profile)

---

## TASK 6.6: PROFILE VIEW & MANAGEMENT
**Priority**: 🟡 Important
**Estimated Time**: 3 hours
**Files**: `src/renderer/profile_view.cljs` (create new)

### Objective
Create UI for viewing and managing user profile, with option to recalibrate.

### Deliverables

#### File: `src/renderer/profile_view.cljs`

```clojure
(ns combatsys.renderer.profile-view
  (:require [reagent.core :as r]
            [re-frame.core :as rf]))

(defn profile-header [profile]
  [:div.profile-header
   [:h1 "Your Profile"]
   [:p.user-id (str "ID: " (:user-id profile))]])

(defn baseline-metrics [profile]
  [:div.baseline-metrics
   [:h2 "Physical Baseline"]
   [:table.metrics-table
    [:tbody
     [:tr
      [:td.label "Height"]
      [:td.value (str (:height-cm profile) " cm")]]
     [:tr
      [:td.label "Shoulder Width"]
      [:td.value (str (get-in profile [:baseline-pose :joint-distances :shoulder-width-cm]) " cm")]]
     [:tr
      [:td.label "Arm Length (avg)"]
      [:td.value (str (Math/round
                       (/ (+ (get-in profile [:baseline-pose :joint-distances :arm-length-left-cm])
                             (get-in profile [:baseline-pose :joint-distances :arm-length-right-cm]))
                          2))
                      " cm")]]]]])

(defn breathing-baseline [profile]
  (let [baseline (:breathing-baseline profile)]
    [:div.breathing-baseline
     [:h2 "Breathing Baseline"]
     [:table.metrics-table
      [:tbody
       [:tr
        [:td.label "Typical Rate"]
        [:td.value (str (:typical-rate-bpm baseline) " bpm")]]
       [:tr
        [:td.label "Typical Depth"]
        [:td.value (str (Math/round (* 100 (:typical-depth baseline))) "%")]]
       [:tr
        [:td.label "Rhythm Regularity"]
        [:td.value (str (Math/round (* 100 (:typical-rhythm-regularity baseline))) "%")]]]]]))

(defn posture-baseline [profile]
  (let [baseline (:posture-baseline profile)]
    [:div.posture-baseline
     [:h2 "Posture Baseline"]
     [:table.metrics-table
      [:tbody
       [:tr
        [:td.label "Forward Head"]
        [:td.value (str (:typical-forward-head-cm baseline) " cm")]]
       [:tr
        [:td.label "Shoulder Imbalance"]
        [:td.value (str (:typical-shoulder-imbalance-deg baseline) "°")]]]]]))

(defn threshold-settings [profile]
  [:div.threshold-settings
   [:h2 "Alert Thresholds"]
   [:p.explanation "These thresholds trigger warnings during analysis."]

   [:h3 "Breathing"]
   [:table.metrics-table
    [:tbody
     [:tr
      [:td.label "Fatigue Threshold"]
      [:td.value (str (get-in profile [:learned-thresholds :breathing-thresholds :fatigue-threshold]))]]
     [:tr
      [:td.label "Rate Alert Threshold"]
      [:td.value (str (get-in profile [:learned-thresholds :breathing-thresholds :rate-alert-threshold]) " bpm")]]]]

   [:h3 "Posture"]
   [:table.metrics-table
    [:tbody
     [:tr
      [:td.label "Forward Head Alert"]
      [:td.value (str (get-in profile [:learned-thresholds :posture-thresholds :forward-head-alert-cm]) " cm")]]
     [:tr
      [:td.label "Shoulder Imbalance Alert"]
      [:td.value (str (get-in profile [:learned-thresholds :posture-thresholds :shoulder-imbalance-alert-deg]) "°")]]]]])

(defn calibration-history [profile]
  [:div.calibration-history
   [:h2 "Calibration History"]
   [:p (str "Last calibrated: " (.toLocaleDateString (:last-calibration-date profile)))]
   [:p (str "Total calibrations: " (:calibration-count profile))]])

(defn profile-actions []
  [:div.profile-actions
   [:button.btn-primary
    {:on-click #(rf/dispatch [:calibration/start])}
    "Recalibrate"]
   [:button.btn-secondary
    {:on-click #(rf/dispatch [:profile/export])}
    "Export Profile"]
   [:button.btn-danger
    {:on-click #(when (js/confirm "Delete your profile? This cannot be undone.")
                  (rf/dispatch [:profile/delete]))}
    "Delete Profile"]])

(defn profile-view []
  (let [profile (rf/subscribe [:user-profile])]
    (fn []
      (if @profile
        [:div.profile-view
         [profile-header @profile]
         [:div.profile-content
          [baseline-metrics @profile]
          [breathing-baseline @profile]
          [posture-baseline @profile]
          [threshold-settings @profile]
          [calibration-history @profile]
          [profile-actions]]]

        [:div.no-profile
         [:h2 "No Profile Found"]
         [:p "You haven't calibrated yet."]
         [:button.btn-primary
          {:on-click #(rf/dispatch [:calibration/start])}
          "Start Calibration"]]))))
```

Add navigation:

```clojure
;; In src/renderer/views.cljs navbar
[:button {:on-click #(rf/dispatch [:ui/set-view :profile])}
 "Profile"]
```

### Acceptance Criteria

- [ ] Profile view shows all baseline metrics
- [ ] "Recalibrate" button starts calibration wizard
- [ ] Can navigate to profile from main app
- [ ] Shows "No Profile" message if not calibrated

---

## INTEGRATION TESTING

### End-to-End Test Scenario

```
1. Fresh install (no profile)
   → Opens to onboarding wizard

2. Complete calibration
   → Record T-pose (10s)
   → Record breathing (60s)
   → Record movement (60s)
   → See completion screen with baseline summary

3. Record new session
   → Record 60s breathing session
   → Click "Analyze"
   → See personalized insights: "18 bpm (15% below your baseline of 21 bpm)"

4. View profile
   → Navigate to Profile view
   → See all baseline metrics
   → See alert thresholds

5. Recalibrate
   → Click "Recalibrate"
   → Complete wizard again
   → New baseline replaces old one
   → Future analyses use new baseline

6. Persistence
   → Close app
   → Reopen app
   → Profile is loaded automatically
   → No onboarding wizard shown
```

---

## FINAL CHECKLIST

### Code Quality
- [ ] All functions compile without warnings
- [ ] All unit tests pass (12 tests)
- [ ] All integration tests pass (3 tests)
- [ ] Code follows ClojureScript idioms (threading, destructuring)
- [ ] All functions have docstrings

### Functionality
- [ ] Calibration wizard works end-to-end
- [ ] User profile saved to disk
- [ ] Profile loaded on app startup
- [ ] Analyzers use personalized thresholds
- [ ] Insights reference baseline
- [ ] Can recalibrate
- [ ] Can view profile

### User Experience
- [ ] Calibration instructions are clear
- [ ] Visual feedback during recording
- [ ] Completion screen shows meaningful summary
- [ ] Profile view is informative
- [ ] No errors in console

### Documentation
- [ ] README updated with calibration info
- [ ] PLAN.md marked LOD 5 complete
- [ ] Code comments explain personalization logic

---

## TIME TRACKING

| Task | Estimated | Actual | Status |
|------|-----------|--------|--------|
| 6.1 Schema Extensions | 2h | | [ ] |
| 6.2 Calibration Analysis | 6h | | [ ] |
| 6.3 Calibration Wizard | 6h | | [ ] |
| 6.4 Profile Storage | 3h | | [ ] |
| 6.5 Analyzer Integration | 4h | | [ ] |
| 6.6 Profile View | 3h | | [ ] |
| **Total** | **24h** | | |

---

**Ready to ship LOD 5? Let's make it personal.** 🎯
