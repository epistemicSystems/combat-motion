(ns combatsys.shared.features
  "Unified feature extraction for all analyzers.

  Philosophy (Rich Hickey):
  - Compute once, use many times
  - Pure functions: timeline → features
  - Cached feature map eliminates redundant computation

  Performance (John Carmack):
  - Before: Each analyzer extracts independently (3x work)
  - After: Extract once, share everywhere (1x work)
  - Expected speedup: 2-3x for multi-analysis

  Usage:
    ;; Old way (redundant)
    (breathing/analyze timeline)  ;; extracts torso motion
    (posture/analyze timeline)    ;; extracts landmarks
    (balance/analyze timeline)    ;; extracts COM

    ;; New way (shared)
    (def features (extract-all-features timeline))
    (breathing/analyze features)
    (posture/analyze features)
    (balance/analyze features)"
  (:require [combatsys.shared.pose :as pose]
            [combatsys.shared.schema :as schema]))

;; ============================================================
;; LANDMARK EXTRACTION
;; ============================================================

(defn extract-landmarks-by-id
  "Extract specific landmarks from all frames.

  Args:
    timeline: Vector of frame maps
    landmark-ids: Vector of landmark ID keywords

  Returns:
    Map of {landmark-id → [positions-per-frame]}

  Example:
    (extract-landmarks-by-id timeline [:nose :left-shoulder])
    => {:nose [{:x 0.5 :y 0.3 ...} {:x 0.51 :y 0.31 ...} ...]
        :left-shoulder [...]}"
  [timeline landmark-ids]
  (let [frames (map :frame/pose timeline)]
    (reduce
     (fn [result landmark-id]
       (assoc result landmark-id
              (mapv #(pose/get-landmark % landmark-id) frames)))
     {}
     landmark-ids)))

(defn average-landmark-positions
  "Average landmark positions over all frames (noise reduction).

  Args:
    landmark-positions: Vector of landmark maps from extract-landmarks-by-id

  Returns:
    Averaged landmark map {:x :y :z :visibility}

  Example:
    (average-landmark-positions [{:landmark/x 0.5 :landmark/y 0.3 ...}
                                 {:landmark/x 0.51 :landmark/y 0.31 ...}])
    => {:x 0.505 :y 0.305 :z ... :visibility ...}"
  [landmark-positions]
  (let [valid-landmarks (filter #(and %
                                      (>= (:landmark/confidence % 0) 0.5))
                                landmark-positions)
        n (count valid-landmarks)]
    (when (pos? n)
      {:x (/ (reduce + (map :landmark/x valid-landmarks)) n)
       :y (/ (reduce + (map :landmark/y valid-landmarks)) n)
       :z (/ (reduce + (map :landmark/z valid-landmarks)) n)
       :visibility (/ (reduce + (map :landmark/confidence valid-landmarks)) n)})))

(defn extract-averaged-landmarks
  "Extract and average key landmarks over timeline.

  Args:
    timeline: Vector of frame maps

  Returns:
    Map of {landmark-id → averaged-position}

  Example:
    (extract-averaged-landmarks timeline)
    => {:nose {:x 0.52 :y 0.31 :z -0.05 :visibility 0.95}
        :left-shoulder {:x 0.42 :y 0.22 ...}
        ...}"
  [timeline]
  (let [key-landmarks [:nose
                       :left-eye :right-eye
                       :left-ear :right-ear
                       :left-shoulder :right-shoulder
                       :left-elbow :right-elbow
                       :left-wrist :right-wrist
                       :left-hip :right-hip
                       :left-knee :right-knee
                       :left-ankle :right-ankle
                       :left-heel :right-heel
                       :left-foot-index :right-foot-index]
        landmark-sequences (extract-landmarks-by-id timeline key-landmarks)]
    (reduce-kv
     (fn [result landmark-id positions]
       (if-let [avg (average-landmark-positions positions)]
         (assoc result landmark-id avg)
         result))
     {}
     landmark-sequences)))

;; ============================================================
;; ANGLE COMPUTATION
;; ============================================================

(defn extract-joint-angles
  "Compute all major joint angles from pose.

  Args:
    averaged-landmarks: Map of {landmark-id → position}

  Returns:
    Map of {angle-name → degrees}

  Example:
    (extract-joint-angles landmarks)
    => {:left-elbow 110.5
        :right-elbow 108.2
        :left-knee 165.3
        ...}"
  [averaged-landmarks]
  (let [compute-angle (fn [id1 id2 id3]
                        (when-let [lm1 (get averaged-landmarks id1)]
                          (when-let [lm2 (get averaged-landmarks id2)]
                            (when-let [lm3 (get averaged-landmarks id3)]
                              ;; Compute angle using vector math
                              (let [v1x (- (:x lm1) (:x lm2))
                                    v1y (- (:y lm1) (:y lm2))
                                    v2x (- (:x lm3) (:x lm2))
                                    v2y (- (:y lm3) (:y lm2))
                                    dot (+ (* v1x v2x) (* v1y v2y))
                                    mag1 (js/Math.sqrt (+ (* v1x v1x) (* v1y v1y)))
                                    mag2 (js/Math.sqrt (+ (* v2x v2x) (* v2y v2y)))]
                                (when (and (> mag1 0) (> mag2 0))
                                  (let [cos-angle (/ dot (* mag1 mag2))
                                        clamped (js/Math.max -1.0 (js/Math.min 1.0 cos-angle))
                                        radians (js/Math.acos clamped)
                                        degrees (* radians (/ 180.0 js/Math.PI))]
                                    degrees)))))))]

    (-> {}
        ;; Upper body angles
        (assoc :left-elbow (compute-angle :left-shoulder :left-elbow :left-wrist))
        (assoc :right-elbow (compute-angle :right-shoulder :right-elbow :right-wrist))
        (assoc :left-shoulder (compute-angle :left-elbow :left-shoulder :left-hip))
        (assoc :right-shoulder (compute-angle :right-elbow :right-shoulder :right-hip))

        ;; Lower body angles
        (assoc :left-hip (compute-angle :left-shoulder :left-hip :left-knee))
        (assoc :right-hip (compute-angle :right-shoulder :right-hip :right-knee))
        (assoc :left-knee (compute-angle :left-hip :left-knee :left-ankle))
        (assoc :right-knee (compute-angle :right-hip :right-knee :right-ankle))

        ;; Remove nil values
        (->> (filter (fn [[_ v]] (some? v)))
             (into {})))))

;; ============================================================
;; VELOCITIES & MOTION
;; ============================================================

(defn compute-centroid
  "Compute geometric centroid of landmark positions.

  Args:
    landmarks: Vector of landmark maps with :x :y :z

  Returns:
    Centroid {:x :y :z}"
  [landmarks]
  (if (empty? landmarks)
    {:x 0.0 :y 0.0 :z 0.0}
    (let [n (count landmarks)]
      {:x (/ (reduce + (map :x landmarks)) n)
       :y (/ (reduce + (map :y landmarks)) n)
       :z (/ (reduce + (map :z landmarks)) n)})))

(defn extract-torso-motion
  "Extract magnitude of torso motion over time (for breathing).

  Computes centroid of shoulders and hips for each frame,
  then calculates frame-to-frame distance.

  Args:
    timeline: Vector of frame maps

  Returns:
    Vector of motion magnitudes (one per frame)"
  [timeline]
  (let [torso-landmark-ids [:left-shoulder :right-shoulder
                            :left-hip :right-hip]
        ;; Extract torso centroids per frame
        centroids
        (mapv
         (fn [frame]
           (let [pose (:frame/pose frame)
                 torso-landmarks (keep #(pose/get-landmark pose %)
                                      torso-landmark-ids)
                 valid-landmarks (filter #(>= (:landmark/confidence % 0) 0.5)
                                        torso-landmarks)]
             (when (seq valid-landmarks)
               (compute-centroid valid-landmarks))))
         timeline)

        ;; Compute frame-to-frame distances
        distances
        (vec
         (cons 0.0 ;; First frame has no previous
               (map (fn [c1 c2]
                      (if (and c1 c2)
                        (let [dx (- (:x c2) (:x c1))
                              dy (- (:y c2) (:y c1))
                              dz (- (:z c2) (:z c1))]
                          (js/Math.sqrt (+ (* dx dx) (* dy dy) (* dz dz))))
                        0.0))
                    centroids
                    (rest centroids))))]
    distances))

(defn extract-velocities
  "Compute per-landmark velocities (frame-to-frame motion).

  Args:
    timeline: Vector of frame maps
    landmark-ids: Vector of landmark IDs to track

  Returns:
    Map of {landmark-id → [velocities-per-frame]}"
  [timeline landmark-ids]
  (let [landmark-sequences (extract-landmarks-by-id timeline landmark-ids)]
    (reduce-kv
     (fn [result landmark-id positions]
       (let [velocities
             (vec
              (cons {:vx 0 :vy 0 :vz 0} ;; First frame
                    (map (fn [p1 p2]
                           (if (and p1 p2)
                             {:vx (- (:landmark/x p2) (:landmark/x p1))
                              :vy (- (:landmark/y p2) (:landmark/y p1))
                              :vz (- (:landmark/z p2) (:landmark/z p1))}
                             {:vx 0 :vy 0 :vz 0}))
                         positions
                         (rest positions))))]
         (assoc result landmark-id velocities)))
     {}
     landmark-sequences)))

;; ============================================================
;; CENTER OF MASS & BALANCE
;; ============================================================

(defn compute-center-of-mass
  "Compute center of mass from averaged landmarks.

  Uses weighted average of body segments based on typical
  anthropometric proportions.

  Args:
    averaged-landmarks: Map of {landmark-id → position}

  Returns:
    COM position {:x :y :z} or nil if insufficient data

  Segment weights (typical adult):
  - Head: 8%
  - Torso: 50%
  - Arms: 10% (5% each)
  - Legs: 32% (16% each)"
  [averaged-landmarks]
  (let [;; Define segment weights
        head-weight 0.08
        torso-weight 0.50
        arm-weight 0.05
        leg-weight 0.16

        ;; Get landmark positions
        nose (get averaged-landmarks :nose)
        left-shoulder (get averaged-landmarks :left-shoulder)
        right-shoulder (get averaged-landmarks :right-shoulder)
        left-hip (get averaged-landmarks :left-hip)
        right-hip (get averaged-landmarks :right-hip)
        left-knee (get averaged-landmarks :left-knee)
        right-knee (get averaged-landmarks :right-knee)

        ;; Compute segment positions
        head-pos nose
        torso-pos (when (and left-shoulder right-shoulder left-hip right-hip)
                    (compute-centroid [left-shoulder right-shoulder left-hip right-hip]))
        left-arm-pos left-shoulder
        right-arm-pos right-shoulder
        left-leg-pos (when (and left-hip left-knee)
                      (compute-centroid [left-hip left-knee]))
        right-leg-pos (when (and right-hip right-knee)
                       (compute-centroid [right-hip right-knee]))

        ;; Collect weighted positions
        weighted-positions
        (filter some?
                [(when head-pos {:pos head-pos :weight head-weight})
                 (when torso-pos {:pos torso-pos :weight torso-weight})
                 (when left-arm-pos {:pos left-arm-pos :weight arm-weight})
                 (when right-arm-pos {:pos right-arm-pos :weight arm-weight})
                 (when left-leg-pos {:pos left-leg-pos :weight leg-weight})
                 (when right-leg-pos {:pos right-leg-pos :weight leg-weight})])]

    (when (seq weighted-positions)
      (let [total-weight (reduce + (map :weight weighted-positions))
            weighted-x (reduce + (map #(* (:x (:pos %)) (:weight %)) weighted-positions))
            weighted-y (reduce + (map #(* (:y (:pos %)) (:weight %)) weighted-positions))
            weighted-z (reduce + (map #(* (:z (:pos %)) (:weight %)) weighted-positions))]
        {:x (/ weighted-x total-weight)
         :y (/ weighted-y total-weight)
         :z (/ weighted-z total-weight)}))))

(defn extract-support-polygon
  "Extract support polygon (foot positions) for balance analysis.

  Args:
    averaged-landmarks: Map of {landmark-id → position}

  Returns:
    Vector of foot positions (polygon vertices)"
  [averaged-landmarks]
  (let [left-heel (get averaged-landmarks :left-heel)
        right-heel (get averaged-landmarks :right-heel)
        left-foot (get averaged-landmarks :left-foot-index)
        right-foot (get averaged-landmarks :right-foot-index)]
    (filterv some? [left-heel right-heel left-foot right-foot])))

;; ============================================================
;; HEAD-SHOULDER ALIGNMENT (for posture)
;; ============================================================

(defn extract-head-shoulder-alignment
  "Extract head and shoulder alignment data for posture analysis.

  Args:
    averaged-landmarks: Map of {landmark-id → position}

  Returns:
    Map with alignment metrics"
  [averaged-landmarks]
  (let [nose (get averaged-landmarks :nose)
        left-shoulder (get averaged-landmarks :left-shoulder)
        right-shoulder (get averaged-landmarks :right-shoulder)]
    (when (and nose left-shoulder right-shoulder)
      (let [shoulder-midpoint (compute-centroid [left-shoulder right-shoulder])
            ;; Forward head: horizontal distance from nose to shoulder line
            forward-distance (- (:z nose) (:z shoulder-midpoint))
            ;; Shoulder imbalance: height difference
            shoulder-imbalance (- (:y left-shoulder) (:y right-shoulder))]
        {:nose nose
         :shoulder-midpoint shoulder-midpoint
         :forward-head-distance forward-distance
         :shoulder-height-diff shoulder-imbalance}))))

;; ============================================================
;; MAIN FEATURE EXTRACTION
;; ============================================================

(defn extract-all-features
  "Extract all features needed by any analyzer.

  This is the MAIN entry point for feature extraction.
  Call once, reuse for all analyzers.

  Args:
    timeline: Vector of frame maps

  Returns:
    Comprehensive feature map:
    {:averaged-landmarks {landmark-id → position}
     :angles {angle-name → degrees}
     :velocities {landmark-id → [velocities-per-frame]}
     :torso-motion [motion-magnitudes-per-frame]
     :center-of-mass {:x :y :z}
     :support-polygon [foot-positions]
     :head-shoulder-alignment {...}
     :timeline timeline}  ;; Original timeline for reference

  Example:
    (def features (extract-all-features timeline))
    (breathing/analyze features)
    (posture/analyze features)
    (balance/analyze features)"
  [timeline]
  (when (seq timeline)
    (let [;; Extract averaged landmarks (most expensive operation)
          averaged-landmarks (extract-averaged-landmarks timeline)

          ;; All derived features (cheap once landmarks are extracted)
          angles (extract-joint-angles averaged-landmarks)
          velocities (extract-velocities timeline
                                        [:nose :left-shoulder :right-shoulder
                                         :left-hip :right-hip :left-knee :right-knee])
          torso-motion (extract-torso-motion timeline)
          com (compute-center-of-mass averaged-landmarks)
          support-polygon (extract-support-polygon averaged-landmarks)
          head-shoulder (extract-head-shoulder-alignment averaged-landmarks)]

      {:averaged-landmarks averaged-landmarks
       :angles angles
       :velocities velocities
       :torso-motion torso-motion
       :center-of-mass com
       :support-polygon support-polygon
       :head-shoulder-alignment head-shoulder
       :timeline timeline})))

;; ============================================================
;; INCREMENTAL FEATURE UPDATE (for real-time path)
;; ============================================================

(defn update-features-incremental
  "Update features with new frame (for real-time analysis).

  Instead of recomputing all features from scratch,
  update incrementally with new frame.

  Args:
    previous-features: Feature map from previous frame
    new-frame: New frame to incorporate

  Returns:
    Updated feature map

  Note: For now, this is a placeholder. Real-time incremental
  updates can be added in LOD 8+ if needed."
  [previous-features new-frame]
  ;; TODO: Implement incremental update for real-time path
  ;; For now, just return previous features
  ;; This is OK because real-time path can work with slightly stale features
  previous-features)

;; ============================================================
;; FEATURE VALIDATION
;; ============================================================

(defn validate-features
  "Validate that extracted features are reasonable.

  Args:
    features: Feature map from extract-all-features

  Returns:
    {:valid? boolean
     :warnings [warning-strings]}"
  [features]
  (let [warnings (atom [])]

    ;; Check landmark count
    (when (< (count (:averaged-landmarks features)) 5)
      (swap! warnings conj "Very few landmarks detected (< 5)"))

    ;; Check angles are in reasonable range
    (doseq [[angle-name degrees] (:angles features)]
      (when (or (< degrees 0) (> degrees 180))
        (swap! warnings conj (str "Angle " angle-name " is out of range: " degrees "°"))))

    ;; Check COM is within reasonable bounds
    (when-let [com (:center-of-mass features)]
      (when (or (< (:y com) 0) (> (:y com) 1))
        (swap! warnings conj "Center of mass Y coordinate out of bounds")))

    {:valid? (empty? @warnings)
     :warnings @warnings}))

;; ============================================================
;; EXPORTS
;; ============================================================

(def ^:export extractFeatures extract-all-features)
(def ^:export validateFeatures validate-features)
