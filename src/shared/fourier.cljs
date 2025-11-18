(ns combatsys.fourier
  "FFT utilities for frequency analysis.

  Wraps fft-js JavaScript library for use in ClojureScript.

  Philosophy (John Carmack):
  - Measure before optimizing
  - FFT is O(n log n) - fast enough for our needs
  - Profile if performance becomes an issue"
  (:require ["fft-js" :as fft-js]))

(defn fft-transform
  "Apply Fast Fourier Transform to real-valued signal.

  Converts time-domain signal to frequency domain.
  Uses Cooley-Tukey FFT algorithm (O(n log n)).

  Args:
    signal: Vector of real numbers (time-domain samples)
    sampling-rate: Samples per second (default 15 fps for pose data)

  Returns:
    Vector of [magnitude frequency] pairs, sorted by frequency
    Only returns positive frequencies (0 to Nyquist)

  Example:
    (def signal [0 1 0 -1 0 1 0 -1]) ;; 1 Hz sine at 4 Hz sampling
    (def freqs (fft-transform signal 4))
    ;; => [[0.0 0.0] [4.0 1.0] [0.0 2.0] ...]
    ;;     ^magnitude  ^frequency (Hz)

  Notes:
    - Signal length should be power of 2 for efficiency
    - Pads signal with zeros if not power of 2
    - Default sampling rate is 15 fps (pose detection rate)"
  ([signal] (fft-transform signal 15))
  ([signal sampling-rate]
   (if (empty? signal)
     []
     (let [;; Pad to next power of 2 for FFT efficiency
           n (count signal)
           next-pow2 (Math/pow 2 (Math/ceil (/ (Math/log n) (Math/log 2))))
           padded (vec (concat signal (repeat (- next-pow2 n) 0)))

           ;; Convert to JS array (fft-js expects JS array)
           js-signal (clj->js padded)

           ;; Compute FFT (returns array of [real, imag] pairs)
           phasors (fft-js/fft js-signal)

           ;; Compute magnitudes from complex numbers
           magnitudes (mapv (fn [[real imag]]
                              (Math/sqrt (+ (* real real) (* imag imag))))
                            (js->clj phasors))

           ;; Compute frequencies (Hz)
           freq-resolution (/ sampling-rate next-pow2)
           freqs (mapv #(* % freq-resolution) (range (count magnitudes)))

           ;; Combine into [magnitude freq] pairs
           result (mapv vector magnitudes freqs)]

       ;; Only return first half (positive frequencies up to Nyquist)
       (subvec result 0 (quot (count result) 2))))))

(defn find-peak-in-range
  "Find frequency with maximum magnitude in given range.

  Args:
    freq-domain: Vector of [magnitude frequency] pairs (from fft-transform)
    freq-min: Minimum frequency (Hz)
    freq-max: Maximum frequency (Hz)

  Returns:
    Map {:frequency Hz :magnitude M :confidence C}
    Confidence = ratio of peak magnitude to mean magnitude in range

  Example:
    (def freqs (fft-transform breathing-signal))
    (def peak (find-peak-in-range freqs 0.1 0.5))
    ;; => {:frequency 0.367 :magnitude 12.5 :confidence 0.94}

  Notes:
    - Returns nil if no frequencies in range
    - Confidence > 0.7 indicates strong periodic signal
    - Confidence < 0.5 indicates noisy or non-periodic signal"
  [freq-domain freq-min freq-max]
  (if (empty? freq-domain)
    {:frequency 0.0 :magnitude 0.0 :confidence 0.0}
    (let [;; Filter to desired frequency range
          in-range (filterv (fn [[mag freq]]
                              (and (>= freq freq-min) (<= freq freq-max)))
                            freq-domain)]

      (if (empty? in-range)
        ;; No frequencies in range
        {:frequency 0.0 :magnitude 0.0 :confidence 0.0}

        (let [;; Find peak (maximum magnitude)
              peak (apply max-key first in-range)
              peak-mag (first peak)
              peak-freq (second peak)

              ;; Compute confidence (peak vs. mean)
              mean-mag (/ (reduce + (map first in-range))
                          (count in-range))
              ;; Normalize: confidence = (peak - mean) / mean
              ;; Clamped to [0.0, 1.0]
              confidence (min 1.0
                              (if (> mean-mag 0.001)
                                (/ (- peak-mag mean-mag) peak-mag)
                                0.0))]

          {:frequency peak-freq
           :magnitude peak-mag
           :confidence confidence})))))

;; ============================================================
;; TESTING HELPERS
;; ============================================================

(comment
  ;; Generate synthetic sine wave for testing
  (defn generate-sine-wave
    "Generate synthetic sine wave at given frequency.

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

  ;; Test FFT with known frequency
  (let [signal (generate-sine-wave 0.5 60 15) ;; 0.5 Hz = 30 bpm
        freq-domain (fft-transform signal 15)
        peak (find-peak-in-range freq-domain 0.1 0.8)]
    (println "Peak frequency:" (:frequency peak) "Hz")
    (println "Expected: 0.5 Hz")
    (println "Confidence:" (:confidence peak))
    ;; Should detect ~0.5 Hz with high confidence
    )
  )
