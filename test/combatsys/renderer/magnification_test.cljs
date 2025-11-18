(ns combatsys.renderer.magnification-test
  "Tests for Eulerian video magnification.

  Run with: npx shadow-cljs compile test
  Then check console for results

  Note: These tests require WebGPU support in the browser/Electron environment."
  (:require [combatsys.renderer.magnification :as mag]
            [combatsys.renderer.gpu :as gpu]))

;; ============================================================================
;; Test Helpers
;; ============================================================================

(defn print-test-result
  "Print test result to console."
  [test-name passed? details]
  (if passed?
    (js/console.log "✅" test-name "PASSED" (when details (str "- " details)))
    (js/console.error "❌" test-name "FAILED" (when details (str "- " details)))))

(defn measure-motion-amplitude
  "Measure motion amplitude in frame sequence.

  Finds the brightest pixel position in each frame and measures
  maximum displacement.

  Args:
    frames: Vector of Uint8ClampedArrays (RGBA pixels)
    width: Frame width
    height: Frame height

  Returns:
    Float - Maximum displacement in pixels"
  [frames width height]
  (let [positions
        (mapv (fn [frame]
                ;; Find brightest pixel (white circle)
                (loop [y 0
                       x 0
                       max-brightness 0
                       max-x 0
                       max-y 0]
                  (if (>= y height)
                    {:x max-x :y max-y}
                    (let [idx (* (+ (* y width) x) 4)
                          brightness (aget frame idx)]
                      (if (> brightness max-brightness)
                        (recur (if (>= (inc x) width) (inc y) y)
                               (if (>= (inc x) width) 0 (inc x))
                               brightness
                               x
                               y)
                        (recur (if (>= (inc x) width) (inc y) y)
                               (if (>= (inc x) width) 0 (inc x))
                               max-brightness
                               max-x
                               max-y))))))
              frames)

        ;; Find min and max X positions
        xs (mapv :x positions)
        min-x (apply min xs)
        max-x (apply max xs)
        amplitude (/ (- max-x min-x) 2.0)] ;; Peak-to-peak / 2

    amplitude))

;; ============================================================================
;; Tests
;; ============================================================================

(defn test-temporal-mean-computation
  "Test temporal mean computation."
  []
  (let [;; Create 3 test frames with different colors
        width 4
        height 4
        pixels-per-frame (* width height 4)

        frame1 (js/Uint8ClampedArray. pixels-per-frame)
        frame2 (js/Uint8ClampedArray. pixels-per-frame)
        frame3 (js/Uint8ClampedArray. pixels-per-frame)]

    ;; Frame 1: All red (255, 0, 0, 255)
    (dotimes [i (/ pixels-per-frame 4)]
      (let [idx (* i 4)]
        (aset frame1 idx 255)
        (aset frame1 (+ idx 3) 255)))

    ;; Frame 2: All green (0, 255, 0, 255)
    (dotimes [i (/ pixels-per-frame 4)]
      (let [idx (* i 4)]
        (aset frame2 (+ idx 1) 255)
        (aset frame2 (+ idx 3) 255)))

    ;; Frame 3: All blue (0, 0, 255, 255)
    (dotimes [i (/ pixels-per-frame 4)]
      (let [idx (* i 4)]
        (aset frame3 (+ idx 2) 255)
        (aset frame3 (+ idx 3) 255)))

    ;; Compute temporal mean
    (let [mean (mag/compute-temporal-mean [frame1 frame2 frame3] width height)
          ;; Mean should be (255+0+0)/3=85, (0+255+0)/3=85, (0+0+255)/3=85
          r (aget mean 0)
          g (aget mean 1)
          b (aget mean 2)
          correct? (and (< 80 r 90) (< 80 g 90) (< 80 b 90))]

      (print-test-result "temporal-mean-computation" correct?
                         (str "Mean RGB: [" r " " g " " b "]")))))

(defn test-synthetic-frame-generation
  "Test generation of synthetic test frames."
  []
  (let [frames (mag/generate-test-frames 10 256 256 {:amplitude 2 :frequency 0.5})
        correct-count? (= 10 (count frames))
        correct-size? (= (* 256 256 4) (.-length (first frames)))
        all-valid? (every? #(instance? js/Uint8ClampedArray %) frames)
        all-correct? (and correct-count? correct-size? all-valid?)]

    (print-test-result "synthetic-frame-generation" all-correct?
                       (str "Generated " (count frames) " frames, "
                            "size: " (.-length (first frames)) " bytes"))))

(defn test-motion-measurement
  "Test motion amplitude measurement."
  []
  (let [;; Generate frames with known amplitude (2 pixels)
        frames (mag/generate-test-frames 30 256 256 {:amplitude 2.0 :frequency 0.5})
        amplitude (measure-motion-amplitude frames 256 256)
        ;; Allow ±0.5 pixel error due to discrete sampling
        correct? (< 1.5 amplitude 2.5)]

    (print-test-result "motion-measurement" correct?
                       (str "Measured amplitude: " amplitude " pixels (expected ~2)"))))

(defn test-identity-magnification
  "Test magnification with gain=1 (should be identity)."
  []
  (js/Promise.
   (fn [resolve reject]
     (-> (gpu/init-gpu!)
         (.then (fn [ctx]
                  (let [;; Generate test frames
                        frames (mag/generate-test-frames 5 64 64 {:amplitude 2})
                        original-amplitude (measure-motion-amplitude frames 64 64)]

                    ;; Magnify with gain=1 (identity)
                    (-> (mag/magnify-frames! ctx frames 64 64 {:gain 1.0 :blur? false})
                        (.then (fn [magnified]
                                 (let [magnified-amplitude (measure-motion-amplitude magnified 64 64)
                                       ;; Motion should be roughly the same (within 20%)
                                       ratio (/ magnified-amplitude original-amplitude)
                                       correct? (< 0.8 ratio 1.2)]

                                   (print-test-result "identity-magnification" correct?
                                                      (str "Original: " original-amplitude
                                                           ", Magnified: " magnified-amplitude
                                                           ", Ratio: " ratio))

                                   (gpu/release-gpu! ctx)
                                   (if correct?
                                     (resolve true)
                                     (reject (js/Error. "Identity test failed"))))))
                        (.catch reject)))))
         (.catch (fn [error]
                   (print-test-result "identity-magnification" false (str "Error: " (.-message error)))
                   (reject error)))))))

(defn test-motion-amplification
  "Test motion amplification with gain=10."
  []
  (js/Promise.
   (fn [resolve reject]
     (-> (gpu/init-gpu!)
         (.then (fn [ctx]
                  (let [;; Generate test frames with 2 pixel motion
                        frames (mag/generate-test-frames 10 64 64 {:amplitude 2.0})
                        original-amplitude (measure-motion-amplitude frames 64 64)
                        expected-gain 10.0]

                    ;; Magnify with gain=10
                    (-> (mag/magnify-frames! ctx frames 64 64 {:gain expected-gain :blur? false})
                        (.then (fn [magnified]
                                 (let [magnified-amplitude (measure-motion-amplitude magnified 64 64)
                                       actual-gain (/ magnified-amplitude original-amplitude)
                                       ;; Allow ±20% error
                                       correct? (< 8.0 actual-gain 12.0)]

                                   (print-test-result "motion-amplification" correct?
                                                      (str "Original: " original-amplitude "px, "
                                                           "Magnified: " magnified-amplitude "px, "
                                                           "Gain: " actual-gain "x (expected " expected-gain "x)"))

                                   (gpu/release-gpu! ctx)
                                   (if correct?
                                     (resolve true)
                                     (reject (js/Error. "Amplification test failed"))))))
                        (.catch reject)))))
         (.catch (fn [error]
                   (print-test-result "motion-amplification" false (str "Error: " (.-message error)))
                   (reject error)))))))

;; ============================================================================
;; Test Runner
;; ============================================================================

(defn run-all-tests!
  "Run all magnification tests in sequence."
  []
  (js/console.log "")
  (js/console.log "🧪 Running Magnification Tests...")
  (js/console.log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

  ;; Sync tests
  (test-temporal-mean-computation)
  (test-synthetic-frame-generation)
  (test-motion-measurement)

  ;; Async tests (GPU-based)
  (-> (test-identity-magnification)
      (.then (fn []
               (test-motion-amplification)))
      (.then (fn []
               (js/console.log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
               (js/console.log "✅ All magnification tests completed!")))
      (.catch (fn [error]
                (js/console.log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                (js/console.error "❌ Test suite failed:" (.-message error))))))

;; Auto-run tests when loaded
(defonce tests-run (run-all-tests!))
