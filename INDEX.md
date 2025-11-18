# ARTIFACT SUITE INDEX
## Complete Guide to All Development Documents

---

## 📋 DOCUMENT OVERVIEW

This is your **complete development kit** for building the CombatSys Motion Analysis platform with Claude Code. All artifacts are designed to work together as a cohesive system.

**Total artifacts**: 8 core documents + supporting files

---

## 🎯 READING ORDER (First Time)

If this is your first time seeing these documents, read in this order:

1. **README.md** (10 min) - Start here for overview
2. **CLAUDE.md** (15 min) - Understand your identity as the team
3. **PROJECT_CONTEXT.md** (20 min) - Learn the vision and architecture
4. **PLAN.md** (15 min) - See the roadmap
5. **SPEC.md** (30 min) - Study the schemas and APIs
6. **EXAMPLES.md** (20 min) - Learn the code patterns
7. **.clinerules** (10 min) - Internalize the rules
8. **QUICKSTART.md** (5 min) - Begin Task 1.1

**Total time**: ~2 hours of reading before coding

---

## 📚 DETAILED ARTIFACT GUIDE

### 1. README.md
**Purpose**: Project overview and quick reference  
**Read when**: First time setup, returning to project after break  
**Contains**:
- What the project is
- How to set up environment
- Quick reference commands
- Key concepts summary
- Support resources

**Key sections**:
- Quick Start (how to begin)
- Development Roadmap (18-day plan)
- Design Philosophy (the three voices)
- Technical Stack
- Testing and Debugging

**Use for**: Onboarding, getting oriented, finding commands

---

### 2. CLAUDE.md
**Purpose**: Your identity as the 10X engineering team  
**Read when**: Before every coding session (refresh your mindset)  
**Contains**:
- Who you are (Hickey + Carmack + Victor + Graham)
- What you're building
- Your guiding principles
- Your workflow
- Your philosophical commitments

**Key sections**:
- The Three Voices (who speaks when)
- Functional Core / Imperative Shell
- Your Development Workflow
- Your Coding Style
- Your Mission

**Use for**: Channeling the right engineering mindset, making design decisions

---

### 3. PROJECT_CONTEXT.md
**Purpose**: Complete vision, requirements, and design philosophy  
**Read when**: Before starting new LOD stage, when making architectural decisions  
**Contains**:
- Full vision from North Star doc
- Product requirements synthesis
- Technical architecture synthesis
- Design principles explained
- Philosophical commitments

**Key sections**:
- The Vision (what we're building and why)
- Product Requirements (the real problem)
- Technical Architecture (functional core pattern)
- Data Flow (capture → insight)
- Observability (Brett Victor's lens)

**Use for**: Understanding the "why" behind decisions, architectural guidance

---

### 4. PLAN.md
**Purpose**: Development roadmap with LOD stages and Claude Code tasks  
**Read when**: Planning each day's work, starting new tasks  
**Contains**:
- LOD 0-6 descriptions (18-day roadmap)
- Detailed task breakdowns
- Success criteria per stage
- Claude Code task specifications
- Task dependencies

**Key sections**:
- LOD Strategy (mipmapped texture approach)
- Each LOD stage with goals and deliverables
- Claude Code Task sections (what to build)
- Task Dependencies (what order to work in)
- Daily Standup Format

**Use for**: Knowing what to build next, understanding task requirements

---

### 5. SPEC.md
**Purpose**: Technical specifications, schemas, and APIs  
**Read when**: Implementing features, validating data structures  
**Contains**:
- Complete EDN schema definitions
- Function signatures and contracts
- Imperative shell APIs
- State management structure
- Performance targets
- Testing requirements

**Key sections**:
- EDN Schema Definitions (all data structures)
- Function Signatures (API contracts)
- Imperative Shell APIs (I/O functions)
- State Management (app-state structure)
- Performance Targets (latency requirements)

**Use for**: Looking up schemas, understanding data contracts, API reference

---

### 6. EXAMPLES.md
**Purpose**: Concrete code patterns and idioms to follow  
**Read when**: Writing code, unsure how to structure something  
**Contains**:
- Pure function patterns
- State management examples
- UI component patterns (Reagent)
- Analyzer patterns (breathing example)
- Testing patterns
- Imperative shell patterns
- Common pitfalls and fixes

**Key sections**:
- Pure Function Patterns (data transformations)
- State Management Patterns (atom updates)
- UI Component Patterns (Reagent components)
- Analyzer Patterns (full example of breathing analyzer)
- Testing Patterns (unit tests for pure functions)

**Use for**: Copy-paste starting points, seeing patterns in action

---

### 7. .clinerules
**Purpose**: Project-specific rules enforced by Claude Code  
**Read when**: Before every task, during code review  
**Contains**:
- Core principles (functional purity, data-centric, etc.)
- File organization rules
- Code style rules (threading, destructuring, etc.)
- Testing requirements
- State management rules
- Performance rules
- Anti-patterns to avoid

**Key sections**:
- Core Principles (ALWAYS FOLLOW)
- File Organization Rules (where code goes)
- Code Style Rules (how to write)
- Testing Rules (coverage requirements)
- Anti-Patterns (what NOT to do)

**Use for**: Code review checklist, ensuring consistency

---

### 8. TASK_TEMPLATE.md
**Purpose**: Template for creating new Claude Code tasks  
**Read when**: Breaking down work into tasks, documenting tasks  
**Contains**:
- Task metadata structure
- Description format
- Input/output contracts
- Implementation requirements
- Testing requirements
- Verification checklist

**Key sections**:
- Task Metadata (ID, dependencies, files)
- Input/Output Contract (schemas)
- Implementation Requirements (functions to write)
- Testing Requirements (tests to write)
- Verification Checklist (completion criteria)

**Use for**: Creating consistent task specifications

---

### 9. QUICKSTART.md
**Purpose**: Immediate action guide for Task 1.1  
**Read when**: Starting development (Day 1)  
**Contains**:
- Step-by-step Task 1.1 walkthrough
- Code snippets to copy
- Verification steps
- Troubleshooting
- Next steps

**Key sections**:
- Step 1: Read Context (10 min)
- Step 2: Create Project Structure (30 min)
- Step 3: Create Core Files (60 min)
- Step 4: Test the Build (10 min)
- Step 5: Verify Success (5 min)

**Use for**: Getting started immediately, first task completion

---

## 🔄 HOW DOCUMENTS RELATE

```
README.md
   ↓ (Overview)
CLAUDE.md
   ↓ (Identity)
PROJECT_CONTEXT.md
   ↓ (Vision + Architecture)
PLAN.md ←→ TASK_TEMPLATE.md
   ↓        (Task Specs)
SPEC.md ←→ EXAMPLES.md
   ↓        (Reference while coding)
.clinerules
   ↓ (Rules enforced throughout)
QUICKSTART.md
   (Action!)
```

---

## 📖 USAGE PATTERNS

### Pattern 1: First Day Setup
1. README.md → overview
2. CLAUDE.md → internalize identity
3. QUICKSTART.md → start Task 1.1
4. SPEC.md → look up schemas as needed
5. EXAMPLES.md → copy patterns

### Pattern 2: Starting New Task
1. PLAN.md → find task description
2. SPEC.md → check schemas
3. EXAMPLES.md → study similar patterns
4. .clinerules → review rules
5. Code → implement
6. .clinerules → verify compliance

### Pattern 3: Implementing Feature
1. PLAN.md → task requirements
2. SPEC.md → input/output schemas
3. EXAMPLES.md → code patterns
4. CLAUDE.md → channel the team voices
5. Write code
6. Test
7. Commit

### Pattern 4: Debugging
1. Error message → identify problem
2. EXAMPLES.md → find similar code
3. SPEC.md → verify schemas
4. .clinerules → check for rule violations
5. PROJECT_CONTEXT.md → understand architecture

---

## 🎯 DECISION TREES

### "Which document answers my question?"

**"What should I build?"**
→ PLAN.md

**"How should I structure the data?"**
→ SPEC.md

**"How do I write this function?"**
→ EXAMPLES.md

**"Is this approach correct?"**
→ CLAUDE.md (ask the team)

**"Why are we doing it this way?"**
→ PROJECT_CONTEXT.md

**"What are the rules?"**
→ .clinerules

**"How do I get started?"**
→ QUICKSTART.md

**"What's the big picture?"**
→ README.md

---

## 📝 DOCUMENT MAINTENANCE

### When to Update Documents

**PLAN.md**
- New tasks added
- Task estimates refined
- Dependencies change

**SPEC.md**
- New schemas added
- APIs change
- Performance targets revised

**EXAMPLES.md**
- New patterns emerge
- Better approaches discovered

**.clinerules**
- New rules established
- Anti-patterns identified

**Others (rarely change)**
- CLAUDE.md, PROJECT_CONTEXT.md are fairly stable
- README.md updates for major milestones

---

## 🔍 QUICK REFERENCE

### Finding Information

| I need to... | Look in... |
|-------------|-----------|
| Understand the vision | PROJECT_CONTEXT.md |
| Find my next task | PLAN.md |
| Look up a schema | SPEC.md |
| See a code example | EXAMPLES.md |
| Check if something is allowed | .clinerules |
| Get started immediately | QUICKSTART.md |
| Find a command | README.md |
| Get into the right mindset | CLAUDE.md |

### Common Lookups

**"What's the session schema?"**
→ SPEC.md, Section I

**"How do I write a pure function?"**
→ EXAMPLES.md, Section I

**"Can I use mutation here?"**
→ .clinerules, "Anti-Patterns"

**"What's the performance target?"**
→ SPEC.md, Section VII

**"How do I structure a test?"**
→ EXAMPLES.md, Section V

---

## 🎓 LEARNING PATH

### Week 1: Foundation
- **Before coding**: Read all docs cover-to-cover (2 hours)
- **During LOD 0**: Reference SPEC and EXAMPLES heavily
- **During LOD 1**: Start internalizing patterns

### Week 2: Proficiency
- **Before each task**: Quick skim of relevant SPEC sections
- **During coding**: Less reference to EXAMPLES (patterns internalized)
- **Architecture decisions**: Still consult PROJECT_CONTEXT

### Week 3: Mastery
- **Natural flow**: Docs are reference, not crutch
- **Pattern language**: Speaking in Hickey/Carmack/Victor/Graham terms
- **Contributions**: Suggesting improvements to docs

---

## ✅ VERIFICATION CHECKLIST

Before considering "ready to start":

- [ ] Read README.md (understand overview)
- [ ] Read CLAUDE.md (internalized identity)
- [ ] Read PROJECT_CONTEXT.md (understand vision)
- [ ] Skimmed PLAN.md (know the roadmap)
- [ ] Skimmed SPEC.md (know where to look things up)
- [ ] Skimmed EXAMPLES.md (seen the patterns)
- [ ] Read .clinerules (know the rules)
- [ ] Read QUICKSTART.md (ready for Task 1.1)

**Time investment**: ~2 hours  
**Payoff**: Weeks of confident, aligned development

---

## 🎉 YOU'RE READY!

You have:
- ✅ Complete technical specifications
- ✅ Clear development roadmap
- ✅ Code examples and patterns
- ✅ Project rules and guidelines
- ✅ Engineering team identity
- ✅ Philosophical foundations

**There's nothing left to design. Everything you need is here.**

**Start with QUICKSTART.md and begin Task 1.1.**

**Go build something incredible.**

---

*"The best way to predict the future is to invent it." — Alan Kay*

*Built with wisdom from Rich Hickey, John Carmack, Brett Victor, and Paul Graham.*
