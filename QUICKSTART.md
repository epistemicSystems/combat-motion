# 🚀 QUICK START GUIDE
## Get Started with Task 1.1 in 10 Minutes

---

## YOUR MISSION TODAY (LOD 0, Task 1.1)

Build the project scaffolding. By end of today, when you run `npm start`, an Electron window will open showing a basic UI.

**Time estimate**: 2-3 hours  
**Difficulty**: ⭐ (Setup/Scaffolding)

---

## STEP 1: READ THE CONTEXT (10 MINUTES)

### Read these in order:
1. **CLAUDE.md** (5 min) - Who you are as the team
2. **PROJECT_CONTEXT.md** - Skim "Overview" and "Architecture" sections (5 min)

### Key takeaways:
- You're building a ClojureScript + Electron desktop app
- Functional core (pure functions) + Imperative shell (I/O)
- Everything flows through EDN data structures
- LOD 0 = Mock data + Basic UI → IT RUNS

---

## STEP 2: CREATE PROJECT STRUCTURE (30 MINUTES)

### Create Directory Structure
```bash
mkdir combatsys-motion
cd combatsys-motion

# Create directory tree
mkdir -p src/{main,renderer,shared}
mkdir -p test/shared
mkdir -p resources/{shaders,models}
mkdir -p docs
```

### Create package.json
```json
{
  "name": "combatsys-motion",
  "version": "1.0.0",
  "description": "Camera-only motion analysis platform",
  "main": "out/main.js",
  "scripts": {
    "dev": "shadow-cljs watch main renderer",
    "build": "shadow-cljs release main renderer",
    "test": "shadow-cljs compile test && node out/test.js",
    "start": "electron ."
  },
  "dependencies": {
    "electron": "^27.0.0"
  },
  "devDependencies": {
    "shadow-cljs": "^2.26.0"
  }
}
```

### Create shadow-cljs.edn
```clojure
{:source-paths ["src/shared" "src/main" "src/renderer" "test"]
 
 :dependencies
 [[reagent "1.2.0"]
  [re-frame "1.4.0"]
  [org.clojure/test.check "1.1.1"]]
 
 :builds
 {:main
  {:target :node-script
   :main combatsys.main.core/init
   :output-to "out/main.js"}
  
  :renderer
  {:target :browser
   :modules {:app {:init-fn combatsys.renderer.core/init}}
   :dev {:http-port 8080
         :http-root "public"}
   :devtools {:after-load combatsys.renderer.core/reload}}
  
  :test
  {:target :node-test
   :output-to "out/test.js"
   :ns-regexp "-test$"}}}
```

### Install Dependencies
```bash
npm install
```

---

## STEP 3: CREATE CORE FILES (60 MINUTES)

### A. Electron Main Process

**File: `src/main/core.cljs`**
```clojure
(ns combatsys.main.core
  (:require ["electron" :refer [app BrowserWindow]]))

(defonce main-window (atom nil))

(defn create-window! []
  (reset! main-window
          (BrowserWindow.
           (clj->js {:width 1200
                     :height 800
                     :webPreferences {:nodeIntegration true
                                     :contextIsolation false}})))
  (.loadURL @main-window "http://localhost:8080")
  (.openDevTools (.webContents @main-window)))

(defn init []
  (.on app "ready" create-window!)
  (.on app "window-all-closed"
       (fn []
         (when-not (= js/process.platform "darwin")
           (.quit app)))))
```

### B. Renderer Entry Point

**File: `src/renderer/core.cljs`**
```clojure
(ns combatsys.renderer.core
  (:require [reagent.dom :as rdom]
            [combatsys.renderer.views :as views]))

(defn mount-root []
  (rdom/render [views/app]
               (.getElementById js/document "app")))

(defn init []
  (mount-root))

(defn ^:dev/after-load reload []
  (mount-root))
```

### C. Basic UI

**File: `src/renderer/views.cljs`**
```clojure
(ns combatsys.renderer.views
  (:require [reagent.core :as r]))

(defn navbar []
  [:nav {:style {:background "#333"
                 :color "#fff"
                 :padding "1rem"}}
   [:h1 "CombatSys Motion Analysis"]
   [:div
    [:button "Live Feed"]
    [:button "Sessions"]
    [:button "Analysis"]]])

(defn live-feed-view []
  [:div {:style {:padding "2rem"}}
   [:h2 "Live Feed"]
   [:p "Camera feed will appear here"]
   [:div
    [:button "Start Recording"]
    [:button "Stop Recording"]]])

(defn app []
  [:div
   [navbar]
   [live-feed-view]])
```

### D. Create HTML Entry Point

**File: `public/index.html`**
```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <title>CombatSys Motion</title>
  <style>
    body {
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }
  </style>
</head>
<body>
  <div id="app"></div>
  <script src="/js/app.js"></script>
</body>
</html>
```

---

## STEP 4: TEST THE BUILD (10 MINUTES)

### Terminal 1: Start ClojureScript Compiler
```bash
npx shadow-cljs watch main renderer
```

Wait for:
```
[:main] Build completed. (X files, Y compiled, Z warnings)
[:renderer] Build completed. (X files, Y compiled, Z warnings)
```

### Terminal 2: Start Electron
```bash
npm start
```

### Expected Result
✅ Electron window opens  
✅ You see "CombatSys Motion Analysis" header  
✅ You see "Live Feed" view  
✅ You see two buttons: "Start Recording" and "Stop Recording"

---

## STEP 5: VERIFY SUCCESS (5 MINUTES)

### Checklist
- [ ] Electron window opens without errors
- [ ] UI renders (header + buttons visible)
- [ ] No compilation warnings in terminal
- [ ] Hot reload works (change text in views.cljs, see update)

### If Something's Wrong
**Error: "Cannot find module 'electron'"**
- Run `npm install` again

**Error: "Build failed"**
- Check shadow-cljs.edn syntax (balanced braces)
- Clear cache: `npx shadow-cljs clean`

**Error: "White screen in Electron"**
- Check browser console (View → Toggle Developer Tools)
- Ensure http://localhost:8080 is accessible

---

## STEP 6: COMMIT YOUR WORK (5 MINUTES)

```bash
git init
git add .
git commit -m "[LOD 0] Task 1.1: Project scaffolding complete

- Created ClojureScript project structure
- Configured shadow-cljs with main + renderer builds
- Added basic Electron app with Reagent UI
- Verified: npm start opens working app

Tests: ✅ Manual testing passed"
```

---

## 🎉 SUCCESS!

You've completed Task 1.1. The app runs end-to-end, even if it's just a stub.

---

## NEXT STEPS

### Immediate (Today, if time)
Move to **Task 1.2: EDN Schema Definitions**
- Create `src/shared/schema.cljs`
- Define specs from SPEC.md
- Add validation functions

### Tomorrow (Day 1 continued)
- Task 1.3: Mock data generators
- Task 1.4: Stub analyzers
- Task 1.5: Enhanced UI (timeline, skeleton)
- Task 1.6: State management

### By End of Day 1 (LOD 0 Complete)
- Full app runs with mock data
- Can navigate between views
- Timeline shows 30 mock frames
- Skeleton overlay displays (fake but drawn)
- All data flows through EDN IR

---

## 📚 REFERENCE FOR NEXT TASKS

As you continue:
1. **Always check PLAN.md** for task description
2. **Always check SPEC.md** for schemas
3. **Always check EXAMPLES.md** for code patterns
4. **Always follow .clinerules** for style/conventions

---

## 💡 TIPS FOR SUCCESS

### REPL-Driven Development
Start a REPL for instant feedback:
```bash
npx shadow-cljs cljs-repl renderer
```

Then evaluate code:
```clojure
(require '[combatsys.renderer.views :as views])
(views/app)
```

### Hot Reload
Every save triggers re-compile and UI refresh. No need to restart.

### Think as the Team
Before writing code, ask:
- **Hickey**: Is this pure? Is the data model clear?
- **Carmack**: Is this fast enough?
- **Victor**: How does the user see this?
- **Graham**: Can I ship this today?

---

## 🆘 IF YOU'RE STUCK

1. Re-read the relevant section in CLAUDE.md
2. Check EXAMPLES.md for similar patterns
3. Review the task in PLAN.md
4. Look at error messages carefully (ClojureScript errors are informative)

---

## ✅ TASK 1.1 COMPLETE

**Time spent**: ~2-3 hours  
**Status**: ✅ DONE  
**Next**: Task 1.2 (EDN Schema Definitions)

**You're off to a great start. Keep going!**

---

*Built with ClojureScript, Electron, and the wisdom of Hickey, Carmack, Victor, and Graham.*
