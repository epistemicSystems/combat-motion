(ns combatsys.renderer.core
  "Renderer process entry point.
   
   This is the 'main' for the Electron renderer (browser) process.
   Initializes re-frame state and mounts the React/Reagent app."
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [combatsys.renderer.state :as state]
            [combatsys.renderer.views :as views]))

;; ============================================================
;; INITIALIZATION
;; ============================================================

(defn mount-root
  "Mount the root Reagent component"
  []
  (rf/clear-subscription-cache!)
  (rdom/render [views/main-view]
               (.getElementById js/document "app")))

(defn ^:export init
  "Initialize the app when Electron loads the page"
  []
  (println "Initializing CombatSys Motion Analysis...")
  
  ;; Initialize re-frame state
  (rf/dispatch-sync [::state/initialize])
  
  ;; Mount React app
  (mount-root)
  
  (println "App initialized successfully!"))

;; ============================================================
;; HOT RELOAD SUPPORT
;; ============================================================

(defn ^:dev/before-load before-reload
  "Called before code reload during development"
  []
  (println "Reloading..."))

(defn ^:dev/after-load after-reload
  "Called after code reload during development"
  []
  (println "Reloaded!")
  (mount-root))
