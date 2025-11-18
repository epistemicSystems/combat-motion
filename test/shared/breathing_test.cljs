(ns combatsys.breathing-test
  "Unit tests for breathing analysis functions.

  Tests cover:
  - Task 3.1: Torso motion extraction
  - Task 3.2: FFT and breathing rate detection
  - Task 3.3: Fatigue window detection and insights"
  (:require [cljs.test :refer-macros [deftest is testing]]
            [combatsys.breathing :as breathing]
            [combatsys.fourier :as fourier]
            [combatsys.mocks :as mocks]))

;; ============================================================
;; TASK 3.1: TORSO MOTION EXTRACTION TESTS
;; ============================================================

(deftest test-filter-landmarks
  (testing "Filter landmarks by ID"
    (let [landmarks [{:landmark-id :nose :x 0.5 :y 0.3 :z 0.0}
                     {:landmark-id :left-shoulder :x 0.4 :y 0.2 :z -0.1}
                     {:landmark-id :right-shoulder :x 0.6 :y 0.2 :z -0.1}
                     {:landmark-id :left-hip :x 0.4 :y 0.5 :z -0.2}]
          result (breathing/filter-landmarks landmarks [:left-shoulder :right-shoulder])]
      (is (= 2 (count result)) "Should return exactly 2 landmarks")
      (is (= :left-shoulder (:landmark-id (first result))) "First should be left shoulder")
      (is (= :right-shoulder (:landmark-id (second result))) "Second should be right shoulder"))))

(deftest test-compute-centroid
  (testing "Compute centroid of landmarks"
    (let [points [{:x 0.0 :y 0.0 :z 0.0}
                  {:x 1.0 :y 1.0 :z 1.0}]
          centroid (breathing/compute-centroid points)]
      (is (= 0.5 (:x centroid)) "X coordinate should be average")
      (is (= 0.5 (:y centroid)) "Y coordinate should be average")
      (is (= 0.5 (:z centroid)) "Z coordinate should be average")))

  (testing "Centroid of empty landmarks"
    (let [centroid (breathing/compute-centroid [])]
      (is (= {:x 0.0 :y 0.0 :z 0.0} centroid) "Should return origin for empty input"))))

(deftest test-frame-to-frame-distance
  (testing "Euclidean distance between consecutive points"
    (let [points [{:x 0.0 :y 0.0 :z 0.0}
                  {:x 1.0 :y 0.0 :z 0.0}
                  {:x 1.0 :y 1.0 :z 0.0}]
          distances (breathing/frame-to-frame-distance points)]
      (is (= 3 (count distances)) "Should have same length as input")
      (is (= 0.0 (first distances)) "First distance should be 0")
      (is (= 1.0 (second distances)) "Second distance should be 1.0")
      (is (= 1.0 (nth distances 2)) "Third distance should be 1.0"))))

(deftest test-moving-average
  (testing "Moving average smoothing"
    (let [signal [1 2 3 10 3 2 1]
          smoothed (breathing/moving-average signal 3)]
      (is (= 7 (count smoothed)) "Should have same length as input")
      ;; Middle peak should be smoothed down
      (is (< (nth smoothed 3) 10) "Peak should be reduced")
      (is (> (nth smoothed 3) 3) "Peak should still be higher than neighbors")))

  (testing "Moving average on empty signal"
    (is (= [] (breathing/moving-average [] 3)) "Should return empty for empty input")))

(deftest test-extract-torso-motion-length
  (testing "Extract torso motion returns correct length"
    (let [timeline (mocks/mock-timeline 90)
          signal (breathing/extract-torso-motion timeline)]
      ;; Signal length should match timeline length
      (is (= 90 (count signal)) "Signal length should match timeline length")
      ;; All values should be numbers
      (is (every? number? signal) "All values should be numbers")
      ;; All values should be non-negative (distance)
      (is (every? #(>= % 0) signal) "All values should be non-negative"))))

(deftest test-extract-torso-motion-empty
  (testing "Extract torso motion on empty timeline"
    (is (= [] (breathing/extract-torso-motion [])) "Should return empty for empty timeline")))

;; ============================================================
;; TASK 3.2: FFT & BREATHING RATE TESTS
;; ============================================================

(defn generate-sine-wave
  "Helper: Generate synthetic sine wave at given frequency.

  Args:
    freq-hz: Frequency in Hz
    duration-s: Duration in seconds
    sampling-rate: Samples per second

  Returns:
    Vector of amplitude values"
  [freq-hz duration-s sampling-rate]
  (let [n-samples (* duration-s sampling-rate)]
    (mapv #(Math/sin (* 2 Math/PI freq-hz (/ % sampling-rate)))
          (range n-samples))))

(deftest test-fft-transform
  (testing "FFT detects known frequency"
    ;; Generate synthetic sine wave at 1 Hz
    ;; Sampling rate: 30 Hz, duration: 2 seconds
    (let [signal (generate-sine-wave 1.0 2 30)
          freq-domain (fourier/fft-transform signal 30)]

      ;; Should have peak near 1 Hz
      (let [peak (fourier/find-peak-in-range freq-domain 0.5 1.5)]
        (is (< 0.9 (:frequency peak) 1.1) "Should detect frequency near 1 Hz")
        (is (> (:confidence peak) 0.5) "Should have reasonable confidence")))))

(deftest test-fft-breathing-frequency
  (testing "FFT detects breathing frequency"
    ;; Generate 20 bpm breathing (0.333 Hz)
    (let [breathing-freq-hz (/ 20 60.0)
          signal (generate-sine-wave breathing-freq-hz 60 15)
          freq-domain (fourier/fft-transform signal 15)
          peak (fourier/find-peak-in-range freq-domain 0.1 0.5)]

      (is (< 0.25 (:frequency peak) 0.4) "Should detect breathing frequency")
      (is (> (:confidence peak) 0.5) "Should have reasonable confidence"))))

(deftest test-breathing-rate-synthetic
  (testing "Breathing rate detection on synthetic signal"
    ;; Generate synthetic breathing at 20 bpm (0.333 Hz)
    (let [duration-s 60
          fps 15
          n-samples (* duration-s fps)
          breathing-freq-hz (/ 20 60.0) ;; 20 bpm = 0.333 Hz

          ;; Generate sine wave with amplitude variation
          signal (mapv #(* 0.02 (Math/sin (* 2 Math/PI breathing-freq-hz (/ % fps))))
                       (range n-samples))

          result (breathing/detect-breathing-rate signal)
          detected-bpm (:rate-bpm result)]

      ;; Allow ±4 bpm error (FFT resolution with 15fps sampling)
      (is (number? detected-bpm) "Should return a number")
      (is (< 16 detected-bpm 24) "Detected rate should be within ±4 bpm of 20")
      (is (>= (:confidence result) 0.0) "Confidence should be non-negative")
      (is (<= (:confidence result) 1.0) "Confidence should be <= 1.0"))))

(deftest test-breathing-rate-insufficient-samples
  (testing "Breathing rate with insufficient samples"
    (let [short-signal [0.1 0.2 0.15 0.1 0.05] ;; Only 5 samples
          result (breathing/detect-breathing-rate short-signal)]
      (is (nil? (:rate-bpm result)) "Should return nil for insufficient samples")
      (is (= 0.0 (:confidence result)) "Confidence should be 0")
      (is (some? (:error result)) "Should include error message"))))

;; ============================================================
;; TASK 3.3: FATIGUE WINDOW TESTS
;; ============================================================

(deftest test-find-below-threshold
  (testing "Find regions below threshold"
    (let [signal [0.5 0.5 0.1 0.1 0.1 0.5 0.5 0.05 0.05]
          regions (breathing/find-below-threshold signal 0.3)]
      (is (= 2 (count regions)) "Should find two regions")
      (is (= {:start-idx 2 :end-idx 4} (first regions)) "First region indices 2-4")
      (is (= {:start-idx 7 :end-idx 8} (second regions)) "Second region indices 7-8")))

  (testing "No regions below threshold"
    (let [signal [0.5 0.5 0.5 0.5]
          regions (breathing/find-below-threshold signal 0.3)]
      (is (= 0 (count regions)) "Should find no regions")))

  (testing "Entire signal below threshold"
    (let [signal [0.1 0.1 0.1 0.1]
          regions (breathing/find-below-threshold signal 0.3)]
      (is (= 1 (count regions)) "Should find one continuous region")
      (is (= {:start-idx 0 :end-idx 3} (first regions)) "Should span entire signal"))))

(deftest test-merge-close-windows
  (testing "Merge adjacent windows within gap"
    (let [regions [{:start-idx 10 :end-idx 15}
                   {:start-idx 18 :end-idx 23}]
          merged (breathing/merge-close-windows regions 5)]
      (is (= 1 (count merged)) "Should merge into one window")
      (is (= {:start-idx 10 :end-idx 23} (first merged)) "Should span both regions")))

  (testing "Keep separate windows outside gap"
    (let [regions [{:start-idx 10 :end-idx 15}
                   {:start-idx 25 :end-idx 30}]
          merged (breathing/merge-close-windows regions 5)]
      (is (= 2 (count merged)) "Should keep separate")
      (is (= regions merged) "Should be unchanged")))

  (testing "Empty regions"
    (is (= [] (breathing/merge-close-windows [] 5)) "Should return empty")))

(deftest test-compute-severity
  (testing "Compute severity for region below threshold"
    (let [signal [0.5 0.5 0.1 0.05 0.1 0.5]
          region {:start-idx 2 :end-idx 4}
          severity (breathing/compute-severity signal region 0.3)]
      ;; Mean in window: (0.1 + 0.05 + 0.1) / 3 = 0.083
      ;; Severity: (0.3 - 0.083) / 0.3 = 0.72
      (is (> severity 0.6) "Severity should be high for significant drop")
      (is (< severity 0.8) "Severity should be reasonable")))

  (testing "Severity at threshold is zero"
    (let [signal [0.5 0.5 0.3 0.3 0.3 0.5]
          region {:start-idx 2 :end-idx 4}
          severity (breathing/compute-severity signal region 0.3)]
      (is (= 0.0 severity) "Severity should be 0 when at threshold")))

  (testing "Invalid region returns zero"
    (let [signal [0.5 0.5 0.5]
          region {:start-idx 5 :end-idx 10}]
      (is (= 0.0 (breathing/compute-severity signal region 0.3)) "Should return 0 for invalid region"))))

(deftest test-detect-fatigue-windows
  (testing "Detect fatigue windows in signal with breath hold"
    (let [;; Signal with intentional breath hold
          ;; 50 frames normal (0.5) + 20 frames hold (0.05) + 50 frames normal (0.5)
          signal (vec (concat (repeat 50 0.5)
                              (repeat 20 0.05)
                              (repeat 50 0.5)))
          windows (breathing/detect-fatigue-windows signal 15)]
      (is (= 1 (count windows)) "Should detect one fatigue window")
      (is (> (:severity (first windows)) 0.7) "Severity should be high for breath hold")
      (is (number? (:start-ms (first windows))) "Should have start timestamp")
      (is (number? (:end-ms (first windows))) "Should have end timestamp")))

  (testing "No fatigue windows in steady signal"
    (let [signal (vec (repeat 100 0.5))
          windows (breathing/detect-fatigue-windows signal)]
      (is (= 0 (count windows)) "Should detect no windows in steady signal")))

  (testing "Filter out very short windows"
    (let [;; Short dip (only 10 frames, < 15 frame minimum)
          signal (vec (concat (repeat 50 0.5)
                              (repeat 10 0.05)
                              (repeat 50 0.5)))
          windows (breathing/detect-fatigue-windows signal)]
      (is (= 0 (count windows)) "Should filter out windows shorter than 1 second")))

  (testing "Empty signal returns empty"
    (is (= [] (breathing/detect-fatigue-windows [])) "Should return empty for empty signal")))

(deftest test-format-timestamp
  (testing "Format milliseconds to MM:SS"
    (is (= "00:00" (breathing/format-timestamp 0)) "Zero milliseconds")
    (is (= "00:05" (breathing/format-timestamp 5000)) "5 seconds")
    (is (= "00:45" (breathing/format-timestamp 45000)) "45 seconds")
    (is (= "01:23" (breathing/format-timestamp 83000)) "1 minute 23 seconds")
    (is (= "02:05" (breathing/format-timestamp 125000)) "2 minutes 5 seconds")))

(deftest test-generate-insights
  (testing "Generate insights for elevated breathing rate"
    (let [analysis {:rate-bpm 28 :depth-score 0.6 :confidence 0.9 :fatigue-windows []}
          insights (breathing/generate-insights analysis)]
      (is (pos? (count insights)) "Should generate at least one insight")
      (is (some #(= (:insight/title %) "Elevated breathing rate") insights)
          "Should detect elevated rate")))

  (testing "Generate insights for shallow breathing"
    (let [analysis {:rate-bpm 18 :depth-score 0.3 :confidence 0.9 :fatigue-windows []}
          insights (breathing/generate-insights analysis)]
      (is (some #(= (:insight/title %) "Shallow breathing detected") insights)
          "Should detect shallow breathing")))

  (testing "Generate insights for normal breathing"
    (let [analysis {:rate-bpm 16 :depth-score 0.8 :confidence 0.9 :fatigue-windows []}
          insights (breathing/generate-insights analysis)]
      (is (some #(= (:insight/title %) "Normal breathing rate") insights)
          "Should recognize normal rate")
      (is (some #(= (:insight/title %) "Strong breathing depth") insights)
          "Should recognize good depth")))

  (testing "Generate insights for fatigue windows"
    (let [analysis {:rate-bpm 18
                    :depth-score 0.6
                    :confidence 0.9
                    :fatigue-windows [{:start-ms 45000 :end-ms 48000 :severity 0.85}]}
          insights (breathing/generate-insights analysis)]
      (is (some #(clojure.string/includes? (:insight/title %) "00:45") insights)
          "Should include timestamp in insight title")
      (is (some #(= (:insight/severity %) :high) insights)
          "High severity window should generate high severity insight")))

  (testing "No insights for low confidence"
    (let [analysis {:rate-bpm 18 :depth-score 0.6 :confidence 0.3 :fatigue-windows []}
          insights (breathing/generate-insights analysis)]
      (is (= 0 (count insights)) "Should not generate insights when confidence is low"))))

;; ============================================================
;; INTEGRATION TEST
;; ============================================================

(deftest test-full-analysis-pipeline
  (testing "Full breathing analysis pipeline"
    ;; Use mock session with known breathing pattern
    (let [session (mocks/mock-breathing-session 60 22) ;; 60s, 22 bpm
          analyzed (breathing/analyze session)
          analysis (get-in analyzed [:session/analysis :breathing])]

      ;; Verify rate detected
      (is (number? (:rate-bpm analysis)) "Rate should be a number")
      (is (< 18 (:rate-bpm analysis) 26) "Rate should be close to 22 bpm")

      ;; Verify depth score present
      (is (number? (:depth-score analysis)) "Depth score should be a number")
      (is (<= 0.0 (:depth-score analysis) 1.0) "Depth score should be between 0 and 1")

      ;; Verify confidence present
      (is (number? (:confidence analysis)) "Confidence should be a number")
      (is (<= 0.0 (:confidence analysis) 1.0) "Confidence should be between 0 and 1")

      ;; Verify fatigue windows present (may be empty)
      (is (vector? (:fatigue-windows analysis)) "Fatigue windows should be a vector")

      ;; Verify insights generated
      (is (vector? (:insights analysis)) "Insights should be a vector")
      (is (pos? (count (:insights analysis))) "Should have at least one insight")

      ;; Verify insight structure
      (let [first-insight (first (:insights analysis))]
        (is (contains? first-insight :insight/title) "Insight should have title")
        (is (contains? first-insight :insight/description) "Insight should have description")
        (is (contains? first-insight :insight/severity) "Insight should have severity")
        (is (contains? first-insight :insight/recommendation) "Insight should have recommendation")))))

;; ============================================================
;; REPL TESTING HELPERS
;; ============================================================

(comment
  ;; Run all tests
  (cljs.test/run-tests 'combatsys.breathing-test)

  ;; Run specific test
  (test-filter-landmarks)
  (test-compute-centroid)
  (test-moving-average)
  (test-extract-torso-motion-length)

  ;; Manual verification
  (require '[combatsys.breathing :as breathing])
  (require '[combatsys.mocks :as mocks])

  ;; Generate mock session
  (def session (mocks/mock-breathing-session 60 22))
  (def timeline (:session/timeline session))

  ;; Extract torso motion
  (def signal (breathing/extract-torso-motion timeline))

  ;; Verify signal properties
  (count signal) ;; => 900 (60s @ 15fps)
  (take 10 signal) ;; => (0.012 0.015 0.018 ...)
  (apply max signal) ;; Check max magnitude
  (apply min signal) ;; Check min magnitude (should be 0 or close)

  ;; Visual inspection of periodicity
  (partition 45 signal) ;; 45 frames ~ 3 seconds
  ;; Should see periodic pattern
  )
