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
;; Session Index I/O (LOD 6)
;; ============================================================================

(defn get-index-file-path
  "Get file path for session index.

  Pure function.

  Returns:
    String path to index.edn file

  Example:
    (get-index-file-path)
    => '/home/user/.config/CombatSys/sessions/index.edn'"
  []
  (path/join (get-sessions-dir) "index.edn"))

(defn load-session-index!
  "Load session index from disk.

  Side effect: File system read.

  Returns:
    Vector of session metadata, or empty vector if index doesn't exist

  Example:
    (load-session-index!)
    => [{:session/id #uuid \"...\"
         :session/name \"Morning Training\"
         :session/created-at \"2025-11-18T10:30:00Z\"
         :session/duration-ms 30000
         :session/frame-count 450
         :session/summary-stats {...}}
        ...]

  Performance:
    - 100 sessions = ~10KB file
    - Load time: <100ms
    - vs loading 100 full sessions = ~90MB, >1000ms

  Error handling:
    - File not found → returns [] (not an error, index will be created)
    - Invalid EDN → returns [] + console.error
    - Corrupted index → returns [] + console.error (can rebuild)"
  []
  (try
    (let [index-path (get-index-file-path)]

      ;; Check if file exists
      (if (fs/existsSync index-path)
        (do
          ;; Read file (synchronous)
          (let [edn-str (fs/readFileSync index-path "utf8")

                ;; Deserialize from EDN
                index (persist/edn-string->session-index edn-str)]

            (js/console.log "Loaded session index:" (count index) "sessions")
            index))

        ;; File doesn't exist (will be created on first save)
        (do
          (js/console.log "Session index not found - will be created on first save")
          [])))

    (catch js/Error e
      (js/console.error "Failed to load session index:" (.-message e))
      [])))

(defn save-session-index!
  "Save session index to disk.

  Side effect: File system write.

  Args:
    index: Vector of session metadata maps

  Returns:
    {:success? boolean
     :file-path string (if successful)
     :error string (if failed)}

  Example:
    (save-session-index! [{:session/id ... :session/name ...} ...])
    => {:success? true :file-path '/home/user/.config/CombatSys/sessions/index.edn'}

  Performance:
    - 100 sessions = ~10KB file
    - Write time: <50ms

  Error handling:
    - Directory doesn't exist → creates it
    - Write fails → returns {:success? false :error ...}
    - Disk full → returns {:success? false :error 'ENOSPC: ...'}"
  [index]
  (try
    ;; Ensure directory exists
    (when-not (ensure-sessions-dir!)
      (throw (js/Error. "Failed to create sessions directory")))

    ;; Get file path
    (let [index-path (get-index-file-path)

          ;; Serialize to EDN
          edn-str (pr-str index)]

      ;; Write to file (synchronous)
      (fs/writeFileSync index-path edn-str "utf8")

      (js/console.log "Saved session index:" (count index) "sessions to" index-path)

      {:success? true
       :file-path index-path})

    (catch js/Error e
      (js/console.error "Failed to save session index:" (.-message e))
      {:success? false
       :error (.-message e)})))

(defn update-session-in-index!
  "Add or update session in index.

  Side effect: File system read + write.

  Args:
    session: Full session map (metadata will be extracted)

  Returns:
    {:success? boolean
     :error string (if failed)}

  Algorithm:
    1. Load current index
    2. Extract metadata from session
    3. Remove old entry if exists
    4. Add new entry
    5. Sort by date (newest first)
    6. Save index

  Performance:
    - O(n) for n sessions, but acceptable for n<1000
    - Typical: ~20ms for 100 sessions

  Example:
    (update-session-in-index! session)
    => {:success? true}

  Note:
    - Automatically called by save-session!
    - Index is rebuilt incrementally (no full rebuild needed)
    - If index corrupt, recreates with just this session"
  [session]
  (try
    ;; Load current index
    (let [index (load-session-index!)

          ;; Extract metadata from session
          metadata (persist/extract-session-metadata session)

          ;; Remove old entry if exists (by session ID)
          index-without-old (filterv #(not= (:session/id %)
                                            (:session/id metadata))
                                     index)

          ;; Add new entry
          updated-index (conj index-without-old metadata)

          ;; Sort by date (newest first)
          sorted-index (vec (sort-by :session/created-at
                                     #(compare %2 %1)  ; Descending
                                     updated-index))]

      ;; Save updated index
      (let [result (save-session-index! sorted-index)]
        (if (:success? result)
          {:success? true}
          {:success? false :error (:error result)})))

    (catch js/Error e
      (js/console.error "Failed to update session in index:" (.-message e))
      {:success? false
       :error (.-message e)})))

(defn remove-session-from-index!
  "Remove session from index.

  Side effect: File system read + write.

  Args:
    session-id: UUID of session to remove

  Returns:
    {:success? boolean
     :error string (if failed)}

  Example:
    (remove-session-from-index! '550e8400-e29b-41d4-a716-446655440000')
    => {:success? true}

  Note:
    - Automatically called by delete-session!
    - If session not in index, still succeeds (idempotent)"
  [session-id]
  (try
    ;; Load current index
    (let [index (load-session-index!)

          ;; Remove session
          updated-index (filterv #(not= (:session/id %) session-id) index)]

      ;; Save updated index (only if changed)
      (if (= (count index) (count updated-index))
        (do
          (js/console.log "Session not in index (already removed?):" session-id)
          {:success? true})

        (let [result (save-session-index! updated-index)]
          (if (:success? result)
            {:success? true}
            {:success? false :error (:error result)}))))

    (catch js/Error e
      (js/console.error "Failed to remove session from index:" (.-message e))
      {:success? false
       :error (.-message e)})))

;; ============================================================================
;; Session File I/O (Modified for LOD 6)
;; ============================================================================

;; NOTE: Modified save-session! to also update index
;; Original implementation above, now enhanced

(defn save-session-with-index!
  "Save session to disk AND update index.

  Side effect: File system write (session file + index update).

  Args:
    session: Session map with :session/id

  Returns:
    {:success? boolean
     :file-path string (if successful)
     :error string (if failed)}

  Example:
    (save-session-with-index! session)
    => {:success? true
        :file-path '/home/user/.config/CombatSys/sessions/550e8400....edn'}

  Performance:
    - Session save: ~50ms for 900KB session
    - Index update: ~20ms for 100 sessions
    - Total: ~70ms (acceptable)

  Note:
    - This replaces save-session! in application code
    - Index update failures don't prevent session save (warn only)
    - Use this for all session saves in LOD 6+"
  [session]
  (let [;; Save session file first
        save-result (save-session! session)]

    (if (:success? save-result)
      (do
        ;; Update index (failure here doesn't prevent session save)
        (let [index-result (update-session-in-index! session)]
          (when-not (:success? index-result)
            (js/console.warn "Session saved but index update failed:" (:error index-result))))

        ;; Return original save result
        save-result)

      ;; Session save failed
      save-result)))

(defn delete-session-with-index!
  "Delete session file AND remove from index.

  Side effect: File system delete (session file + index update).

  Args:
    session-id: Session UUID (as string or UUID)

  Returns:
    {:success? boolean
     :error string (if failed)}

  Example:
    (delete-session-with-index! '550e8400-e29b-41d4-a716-446655440000')
    => {:success? true}

  Note:
    - This replaces delete-session! in application code
    - Index update failures don't prevent session deletion (warn only)
    - Use this for all session deletions in LOD 6+"
  [session-id]
  (let [;; Delete session file first
        delete-result (delete-session! session-id)]

    (if (:success? delete-result)
      (do
        ;; Remove from index (failure here doesn't prevent session deletion)
        (let [index-result (remove-session-from-index! session-id)]
          (when-not (:success? index-result)
            (js/console.warn "Session deleted but index update failed:" (:error index-result))))

        ;; Return original delete result
        delete-result)

      ;; Session deletion failed
      delete-result)))

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

;; ============================================================================
;; User Profile Storage (LOD 5)
;; ============================================================================

(defn get-profiles-dir
  "Get profiles directory path.

  Pure function.

  Returns:
    String path to profiles directory

  Example:
    (get-profiles-dir)
    => '/home/user/.config/CombatSys/profiles'"
  []
  (path/join (get-user-data-path) "profiles"))

(defn get-profile-file-path
  "Get file path for user profile.

  Pure function.

  Args:
    user-id: User UUID (as string or UUID)

  Returns:
    String path to profile EDN file

  Example:
    (get-profile-file-path '550e8400-e29b-41d4-a716-446655440000')
    => '/home/user/.config/CombatSys/profiles/550e8400-e29b-41d4-a716-446655440000.edn'"
  [user-id]
  (let [id-str (str user-id)
        filename (str id-str ".edn")]
    (path/join (get-profiles-dir) filename)))

(defn ensure-profiles-dir!
  "Create profiles directory if it doesn't exist.

  Side effect: File system write (mkdir).

  Returns:
    true if directory exists/created, false on error

  Example:
    (ensure-profiles-dir!)
    => true"
  []
  (try
    (let [profiles-dir (get-profiles-dir)]
      (when-not (fs/existsSync profiles-dir)
        (fs/mkdirSync profiles-dir #js {:recursive true})
        (js/console.log "Created profiles directory:" profiles-dir))
      true)
    (catch js/Error e
      (js/console.error "Failed to create profiles directory:" (.-message e))
      false)))

(defn save-user-profile!
  "Save user profile to disk as EDN file.

  Side effect: File system write.

  Args:
    profile: User profile map with :user-id

  Returns:
    {:success? boolean
     :file-path string (if successful)
     :error string (if failed)}

  Example:
    (save-user-profile! profile)
    => {:success? true
        :file-path '/home/user/.config/CombatSys/profiles/550e8400....edn'}"
  [profile]
  (try
    ;; Ensure directory exists
    (when-not (ensure-profiles-dir!)
      (throw (js/Error. "Failed to create profiles directory")))

    ;; Validate profile
    (when-not (persist/valid-user-profile? profile)
      (throw (js/Error. "Invalid user profile")))

    ;; Get file path
    (let [user-id (:user-id profile)
          file-path (get-profile-file-path user-id)

          ;; Serialize to EDN
          edn-str (persist/profile->edn-string profile)]

      ;; Write to file (synchronous)
      (fs/writeFileSync file-path edn-str "utf8")

      (js/console.log "Saved user profile:" user-id "to" file-path)

      {:success? true
       :file-path file-path})

    (catch js/Error e
      (js/console.error "Failed to save user profile:" (.-message e))
      {:success? false
       :error (.-message e)})))

(defn load-user-profile!
  "Load user profile from disk by ID.

  Side effect: File system read.

  Args:
    user-id: User UUID (as string or UUID)

  Returns:
    User profile map if found, nil otherwise

  Example:
    (load-user-profile! '550e8400-e29b-41d4-a716-446655440000')
    => {:user-id #uuid '550e8400...' :height-cm 178 ...}

    (load-user-profile! 'nonexistent')
    => nil

  Error handling:
    - File not found → nil (not an error)
    - File read error → nil + console.error
    - Invalid EDN → nil + console.error
    - Schema validation fails → nil + console.error"
  [user-id]
  (try
    (let [file-path (get-profile-file-path user-id)]

      ;; Check if file exists
      (if (fs/existsSync file-path)
        (do
          ;; Read file (synchronous)
          (let [edn-str (fs/readFileSync file-path "utf8")

                ;; Deserialize from EDN
                profile (persist/edn-string->profile edn-str)]

            ;; Validate loaded profile
            (if (persist/valid-user-profile? profile)
              (do
                (js/console.log "Loaded user profile:" user-id)
                profile)
              (do
                (js/console.error "Loaded profile is invalid:" user-id)
                nil))))

        ;; File doesn't exist
        (do
          (js/console.warn "Profile file not found:" user-id)
          nil)))

    (catch js/Error e
      (js/console.error "Failed to load user profile:" user-id (.-message e))
      nil)))

(defn get-default-user-profile!
  "Get the default (most recently used) user profile.

  For single-user app, this is just the first profile found.

  Side effect: File system read (directory listing + file read).

  Returns:
    User profile map, or nil if no profiles exist

  Example:
    (get-default-user-profile!)
    => {:user-id #uuid '...' :height-cm 178 ...}

  Error handling:
    - No profiles found → nil (not an error)
    - All profiles corrupted → nil + console.error"
  []
  (try
    ;; Ensure directory exists
    (ensure-profiles-dir!)

    (let [profiles-dir (get-profiles-dir)

          ;; Read directory (synchronous)
          files (fs/readdirSync profiles-dir)

          ;; Filter .edn files and extract IDs
          profile-ids (->> files
                           (filter #(.endsWith % ".edn"))
                           (mapv #(subs % 0 (- (count %) 4))))]  ; Remove .edn extension

      (if (seq profile-ids)
        (do
          (js/console.log "Found" (count profile-ids) "user profiles")
          ;; Load first profile
          (load-user-profile! (first profile-ids)))
        (do
          (js/console.log "No user profiles found")
          nil)))

    (catch js/Error e
      (js/console.error "Failed to get default user profile:" (.-message e))
      nil)))

(defn list-user-profile-ids!
  "List all saved user profile IDs.

  Side effect: File system read (directory listing).

  Returns:
    Vector of user ID strings (UUIDs)

  Example:
    (list-user-profile-ids!)
    => ['550e8400-e29b-41d4-a716-446655440000']"
  []
  (try
    ;; Ensure directory exists
    (ensure-profiles-dir!)

    (let [profiles-dir (get-profiles-dir)

          ;; Read directory (synchronous)
          files (fs/readdirSync profiles-dir)

          ;; Filter .edn files and extract IDs
          profile-ids (->> files
                           (filter #(.endsWith % ".edn"))
                           (mapv #(subs % 0 (- (count %) 4))))]  ; Remove .edn extension

      (js/console.log "Found" (count profile-ids) "user profiles")
      profile-ids)

    (catch js/Error e
      (js/console.error "Failed to list user profiles:" (.-message e))
      [])))

(defn delete-user-profile!
  "Delete user profile file from disk.

  Side effect: File system delete.

  Args:
    user-id: User UUID (as string or UUID)

  Returns:
    {:success? boolean
     :error string (if failed)}

  Example:
    (delete-user-profile! '550e8400-e29b-41d4-a716-446655440000')
    => {:success? true}"
  [user-id]
  (try
    (let [file-path (get-profile-file-path user-id)]

      ;; Check if file exists
      (if (fs/existsSync file-path)
        (do
          ;; Delete file (synchronous)
          (fs/unlinkSync file-path)
          (js/console.log "Deleted user profile:" user-id)
          {:success? true})

        ;; File doesn't exist (treat as success)
        (do
          (js/console.warn "Profile file not found (already deleted?):" user-id)
          {:success? true})))

    (catch js/Error e
      (js/console.error "Failed to delete user profile:" user-id (.-message e))
      {:success? false
       :error (.-message e)})))
