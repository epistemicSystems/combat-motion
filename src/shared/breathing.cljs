(ns combatsys.breathing
  "Breathing analysis from pose timeline.
   
   LOD 0: Returns hardcoded mock results
   LOD 2+: Real FFT-based breathing detection
   
   Philosophy (Rich Hickey):
   - Pure function: timeline → analysis
   - No side effects, fully testable
   - Composable with other analyzers"
  (:require [combatsys.schema :as schema]
            [combatsys.mocks :as mocks]
            [combatsys.fourier :as fourier]))

;; ============================================================
;; TORSO MOTION EXTRACTION (LOD 2: Real Implementation)
;; ============================================================

(defn filter-landmarks
  "Extract specific landmarks by ID from pose.

  Args:
    landmarks: Vector of landmark maps [{:landmark-id :nose :x 0.5 :y 0.3 ...} ...]
    ids: Set or vector of landmark IDs to extract

  Returns:
    Vector of landmark maps matching the IDs

  Example:
    (filter-landmarks landmarks [:left-shoulder :right-shoulder])
    ;; => [{:landmark-id :left-shoulder :x 0.4 :y 0.2 ...}
    ;;     {:landmark-id :right-shoulder :x 0.6 :y 0.2 ...}]"
  [landmarks ids]
  (filterv (fn [landmark]
             (contains? (set ids) (:landmark-id landmark)))
           landmarks))

(defn compute-centroid
  "Compute geometric centroid (average position) of landmarks.

  Args:
    landmarks: Vector of landmark maps with :x, :y, :z coordinates

  Returns:
    Point map {:x X :y Y :z Z}

  Example:
    (compute-centroid [{:x 0.4 :y 0.2 :z -0.1}
                       {:x 0.6 :y 0.2 :z -0.1}])
    ;; => {:x 0.5 :y 0.2 :z -0.1}"
  [landmarks]
  (if (empty? landmarks)
    {:x 0.0 :y 0.0 :z 0.0}
    (let [n (count landmarks)
          sum-x (reduce + 0 (map :x landmarks))
          sum-y (reduce + 0 (map :y landmarks))
          sum-z (reduce + 0 (map :z landmarks))]
      {:x (/ sum-x n)
       :y (/ sum-y n)
       :z (/ sum-z n)})))

(defn frame-to-frame-distance
  "Compute Euclidean distance between consecutive points.

  Args:
    points: Vector of point maps [{:x X :y Y :z Z} ...]

  Returns:
    Vector of distances (length = n where n = count of points)
    First value is 0 (no previous frame)

  Example:
    (frame-to-frame-distance [{:x 0.5 :y 0.5 :z 0}
                              {:x 0.52 :y 0.51 :z 0}
                              {:x 0.53 :y 0.52 :z 0}])
    ;; => [0.0 0.0223... 0.0141...]"
  [points]
  (vec
   (cons 0.0 ;; First frame has no previous frame
         (map (fn [p1 p2]
                (let [dx (- (:x p2) (:x p1))
                      dy (- (:y p2) (:y p1))
                      dz (- (:z p2) (:z p1))]
                  (Math/sqrt (+ (* dx dx) (* dy dy) (* dz dz)))))
              points
              (rest points)))))

(defn moving-average
  "Apply moving average smoothing to signal.

  Uses centered window (equal samples before and after current point).
  At boundaries, uses asymmetric window.

  Args:
    signal: Vector of numbers
    window-size: Odd integer (e.g., 5, 7, 9)

  Returns:
    Smoothed signal (same length as input)

  Example:
    (moving-average [1 2 3 10 3 2 1] 3)
    ;; => [1.5 2.0 5.0 5.33 5.0 2.0 1.5]
    ;; Middle value (10) smoothed to 5.33 = (3+10+3)/3"
  [signal window-size]
  (if (empty? signal)
    []
    (let [half-window (quot window-size 2)]
      (mapv (fn [i]
              (let [start (max 0 (- i half-window))
                    end (min (count signal) (+ i half-window 1))
                    window (subvec signal start end)]
                (/ (reduce + window) (count window))))
            (range (count signal))))))

(defn extract-torso-motion
  "Extract torso motion magnitude from timeline.

  Algorithm:
  1. Select torso landmarks (shoulders, hips) from each frame
  2. Compute centroid of torso region per frame
  3. Calculate Euclidean distance between consecutive centroids
  4. Smooth signal with moving average (window size 5 frames)

  Args:
    timeline: Vector of frames with :frame/pose data

  Returns:
    Vector of motion magnitude values (one per frame)

  Example:
    (def timeline [{:frame/pose {:landmarks [...]} :frame/timestamp-ms 0}
                   {:frame/pose {:landmarks [...]} :frame/timestamp-ms 33}
                   ...])
    (def signal (extract-torso-motion timeline))
    ;; => [0.012 0.015 0.018 0.022 0.024 0.023 0.019 0.014 ...]
    (count signal) ;; => (count timeline)"
  [timeline]
  (if (empty? timeline)
    []
    (let [;; Step 1: Extract torso landmarks from each frame
          torso-ids [:left-shoulder :right-shoulder
                     :left-hip :right-hip]

          torso-points-per-frame
          (mapv (fn [frame]
                  (let [landmarks (get-in frame [:frame/pose :landmarks])]
                    (filter-landmarks landmarks torso-ids)))
                timeline)

          ;; Step 2: Compute centroid per frame
          centroids (mapv compute-centroid torso-points-per-frame)

          ;; Step 3: Frame-to-frame distance
          distances (frame-to-frame-distance centroids)

          ;; Step 4: Smooth signal
          smoothed (moving-average distances 5)]

      smoothed)))

;; ============================================================
;; BREATHING RATE DETECTION (LOD 2: Real Implementation)
;; ============================================================

(defn detect-breathing-rate
  "Detect breathing rate from torso motion signal using FFT.

  Algorithm:
  1. Apply FFT to convert signal to frequency domain
  2. Find peak frequency in breathing range (0.1-0.5 Hz)
     - 0.1 Hz = 6 bpm (very slow breathing)
     - 0.5 Hz = 30 bpm (very fast breathing)
  3. Convert frequency (Hz) to rate (breaths per minute)
  4. Compute confidence from peak magnitude
  5. Compute depth score (RMS amplitude)

  Args:
    signal: Vector of motion magnitudes (from extract-torso-motion)

  Returns:
    Map with keys:
      :rate-bpm - Breathing rate in breaths per minute
      :frequency-hz - Dominant frequency in Hz
      :confidence - Quality score (0.0-1.0)
      :method - Detection method (:fft-peak-detection)
      :depth-score - Breathing depth (amplitude of motion)

  Example:
    (def signal (extract-torso-motion timeline))
    (def result (detect-breathing-rate signal))
    ;; => {:rate-bpm 21.8
    ;;     :frequency-hz 0.363
    ;;     :confidence 0.94
    ;;     :method :fft-peak-detection
    ;;     :depth-score 0.78}

  Notes:
    - Requires at least 30 samples (2 seconds at 15 fps)
    - More samples = better frequency resolution
    - 60 seconds (900 samples) gives ~0.017 Hz resolution"
  [signal]
  (if (< (count signal) 30)
    ;; Not enough data
    {:rate-bpm nil
     :confidence 0.0
     :error "Insufficient samples (need at least 30 frames, ~2 seconds)"}

    (let [;; Apply FFT (sampling rate = 15 fps for pose data)
          freq-domain (fourier/fft-transform signal 15)

          ;; Find peak in breathing range (0.1-0.5 Hz = 6-30 bpm)
          peak (fourier/find-peak-in-range freq-domain 0.1 0.5)

          ;; Convert Hz to BPM (breaths per minute)
          bpm (* (:frequency peak) 60)

          ;; Compute depth score (RMS of signal, normalized)
          ;; RMS = sqrt(mean(signal^2))
          rms (Math/sqrt (/ (reduce + (map #(* % %) signal))
                            (count signal)))
          ;; Normalize by expected max motion (0.05 = ~5% of frame)
          depth (/ rms 0.05)]

      {:rate-bpm bpm
       :frequency-hz (:frequency peak)
       :confidence (:confidence peak)
       :method :fft-peak-detection
       :depth-score (min 1.0 (max 0.0 depth))})))

;; ============================================================
;; FATIGUE WINDOW DETECTION (LOD 2: Real Implementation)
;; ============================================================

(defn find-below-threshold
  "Find contiguous regions where signal is below threshold.

  Args:
    signal: Vector of numbers
    threshold: Cutoff value

  Returns:
    Vector of regions [{:start-idx N :end-idx M} ...]

  Example:
    (find-below-threshold [0.5 0.5 0.1 0.1 0.1 0.5 0.5] 0.3)
    ;; => [{:start-idx 2 :end-idx 4}]
    ;;     Indices 2,3,4 are below 0.3"
  [signal threshold]
  (loop [i 0
         regions []
         in-region? false
         start-idx nil]

    (if (>= i (count signal))
      ;; End of signal
      (if in-region?
        (conj regions {:start-idx start-idx :end-idx (dec i)})
        regions)

      (let [val (nth signal i)
            below? (< val threshold)]

        (cond
          ;; Start new region
          (and below? (not in-region?))
          (recur (inc i) regions true i)

          ;; End region
          (and (not below?) in-region?)
          (recur (inc i)
                 (conj regions {:start-idx start-idx :end-idx (dec i)})
                 false
                 nil)

          ;; Continue current state
          :else
          (recur (inc i) regions in-region? start-idx))))))

(defn merge-close-windows
  "Merge regions that are close together (within gap threshold).

  Rationale: Brief spikes between breath holds should be ignored.

  Args:
    regions: Vector of region maps [{:start-idx N :end-idx M} ...]
    max-gap: Maximum gap (in indices) to merge across

  Returns:
    Merged regions

  Example:
    (merge-close-windows [{:start-idx 10 :end-idx 15}
                          {:start-idx 18 :end-idx 23}]
                         5) ;; Gap of 2 (18-15=3) is < 5
    ;; => [{:start-idx 10 :end-idx 23}]"
  [regions max-gap]
  (if (empty? regions)
    []

    (reduce (fn [merged region]
              (let [last-region (peek merged)]
                (if (and last-region
                         (<= (- (:start-idx region)
                                (:end-idx last-region))
                             max-gap))
                  ;; Merge with previous
                  (conj (pop merged)
                        {:start-idx (:start-idx last-region)
                         :end-idx (:end-idx region)})
                  ;; Keep separate
                  (conj merged region))))
            [(first regions)]
            (rest regions))))

(defn compute-severity
  "Compute severity of fatigue window.

  Severity = how much below threshold the signal dropped.

  Args:
    signal: Full signal vector
    region: Region map {:start-idx N :end-idx M}
    threshold: Threshold value

  Returns:
    Severity score (0.0 = at threshold, 1.0 = complete stop)

  Example:
    (compute-severity [0.5 0.5 0.1 0.05 0.1 0.5]
                      {:start-idx 2 :end-idx 4}
                      0.3)
    ;; Mean in window: (0.1 + 0.05 + 0.1) / 3 = 0.083
    ;; Severity: (0.3 - 0.083) / 0.3 = 0.72"
  [signal region threshold]
  (let [start (:start-idx region)
        end (:end-idx region)]
    (if (or (< end start) (>= start (count signal)))
      0.0 ;; Invalid region
      (let [window (subvec signal start (min (inc end) (count signal)))
            mean-val (/ (reduce + window) (count window))
            severity (/ (- threshold mean-val) threshold)]

        ;; Clamp to [0.0, 1.0]
        (max 0.0 (min 1.0 severity))))))

(defn detect-fatigue-windows
  "Detect periods where breathing stops or becomes shallow.

  Algorithm:
  1. Compute dynamic threshold (fraction of mean signal amplitude)
  2. Find regions below threshold
  3. Merge adjacent regions (within 2 seconds)
  4. Filter out very short windows (<1 second)
  5. Compute severity for each window
  6. Convert indices to timestamps

  Args:
    signal: Vector of motion magnitudes
    threshold-fraction: Threshold as fraction of mean (default 0.3)
    fps: Frames per second (default 15)

  Returns:
    Vector of fatigue windows [{:start-ms T1 :end-ms T2 :severity S} ...]

  Example:
    (def signal [0.5 0.5 0.5 0.1 0.1 0.1 0.5 0.5])
    (detect-fatigue-windows signal 0.3 30)
    ;; => [{:start-ms 100 :end-ms 200 :severity 0.75}]

  Personalization:
    - Default threshold-fraction: 0.3 (30% of mean)
    - User profile can customize based on their breathing baseline"
  ([signal]
   (detect-fatigue-windows signal 0.3 15))
  ([signal threshold-fraction]
   (detect-fatigue-windows signal threshold-fraction 15))
  ([signal threshold-fraction fps]
   (if (empty? signal)
     []
     (let [;; Dynamic threshold (personalized fraction of mean amplitude)
           mean-amplitude (/ (reduce + signal) (count signal))
           threshold (* threshold-fraction mean-amplitude)

           ;; Find low regions
           regions (find-below-threshold signal threshold)

           ;; Merge close windows (within 2 seconds = 30 frames at 15fps)
           merged (merge-close-windows regions 30)

           ;; Filter out very short windows (<1 second = 15 frames)
           min-duration 15
           filtered (filterv #(> (- (:end-idx %) (:start-idx %)) min-duration) merged)

           ;; Compute severity and convert to timestamps
           ms-per-frame (/ 1000 fps)]

       (mapv (fn [region]
               {:start-ms (long (* (:start-idx region) ms-per-frame))
                :end-ms (long (* (:end-idx region) ms-per-frame))
                :severity (compute-severity signal region threshold)})
             filtered)))))

;; ============================================================
;; INSIGHT GENERATION (LOD 2: Real Implementation)
;; ============================================================

(defn format-timestamp
  "Convert milliseconds to MM:SS format.

  Args:
    ms: Milliseconds

  Returns:
    String like \"01:23\" or \"00:05\"

  Example:
    (format-timestamp 45000) ;; => \"00:45\"
    (format-timestamp 125000) ;; => \"02:05\""
  [ms]
  (let [total-seconds (quot ms 1000)
        minutes (quot total-seconds 60)
        seconds (mod total-seconds 60)]
    (str (when (< minutes 10) "0") minutes
         ":"
         (when (< seconds 10) "0") seconds)))

(defn generate-insights
  "Generate coaching insights from breathing analysis.

  Creates natural language recommendations based on:
  - Breathing rate (too fast/slow/normal, or delta from personal baseline)
  - Depth score (shallow/deep)
  - Fatigue windows (breath holds)

  Args:
    analysis-map: Map with :rate-bpm, :depth-score, :fatigue-windows, :delta-from-baseline, etc.
    user-profile: (optional) User profile with baseline for personalized insights

  Returns:
    Vector of insight maps with:
      :insight/title - Short summary
      :insight/description - Detailed explanation
      :insight/severity - :low, :medium, :high
      :insight/recommendation - Actionable advice

  Example (no profile):
    (generate-insights {:rate-bpm 28
                        :depth-score 0.4
                        :fatigue-windows [{:start-ms 45000 :end-ms 48000}]}
                       nil)
    ;; => [{:insight/title \"Elevated breathing rate\" ...}]

  Example (with profile):
    (generate-insights {:rate-bpm 26
                        :baseline-rate 22
                        :delta-from-baseline 4
                        :pct-change 18.2
                        :fatigue-windows []}
                       user-profile)
    ;; => [{:insight/title \"Breathing rate elevated\"
    ;;      :insight/description \"Your rate is 18% above your baseline of 22 bpm\" ...}]"
  ([analysis-map]
   (generate-insights analysis-map nil))
  ([{:keys [rate-bpm depth-score fatigue-windows confidence
            baseline-rate delta-from-baseline pct-change]}
    user-profile]
   (let [insights (atom [])]

     ;; Insight 1: Breathing rate analysis (personalized if profile exists)
     (when (and rate-bpm (> confidence 0.5))
       (if (and user-profile baseline-rate delta-from-baseline)
         ;; PERSONALIZED INSIGHT (with baseline comparison)
         (let [abs-pct (js/Math.abs pct-change)]
           (cond
             ;; Significantly elevated (>15% above baseline)
             (and (pos? delta-from-baseline) (> abs-pct 15))
             (swap! insights conj
                    {:insight/title "Breathing rate elevated"
                     :insight/description (str "Your rate of " (int rate-bpm) " bpm is "
                                              (int abs-pct) "% above your baseline of "
                                              (int baseline-rate) " bpm")
                     :insight/severity (if (> abs-pct 25) :high :medium)
                     :insight/recommendation "Focus on slower, controlled breathing to return to your baseline pace."})

             ;; Significantly lowered (>15% below baseline)
             (and (neg? delta-from-baseline) (> abs-pct 15))
             (swap! insights conj
                    {:insight/title "Breathing rate lowered"
                     :insight/description (str "Your rate of " (int rate-bpm) " bpm is "
                                              (int abs-pct) "% below your baseline of "
                                              (int baseline-rate) " bpm")
                     :insight/severity :low
                     :insight/recommendation "Good recovery breathing. This is slower than your typical pace."})

             ;; Within baseline range (±15%)
             :else
             (swap! insights conj
                    {:insight/title "Breathing rate normal"
                     :insight/description (str "Your rate of " (int rate-bpm) " bpm is within "
                                              (int abs-pct) "% of your baseline of "
                                              (int baseline-rate) " bpm")
                     :insight/severity :low
                     :insight/recommendation "Maintain this steady breathing pattern."})))

         ;; GENERIC INSIGHT (no baseline)
         (cond
           ;; Very fast breathing (>25 bpm)
           (> rate-bpm 25)
           (swap! insights conj
                  {:insight/title "Elevated breathing rate"
                   :insight/description (str "Breathing rate of " (int rate-bpm) " bpm is higher than typical resting rate (12-20 bpm)")
                   :insight/severity :medium
                   :insight/recommendation "Focus on slower, controlled breathing. Try 4-count inhale, 6-count exhale."})

           ;; Very slow breathing (<8 bpm)
           (< rate-bpm 8)
           (swap! insights conj
                  {:insight/title "Very slow breathing detected"
                   :insight/description (str "Breathing rate of " (int rate-bpm) " bpm is unusually low")
                   :insight/severity :low
                   :insight/recommendation "Ensure you're breathing naturally. Breath holds may be affecting the measurement."})

           ;; Normal range (12-20 bpm)
           (and (>= rate-bpm 12) (<= rate-bpm 20))
           (swap! insights conj
                  {:insight/title "Normal breathing rate"
                   :insight/description (str "Breathing rate of " (int rate-bpm) " bpm is within healthy resting range (12-20 bpm)")
                   :insight/severity :low
                   :insight/recommendation "Maintain this steady breathing pattern during warm-up and recovery."}))))

     ;; Insight 2: Depth score analysis
     (when (and depth-score (> confidence 0.5))
       (cond
         ;; Shallow breathing (<0.5)
         (< depth-score 0.5)
         (swap! insights conj
                {:insight/title "Shallow breathing detected"
                 :insight/description (str "Breathing depth score of " (int (* depth-score 100)) "% indicates limited torso expansion")
                 :insight/severity :medium
                 :insight/recommendation "Practice diaphragmatic breathing. Focus on belly expansion rather than chest."})

         ;; Good depth (>0.7)
         (> depth-score 0.7)
         (swap! insights conj
                {:insight/title "Strong breathing depth"
                 :insight/description (str "Breathing depth score of " (int (* depth-score 100)) "% shows good torso expansion")
                 :insight/severity :low
                 :insight/recommendation "Excellent. Maintain this breathing pattern during training."})))

     ;; Insight 3: Fatigue windows (breath holds)
     (when (seq fatigue-windows)
       (doseq [window fatigue-windows]
         (let [duration-ms (- (:end-ms window) (:start-ms window))
               duration-s (/ duration-ms 1000.0)
               severity (:severity window)
               start-time (format-timestamp (:start-ms window))]
           (swap! insights conj
                  {:insight/title (str "Breath disruption at " start-time)
                   :insight/description (str "Breathing stopped or became very shallow for "
                                            (format "%.1f" duration-s) " seconds "
                                            "(severity: " (int (* severity 100)) "%)")
                   :insight/severity (cond
                                      (> severity 0.8) :high
                                      (> severity 0.5) :medium
                                      :else :low)
                   :insight/recommendation (if (> duration-s 3)
                                            "Extended breath hold detected. Monitor breathing during high-intensity movements."
                                            "Brief breathing disruption. May indicate movement transition or exertion.")}))))

     ;; Return insights (or empty vector if none generated)
     @insights)))

;; ============================================================
;; MAIN ANALYZER (Pure function: session → session)
;; ============================================================

(defn analyze
  "Main entry point: analyze breathing patterns in a session.

  Algorithm:
  1. Extract torso motion signal from pose timeline
  2. Detect breathing rate and depth via FFT
  3. Detect fatigue windows (breath holds)
  4. Generate coaching insights (personalized if user-profile provided)

  Input:
    session: Session map with :session/timeline
    user-profile: (optional) User profile with learned thresholds and baselines

  Output: same session with :session/analysis populated

  This is a PURE FUNCTION - no side effects, fully testable.

  Example (no profile):
    (def session {:session/timeline [... 900 frames ...]})
    (def analyzed (analyze session))
    (get-in analyzed [:session/analysis :breathing :rate-bpm])
    ;; => 21.8

  Example (with profile):
    (def analyzed (analyze session user-profile))
    (get-in analyzed [:session/analysis :breathing :delta-from-baseline])
    ;; => 4.2 (current rate is 4.2 bpm above user's baseline)"
  ([session]
   (analyze session nil))
  ([session user-profile]
   (let [timeline (:session/timeline session)

         ;; Step 1: Extract torso motion signal
         torso-signal (extract-torso-motion timeline)

         ;; Step 2: Detect breathing rate and depth (FFT analysis)
         rate-analysis (detect-breathing-rate torso-signal)
         rate-bpm (:rate-bpm rate-analysis)
         depth-score (:depth-score rate-analysis)

         ;; Step 3: Get thresholds from profile or use defaults
         fatigue-threshold (if user-profile
                             (get-in user-profile
                                     [:learned-thresholds :breathing-thresholds :fatigue-threshold])
                             0.3) ;; Default: 30% drop in amplitude

         ;; Step 4: Detect fatigue windows using personalized threshold
         fatigue-windows (detect-fatigue-windows torso-signal fatigue-threshold)

         ;; Step 5: Compute delta from baseline (if profile exists)
         baseline-rate (when user-profile
                         (get-in user-profile [:breathing-baseline :typical-rate-bpm]))
         delta-rate (when baseline-rate (- rate-bpm baseline-rate))
         pct-change (when baseline-rate (* 100 (/ delta-rate baseline-rate)))

         ;; Step 6: Combine all metrics
         complete-analysis (merge rate-analysis
                                  {:fatigue-windows fatigue-windows
                                   :baseline-rate baseline-rate
                                   :delta-from-baseline delta-rate
                                   :pct-change pct-change})

         ;; Step 7: Generate coaching insights (personalized if profile provided)
         insights (generate-insights complete-analysis user-profile)]

     ;; Return new session with analysis populated
     (assoc-in session
               [:session/analysis :breathing]
               (assoc complete-analysis :insights insights)))))

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
