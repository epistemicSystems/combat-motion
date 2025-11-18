(ns combatsys.main.core
  "Electron main process entry point.

   Philosophy (Imperative Shell):
   - All hardware/OS interactions happen here
   - Window management, IPC, file system
   - Keep logic minimal, delegate to renderer

   This is the 'Node.js' side of Electron."
  (:require ["electron" :refer [app BrowserWindow]]))

;; ============================================================
;; STATE (Main Process)
;; ============================================================

(defonce main-window (atom nil))

;; ============================================================
;; WINDOW MANAGEMENT
;; ============================================================

(defn create-window!
  "Create the main Electron window.
   Side effect: Opens native OS window."
  []
  (println "Creating main window...")
  (reset! main-window
          (BrowserWindow.
           (clj->js {:width 1400
                     :height 900
                     :webPreferences {:nodeIntegration true
                                     :contextIsolation false
                                     :enableRemoteModule true}
                     :title "CombatSys Motion Analysis"})))

  ;; Load the renderer
  (.loadURL @main-window "http://localhost:8021")

  ;; Open DevTools in development
  (.openDevTools (.webContents @main-window))

  ;; Handle window close
  (.on @main-window "closed"
       (fn []
         (println "Main window closed")
         (reset! main-window nil)))

  (println "Main window created"))

;; ============================================================
;; APP LIFECYCLE
;; ============================================================

(defn ^:export init
  "Initialize the Electron app.
   Called when shadow-cljs compiles this target."
  []
  (println "Initializing Electron main process...")

  ;; Create window when ready
  (.on app "ready"
       (fn []
         (println "Electron app ready")
         (create-window!)))

  ;; Quit when all windows closed (except on macOS)
  (.on app "window-all-closed"
       (fn []
         (println "All windows closed")
         (when-not (= js/process.platform "darwin")
           (println "Quitting app")
           (.quit app))))

  ;; Recreate window when dock icon clicked (macOS)
  (.on app "activate"
       (fn []
         (when (nil? @main-window)
           (println "Reactivating app")
           (create-window!))))

  (println "Electron main process initialized"))
