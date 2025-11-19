(ns combatsys.renderer.explainability
  "Explainability and observability UI components.

  Philosophy (Brett Victor):
  - Every number has a story
  - Show derivation, not just results
  - Make the invisible visible

  Every metric includes:
  - The final value
  - How it was calculated (provenance)
  - Intermediate steps with visualizations
  - Confidence/quality indicators"
  (:require [reagent.core :as r]
            [combatsys.renderer.animations :as anim]
            [combatsys.renderer.charts :as charts]))

;; ============================================================
;; PROVENANCE TRACKING
;; ============================================================

(defn add-provenance
  "Add provenance metadata to analysis result.

  Args:
    value: The computed value
    metadata: Map with provenance info
      {:method :fft-peak-detection
       :source-frames [120 121 122 ...]
       :intermediate-steps [{:step :extract-signal :result [...]}
                            {:step :smooth :result [...]}
                            {:step :fft :result {...}}]
       :confidence 0.94
       :explanation \"Detected dominant frequency at 0.37 Hz\"}

  Returns:
    Map with :value and :provenance

  Example:
    (add-provenance 22
                    {:method :fft-peak-detection
                     :explanation \"Detected from torso motion\"})"
  [value metadata]
  {:value value
   :provenance metadata})

(defn get-provenance
  "Extract provenance from analysis result.

  Args:
    analysis-result: Map with :provenance key

  Returns:
    Provenance map or nil"
  [analysis-result]
  (:provenance analysis-result))

;; ============================================================
;; EXPLAINABILITY UI COMPONENTS
;; ============================================================

(defn metric-with-explain-button
  "Display metric with '?' button to show explanation.

  Args:
    label: Metric label
    value: Metric value (can be number or string)
    provenance: Provenance metadata
    opts: {:unit \"bpm\" :format-fn #(.toFixed % 1)}

  Example:
    [metric-with-explain-button
     \"Breathing Rate\"
     22
     {:method :fft :confidence 0.94}
     {:unit \" bpm\"}]"
  ([label value provenance]
   (metric-with-explain-button label value provenance {}))
  ([label value provenance {:keys [unit format-fn show-confidence?]
                            :or {unit ""
                                 format-fn identity
                                 show-confidence? true}}]
   (let [showing-explanation (r/atom false)]
     (fn [label value provenance opts]
       [:div.metric-container
        {:style {:display "flex"
                 :align-items "center"
                 :gap "12px"
                 :padding "12px"
                 :border "1px solid #e0e0e0"
                 :border-radius "8px"
                 :background "#fff"
                 :margin-bottom "8px"}}

        ;; Metric label and value
        [:div.metric-content
         {:style {:flex 1}}
         [:div.metric-label
          {:style {:font-size "14px"
                   :color "#666"
                   :margin-bottom "4px"}}
          label]
         [:div.metric-value
          {:style {:font-size "24px"
                   :font-weight "600"
                   :color "#333"}}
          (str (format-fn value) unit)]

         ;; Confidence indicator
         (when (and show-confidence? (:confidence provenance))
           [:div.confidence-indicator
            {:style {:font-size "12px"
                     :color "#999"
                     :margin-top "4px"}}
            (str "Confidence: " (.toFixed (* 100 (:confidence provenance)) 0) "%")])]

        ;; Explain button
        [:button.explain-btn
         {:style {:width "32px"
                  :height "32px"
                  :border-radius "50%"
                  :border "2px solid #007bff"
                  :background "transparent"
                  :color "#007bff"
                  :font-size "16px"
                  :font-weight "600"
                  :cursor "pointer"
                  :display "flex"
                  :align-items "center"
                  :justify-content "center"
                  :transition "all 0.2s ease-out"}
          :on-click #(swap! showing-explanation not)
          :on-mouse-enter #(-> % .-target .-style .-background (set! "#007bff"))
          :on-mouse-leave #(-> % .-target .-style .-background (set! "transparent"))}
         "?"]

        ;; Explanation modal
        (when @showing-explanation
          [anim/modal showing-explanation
           [explanation-content label value provenance]
           {:on-close #(reset! showing-explanation false)}])]))))

(defn explanation-content
  "Content for explanation modal.

  Shows:
  1. Method used
  2. Intermediate steps with visualizations
  3. Confidence and quality metrics
  4. Human-readable explanation

  Args:
    label: Metric label
    value: Final value
    provenance: Provenance metadata"
  [label value provenance]
  [:div.explanation-modal
   {:style {:max-width "800px"}}

   ;; Header
   [:div.explanation-header
    {:style {:margin-bottom "24px"}}
    [:h2 {:style {:font-size "20px"
                  :font-weight "600"
                  :margin-bottom "8px"}}
     (str "How we calculated: " label)]
    [:p {:style {:color "#666"
                 :font-size "14px"}}
     (or (:explanation provenance)
         "This metric was computed from pose estimation data.")]]

   ;; Method
   [:div.explanation-section
    {:style {:margin-bottom "24px"}}
    [:h3 {:style {:font-size "16px"
                  :font-weight "600"
                  :margin-bottom "8px"}}
     "Method"]
    [:p {:style {:color "#666"}}
     (str "Algorithm: " (name (or (:method provenance) :unknown)))]]

   ;; Intermediate steps
   (when-let [steps (:intermediate-steps provenance)]
     [:div.explanation-section
      {:style {:margin-bottom "24px"}}
      [:h3 {:style {:font-size "16px"
                    :font-weight "600"
                    :margin-bottom "12px"}}
       "Calculation Steps"]

      (for [[idx step] (map-indexed vector steps)]
        ^{:key idx}
        [:div.step-card
         {:style {:border "1px solid #e0e0e0"
                  :border-radius "8px"
                  :padding "16px"
                  :margin-bottom "12px"
                  :background "#f9f9f9"}}

         [:div.step-header
          {:style {:display "flex"
                   :align-items "center"
                   :gap "8px"
                   :margin-bottom "8px"}}
          [:div.step-number
           {:style {:width "24px"
                    :height "24px"
                    :border-radius "50%"
                    :background "#007bff"
                    :color "#fff"
                    :display "flex"
                    :align-items "center"
                    :justify-content "center"
                    :font-size "12px"
                    :font-weight "600"}}
           (inc idx)]
          [:h4 {:style {:font-size "14px"
                        :font-weight "600"}}
           (name (:step step))]]

         ;; Step description
         (when-let [desc (:description step)]
           [:p {:style {:color "#666"
                        :font-size "13px"
                        :margin-bottom "12px"}}
            desc])

         ;; Step visualization (if available)
         (when-let [result (:result step)]
           [:div.step-visualization
            {:style {:margin-top "12px"}}
            (cond
              ;; Vector of numbers → simple line chart
              (and (vector? result) (number? (first result)))
              [:div.mini-chart
               {:style {:height "60px"
                        :background "#fff"
                        :border "1px solid #e0e0e0"
                        :border-radius "4px"
                        :padding "8px"}}
               [charts/sparkline result {:width 300 :height 40}]]

              ;; Map with numeric values → table
              (map? result)
              [:div.result-table
               {:style {:font-size "12px"
                        :font-family "monospace"
                        :background "#fff"
                        :border "1px solid #e0e0e0"
                        :border-radius "4px"
                        :padding "8px"}}
               (for [[k v] result]
                 ^{:key k}
                 [:div {:style {:display "flex"
                               :justify-content "space-between"
                               :margin-bottom "4px"}}
                  [:span {:style {:color "#666"}} (name k)]
                  [:span {:style {:font-weight "600"}}
                   (if (number? v) (.toFixed v 3) (str v))]])]

              ;; Default: show as string
              :else
              [:pre {:style {:font-size "11px"
                            :color "#666"
                            :overflow "auto"
                            :max-height "100px"}}
               (str result)])])])])

   ;; Quality indicators
   [:div.explanation-section
    {:style {:margin-bottom "24px"}}
    [:h3 {:style {:font-size "16px"
                  :font-weight "600"
                  :margin-bottom "12px"}}
     "Quality Indicators"]

    [:div.quality-grid
     {:style {:display "grid"
              :grid-template-columns "1fr 1fr"
              :gap "12px"}}

     ;; Confidence
     (when-let [conf (:confidence provenance)]
       [:div.quality-item
        {:style {:border "1px solid #e0e0e0"
                 :border-radius "8px"
                 :padding "12px"}}
        [:div {:style {:font-size "12px"
                       :color "#666"
                       :margin-bottom "4px"}}
         "Confidence"]
        [:div {:style {:font-size "18px"
                       :font-weight "600"
                       :color (cond
                               (>= conf 0.9) "#28a745"
                               (>= conf 0.7) "#ffc107"
                               :else "#dc3545")}}
         (str (.toFixed (* 100 conf) 0) "%")]])

     ;; Data quality
     (when-let [quality (:data-quality provenance)]
       [:div.quality-item
        {:style {:border "1px solid #e0e0e0"
                 :border-radius "8px"
                 :padding "12px"}}
        [:div {:style {:font-size "12px"
                       :color "#666"
                       :margin-bottom "4px"}}
         "Data Quality"]
        [:div {:style {:font-size "18px"
                       :font-weight "600"}}
         (name quality)]])

     ;; Sample size
     (when-let [n (:sample-size provenance)]
       [:div.quality-item
        {:style {:border "1px solid #e0e0e0"
                 :border-radius "8px"
                 :padding "12px"}}
        [:div {:style {:font-size "12px"
                       :color "#666"
                       :margin-bottom "4px"}}
         "Sample Size"]
        [:div {:style {:font-size "18px"
                       :font-weight "600"}}
         (str n " frames")]])

     ;; Source frames
     (when-let [frames (:source-frames provenance)]
       [:div.quality-item
        {:style {:border "1px solid #e0e0e0"
                 :border-radius "8px"
                 :padding "12px"}}
        [:div {:style {:font-size "12px"
                       :color "#666"
                       :margin-bottom "4px"}}
         "Time Range"]
        [:div {:style {:font-size "14px"
                       :font-weight "600"}}
         (str "Frames " (first frames) "-" (last frames))]])]]

   ;; Close button
   [:div.explanation-footer
    {:style {:margin-top "24px"
             :display "flex"
             :justify-content "flex-end"}}
    [anim/button-with-feedback "Close"
                                #(reset! showing-explanation false)
                                {:variant :primary}]]])

;; ============================================================
;; SPARKLINE CHART (mini visualization)
;; ============================================================

(defn sparkline
  "Tiny inline chart for showing data trends.

  Args:
    data: Vector of numbers
    opts: {:width 100 :height 30 :color \"#007bff\"}

  Example:
    [sparkline [1 2 3 2 4 3 5] {:width 150 :height 40}]"
  ([data]
   (sparkline data {}))
  ([data {:keys [width height color]
          :or {width 100
               height 30
               color "#007bff"}}]
   (when (seq data)
     (let [min-val (apply min data)
           max-val (apply max data)
           range (- max-val min-val)
           normalize (fn [v]
                      (if (zero? range)
                        0.5
                        (/ (- v min-val) range)))
           points (map-indexed
                   (fn [idx v]
                     (let [x (* (/ idx (dec (count data))) width)
                           y (* (- 1 (normalize v)) height)]
                       [x y]))
                   data)
           path (str "M " (clojure.string/join " L " (map #(str (first %) " " (second %)) points)))]

       [:svg {:width width :height height :style {:display "block"}}
        [:path {:d path
                :stroke color
                :stroke-width 2
                :fill "none"}]]))))

;; ============================================================
;; CONFIDENCE BADGE
;; ============================================================

(defn confidence-badge
  "Badge showing confidence level.

  Args:
    confidence: Number 0-1
    opts: {:size :small | :medium | :large}

  Example:
    [confidence-badge 0.94 {:size :medium}]"
  ([confidence]
   (confidence-badge confidence {}))
  ([confidence {:keys [size]
                :or {size :medium}}]
   (let [pct (* 100 confidence)
         color (cond
                 (>= confidence 0.9) "#28a745"
                 (>= confidence 0.7) "#ffc107"
                 (>= confidence 0.5) "#fd7e14"
                 :else "#dc3545")
         size-px (case size
                   :small "60px"
                   :medium "80px"
                   :large "100px"
                   "80px")]

     [:div.confidence-badge
      {:style {:width size-px
               :height size-px
               :border-radius "50%"
               :border (str "8px solid " color)
               :display "flex"
               :flex-direction "column"
               :align-items "center"
               :justify-content "center"
               :background "#fff"}}
      [:div {:style {:font-size "24px"
                     :font-weight "600"
                     :color color}}
       (str (.toFixed pct 0) "%")]
      [:div {:style {:font-size "10px"
                     :color "#999"
                     :text-transform "uppercase"}}
       "confidence"]])))

;; ============================================================
;; DATA LINEAGE VIEW
;; ============================================================

(defn data-lineage
  "Visual flowchart showing data transformations.

  Args:
    steps: Vector of transformation steps
      [{:name \"Raw Data\" :description \"33 landmarks\"}
       {:name \"Filter\" :description \"Remove low confidence\"}
       {:name \"Smooth\" :description \"Moving average\"}
       {:name \"Result\" :description \"Final value\"}]

  Example:
    [data-lineage [{:name \"Input\" :description \"Timeline\"}
                   {:name \"Extract\" :description \"Torso motion\"}
                   {:name \"FFT\" :description \"Frequency domain\"}
                   {:name \"Output\" :description \"22 bpm\"}]]"
  [steps]
  [:div.data-lineage
   {:style {:display "flex"
            :align-items "center"
            :gap "8px"
            :overflow-x "auto"
            :padding "16px"}}

   (for [[idx step] (map-indexed vector steps)]
     (list
      ^{:key (str "step-" idx)}
      [:div.lineage-step
       {:style {:min-width "120px"
                :padding "12px"
                :border "2px solid #007bff"
                :border-radius "8px"
                :background "#f0f8ff"
                :text-align "center"}}
       [:div {:style {:font-weight "600"
                      :font-size "12px"
                      :color "#007bff"
                      :margin-bottom "4px"}}
        (:name step)]
       [:div {:style {:font-size "11px"
                      :color "#666"}}
        (:description step)]]

      ;; Arrow between steps
      (when (< idx (dec (count steps)))
        ^{:key (str "arrow-" idx)}
        [:div.arrow
         {:style {:color "#007bff"
                  :font-size "20px"}}
         "→"])))])

;; ============================================================
;; EXPORTS
;; ============================================================

(def ^:export metricWithExplain metric-with-explain-button)
(def ^:export addProvenance add-provenance)
(def ^:export sparkline sparkline)
(def ^:export confidenceBadge confidence-badge)
(def ^:export dataLineage data-lineage)
