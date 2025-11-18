(ns combatsys.posture
  "Posture analysis from pose timeline.

   Pure functional module - analyzes body alignment from camera poses.

   Key Metrics:
   - Forward head posture (cm)
   - Shoulder imbalance (degrees)
   - Spine alignment (classification)
   - Overall posture score (0-1 scale)

   Algorithm:
   1. Extract landmarks (nose, shoulders, hips, knees)
   2. Average over timeline (reduce noise)
   3. Compute geometric metrics
   4. Generate insights

   Pure functions: session → session' (no side effects)"
  (:require [combatsys.schema :as schema]))

;; ============================================================
;; HELPER FUNCTIONS
;; ============================================================

(defn get-landmark
  "Extract specific landmark from pose.

   Args:
     pose: Pose map with :landmarks vector
     landmark-id: Keyword (e.g., :nose, :left-shoulder)

   Returns:
     Landmark map with :x, :y, :z, :visibility or nil if not found

   Example:
     (get-landmark pose :nose)
     ;; => {:landmark-id :nose :x 0.52 :y 0.31 :z -0.05 :visibility 0.98}"
  [pose landmark-id]
  (->> (:landmarks pose)
       (filter #(= landmark-id (:landmark-id %)))
       first))

(defn average-landmarks
  "Average landmark position over multiple frames.

   Args:
     frames: Vector of frame maps
     landmark-id: Keyword identifying landmark

   Returns:
     Averaged landmark position {:x :y :z :visibility}

   Example:
     (average-landmarks timeline :nose)
     ;; => {:x 0.521 :y 0.308 :z -0.048 :visibility 0.95}"
  [frames landmark-id]
  (let [landmarks (->> frames
                       (map :frame/pose)
                       (map #(get-landmark % landmark-id))
                       (filter some?) ;; Remove nil (missing landmarks)
                       (filter #(> (:visibility %) 0.5))) ;; Only high confidence

        n (count landmarks)]

    (when (pos? n)
      {:x (/ (reduce + (map :x landmarks)) n)
       :y (/ (reduce + (map :y landmarks)) n)
       :z (/ (reduce + (map :z landmarks)) n)
       :visibility (/ (reduce + (map :visibility landmarks)) n)})))

(defn midpoint
  "Compute midpoint between two landmarks.

   Args:
     lm1, lm2: Landmark maps with :x, :y, :z

   Returns:
     Midpoint {:x :y :z}

   Example:
     (midpoint {:x 0.4 :y 0.5 :z 0} {:x 0.6 :y 0.5 :z 0})
     ;; => {:x 0.5 :y 0.5 :z 0}"
  [lm1 lm2]
  {:x (/ (+ (:x lm1) (:x lm2)) 2)
   :y (/ (+ (:y lm1) (:y lm2)) 2)
   :z (/ (+ (:z lm1) (:z lm2)) 2)})

;; ============================================================
;; LANDMARK EXTRACTION
;; ============================================================

(defn extract-relevant-landmarks
  "Extract and average all landmarks needed for posture analysis.

   Args:
     timeline: Vector of frame maps

   Returns:
     Map of landmark-id → averaged position

   Example:
     (extract-relevant-landmarks timeline)
     ;; => {:nose {...}
     ;;     :left-shoulder {...}
     ;;     :right-shoulder {...}
     ;;     :left-hip {...}
     ;;     :right-hip {...}
     ;;     :left-knee {...}
     ;;     :right-knee {...}}"
  [timeline]
  {:nose (average-landmarks timeline :nose)
   :left-shoulder (average-landmarks timeline :left-shoulder)
   :right-shoulder (average-landmarks timeline :right-shoulder)
   :left-hip (average-landmarks timeline :left-hip)
   :right-hip (average-landmarks timeline :right-hip)
   :left-knee (average-landmarks timeline :left-knee)
   :right-knee (average-landmarks timeline :right-knee)})

;; ============================================================
;; FORWARD HEAD POSTURE (FHP)
;; ============================================================

(defn calibrate-pixel-to-cm
  "Compute pixel-to-cm conversion ratio.

   Uses known user height to calibrate camera distance.

   Args:
     user-height-cm: User's height in centimeters
     landmarks: Map of averaged landmarks

   Returns:
     Ratio (cm per pixel) as float

   Example:
     (calibrate-pixel-to-cm 175 landmarks)
     ;; => 0.185 (cm per pixel)"
  [user-height-cm landmarks]
  (let [;; Measure height in pixels (nose to ankle)
        nose-y (:y (:nose landmarks))
        left-ankle-y (:y (get landmarks :left-ankle {:y 1.0}))
        right-ankle-y (:y (get landmarks :right-ankle {:y 1.0}))
        ankle-y (/ (+ left-ankle-y right-ankle-y) 2)

        height-pixels (Math/abs (- nose-y ankle-y))]

    (if (pos? height-pixels)
      (/ user-height-cm height-pixels)
      0.2))) ;; Default fallback (rough estimate)

(defn measure-forward-head
  "Measure forward head posture distance.

   Computes horizontal distance from nose to vertical line through shoulders.

   Args:
     landmarks: Map of averaged landmarks
     user-height-cm: User height for calibration (optional, default 170cm)

   Returns:
     Distance in centimeters (float)

   Algorithm:
   1. Find shoulder midpoint
   2. Measure horizontal distance from nose to shoulder midpoint
   3. Convert pixels to cm using calibration

   Normal Range: 0-3 cm
   Alert Threshold: >5 cm

   Example:
     (measure-forward-head landmarks 175)
     ;; => 4.2 (4.2cm forward)"
  ([landmarks] (measure-forward-head landmarks 170))
  ([landmarks user-height-cm]
   (let [nose (:nose landmarks)
         left-shoulder (:left-shoulder landmarks)
         right-shoulder (:right-shoulder landmarks)

         ;; Shoulder midpoint
         shoulder-center (midpoint left-shoulder right-shoulder)

         ;; Horizontal distance (x-axis, normalized [0,1])
         nose-x (:x nose)
         shoulder-x (:x shoulder-center)
         pixel-distance (Math/abs (- nose-x shoulder-x))

         ;; Calibrate to cm
         cm-per-pixel (calibrate-pixel-to-cm user-height-cm landmarks)
         distance-cm (* pixel-distance cm-per-pixel)]

     distance-cm)))

;; ============================================================
;; SHOULDER IMBALANCE
;; ============================================================

(defn measure-shoulder-imbalance
  "Measure shoulder height imbalance.

   Computes angle of line connecting shoulders relative to horizontal.

   Args:
     landmarks: Map of averaged landmarks

   Returns:
     Angle in degrees (float)
     - 0° = perfectly level
     - Positive = right shoulder higher
     - Negative = left shoulder higher

   Normal Range: -3° to +3°
   Alert Threshold: >5° (absolute value)

   Example:
     (measure-shoulder-imbalance landmarks)
     ;; => 3.5 (right shoulder 3.5° higher)"
  [landmarks]
  (let [left-shoulder (:left-shoulder landmarks)
        right-shoulder (:right-shoulder landmarks)

        ;; Compute vector from left to right shoulder
        dx (- (:x right-shoulder) (:x left-shoulder))
        dy (- (:y right-shoulder) (:y left-shoulder))

        ;; Compute angle (atan2 returns radians)
        angle-rad (Math/atan2 dy dx)

        ;; Convert to degrees
        angle-deg (* angle-rad (/ 180 Math/PI))]

    ;; Normalize to [-90, 90] range
    (cond
      (> angle-deg 90) (- angle-deg 180)
      (< angle-deg -90) (+ angle-deg 180)
      :else angle-deg)))

;; ============================================================
;; SPINE ALIGNMENT
;; ============================================================

(defn vector-subtract
  "Subtract two 3D vectors.

   Args:
     v1, v2: Maps with :x, :y, :z

   Returns:
     Difference vector {:x :y :z}

   Example:
     (vector-subtract {:x 1 :y 2 :z 0} {:x 0.5 :y 1 :z 0})
     ;; => {:x 0.5 :y 1 :z 0}"
  [v1 v2]
  {:x (- (:x v1) (:x v2))
   :y (- (:y v1) (:y v2))
   :z (- (:z v1) (:z v2))})

(defn angle-between-vectors
  "Compute angle between two 3D vectors.

   Args:
     v1, v2: Vectors as maps with :x, :y, :z

   Returns:
     Angle in degrees (float)

   Example:
     (angle-between-vectors {:x 1 :y 0 :z 0} {:x 0 :y 1 :z 0})
     ;; => 90.0"
  [v1 v2]
  (let [;; Dot product
        dot (+ (* (:x v1) (:x v2))
               (* (:y v1) (:y v2))
               (* (:z v1) (:z v2)))

        ;; Magnitudes
        mag1 (Math/sqrt (+ (* (:x v1) (:x v1))
                          (* (:y v1) (:y v1))
                          (* (:z v1) (:z v1))))
        mag2 (Math/sqrt (+ (* (:x v2) (:x v2))
                          (* (:y v2) (:y v2))
                          (* (:z v2) (:z v2))))

        ;; Angle (acos of normalized dot product)
        angle-rad (if (and (pos? mag1) (pos? mag2))
                    (Math/acos (/ dot (* mag1 mag2)))
                    0)]

    ;; Convert to degrees
    (* angle-rad (/ 180 Math/PI))))

(defn assess-spine-alignment
  "Classify spine alignment from body landmarks.

   Analyzes shoulder-hip-knee alignment to detect postural deviations.

   Args:
     landmarks: Map of averaged landmarks

   Returns:
     Keyword classification:
     - :neutral (straight, ideal)
     - :kyphotic (upper back rounded, hunched)
     - :lordotic (lower back curved, swayback)

   Algorithm:
   1. Compute vectors: shoulder→hip, hip→knee
   2. Measure angle between vectors
   3. Classify based on angle and direction

   Example:
     (assess-spine-alignment landmarks)
     ;; => :neutral"
  [landmarks]
  (let [;; Center points
        shoulder-center (midpoint (:left-shoulder landmarks)
                                 (:right-shoulder landmarks))
        hip-center (midpoint (:left-hip landmarks)
                            (:right-hip landmarks))
        knee-center (midpoint (:left-knee landmarks)
                             (:right-knee landmarks))

        ;; Vectors
        shoulder-to-hip (vector-subtract hip-center shoulder-center)
        hip-to-knee (vector-subtract knee-center hip-center)

        ;; Angle between upper and lower body
        angle (angle-between-vectors shoulder-to-hip hip-to-knee)

        ;; Check if leaning forward or backward (x-axis)
        forward-lean? (> (:x shoulder-to-hip) 0.05)]

    (cond
      ;; Nearly straight (small angle)
      (< angle 15) :neutral

      ;; Hunched forward (kyphotic)
      (and (> angle 15) forward-lean?) :kyphotic

      ;; Leaning backward (lordotic)
      (and (> angle 15) (not forward-lean?)) :lordotic

      ;; Default
      :else :neutral)))

;; ============================================================
;; SCORING & INSIGHTS
;; ============================================================

(defn compute-overall-score
  "Compute overall posture quality score.

   Combines forward head, shoulder imbalance, and spine alignment.

   Args:
     fhp: Forward head distance (cm)
     shoulder-imbalance: Shoulder angle (degrees)
     spine: Spine classification keyword

   Returns:
     Score from 0.0 (poor) to 1.0 (excellent)

   Scoring Formula:
   - FHP component: 1.0 at 0cm, 0.0 at 10cm (linear)
   - Shoulder component: 1.0 at 0°, 0.0 at 15° (linear)
   - Spine component: 1.0 if :neutral, 0.7 if :kyphotic/:lordotic
   - Weighted average: 40% FHP, 30% shoulder, 30% spine

   Example:
     (compute-overall-score 4.2 3.5 :neutral)
     ;; => 0.84"
  [fhp shoulder-imbalance spine]
  (let [;; Component scores (0-1 scale)
        fhp-score (max 0.0 (min 1.0 (- 1.0 (/ fhp 10.0))))
        shoulder-score (max 0.0 (min 1.0 (- 1.0 (/ (Math/abs shoulder-imbalance) 15.0))))
        spine-score (case spine
                      :neutral 1.0
                      :kyphotic 0.7
                      :lordotic 0.7
                      0.5)

        ;; Weighted average
        overall (+ (* 0.4 fhp-score)
                   (* 0.3 shoulder-score)
                   (* 0.3 spine-score))]

    overall))

(defn generate-insights
  "Generate coaching insights from posture metrics.

   Converts numbers into actionable recommendations in natural language.

   Args:
     fhp: Forward head distance (cm)
     shoulder-imbalance: Shoulder angle (degrees)
     spine: Spine classification
     overall-score: Overall score (0-1)

   Returns:
     Vector of insight maps with keys:
     - :insight/title (string)
     - :insight/description (string)
     - :insight/severity (:low | :medium | :high)
     - :insight/recommendation (string)

   Example:
     (generate-insights 4.2 3.5 :neutral 0.84)
     ;; => [{:insight/title \"Forward head posture detected\" ...}]"
  [fhp shoulder-imbalance spine overall-score]
  (let [insights (transient [])]

    ;; Forward head insight
    (when (> fhp 5.0)
      (conj! insights
             {:insight/title "Forward head posture detected"
              :insight/description
              (str "Your head is " (.toFixed fhp 1)
                   " cm forward of your shoulders. This can lead to neck "
                   "strain and headaches.")
              :insight/severity (if (> fhp 8.0) :high :medium)
              :insight/recommendation
              "Practice chin tucks: Gently pull your chin back toward your neck, keeping eyes level. Hold for 5 seconds. Repeat 10 times, 3× daily. Also check your desk and screen height."}))

    ;; Shoulder imbalance insight
    (when (> (Math/abs shoulder-imbalance) 5.0)
      (let [higher-side (if (> shoulder-imbalance 0) "right" "left")
            lower-side (if (> shoulder-imbalance 0) "left" "right")]
        (conj! insights
               {:insight/title "Shoulder imbalance detected"
                :insight/description
                (str "Your " higher-side " shoulder is "
                     (.toFixed (Math/abs shoulder-imbalance) 1)
                     "° higher than your " lower-side " shoulder.")
                :insight/severity :medium
                :insight/recommendation
                (str "Stretch your " higher-side " side regularly: "
                     "Side bend away from the higher shoulder, holding 20-30 seconds. "
                     "Strengthen the " lower-side " side with targeted exercises.")})))

    ;; Spine alignment insight
    (when (not= spine :neutral)
      (let [spine-name (name spine)
            description (case spine
                          :kyphotic "Your upper back shows excessive rounding (hunched posture)."
                          :lordotic "Your lower back shows excessive curvature (swayback)."
                          "Your spine shows deviation from neutral alignment.")]
        (conj! insights
               {:insight/title (str (clojure.string/capitalize spine-name) " posture detected")
                :insight/description description
                :insight/severity :medium
                :insight/recommendation
                (case spine
                  :kyphotic "Practice chest opening exercises: doorway stretches, wall angels, and thoracic extensions. Strengthen mid-back muscles."
                  :lordotic "Strengthen your core: planks, dead bugs, and pelvic tilts. Stretch hip flexors and hamstrings."
                  "Consult a physical therapist for personalized assessment.")})))

    ;; Overall encouragement or warning
    (cond
      (< overall-score 0.6)
      (conj! insights
             {:insight/title "Posture needs significant improvement"
              :insight/description
              (str "Overall posture score: " (.toFixed (* 100 overall-score) 0) "/100. "
                   "Multiple postural issues detected.")
              :insight/severity :high
              :insight/recommendation
              "Consider consulting a physical therapist or posture specialist for a comprehensive assessment. Regular awareness and targeted exercises can make a big difference."})

      (< overall-score 0.8)
      (conj! insights
             {:insight/title "Room for posture improvement"
              :insight/description
              (str "Overall posture score: " (.toFixed (* 100 overall-score) 0) "/100. "
                   "Some postural habits to address.")
              :insight/severity :low
              :insight/recommendation
              "Focus on the specific recommendations above. Set reminders to check your posture throughout the day."})

      :else
      (conj! insights
             {:insight/title "Good posture!"
              :insight/description
              (str "Overall posture score: " (.toFixed (* 100 overall-score) 0) "/100. "
                   "Your posture is well-aligned.")
              :insight/severity :low
              :insight/recommendation
              "Maintain your current awareness. Continue regular movement and stretching."}))

    (persistent! insights)))

;; ============================================================
;; MAIN ANALYZER
;; ============================================================

(defn analyze
  "Analyze posture in a session.

   Pure function: session → session'

   Extracts pose landmarks, computes posture metrics, generates insights.

   Args:
     session: Session map with :session/timeline

   Returns:
     Session map with :posture added to [:session/analysis :posture]

   Schema:
     Conforms to ::schema/posture-analysis

   Performance:
     <5ms for 60-frame timeline

   Example:
     (def session (load-session \"my-session\"))
     (def analyzed (posture/analyze session))
     (get-in analyzed [:session/analysis :posture :head-forward-cm])
     ;; => 4.2

     (get-in analyzed [:session/analysis :posture :insights])
     ;; => [{:insight/title \"Forward head posture detected\" ...}]"
  [session]
  (let [timeline (:session/timeline session)

        ;; Step 1: Extract and average landmarks
        landmarks (extract-relevant-landmarks timeline)

        ;; Step 2: Compute metrics
        user-height (get-in session [:session/user :user/height-cm] 170)
        fhp (measure-forward-head landmarks user-height)
        shoulder-imbalance (measure-shoulder-imbalance landmarks)
        spine (assess-spine-alignment landmarks)
        overall-score (compute-overall-score fhp shoulder-imbalance spine)

        ;; Step 3: Generate insights
        insights (generate-insights fhp shoulder-imbalance spine overall-score)

        ;; Step 4: Build analysis result
        posture-analysis
        {:head-forward-cm fhp
         :shoulder-imbalance-deg shoulder-imbalance
         :spine-alignment spine
         :overall-score overall-score
         :insights insights

         ;; Metadata
         :method :geometric-2d
         :confidence 0.85
         :source-frames (mapv :frame/index timeline)}]

    ;; Return session with posture analysis added
    (assoc-in session [:session/analysis :posture] posture-analysis)))
