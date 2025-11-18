# Claude Code Prompt Guide
**Project:** CombatSys Motion Analysis Platform  
**Purpose:** How to effectively delegate tasks to Claude Code  
**Last Updated:** 2025-11-17

---

## Table of Contents
1. [Introduction](#introduction)
2. [Task Structure](#task-structure)
3. [Prompt Templates](#prompt-templates)
4. [Best Practices](#best-practices)
5. [Common Patterns](#common-patterns)
6. [Troubleshooting](#troubleshooting)

---

## Introduction

### What is Claude Code?

Claude Code is Anthropic's agentic coding tool that can:
- Write complete modules from specifications
- Refactor existing code
- Add tests
- Fix bugs
- Optimize performance

### When to Use Claude Code

✅ **Good use cases:**
- Implementing well-specified modules (schema, analyzers, UI components)
- Writing tests for pure functions
- Creating boilerplate (file structure, configs)
- Porting algorithms from pseudocode or other languages

❌ **Poor use cases:**
- Vague requirements ("make it better")
- Architectural decisions (use human judgment)
- Complex debugging (use REPL + human reasoning)

---

## Task Structure

### Anatomy of a Good Task

Every Claude Code task should have:

1. **File(s) to create/modify**: Explicit file paths
2. **Purpose**: One-sentence goal
3. **Input schema**: What data the module receives
4. **Output schema**: What data the module produces
5. **Constraints**: Performance, edge cases, dependencies
6. **Acceptance criteria**: How to verify success
7. **Example usage**: REPL-testable code

### Template

```markdown
TASK: [Module Name]
FILE: [path/to/file.cljs]

PURPOSE:
[One sentence describing what this module does]

INPUT SCHEMA:
[EDN or code showing expected input structure]

OUTPUT SCHEMA:
[EDN or code showing expected output structure]

CONSTRAINTS:
- [Performance requirement, e.g., "<5ms per call"]
- [Edge case handling, e.g., "gracefully handle missing data"]
- [Dependencies, e.g., "uses combatsys.schema for validation"]

ACCEPTANCE CRITERIA:
[List of testable conditions that must be true]

EXAMPLE USAGE:
```clojure
;; REPL test
(require '[combatsys.module :as module])

(def input {...})
(def output (module/function input))

;; Expected result
(= output {...})
;; => true
```

ADDITIONAL CONTEXT:
[Optional: links to algorithms, papers, related code]
```

---

## Prompt Templates

### Template 1: Pure Function Module

Use this for modules in the functional core (analyzers, processors, insights).

```markdown
Create a ClojureScript module for [FUNCTIONALITY].

FILE: src/shared/[module-name].cljs

PURPOSE:
[What this module does, e.g., "Analyze breathing patterns from pose timeline"]

INPUT:
[EDN showing input structure]

OUTPUT:
[EDN showing output structure]

CONSTRAINTS:
- Pure functions only (no side effects)
- Must handle edge cases: [list cases]
- Performance target: [target, e.g., "<100ms for 2-minute session"]

FUNCTIONS:
- (analyze [timeline]) → analysis-map
- (helper-fn [input]) → intermediate-result
- ...

TESTS:
Include unit tests for:
- Happy path (normal input)
- Edge cases (empty input, extreme values)
- Error conditions (invalid input)

EXAMPLE:
```clojure
(require '[combatsys.[module] :as module])

(def timeline [...])
(def result (module/analyze timeline))

;; Expected
(:key result)
;; => expected-value
```

ADDITIONAL CONTEXT:
[Links to algorithms, papers, or existing code]
```

**Example: Breathing Analyzer**

```markdown
Create a ClojureScript module for breathing analysis.

FILE: src/shared/breathing.cljs

PURPOSE:
Analyze breathing patterns from pose timeline. Extract breathing rate (BPM), 
detect fatigue windows, and generate coaching insights.

INPUT:
```clojure
;; Timeline: vector of frames
[{:frame/index 0
  :frame/timestamp-ms 0
  :frame/pose
  {:pose/landmarks [{:landmark/id :left-shoulder :x 0.45 :y 0.42 ...} ...]}}
 ...]
```

OUTPUT:
```clojure
{:rate-bpm 22.5
 :depth-score 0.75
 :rhythm-regularity 0.88
 :fatigue-windows
 [{:start-ms 120000 :end-ms 145000 :severity 0.92}]
 :insights
 [{:insight/title "Breathing window shortened"
   :insight/description "Your aerobic capacity is degrading"
   :insight/severity :medium
   :insight/recommendation "Practice nasal breathing during drilling"}]}
```

CONSTRAINTS:
- Pure functions (no side effects, no atoms)
- Handle sessions from 30s to 5 minutes
- Performance: <100ms for 2-minute session
- Gracefully handle irregular breathing (don't crash)
- Detect breathing rate in range 6-60 BPM (0.1-1 Hz)

FUNCTIONS:
- (analyze [timeline]) → analysis-map
- (extract-torso-motion [timeline]) → signal (vector of floats)
- (detect-breathing-rate [signal]) → bpm (float)
- (detect-fatigue-windows [signal]) → windows (vector of maps)
- (generate-insights [rate windows]) → insights (vector of maps)

ALGORITHM:
1. Extract torso landmarks (shoulders, chest estimate)
2. Compute centroid of torso per frame
3. Compute motion magnitude (distance between consecutive frames)
4. Smooth signal (moving average, window=5)
5. Apply FFT to signal
6. Find dominant frequency in 0.1-1 Hz range
7. Convert to BPM (* 60)
8. Detect fatigue windows (where signal < threshold)

TESTS:
Include unit tests for:
- Normal breathing (22 BPM, 60 seconds)
- Breath hold (detect fatigue window)
- Irregular breathing (handle gracefully)
- Empty timeline (return error or default values)

EXAMPLE:
```clojure
(require '[combatsys.breathing :as breathing])
(require '[combatsys.mocks :as mocks])

(def timeline (mocks/mock-breathing-session 22 60))
(def result (breathing/analyze timeline))

(:rate-bpm result)
;; => 21.8 (should be within 20-24)

(count (:fatigue-windows result))
;; => 0 (no breath holds in this session)
```
```

---

### Template 2: Imperative Module (Side Effects)

Use this for modules in the imperative shell (camera, storage, GPU).

```markdown
Create a ClojureScript module for [FUNCTIONALITY] with side effects.

FILE: [path/to/file.cljs]

PURPOSE:
[What this module does, e.g., "Access webcam and capture frames"]

SIDE EFFECTS:
[List of side effects, e.g., "Accesses hardware camera", "Writes to disk"]

API:
- (init-resource [config]) → resource-handle
- (use-resource [handle input]) → output
- (cleanup-resource [handle]) → nil

ERROR HANDLING:
- [Error type]: [How to handle, e.g., "Camera not found: return error map"]
- [Error type]: [How to handle]

EXAMPLE:
```clojure
(require '[combatsys.[module] :as module])

(def resource (module/init-resource {:option value}))
(def result (module/use-resource resource input))
(module/cleanup-resource resource)
```

CONSTRAINTS:
- Thread-safe (handle async operations properly)
- Graceful error handling (never throw uncaught exceptions)
- Resource cleanup (always release resources)
```

**Example: Camera Module**

```markdown
Create a ClojureScript module for webcam access.

FILE: src/main/camera.cljs

PURPOSE:
Access webcam via getUserMedia API and capture frames.

SIDE EFFECTS:
- Accesses hardware camera (requires OS permission)
- Reads video stream (continuous I/O)

API:
- (init-camera [config]) → camera-handle
  - config: {:resolution [width height] :fps number}
  - Returns: camera handle or error map
- (capture-frame [camera-handle]) → frame-data
  - Returns: {:frame-data buffer :timestamp-ms number}
- (release-camera [camera-handle]) → nil
  - Stops video stream, releases camera

ERROR HANDLING:
- Camera not found: return {:error :camera-not-found}
- Permission denied: return {:error :permission-denied}
- Capture failed: return {:error :capture-failed :reason ...}

CONSTRAINTS:
- Thread-safe (camera may be accessed concurrently)
- Graceful error handling (never throw uncaught exceptions)
- Always release camera on cleanup
- Log errors for debugging

IMPLEMENTATION:
Use Electron's getUserMedia API:
```javascript
navigator.mediaDevices.getUserMedia({video: {...}})
  .then(stream => ...)
  .catch(error => ...)
```

EXAMPLE:
```clojure
(require '[combatsys.camera :as camera])

(def cam (camera/init-camera {:resolution [1920 1080] :fps 30}))

(if (:error cam)
  (println "Error:" (:error cam))
  (let [frame (camera/capture-frame cam)]
    (println "Captured frame at" (:timestamp-ms frame))
    (camera/release-camera cam)))
```
```

---

### Template 3: UI Component (Reagent)

Use this for Reagent/React components.

```markdown
Create a Reagent component for [FUNCTIONALITY].

FILE: src/renderer/[component-name].cljs

PURPOSE:
[What this component displays/does]

PROPS:
[Map of props with types and descriptions]

STATE:
[Local state managed by this component, if any]

SUBSCRIPTIONS:
[re-frame subscriptions this component depends on]

EVENTS:
[re-frame events this component dispatches]

APPEARANCE:
[Describe visual layout, styling requirements]

EXAMPLE USAGE:
```clojure
[component-name {:prop-1 value-1 :prop-2 value-2}]
```

CONSTRAINTS:
- Use Reagent (React wrapper)
- Pure component (no side effects in render)
- Accessible (keyboard navigation, ARIA labels if needed)
```

**Example: Timeline Scrubber**

```markdown
Create a Reagent component for a video timeline scrubber.

FILE: src/renderer/timeline.cljs

PURPOSE:
Display session timeline with frame markers, allow scrubbing to any frame.

PROPS:
- :session - session map (with :session/timeline)
- :current-frame-index - integer (which frame is selected)
- :on-seek - function (called when user scrubs, receives frame-index)

STATE:
- :dragging? - boolean (true while user is dragging)

SUBSCRIPTIONS:
- :current-session (from re-frame)
- :timeline-position (from re-frame)

EVENTS:
- :seek-to-frame (dispatched when user clicks/drags)

APPEARANCE:
- Horizontal bar (full width)
- Frame markers (small vertical lines)
- Current position indicator (larger line, different color)
- Hover: show frame index tooltip
- Draggable: user can click-and-drag to scrub

INTERACTIONS:
- Click: jump to frame
- Drag: scrub continuously (throttle events to 10fps)
- Keyboard: arrow left/right to step frame-by-frame

EXAMPLE USAGE:
```clojure
[timeline-scrubber
 {:session @(rf/subscribe [:current-session])
  :current-frame-index @(rf/subscribe [:current-frame-index])
  :on-seek #(rf/dispatch [:seek-to-frame %])}]
```

CONSTRAINTS:
- Performant (handle 1000+ frame timelines)
- Accessible (keyboard navigation)
- Responsive (adapt to window width)
```

---

## Best Practices

### 1. Provide Context

✅ **Good**:
```markdown
Create a breathing analyzer.

Context: This is part of a motion analysis system. We have a timeline of 
pose frames (MediaPipe landmarks), and we want to detect breathing rate 
from torso motion using FFT.

Related modules:
- combatsys.schema (defines frame structure)
- combatsys.fourier (FFT implementation)
```

❌ **Bad**:
```markdown
Create a breathing analyzer.

[No context provided]
```

---

### 2. Show, Don't Tell

✅ **Good**:
```markdown
INPUT:
```clojure
{:frame/pose
 {:pose/landmarks
  [{:landmark/id :left-shoulder :x 0.45 :y 0.42 :z -0.05}
   {:landmark/id :right-shoulder :x 0.55 :y 0.43 :z -0.04}
   ...]}}
```

OUTPUT:
```clojure
{:rate-bpm 22.5
 :depth-score 0.75}
```
```

❌ **Bad**:
```markdown
Input is a frame with pose data.
Output is breathing analysis with rate and depth.
```

---

### 3. Specify Edge Cases

✅ **Good**:
```markdown
EDGE CASES:
- Empty timeline: return {:rate-bpm nil :error "No data"}
- Timeline < 10 seconds: return {:rate-bpm nil :error "Too short"}
- No torso visible: return {:rate-bpm nil :error "No torso detected"}
- Irregular breathing: estimate best-effort rate, flag as low-confidence
```

❌ **Bad**:
```markdown
Handle edge cases gracefully.
```

---

### 4. Provide Testable Examples

✅ **Good**:
```markdown
EXAMPLE:
```clojure
(require '[combatsys.breathing :as breathing])

(def timeline (vector (repeat 900 mock-frame)))  ;; 30s @ 30fps
(def result (breathing/analyze timeline))

;; Test
(< 20 (:rate-bpm result) 24)
;; => true (22 BPM ± 2)

(empty? (:fatigue-windows result))
;; => true (no breath holds)
```
```

❌ **Bad**:
```markdown
Test that it works.
```

---

### 5. Separate Concerns

✅ **Good**:
```markdown
TASK 1: Extract torso motion (pure function)
FILE: src/shared/breathing.cljs

TASK 2: Detect breathing rate from signal (pure function)
FILE: src/shared/breathing.cljs

TASK 3: Generate insights (pure function)
FILE: src/shared/breathing.cljs
```

❌ **Bad**:
```markdown
TASK: Do everything for breathing analysis in one giant function.
```

---

## Common Patterns

### Pattern 1: Pure Function with Schema

```markdown
Create [MODULE] for [FUNCTIONALITY].

FILE: src/shared/[module].cljs

INPUT: [schema]
OUTPUT: [schema]
CONSTRAINTS: Pure, no side effects, [performance]

FUNCTIONS:
- (main-fn [input]) → output
- (helper-fn [input]) → intermediate

TESTS: [examples]
```

### Pattern 2: Stateful Module with Lifecycle

```markdown
Create [MODULE] for [FUNCTIONALITY] with side effects.

FILE: src/main/[module].cljs

LIFECYCLE:
- (init [config]) → handle
- (use [handle input]) → output
- (cleanup [handle]) → nil

ERROR HANDLING: [cases]
CONSTRAINTS: Thread-safe, cleanup on error

TESTS: [examples]
```

### Pattern 3: UI Component with Props

```markdown
Create Reagent component [COMPONENT].

FILE: src/renderer/[component].cljs

PROPS: [map]
STATE: [local]
SUBSCRIPTIONS: [re-frame]
EVENTS: [re-frame]

APPEARANCE: [layout]
INTERACTIONS: [behavior]

EXAMPLE: [usage code]
```

---

## Troubleshooting

### Problem: Claude Code doesn't understand the task

**Solution**: Add more context.
- Show example input/output data
- Link to similar code in the project
- Provide pseudocode or algorithm steps

---

### Problem: Generated code doesn't match project style

**Solution**: Specify style requirements.
```markdown
STYLE:
- Use kebab-case for function names
- Use namespaced keywords (:namespace/key)
- Pure functions in (defn name [args] body) format
- Side effects in (defn name! [args] body) format (with !)
- Docstrings for all public functions
```

---

### Problem: Generated code has bugs

**Solution**: Improve acceptance criteria.
```markdown
ACCEPTANCE CRITERIA:
- Must pass these specific tests: [list tests]
- Must handle these edge cases: [list cases]
- Must not throw exceptions for invalid input
```

---

### Problem: Generated code is slow

**Solution**: Specify performance requirements.
```markdown
PERFORMANCE:
- Target: <5ms per call
- Constraints: No unnecessary allocation, use transients for large updates
- Profile with: (time (repeatedly 1000 #(function input)))
```

---

### Problem: Claude Code creates files in wrong location

**Solution**: Use absolute paths.
```markdown
FILE: src/shared/breathing.cljs

[Not: "breathing.cljs" or "the breathing file"]
```

---

## Advanced Techniques

### Technique 1: Incremental Refinement

Instead of one big task, break into stages:

```markdown
STAGE 1: Create stub (hardcoded return values)
STAGE 2: Implement core logic (main algorithm)
STAGE 3: Add edge case handling
STAGE 4: Optimize performance
STAGE 5: Add comprehensive tests
```

---

### Technique 2: Test-Driven Prompts

Start with tests, then ask Claude to implement:

```markdown
Here are the tests that must pass:

```clojure
(deftest test-breathing-rate
  (let [timeline (mock-breathing-session 22 60)
        result (breathing/analyze timeline)]
    (is (< 20 (:rate-bpm result) 24))))

(deftest test-breath-hold
  (let [timeline (mock-breathing-with-hold 20 30 35)
        result (breathing/analyze timeline)]
    (is (= 1 (count (:fatigue-windows result))))))
```

Implement the breathing/analyze function to make these tests pass.
```

---

### Technique 3: Reference Existing Code

Point to similar code in the project:

```markdown
Create posture analyzer similar to breathing analyzer.

REFERENCE:
See src/shared/breathing.cljs for structure and style.

DIFFERENCES:
- Instead of torso motion, analyze head/shoulder positions
- Instead of breathing rate, compute forward head distance and shoulder imbalance
```

---

## Checklist: Before Submitting a Task

- [ ] File path is specified (absolute path)
- [ ] Purpose is clear (one sentence)
- [ ] Input schema is shown (EDN or code)
- [ ] Output schema is shown (EDN or code)
- [ ] Constraints are listed (performance, edge cases, dependencies)
- [ ] Acceptance criteria are testable
- [ ] Example usage is provided (REPL code)
- [ ] Edge cases are specified
- [ ] Error handling is described
- [ ] Style guide is referenced (if needed)

---

## Appendix: Example Tasks (Full Prompts)

### Example 1: Schema Definition

See TODO.md Task 1.1 for a complete example.

### Example 2: Pure Function Module

See TODO.md Task 2.1 (Torso Motion Extraction) for a complete example.

### Example 3: Stateful Module

See TODO.md Task 1.4 (Camera Integration) for a complete example.

### Example 4: UI Component

See TODO.md Task 1.3 (Basic UI) for a complete example.

---

**Document Status**: LIVING DOCUMENT  
**Update Frequency**: As patterns emerge  
**Owner**: Engineering Lead
