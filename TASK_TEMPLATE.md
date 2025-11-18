# CLAUDE CODE TASK TEMPLATE
## Use this template for each development task

---

## TASK METADATA

```yaml
task_id: "X.Y"              # e.g., "1.1", "2.3"
lod_stage: "LOD N"          # e.g., "LOD 0", "LOD 1"
title: "Brief Task Name"
estimated_hours: N
dependencies: ["X.Y", ...]  # Other tasks that must complete first
files_affected:
  - path/to/file.cljs
  - path/to/test.cljs
```

---

## TASK DESCRIPTION

### What This Task Achieves
[1-2 sentences describing the goal]

### Why It Matters
[How this fits into the larger system]

### Success Criteria
- ✅ [Specific, measurable outcome 1]
- ✅ [Specific, measurable outcome 2]
- ✅ [Specific, measurable outcome 3]

---

## INPUT/OUTPUT CONTRACT

### Input Schema
```clojure
;; What data this task expects as input
;; Reference SPEC.md for full schema definitions

(s/def ::input-type
  (s/keys :req-un [::field1 ::field2]))

;; Example:
{:field1 "value"
 :field2 42}
```

### Output Schema
```clojure
;; What data this task produces as output
;; Must conform to schema in SPEC.md

(s/def ::output-type
  (s/keys :req-un [::result1 ::result2]))

;; Example:
{:result1 [1 2 3]
 :result2 {:key "value"}}
```

---

## IMPLEMENTATION REQUIREMENTS

### Core Functions to Implement
```clojure
;; 1. Main entry point
(defn main-function
  "Brief description"
  [input]
  ,,,)

;; 2. Helper function A
(defn helper-a
  "Brief description"
  [data]
  ,,,)

;; 3. Helper function B
(defn helper-b
  "Brief description"
  [data]
  ,,,)
```

### Constraints
- **Performance**: [Target latency/throughput if applicable]
- **Purity**: [Must be pure / May have side effects]
- **Error handling**: [How to handle invalid input]
- **Edge cases**: [Specific cases to handle]

---

## TESTING REQUIREMENTS

### Test Cases to Implement
```clojure
;; 1. Happy path
(deftest test-main-function-normal
  (testing "Normal case with valid input"
    (let [input {...}
          result (main-function input)]
      (is (s/valid? ::output-type result))
      (is (= expected-value (:result1 result))))))

;; 2. Edge case
(deftest test-main-function-edge-case
  (testing "Edge case: empty input"
    (let [result (main-function {})]
      (is (= default-value result)))))

;; 3. Error handling
(deftest test-main-function-error
  (testing "Invalid input returns nil"
    (is (nil? (main-function invalid-input)))))
```

---

## DEPENDENCIES

### External Libraries Needed
- [library name] - [why needed]

### Internal Modules Required
- `combatsys.module-name` - [what functions/data]

### Resources Required
- [File/model/shader if applicable]

---

## IMPLEMENTATION HINTS

### Algorithmic Approach
[Brief explanation of the algorithm or approach to use]

### Potential Pitfalls
- ⚠️ [Common mistake to avoid]
- ⚠️ [Edge case to watch for]

### Code Patterns to Follow
Reference EXAMPLES.md sections:
- [Section name in EXAMPLES.md]

---

## VERIFICATION CHECKLIST

Before considering this task complete:

### Compilation
- [ ] Code compiles without warnings
- [ ] `npx shadow-cljs watch` runs clean

### Testing
- [ ] All unit tests pass
- [ ] `npx shadow-cljs test` reports success
- [ ] Edge cases covered
- [ ] Error handling tested

### Schema Conformance
- [ ] Input validated with spec
- [ ] Output validated with spec
- [ ] `(s/valid? ::type data)` returns true

### Code Quality
- [ ] Functions are pure (if in `shared/`)
- [ ] Side effects marked with `!`
- [ ] Docstrings present
- [ ] Examples in docstrings
- [ ] Threading macros used appropriately
- [ ] Destructuring used where applicable

### Performance (if on critical path)
- [ ] Profiled with `profile-fn`
- [ ] Meets performance target
- [ ] No unnecessary allocations

### Documentation
- [ ] Docstrings complete
- [ ] Comments explain WHY, not WHAT
- [ ] Examples provided

### Integration
- [ ] Integrates with existing code
- [ ] Doesn't break prior LOD stages
- [ ] `npm start` still works

---

## COMPLETION CRITERIA

### Deliverables
- [ ] Source file: `src/.../filename.cljs`
- [ ] Test file: `test/.../filename_test.cljs`
- [ ] Documentation: Docstrings in code
- [ ] Integration: Works end-to-end

### Demo
[How to demonstrate this task is complete]
```bash
# Commands to run
npm start

# What to observe
[Expected behavior/output]
```

---

## EXAMPLE TASK (FILLED IN)

### TASK METADATA
```yaml
task_id: "3.1"
lod_stage: "LOD 2"
title: "Torso Motion Extraction"
estimated_hours: 6
dependencies: ["2.3"]  # Needs pose processing
files_affected:
  - src/shared/breathing.cljs
  - test/shared/breathing_test.cljs
```

### TASK DESCRIPTION

**What**: Extract magnitude of torso landmark motion over time from a timeline of poses.

**Why**: This is the foundation for breathing analysis. Breathing causes subtle motion in the torso (chest, shoulders, abdomen) that we can detect and analyze.

**Success Criteria**:
- ✅ Returns vector of motion magnitudes (one per frame)
- ✅ Motion signal correlates with breathing (visual inspection)
- ✅ Works with real MediaPipe poses (handles occlusions)

### INPUT/OUTPUT CONTRACT

```clojure
;; Input: Timeline of frames with poses
(s/def ::input ::schema/timeline)

;; Output: Vector of motion magnitudes
(s/def ::output (s/coll-of number?))

;; Example input:
[{:frame/pose {:landmarks [...]}}
 {:frame/pose {:landmarks [...]}}
 ...]

;; Example output:
[0.12 0.15 0.13 0.08 ...]
```

### CORE FUNCTIONS

```clojure
(defn extract-torso-landmarks
  "Select torso landmarks from pose"
  [pose]
  ,,,)

(defn compute-motion-magnitude
  "Compute average motion magnitude from landmarks"
  [landmarks prev-landmarks]
  ,,,)

(defn extract-torso-motion
  "Main entry point: timeline → motion signal"
  [timeline]
  ,,,)
```

### TESTING

```clojure
(deftest test-torso-motion-extraction
  (testing "Returns vector of correct length"
    (let [timeline (mocks/mock-timeline 30)
          signal (extract-torso-motion timeline)]
      (is (= 30 (count signal)))
      (is (every? number? signal)))))

(deftest test-motion-magnitude
  (testing "Motion magnitude is always non-negative"
    (let [timeline (mocks/mock-timeline 100)
          signal (extract-torso-motion timeline)]
      (is (every? #(>= % 0) signal)))))
```

---

## NOTES FOR CLAUDE CODE

### Before Starting
1. Read this task template
2. Review relevant sections in SPEC.md
3. Study similar patterns in EXAMPLES.md
4. Think as the team (Hickey + Carmack + Victor + Graham)

### During Implementation
- Keep functions small and focused
- Test as you go (REPL-driven)
- Profile if on critical path
- Document decisions

### After Completion
- Run full checklist above
- Commit with descriptive message
- Move to next task

---

**Remember**: You're building something incredible. Channel the wisdom of the masters.
