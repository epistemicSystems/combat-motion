(ns combatsys.mocks
  "Mock data generators for LOD 0 development.
   
   These create realistic-looking fake data that conforms to our schemas.
   Allows the entire app to run without real camera/pose estimation.
   
   Philosophy (Paul Graham):
   - Always have something working
   - Mock data lets us iterate on UX before heavy ML integration"
  (:require [combatsys.schema :as schema]))

;; ============================================================
;; MOCK LANDMARKS
;; ============================================================

(defn mock-landmark
  "Generate a single mock landmark with realistic coordinates"
  [id base-x base-y base-z noise]
  (schema/landmark
   id
   (+ base-x (* noise (- (rand) 0.5)))
   (+ base-y (* noise (- (rand) 0.5)))
   (+ base-z (* noise (- (rand) 0.5)))
   (+ 0.9 (* 0.1 (rand))))) ;; 90-100% visibility

(defn mock-standing-pose
  "Generate a standing T-pose with slight variation"
  []
  (let [noise 0.02] ;; Small random variation
    [(mock-landmark :nose 0.5 0.3 -0.1 noise)
     (mock-landmark :left-eye 0.48 0.28 -0.08 noise)
     (mock-landmark :right-eye 0.52 0.28 -0.08 noise)
     (mock-landmark :left-ear 0.46 0.30 0.0 noise)
     (mock-landmark :right-ear 0.54 0.30 0.0 noise)
     (mock-landmark :mouth-left 0.48 0.32 -0.05 noise)
     (mock-landmark :mouth-right 0.52 0.32 -0.05 noise)
     (mock-landmark :left-shoulder 0.42 0.42 0.0 noise)
     (mock-landmark :right-shoulder 0.58 0.42 0.0 noise)
     (mock-landmark :left-elbow 0.35 0.52 0.0 noise)
     (mock-landmark :right-elbow 0.65 0.52 0.0 noise)
     (mock-landmark :left-wrist 0.30 0.62 0.0 noise)
     (mock-landmark :right-wrist 0.70 0.62 0.0 noise)
     (mock-landmark :left-hip 0.45 0.58 0.0 noise)
     (mock-landmark :right-hip 0.55 0.58 0.0 noise)
     (mock-landmark :left-knee 0.44 0.72 0.0 noise)
     (mock-landmark :right-knee 0.56 0.72 0.0 noise)
     (mock-landmark :left-ankle 0.43 0.88 0.0 noise)
     (mock-landmark :right-ankle 0.57 0.88 0.0 noise)]))

(defn mock-breathing-pose
  "Generate a pose with simulated breathing motion in chest"
  [phase] ;; 0-1, where 0 = exhale, 0.5 = inhale peak
  (let [chest-expansion (* 0.03 (Math/sin (* Math/PI 2 phase)))
        noise 0.015]
    [(mock-landmark :nose 0.5 0.3 -0.1 noise)
     (mock-landmark :left-shoulder (- 0.42 chest-expansion) 0.42 chest-expansion noise)
     (mock-landmark :right-shoulder (+ 0.58 chest-expansion) 0.42 chest-expansion noise)
     (mock-landmark :left-hip 0.45 0.58 0.0 noise)
     (mock-landmark :right-hip 0.55 0.58 0.0 noise)
     (mock-landmark :left-knee 0.44 0.72 0.0 noise)
     (mock-landmark :right-knee 0.56 0.72 0.0 noise)
     (mock-landmark :left-ankle 0.43 0.88 0.0 noise)
     (mock-landmark :right-ankle 0.57 0.88 0.0 noise)]))

;; ============================================================
;; MOCK FRAMES
;; ============================================================

(defn mock-frame
  "Generate a single mock frame"
  [index timestamp-ms landmarks]
  (schema/frame
   index
   timestamp-ms
   (schema/pose landmarks 0.94)))

(defn mock-breathing-timeline
  "Generate a timeline simulating breathing at given BPM"
  [duration-sec bpm]
  (let [fps 30
        frame-count (* duration-sec fps)
        breath-period-frames (/ (* fps 60) bpm)]
    (mapv
     (fn [i]
       (let [phase (mod (/ i breath-period-frames) 1.0)
             landmarks (mock-breathing-pose phase)]
         (mock-frame i (* i (/ 1000 fps)) landmarks)))
     (range frame-count))))

(defn mock-static-timeline
  "Generate a timeline with minimal motion (T-pose)"
  [duration-sec]
  (let [fps 30
        frame-count (* duration-sec fps)]
    (mapv
     (fn [i]
       (mock-frame i (* i (/ 1000 fps)) (mock-standing-pose)))
     (range frame-count))))

;; ============================================================
;; MOCK SESSIONS
;; ============================================================

(defn mock-breathing-session
  "Create a complete mock session with breathing data"
  [duration-sec bpm]
  (let [user-id (random-uuid)
        session (schema/new-session user-id)
        timeline (mock-breathing-timeline duration-sec bpm)]
    (-> session
        (assoc :session/status :complete)
        (assoc :session/duration-ms (* duration-sec 1000))
        (assoc :session/timeline timeline)
        (assoc-in [:session/analysis :breathing]
                  (schema/breathing-analysis
                   bpm
                   0.75
                   [{:start-ms 30000 :end-ms 33000 :severity 0.8}
                    {:start-ms 55000 :end-ms 58000 :severity 0.6}])))))

(defn mock-static-session
  "Create a mock session with static pose"
  [duration-sec]
  (let [user-id (random-uuid)
        session (schema/new-session user-id)
        timeline (mock-static-timeline duration-sec)]
    (-> session
        (assoc :session/status :complete)
        (assoc :session/duration-ms (* duration-sec 1000))
        (assoc :session/timeline timeline))))

;; ============================================================
;; MOCK ANALYSIS RESULTS
;; ============================================================

(defn mock-breathing-analysis
  "Generate mock breathing analysis results"
  []
  {:rate-bpm 22
   :depth-score 0.75
   :rhythm-regularity 0.88
   :fatigue-windows
   [{:start-ms 30000 :end-ms 33000 :severity 0.8}
    {:start-ms 55000 :end-ms 58000 :severity 0.6}]
   :insights
   [{:insight/title "Breathing window shortened"
     :insight/description "Your aerobic capacity is degrading"
     :insight/severity :medium
     :insight/frames [400 410 420]
     :insight/recommendation "Practice nasal breathing during drilling"}]})

(defn mock-posture-analysis
  "Generate mock posture analysis results"
  []
  {:head-forward-cm 4.2
   :shoulder-imbalance-deg 3.5
   :spine-alignment :neutral
   :overall-score 0.84
   :insights
   [{:insight/title "Forward head posture detected"
     :insight/description "Head is 4.2cm forward of shoulders"
     :insight/severity :low
     :insight/recommendation "Practice chin tucks and neck stretches"}]})

;; ============================================================
;; MOCK REAL-TIME EVENTS
;; ============================================================

(defn mock-breathing-event
  "Generate a mock breathing pause event"
  []
  (schema/event
   :breathing-pause
   0.8
   "Breathing stopped for 2 seconds"))

(defn mock-posture-event
  "Generate a mock posture violation event"
  []
  (schema/event
   :forward-head
   0.6
   "Head posture: 5cm forward"))

;; ============================================================
;; EXPORT FOR TESTING
;; ============================================================

(def demo-session
  "A pre-generated demo session for immediate use"
  (mock-breathing-session 60 22))
