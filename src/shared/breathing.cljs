(ns combatsys.breathing
  "Breathing analysis from pose timeline.
   
   LOD 0: Returns hardcoded mock results
   LOD 2+: Real FFT-based breathing detection
   
   Philosophy (Rich Hickey):
   - Pure function: timeline → analysis
   - No side effects, fully testable
   - Composable with other analyzers"
  (:require [combatsys.schema :as schema]
            [combatsys.mocks :as mocks]))

;; ============================================================
;; TORSO MOTION EXTRACTION (Stub for LOD 0)
;; ============================================================

(defn extract-torso-motion
  "Extract torso landmarks (shoulders, chest) from timeline.
   
   LOD 0: Just returns mock data
   LOD 2+: Real landmark filtering and smoothing"
  [timeline]
  ;; Stub: return fake oscillating signal
  (mapv (fn [frame]
          (let [t (:frame/timestamp-ms frame)
                phase (mod (/ t 3000) 1.0)] ;; ~20 bpm fake breathing
            {:timestamp-ms t
             :torso-motion (Math/sin (* Math/PI 2 phase))}))
        timeline))

;; ============================================================
;; BREATHING RATE DETECTION (Stub for LOD 0)
;; ============================================================

(defn detect-breathing-rate
  "Compute breathing rate from torso motion signal.
   
   LOD 0: Returns hardcoded 22 bpm
   LOD 2+: FFT-based periodicity detection"
  [torso-signal]
  ;; Stub: always return 22 bpm
  22)

(defn compute-depth-score
  "Estimate breathing depth from motion amplitude.
   
   LOD 0: Returns hardcoded 0.75
   LOD 2+: RMS amplitude computation"
  [torso-signal]
  ;; Stub
  0.75)

;; ============================================================
;; FATIGUE WINDOW DETECTION (Stub for LOD 0)
;; ============================================================

(defn detect-fatigue-windows
  "Find periods where breathing stops or becomes shallow.
   
   LOD 0: Returns hardcoded windows
   LOD 2+: Pattern matching on signal drops"
  [torso-signal]
  ;; Stub: fake fatigue windows
  [{:start-ms 30000 :end-ms 33000 :severity 0.8}
   {:start-ms 55000 :end-ms 58000 :severity 0.6}])

;; ============================================================
;; INSIGHT GENERATION (Stub for LOD 0)
;; ============================================================

(defn generate-insights
  "Convert metrics into coaching language.
   
   LOD 0: Returns hardcoded insights
   LOD 3+: Template-based insight generation"
  [rate depth-score fatigue-windows]
  [{:insight/title "Breathing window shortened"
    :insight/description "Your aerobic capacity is degrading"
    :insight/severity :medium
    :insight/frames [400 410 420]
    :insight/recommendation "Practice nasal breathing during drilling"}])

;; ============================================================
;; MAIN ANALYZER (Pure function: session → session)
;; ============================================================

(defn analyze
  "Main entry point: analyze breathing patterns in a session.
   
   Input: session map with :session/timeline
   Output: same session with :session/analysis populated
   
   This is a PURE FUNCTION - no side effects, fully testable."
  [session]
  (let [timeline (:session/timeline session)
        
        ;; Extract and process (all stubs for LOD 0)
        torso-signal (extract-torso-motion timeline)
        rate (detect-breathing-rate torso-signal)
        depth (compute-depth-score torso-signal)
        windows (detect-fatigue-windows torso-signal)
        insights (generate-insights rate depth windows)]
    
    ;; Return new session with analysis populated
    (assoc-in session
              [:session/analysis :breathing]
              {:rate-bpm rate
               :depth-score depth
               :rhythm-regularity 0.88
               :fatigue-windows windows
               :insights insights})))

;; ============================================================
;; TESTING HELPERS
;; ============================================================

(comment
  ;; REPL-driven development
  (require '[combatsys.mocks :as mocks])
  
  ;; Generate a mock session
  (def test-session (mocks/mock-breathing-session 60 22))
  
  ;; Analyze it
  (def analyzed (analyze test-session))
  
  ;; Check results
  (get-in analyzed [:session/analysis :breathing :rate-bpm])
  ;; => 22
  
  (get-in analyzed [:session/analysis :breathing :insights])
  ;; => [{:insight/title "Breathing window shortened" ...}]
  )
