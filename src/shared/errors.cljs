(ns combatsys.shared.errors
  "Error handling and graceful degradation utilities.

  Philosophy (Rich Hickey):
  - Errors are data, not exceptions
  - Make errors explicit and actionable
  - Provide context for debugging

  User Experience (Brett Victor):
  - Error messages guide users to solutions
  - Show what went wrong AND how to fix it
  - Never crash - degrade gracefully

  Error Structure:
  {:error/type :camera-unavailable
   :error/severity :critical | :warning | :info
   :error/message \"Camera disconnected\"
   :error/suggestion \"Check camera connection and try again\"
   :error/timestamp 1234567890
   :error/context {...}}  ;; Additional debugging info"
  (:require [clojure.string :as str]))

;; ============================================================
;; ERROR TYPES
;; ============================================================

(def error-types
  "Enumeration of all error types in the system."
  {;; Camera errors
   :camera-not-found {:severity :critical
                      :message "No camera detected"
                      :suggestion "Connect a webcam or enable camera permissions"}
   :camera-permission-denied {:severity :critical
                              :message "Camera permission denied"
                              :suggestion "Grant camera permission in browser/system settings"}
   :camera-disconnected {:severity :critical
                         :message "Camera disconnected during recording"
                         :suggestion "Reconnect camera and try again"}
   :camera-busy {:severity :warning
                 :message "Camera in use by another application"
                 :suggestion "Close other apps using the camera"}

   ;; MediaPipe / Pose estimation errors
   :pose-model-load-failed {:severity :critical
                            :message "Failed to load pose detection model"
                            :suggestion "Check internet connection and reload"}
   :pose-detection-failed {:severity :warning
                           :message "No person detected in frame"
                           :suggestion "Position yourself in camera view"}
   :pose-low-confidence {:severity :warning
                         :message "Pose detection confidence low"
                         :suggestion "Improve lighting and remove obstructions"}

   ;; Analysis errors
   :analysis-insufficient-data {:severity :warning
                                :message "Insufficient data for analysis"
                                :suggestion "Record longer session (minimum 30 seconds)"}
   :analysis-failed {:severity :warning
                     :message "Analysis failed to complete"
                     :suggestion "Try analyzing again or contact support"}
   :breathing-detection-failed {:severity :warning
                                :message "Could not detect breathing rate"
                                :suggestion "Ensure upper body is visible in camera"}

   ;; File I/O errors
   :session-save-failed {:severity :critical
                         :message "Failed to save session"
                         :suggestion "Check disk space and permissions"}
   :session-load-failed {:severity :warning
                         :message "Failed to load session"
                         :suggestion "File may be corrupted - try another session"}
   :session-not-found {:severity :warning
                       :message "Session not found"
                       :suggestion "Session may have been deleted"}

   ;; GPU / Performance errors
   :gpu-unavailable {:severity :warning
                     :message "GPU acceleration unavailable"
                     :suggestion "Magnification will use CPU (slower)"}
   :out-of-memory {:severity :critical
                   :message "System out of memory"
                   :suggestion "Close other applications and try again"}
   :performance-degraded {:severity :info
                          :message "Performance below target"
                          :suggestion "Close other apps or reduce quality settings"}

   ;; Network errors
   :network-error {:severity :warning
                   :message "Network error"
                   :suggestion "Check internet connection"}
   :cloud-sync-failed {:severity :warning
                       :message "Cloud sync failed"
                       :suggestion "Your data is safe locally - will retry later"}

   ;; Generic
   :unknown-error {:severity :warning
                   :message "An unexpected error occurred"
                   :suggestion "Please try again or contact support"}})

;; ============================================================
;; ERROR CREATION
;; ============================================================

(defn create-error
  "Create standardized error map.

  Args:
    error-type: Keyword from error-types
    context: (optional) Map with additional debugging info

  Returns:
    Error map with :error/ namespaced keys

  Example:
    (create-error :camera-not-found {:attempted-device \"cam-0\"})"
  ([error-type]
   (create-error error-type {}))
  ([error-type context]
   (let [error-def (get error-types error-type
                        (get error-types :unknown-error))]
     (merge
      {:error/type error-type
       :error/severity (:severity error-def)
       :error/message (:message error-def)
       :error/suggestion (:suggestion error-def)
       :error/timestamp (js/Date.now)}
      (when (seq context)
        {:error/context context})))))

(defn create-custom-error
  "Create error with custom message and suggestion.

  Args:
    error-type: Keyword (can be ad-hoc)
    severity: :critical | :warning | :info
    message: Error message
    suggestion: Suggested action
    context: (optional) Additional context

  Returns:
    Error map

  Example:
    (create-custom-error :calibration-timeout
                         :warning
                         \"Calibration timed out\"
                         \"Try calibration again\"
                         {:duration-ms 60000})"
  ([error-type severity message suggestion]
   (create-custom-error error-type severity message suggestion {}))
  ([error-type severity message suggestion context]
   (merge
    {:error/type error-type
     :error/severity severity
     :error/message message
     :error/suggestion suggestion
     :error/timestamp (js/Date.now)}
    (when (seq context)
      {:error/context context}))))

;; ============================================================
;; ERROR HANDLING WRAPPERS
;; ============================================================

(defn safe-execute
  "Execute function with error handling.

  Returns [result error]:
  - On success: [result nil]
  - On error: [nil error-map]

  Args:
    f: Function to execute
    error-type: Error type to create on failure
    context: (optional) Context map

  Example:
    (let [[result error] (safe-execute
                           #(js/JSON.parse invalid-json)
                           :json-parse-failed
                           {:input invalid-json})]
      (if error
        (handle-error error)
        (process result)))"
  ([f error-type]
   (safe-execute f error-type {}))
  ([f error-type context]
   (try
     [(f) nil]
     (catch js/Error e
       (let [error-context (merge context
                                  {:exception-message (.-message e)
                                   :exception-stack (.-stack e)})]
         [nil (create-error error-type error-context)])))))

(defn safe-execute-with-fallback
  "Execute function with fallback value on error.

  Args:
    f: Function to execute
    fallback: Value to return on error
    error-type: Error type for logging
    on-error: (optional) Callback for error handling

  Returns:
    Result of f on success, fallback on error

  Side effect:
    Calls on-error callback if provided

  Example:
    (safe-execute-with-fallback
      #(analyze-breathing timeline)
      {:rate-bpm nil :error true}
      :breathing-detection-failed
      (fn [error] (log-error error)))"
  ([f fallback error-type]
   (safe-execute-with-fallback f fallback error-type nil))
  ([f fallback error-type on-error]
   (try
     (f)
     (catch js/Error e
       (let [error (create-error error-type
                                {:exception-message (.-message e)})]
         (when on-error
           (on-error error))
         fallback)))))

;; ============================================================
;; ERROR RECOVERY STRATEGIES
;; ============================================================

(defn retry-with-backoff
  "Retry operation with exponential backoff.

  Args:
    f: Async function returning promise
    max-attempts: Maximum retry attempts (default 3)
    initial-delay-ms: Initial delay in ms (default 1000)

  Returns:
    Promise that resolves with result or rejects after max attempts

  Example:
    (retry-with-backoff
      #(fetch-data-from-api)
      3     ;; max 3 attempts
      2000) ;; start with 2s delay"
  ([f]
   (retry-with-backoff f 3 1000))
  ([f max-attempts initial-delay-ms]
   (letfn [(attempt [n delay]
             (-> (f)
                 (.catch (fn [error]
                          (if (< n max-attempts)
                            (js/Promise.
                             (fn [resolve reject]
                               (js/console.warn
                                (str "Attempt " n " failed, retrying in " delay "ms..."))
                               (js/setTimeout
                                (fn []
                                  (-> (attempt (inc n) (* delay 2))
                                      (.then resolve)
                                      (.catch reject)))
                                delay)))
                            (do
                              (js/console.error
                               (str "All " max-attempts " attempts failed"))
                              (js/Promise.reject error)))))))]
     (attempt 1 initial-delay-ms))))

;; ============================================================
;; ERROR LOGGING
;; ============================================================

(defonce error-log
  "Atom storing recent errors for debugging."
  (atom []))

(defn log-error!
  "Log error to console and error-log atom.

  Side effects:
  - Logs to console (error, warn, or info)
  - Appends to error-log atom

  Args:
    error: Error map from create-error

  Returns:
    error (passthrough)"
  [error]
  (let [severity (:error/severity error)
        message (:error/message error)
        log-fn (case severity
                 :critical js/console.error
                 :warning js/console.warn
                 :info js/console.info
                 js/console.log)]

    ;; Log to console with formatting
    (log-fn (str "[" (name severity) "] " message))
    (when-let [suggestion (:error/suggestion error)]
      (log-fn (str "  → " suggestion)))
    (when-let [context (:error/context error)]
      (js/console.log "  Context:" (clj->js context)))

    ;; Append to error log (keep last 100 errors)
    (swap! error-log
           (fn [log]
             (let [updated (conj log error)]
               (vec (take-last 100 updated)))))

    ;; Return error for chaining
    error))

(defn get-recent-errors
  "Get N most recent errors.

  Args:
    n: Number of errors to return (default 10)
    severity-filter: (optional) Only return errors of this severity

  Returns:
    Vector of error maps"
  ([n]
   (get-recent-errors n nil))
  ([n severity-filter]
   (let [errors (if severity-filter
                  (filter #(= (:error/severity %) severity-filter) @error-log)
                  @error-log)]
     (vec (take-last n errors)))))

(defn clear-error-log!
  "Clear error log.

  Side effect: Resets error-log atom"
  []
  (reset! error-log []))

;; ============================================================
;; USER-FACING ERROR MESSAGES
;; ============================================================

(defn format-error-for-ui
  "Format error for display in UI.

  Args:
    error: Error map

  Returns:
    Map with :title :message :actions

  Example:
    {:title \"Camera Error\"
     :message \"Camera disconnected during recording\"
     :actions [{:label \"Retry\" :action :retry-camera}
               {:label \"Use File\" :action :upload-video}]}"
  [error]
  (let [severity (:error/severity error)
        icon (case severity
               :critical "🔴"
               :warning "⚠️"
               :info "ℹ️"
               "")]
    {:title (str icon " " (:error/message error))
     :message (:error/suggestion error)
     :severity severity
     :timestamp (:error/timestamp error)
     :dismissible (not= severity :critical)
     :actions (case (:error/type error)
                ;; Camera errors
                :camera-not-found
                [{:label "Check Permissions" :action :check-camera-permissions}
                 {:label "Use Video File" :action :upload-video}]

                :camera-disconnected
                [{:label "Retry" :action :retry-camera}
                 {:label "Use Video File" :action :upload-video}]

                ;; Analysis errors
                :analysis-insufficient-data
                [{:label "Record Longer Session" :action :start-recording}]

                :breathing-detection-failed
                [{:label "Try Again" :action :retry-analysis}
                 {:label "Adjust Camera" :action :show-camera-tips}]

                ;; Session errors
                :session-save-failed
                [{:label "Retry Save" :action :retry-save}
                 {:label "Export Locally" :action :export-local}]

                ;; Default actions
                [{:label "Dismiss" :action :dismiss}])}))

;; ============================================================
;; GRACEFUL DEGRADATION HELPERS
;; ============================================================

(defn analyze-with-fallback
  "Analyze timeline with graceful degradation.

  If full analysis fails, return partial results with error.

  Args:
    timeline: Timeline to analyze
    analyzer-fn: Analyzer function
    fallback-result: Result to return on failure

  Returns:
    Analysis result (full or fallback)"
  [timeline analyzer-fn fallback-result]
  (safe-execute-with-fallback
   #(analyzer-fn timeline)
   (merge fallback-result {:error true})
   :analysis-failed
   log-error!))

(defn get-feature-with-fallback
  "Get feature from map with fallback.

  Args:
    features: Feature map
    feature-key: Key to extract
    fallback: Fallback value

  Returns:
    Feature value or fallback"
  [features feature-key fallback]
  (get features feature-key fallback))

;; ============================================================
;; EXPORTS
;; ============================================================

(def ^:export createError create-error)
(def ^:export logError log-error!)
(def ^:export getRecentErrors get-recent-errors)
(def ^:export formatErrorForUI format-error-for-ui)
(def ^:export safeExecute safe-execute)
(def ^:export retryWithBackoff retry-with-backoff)
