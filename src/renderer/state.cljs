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
            [combatsys.posture :as posture]
            [combatsys.calibration :as calibration]
            [combatsys.personalization :as personalization]
            [combatsys.analytics :as analytics]
            [combatsys.comparison :as comparison]
            [combatsys.trends :as trends]
            [combatsys.renderer.persistence :as persist]
            [combatsys.renderer.files :as files]
            [combatsys.renderer.video :as video]
            [combatsys.renderer.magnification :as mag]
            [combatsys.renderer.gpu :as gpu]))

;; ============================================================
;; INITIAL STATE
;; ============================================================

(def initial-state
  {:ui
   {:current-view :live-feed ;; :live-feed | :session-browser | :analysis
    :selected-session-id nil
    :timeline-position-ms 0
    :overlays #{:skeleton :metrics} ;; Which overlays to show
    :analysis-tab :breathing ;; :breathing | :posture (active analysis tab)
    :settings {:fps 30
               :show-debug false}}

   :camera
   {:active? false ;; Is camera stream active?
    :handle nil ;; Camera handle from camera/init-camera!
    :error nil ;; Last camera error
    :fps 0 ;; Current FPS
    :frame-count 0} ;; Total frames captured

   :pose-detector
   {:status :not-initialized ;; :not-initialized | :loading | :ready | :error
    :error nil ;; Last detector error
    :last-pose nil ;; Most recent pose detected
    :pose-count 0} ;; Total poses estimated

   :capture
   {:mode :idle ;; :idle | :recording | :processing
    :current-frame nil
    :buffer [] ;; Last 30 frames for real-time analysis
    :recording-start-time nil}

   :sessions {} ;; Map of session-id → session (in-memory, active session)

   :current-session-id nil

   :saved-sessions
   {:loaded-ids [] ;; List of session IDs available on disk
    :sessions-by-id {}} ;; Map of session-id → lightweight session metadata

   :feedback
   {:cues-queue [] ;; Audio cues to play
    :recent-events [] ;; Last 10 events
    :alerts []}

   :magnification
   {:first-frame nil ;; First frame for ROI selection {:frame Uint8ClampedArray :width :height}
    :roi nil ;; Selected ROI {:x :y :width :height}
    :original-frames nil ;; Vector of decoded frames
    :magnified-frames nil ;; Vector of magnified frames
    :processing? false ;; Is magnification in progress?
    :progress 0.0 ;; Progress 0.0 → 1.0
    :playback
    {:playing? false
     :current-frame-index 0
     :view-mode :side-by-side} ;; :original | :magnified | :side-by-side
    :metadata nil ;; {:width :height :fps :frame-count}
    :error nil}

   :calibration
   {:active? false ;; Is calibration wizard active?
    :current-step-idx nil ;; nil = not started, 0-2 = steps, 3+ = complete
    :user-height-cm nil ;; User-provided height (asked during T-pose)
    :recording? false ;; Is current step recording?
    :seconds-remaining 0 ;; Countdown timer
    :timer-handle nil ;; js/setInterval handle for cleanup
    :sessions [] ;; Collected calibration sessions [{:calibration-type X :timeline [...]}]
    :completed-profile nil ;; Created user profile after all 3 sessions complete
    :error nil}

   :user-profile nil ;; Active user profile (loaded from disk or created via calibration)

   :session-browser
   {:index [] ;; Vector of session metadata (loaded from index.edn)
    :search-text "" ;; Search filter text
    :sort-by :date ;; :date | :duration | :name
    :date-filter :all ;; :all | :last-7-days | :last-30-days | :last-90-days
    :selected-ids []} ;; Vector of selected session IDs (max 2 for comparison)

   :comparison
   {:report nil}}) ;; Comparison report from compare-sessions

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
         user-profile (:user-profile db) ;; Get active user profile for personalization

         ;; Run all analyzers (pure functions)
         ;; Pass user-profile for personalized thresholds and baseline comparison
         analyzed (-> session
                      (breathing/analyze user-profile)
                      (posture/analyze user-profile))]

     (when user-profile
       (println "Using personalized analysis for user:" (:user-id user-profile)))

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

(rf/reg-event-db
 ::set-analysis-tab
 (fn [db [_ tab]]
   (println "Switching analysis tab to:" tab)
   (assoc-in db [:ui :analysis-tab] tab)))

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
;; MEDIAPIPE / POSE DETECTOR EVENTS
;; ============================================================

(rf/reg-event-db
 ::detector-initializing
 (fn [db _]
   (println "MediaPipe detector initializing...")
   (-> db
       (assoc-in [:pose-detector :status] :loading)
       (assoc-in [:pose-detector :error] nil))))

(rf/reg-event-db
 ::detector-ready
 (fn [db _]
   (println "MediaPipe detector ready")
   (-> db
       (assoc-in [:pose-detector :status] :ready)
       (assoc-in [:pose-detector :error] nil))))

(rf/reg-event-db
 ::detector-error
 (fn [db [_ error]]
   (println "MediaPipe detector error:" error)
   (-> db
       (assoc-in [:pose-detector :status] :error)
       (assoc-in [:pose-detector :error] error))))

(rf/reg-event-db
 ::pose-detected
 (fn [db [_ pose]]
   ;; Store last pose and increment counter
   (-> db
       (assoc-in [:pose-detector :last-pose] pose)
       (update-in [:pose-detector :pose-count] inc))))

(rf/reg-event-db
 ::no-pose-detected
 (fn [db _]
   ;; Clear last pose when no person detected
   (assoc-in db [:pose-detector :last-pose] nil)))

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

(rf/reg-sub
 ::analysis-tab
 (fn [db _]
   (get-in db [:ui :analysis-tab] :breathing))) ;; Default to breathing

(rf/reg-sub
 ::available-analysis-tabs
 :<- [::current-session]
 (fn [session _]
   "Return vector of available analysis tabs for current session."
   (when session
     (let [analysis (:session/analysis session)]
       (cond-> []
         (some? (:breathing analysis)) (conj :breathing)
         (some? (:posture analysis)) (conj :posture))))))

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

;; ============================================================
;; POSE DETECTOR SUBSCRIPTIONS
;; ============================================================

(rf/reg-sub
 ::detector-status
 (fn [db _]
   (get-in db [:pose-detector :status])))

(rf/reg-sub
 ::detector-ready?
 (fn [db _]
   (= :ready (get-in db [:pose-detector :status]))))

(rf/reg-sub
 ::detector-error
 (fn [db _]
   (get-in db [:pose-detector :error])))

(rf/reg-sub
 ::last-pose
 (fn [db _]
   (get-in db [:pose-detector :last-pose])))

(rf/reg-sub
 ::pose-count
 (fn [db _]
   (get-in db [:pose-detector :pose-count])))

;; ============================================================
;; SESSION RECORDING EVENTS
;; ============================================================

(rf/reg-event-db
 ::start-session-recording
 (fn [db [_ session-name]]
   (println "Starting session recording:" session-name)
   (let [new-session (persist/create-session (or session-name "Untitled Session"))
         session-id (:session/id new-session)]
     (-> db
         (assoc-in [:capture :mode] :recording)
         (assoc-in [:capture :recording-start-time] (.now js/Date))
         (assoc :current-session-id session-id)
         (assoc-in [:sessions session-id] new-session)))))

(rf/reg-event-db
 ::stop-session-recording
 (fn [db _]
   (println "Stopping session recording...")
   (let [session-id (:current-session-id db)
         session (get-in db [:sessions session-id])
         ;; Finalize session with metadata
         finalized-session (persist/finalize-session session)]
     (-> db
         (assoc-in [:capture :mode] :idle)
         (assoc-in [:capture :recording-start-time] nil)
         (assoc-in [:sessions session-id] finalized-session)))))

(rf/reg-event-db
 ::append-frame-to-recording
 (fn [db [_ frame]]
   (let [session-id (:current-session-id db)
         mode (get-in db [:capture :mode])]
     (if (and (= mode :recording) session-id)
       ;; Append frame to current recording session
       (let [session (get-in db [:sessions session-id])
             updated-session (persist/append-frame-to-session session frame)]
         (assoc-in db [:sessions session-id] updated-session))
       ;; Not recording, just update buffer
       db))))

(rf/reg-event-fx
 ::save-current-session
 (fn [{:keys [db]} _]
   (println "Saving current session...")
   (let [session-id (:current-session-id db)
         session (get-in db [:sessions session-id])]
     (if session
       (let [result (files/save-session-with-index! session)]  ;; LOD 6: Use index-aware save
         (if (:success? result)
           (do
             (println "Session saved successfully:" (:file-path result))
             ;; Reload session index to reflect new session
             {:dispatch [::session-browser/init]})
           (do
             (js/console.error "Failed to save session:" (:error result))
             {})))
       (do
         (js/console.warn "No current session to save")
         {})))))

(rf/reg-event-fx
 ::load-session-from-disk
 (fn [{:keys [db]} [_ session-id]]
   (println "Loading session from disk:" session-id)
   (if-let [session (files/load-session! session-id)]
     (do
       (println "Session loaded successfully:" session-id)
       {:db (-> db
                (assoc-in [:sessions session-id] session)
                (assoc :current-session-id session-id)
                (assoc-in [:ui :current-view] :analysis))})
     (do
       (js/console.error "Failed to load session:" session-id)
       {}))))

(rf/reg-event-fx
 ::load-all-saved-sessions-list
 (fn [{:keys [db]} _]
   (println "Loading saved sessions list...")
   (let [session-ids (files/list-session-ids!)]
     (println "Found" (count session-ids) "saved sessions")
     {:db (assoc-in db [:saved-sessions :loaded-ids] session-ids)})))

(rf/reg-event-fx
 ::delete-session
 (fn [{:keys [db]} [_ session-id]]
   (println "Deleting session:" session-id)
   (let [result (files/delete-session! session-id)]
     (if (:success? result)
       (do
         (println "Session deleted successfully")
         {:db (-> db
                  (update-in [:saved-sessions :loaded-ids]
                             (fn [ids] (filterv #(not= % session-id) ids)))
                  (update-in [:saved-sessions :sessions-by-id]
                             dissoc session-id)
                  (update :sessions dissoc session-id))})
       (do
         (js/console.error "Failed to delete session:" (:error result))
         {})))))

;; ============================================================
;; SESSION RECORDING SUBSCRIPTIONS
;; ============================================================

(rf/reg-sub
 ::recording?
 (fn [db _]
   (= :recording (get-in db [:capture :mode]))))

(rf/reg-sub
 ::recording-duration-ms
 (fn [db _]
   (when-let [start-time (get-in db [:capture :recording-start-time])]
     (- (.now js/Date) start-time))))

(rf/reg-sub
 ::current-recording-frame-count
 (fn [db _]
   (when-let [session-id (:current-session-id db)]
     (get-in db [:sessions session-id :session/frame-count] 0))))

(rf/reg-sub
 ::saved-session-ids
 (fn [db _]
   (get-in db [:saved-sessions :loaded-ids] [])))

(rf/reg-sub
 ::saved-sessions-metadata
 (fn [db _]
   (get-in db [:saved-sessions :sessions-by-id] {})))

;; ============================================================
;; MAGNIFICATION EVENTS
;; ============================================================

(rf/reg-event-fx
 ::magnification/load-first-frame
 (fn [{:keys [db]} [_ session-id]]
   (println "Loading first frame for ROI selection...")
   (let [session (get-in db [:sessions session-id])
         video-path (get-in session [:session/video-path])]
     (if video-path
       ;; Load first frame from video
       (do
         (-> (video/get-first-frame! video-path)
             (.then (fn [result]
                      (rf/dispatch [::magnification/first-frame-loaded result])))
             (.catch (fn [err]
                       (rf/dispatch [::magnification/error (str "Failed to load video: " (.-message err))]))))
         {:db db})
       ;; No video path, use first frame from timeline if available
       (let [first-frame (first (get-in session [:session/timeline]))]
         (if first-frame
           {:db (assoc-in db [:magnification :first-frame]
                         {:frame (:frame/pixels first-frame)
                          :width (:frame/width first-frame)
                          :height (:frame/height first-frame)})}
           {:db (assoc-in db [:magnification :error] "No video or frames available")}))))))

(rf/reg-event-db
 ::magnification/first-frame-loaded
 (fn [db [_ result]]
   (println "First frame loaded:" (:width result) "×" (:height result))
   (assoc-in db [:magnification :first-frame] result)))

(rf/reg-event-db
 ::magnification/roi-selected
 (fn [db [_ roi]]
   (println "ROI selected:" roi)
   (assoc-in db [:magnification :roi] roi)))

(rf/reg-event-fx
 ::magnification/start
 (fn [{:keys [db]} [_ session-id gain blur?]]
   (println "Starting magnification pipeline...")
   (let [session (get-in db [:sessions session-id])
         video-path (get-in session [:session/video-path])
         roi (get-in db [:magnification :roi])]

     (if-not roi
       {:db (assoc-in db [:magnification :error] "No ROI selected")}

       (do
         ;; Step 1: Initialize GPU
         (-> (gpu/init-gpu!)
             (.then (fn [gpu-ctx]
                      ;; Step 2: Decode video
                      (-> (video/decode-video! video-path
                                              {:fps 15
                                               :on-progress #(rf/dispatch [::magnification/update-progress :decode %])})
                          (.then (fn [result]
                                   (let [{:keys [frames width height fps]} result]
                                     (println "Decoded" (count frames) "frames")
                                     ;; Store decoded frames
                                     (rf/dispatch [::magnification/frames-decoded frames width height fps])

                                     ;; Step 3: Magnify frames
                                     (-> (mag/magnify-frames! gpu-ctx frames width height
                                                             {:gain gain
                                                              :blur? blur?})
                                         (.then (fn [magnified-frames]
                                                  (println "Magnification complete!")
                                                  (rf/dispatch [::magnification/complete magnified-frames])
                                                  ;; Cleanup GPU
                                                  (gpu/release-gpu! gpu-ctx)))
                                         (.catch (fn [err]
                                                   (gpu/release-gpu! gpu-ctx)
                                                   (rf/dispatch [::magnification/error
                                                                (str "Magnification failed: " (.-message err))])))))))
                          (.catch (fn [err]
                                    (gpu/release-gpu! gpu-ctx)
                                    (rf/dispatch [::magnification/error
                                                 (str "Video decode failed: " (.-message err))])))))))
             (.catch (fn [err]
                       (rf/dispatch [::magnification/error
                                    (str "GPU init failed: " (.-message err))]))))

         ;; Update state to show processing
         {:db (-> db
                  (assoc-in [:magnification :processing?] true)
                  (assoc-in [:magnification :progress] 0.0)
                  (assoc-in [:magnification :error] nil))})))))

(rf/reg-event-db
 ::magnification/frames-decoded
 (fn [db [_ frames width height fps]]
   (println "Storing" (count frames) "decoded frames")
   (-> db
       (assoc-in [:magnification :original-frames] frames)
       (assoc-in [:magnification :metadata] {:width width
                                             :height height
                                             :fps fps
                                             :frame-count (count frames)})
       (assoc-in [:magnification :progress] 0.5)))) ;; Decode = 50% of work

(rf/reg-event-db
 ::magnification/update-progress
 (fn [db [_ stage progress]]
   (let [;; Decode = 0-50%, Magnify = 50-100%
         total-progress (case stage
                         :decode (* progress 0.5)
                         :magnify (+ 0.5 (* progress 0.5))
                         progress)]
     (assoc-in db [:magnification :progress] total-progress))))

(rf/reg-event-db
 ::magnification/complete
 (fn [db [_ magnified-frames]]
   (println "Magnification complete with" (count magnified-frames) "frames")
   (-> db
       (assoc-in [:magnification :magnified-frames] magnified-frames)
       (assoc-in [:magnification :processing?] false)
       (assoc-in [:magnification :progress] 1.0))))

(rf/reg-event-db
 ::magnification/error
 (fn [db [_ error-msg]]
   (js/console.error "Magnification error:" error-msg)
   (-> db
       (assoc-in [:magnification :error] error-msg)
       (assoc-in [:magnification :processing?] false))))

(rf/reg-event-db
 ::magnification/toggle-playback
 (fn [db _]
   (update-in db [:magnification :playback :playing?] not)))

(rf/reg-event-db
 ::magnification/seek-frame
 (fn [db [_ frame-index]]
   (assoc-in db [:magnification :playback :current-frame-index] frame-index)))

(rf/reg-event-db
 ::magnification/set-view-mode
 (fn [db [_ view-mode]]
   (assoc-in db [:magnification :playback :view-mode] view-mode)))

(rf/reg-event-db
 ::magnification/reset
 (fn [db _]
   (assoc db :magnification
          {:first-frame nil
           :roi nil
           :original-frames nil
           :magnified-frames nil
           :processing? false
           :progress 0.0
           :playback {:playing? false
                     :current-frame-index 0
                     :view-mode :side-by-side}
           :metadata nil
           :error nil})))

;; ============================================================
;; MAGNIFICATION SUBSCRIPTIONS
;; ============================================================

(rf/reg-sub
 ::magnification/first-frame
 (fn [db _]
   (get-in db [:magnification :first-frame])))

(rf/reg-sub
 ::magnification/roi
 (fn [db _]
   (get-in db [:magnification :roi])))

(rf/reg-sub
 ::magnification/original-frames
 (fn [db _]
   (get-in db [:magnification :original-frames])))

(rf/reg-sub
 ::magnification/magnified-frames
 (fn [db _]
   (get-in db [:magnification :magnified-frames])))

(rf/reg-sub
 ::magnification/processing?
 (fn [db _]
   (get-in db [:magnification :processing?])))

(rf/reg-sub
 ::magnification/progress
 (fn [db _]
   (get-in db [:magnification :progress])))

(rf/reg-sub
 ::magnification/playback-state
 (fn [db _]
   (get-in db [:magnification :playback])))

(rf/reg-sub
 ::magnification/metadata
 (fn [db _]
   (get-in db [:magnification :metadata])))

(rf/reg-sub
 ::magnification/error
 (fn [db _]
   (get-in db [:magnification :error])))

(rf/reg-sub
 ::magnification/current-frame
 :<- [::magnification/playback-state]
 :<- [::magnification/original-frames]
 :<- [::magnification/magnified-frames]
 (fn [[playback original-frames magnified-frames] _]
   (let [{:keys [current-frame-index view-mode]} playback]
     (when (and original-frames magnified-frames)
       {:original (nth original-frames current-frame-index nil)
        :magnified (nth magnified-frames current-frame-index nil)
        :view-mode view-mode
        :index current-frame-index}))))

;; ============================================================
;; CALIBRATION WIZARD EVENTS
;; ============================================================

(rf/reg-event-db
 ::calibration/start-wizard
 (fn [db _]
   (println "Starting calibration wizard...")
   (-> db
       (assoc-in [:calibration :active?] true)
       (assoc-in [:calibration :current-step-idx] 0)
       (assoc-in [:calibration :sessions] [])
       (assoc-in [:calibration :completed-profile] nil)
       (assoc-in [:calibration :error] nil)
       (assoc-in [:ui :current-view] :calibration))))

(rf/reg-event-db
 ::calibration/set-user-height
 (fn [db [_ height-cm]]
   (println "Setting user height:" height-cm "cm")
   (assoc-in db [:calibration :user-height-cm] height-cm)))

(rf/reg-event-db
 ::calibration/start-step
 (fn [db [_ step-idx]]
   (println "Starting calibration step" step-idx)
   (let [step-types [:t-pose :breathing :movement]
         step-type (nth step-types step-idx)
         duration-s (case step-type
                      :t-pose 10
                      :breathing 60
                      :movement 60)
         session-id (random-uuid)
         ;; Create new session for this calibration step
         new-session {:session/id session-id
                      :session/name (str "Calibration - " (name step-type))
                      :session/created-at (js/Date.)
                      :session/timeline []
                      :session/status :recording}]
     (-> db
         (assoc-in [:calibration :current-step-idx] step-idx)
         (assoc-in [:calibration :recording?] true)
         (assoc-in [:calibration :seconds-remaining] duration-s)
         ;; Create new calibration session
         (assoc :current-session-id session-id)
         (assoc-in [:sessions session-id] new-session)
         (assoc-in [:capture :mode] :recording)
         (assoc-in [:capture :recording-start-time] (.now js/Date))))))

(rf/reg-event-db
 ::calibration/tick
 (fn [db _]
   (let [seconds (get-in db [:calibration :seconds-remaining])]
     (if (<= seconds 1)
       ;; Time's up - stop recording and complete step
       (let [session-id (:current-session-id db)
             session (get-in db [:sessions session-id])
             timeline (:session/timeline session)
             step-idx (get-in db [:calibration :current-step-idx])
             step-types [:t-pose :breathing :movement]
             step-type (nth step-types step-idx)

             ;; Create calibration session
             cal-session {:calibration-type step-type
                         :session-id session-id
                         :created-at (js/Date.)
                         :duration-ms (* (case step-type
                                          :t-pose 10
                                          :breathing 60
                                          :movement 60) 1000)
                         :timeline timeline}

             ;; Add to sessions list
             sessions (conj (get-in db [:calibration :sessions]) cal-session)

             ;; Check if all 3 sessions complete
             all-complete? (= 3 (count sessions))]

         (-> db
             (assoc-in [:calibration :recording?] false)
             (assoc-in [:calibration :seconds-remaining] 0)
             (assoc-in [:calibration :sessions] sessions)
             (assoc-in [:capture :mode] :idle)
             ;; If all complete, create profile
             (cond->
               all-complete?
               (assoc-in [:calibration :completed-profile]
                        (personalization/create-user-profile
                         (random-uuid)
                         sessions
                         (get-in db [:calibration :user-height-cm]))))))
       ;; Decrement counter
       (update-in db [:calibration :seconds-remaining] dec)))))

(rf/reg-event-db
 ::calibration/complete-step
 (fn [db _]
   (println "Completing current calibration step...")
   (let [step-idx (get-in db [:calibration :current-step-idx])]
     (if (< step-idx 2)
       ;; Advance to next step
       (-> db
           (assoc-in [:calibration :current-step-idx] (inc step-idx))
           (assoc-in [:calibration :recording?] false))
       ;; All steps complete - show completion screen
       (-> db
           (assoc-in [:calibration :current-step-idx] 3)
           (assoc-in [:calibration :recording?] false))))))

(rf/reg-event-db
 ::calibration/skip-wizard
 (fn [db _]
   (println "Skipping calibration wizard...")
   (-> db
       (assoc-in [:calibration :active?] false)
       (assoc-in [:calibration :current-step-idx] nil)
       (assoc-in [:calibration :sessions] [])
       (assoc :user-profile nil) ;; No profile, use generic thresholds
       (assoc-in [:ui :current-view] :live-feed))))

(rf/reg-event-db
 ::calibration/cancel-wizard
 (fn [db _]
   (println "Canceling calibration wizard...")
   (-> db
       (assoc-in [:calibration :active?] false)
       (assoc-in [:calibration :current-step-idx] nil)
       (assoc-in [:calibration :recording?] false)
       (assoc-in [:calibration :sessions] [])
       (assoc-in [:capture :mode] :idle)
       (assoc-in [:ui :current-view] :live-feed))))

(rf/reg-event-fx
 ::calibration/finish
 (fn [{:keys [db]} _]
   (println "Finishing calibration wizard...")
   (let [profile (get-in db [:calibration :completed-profile])]
     {:db (-> db
              (assoc :user-profile profile)
              (assoc-in [:calibration :active?] false)
              (assoc-in [:calibration :current-step-idx] nil)
              (assoc-in [:ui :current-view] :live-feed))
      :dispatch [::user-profile/save profile]})))

;; ============================================================
;; USER PROFILE EVENTS
;; ============================================================

(rf/reg-event-db
 ::user-profile/loaded
 (fn [db [_ profile]]
   (println "User profile loaded:" (:user-id profile))
   (assoc db :user-profile profile)))

(rf/reg-event-fx
 ::user-profile/save
 (fn [{:keys [db]} [_ profile]]
   (println "Saving user profile to disk...")
   (let [result (files/save-user-profile! profile)]
     (if (:success? result)
       (do
         (println "Profile saved successfully:" (:file-path result))
         {:db db})
       (do
         (js/console.error "Failed to save profile:" (:error result))
         {:db (assoc-in db [:calibration :error]
                       (str "Failed to save profile: " (:error result)))})))))

;; ============================================================
;; CALIBRATION WIZARD SUBSCRIPTIONS
;; ============================================================

(rf/reg-sub
 ::calibration/active?
 (fn [db _]
   (get-in db [:calibration :active?])))

(rf/reg-sub
 ::calibration/current-step-idx
 (fn [db _]
   (get-in db [:calibration :current-step-idx])))

(rf/reg-sub
 ::calibration/user-height-cm
 (fn [db _]
   (get-in db [:calibration :user-height-cm])))

(rf/reg-sub
 ::calibration/recording?
 (fn [db _]
   (get-in db [:calibration :recording?])))

(rf/reg-sub
 ::calibration/seconds-remaining
 (fn [db _]
   (get-in db [:calibration :seconds-remaining])))

(rf/reg-sub
 ::calibration/sessions
 (fn [db _]
   (get-in db [:calibration :sessions])))

(rf/reg-sub
 ::calibration/completed-profile
 (fn [db _]
   (get-in db [:calibration :completed-profile])))

(rf/reg-sub
 ::calibration/error
 (fn [db _]
   (get-in db [:calibration :error])))

(rf/reg-sub
 ::user-profile
 (fn [db _]
   (:user-profile db)))

;; ============================================================
;; SESSION BROWSER EVENTS (LOD 6)
;; ============================================================

(rf/reg-event-db
 ::session-browser/init
 (fn [db _]
   (println "Initializing session browser...")
   ;; Load session index from disk
   (let [index (files/load-session-index!)]
     (println "Loaded session index:" (count index) "sessions")
     (assoc-in db [:session-browser :index] index))))

(rf/reg-event-db
 ::session-browser/set-search
 (fn [db [_ search-text]]
   (assoc-in db [:session-browser :search-text] search-text)))

(rf/reg-event-db
 ::session-browser/set-sort
 (fn [db [_ sort-key]]
   (assoc-in db [:session-browser :sort-by] sort-key)))

(rf/reg-event-db
 ::session-browser/set-date-filter
 (fn [db [_ filter-key]]
   (assoc-in db [:session-browser :date-filter] filter-key)))

(rf/reg-event-db
 ::session-browser/toggle-selection
 (fn [db [_ session-id]]
   (let [selected-ids (get-in db [:session-browser :selected-ids])
         already-selected? (some #(= % session-id) selected-ids)
         updated-ids (if already-selected?
                       (filterv #(not= % session-id) selected-ids)
                       (conj selected-ids session-id))]
     ;; Limit to 2 selections for comparison
     (assoc-in db [:session-browser :selected-ids]
               (vec (take 2 updated-ids))))))

(rf/reg-event-db
 ::session-browser/clear-selection
 (fn [db _]
   (assoc-in db [:session-browser :selected-ids] [])))

(rf/reg-event-fx
 ::session-browser/delete-session
 (fn [{:keys [db]} [_ session-id]]
   (println "Deleting session from browser:" session-id)
   ;; Delete session file and update index
   (let [result (files/delete-session-with-index! session-id)]
     (if (:success? result)
       (do
         (println "Session deleted successfully")
         ;; Reload index to reflect deletion
         {:dispatch [::session-browser/init]})
       (do
         (js/console.error "Failed to delete session:" (:error result))
         {})))))

;; ============================================================
;; SESSION BROWSER SUBSCRIPTIONS (LOD 6)
;; ============================================================

(rf/reg-sub
 ::session-browser/index
 (fn [db _]
   (get-in db [:session-browser :index])))

(rf/reg-sub
 ::session-browser/search-text
 (fn [db _]
   (get-in db [:session-browser :search-text])))

(rf/reg-sub
 ::session-browser/sort-by
 (fn [db _]
   (get-in db [:session-browser :sort-by])))

(rf/reg-sub
 ::session-browser/date-filter
 (fn [db _]
   (get-in db [:session-browser :date-filter])))

(rf/reg-sub
 ::session-browser/selected-ids
 (fn [db _]
   (get-in db [:session-browser :selected-ids])))

(rf/reg-sub
 ::session-browser/selected-count
 :<- [::session-browser/selected-ids]
 (fn [selected-ids _]
   (count selected-ids)))

(rf/reg-sub
 ::session-browser/total-count
 :<- [::session-browser/index]
 (fn [index _]
   (count index)))

(rf/reg-sub
 ::session-browser/filtered-sessions
 (fn [db _]
   (let [index (get-in db [:session-browser :index])
         search-text (get-in db [:session-browser :search-text])
         sort-by (get-in db [:session-browser :sort-by])
         date-filter (get-in db [:session-browser :date-filter])

         ;; Apply date filter
         date-filtered (case date-filter
                         :last-7-days (analytics/filter-sessions-by-date-range
                                       index
                                       (analytics/get-date-n-days-ago 7)
                                       nil)
                         :last-30-days (analytics/filter-sessions-by-date-range
                                        index
                                        (analytics/get-date-n-days-ago 30)
                                        nil)
                         :last-90-days (analytics/filter-sessions-by-date-range
                                        index
                                        (analytics/get-date-n-days-ago 90)
                                        nil)
                         :all index
                         index)

         ;; Apply search filter
         search-filtered (analytics/filter-sessions-by-search
                          date-filtered
                          search-text)

         ;; Apply sort
         sorted (analytics/sort-sessions search-filtered sort-by false)]

     sorted)))

(rf/reg-sub
 ::session-browser/aggregate-stats
 :<- [::session-browser/index]
 (fn [index _]
   (when (seq index)
     (analytics/compute-aggregate-stats index))))

;; ============================================================
;; COMPARISON EVENTS (LOD 6)
;; ============================================================

(rf/reg-event-fx
 ::comparison/compare-selected-sessions
 (fn [{:keys [db]} _]
   (println "Comparing selected sessions...")
   (let [selected-ids (get-in db [:session-browser :selected-ids])
         session-a-id (first selected-ids)
         session-b-id (second selected-ids)]

     (if (and session-a-id session-b-id)
       ;; Load both sessions (from memory or disk)
       (let [session-a (or (get-in db [:sessions session-a-id])
                           (files/load-session! session-a-id))
             session-b (or (get-in db [:sessions session-b-id])
                           (files/load-session! session-b-id))]

         (if (and session-a session-b)
           (let [comparison-report (comparison/compare-sessions session-a session-b)]
             (println "Comparison complete:" (:overall-assessment comparison-report))
             {:db (-> db
                      (assoc-in [:comparison :report] comparison-report)
                      (assoc-in [:sessions session-a-id] session-a)
                      (assoc-in [:sessions session-b-id] session-b))
              :dispatch [::set-view :comparison]})
           (do
             (js/console.error "Failed to load sessions for comparison")
             {})))
       (do
         (js/console.warn "Need exactly 2 sessions selected for comparison")
         {})))))

;; ============================================================
;; COMPARISON SUBSCRIPTIONS (LOD 6)
;; ============================================================

(rf/reg-sub
 ::comparison/report
 (fn [db _]
   (get-in db [:comparison :report])))

;; ============================================================
;; ANALYTICS / TREND ANALYSIS SUBSCRIPTIONS (LOD 6)
;; ============================================================

(rf/reg-sub
 ::analytics/trend-analysis
 :<- [::session-browser/index]
 (fn [session-index _]
   "Compute trend analysis from session index.

   Note: We need to load full sessions to get analysis data.
   For now, we'll compute trends from metadata only (summary stats).

   Future enhancement: Load only analysis data from sessions (not full timeline)."
   (when (>= (count session-index) 3)
     ;; For trend analysis, we need sessions sorted by date (oldest to newest)
     (let [sorted-sessions (sort-by :session/created-at session-index)

           ;; Extract metric values from metadata
           breathing-rate-values (keep #(get-in % [:session/summary-stats :avg-breathing-rate])
                                       sorted-sessions)
           breathing-depth-values (keep #(get-in % [:session/summary-stats :avg-breathing-depth])
                                        sorted-sessions)
           posture-score-values (keep #(get-in % [:session/summary-stats :avg-posture-score])
                                      sorted-sessions)
           forward-head-values (keep #(get-in % [:session/summary-stats :forward-head-cm])
                                     sorted-sessions)

           timestamps (map :session/created-at sorted-sessions)]

       ;; Compute regression for each metric
       {:session-count (count sorted-sessions)
        :date-range {:start-date (:session/created-at (first sorted-sessions))
                     :end-date (:session/created-at (last sorted-sessions))}
        :trends (cond-> {}
                  (seq breathing-rate-values)
                  (assoc :breathing-rate
                         (let [regression (trends/fit-linear-regression breathing-rate-values)
                               slope-threshold 0.05
                               direction (cond
                                          (> (:m regression) slope-threshold) :improving
                                          (< (:m regression) (- slope-threshold)) :declining
                                          :else :stable)]
                           {:metric-name :rate-bpm
                            :values (vec breathing-rate-values)
                            :timestamps (vec timestamps)
                            :trend-direction direction
                            :slope (:m regression)
                            :intercept (:b regression)
                            :r2 (:r2 regression)}))

                  (seq breathing-depth-values)
                  (assoc :breathing-depth
                         (let [regression (trends/fit-linear-regression breathing-depth-values)
                               slope-threshold 0.05
                               direction (cond
                                          (> (:m regression) slope-threshold) :improving
                                          (< (:m regression) (- slope-threshold)) :declining
                                          :else :stable)]
                           {:metric-name :depth-score
                            :values (vec breathing-depth-values)
                            :timestamps (vec timestamps)
                            :trend-direction direction
                            :slope (:m regression)
                            :intercept (:b regression)
                            :r2 (:r2 regression)}))

                  (seq posture-score-values)
                  (assoc :posture-score
                         (let [regression (trends/fit-linear-regression posture-score-values)
                               slope-threshold 0.05
                               direction (cond
                                          (> (:m regression) slope-threshold) :improving
                                          (< (:m regression) (- slope-threshold)) :declining
                                          :else :stable)]
                           {:metric-name :overall-score
                            :values (vec posture-score-values)
                            :timestamps (vec timestamps)
                            :trend-direction direction
                            :slope (:m regression)
                            :intercept (:b regression)
                            :r2 (:r2 regression)}))

                  (seq forward-head-values)
                  (assoc :forward-head
                         (let [regression (trends/fit-linear-regression forward-head-values)
                               slope-threshold 0.05
                               ;; Note: For forward head, lower is better, so flip the direction
                               direction (cond
                                          (< (:m regression) (- slope-threshold)) :improving
                                          (> (:m regression) slope-threshold) :declining
                                          :else :stable)]
                           {:metric-name :head-forward-cm
                            :values (vec forward-head-values)
                            :timestamps (vec timestamps)
                            :trend-direction direction
                            :slope (:m regression)
                            :intercept (:b regression)
                            :r2 (:r2 regression)})))}))))
