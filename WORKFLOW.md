# WORKFLOW.md - Development Workflows

## Claude Code Role-Play Configuration

**You are the Lead Architect orchestrating a virtual team**:

- **Rich Hickey**: Functional purity, data orientation, simplicity
- **John Carmack**: Performance pragmatism, critical path optimization
- **10x Y-Combinator Hackers**: Fast iteration, user focus, shipping
- **10x Google Engineers**: System design, testing, reliability

**Before every task**: Channel all four perspectives. Ask:
1. **Hickey**: "Is this simple? Can I make it pure?"
2. **Carmack**: "Is this on the critical path? Should I profile first?"
3. **Y-Combinator**: "Can I ship something testable in 4 hours?"
4. **Google**: "How do I verify this works? What breaks?"

---

## Daily Workflow

### Morning Setup (5 minutes)

```bash
# 1. Pull latest changes
git pull origin main

# 2. Start ClojureScript compiler
npx shadow-cljs watch main renderer
# Wait for "Build completed" message

# 3. Start Electron app (separate terminal)
npm start

# 4. Connect REPL (separate terminal)
npx shadow-cljs cljs-repl renderer
```

**Verify setup**:
- ✅ App window opens
- ✅ Hot reload works (edit a file, see change)
- ✅ REPL responds to `(+ 1 1)` → `2`

### Task Selection (10 minutes)

**Think harder** about today's work:

1. **Review TASKS.md**: Which task is next?
2. **Check dependencies**: Are prerequisites complete?
3. **Estimate effort**: 2-4 hours? If >4 hours, break it down.
4. **Read context**: CLAUDE.md, SPEC.md, related code

### Implementation Loop (Repeat)

#### Step 1: Plan (15 minutes)

**Before writing code**, answer:

```
What am I building?
  - [One sentence goal]

What needs to change?
  - File 1: Add function X
  - File 2: Modify function Y
  - File 3: New namespace Z

How will I test it?
  - REPL examples
  - Manual verification steps
  - Edge cases to consider

What could go wrong?
  - Dependency issues?
  - Performance concerns?
  - Breaking changes?
```

Use "think harder" to explore alternatives.

#### Step 2: Interface First (15 minutes)

**Write function signatures and docstrings BEFORE implementation**:

```clojure
(defn my-new-function
  "What this function does. Why it exists.
   
   Input: What it takes
   Output: What it returns
   
   Example:
   (my-new-function {:foo 1}) => {:bar 2}
   
   Pure function - no side effects."
  [arg1 arg2]
  ;; TODO: Implement
  nil)
```

**Benefits**:
- Forces clarity on API design
- Makes dependencies explicit
- Enables TDD (write tests against interface)

#### Step 3: Implement Incrementally (60-90 minutes)

**Small chunks, test constantly**:

```clojure
;; Chunk 1: Basic case (15 min)
(defn my-function [x]
  (if (pos? x)
    (* x 2)
    0))

;; Test in REPL
(my-function 5)   ;; => 10
(my-function -3)  ;; => 0

;; Chunk 2: Handle edge cases (15 min)
(defn my-function [x]
  (cond
    (nil? x) 0
    (pos? x) (* x 2)
    :else 0))

;; Test in REPL
(my-function nil)  ;; => 0

;; Chunk 3: Optimize (if needed) (15 min)
;; ...
```

**Red flags**:
- 🚩 Writing >50 lines without testing
- 🚩 Mutating state in pure functions
- 🚩 Complex logic in UI components
- 🚩 Adding dependencies without approval

#### Step 4: REPL Verification (10 minutes)

**Every function must be testable in REPL**:

```clojure
;; Load namespace
(require '[combatsys.breathing :as breathing] :reload)

;; Test pure function
(def test-data (mocks/breathing-timeline 60 22))
(breathing/extract-torso-motion test-data)
;; => [{:timestamp-ms 0 :motion-magnitude 0.02} ...]

;; Verify result shape
(first *1)
;; => {:timestamp-ms 0 :motion-magnitude 0.02}

;; Check edge cases
(breathing/extract-torso-motion [])
;; => []
```

**If something breaks**:
1. Read error message carefully
2. Check function signature
3. Verify input data shape
4. Add `println` debugging
5. Ask Claude Code for help

#### Step 5: Commit (5 minutes)

**Atomic commits with clear messages**:

```bash
# Stage changes
git add src/shared/breathing.cljs

# Commit with descriptive message
git commit -m "Add torso motion extraction (Task 2.1)

- Extract torso landmarks from timeline
- Compute motion magnitude per frame
- Apply temporal smoothing
- Tested in REPL with mock data"

# Push frequently
git push origin main
```

**Commit guidelines**:
- ✅ One logical change per commit
- ✅ Working code (compiles, no errors)
- ✅ Clear message (what + why)
- ❌ Don't commit broken code
- ❌ Don't commit commented-out code

### End of Day (10 minutes)

**Leave clean state for tomorrow**:

1. **Final commit** (or stash if incomplete)
2. **Update TASKS.md** (mark completed tasks)
3. **Write notes** (blockers, questions, next steps)
4. **Push changes** (so other team members can continue)

---

## Special Workflows

### Workflow 1: Adding New Analyzer (LOD 2-4)

**Context**: All analyzers follow same pattern.

**Steps**:

1. **Create namespace**:
   ```bash
   touch src/shared/my-analyzer.cljs
   ```

2. **Write interface**:
   ```clojure
   (ns combatsys.shared.my-analyzer)
   
   (defn analyze
     "Main entry point: session → analyzed-session
      
      Input:  {:session/id ... :session/timeline [...]}
      Output: Same session with :session/analysis populated"
     [session]
     ;; TODO
     session)
   ```

3. **Implement helper functions** (pure, testable):
   ```clojure
   (defn extract-features [timeline] ...)
   (defn compute-metric [features] ...)
   (defn generate-insights [metric] ...)
   ```

4. **Wire into pipeline** (state.cljs):
   ```clojure
   (rf/reg-event-db
    ::analyze-session
    (fn [db [_ session-id]]
      (let [session (get-in db [:sessions session-id])
            analyzed (-> session
                         breathing/analyze
                         posture/analyze
                         my-analyzer/analyze)]  ; Add here
        (assoc-in db [:sessions session-id] analyzed))))
   ```

5. **Add UI panel** (views.cljs):
   ```clojure
   (defn my-metrics []
     (let [analysis @(rf/subscribe [::state/my-analysis])]
       [panel "My Analysis"
        [:div "Results: " (:my-metric analysis)]]))
   ```

6. **Test end-to-end**:
   - Load demo session
   - Click "Analyze Session"
   - Verify new metrics appear

**Time estimate**: 4-6 hours

---

### Workflow 2: Debugging Performance Issues

**Context**: Real-time loop is slow or UI is laggy.

**Steps**:

1. **Profile first** (don't guess):
   ```clojure
   (defn profile-fn [f label]
     (let [start (js/performance.now)
           result (f)
           end (js/performance.now)]
       (println (str label ": " (- end start) "ms"))
       result))
   
   ;; Wrap suspect functions
   (profile-fn #(process-frame frame) "process-frame")
   ```

2. **Identify bottleneck**:
   - Camera capture: <10ms ✅
   - Pose estimation: 45ms ⚠️ TOO SLOW
   - State update: <1ms ✅
   - Rendering: 8ms ✅

3. **Optimize bottleneck**:
   ```clojure
   ;; Option 1: Throttle (easiest)
   (def process-every-nth-frame 3)  ; 10fps instead of 30fps
   
   ;; Option 2: Use lighter model
   {:modelType "lite"}  ; Instead of "full"
   
   ;; Option 3: Lower resolution
   [640 480]  ; Instead of [1920 1080]
   ```

4. **Measure again**:
   - Pose estimation: 18ms ✅ FIXED

5. **Document solution**:
   ```clojure
   ;; In CLAUDE.md
   ## Performance Notes
   - MediaPipe 'full' model too slow (45ms)
   - Using 'lite' model at 640x480 (18ms)
   - Processing every 3rd frame for 10fps pose updates
   ```

**Carmack's rule**: Profile → Optimize → Measure → Document

---

### Workflow 3: Integrating External Library

**Context**: Need to use JavaScript library (e.g., FFT, video encoding).

**Steps**:

1. **Research library** (10 minutes):
   - Check npm page
   - Read API docs
   - Verify browser/Node.js compatibility

2. **Install**:
   ```bash
   npm install fft.js
   ```

3. **Create ClojureScript wrapper**:
   ```clojure
   (ns combatsys.shared.fourier
     (:require ["fft.js" :as FFT]))
   
   (defn fft [signal]
     "ClojureScript wrapper around fft.js.
      Input: Vector of floats
      Output: Vector of {:frequency Float :magnitude Float}"
     (let [fft-obj (FFT. (count signal))
           out (js/Float64Array. (count signal))
           _result (.realTransform fft-obj out (clj->js signal))]
       ;; Convert JS result to ClojureScript
       (mapv (fn [i]
               {:frequency (/ i (count signal))
                :magnitude (aget out i)})
             (range (count signal)))))
   ```

4. **Test in REPL**:
   ```clojure
   (require '[combatsys.shared.fourier :as fourier])
   
   (def signal [1 0 -1 0 1 0 -1 0])
   (fourier/fft signal)
   ;; => [{:frequency 0 :magnitude ...} ...]
   ```

5. **Document usage** (in namespace docstring):
   ```clojure
   (ns combatsys.shared.fourier
     "FFT utilities using fft.js library.
      
      Example:
      (fft [1 0 -1 0 1 0 -1 0])
      => [{:frequency 0 :magnitude 0} ...]"
     (:require ["fft.js" :as FFT]))
   ```

**Time estimate**: 1-2 hours

---

### Workflow 4: Test-Driven Development (Recommended)

**Context**: For complex algorithms (FFT, motion magnification).

**Steps**:

1. **Write tests first** (examples in docstring):
   ```clojure
   (defn detect-breathing-rate
     "Detects breathing rate from motion signal.
      
      Test cases:
      (detect-breathing-rate (synthetic-signal 60 22)) => ~22.0
      (detect-breathing-rate (synthetic-signal 60 15)) => ~15.0
      (detect-breathing-rate []) => nil"
     [motion-signal]
     ;; TODO: Implement
     nil)
   ```

2. **Implement to pass tests**:
   ```clojure
   (defn detect-breathing-rate [motion-signal]
     (when (seq motion-signal)
       (let [fft-result (fft (map :motion-magnitude motion-signal))
             dominant-freq (find-peak fft-result 0.1 0.5)]
         (* dominant-freq 60))))  ; Hz → BPM
   ```

3. **Verify in REPL**:
   ```clojure
   (detect-breathing-rate (synthetic-signal 60 22))
   ;; => 21.8 (close enough!)
   ```

4. **Iterate until accurate**:
   - Adjust windowing
   - Tune peak detection
   - Handle edge cases

**Y-Combinator style**: Ship working prototype, refine based on real data.

---

## Communication with Claude Code

### Effective Prompts

**Good prompts are**:
- ✅ Specific about the goal
- ✅ Reference existing code/patterns
- ✅ Include success criteria
- ✅ Ask for "think harder" when complex

**Examples**:

```
❌ BAD: "Fix the breathing analyzer"

✅ GOOD: "Think harder about why the breathing rate detection 
is returning 0 bpm. The input motion signal has 1800 samples 
with periodic oscillation (~22 bpm). Check:
1. Is FFT window size correct?
2. Is frequency band (0.1-0.5 Hz) appropriate?
3. Are we converting Hz → BPM correctly?
Show me the intermediate values for debugging."
```

```
❌ BAD: "Make it faster"

✅ GOOD: "Profile the pose estimation step and identify 
bottlenecks. Current: 45ms per frame, target: <30ms.
Suggest optimizations (model size, resolution, throttling).
Measure before and after."
```

### Using Extended Thinking

**When to use**:
- Architecture decisions
- Complex algorithms (FFT, WebGPU shaders)
- Debugging subtle bugs
- Performance optimization strategies

**Keywords** (in order of reasoning budget):
- "think" (basic)
- "think hard" (more time)
- "think harder" (even more)
- "ultrathink" (maximum)

**Example**:
```
"Ultrathink about how to integrate WebGPU compute shaders 
for Eulerian magnification. Consider:
1. Shader language (WGSL)
2. Frame upload/download pipeline
3. Memory management
4. Fallback for systems without WebGPU
Provide detailed implementation plan with code examples."
```

---

## Troubleshooting Workflows

### Problem: App Won't Start

**Checklist**:
1. Is `shadow-cljs watch` running? Check Terminal 1.
2. Did ClojureScript compile? Look for "Build completed".
3. Are there compile errors? Read error messages carefully.
4. Is Electron installed? Run `npm install`.
5. Try clean rebuild: `npm run clean && npx shadow-cljs watch main renderer`

### Problem: Hot Reload Not Working

**Checklist**:
1. Is `shadow-cljs watch` running?
2. Did you save the file?
3. Are there syntax errors? Check Terminal 1 output.
4. Try hard refresh: Cmd+Shift+R (macOS) / Ctrl+Shift+R (Windows/Linux)
5. Restart `shadow-cljs watch` if stuck.

### Problem: REPL Not Responding

**Checklist**:
1. Is `shadow-cljs cljs-repl renderer` connected?
2. Try disconnecting and reconnecting.
3. Check for infinite loops or exceptions.
4. Restart REPL: Exit and run `npx shadow-cljs cljs-repl renderer` again.

### Problem: Test Failing

**Checklist**:
1. Read error message completely.
2. Check input data format (schema match?).
3. Test function in isolation (REPL).
4. Add `println` debugging.
5. Verify assumptions (units, coordinate systems, etc.).

---

## Git Workflow

### Branch Strategy (Simple)

**Main branch only** (for single developer):
- All commits go to `main`
- Keep `main` always working
- Commit frequently (every 1-2 hours)

**Multi-developer** (future):
- Feature branches: `git checkout -b feature/breathing-analysis`
- Pull requests for review
- Merge to `main` when complete

### Commit Messages

**Template**:
```
[Task X.Y] Short description

- Bullet point of what changed
- Another change
- Why this change was made (if non-obvious)

Tested: [REPL | Manual | Both]
```

**Examples**:
```
[Task 1.2] Integrate MediaPipe pose estimation

- Add mediapipe.cljs wrapper around tfjs
- Convert JS keypoints to EDN landmarks
- Validate pose confidence > 0.5

Tested: REPL with sample image, manual with live camera
```

```
[Task 2.1] Extract torso motion from timeline

- Filter to torso landmarks (shoulders, hips)
- Compute frame-to-frame motion magnitude
- Apply temporal smoothing (3-frame window)

Tested: REPL with mock and real sessions
```

### Before Committing

**Run this checklist**:
```bash
# 1. Code compiles
npx shadow-cljs compile main renderer
# Look for warnings/errors

# 2. App runs
npm start
# Verify no crashes

# 3. No debug code left
git diff
# Remove console.log, commented code, TODOs

# 4. Format is good
# (ClojureScript doesn't need formatting tool)

# 5. Commit
git add [files]
git commit -m "[message]"
git push origin main
```

---

## Documentation Workflow

### When to Update Docs

**CLAUDE.md**: When workflow changes
- New commands
- New patterns
- Common issues

**SPEC.md**: When data model changes
- New schema fields
- New validation rules
- New interfaces

**PLAN.md**: When timeline changes
- Task estimates off
- New features added
- Priorities shift

**TASKS.md**: As tasks complete
- Mark complete: `- [x]`
- Add notes/issues
- Update estimates

**This file (WORKFLOW.md)**: When workflow improves
- Better patterns discovered
- New tools integrated
- Lessons learned

### Doc Update Template

```markdown
## [Date] Update by [Name]

**What changed**: [Description]

**Why**: [Reason]

**Impact**: [Who needs to know]

**Example**: [Code/command example]
```

---

## Success Patterns (Things That Work)

### Pattern 1: Interface-First Development
Write function signatures and docstrings before implementing. Forces clarity.

### Pattern 2: REPL-Driven Development
Test every function in REPL before moving on. Catches bugs early.

### Pattern 3: Incremental Commits
Commit working code every 1-2 hours. Easy to revert, clear history.

### Pattern 4: Profile Before Optimizing
Measure first. Optimize bottlenecks. Measure again. Don't guess.

### Pattern 5: Pure Functions Everywhere
90% of code should be pure. Easier to test, debug, and reason about.

---

## Anti-Patterns (Things to Avoid)

### Anti-Pattern 1: Big Bang Implementation
Writing 500 lines without testing. Hard to debug when it breaks.

### Anti-Pattern 2: Premature Optimization
Optimizing before profiling. Wastes time on non-bottlenecks.

### Anti-Pattern 3: Side Effects in Core
Mutating state or doing I/O in `src/shared/`. Breaks purity.

### Anti-Pattern 4: Uncommented Magic Numbers
`(* x 0.367)` without explanation. What is 0.367?

### Anti-Pattern 5: Ignoring REPL Errors
"It'll probably work in the app." Test in REPL first!

---

**Remember**: You're orchestrating experts. Think like all four: Hickey (purity), Carmack (performance), YC (speed), Google (reliability).

**Always use "think harder" for complex decisions.**
