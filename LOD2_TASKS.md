# LOD 2: BREATHING ANALYSIS - TASK BREAKDOWN
**Sprint Duration**: Days 5-7 (Estimated: 24 hours total)
**Goal**: Replace breathing.cljs stubs with real FFT-based breathing analysis
**Status**: Ready to Begin
**Date**: 2025-11-18

---

## 📋 OVERVIEW

### The Three Core Tasks

```
Task 3.1: Torso Motion Extraction    [8 hours] → Signal generation
                ↓
Task 3.2: FFT & Breathing Rate       [10 hours] → Rate detection
                ↓
Task 3.3: Fatigue Window Detection   [6 hours] → Pattern analysis
```

**Total Estimated Time**: 24 hours
**Target Completion**: Day 7

---

## 🎯 TASK 3.1: TORSO MOTION EXTRACTION

**File**: `src/shared/breathing.cljs`
**Estimated Time**: 8 hours
**Priority**: 🔴 Critical (blocks all other tasks)
**Status**: Not Started

### Objective

Replace the stub `extract-torso-motion` function with real implementation that:
1. Extracts torso landmarks from each frame
2. Computes centroid of torso region
3. Calculates frame-to-frame motion magnitude
4. Smooths the signal to reduce noise
5. Returns clean time-series data

### Current Stub Code

```clojure
(defn extract-torso-motion [timeline]
  ;; Stub: return fake oscillating signal
  (mapv (fn [frame]
          (let [t (:frame/timestamp-ms frame)
                phase (mod (/ t 3000) 1.0)]
            {:timestamp-ms t
             :torso-motion (Math/sin (* Math/PI 2 phase))}))
        timeline))
```

### Target Implementation

```clojure
(defn extract-torso-motion
  "Extract torso motion magnitude from timeline.

  Algorithm:
  1. Select torso landmarks (shoulders, hips) from each frame
  2. Compute centroid of torso region per frame
  3. Calculate Euclidean distance between consecutive centroids
  4. Smooth signal with moving average (window size 5-7 frames)

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

    smoothed))
```

### Required Helper Functions

#### 3.1.1: `filter-landmarks`

```clojure
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
```

#### 3.1.2: `compute-centroid`

```clojure
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

  (let [n (count landmarks)
        sum-x (reduce + 0 (map :x landmarks))
        sum-y (reduce + 0 (map :y landmarks))
        sum-z (reduce + 0 (map :z landmarks))]

    {:x (/ sum-x n)
     :y (/ sum-y n)
     :z (/ sum-z n)}))
```

#### 3.1.3: `frame-to-frame-distance`

```clojure
(defn frame-to-frame-distance
  "Compute Euclidean distance between consecutive points.

  Args:
    points: Vector of point maps [{:x X :y Y :z Z} ...]

  Returns:
    Vector of distances (length = n-1 where n = count of points)
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
```

#### 3.1.4: `moving-average`

```clojure
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

  (let [half-window (quot window-size 2)]
    (mapv (fn [i]
            (let [start (max 0 (- i half-window))
                  end (min (count signal) (+ i half-window 1))
                  window (subvec signal start end)]
              (/ (reduce + window) (count window))))
          (range (count signal)))))
```

### Testing Strategy

#### Unit Tests

```clojure
(ns combatsys.breathing-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [combatsys.breathing :as breathing]
            [combatsys.mocks :as mocks]))

(deftest test-filter-landmarks
  (let [landmarks [{:landmark-id :nose :x 0.5 :y 0.3}
                   {:landmark-id :left-shoulder :x 0.4 :y 0.2}
                   {:landmark-id :right-shoulder :x 0.6 :y 0.2}]
        result (breathing/filter-landmarks landmarks [:left-shoulder :right-shoulder])]
    (is (= 2 (count result)))
    (is (= :left-shoulder (:landmark-id (first result))))))

(deftest test-compute-centroid
  (let [points [{:x 0.0 :y 0.0 :z 0.0}
                {:x 1.0 :y 1.0 :z 1.0}]
        centroid (breathing/compute-centroid points)]
    (is (= 0.5 (:x centroid)))
    (is (= 0.5 (:y centroid)))
    (is (= 0.5 (:z centroid)))))

(deftest test-moving-average
  (let [signal [1 2 3 10 3 2 1]
        smoothed (breathing/moving-average signal 3)]
    (is (= 7 (count smoothed)))
    ;; Middle peak should be smoothed down
    (is (< (nth smoothed 3) 10))
    (is (> (nth smoothed 3) 3))))

(deftest test-extract-torso-motion-length
  (let [timeline (mocks/mock-timeline 90)
        signal (breathing/extract-torso-motion timeline)]
    ;; Signal length should match timeline length
    (is (= 90 (count signal)))
    ;; All values should be numbers
    (is (every? number? signal))
    ;; All values should be non-negative (distance)
    (is (every? #(>= % 0) signal))))
```

#### REPL Verification

```clojure
(require '[combatsys.breathing :as breathing])
(require '[combatsys.mocks :as mocks])

;; Generate mock session
(def session (mocks/mock-breathing-session 60 22))
(def timeline (:session/timeline session))

;; Extract torso motion
(def signal (breathing/extract-torso-motion timeline))

;; Verify signal properties
(count signal)
;; => 900 (60s @ 15fps)

(take 10 signal)
;; => (0.012 0.015 0.018 0.022 0.024 0.023 0.019 0.014 0.011 0.010)

;; Check signal is periodic (visual inspection)
(partition 45 signal) ;; 45 frames ~ 3 seconds ~ ~7 breaths at 22bpm
;; Should see ~7 peaks
```

### Acceptance Criteria

- [ ] All helper functions implemented and documented
- [ ] `extract-torso-motion` replaces stub
- [ ] Returns vector of numbers (motion magnitudes)
- [ ] Length matches input timeline length
- [ ] All values are non-negative
- [ ] Signal shows periodicity (visual inspection)
- [ ] Unit tests pass
- [ ] REPL verification succeeds
- [ ] Code compiles without warnings

---

## 🎯 TASK 3.2: FFT & BREATHING RATE DETECTION

**File**: `src/shared/breathing.cljs`, `src/shared/fourier.cljs` (new)
**Estimated Time**: 10 hours
**Priority**: 🔴 Critical
**Status**: Blocked by Task 3.1

### Objective

Implement FFT-based breathing rate detection:
1. Install and wrap FFT library (fft-js)
2. Apply FFT to torso motion signal
3. Find dominant frequency in breathing range (0.1-0.5 Hz)
4. Convert frequency to breaths per minute (BPM)
5. Compute confidence score
6. Return structured analysis

### Prerequisites

```bash
# Install fft-js library
npm install fft-js --save
```

Add to `shadow-cljs.edn` `:dependencies`:
```clojure
["fft-js" "0.0.12"]
```

### Target Implementation

#### 3.2.1: Create `fourier.cljs` (New File)

```clojure
(ns combatsys.fourier
  "FFT utilities for frequency analysis.

  Wraps fft-js JavaScript library for use in ClojureScript."
  (:require ["fft-js" :as fft-js]))

(defn fft-transform
  "Apply Fast Fourier Transform to real-valued signal.

  Converts time-domain signal to frequency domain.
  Uses Cooley-Tukey FFT algorithm (O(n log n)).

  Args:
    signal: Vector of real numbers (time-domain samples)

  Returns:
    Vector of [magnitude frequency] pairs, sorted by frequency
    Only returns positive frequencies (0 to Nyquist)

  Example:
    (def signal [0 1 0 -1 0 1 0 -1]) ;; 1 Hz sine at 4 Hz sampling
    (def freqs (fft-transform signal))
    ;; => [[0.0 0.0] [4.0 1.0] [0.0 2.0] ...]
    ;;     ^magnitude at 1 Hz (second element)

  Notes:
    - Signal length should be power of 2 for efficiency
    - Pads signal with zeros if not power of 2
    - Sampling rate assumed to be 30 fps (video frame rate)"
  [signal]

  (let [;; Pad to power of 2
        n (count signal)
        next-pow2 (Math/pow 2 (Math/ceil (/ (Math/log n) (Math/log 2))))
        padded (concat signal (repeat (- next-pow2 n) 0))

        ;; Convert to JS array
        js-signal (clj->js padded)

        ;; Compute FFT (returns array of [real, imag] pairs)
        phasors (fft-js/fft js-signal)

        ;; Compute magnitudes
        magnitudes (map (fn [[real imag]]
                          (Math/sqrt (+ (* real real) (* imag imag))))
                        (js->clj phasors))

        ;; Compute frequencies (assuming 30 fps)
        sampling-rate 30 ;; frames per second
        freq-resolution (/ sampling-rate next-pow2)
        freqs (map #(* % freq-resolution) (range (count magnitudes)))

        ;; Return [magnitude freq] pairs (only positive frequencies)
        result (mapv vector magnitudes freqs)]

    ;; Only return first half (positive frequencies)
    (subvec result 0 (quot (count result) 2))))

(defn find-peak-in-range
  "Find frequency with maximum magnitude in given range.

  Args:
    freq-domain: Vector of [magnitude frequency] pairs (from fft-transform)
    freq-min: Minimum frequency (Hz)
    freq-max: Maximum frequency (Hz)

  Returns:
    Map {:frequency Hz :magnitude M :confidence C}
    Confidence = ratio of peak magnitude to mean magnitude

  Example:
    (def freqs (fft-transform breathing-signal))
    (def peak (find-peak-in-range freqs 0.1 0.5))
    ;; => {:frequency 0.367 :magnitude 12.5 :confidence 0.94}"
  [freq-domain freq-min freq-max]

  (let [;; Filter to range
        in-range (filterv (fn [[mag freq]]
                            (and (>= freq freq-min) (<= freq freq-max)))
                          freq-domain)

        ;; Find peak
        peak (apply max-key first in-range)
        peak-mag (first peak)
        peak-freq (second peak)

        ;; Compute confidence (peak vs. mean)
        mean-mag (/ (reduce + (map first in-range)) (count in-range))
        confidence (min 1.0 (/ peak-mag (+ mean-mag 0.001)))] ;; Avoid div by zero

    {:frequency peak-freq
     :magnitude peak-mag
     :confidence confidence}))
```

#### 3.2.2: Update `breathing.cljs`

```clojure
(ns combatsys.breathing
  (:require [combatsys.schema :as schema]
            [combatsys.fourier :as fourier]))

(defn detect-breathing-rate
  "Detect breathing rate from torso motion signal using FFT.

  Algorithm:
  1. Apply FFT to convert signal to frequency domain
  2. Find peak frequency in breathing range (0.1-0.5 Hz)
     - 0.1 Hz = 6 bpm (very slow breathing)
     - 0.5 Hz = 30 bpm (very fast breathing)
  3. Convert frequency (Hz) to rate (breaths per minute)
  4. Compute confidence from peak magnitude

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
     :error "Insufficient samples (need 30+)"}

    (let [;; Apply FFT
          freq-domain (fourier/fft-transform signal)

          ;; Find peak in breathing range
          peak (fourier/find-peak-in-range freq-domain 0.1 0.5)

          ;; Convert Hz to BPM (breaths per minute)
          bpm (* (:frequency peak) 60)

          ;; Compute depth score (RMS of signal)
          depth (/ (Math/sqrt (/ (reduce + (map #(* % %) signal))
                                 (count signal)))
                   ;; Normalize by max expected motion
                   0.05)]

      {:rate-bpm bpm
       :frequency-hz (:frequency peak)
       :confidence (:confidence peak)
       :method :fft-peak-detection
       :depth-score (min 1.0 depth)})))
```

### Testing Strategy

#### Unit Tests

```clojure
(deftest test-fft-transform
  (let [;; Generate synthetic sine wave at 1 Hz
        ;; Sampling rate: 30 Hz, duration: 2 seconds
        signal (mapv #(Math/sin (* 2 Math/PI (/ % 30))) (range 60))
        freq-domain (fourier/fft-transform signal)]

    ;; Should have peak near 1 Hz
    (let [peak (fourier/find-peak-in-range freq-domain 0.5 1.5)]
      (is (< 0.9 (:frequency peak) 1.1)))))

(deftest test-breathing-rate-synthetic
  ;; Generate synthetic breathing at 20 bpm (0.333 Hz)
  (let [duration-s 60
        fps 15
        n-samples (* duration-s fps)
        breathing-freq-hz (/ 20 60.0) ;; 20 bpm = 0.333 Hz

        ;; Sine wave
        signal (mapv #(Math/sin (* 2 Math/PI breathing-freq-hz (/ % fps)))
                     (range n-samples))

        result (breathing/detect-breathing-rate signal)
        detected-bpm (:rate-bpm result)]

    ;; Allow ±2 bpm error
    (is (< 18 detected-bpm 22))
    (is (> (:confidence result) 0.7))))

(deftest test-breathing-rate-real-session
  ;; Use mock session with known breathing pattern
  (let [session (mocks/mock-breathing-session 60 22)
        timeline (:session/timeline session)
        signal (breathing/extract-torso-motion timeline)
        result (breathing/detect-breathing-rate signal)]

    ;; Should detect close to 22 bpm
    (is (number? (:rate-bpm result)))
    (is (< 18 (:rate-bpm result) 26))))
```

#### Manual Validation Test

```clojure
;; REPL: Validate with real recording
(require '[combatsys.breathing :as breathing])
(require '[combatsys.renderer.files :as files])

;; 1. User records 60s session, manually counts breaths
;;    Manual count: 18 breaths in 60s = 18 bpm

;; 2. Load session
(def session (files/load-session "2025-11-18_breathing-test"))

;; 3. Analyze
(def signal (breathing/extract-torso-motion (:session/timeline session)))
(def result (breathing/detect-breathing-rate signal))

;; 4. Compare
(:rate-bpm result)
;; => ~17-19 (within ±2 bpm of 18)

(:confidence result)
;; => ~0.85+ (high confidence)
```

### Acceptance Criteria

- [ ] `fft-js` installed and imported successfully
- [ ] `fourier.cljs` created with FFT wrapper
- [ ] `fft-transform` function works (tested with synthetic sine)
- [ ] `find-peak-in-range` finds correct peak
- [ ] `detect-breathing-rate` replaces stub
- [ ] Returns structured map with all required keys
- [ ] Synthetic signal test: detects 20 bpm within ±2 bpm
- [ ] Real session test: detects rate within ±2 bpm of manual count
- [ ] Unit tests pass
- [ ] Code compiles without warnings

---

## 🎯 TASK 3.3: FATIGUE WINDOW DETECTION & INSIGHTS

**File**: `src/shared/breathing.cljs`
**Estimated Time**: 6 hours
**Priority**: 🟡 Important
**Status**: Blocked by Task 3.1

### Objective

Detect periods where breathing stops or becomes shallow:
1. Identify regions where signal amplitude drops below threshold
2. Merge adjacent low-amplitude regions
3. Compute severity (how much below threshold)
4. Generate coaching insights in natural language

### Target Implementation

#### 3.3.1: `find-below-threshold`

```clojure
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
```

#### 3.3.2: `merge-close-windows`

```clojure
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
```

#### 3.3.3: `compute-severity`

```clojure
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

  (let [window (subvec signal (:start-idx region) (inc (:end-idx region)))
        mean-val (/ (reduce + window) (count window))
        severity (/ (- threshold mean-val) threshold)]

    ;; Clamp to [0.0, 1.0]
    (max 0.0 (min 1.0 severity))))
```

#### 3.3.4: `detect-fatigue-windows`

```clojure
(defn detect-fatigue-windows
  "Detect periods where breathing stops or becomes shallow.

  Algorithm:
  1. Compute dynamic threshold (30% of mean signal amplitude)
  2. Find regions below threshold
  3. Merge adjacent regions (within 2 seconds)
  4. Compute severity for each window
  5. Convert indices to timestamps

  Args:
    signal: Vector of motion magnitudes
    fps: Frames per second (default 15)

  Returns:
    Vector of fatigue windows [{:start-ms T1 :end-ms T2 :severity S} ...]

  Example:
    (def signal [0.5 0.5 0.5 0.1 0.1 0.1 0.5 0.5])
    (detect-fatigue-windows signal 30)
    ;; => [{:start-ms 100 :end-ms 200 :severity 0.75}]"
  [signal & {:keys [fps] :or {fps 15}}]

  (let [;; Dynamic threshold (30% of mean)
        mean-amplitude (/ (reduce + signal) (count signal))
        threshold (* 0.3 mean-amplitude)

        ;; Find low regions
        regions (find-below-threshold signal threshold)

        ;; Merge close windows (within 2 seconds = 30 frames at 15fps)
        merged (merge-close-windows regions 30)

        ;; Filter out very short windows (<1 second = 15 frames)
        filtered (filterv #(> (- (:end-idx %) (:start-idx %)) 15) merged)

        ;; Compute severity and convert to timestamps
        ms-per-frame (/ 1000 fps)]

    (mapv (fn [region]
            {:start-ms (* (:start-idx region) ms-per-frame)
             :end-ms (* (:end-idx region) ms-per-frame)
             :severity (compute-severity signal region threshold)})
          filtered)))
```

#### 3.3.5: `generate-insights`

```clojure
(defn generate-insights
  "Generate coaching insights from breathing analysis.

  Creates natural language recommendations based on:
  - Breathing rate (too fast/slow/normal)
  - Depth score (shallow/deep)
  - Fatigue windows (breath holds)

  Args:
    analysis: Breathing analysis map

  Returns:
    Vector of insight maps

  Example:
    (generate-insights {:rate-bpm 28 :depth-score 0.45 :fatigue-windows [...]})
    ;; => [{:title \"Breathing rate elevated\"
    ;;      :description \"Your breathing rate (28 bpm) is higher than optimal\"
    ;;      :recommendation \"Practice slow nasal breathing (12-15 bpm)\"}]"
  [analysis]

  (let [rate (:rate-bpm analysis)
        depth (:depth-score analysis)
        windows (:fatigue-windows analysis)
        insights []]

    (cond-> insights

      ;; Rate too high (>25 bpm)
      (and rate (> rate 25))
      (conj {:title "Breathing rate elevated"
             :description (str "Your breathing rate (" (int rate) " bpm) is higher than optimal (12-20 bpm)")
             :severity :medium
             :recommendation "Practice slow nasal breathing during warm-up"})

      ;; Rate too low (<8 bpm)
      (and rate (< rate 8))
      (conj {:title "Breathing rate very low"
             :description (str "Your breathing rate (" (int rate) " bpm) is unusually low")
             :severity :low
             :recommendation "Ensure you're breathing naturally, not holding breath"})

      ;; Shallow breathing
      (< depth 0.5)
      (conj {:title "Shallow breathing detected"
             :description (str "Your breathing depth is low (" (int (* depth 100)) "%)")
             :severity :medium
             :recommendation "Practice diaphragmatic breathing for deeper breaths"})

      ;; Fatigue windows present
      (seq windows)
      (into (mapv (fn [window]
                    {:title "Breathing disruption"
                     :description (str "Breathing stopped or became shallow at "
                                       (format-timestamp (:start-ms window)))
                     :severity (if (> (:severity window) 0.8) :high :medium)
                     :recommendation "Monitor breathing during high-intensity phases"
                     :timestamp-ms (:start-ms window)})
                  windows)))))

(defn format-timestamp
  "Convert milliseconds to MM:SS format."
  [ms]
  (let [seconds (quot ms 1000)
        mins (quot seconds 60)
        secs (mod seconds 60)]
    (str mins ":" (if (< secs 10) "0" "") secs)))
```

### Testing Strategy

```clojure
(deftest test-find-below-threshold
  (let [signal [0.5 0.5 0.1 0.1 0.1 0.5 0.5]
        regions (breathing/find-below-threshold signal 0.3)]
    (is (= 1 (count regions)))
    (is (= 2 (:start-idx (first regions))))
    (is (= 4 (:end-idx (first regions))))))

(deftest test-merge-close-windows
  (let [regions [{:start-idx 10 :end-idx 15}
                 {:start-idx 18 :end-idx 23}]
        merged (breathing/merge-close-windows regions 5)]
    (is (= 1 (count merged)))
    (is (= 10 (:start-idx (first merged))))
    (is (= 23 (:end-idx (first merged))))))

(deftest test-compute-severity
  (let [signal [0.5 0.5 0.1 0.05 0.1 0.5]
        region {:start-idx 2 :end-idx 4}
        severity (breathing/compute-severity signal region 0.3)]
    (is (> severity 0.5))
    (is (< severity 1.0))))

(deftest test-detect-fatigue-windows
  (let [;; Signal with intentional breath hold
        signal (vec (concat (repeat 50 0.5) ;; Normal
                            (repeat 20 0.05) ;; Hold
                            (repeat 50 0.5))) ;; Resume
        windows (breathing/detect-fatigue-windows signal)]
    (is (= 1 (count windows)))
    (is (> (:severity (first windows)) 0.7))))

(deftest test-generate-insights
  (let [analysis {:rate-bpm 28
                  :depth-score 0.45
                  :fatigue-windows [{:start-ms 30000 :end-ms 33000 :severity 0.85}]}
        insights (breathing/generate-insights analysis)]
    ;; Should generate insights for high rate, shallow depth, and fatigue
    (is (>= (count insights) 3))))
```

### Acceptance Criteria

- [ ] All helper functions implemented
- [ ] `detect-fatigue-windows` replaces stub
- [ ] `generate-insights` creates natural language
- [ ] Unit tests pass (synthetic signals)
- [ ] Manual test: detects intentional 5s breath hold
- [ ] Insights are actionable and accurate
- [ ] Code compiles without warnings

---

## 🔄 INTEGRATION & END-TO-END

### Task 3.4: Wire Up Analysis

**Estimated Time**: 2 hours
**Priority**: 🔴 Critical

#### Update `analyze` Function

```clojure
(defn analyze
  "Main entry point: analyze breathing patterns in a session.

  Pure function: session → session with analysis populated."
  [session]

  (let [timeline (:session/timeline session)

        ;; Step 1: Extract torso motion (Task 3.1)
        signal (extract-torso-motion timeline)

        ;; Step 2: Detect rate and depth (Task 3.2)
        rate-analysis (detect-breathing-rate signal)

        ;; Step 3: Detect fatigue windows (Task 3.3)
        windows (detect-fatigue-windows signal)

        ;; Step 4: Generate insights (Task 3.3)
        insights (generate-insights (assoc rate-analysis
                                           :fatigue-windows windows))

        ;; Assemble complete analysis
        breathing-analysis (merge rate-analysis
                                  {:fatigue-windows windows
                                   :insights insights
                                   :rhythm-regularity 0.88 ;; TODO: Implement in future
                                   })]

    ;; Return session with analysis populated
    (assoc-in session [:session/analysis :breathing] breathing-analysis)))
```

#### Add Re-frame Event

```clojure
;; src/renderer/state.cljs

(rf/reg-event-fx
 :session/analyze-breathing
 (fn [{:keys [db]} [_ session-id]]
   (let [session (get-in db [:sessions session-id])
         analyzed (breathing/analyze session)]
     {:db (assoc-in db [:sessions session-id] analyzed)
      :dispatch [:ui/show-notification "Breathing analysis complete"]})))
```

#### Add UI Button

```clojure
;; src/renderer/views.cljs

(defn session-controls []
  (let [session @(rf/subscribe [:current-session])
        has-analysis? (get-in session [:session/analysis :breathing])]
    [:div.session-controls
     [:button.analyze-btn
      {:on-click #(rf/dispatch [:session/analyze-breathing (:session/id session)])}
      "🔬 Analyze Breathing"]

     (when has-analysis?
       [:div.analysis-results
        (let [analysis (get-in session [:session/analysis :breathing])]
          [:div
           [:p "Rate: " (:rate-bpm analysis) " bpm"]
           [:p "Depth: " (int (* 100 (:depth-score analysis))) "%"]
           [:p "Confidence: " (int (* 100 (:confidence analysis))) "%"]

           (when (seq (:fatigue-windows analysis))
             [:div.fatigue-windows
              [:h4 "⚠️ Fatigue Windows"]
              (for [window (:fatigue-windows analysis)]
                ^{:key (:start-ms window)}
                [:p (format-timestamp (:start-ms window))
                 " - Severity: " (int (* 100 (:severity window))) "%"])])

           (when (seq (:insights analysis))
             [:div.insights
              [:h4 "💡 Insights"]
              (for [insight (:insights analysis)]
                ^{:key (:title insight)}
                [:div.insight
                 [:h5 (:title insight)]
                 [:p (:description insight)]
                 [:p.recommendation "→ " (:recommendation insight)]])])])])]))
```

### End-to-End Test

```clojure
(deftest test-full-pipeline
  ;; 1. Create mock session
  (let [session (mocks/mock-breathing-session 60 22)]

    ;; 2. Analyze
    (let [analyzed (breathing/analyze session)
          analysis (get-in analyzed [:session/analysis :breathing])]

      ;; 3. Verify all components present
      (is (number? (:rate-bpm analysis)))
      (is (number? (:depth-score analysis)))
      (is (number? (:confidence analysis)))
      (is (vector? (:fatigue-windows analysis)))
      (is (vector? (:insights analysis)))

      ;; 4. Verify rate is reasonable
      (is (< 18 (:rate-bpm analysis) 26))

      ;; 5. Verify insights generated
      (is (pos? (count (:insights analysis)))))))
```

---

## ✅ COMPLETION CHECKLIST

### Task 3.1: Torso Motion Extraction
- [ ] `filter-landmarks` implemented
- [ ] `compute-centroid` implemented
- [ ] `frame-to-frame-distance` implemented
- [ ] `moving-average` implemented
- [ ] `extract-torso-motion` replaces stub
- [ ] Unit tests pass
- [ ] REPL verification successful
- [ ] Signal shows periodicity

### Task 3.2: FFT & Breathing Rate
- [ ] `fft-js` installed
- [ ] `fourier.cljs` created
- [ ] `fft-transform` implemented
- [ ] `find-peak-in-range` implemented
- [ ] `detect-breathing-rate` replaces stub
- [ ] Synthetic signal test passes (20 bpm ± 2)
- [ ] Real recording test passes (manual count ± 2)
- [ ] Unit tests pass

### Task 3.3: Fatigue Windows & Insights
- [ ] `find-below-threshold` implemented
- [ ] `merge-close-windows` implemented
- [ ] `compute-severity` implemented
- [ ] `detect-fatigue-windows` replaces stub
- [ ] `generate-insights` implemented
- [ ] Unit tests pass
- [ ] Manual breath-hold test passes
- [ ] Insights are actionable

### Task 3.4: Integration
- [ ] `analyze` function complete (no stubs)
- [ ] Re-frame event `:session/analyze-breathing` works
- [ ] UI button triggers analysis
- [ ] Analysis results display correctly
- [ ] End-to-end test passes

### Documentation
- [ ] All functions have docstrings
- [ ] Examples included in docstrings
- [ ] REPL verification scripts documented
- [ ] Manual test procedures documented

### Code Quality
- [ ] Zero TODOs or FIXMEs
- [ ] No warnings during compilation
- [ ] All functions are pure (no side effects in shared/)
- [ ] Code follows ClojureScript idioms

---

## 🚀 READY TO SHIP

When all checkboxes are complete, LOD 2 is done!

**Success Criteria**:
```
User can:
✅ Record 60-second breathing session
✅ Click "Analyze Breathing" button
✅ See breathing rate (within ±2 bpm of manual count)
✅ See breath holds detected
✅ Receive actionable insights
```

---

**Document Owner**: The 10X Team
**Last Updated**: 2025-11-18
**Status**: Ready for Implementation 🎯
