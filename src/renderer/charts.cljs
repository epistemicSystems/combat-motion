(ns combatsys.renderer.charts
  "Simple chart components using SVG.

   Philosophy (John Carmack):
   - Hand-rolled SVG is fast for small datasets (n < 100)
   - No external dependencies (keeps bundle small)
   - Full control over rendering

   Philosophy (Brett Victor):
   - Make the data visible
   - Show the trend line so users understand the math
   - Interactive would be nice, but static is sufficient for v1"
  (:require [reagent.core :as r]))

;; ============================================================================
;; LINE CHART
;; ============================================================================

(defn line-chart
  "Simple SVG line chart with optional trend line.

  Props:
    :data - Vector of y-values (numbers)
    :labels - Vector of x-axis labels (strings)
    :width - Chart width in pixels (default 700)
    :height - Chart height in pixels (default 350)
    :title - Chart title (string)
    :y-label - Y-axis label (string)
    :trend-line - (optional) Regression line {:m slope :b intercept}

  Example:
    [line-chart {:data [20 21 22 23 24]
                 :labels [\"Mon\" \"Tue\" \"Wed\" \"Thu\" \"Fri\"]
                 :title \"Breathing Rate\"
                 :y-label \"Rate (bpm)\"
                 :width 700
                 :height 350
                 :trend-line {:m 1.0 :b 20.0}}]"
  [{:keys [data labels width height title y-label trend-line]
    :or {width 700 height 350}}]

  (when (and (seq data) (seq labels))
    (let [padding 60
          chart-width (- width (* 2 padding))
          chart-height (- height (* 2 padding))

          ;; Compute y-axis scale
          min-y (apply min data)
          max-y (apply max data)
          y-range (if (zero? (- max-y min-y))
                    1  ;; Avoid division by zero
                    (- max-y min-y))
          y-scale (/ chart-height y-range)

          ;; Compute data points
          points (map-indexed
                  (fn [i y]
                    (let [x (+ padding (* i (/ chart-width (dec (count data)))))
                          y-coord (+ padding (- chart-height (* (- y min-y) y-scale)))]
                      [x y-coord]))
                  data)

          ;; Create SVG path for data line
          line-path (apply str "M "
                          (interpose " L "
                                     (map (fn [[x y]]
                                            (str x " " y))
                                          points)))]

      [:svg {:width width
             :height height
             :style {:background-color "white"
                     :border "1px solid #ddd"
                     :border-radius "8px"}}

       ;; Title
       [:text {:x (/ width 2)
               :y 30
               :text-anchor "middle"
               :font-size 18
               :font-weight "600"
               :fill "#333"}
        title]

       ;; Y-axis
       [:line {:x1 padding
               :y1 padding
               :x2 padding
               :y2 (+ padding chart-height)
               :stroke "#333"
               :stroke-width 2}]

       ;; X-axis
       [:line {:x1 padding
               :y1 (+ padding chart-height)
               :x2 (+ padding chart-width)
               :y2 (+ padding chart-height)
               :stroke "#333"
               :stroke-width 2}]

       ;; Y-axis label
       [:text {:x 20
               :y (/ height 2)
               :text-anchor "middle"
               :font-size 12
               :fill "#666"
               :transform (str "rotate(-90 20 " (/ height 2) ")")}
        y-label]

       ;; Y-axis ticks and labels
       (let [num-ticks 5
             tick-values (for [i (range (inc num-ticks))]
                          (+ min-y (* i (/ y-range num-ticks))))]
         (for [[i tick-value] (map-indexed vector tick-values)]
           (let [y-pos (+ padding (- chart-height (* i (/ chart-height num-ticks))))]
             ^{:key i}
             [:g
              ;; Tick mark
              [:line {:x1 (- padding 5)
                      :y1 y-pos
                      :x2 padding
                      :y2 y-pos
                      :stroke "#333"
                      :stroke-width 1}]
              ;; Label
              [:text {:x (- padding 10)
                      :y (+ y-pos 4)
                      :text-anchor "end"
                      :font-size 10
                      :fill "#666"}
               (.toFixed tick-value 1)]])))

       ;; Grid lines (horizontal)
       (let [num-ticks 5]
         (for [i (range 1 num-ticks)]
           (let [y-pos (+ padding (* i (/ chart-height num-ticks)))]
             ^{:key i}
             [:line {:x1 padding
                     :y1 y-pos
                     :x2 (+ padding chart-width)
                     :y2 y-pos
                     :stroke "#eee"
                     :stroke-width 1}])))

       ;; Data line
       [:path {:d line-path
               :stroke "#007AFF"
               :stroke-width 3
               :fill "none"}]

       ;; Data points
       (for [[i [x y]] (map-indexed vector points)]
         ^{:key i}
         [:circle {:cx x
                   :cy y
                   :r 5
                   :fill "#007AFF"
                   :stroke "white"
                   :stroke-width 2}])

       ;; Trend line (if provided)
       (when trend-line
         (let [{:keys [m b]} trend-line
               x1 padding
               y1-value b
               y1 (+ padding (- chart-height (* (- y1-value min-y) y-scale)))
               x2 (+ padding chart-width)
               y2-value (+ (* m (dec (count data))) b)
               y2 (+ padding (- chart-height (* (- y2-value min-y) y-scale)))]
           [:line {:x1 x1
                   :y1 y1
                   :x2 x2
                   :y2 y2
                   :stroke "#FF6B6B"
                   :stroke-width 2
                   :stroke-dasharray "5,5"}]))

       ;; X-axis labels
       (for [[i label] (map-indexed vector labels)]
         (let [x (+ padding (* i (/ chart-width (dec (count labels)))))]
           ^{:key i}
           [:text {:x x
                   :y (+ padding chart-height 20)
                   :text-anchor "middle"
                   :font-size 10
                   :fill "#666"}
            label]))])))
