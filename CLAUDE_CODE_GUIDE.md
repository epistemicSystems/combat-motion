# CLAUDE CODE - Complete Development Kit

## Overview

This directory contains a **complete suite of artifacts** optimized for **Claude Code** agentic development, following Anthropic's latest best practices (November 2025).

The project implements a **camera-only motion analysis platform** using **ClojureScript** with a **functional core / imperative shell** architecture.

---

## Quick Start with Claude Code

```bash
cd combatsys-motion

# Start Claude Code
claude

# First prompt (let Claude explore)
> Read CLAUDE.md, then give me a 30-second overview of this project.

# Second prompt (channel the team)
> I want to implement Task 1.1 (Camera Integration) from TASKS.md. 
> Think hard about the approach. Channel Rich Hickey for design, 
> John Carmack for performance awareness, and YC hackers for shipping speed.
```

---

## Artifact Suite

### Core Documents (Read These First)

| File | Purpose | When to Read |
|------|---------|--------------|
| **CLAUDE.md** | Primary instructions for Claude Code | Every session (auto-loaded) |
| **CONTEXT.md** | Philosophy & role-play framework | Before starting work |
| **README.md** | Project setup & architecture | Initial onboarding |

### Development Guides

| File | Purpose | When to Read |
|------|---------|--------------|
| **SPEC.md** | Technical specifications | Architecture questions |
| **PLAN.md** | LOD progression & roadmap | Planning sprints |
| **TASKS.md** | Granular task breakdown | Before implementing |
| **WORKFLOWS.md** | Common development workflows | When stuck or debugging |

### Reference Documents

| File | Purpose | When to Read |
|------|---------|--------------|
| **GETTING_STARTED.md** | Setup instructions & LOD 0 demo | First run |
| **ARCHITECTURE.md** | System architecture details | Deep dives |
| **TODO.md** | Future features & ideas | Planning ahead |

---

## The Role-Play Framework

### Who You're Managing

When using Claude Code on this project, you're a **Team Lead Orchestrator** managing:

1. **Rich Hickey** (Functional philosopher) - Data-first design
2. **John Carmack** (Performance pragmatist) - GPU optimization
3. **10x Y-Combinator Hackers** - Ship fast, iterate
4. **10x Google Engineers** - Production quality

### How to Channel Each Voice

**For architecture/design questions**:
```
> Think hard about this from Rich Hickey's perspective. 
> Is this a pure data transformation? Can we avoid side effects here?
```

**For performance issues**:
```
> Channel John Carmack. Profile this code and tell me where the bottleneck is. 
> Should we optimize, or is it fast enough?
```

**For feature implementation**:
```
> YC hacker mode: Get this working end-to-end TODAY. 
> What's the fastest path to a demo-able prototype?
```

**For robustness/testing**:
```
> Google engineer perspective: What edge cases am I missing? 
> What tests should I write?
```

---

## Extended Thinking Commands

Claude Code supports special commands for deeper reasoning:

| Command | Budget | Use When |
|---------|--------|----------|
| `think` | Standard | Normal complexity |
| `think hard` | Medium | Architecture decisions |
| `think harder` | High | Complex algorithms |
| `ultrathink` | Very High | Multi-system integration |
| `megathink` | Maximum | Critical design choices |

**Example**:
```
> Ultrathink about how to integrate WebGPU shaders 
> for Eulerian magnification while maintaining the 
> functional core / imperative shell separation.
```

---

## Development Workflows

### Workflow 1: Starting a New Feature

```bash
# 1. Read the task
> Read TASKS.md and find Task 1.2 (MediaPipe Integration)

# 2. Research
> Search the codebase for how we handle external JS libraries. 
> Show me examples from existing code.

# 3. Plan (use extended thinking!)
> Think hard about the best way to wrap MediaPipe for ClojureScript. 
> Consider: data conversion, error handling, performance.

# 4. Implement
> Implement Task 1.2 following the plan. 
> Test in REPL as you go. Commit when working.

# 5. Validate
> Run the integration test from TASKS.md section 1.6. 
> Did it pass?
```

---

### Workflow 2: Debugging

```bash
# 1. Describe the problem
> The skeleton overlay isn't drawing. Video works but no pose visualization.

# 2. Ask Claude to investigate
> Think about potential causes. Channel Google engineer perspective 
> and think through edge cases. What could be wrong?

# 3. Claude explores the code
# (Claude will use file_read, bash_tool, etc. to investigate)

# 4. Get recommendations
> Based on your investigation, what are the top 3 most likely causes? 
> Give me debugging steps for each.

# 5. Implement fix
> Implement your recommended fix. Test in REPL to confirm it works.
```

---

### Workflow 3: Optimization

```bash
# 1. Measure first
> Profile the capture loop and show me where time is being spent.

# 2. Channel Carmack
> Think like John Carmack. Looking at these profile results, 
> what's the bottleneck? Should we optimize, or is it fast enough?

# 3. Get recommendations
> If optimization is needed, give me 3 options ranked by 
> effort vs. impact. Include tradeoffs.

# 4. Implement chosen optimization
> Let's go with Option 2 (skip frames). Implement it and 
> verify the performance improvement.
```

---

## File Organization

### Pure Functional Core (`src/shared/`)

**All domain logic lives here. 90% of the codebase.**

```
src/shared/
├── schema.cljs      # Data model definitions
├── mocks.cljs       # Mock data generators
├── breathing.cljs   # Breathing analyzer (pure)
├── posture.cljs     # Posture analyzer (pure)
└── utils.cljs       # Shared utilities (pure)
```

**Key principle**: Every function is `data → data`. No side effects.

---

### Imperative Shell (`src/renderer/` & `src/main/`)

**All I/O and side effects here. 10% of the codebase.**

```
src/renderer/
├── core.cljs        # App initialization
├── state.cljs       # re-frame state management
├── views.cljs       # Reagent UI components
├── camera.cljs      # Camera I/O (side effects!)
└── mediapipe.cljs   # MediaPipe interop (side effects!)

src/main/
└── core.cljs        # Electron window management
```

**Key principle**: Side effects isolated and explicit.

---

## Testing Strategy

### REPL-Driven Development (Primary)

```clojure
;; Test as you code (fastest feedback)
(require '[combatsys.breathing :as breathing])
(require '[combatsys.mocks :as mocks])

(def session (mocks/mock-breathing-session 60 22))
(def result (breathing/analyze session))
(:rate-bpm (:breathing (:session/analysis result)))
;; => 22
```

### Unit Tests (Secondary)

```bash
# Run test suite
npx shadow-cljs compile test
```

### Integration Tests (Final validation)

```bash
# Manual integration testing
npm start
# Follow checklist in TASKS.md section 1.6
```

---

## Common Patterns

### Pattern 1: Pure Analyzer Module

```clojure
(ns combatsys.new-analyzer
  "Docstring explaining what this analyzes.")

(defn analyze
  "Main entry point.
   Input: session map
   Output: same session with :session/analysis :new-feature populated
   Pure function - no side effects."
  [session]
  (let [timeline (:session/timeline session)
        computed-metric (compute-something timeline)]
    (assoc-in session
              [:session/analysis :new-feature]
              {:metric computed-metric
               :insights [...]})))
```

### Pattern 2: Re-frame Event Handler

```clojure
;; Pure event handler
(rf/reg-event-db
 ::my-event
 (fn [db [_ args]]
   ;; Pure transformation: db → new-db
   (update-in db [:some :path] transform-fn args)))
```

### Pattern 3: Reagent Component

```clojure
(defn my-component []
  (let [data @(rf/subscribe [::state/my-data])]
    ;; Pure render function
    [:div "Display " data]))
```

---

## Performance Targets

### Real-Time Path

| Operation | Budget | LOD | Notes |
|-----------|--------|-----|-------|
| Frame capture | 10ms | 1 | getUserMedia |
| Pose estimation | 30ms | 1 | MediaPipe GPU |
| State update | 1ms | 1 | Pure functions |
| Render | 5ms | 1 | Canvas drawing |
| **Total** | **46ms** | - | **~22fps** |

### Offline Path

| Operation | Time | Notes |
|-----------|------|-------|
| Eulerian magnification | Minutes | GPU compute shader |
| FFT analysis | Seconds | CPU, parallelizable |
| Session analysis | <1 second | Pure functions |

---

## LOD Progression

### Current: LOD 0 ✅

- EDN schema complete
- Mock data generators working
- Stub analyzers (breathing, posture)
- Basic UI functional

### Next: LOD 1 (Days 2-4)

**Goal**: Real camera + MediaPipe

**Tasks**:
1. Camera integration (getUserMedia)
2. MediaPipe pose detection
3. Skeleton overlay
4. Session recording

**See TASKS.md for detailed breakdown**

### Future: LOD 2-6

See PLAN.md for complete roadmap through MVP.

---

## Anthropic Best Practices Applied

This project follows Anthropic's official Claude Code best practices:

✅ **CLAUDE.md file** - Auto-loaded into every session
✅ **Extended thinking** - Use "think hard" for complex problems
✅ **Test-driven development** - Tests first, implementation second
✅ **Structured workflows** - Explore → Plan → Code → Commit
✅ **Context optimization** - Concise, relevant documentation
✅ **Safety controls** - Pre-approved commands, permission for risky ops
✅ **REPL-driven** - Continuous testing during development

---

## Example Session Flow

### Session 1: Implementing Camera Integration

```bash
claude

# Claude automatically reads CLAUDE.md

> I want to implement Task 1.1 from TASKS.md (Camera Integration). 
> First, read the task and understand what we're building. 
> Then, think hard about the approach.

# Claude reads task, thinks deeply, proposes approach

> Your approach looks good. One question: how do we handle 
> camera permission denial gracefully?

# Claude suggests error handling strategy

> Great. Let's implement it. Start with the camera.cljs file. 
> Test each function in REPL as you write it.

# Claude implements, tests, commits

> Perfect! Now let's wire it into the UI. 
> Add a "Start Camera" button that triggers this.

# Claude modifies views.cljs, state.cljs

> Excellent. Let's test end-to-end. Run npm start 
> and tell me what happens when I click "Start Camera".

# Manual testing, iterate on issues

> All working! Commit with a clear message explaining what we built.

# Claude commits

> Great session! What should we tackle next?
```

---

## Key Commands

### Development

```bash
# Watch and compile
npx shadow-cljs watch main renderer

# Start Electron
npm start

# Connect REPL
npx shadow-cljs cljs-repl renderer
```

### Testing

```bash
# Run tests
npx shadow-cljs compile test

# Manual testing
npm start
# Follow checklist in TASKS.md
```

### Building

```bash
# Production build
npx shadow-cljs release main renderer

# Package
npm run package
```

---

## Tips for Working with Claude Code

### 1. Use Extended Thinking Liberally

```
❌ "Implement breathing analysis"
✅ "Think hard about breathing analysis. What's the data transformation? 
   What edge cases exist? Then implement it."
```

### 2. Reference Specific Documents

```
❌ "How should I structure this?"
✅ "Read SPEC.md section on module boundaries, then tell me how 
   to structure this new analyzer."
```

### 3. Channel Team Members

```
❌ "Is this fast enough?"
✅ "Channel John Carmack. Profile this and tell me if optimization 
   is needed or if it's fast enough."
```

### 4. Test Continuously

```
❌ "Write the whole module"
✅ "Write one function. Test it in REPL. Then write the next one."
```

### 5. Commit Working Slices

```
❌ Wait until entire feature is done
✅ Commit each working piece (camera init, pose detection, etc.)
```

---

## Troubleshooting

### Claude seems confused about architecture

```
> Read CONTEXT.md section on "The Three Laws". 
> Then re-read my question with those principles in mind.
```

### Claude suggests impure functions

```
> Rich Hickey would say this has side effects. 
> Can we restructure as a pure function with side effects 
> isolated to the imperative shell?
```

### Performance concerns

```
> Carmack mode: Don't optimize until we profile. 
> Let's measure first and see if this is actually slow.
```

### Not sure how to proceed

```
> Think hard about the next step. Read WORKFLOWS.md 
> to see if there's a pattern that fits. If not, 
> channel the team and propose an approach.
```

---

## Success Metrics

You're using this well when:

- ✅ Claude references the right documents
- ✅ Claude channels appropriate team voices
- ✅ Code maintains functional purity
- ✅ Commits are frequent and working
- ✅ Tests are written before implementation
- ✅ Performance is measured, not assumed

---

## Resources

- **Anthropic Claude Code Best Practices**: https://www.anthropic.com/engineering/claude-code-best-practices
- **ClojureScript Guide**: https://clojurescript.org/guides/quick-start
- **re-frame Documentation**: https://day8.github.io/re-frame
- **Shadow-CLJS Guide**: https://shadow-cljs.github.io/docs/UsersGuide.html

---

**You now have everything you need to build brilliantly with Claude Code. Let's ship it!** 🚀
