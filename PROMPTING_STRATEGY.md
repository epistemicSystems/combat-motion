# Claude Code Meta-Strategy: Optimal Delegation Patterns
**Project:** CombatSys Motion Analysis Platform  
**Purpose:** Master guide for engineering with Claude Code  
**Audience:** Technical leads, Claude Code operators

---

## Executive Summary

This document defines **optimal delegation patterns** for using Claude Code on the CombatSys project. It synthesizes best practices from Anthropic's documentation with project-specific patterns that maximize velocity while maintaining quality.

**Core Principle**: Claude Code is most effective when given **complete context, explicit constraints, and testable acceptance criteria** in a structured format.

---

## The Three-Phase Delegation Model

### Phase 1: Context Assembly (Human-Led)
**Duration**: 5-10 minutes per task  
**Output**: Complete task specification

```
1. Identify the module/feature to build
2. Gather context:
   - Related code (existing modules)
   - Schemas (input/output data structures)
   - Constraints (performance, dependencies)
   - Examples (REPL-testable code)
3. Write task specification using templates
```

### Phase 2: Implementation (Claude Code-Led)
**Duration**: 10-60 minutes per task  
**Output**: Working code + tests

```
1. Submit task specification to Claude Code
2. Claude generates implementation
3. Claude writes tests
4. Claude validates against acceptance criteria
```

### Phase 3: Validation (Human-Led)
**Duration**: 5-15 minutes per task  
**Output**: Merged, tested code

```
1. Run tests in REPL
2. Verify acceptance criteria
3. Check code quality (style, docs)
4. Merge if passing, refine if not
```

---

## Context Hierarchy: What to Include

### Essential Context (Always Required)

1. **File Path**: Exact location
   ```
   FILE: src/shared/breathing.cljs
   ```

2. **Purpose**: One-sentence goal
   ```
   PURPOSE: Analyze breathing patterns from pose timeline
   ```

3. **Input Schema**: Show exact EDN structure
   ```clojure
   INPUT:
   [{:frame/pose {:pose/landmarks [{:landmark/id :left-shoulder ...}]}}]
   ```

4. **Output Schema**: Show exact EDN structure
   ```clojure
   OUTPUT:
   {:rate-bpm 22.5 :fatigue-windows [...] :insights [...]}
   ```

5. **Constraints**: Performance, edge cases, dependencies
   ```
   CONSTRAINTS:
   - Pure function (no side effects)
   - <100ms for 2-minute session
   - Handle irregular breathing gracefully
   ```

6. **Acceptance Criteria**: Testable conditions
   ```
   ACCEPTANCE:
   - (< 20 (:rate-bpm result) 24) ;; Within ±2 BPM
   - (empty? (:errors result))    ;; No errors
   ```

---

### Supplementary Context (Include When Relevant)

7. **Algorithm**: Pseudocode or references
   ```
   ALGORITHM:
   1. Extract torso landmarks
   2. Compute motion signal
   3. Apply FFT
   4. Find dominant frequency in 0.1-1 Hz
   5. Convert to BPM
   ```

8. **Related Code**: Links to similar modules
   ```
   REFERENCE:
   See src/shared/posture.cljs for similar structure
   ```

9. **Tests**: Example test cases
   ```clojure
   TEST:
   (deftest test-breathing-rate
     (let [result (analyze (mock-breathing-session 22 60))]
       (is (< 20 (:rate-bpm result) 24))))
   ```

10. **Style Guide**: Project-specific conventions
    ```
    STYLE:
    - Pure functions: (defn name [args] ...)
    - Side effects: (defn name! [args] ...)
    - Namespaced keywords: :session/id
    ```

---

## Task Templates by Module Type

### Template A: Pure Function (Functional Core)

**Use Case**: Analyzers, processors, insights

```markdown
TASK: [Module Name]
FILE: src/shared/[module].cljs

PURPOSE:
[One sentence: what this module does]

INPUT SCHEMA:
```clojure
[Show exact EDN structure]
```

OUTPUT SCHEMA:
```clojure
[Show exact EDN structure]
```

CONSTRAINTS:
- Pure functions only (no side effects)
- [Performance target]
- [Edge case handling]
- [Dependencies]

FUNCTIONS:
- (main-fn [input]) → output
- (helper-fn [input]) → intermediate

ALGORITHM:
1. [Step 1]
2. [Step 2]
...

TESTS:
```clojure
(deftest test-name
  (let [result (main-fn input)]
    (is (= expected result))))
```

ACCEPTANCE CRITERIA:
- [Testable condition 1]
- [Testable condition 2]
```

---

### Template B: Stateful Module (Imperative Shell)

**Use Case**: Camera, storage, GPU

```markdown
TASK: [Module Name]
FILE: src/main/[module].cljs

PURPOSE:
[One sentence: what this module does]

SIDE EFFECTS:
- [Effect 1: e.g., accesses camera hardware]
- [Effect 2: e.g., writes to file system]

API:
- (init-resource [config]) → resource-handle
- (use-resource [handle input]) → output
- (cleanup-resource [handle]) → nil

ERROR HANDLING:
- [Error type]: [How to handle]
- [Error type]: [How to handle]

CONSTRAINTS:
- Thread-safe (handle async properly)
- Graceful error handling (never throw uncaught)
- Resource cleanup (always release)

IMPLEMENTATION:
[Specific APIs or libraries to use]

TESTS:
```clojure
(deftest test-lifecycle
  (let [resource (init-resource config)]
    (is (not (:error resource)))
    (cleanup-resource resource)))
```

ACCEPTANCE CRITERIA:
- [Testable condition 1]
- [Testable condition 2]
```

---

### Template C: UI Component (Reagent)

**Use Case**: UI views, components

```markdown
TASK: [Component Name]
FILE: src/renderer/[component].cljs

PURPOSE:
[One sentence: what this component displays]

PROPS:
- :prop-1 [type] - [description]
- :prop-2 [type] - [description]

STATE:
- [Local state if any]

SUBSCRIPTIONS:
- :subscription-1 (from re-frame)

EVENTS:
- :event-1 (dispatched when...)

APPEARANCE:
[Describe visual layout]

INTERACTIONS:
- [User action → system response]

EXAMPLE USAGE:
```clojure
[component-name {:prop-1 value-1}]
```

CONSTRAINTS:
- Pure component (no side effects in render)
- [Performance considerations]
- [Accessibility requirements]
```

---

## Optimal Prompt Structure

### The Three-Block Pattern

Every prompt should have three blocks:

```markdown
[BLOCK 1: CONTEXT]
Create [MODULE] for [FUNCTIONALITY].

FILE: [path]
PURPOSE: [one sentence]
CONTEXT: [background, related code, etc.]

[BLOCK 2: SPECIFICATION]
INPUT: [schema]
OUTPUT: [schema]
CONSTRAINTS: [list]
FUNCTIONS: [list]
ALGORITHM: [steps]

[BLOCK 3: VALIDATION]
TESTS: [example tests]
ACCEPTANCE: [criteria]
EXAMPLE: [REPL-testable code]
```

**Why this works**:
- **Block 1**: Orients Claude to the problem space
- **Block 2**: Provides technical specification
- **Block 3**: Defines success conditions

---

## Progressive Refinement Strategy

### Stage 1: Stub Implementation (LOD 0)
**Goal**: Get structure right, verify interfaces

```markdown
Create STUB for [module].

Return hardcoded values conforming to output schema.
Focus on:
- Correct function signatures
- Docstrings
- Schema validation
```

### Stage 2: Core Logic (LOD 1-2)
**Goal**: Implement main algorithm

```markdown
Replace stub in [module] with real implementation.

REFERENCE: Previous stub at [path]
ALGORITHM: [detailed steps]
```

### Stage 3: Edge Cases (LOD 3-4)
**Goal**: Handle errors and edge cases

```markdown
Enhance [module] with edge case handling.

EXISTING: [module path]
ADD:
- Handle empty input
- Handle extreme values
- Graceful degradation
```

### Stage 4: Optimization (LOD 5+)
**Goal**: Improve performance

```markdown
Optimize [module] for performance.

EXISTING: [module path]
PROFILE RESULTS: [bottlenecks]
TARGET: [performance goal]
```

---

## Common Anti-Patterns (Avoid These)

### ❌ Anti-Pattern 1: Vague Requirements
```markdown
Create a breathing analyzer. Make it good.
```

**Why it fails**: No constraints, no schema, no acceptance criteria.

**Fix**: Use Template A with complete specification.

---

### ❌ Anti-Pattern 2: Missing Context
```markdown
Implement the analyze function.
```

**Why it fails**: No module context, no input/output specification.

**Fix**: Include file path, purpose, schemas.

---

### ❌ Anti-Pattern 3: No Validation
```markdown
Create [module]. [specification]
```

**Why it fails**: No way to verify success.

**Fix**: Add tests and acceptance criteria.

---

### ❌ Anti-Pattern 4: Too Much at Once
```markdown
Create entire breathing analyzer with all features and optimizations.
```

**Why it fails**: Scope too large, hard to debug if something fails.

**Fix**: Break into stages (stub → core → edges → optimize).

---

## Quality Gates

### Gate 1: Specification Complete?
Before submitting to Claude Code:

- [ ] File path specified
- [ ] Purpose is one clear sentence
- [ ] Input schema shown (EDN/code)
- [ ] Output schema shown (EDN/code)
- [ ] Constraints listed
- [ ] Acceptance criteria testable
- [ ] Example usage provided

### Gate 2: Implementation Valid?
After Claude Code returns:

- [ ] Compiles without errors
- [ ] Passes provided tests
- [ ] Meets acceptance criteria
- [ ] Follows project style guide
- [ ] Has docstrings

### Gate 3: Integration Ready?
Before merging:

- [ ] REPL tests pass
- [ ] Integrates with existing modules
- [ ] No regressions in other modules
- [ ] Documentation updated

---

## Velocity Optimization

### Parallel Task Delegation

Identify independent tasks from dependency graph:

```
Week 1 Parallelization:
  Task 1.1 (schema) → [blocks 1.2, 1.3]
  Task 1.2 (stub analyzers) → [can start after 1.1]
  Task 1.3 (UI) → [can start after 1.1]
  
Week 2 Parallelization:
  Task 3.1 (shader) → [independent]
  Task 3.2 (buffer mgmt) → [independent]
  Then merge for 3.3 (integration)
```

Multiple Claude Code sessions can work on independent tasks simultaneously.

---

### Batch Similar Tasks

Group tasks of same type:

```
Batch 1: All schema definitions (1.1, related)
Batch 2: All stub analyzers (1.2, 2.x stubs)
Batch 3: All UI components (1.3, 4.2, 6.1)
```

Use consistent prompts within batch for efficiency.

---

### Reuse Successful Patterns

When a task succeeds, save the prompt as a template:

```markdown
FILE: .prompts/analyzer-template.md

[Successful prompt structure]

USAGE:
1. Copy this template
2. Replace [MODULE] with specific module
3. Update schemas and constraints
4. Submit to Claude Code
```

---

## Debugging Failed Tasks

### Diagnosis Steps

1. **Check compilation**: Does it compile?
   - If no: syntax error in prompt, refine specification
   
2. **Check tests**: Do provided tests pass?
   - If no: specification mismatch, clarify acceptance criteria
   
3. **Check behavior**: Does it meet acceptance criteria?
   - If no: algorithm issue, provide more detail or reference

### Refinement Prompt Template

When initial attempt fails:

```markdown
REFINEMENT: [Module Name]

ISSUE:
[What went wrong]

EXISTING CODE:
[Path to current implementation]

CORRECTION:
[Specific fix needed]

UPDATED CONSTRAINTS:
[Additional constraints based on failure]

UPDATED TESTS:
[Tests that should now pass]
```

---

## Anthropic Best Practices Summary

From Anthropic's Claude Code documentation:

1. **Structured Prompts**: Use markdown formatting for clarity
2. **Complete Context**: Include all relevant information upfront
3. **Testable Criteria**: Acceptance criteria must be verifiable
4. **Iterative Refinement**: Start simple, add complexity
5. **Error Handling**: Specify how to handle errors explicitly
6. **Examples**: Show desired behavior with code examples

---

## Project-Specific Optimizations

### For CombatSys Motion:

1. **Schema-First**: Always start with EDN schema
2. **Pure-First**: Implement pure functions before side effects
3. **REPL-Driven**: All examples must be REPL-testable
4. **LOD Progression**: Follow LOD stages strictly (no jumping ahead)
5. **Style Enforcement**: Include style guide in every prompt

---

## Measuring Effectiveness

### Metrics to Track

| Metric | Target | Measurement |
|--------|--------|-------------|
| Tasks completed per day | 3-5 | Count from TODO.md |
| First-attempt success rate | >80% | Passes tests without refinement |
| Average refinement cycles | <2 | Count prompt iterations |
| Code quality score | >8/10 | Manual review (style, docs, tests) |

### Continuous Improvement

After each milestone (LOD completion):
1. Review which prompts worked well
2. Identify common failure patterns
3. Update templates
4. Share learnings with team

---

## Appendix: Example Session Transcript

### Successful Task: Breathing Analyzer (LOD 2)

**Prompt** (5 minutes to write):
```markdown
Create a ClojureScript module for breathing analysis.

FILE: src/shared/breathing.cljs

PURPOSE:
Analyze breathing patterns from pose timeline.

INPUT:
[{:frame/pose {:pose/landmarks [...]}}]

OUTPUT:
{:rate-bpm 22.5 :fatigue-windows [...] :insights [...]}

[Full specification as per Template A]
```

**Claude Code Response** (20 minutes):
- Generated `src/shared/breathing.cljs` (200 lines)
- Generated `test/shared/breathing_test.cljs` (50 lines)
- All tests pass

**Validation** (5 minutes):
- REPL test: `(breathing/analyze test-timeline)` ✓
- Acceptance: Rate within ±2 BPM ✓
- Style: Conforms to guide ✓

**Total Time**: 30 minutes (spec + gen + validate)

---

## Final Checklist: Am I Ready to Delegate?

Before submitting any task to Claude Code:

- [ ] I have identified the exact file(s) to create/modify
- [ ] I can describe the purpose in one sentence
- [ ] I have shown (not described) the input schema
- [ ] I have shown (not described) the output schema
- [ ] I have listed all constraints (performance, edge cases, deps)
- [ ] I have written testable acceptance criteria
- [ ] I have provided a REPL-testable example
- [ ] I have checked for similar existing code to reference
- [ ] I have estimated the task complexity (simple/medium/complex)
- [ ] I have verified this task is on the critical path for current LOD

If all boxes checked: **submit to Claude Code** ✅

If any boxes unchecked: **gather more context first** ❌

---

**Document Status**: MASTER REFERENCE  
**Owner**: Engineering Lead  
**Usage**: Consult before every Claude Code session
