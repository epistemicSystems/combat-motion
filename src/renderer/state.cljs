(ns combatsys.renderer.state
  "Centralized state management using re-frame.
   
   Philosophy (Game Loop Pattern):
   - Single app-state atom
   - Pure event handlers (state → state)
   - Side effects isolated to event handlers
   
   This is the 'imperative shell' around our functional core."
  (:require [re-frame.core :as rf]
            [combatsys.schema :as schema]
            [combatsys.mocks :as mocks]
            [combatsys.breathing :as breathing]
            [combatsys.posture :as posture]))

;; ============================================================
;; INITIAL STATE
;; ============================================================

(def initial-state
  {:ui
   {:current-view :live-feed ;; :live-feed | :session-browser | :analysis
    :selected-session-id nil
    :timeline-position-ms 0
    :overlays #{:skeleton :metrics} ;; Which overlays to show
    :settings {:fps 30
               :show-debug false}}

   :camera
   {:active? false ;; Is camera stream active?
    :handle nil ;; Camera handle from camera/init-camera!
    :error nil ;; Last camera error
    :fps 0 ;; Current FPS
    :frame-count 0} ;; Total frames captured

   :capture
   {:mode :idle ;; :idle | :recording | :processing
    :current-frame nil
    :buffer [] ;; Last 30 frames for real-time analysis
    :recording-start-time nil}

   :sessions {} ;; Map of session-id → session

   :current-session-id nil

   :feedback
   {:cues-queue [] ;; Audio cues to play
    :recent-events [] ;; Last 10 events
    :alerts []}})

;; ============================================================
;; EVENTS (State updates - all pure functions)
;; ============================================================

(rf/reg-event-db
 ::initialize
 (fn [_ _]
   (println "Initializing app state...")
   initial-state))

(rf/reg-event-db
 ::load-demo-session
 (fn [db _]
   (println "Loading demo session...")
   (let [demo-session mocks/demo-session
         session-id (:session/id demo-session)]
     (-> db
         (assoc-in [:sessions session-id] demo-session)
         (assoc :current-session-id session-id)
         (assoc-in [:ui :current-view] :analysis)))))

(rf/reg-event-db
 ::start-recording
 (fn [db _]
   (println "Starting recording...")
   (let [user-id (random-uuid)
         new-session (schema/new-session user-id)
         session-id (:session/id new-session)]
     (-> db
         (assoc-in [:capture :mode] :recording)
         (assoc-in [:capture :recording-start-time] (.now js/Date))
         (assoc :current-session-id session-id)
         (assoc-in [:sessions session-id] 
                   (assoc new-session :session/status :recording))))))

(rf/reg-event-db
 ::stop-recording
 (fn [db _]
   (println "Stopping recording...")
   (let [session-id (:current-session-id db)]
     (-> db
         (assoc-in [:capture :mode] :idle)
         (assoc-in [:sessions session-id :session/status] :complete)))))

(rf/reg-event-db
 ::append-frame
 (fn [db [_ frame]]
   (let [session-id (:current-session-id db)]
     (-> db
         (update-in [:sessions session-id :session/timeline] conj frame)
         (assoc-in [:capture :current-frame] frame)
         (update-in [:capture :buffer] #(take-last 30 (conj % frame)))))))

(rf/reg-event-db
 ::analyze-session
 (fn [db [_ session-id]]
   (println "Analyzing session" session-id "...")
   (let [session (get-in db [:sessions session-id])
         
         ;; Run all analyzers (pure functions)
         analyzed (-> session
                      breathing/analyze
                      posture/analyze)]
     
     ;; Update session with analysis results
     (assoc-in db [:sessions session-id] analyzed))))

(rf/reg-event-db
 ::set-view
 (fn [db [_ view]]
   (assoc-in db [:ui :current-view] view)))

(rf/reg-event-db
 ::select-session
 (fn [db [_ session-id]]
   (-> db
       (assoc-in [:ui :selected-session-id] session-id)
       (assoc :current-session-id session-id))))

(rf/reg-event-db
 ::set-timeline-position
 (fn [db [_ position-ms]]
   (assoc-in db [:ui :timeline-position-ms] position-ms)))

(rf/reg-event-db
 ::toggle-overlay
 (fn [db [_ overlay-key]]
   (update-in db [:ui :overlays]
              (fn [overlays]
                (if (contains? overlays overlay-key)
                  (disj overlays overlay-key)
                  (conj overlays overlay-key))))))

;; ============================================================
;; CAMERA EVENTS
;; ============================================================

(rf/reg-event-db
 ::camera-started
 (fn [db [_ camera-handle]]
   (println "Camera started:" camera-handle)
   (-> db
       (assoc-in [:camera :active?] true)
       (assoc-in [:camera :handle] camera-handle)
       (assoc-in [:camera :error] nil))))

(rf/reg-event-db
 ::camera-stopped
 (fn [db _]
   (println "Camera stopped")
   (-> db
       (assoc-in [:camera :active?] false)
       (assoc-in [:camera :handle] nil)
       (assoc-in [:camera :fps] 0)
       (assoc-in [:camera :frame-count] 0))))

(rf/reg-event-db
 ::camera-error
 (fn [db [_ error]]
   (println "Camera error:" error)
   (-> db
       (assoc-in [:camera :active?] false)
       (assoc-in [:camera :error] error))))

(rf/reg-event-db
 ::camera-frame-captured
 (fn [db [_ frame]]
   ;; Update frame count and current frame
   ;; Note: We don't store the full frame data (too large)
   ;; Just track that we captured it
   (let [frame-count (inc (get-in db [:camera :frame-count]))]
     (-> db
         (assoc-in [:camera :frame-count] frame-count)
         (assoc-in [:capture :current-frame]
                   {:timestamp-ms (:timestamp-ms frame)
                    :frame-index frame-count})))))

(rf/reg-event-db
 ::camera-update-fps
 (fn [db [_ fps]]
   (assoc-in db [:camera :fps] fps)))

;; ============================================================
;; SUBSCRIPTIONS (Read from state)
;; ============================================================

(rf/reg-sub
 ::current-view
 (fn [db _]
   (get-in db [:ui :current-view])))

(rf/reg-sub
 ::current-session
 (fn [db _]
   (when-let [session-id (:current-session-id db)]
     (get-in db [:sessions session-id]))))

(rf/reg-sub
 ::all-sessions
 (fn [db _]
   (vals (:sessions db))))

(rf/reg-sub
 ::capture-mode
 (fn [db _]
   (get-in db [:capture :mode])))

(rf/reg-sub
 ::current-frame
 (fn [db _]
   (get-in db [:capture :current-frame])))

(rf/reg-sub
 ::timeline-position
 (fn [db _]
   (get-in db [:ui :timeline-position-ms])))

(rf/reg-sub
 ::overlays
 (fn [db _]
   (get-in db [:ui :overlays])))

(rf/reg-sub
 ::recent-events
 (fn [db _]
   (get-in db [:feedback :recent-events])))

;; ============================================================
;; DERIVED SUBSCRIPTIONS (Computed from other subs)
;; ============================================================

(rf/reg-sub
 ::current-frame-at-position
 :<- [::current-session]
 :<- [::timeline-position]
 (fn [[session position-ms] _]
   (when session
     (let [timeline (:session/timeline session)]
       ;; Find frame closest to position
       (first (filter #(<= (:frame/timestamp-ms %) position-ms) timeline))))))

(rf/reg-sub
 ::breathing-analysis
 :<- [::current-session]
 (fn [session _]
   (get-in session [:session/analysis :breathing])))

(rf/reg-sub
 ::posture-analysis
 :<- [::current-session]
 (fn [session _]
   (get-in session [:session/analysis :posture])))

;; ============================================================
;; CAMERA SUBSCRIPTIONS
;; ============================================================

(rf/reg-sub
 ::camera-active?
 (fn [db _]
   (get-in db [:camera :active?])))

(rf/reg-sub
 ::camera-handle
 (fn [db _]
   (get-in db [:camera :handle])))

(rf/reg-sub
 ::camera-error
 (fn [db _]
   (get-in db [:camera :error])))

(rf/reg-sub
 ::camera-fps
 (fn [db _]
   (get-in db [:camera :fps])))

(rf/reg-sub
 ::camera-frame-count
 (fn [db _]
   (get-in db [:camera :frame-count])))
