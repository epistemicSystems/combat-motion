(ns combatsys.renderer.animations
  "UI animation and transition utilities.

  Philosophy (Brett Victor):
  - Make state transitions visible
  - Immediate feedback (<100ms)
  - Smooth, natural motion

  Performance (John Carmack):
  - Use CSS transforms (GPU accelerated)
  - Avoid layout thrashing
  - 60fps or bust"
  (:require [reagent.core :as r]))

;; ============================================================
;; ANIMATED COMPONENTS
;; ============================================================

(defn fade-in
  "Fade in component over duration.

  Args:
    component: Reagent component to wrap
    opts: {:duration-ms 300
           :delay-ms 0}

  Example:
    [fade-in [:div \"Hello\"] {:duration-ms 500}]"
  ([component]
   (fade-in component {}))
  ([component {:keys [duration-ms delay-ms]
               :or {duration-ms 300
                    delay-ms 0}}]
   [:div {:style {:animation (str "fadeIn " duration-ms "ms ease-out " delay-ms "ms both")}}
    component]))

(defn slide-in
  "Slide in component from direction.

  Args:
    component: Reagent component
    direction: :left | :right | :top | :bottom
    opts: {:duration-ms 300 :delay-ms 0}

  Example:
    [slide-in [:div \"Panel\"] :left {:duration-ms 400}]"
  ([component direction]
   (slide-in component direction {}))
  ([component direction {:keys [duration-ms delay-ms]
                         :or {duration-ms 300
                              delay-ms 0}}]
   (let [animation-name (str "slideIn" (name direction))]
     [:div {:style {:animation (str animation-name " " duration-ms "ms ease-out " delay-ms "ms both")}}
      component])))

(defn scale-in
  "Scale in component from center.

  Args:
    component: Reagent component
    opts: {:duration-ms 200 :delay-ms 0}

  Example:
    [scale-in [:button \"Click\"] {:duration-ms 200}]"
  ([component]
   (scale-in component {}))
  ([component {:keys [duration-ms delay-ms]
               :or {duration-ms 200
                    delay-ms 0}}]
   [:div {:style {:animation (str "scaleIn " duration-ms "ms ease-out " delay-ms "ms both")}}
    component]))

;; ============================================================
;; LOADING STATES
;; ============================================================

(defn spinner
  "Loading spinner component.

  Args:
    opts: {:size :small | :medium | :large
           :color \"#007bff\"}

  Example:
    [spinner {:size :large :color \"#28a745\"}]"
  ([]
   (spinner {}))
  ([{:keys [size color]
     :or {size :medium
          color "#007bff"}}]
   (let [dimension (case size
                     :small "20px"
                     :medium "40px"
                     :large "60px"
                     "40px")]
     [:div.spinner
      {:style {:width dimension
               :height dimension
               :border (str "3px solid " color "33")
               :border-top-color color
               :border-radius "50%"
               :animation "spin 0.8s linear infinite"}}])))

(defn progress-bar
  "Progress bar component.

  Args:
    progress: Number 0-100 (percentage)
    opts: {:show-label? true
           :color \"#007bff\"
           :height \"20px\"}

  Example:
    [progress-bar 65 {:show-label? true}]"
  ([progress]
   (progress-bar progress {}))
  ([progress {:keys [show-label? color height]
              :or {show-label? true
                   color "#007bff"
                   height "20px"}}]
   [:div.progress-container
    {:style {:width "100%"
             :background "#e9ecef"
             :border-radius "10px"
             :overflow "hidden"
             :height height}}
    [:div.progress-bar
     {:style {:width (str (max 0 (min 100 progress)) "%")
              :height "100%"
              :background color
              :transition "width 0.3s ease-out"
              :display "flex"
              :align-items "center"
              :justify-content "center"}}
     (when show-label?
       [:span {:style {:color "#fff"
                       :font-size "12px"
                       :font-weight "600"}}
        (str (.toFixed progress 0) "%")])]]))

(defn skeleton-loader
  "Skeleton loading placeholder.

  Args:
    type: :text | :circle | :rect
    opts: {:width \"100%\" :height \"20px\"}

  Example:
    [skeleton-loader :text {:width \"80%\"}]"
  ([type]
   (skeleton-loader type {}))
  ([type {:keys [width height]
          :or {width "100%"
               height "20px"}}]
   [:div.skeleton
    {:style {:width width
             :height height
             :background "linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)"
             :background-size "200% 100%"
             :animation "shimmer 1.5s infinite"
             :border-radius (case type
                              :circle "50%"
                              :text "4px"
                              :rect "4px"
                              "4px")}}]))

;; ============================================================
;; ANIMATED NUMBER (count up/down)
;; ============================================================

(defn animated-number
  "Number that animates when value changes.

  Args:
    value: Current numeric value
    opts: {:duration-ms 500
           :format-fn identity
           :suffix \"\"}

  Example:
    [animated-number 42 {:suffix \" bpm\" :format-fn #(.toFixed % 1)}]"
  ([value]
   (animated-number value {}))
  ([value {:keys [duration-ms format-fn suffix]
           :or {duration-ms 500
                format-fn identity
                suffix ""}}]
   (let [displayed-value (r/atom value)
         start-value (atom value)
         start-time (atom nil)]

     (r/create-class
      {:component-did-mount
       (fn [this]
         (reset! start-value value)
         (reset! displayed-value value))

       :component-did-update
       (fn [this [_ old-value]]
         (when (not= value old-value)
           (reset! start-value old-value)
           (reset! start-time (js/Date.now))

           ;; Animate using requestAnimationFrame
           (letfn [(animate []
                     (let [now (js/Date.now)
                           elapsed (- now @start-time)
                           progress (min 1.0 (/ elapsed duration-ms))
                           eased-progress (- 1 (js/Math.pow (- 1 progress) 3)) ;; ease-out cubic
                           current (+ @start-value
                                     (* (- value @start-value) eased-progress))]
                       (reset! displayed-value current)
                       (when (< progress 1.0)
                         (js/requestAnimationFrame animate))))]
             (animate))))

       :reagent-render
       (fn [value opts]
         [:span.animated-number
          (str (format-fn @displayed-value) suffix)])}))))

;; ============================================================
;; TRANSITIONS
;; ============================================================

(defn transition-group
  "Wrapper for animating list items.

  Automatically animates items entering/leaving.

  Args:
    items: Vector of [key component] pairs
    opts: {:enter-duration 300 :exit-duration 200}

  Example:
    [transition-group
     [[:item-1 [card-component item1]]
      [:item-2 [card-component item2]]]]"
  ([items]
   (transition-group items {}))
  ([items {:keys [enter-duration exit-duration]
           :or {enter-duration 300
                exit-duration 200}}]
   [:div.transition-group
    (for [[key component] items]
      ^{:key key}
      [fade-in component {:duration-ms enter-duration}])]))

;; ============================================================
;; INTERACTIVE FEEDBACK
;; ============================================================

(defn button-with-feedback
  "Button with visual feedback on click.

  Args:
    label: Button text
    on-click: Click handler
    opts: {:variant :primary | :secondary | :danger
           :disabled? false
           :loading? false}

  Example:
    [button-with-feedback \"Save\"
                          #(save-session)
                          {:variant :primary :loading? saving?}]"
  ([label on-click]
   (button-with-feedback label on-click {}))
  ([label on-click {:keys [variant disabled? loading?]
                    :or {variant :primary
                         disabled? false
                         loading? false}}]
   (let [clicked (r/atom false)]
     (fn [label on-click opts]
       [:button.btn
        {:class (str "btn-" (name variant)
                    (when @clicked " btn-clicked")
                    (when disabled? " btn-disabled")
                    (when loading? " btn-loading"))
         :disabled (or disabled? loading?)
         :on-click (fn [e]
                     (when-not (or disabled? loading?)
                       (reset! clicked true)
                       (js/setTimeout #(reset! clicked false) 200)
                       (on-click e)))}
        (if loading?
          [:span.btn-content
           [spinner {:size :small :color "#fff"}]
           [:span {:style {:margin-left "8px"}} "Loading..."]]
          label)]))))

;; ============================================================
;; TOOLTIPS (hover explanations)
;; ============================================================

(defn tooltip
  "Tooltip component for hover explanations.

  Args:
    content: Main content to wrap
    tip: Tooltip text or component
    opts: {:position :top | :bottom | :left | :right}

  Example:
    [tooltip
     [:span \"Hover me\"]
     \"This is a tooltip\"
     {:position :top}]"
  ([content tip]
   (tooltip content tip {}))
  ([content tip {:keys [position]
                 :or {position :top}}]
   (let [showing (r/atom false)]
     (fn [content tip opts]
       [:div.tooltip-container
        {:style {:position "relative"
                 :display "inline-block"}
         :on-mouse-enter #(reset! showing true)
         :on-mouse-leave #(reset! showing false)}
        content
        (when @showing
          [:div.tooltip
           {:class (str "tooltip-" (name position))
            :style {:position "absolute"
                    :z-index 1000
                    :background "rgba(0, 0, 0, 0.9)"
                    :color "#fff"
                    :padding "8px 12px"
                    :border-radius "4px"
                    :font-size "12px"
                    :white-space "nowrap"
                    :pointer-events "none"
                    :animation "fadeIn 0.2s ease-out"
                    ;; Position based on direction
                    :bottom (when (= position :top) "100%")
                    :top (when (= position :bottom) "100%")
                    :left (when (= position :right) "100%")
                    :right (when (= position :left) "100%")
                    :margin-bottom (when (= position :top) "8px")
                    :margin-top (when (= position :bottom) "8px")
                    :margin-left (when (= position :right) "8px")
                    :margin-right (when (= position :left) "8px")}}
           tip])]))))

;; ============================================================
;; MODAL TRANSITIONS
;; ============================================================

(defn modal
  "Modal dialog with backdrop and animations.

  Args:
    showing?: Boolean atom controlling visibility
    content: Modal content component
    opts: {:on-close fn :backdrop-click-closes? true}

  Example:
    [modal showing?
           [:div \"Modal content\"]
           {:on-close #(reset! showing? false)}]"
  ([showing? content]
   (modal showing? content {}))
  ([showing? content {:keys [on-close backdrop-click-closes?]
                      :or {backdrop-click-closes? true}}]
   (when @showing?
     [:div.modal-backdrop
      {:style {:position "fixed"
               :top 0
               :left 0
               :right 0
               :bottom 0
               :background "rgba(0, 0, 0, 0.5)"
               :display "flex"
               :align-items "center"
               :justify-content "center"
               :z-index 9999
               :animation "fadeIn 0.2s ease-out"}
       :on-click (when (and backdrop-click-closes? on-close)
                   (fn [e]
                     (when (= (.-target e) (.-currentTarget e))
                       (on-close))))}
      [:div.modal-content
       {:style {:background "#fff"
                :border-radius "8px"
                :padding "24px"
                :max-width "600px"
                :max-height "80vh"
                :overflow-y "auto"
                :animation "scaleIn 0.3s ease-out"}
        :on-click #(.stopPropagation %)} ;; Prevent backdrop click
       content]])))

;; ============================================================
;; EXPORTS
;; ============================================================

(def ^:export fadeIn fade-in)
(def ^:export slideIn slide-in)
(def ^:export scaleIn scale-in)
(def ^:export spinner spinner)
(def ^:export progressBar progress-bar)
(def ^:export animatedNumber animated-number)
(def ^:export buttonWithFeedback button-with-feedback)
(def ^:export tooltip tooltip)
(def ^:export modal modal)
