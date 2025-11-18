# CombatSys Motion Analysis - Documentation Index
**Complete Navigation Guide**

---

## Quick Start Path

**New to the project?** Follow this reading order:

1. **[README.md](README.md)** (5 min) - Project overview and quick start
2. **[ARCHITECTURE.md](ARCHITECTURE.md)** (15 min) - System design and philosophy
3. **[SPEC.md](SPEC.md)** (20 min) - Technical specification
4. **[PLAN.md](PLAN.md)** (15 min) - Development roadmap

**Ready to code?** Continue with:

5. **[PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)** (10 min) - File organization
6. **[PROMPT_GUIDE.md](PROMPT_GUIDE.md)** (15 min) - How to use Claude Code
7. **[TODO.md](TODO.md)** (ongoing) - Task list with prompts

**Leading the project?** Study:

8. **[PROMPTING_STRATEGY.md](PROMPTING_STRATEGY.md)** (20 min) - Meta-strategy
9. **[CHANGELOG.md](CHANGELOG.md)** (ongoing) - Version history

---

## Documentation by Purpose

### For Understanding the System

| Document | Purpose | Time to Read |
|----------|---------|--------------|
| [README.md](README.md) | High-level overview, quick start | 5 min |
| [SPEC.md](SPEC.md) | Complete technical specification | 20 min |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Design patterns, philosophy | 15 min |
| [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) | File organization | 10 min |

### For Development

| Document | Purpose | Time to Read |
|----------|---------|--------------|
| [PLAN.md](PLAN.md) | LOD-based roadmap, 3 weeks to MVP | 15 min |
| [TODO.md](TODO.md) | Concrete tasks with acceptance criteria | Ongoing |
| [PROMPT_GUIDE.md](PROMPT_GUIDE.md) | How to write effective Claude Code prompts | 15 min |
| [CHANGELOG.md](CHANGELOG.md) | Version history and release notes | 5 min |

### For Engineering Leadership

| Document | Purpose | Time to Read |
|----------|---------|--------------|
| [PROMPTING_STRATEGY.md](PROMPTING_STRATEGY.md) | Meta-strategy for Claude Code delegation | 20 min |
| [PLAN.md](PLAN.md) | Sprint planning, dependencies | 15 min |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Architectural decisions | 15 min |

---

## Documentation by Role

### If you are a: **Developer**

**Must Read**:
1. README.md - Get oriented
2. PROJECT_STRUCTURE.md - Understand file layout
3. TODO.md - Pick a task
4. PROMPT_GUIDE.md - Learn how to delegate

**Reference**:
- SPEC.md - For schemas and API details
- ARCHITECTURE.md - For design patterns

### If you are a: **Tech Lead**

**Must Read**:
1. ARCHITECTURE.md - Understand design philosophy
2. PLAN.md - Review roadmap and milestones
3. PROMPTING_STRATEGY.md - Master delegation patterns
4. TODO.md - Track progress

**Reference**:
- SPEC.md - For technical details
- CHANGELOG.md - For release planning

### If you are a: **Claude Code Operator**

**Must Read**:
1. PROMPT_GUIDE.md - Learn effective prompting
2. TODO.md - See task templates
3. PROMPTING_STRATEGY.md - Understand meta-patterns

**Reference**:
- SPEC.md - For schemas and constraints
- PROJECT_STRUCTURE.md - For file paths

---

## Key Concepts by Document

### SPEC.md - Technical Specification
- System requirements (hardware, software)
- Data models (EDN schemas for session, frame, analysis)
- Module specifications (pose, breathing, posture, etc.)
- Performance targets (real-time vs offline)
- UI specifications (views, components)
- Testing requirements

### PLAN.md - Development Plan
- LOD (Level of Detail) strategy
- 7 stages from blueprint to production (3 weeks)
- Weekly milestones
- Task dependencies
- Risk mitigation

### TODO.md - Task List
- Concrete tasks organized by LOD stage
- Each task has:
  - File path, purpose, schemas
  - Constraints, acceptance criteria
  - Claude Code prompts
  - Validation steps

### ARCHITECTURE.md - System Architecture
- Design philosophy (Hickey + Carmack + Graham)
- Layered architecture (UI, state, shell, core)
- Component descriptions
- Data flow diagrams
- Performance considerations

### PROMPT_GUIDE.md - Claude Code Guide
- Task structure templates
- Best practices (show don't tell, edge cases)
- Common patterns (pure function, stateful, UI)
- Troubleshooting

### PROMPTING_STRATEGY.md - Meta-Strategy
- Three-phase delegation model
- Context hierarchy (essential vs supplementary)
- Task templates by type
- Quality gates
- Velocity optimization

### PROJECT_STRUCTURE.md - File Organization
- Directory layout
- Module dependencies
- File descriptions (purpose, functions, deps)
- Development workflow
- Code style guide

### CHANGELOG.md - Version History
- LOD milestones
- Features added per version
- Known issues
- Future roadmap

---

## Critical Decision Points

### Before Starting Development

**Questions to Answer**:
1. Do I understand the architecture? → Read ARCHITECTURE.md
2. Do I know what to build? → Read SPEC.md
3. Do I know how to build it? → Read PLAN.md
4. Where do files go? → Read PROJECT_STRUCTURE.md

### Before Each Task

**Questions to Answer**:
1. Is this task clearly specified? → Check TODO.md
2. Do I have complete context? → Use PROMPT_GUIDE.md templates
3. How do I validate success? → Check acceptance criteria
4. Is this on the critical path? → Check PLAN.md dependencies

### When Stuck

**Troubleshooting**:
1. Compilation error? → Check PROJECT_STRUCTURE.md for correct paths
2. Unclear requirements? → Re-read SPEC.md for that module
3. Task too large? → Break down using PLAN.md LOD stages
4. Claude Code not working? → Review PROMPTING_STRATEGY.md

---

## Document Maintenance

### Update Frequency

| Document | Update Trigger | Frequency |
|----------|---------------|-----------|
| README.md | Major milestones | Weekly |
| SPEC.md | Requirements change | As needed |
| PLAN.md | Roadmap change | Weekly |
| TODO.md | Task completion | Daily |
| ARCHITECTURE.md | Design decisions | As needed |
| PROMPT_GUIDE.md | Pattern emerges | Weekly |
| PROMPTING_STRATEGY.md | Meta-pattern found | Monthly |
| PROJECT_STRUCTURE.md | File reorg | As needed |
| CHANGELOG.md | Version release | Per release |

### Responsibility

| Document | Owner | Reviewers |
|----------|-------|-----------|
| README.md | Tech Lead | All |
| SPEC.md | Tech Lead | Engineers |
| PLAN.md | Tech Lead | Engineers |
| TODO.md | All Engineers | Tech Lead |
| ARCHITECTURE.md | Tech Lead | Senior Engineers |
| PROMPT_GUIDE.md | Claude Code Lead | All |
| PROMPTING_STRATEGY.md | Claude Code Lead | Tech Lead |
| PROJECT_STRUCTURE.md | Tech Lead | All |
| CHANGELOG.md | Release Manager | Tech Lead |

---

## Documentation Principles

### 1. Single Source of Truth
- Each concept has ONE authoritative document
- No duplication across documents
- Cross-reference liberally

### 2. Actionable Over Descriptive
- Show code examples, not just descriptions
- Provide templates, not just guidelines
- Include acceptance criteria, not just goals

### 3. Living Documents
- Update continuously, don't let docs rot
- Version with code (same commits)
- Review during milestones

### 4. Accessibility
- Use clear headings for navigation
- Provide reading time estimates
- Include quick reference sections

---

## Quick Reference Cards

### Task Template (Copy-Paste)

```markdown
TASK: [Module Name]
FILE: [path]

PURPOSE: [one sentence]

INPUT:
```clojure
[schema]
```

OUTPUT:
```clojure
[schema]
```

CONSTRAINTS:
- [constraint 1]
- [constraint 2]

ACCEPTANCE:
- [criteria 1]
- [criteria 2]

EXAMPLE:
```clojure
(require '[combatsys.module :as m])
(m/function input)
;; => expected
```
```

### Validation Checklist (Copy-Paste)

```
Before submitting to Claude Code:
- [ ] File path specified
- [ ] Purpose clear
- [ ] Input schema shown
- [ ] Output schema shown
- [ ] Constraints listed
- [ ] Acceptance criteria testable
- [ ] Example provided

After receiving code:
- [ ] Compiles
- [ ] Tests pass
- [ ] Meets criteria
- [ ] Follows style
- [ ] Has docstrings
```

---

## FAQ

**Q: Where do I start?**  
A: Read README.md, then pick a task from TODO.md that matches your skill level.

**Q: How do I write a good Claude Code prompt?**  
A: Use templates from PROMPT_GUIDE.md. Include schemas, constraints, examples.

**Q: What if my task is too big?**  
A: Break it into LOD stages from PLAN.md. Start with stub, then add features.

**Q: How do I know if my task is done?**  
A: Check acceptance criteria in TODO.md. All must pass.

**Q: Where should this file go?**  
A: Check PROJECT_STRUCTURE.md for module organization.

**Q: What's the difference between PROMPT_GUIDE and PROMPTING_STRATEGY?**  
A: PROMPT_GUIDE is tactical (how to write one prompt). PROMPTING_STRATEGY is strategic (how to structure entire workflow).

**Q: Do I need to read everything?**  
A: No. Use "Documentation by Role" section above to find what's relevant to you.

---

## External References

### Anthropic Documentation
- Claude Code Official Docs: https://docs.claude.com/
- Prompt Engineering Guide: https://docs.anthropic.com/prompting

### Project Technologies
- ClojureScript: https://clojurescript.org/
- Reagent: https://reagent-project.github.io/
- re-frame: https://day8.github.io/re-frame/
- Electron: https://www.electronjs.org/
- MediaPipe: https://google.github.io/mediapipe/
- WebGPU: https://gpuweb.github.io/gpuweb/

### Philosophical Influences
- Rich Hickey, "Simple Made Easy": https://www.infoq.com/presentations/Simple-Made-Easy/
- John Carmack .plan archives: http://www.armadilloaerospace.com/n.x/johnc/
- Paul Graham essays: http://paulgraham.com/articles.html

---

**This index is your map. Use it to navigate the documentation efficiently.**

**For more detailed navigation within each document, check the Table of Contents at the top of each file.**

---

Last Updated: 2025-11-17  
Maintained By: Engineering Team
