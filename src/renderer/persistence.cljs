(ns combatsys.renderer.persistence
  "Session persistence - pure serialization and deserialization.

  Philosophy (Functional Core):
  - All functions are pure (no file I/O)
  - EDN format for human-readable sessions
  - Schema validation for loaded data

  Session format:
  {:session/id #uuid '...'
   :session/created-at '2025-11-18T12:34:56Z'
   :session/name 'Morning Training'
   :session/duration-ms 30000
   :session/timeline [{:frame/index 0 :frame/pose {...}}]
   :session/metadata {...}}"
  (:require [cljs.reader :as reader]
            [combatsys.schema :as schema]
            [clojure.string :as str]))

;; ============================================================================
;; Pure Serialization (EDN)
;; ============================================================================

(defn session->edn-string
  "Convert session map to EDN string.

  Pure function.

  Args:
    session: Session map with :session/id, :session/timeline, etc.

  Returns:
    EDN string suitable for file storage

  Example:
    (session->edn-string session)
    => '{:session/id #uuid \"...\" :session/timeline [...]}'"
  [session]
  (pr-str session))

(defn edn-string->session
  "Parse EDN string to session map.

  Pure function.

  Args:
    edn-str: EDN string from file

  Returns:
    Session map, or nil if parsing fails

  Example:
    (edn-string->session '{:session/id #uuid \"...\"}')
    => {:session/id #uuid \"...\"}

  Error handling:
    - Invalid EDN → returns nil
    - Malformed data → returns nil
    - Logs error to console"
  [edn-str]
  (try
    (reader/read-string edn-str)
    (catch js/Error e
      (js/console.error "Failed to parse session EDN:" (.-message e))
      nil)))

;; ============================================================================
;; Session Creation (Pure Functions)
;; ============================================================================

(defn create-session
  "Create new empty session.

  Pure function.

  Args:
    name: Session name (optional, default 'Untitled Session')

  Returns:
    New session map with generated UUID

  Example:
    (create-session 'Morning Training')
    => {:session/id #uuid \"...\"
        :session/created-at \"2025-11-18T...\"
        :session/name \"Morning Training\"
        :session/timeline []
        :session/duration-ms 0}"
  ([

]
   (create-session "Untitled Session"))
  ([name]
   {:session/id (random-uuid)
    :session/created-at (.toISOString (js/Date.))
    :session/name name
    :session/timeline []
    :session/duration-ms 0
    :session/frame-count 0
    :session/metadata {}}))

(defn append-frame-to-session
  "Append frame to session timeline.

  Pure function.

  Args:
    session: Session map
    frame: Frame map with :frame/pose, :frame/timestamp-ms, etc.

  Returns:
    Updated session with frame appended

  Example:
    (append-frame-to-session session frame)
    => {:session/id ...
        :session/timeline [frame1 frame2 frame3]
        :session/frame-count 3}"
  [session frame]
  (-> session
      (update :session/timeline conj frame)
      (update :session/frame-count inc)))

(defn finalize-session
  "Finalize session with computed metadata.

  Pure function. Call when recording stops.

  Args:
    session: Session map with timeline

  Returns:
    Session with :session/duration-ms and :session/metadata computed

  Computes:
    - duration-ms: Last frame timestamp
    - avg-confidence: Average pose confidence across all frames
    - fps: Frames per second (actual)

  Example:
    (finalize-session session)
    => {:session/id ...
        :session/duration-ms 30125
        :session/metadata {:avg-confidence 0.92
                           :fps 14.9
                           :total-frames 450}}"
  [session]
  (let [timeline (:session/timeline session)
        frame-count (count timeline)
        duration-ms (if (seq timeline)
                      (:frame/timestamp-ms (last timeline))
                      0)

        ;; Compute average pose confidence
        confidences (keep #(get-in % [:frame/pose :pose/confidence]) timeline)
        avg-confidence (if (seq confidences)
                         (/ (reduce + confidences) (count confidences))
                         0)

        ;; Compute actual FPS
        fps (if (pos? duration-ms)
              (* (/ frame-count duration-ms) 1000)
              0)]

    (assoc session
           :session/duration-ms duration-ms
           :session/frame-count frame-count
           :session/metadata {:avg-confidence avg-confidence
                              :fps fps
                              :total-frames frame-count})))

;; ============================================================================
;; Session Metadata (Pure Functions)
;; ============================================================================

(defn session-summary
  "Create human-readable summary of session.

  Pure function.

  Args:
    session: Session map

  Returns:
    String summary

  Example:
    (session-summary session)
    => 'Morning Training | 30.1s | 450 frames | 14.9 fps | 92% confidence'"
  [session]
  (let [name (:session/name session)
        duration-sec (/ (:session/duration-ms session) 1000)
        frame-count (:session/frame-count session)
        fps (get-in session [:session/metadata :fps] 0)
        conf (get-in session [:session/metadata :avg-confidence] 0)]
    (str name " | "
         (.toFixed duration-sec 1) "s | "
         frame-count " frames | "
         (.toFixed fps 1) " fps | "
         (.toFixed (* conf 100) 0) "% confidence")))

(defn session-file-size
  "Estimate session file size in bytes.

  Pure function (approximation).

  Args:
    session: Session map

  Returns:
    Estimated file size in bytes

  Approximation:
    Each frame ~2KB (pose + angles + metadata)
    Plus session overhead ~200 bytes

  Example:
    (session-file-size session)
    => 900200  ; ~900KB for 450 frames"
  [session]
  (let [frame-count (:session/frame-count session 0)
        bytes-per-frame 2000  ; Approximation
        overhead 200]
    (+ (* frame-count bytes-per-frame) overhead)))

(defn session-date-str
  "Format session creation date as human-readable string.

  Pure function.

  Args:
    session: Session map with :session/created-at

  Returns:
    Formatted date string

  Example:
    (session-date-str session)
    => '2025-11-18 12:34:56'"
  [session]
  (let [iso-str (:session/created-at session)
        date (js/Date. iso-str)]
    (.toLocaleString date)))

;; ============================================================================
;; Validation (Pure Functions)
;; ============================================================================

(defn valid-session?
  "Check if session map is valid.

  Pure function.

  Args:
    session: Session map to validate

  Returns:
    Boolean - true if valid

  Checks:
    - Has :session/id (UUID)
    - Has :session/timeline (vector)
    - Has :session/created-at (string)
    - Has :session/name (string)

  Example:
    (valid-session? session)
    => true"
  [session]
  (and (map? session)
       (uuid? (:session/id session))
       (vector? (:session/timeline session))
       (string? (:session/created-at session))
       (string? (:session/name session))))

(defn validate-session
  "Validate session and return errors.

  Pure function.

  Args:
    session: Session map

  Returns:
    {:valid? boolean :errors [error messages]}

  Example:
    (validate-session session)
    => {:valid? true :errors []}

    (validate-session invalid-session)
    => {:valid? false :errors ['Missing :session/id']}"
  [session]
  (let [errors (atom [])]

    (when-not (map? session)
      (swap! errors conj "Session is not a map"))

    (when-not (uuid? (:session/id session))
      (swap! errors conj "Missing or invalid :session/id"))

    (when-not (vector? (:session/timeline session))
      (swap! errors conj "Missing or invalid :session/timeline"))

    (when-not (string? (:session/created-at session))
      (swap! errors conj "Missing or invalid :session/created-at"))

    (when-not (string? (:session/name session))
      (swap! errors conj "Missing or invalid :session/name"))

    {:valid? (empty? @errors)
     :errors @errors}))

;; ============================================================================
;; Session Queries (Pure Functions)
;; ============================================================================

(defn get-frame
  "Get frame by index from session.

  Pure function.

  Args:
    session: Session map
    frame-index: Frame index (0-based)

  Returns:
    Frame map, or nil if index out of bounds

  Example:
    (get-frame session 10)
    => {:frame/index 10 :frame/pose {...}}"
  [session frame-index]
  (get-in session [:session/timeline frame-index]))

(defn get-frames-in-range
  "Get frames in time range.

  Pure function.

  Args:
    session: Session map
    start-ms: Start time in milliseconds
    end-ms: End time in milliseconds

  Returns:
    Vector of frames in range

  Example:
    (get-frames-in-range session 1000 5000)
    => [{:frame/timestamp-ms 1000 ...}
        {:frame/timestamp-ms 1033 ...}
        ...]"
  [session start-ms end-ms]
  (filterv #(and (>= (:frame/timestamp-ms %) start-ms)
                 (<= (:frame/timestamp-ms %) end-ms))
           (:session/timeline session)))

(defn find-frame-by-timestamp
  "Find frame closest to timestamp.

  Pure function.

  Args:
    session: Session map
    timestamp-ms: Target timestamp

  Returns:
    Frame closest to timestamp, or nil if no frames

  Example:
    (find-frame-by-timestamp session 15000)
    => {:frame/timestamp-ms 15033 ...}"
  [session timestamp-ms]
  (when-let [timeline (seq (:session/timeline session))]
    (reduce (fn [closest frame]
              (let [closest-diff (js/Math.abs (- timestamp-ms (:frame/timestamp-ms closest)))
                    frame-diff (js/Math.abs (- timestamp-ms (:frame/timestamp-ms frame)))]
                (if (< frame-diff closest-diff)
                  frame
                  closest)))
            (first timeline)
            (rest timeline))))

(comment
  ;; REPL Testing

  ;; Create new session
  (def session (create-session "Morning Training"))

  ;; Create mock frame
  (def frame {:frame/index 0
              :frame/timestamp-ms 0
              :frame/pose {:pose/confidence 0.95
                           :pose/landmarks []
                           :pose/angles {}}})

  ;; Append frame
  (def session2 (append-frame-to-session session frame))

  ;; Finalize
  (def final-session (finalize-session session2))

  ;; Serialize
  (def edn-str (session->edn-string final-session))

  ;; Deserialize
  (def loaded-session (edn-string->session edn-str))

  ;; Validate
  (valid-session? loaded-session)
  ;; => true

  (validate-session loaded-session)
  ;; => {:valid? true :errors []}

  ;; Summary
  (session-summary loaded-session)
  ;; => "Morning Training | 0.0s | 1 frames | Infinity fps | 95% confidence"

  ;; File size estimate
  (session-file-size loaded-session)
  ;; => 2200 bytes

  )

;; ============================================================================
;; User Profile Serialization (LOD 5)
;; ============================================================================

(defn profile->edn-string
  "Convert user profile map to EDN string.

  Pure function.

  Args:
    profile: User profile map with :user-id, :baseline-pose, etc.

  Returns:
    EDN string suitable for file storage

  Example:
    (profile->edn-string profile)
    => '{:user-id #uuid \"...\" :height-cm 178 ...}'"
  [profile]
  (pr-str profile))

(defn edn-string->profile
  "Parse EDN string to user profile map.

  Pure function.

  Args:
    edn-str: EDN string from file

  Returns:
    User profile map, or nil if parsing fails

  Example:
    (edn-string->profile '{:user-id #uuid \"...\"}')
    => {:user-id #uuid \"...\"}

  Error handling:
    - Invalid EDN → returns nil
    - Malformed data → returns nil
    - Logs error to console"
  [edn-str]
  (try
    (reader/read-string edn-str)
    (catch js/Error e
      (js/console.error "Failed to parse profile EDN:" (.-message e))
      nil)))

(defn valid-user-profile?
  "Check if user profile map is valid.

  Pure function.

  Args:
    profile: User profile map to validate

  Returns:
    Boolean - true if valid

  Uses schema/valid-user-profile? for validation.

  Example:
    (valid-user-profile? profile)
    => true"
  [profile]
  (and (map? profile)
       (schema/valid-user-profile? profile)))

(defn validate-user-profile
  "Validate user profile and return errors.

  Pure function.

  Args:
    profile: User profile map

  Returns:
    {:valid? boolean :errors [error messages]}

  Example:
    (validate-user-profile profile)
    => {:valid? true :errors []}

    (validate-user-profile invalid-profile)
    => {:valid? false :errors ['Schema validation failed']}"
  [profile]
  (if (schema/valid-user-profile? profile)
    {:valid? true :errors []}
    {:valid? false :errors ["Schema validation failed"]}))
