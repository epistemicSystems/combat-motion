(ns combatsys.renderer.files
  "File system operations for session storage.

  Philosophy (Imperative Shell):
  - All functions have side effects (file I/O)
  - Named with ! suffix to indicate side effects
  - Uses Node.js fs module (requires nodeIntegration: true)
  - Synchronous for simplicity (sessions are small <1MB)

  Storage location:
    Linux: ~/.config/CombatSys/sessions/
    macOS: ~/Library/Application Support/CombatSys/sessions/
    Windows: %APPDATA%/CombatSys/sessions/

  File format:
    {uuid}.edn - EDN format for each session"
  (:require [combatsys.renderer.persistence :as persist]
            ["fs" :as fs]
            ["path" :as path]
            ["electron" :as electron]))

;; ============================================================================
;; Storage Paths
;; ============================================================================

(defn get-user-data-path
  "Get Electron userData path.

  Pure function (reads from Electron API).

  Returns:
    String path to userData directory

  Example:
    (get-user-data-path)
    => '/home/user/.config/CombatSys'  (Linux)
    => '/Users/user/Library/Application Support/CombatSys'  (macOS)
    => 'C:\\Users\\user\\AppData\\Roaming\\CombatSys'  (Windows)"
  []
  (.. electron -app (getPath "userData")))

(defn get-sessions-dir
  "Get sessions directory path.

  Pure function.

  Returns:
    String path to sessions directory

  Example:
    (get-sessions-dir)
    => '/home/user/.config/CombatSys/sessions'"
  []
  (path/join (get-user-data-path) "sessions"))

(defn get-session-file-path
  "Get file path for session ID.

  Pure function.

  Args:
    session-id: Session UUID (as string or UUID)

  Returns:
    String path to session EDN file

  Example:
    (get-session-file-path '550e8400-e29b-41d4-a716-446655440000')
    => '/home/user/.config/CombatSys/sessions/550e8400-e29b-41d4-a716-446655440000.edn'"
  [session-id]
  (let [id-str (str session-id)
        filename (str id-str ".edn")]
    (path/join (get-sessions-dir) filename)))

;; ============================================================================
;; Directory Management (Side Effects)
;; ============================================================================

(defn ensure-sessions-dir!
  "Create sessions directory if it doesn't exist.

  Side effect: File system write (mkdir).

  Returns:
    true if directory exists/created, false on error

  Example:
    (ensure-sessions-dir!)
    => true"
  []
  (try
    (let [sessions-dir (get-sessions-dir)]
      (when-not (fs/existsSync sessions-dir)
        (fs/mkdirSync sessions-dir #js {:recursive true})
        (js/console.log "Created sessions directory:" sessions-dir))
      true)
    (catch js/Error e
      (js/console.error "Failed to create sessions directory:" (.-message e))
      false)))

;; ============================================================================
;; Session File I/O (Side Effects)
;; ============================================================================

(defn save-session!
  "Save session to disk as EDN file.

  Side effect: File system write.

  Args:
    session: Session map with :session/id

  Returns:
    {:success? boolean
     :file-path string (if successful)
     :error string (if failed)}

  Example:
    (save-session! session)
    => {:success? true
        :file-path '/home/user/.config/CombatSys/sessions/550e8400....edn'}

  Error handling:
    - Directory creation fails → {:success? false :error 'Failed to create directory'}
    - File write fails → {:success? false :error 'Failed to write file: ...'}
    - Disk full → {:success? false :error 'ENOSPC: no space left on device'}
    - Permission denied → {:success? false :error 'EACCES: permission denied'}"
  [session]
  (try
    ;; Ensure directory exists
    (when-not (ensure-sessions-dir!)
      (throw (js/Error. "Failed to create sessions directory")))

    ;; Get file path
    (let [session-id (:session/id session)
          file-path (get-session-file-path session-id)

          ;; Serialize to EDN
          edn-str (persist/session->edn-string session)]

      ;; Write to file (synchronous)
      (fs/writeFileSync file-path edn-str "utf8")

      (js/console.log "Saved session:" session-id "to" file-path)

      {:success? true
       :file-path file-path})

    (catch js/Error e
      (js/console.error "Failed to save session:" (.-message e))
      {:success? false
       :error (.-message e)})))

(defn load-session!
  "Load session from disk by ID.

  Side effect: File system read.

  Args:
    session-id: Session UUID (as string or UUID)

  Returns:
    Session map if found, nil otherwise

  Example:
    (load-session! '550e8400-e29b-41d4-a716-446655440000')
    => {:session/id #uuid '550e8400...' :session/timeline [...]}

    (load-session! 'nonexistent')
    => nil

  Error handling:
    - File not found → nil (not an error)
    - File read error → nil + console.error
    - Invalid EDN → nil + console.error
    - Corrupted session → nil + console.error"
  [session-id]
  (try
    (let [file-path (get-session-file-path session-id)]

      ;; Check if file exists
      (if (fs/existsSync file-path)
        (do
          ;; Read file (synchronous)
          (let [edn-str (fs/readFileSync file-path "utf8")

                ;; Deserialize from EDN
                session (persist/edn-string->session edn-str)]

            ;; Validate loaded session
            (if (persist/valid-session? session)
              (do
                (js/console.log "Loaded session:" session-id)
                session)
              (do
                (js/console.error "Loaded session is invalid:" session-id)
                nil))))

        ;; File doesn't exist
        (do
          (js/console.warn "Session file not found:" session-id)
          nil)))

    (catch js/Error e
      (js/console.error "Failed to load session:" session-id (.-message e))
      nil)))

(defn delete-session!
  "Delete session file from disk.

  Side effect: File system delete.

  Args:
    session-id: Session UUID (as string or UUID)

  Returns:
    {:success? boolean
     :error string (if failed)}

  Example:
    (delete-session! '550e8400-e29b-41d4-a716-446655440000')
    => {:success? true}"
  [session-id]
  (try
    (let [file-path (get-session-file-path session-id)]

      ;; Check if file exists
      (if (fs/existsSync file-path)
        (do
          ;; Delete file (synchronous)
          (fs/unlinkSync file-path)
          (js/console.log "Deleted session:" session-id)
          {:success? true})

        ;; File doesn't exist (treat as success)
        (do
          (js/console.warn "Session file not found (already deleted?):" session-id)
          {:success? true})))

    (catch js/Error e
      (js/console.error "Failed to delete session:" session-id (.-message e))
      {:success? false
       :error (.-message e)})))

;; ============================================================================
;; Session Listing (Side Effects)
;; ============================================================================

(defn list-session-ids!
  "List all saved session IDs.

  Side effect: File system read (directory listing).

  Returns:
    Vector of session ID strings (UUIDs)

  Example:
    (list-session-ids!)
    => ['550e8400-e29b-41d4-a716-446655440000'
        '6ba7b810-9dad-11d1-80b4-00c04fd430c8']

  Error handling:
    - Directory doesn't exist → creates it, returns []
    - Read error → [] + console.error"
  []
  (try
    ;; Ensure directory exists
    (ensure-sessions-dir!)

    (let [sessions-dir (get-sessions-dir)

          ;; Read directory (synchronous)
          files (fs/readdirSync sessions-dir)

          ;; Filter .edn files and extract IDs
          session-ids (->> files
                           (filter #(.endsWith % ".edn"))
                           (mapv #(subs % 0 (- (count %) 4))))]  ; Remove .edn extension

      (js/console.log "Found" (count session-ids) "saved sessions")
      session-ids)

    (catch js/Error e
      (js/console.error "Failed to list sessions:" (.-message e))
      [])))

(defn list-sessions!
  "List all saved sessions with metadata.

  Side effect: File system read (loads all session files).

  Returns:
    Vector of session maps (sorted by creation date, newest first)

  Example:
    (list-sessions!)
    => [{:session/id #uuid '...'
         :session/name 'Morning Training'
         :session/created-at '2025-11-18T12:00:00Z'
         :session/duration-ms 30000
         ...}
        ...]

  Note:
    - Loads all sessions (potentially slow for many sessions)
    - Skips corrupted/invalid sessions
    - For LOD 1 (small number of sessions)
    - LOD 2+: Use pagination or metadata index"
  []
  (try
    (let [session-ids (list-session-ids!)

          ;; Load all sessions
          sessions (->> session-ids
                        (keep load-session!)  ; Skip nil (corrupted/invalid)
                        (sort-by :session/created-at)
                        (reverse)  ; Newest first
                        (vec))]

      (js/console.log "Loaded" (count sessions) "sessions")
      sessions)

    (catch js/Error e
      (js/console.error "Failed to list sessions:" (.-message e))
      [])))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn get-sessions-dir-size!
  "Get total size of sessions directory in bytes.

  Side effect: File system read (all session files).

  Returns:
    Total size in bytes

  Example:
    (get-sessions-dir-size!)
    => 4567890  ; ~4.5 MB"
  []
  (try
    (ensure-sessions-dir!)

    (let [sessions-dir (get-sessions-dir)
          files (fs/readdirSync sessions-dir)

          ;; Sum file sizes
          total-size (reduce (fn [sum filename]
                               (let [file-path (path/join sessions-dir filename)
                                     stats (fs/statSync file-path)]
                                 (+ sum (.-size stats))))
                             0
                             files)]

      (js/console.log "Sessions directory size:" total-size "bytes")
      total-size)

    (catch js/Error e
      (js/console.error "Failed to get directory size:" (.-message e))
      0)))

(defn session-exists?
  "Check if session file exists on disk.

  Side effect: File system read (stat).

  Args:
    session-id: Session UUID

  Returns:
    Boolean - true if file exists

  Example:
    (session-exists? '550e8400-e29b-41d4-a716-446655440000')
    => true"
  [session-id]
  (let [file-path (get-session-file-path session-id)]
    (fs/existsSync file-path)))

(comment
  ;; REPL Testing

  ;; Check paths
  (get-user-data-path)
  ;; => "/home/user/.config/CombatSys"

  (get-sessions-dir)
  ;; => "/home/user/.config/CombatSys/sessions"

  ;; Create sessions directory
  (ensure-sessions-dir!)
  ;; => true

  ;; Create test session
  (def session (persist/create-session "Test Session"))

  ;; Save session
  (save-session! session)
  ;; => {:success? true :file-path "..."}

  ;; List sessions
  (list-session-ids!)
  ;; => ["550e8400-e29b-41d4-a716-446655440000" ...]

  ;; Load session
  (def loaded (load-session! (:session/id session)))

  ;; Verify loaded session
  (= session loaded)
  ;; => true

  ;; Delete session
  (delete-session! (:session/id session))
  ;; => {:success? true}

  ;; List all sessions with metadata
  (list-sessions!)
  ;; => [{:session/id ... :session/name "Morning Training" ...}]

  ;; Check directory size
  (get-sessions-dir-size!)
  ;; => 123456

  )
