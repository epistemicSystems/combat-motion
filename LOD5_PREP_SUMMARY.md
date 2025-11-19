# LOD 5 PREPARATION SUMMARY
## User Calibration & Personalization - Ready to Build

**Date**: 2025-11-18
**Current Status**: LOD 4 Complete, LOD 5 Planned
**Branch**: `claude/prep-lod5-planning-01CKeisxuA9bDuJzBf5eCTig`

---

## 📋 WHAT WAS PREPARED

### 1. **LOD5_CONTEXT.md** (Comprehensive Background)
**Purpose**: Provide deep context for building personalization system

**Contents**:
- ✅ Summary of what's built (LOD 0-4)
- ✅ The problem LOD 5 solves (hardcoded thresholds → personalized baselines)
- ✅ Complete vision for LOD 5 deliverables
- ✅ Architectural philosophy from the "team" (Hickey, Carmack, Victor, Graham)
- ✅ Data model extensions (new schema definitions)
- ✅ Integration points with existing code
- ✅ File structure (5 new files, 4 modified)
- ✅ Calibration algorithm step-by-step
- ✅ Testing strategy (unit, integration, manual)
- ✅ Success criteria and demo flow

**Key Insights**:
- Personalization is just **data transformation** (pure functions)
- Not performance-critical (offline processing is fine)
- UI must make calibration **transparent and reassuring**
- Ship simplest version first (no adaptive learning yet)

### 2. **LOD5_TASKS.md** (Detailed Implementation Plan)
**Purpose**: Break LOD 5 into concrete, actionable tasks

**Contents**:
- ✅ 6 major tasks with dependencies
- ✅ 18-24 hour estimate
- ✅ Specific files to create/modify
- ✅ Function signatures with docstrings
- ✅ Code examples for every deliverable
- ✅ Unit test specifications
- ✅ Acceptance criteria per task
- ✅ Integration testing scenarios
- ✅ Final checklist

**Task Breakdown**:
1. **Task 6.1**: Schema Extensions (2h) - Extend EDN schemas
2. **Task 6.2**: Calibration Analysis (6h) - Pure functions for extracting baselines
3. **Task 6.3**: Calibration Wizard (6h) - Step-by-step UI
4. **Task 6.4**: Profile Storage (3h) - Save/load from disk
5. **Task 6.5**: Analyzer Integration (4h) - Use personalized thresholds
6. **Task 6.6**: Profile View (3h) - View/manage profile UI

---

## 🎯 LOD 5 OBJECTIVES

### The Transformation
**From**: Generic analyzer with hardcoded thresholds
**To**: Personal coach that knows the individual user

### What Works After LOD 5

```
First-time user:
  1. Opens app → "Welcome! Let's calibrate" wizard
  2. T-pose (10s) → measures body proportions
  3. Normal breathing (60s) → learns respiratory baseline
  4. Free movement (60s) → measures range of motion
  5. "Profile created!" → sees baseline summary

Calibrated user:
  1. Records session → analyzes with personal thresholds
  2. Sees insights: "18 bpm (15% below YOUR baseline of 21 bpm)"
  3. Can view profile, recalibrate, or export
  4. Profile persists across app restarts
```

---

## 🏗️ ARCHITECTURAL CHANGES

### New Schema Definitions
- `::calibration-session` - T-pose, breathing, or movement session
- `::baseline-pose` - Body measurements from T-pose
- `::breathing-baseline` - Typical rate, depth, regularity
- `::posture-baseline` - Typical forward head, shoulder imbalance
- `::learned-thresholds` - Personalized alert thresholds
- `::user-profile` - Complete profile with all baselines

### New Files
```
src/
├── shared/
│   ├── calibration.cljs        # Analyze calibration sessions
│   └── personalization.cljs    # Create user profiles
│
├── renderer/
│   ├── onboarding.cljs         # Calibration wizard UI
│   └── profile_view.cljs       # View/edit profile
```

### Modified Files
```
src/
├── shared/
│   ├── schema.cljs             # Add calibration schemas
│   ├── breathing.cljs          # Accept user-profile param
│   └── posture.cljs            # Accept user-profile param
│
└── renderer/
    ├── persistence.cljs        # Add profile save/load
    ├── state.cljs              # Add calibration state
    └── views.cljs              # Pass profile to analyzers
```

---

## 📊 DATA FLOW

### Calibration Flow
```
User completes 3 sessions
    ↓
[analyze-t-pose-session timeline] → {:height-cm ... :baseline-pose ...}
[analyze-breathing-session timeline] → {:typical-rate-bpm ... :typical-depth ...}
[analyze-movement-session timeline] → {:rom-ranges ...}
    ↓
[compute-breathing-thresholds baseline] → {:fatigue-threshold ... :rate-alert-threshold ...}
[compute-posture-thresholds baseline] → {:forward-head-alert-cm ... :shoulder-imbalance-alert-deg ...}
    ↓
[create-user-profile sessions] → Complete user-profile map
    ↓
[save-user-profile! profile] → Saved to ~/.combatsys/profiles/{id}.edn
```

### Analysis Flow (with personalization)
```
User records session
    ↓
[breathing/analyze timeline user-profile]
    ↓
Get fatigue-threshold from user-profile (or default to 0.3)
    ↓
Detect fatigue windows using personalized threshold
    ↓
Compute delta from baseline rate
    ↓
Generate insights: "18 bpm (15% below YOUR baseline of 21 bpm)"
```

---

## 🧪 TESTING STRATEGY

### Unit Tests (12 tests)
- `test-t-pose-analysis` - Extracts height and body measurements
- `test-breathing-analysis` - Computes typical rate/depth
- `test-movement-analysis` - Extracts ROM ranges
- `test-threshold-computation` - Thresholds scale with baseline
- `test-profile-creation` - Creates valid profile from sessions
- Plus 7 more in calibration_test.cljs and personalization_test.cljs

### Integration Tests (3 tests)
- End-to-end calibration flow
- Personalized analysis with profile
- Profile persistence (save/load cycle)

### Manual Testing
- First-time user onboarding
- Calibrated analysis shows personalized insights
- Recalibration flow
- Profile persistence across app restarts

---

## ⚡ KEY IMPLEMENTATION DECISIONS

### 1. **Pure Functions First** (Hickey's Voice)
All calibration analysis is pure data transformation:
```clojure
calibration-sessions → user-profile  (pure)
timeline + user-profile → analysis  (pure)
```
Side effects isolated to:
- Camera recording (existing)
- File I/O (save/load profile)

### 2. **No Premature Optimization** (Carmack's Voice)
- Calibration is offline (can take 5 seconds)
- Analysis with personalized thresholds has same O(n) as before
- Profile lookup is O(1) map access
- **No performance concerns for LOD 5**

### 3. **Transparent Calibration** (Victor's Voice)
- Show user what we're measuring
- Live feedback during recording ("✓ Arms extended")
- Explain the result ("Your baseline is X")
- Make recalibration always available

### 4. **Ship Simplest Version** (Graham's Voice)
**In scope for LOD 5**:
- ✅ 3-session calibration
- ✅ Personalized thresholds
- ✅ Baseline-aware insights
- ✅ Profile persistence

**Out of scope (future)**:
- ❌ Adaptive learning (baseline evolution)
- ❌ Multi-user profiles
- ❌ Body composition estimates
- ❌ Injury history integration

---

## 📦 DELIVERABLES CHECKLIST

### Documentation
- [x] LOD5_CONTEXT.md - Complete background and philosophy
- [x] LOD5_TASKS.md - Detailed task breakdown with code examples
- [x] LOD5_PREP_SUMMARY.md - This summary

### Code (to be implemented)
- [ ] Task 6.1: Schema extensions
- [ ] Task 6.2: Calibration analysis functions
- [ ] Task 6.3: Calibration wizard UI
- [ ] Task 6.4: Profile storage
- [ ] Task 6.5: Analyzer integration
- [ ] Task 6.6: Profile view

### Testing (to be implemented)
- [ ] 12 unit tests
- [ ] 3 integration tests
- [ ] Manual test checklist execution

---

## 🚀 NEXT STEPS

### Immediate (Start LOD 5 Implementation)
1. Create branch: `claude/lod5-user-calibration-{session-id}`
2. Start with Task 6.1 (Schema Extensions) - 2 hours
3. Proceed sequentially through tasks
4. Commit after each task completion

### Recommended Workflow
```bash
# Day 1 (8 hours)
- Task 6.1: Schema Extensions (2h)
- Task 6.2: Calibration Analysis (6h)

# Day 2 (8 hours)
- Task 6.3: Calibration Wizard (6h)
- Task 6.4: Profile Storage (2h of 3h)

# Day 3 (8 hours)
- Task 6.4: Profile Storage (1h remaining)
- Task 6.5: Analyzer Integration (4h)
- Task 6.6: Profile View (3h)

# Day 4 (4 hours)
- Integration testing
- Bug fixes
- Documentation updates
```

### Success Metrics
At end of LOD 5:
- ✅ First-time user completes calibration in <5 minutes
- ✅ Analyses show personalized insights
- ✅ Profile persists across restarts
- ✅ Can recalibrate at any time
- ✅ All tests pass
- ✅ No console errors

---

## 💡 IMPLEMENTATION TIPS

### For Task 6.2 (Calibration Analysis)
- Start with `average-poses` - simplest pure function
- Use existing `pose/compute-distance` for measurements
- Test each function individually in REPL
- Mock data first, real sessions second

### For Task 6.3 (Calibration Wizard)
- Build welcome screen first (no recording yet)
- Add one step at a time
- Use mock timer before integrating real recording
- Visual feedback is critical (user needs confidence)

### For Task 6.5 (Analyzer Integration)
- Make user-profile parameter optional (backward compatibility)
- Default to nil → generic thresholds
- Test both paths (with and without profile)
- Update insights to reference baseline

---

## 📚 REFERENCE DOCUMENTS

### Must Read Before Starting
1. **LOD5_CONTEXT.md** - Full background (20 min read)
2. **LOD5_TASKS.md** - Implementation details (30 min read)
3. **SPEC.md** - Existing schema definitions (reference)
4. **PLAN.md** - Overall roadmap (reference)

### Code Examples Location
- Calibration analysis: LOD5_TASKS.md Task 6.2
- Wizard UI: LOD5_TASKS.md Task 6.3
- Profile storage: LOD5_TASKS.md Task 6.4
- Integration: LOD5_TASKS.md Task 6.5

---

## 🎭 THE TEAM'S FINAL WORDS

**Rich Hickey**: "Remember, calibration is just data. Extract it, transform it, use it. No magic, no mutation."

**John Carmack**: "This isn't on the critical path. Take the time to get it right. Measure twice, code once."

**Brett Victor**: "Make the user feel understood. They're trusting us with their body data. Show them why."

**Paul Graham**: "Ship the simplest version that delivers value. Adaptive learning can wait. Personalized thresholds are enough."

---

## ✅ READY TO BUILD

All planning is complete. The path is clear. The team is aligned.

**Next command**:
```bash
# Start LOD 5 implementation
Read LOD5_CONTEXT.md, then LOD5_TASKS.md

# Begin with Task 6.1
Implement schema extensions as specified in Task 6.1
```

**Let's make CombatSys personal.** 🎯

---

**Document Version**: 1.0
**Last Updated**: 2025-11-18
**Prepared By**: Claude (Engineering Team)
