(ns combatsys.renderer.profile-view
  "User profile view and management UI.

  Displays baseline metrics, learned thresholds, and calibration history.
  Provides actions to recalibrate, export, or delete profile.

  Philosophy (Brett Victor):
  - Make the invisible visible: show what the system learned
  - Every metric has context and explanation
  - Users understand their baseline"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [combatsys.renderer.state :as state]))

;; ============================================================
;; PROFILE HEADER
;; ============================================================

(defn profile-header
  "Display profile header with user ID and last calibration date"
  [profile]
  [:div.profile-header
   [:h1 "Your Profile"]
   [:p.subtitle "Personalized baseline metrics learned from your calibration"]
   [:p.user-id {:style {:font-size "12px" :color "#666"}}
    (str "Profile ID: " (subs (str (:user-id profile)) 0 8) "...")]])

;; ============================================================
;; BASELINE METRICS
;; ============================================================

(defn baseline-metrics
  "Display physical baseline measurements"
  [profile]
  (let [joint-distances (get-in profile [:baseline-pose :joint-distances])]
    [:div.baseline-metrics
     [:h2 "Physical Baseline"]
     [:p.explanation "Body measurements from your T-pose calibration"]

     [:table.metrics-table
      [:tbody
       [:tr
        [:td.label "Height"]
        [:td.value (str (.toFixed (:height-cm profile) 1) " cm")]]

       (when joint-distances
         [:<>
          [:tr
           [:td.label "Shoulder Width"]
           [:td.value (str (.toFixed (:shoulder-width-cm joint-distances) 1) " cm")]]

          [:tr
           [:td.label "Arm Length (avg)"]
           [:td.value
            (let [left (:arm-length-left-cm joint-distances)
                  right (:arm-length-right-cm joint-distances)]
              (if (and left right)
                (str (.toFixed (/ (+ left right) 2) 1) " cm")
                "N/A"))]]])]]]))

;; ============================================================
;; BREATHING BASELINE
;; ============================================================

(defn breathing-baseline
  "Display breathing baseline metrics"
  [profile]
  (let [baseline (:breathing-baseline profile)]
    (if baseline
      [:div.breathing-baseline
       [:h2 "Breathing Baseline"]
       [:p.explanation "Your typical breathing pattern at rest"]

       [:table.metrics-table
        [:tbody
         [:tr
          [:td.label "Typical Rate"]
          [:td.value (str (.toFixed (:typical-rate-bpm baseline) 1) " bpm")]]

         [:tr
          [:td.label "Typical Depth"]
          [:td.value (str (.toFixed (* 100 (:typical-depth baseline)) 0) "%")]]

         [:tr
          [:td.label "Rhythm Regularity"]
          [:td.value (str (.toFixed (* 100 (:typical-rhythm-regularity baseline)) 0) "%")]]]]]

      [:div.breathing-baseline
       [:h2 "Breathing Baseline"]
       [:p.missing "Not calibrated"]])))

;; ============================================================
;; POSTURE BASELINE
;; ============================================================

(defn posture-baseline
  "Display posture baseline metrics"
  [profile]
  (let [baseline (:posture-baseline profile)]
    (if baseline
      [:div.posture-baseline
       [:h2 "Posture Baseline"]
       [:p.explanation "Your typical posture in relaxed T-pose"]

       [:table.metrics-table
        [:tbody
         [:tr
          [:td.label "Forward Head"]
          [:td.value (str (.toFixed (:typical-forward-head-cm baseline) 1) " cm")]]

         [:tr
          [:td.label "Shoulder Imbalance"]
          [:td.value (str (.toFixed (js/Math.abs (:typical-shoulder-imbalance-deg baseline)) 1) "°")]]]]]

      [:div.posture-baseline
       [:h2 "Posture Baseline"]
       [:p.missing "Not calibrated"]])))

;; ============================================================
;; THRESHOLD SETTINGS
;; ============================================================

(defn threshold-settings
  "Display learned alert thresholds"
  [profile]
  (let [thresholds (:learned-thresholds profile)]
    [:div.threshold-settings
     [:h2 "Alert Thresholds"]
     [:p.explanation "These personalized thresholds trigger warnings during analysis"]

     ;; Breathing thresholds
     [:h3 "Breathing"]
     [:table.metrics-table
      [:tbody
       [:tr
        [:td.label "Fatigue Threshold"]
        [:td.value (str (.toFixed (get-in thresholds [:breathing-thresholds :fatigue-threshold]) 2))]]

       [:tr
        [:td.label "Rate Alert Threshold"]
        [:td.value (str (.toFixed (get-in thresholds [:breathing-thresholds :rate-alert-threshold]) 1) " bpm")]]]]

     ;; Posture thresholds
     [:h3 "Posture"]
     [:table.metrics-table
      [:tbody
       [:tr
        [:td.label "Forward Head Alert"]
        [:td.value (str (.toFixed (get-in thresholds [:posture-thresholds :forward-head-alert-cm]) 1) " cm")]]

       [:tr
        [:td.label "Shoulder Imbalance Alert"]
        [:td.value (str (.toFixed (get-in thresholds [:posture-thresholds :shoulder-imbalance-alert-deg]) 1) "°")]]]]]))

;; ============================================================
;; CALIBRATION HISTORY
;; ============================================================

(defn calibration-history
  "Display calibration history"
  [profile]
  [:div.calibration-history
   [:h2 "Calibration History"]

   [:table.metrics-table
    [:tbody
     [:tr
      [:td.label "Last Calibrated"]
      [:td.value (.toLocaleDateString (:last-calibration-date profile))]]

     [:tr
      [:td.label "Total Calibrations"]
      [:td.value (str (:calibration-count profile))]]]]])

;; ============================================================
;; PROFILE ACTIONS
;; ============================================================

(defn profile-actions
  "Action buttons for profile management"
  []
  [:div.profile-actions
   [:button.btn-primary
    {:on-click #(rf/dispatch [::state/calibration/start-wizard])}
    "Recalibrate"]

   ;; TODO: Implement export and delete
   #_[:button.btn-secondary
    {:on-click #(rf/dispatch [::state/profile/export])}
    "Export Profile"]

   #_[:button.btn-danger
    {:on-click #(when (js/confirm "Delete your profile? This cannot be undone.")
                  (rf/dispatch [::state/profile/delete]))}
    "Delete Profile"]])

;; ============================================================
;; MAIN VIEW
;; ============================================================

(defn profile-view
  "Main profile view component"
  []
  (let [profile (rf/subscribe [::state/user-profile])]
    (fn []
      (if @profile
        [:div.profile-view
         [profile-header @profile]

         [:div.profile-content
          [baseline-metrics @profile]
          [breathing-baseline @profile]
          [posture-baseline @profile]
          [threshold-settings @profile]
          [calibration-history @profile]
          [profile-actions]]]

        ;; No profile state
        [:div.no-profile
         [:h2 "No Profile Found"]
         [:p "You haven't calibrated yet. Calibration creates a personalized baseline for accurate feedback."]
         [:button.btn-primary.btn-large
          {:on-click #(rf/dispatch [::state/calibration/start-wizard])}
          "Start Calibration"]]))))
