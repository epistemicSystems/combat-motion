# LOD 6 PREP SUMMARY: MULTI-SESSION ANALYTICS
**Date**: November 19, 2025
**Phase**: Pre-Implementation Planning
**Status**: ✅ Planning Complete, Ready for Implementation

---

## OVERVIEW

LOD 6 transforms CombatSys from a **session-by-session analysis tool** into a **longitudinal training companion** that helps users track progress over time and understand improvement patterns.

### Key Deliverables
1. **Session Browser**: Fast browsing of all recorded sessions with filtering/sorting
2. **Session Comparison**: Side-by-side comparison of any 2 sessions
3. **Trend Analysis**: Graphs showing metric trends over time with linear regression
4. **Analytics Dashboard**: High-level insights and progress tracking

### Estimated Time
**Total**: 20-22 hours across 5 major tasks

---

## PLANNING DOCUMENTS CREATED

### 1. LOD6_CONTEXT.md (Strategic Context)
**Purpose**: Provides deep philosophical and architectural context for LOD 6

**Key Sections**:
- **What's Been Built**: Summary of LODs 0-5
- **Problem Statement**: Why single-session analysis isn't enough
- **Vision**: What LOD 6 delivers (browser, comparison, trends, dashboard)
- **Architectural Philosophy**: Team perspectives (Hickey, Carmack, Victor, Graham)
- **Data Model Extensions**: New schema definitions for metadata, comparison, trends
- **Core Algorithms**: Comparison logic, linear regression, aggregate insights
- **Testing Strategy**: Unit tests, integration tests
- **Success Criteria**: End-to-end demo scenario

**Use When**: Understanding the "why" and "how" of LOD 6 architecture

### 2. LOD6_TASKS.md (Tactical Implementation)
**Purpose**: Detailed, actionable task breakdown with code examples

**Tasks Defined**:
- **Task 7.1**: Session Index & Metadata Extraction (3h)
- **Task 7.2**: Session Browser UI (5h)
- **Task 7.3**: Session Comparison Logic (4h)
- **Task 7.4**: Comparison UI (4h)
- **Task 7.5**: Trend Analysis & Graphs (4-6h)

**For Each Task**:
- Priority (🔴 Critical / 🟡 Important)
- Estimated time
- Files to create/modify
- Complete code examples
- Test specifications
- Acceptance criteria

**Use When**: Implementing specific features step-by-step

### 3. This Document (LOD6_PREP_SUMMARY.md)
**Purpose**: Quick reference and implementation guide

---

## DEPENDENCY GRAPH

```
LOD 5 (User Calibration) ✅ COMPLETE
    ↓
Task 7.1: Session Index & Metadata
    ↓
    ├─→ Task 7.2: Session Browser UI ──┐
    │                                  ├─→ Task 7.4: Comparison UI
    └─→ Task 7.3: Comparison Logic ────┘
                    ↓
              Task 7.5: Trend Analysis & Graphs
```

**Parallelization Opportunities**:
- Tasks 7.2 and 7.3 can run in parallel after 7.1
- Task 7.4 requires both 7.2 and 7.3
- Task 7.5 is the final integration step

---

## FILES TO CREATE (6 New Files)

### Pure Logic (src/shared/)
1. **`src/shared/analytics.cljs`**
   - Session filtering & sorting
   - Aggregate statistics
   - Best session finder
   - ~150 lines

2. **`src/shared/comparison.cljs`**
   - Metric comparison logic
   - Session comparison algorithm
   - Insight generation
   - ~250 lines

3. **`src/shared/trends.cljs`**
   - Linear regression (least squares)
   - Trend computation
   - Trend classification
   - ~150 lines

### UI Components (src/renderer/)
4. **`src/renderer/session_browser.cljs`**
   - Session list view
   - Filter controls
   - Selection management
   - ~300 lines

5. **`src/renderer/comparison_view.cljs`**
   - Side-by-side comparison UI
   - Metric delta display
   - Insights panel
   - ~200 lines

6. **`src/renderer/charts.cljs`**
   - SVG line chart component
   - Reusable graphing utilities
   - ~150 lines

**Total New Code**: ~1,200 lines

---

## FILES TO MODIFY (3 Existing Files)

### 1. `src/renderer/persistence.cljs`
**Changes**:
- Add session index management
- Implement `load-session-index!` and `save-session-index!`
- Modify `save-session!` to update index
- Add metadata extraction functions

**Lines Added**: ~100-150

### 2. `src/renderer/state.cljs`
**Changes**:
- Add `:session-browser` state
- Add `:analytics` state (comparison report, trend data)
- New events: `:session-browser/init`, `:session-browser/filter`, etc.
- New subscriptions: `:session-browser/filtered-sessions`, etc.

**Lines Added**: ~150-200

### 3. `src/renderer/views.cljs`
**Changes**:
- Add navigation to Session Browser and Analytics
- Wire up new views to routing

**Lines Added**: ~20-30

---

## KEY ALGORITHMS IMPLEMENTED

### 1. Linear Regression (Least Squares)
**File**: `src/shared/trends.cljs`

**Formula**:
```
y = mx + b

m = (n·Σxy - Σx·Σy) / (n·Σx² - (Σx)²)
b = (Σy - m·Σx) / n
R² = 1 - (SS_res / SS_tot)
```

**Use Case**: Fit trend line to breathing rate over 10 sessions

### 2. Session Comparison
**File**: `src/shared/comparison.cljs`

**Logic**:
- Compute delta: `metric_b - metric_a`
- Compute percent change: `(delta / metric_a) × 100`
- Classify direction: `:increased` | `:decreased` | `:unchanged`
- Determine improvement based on "higher is better" vs "lower is better"

**Use Case**: Compare Session A (Nov 1) vs Session B (Nov 22)

### 3. Aggregate Statistics
**File**: `src/shared/analytics.cljs`

**Computed Metrics**:
- Total sessions
- Total training time (hours)
- Average session duration
- Average breathing rate across all sessions
- Average posture score
- Best session (by metric)

**Use Case**: Dashboard header stats

---

## DATA MODEL ADDITIONS

### New Schema Definitions

#### `::session-metadata` (Lightweight for Fast Loading)
```clojure
{:session/id #uuid "..."
 :session/name "Morning Training"
 :session/created-at "2025-11-18T10:30:00Z"
 :session/duration-ms 30000
 :session/frame-count 450
 :session/tags ["breathing" "posture"]
 :session/notes "Felt good today"
 :session/summary-stats {:avg-breathing-rate 21.5
                         :avg-posture-score 0.84}}
```

#### `::comparison-report`
```clojure
{:session-a-id #uuid "..."
 :session-b-id #uuid "..."
 :timestamp-compared #inst "..."
 :breathing-comparison {...}
 :posture-comparison {...}
 :overall-assessment :significant-improvement
 :insights [...]}
```

#### `::trend-data`
```clojure
{:metric-name :breathing-rate
 :values [18 19 20 21 22]
 :timestamps ["2025-11-01T..." "2025-11-08T..." ...]
 :trend-direction :improving
 :slope 0.8
 :intercept 18.2
 :r2 0.92}
```

---

## TESTING PLAN

### Unit Tests (10 Tests)

#### `test/shared/analytics_test.cljs` (3 tests)
- `test-filter-by-date-range`
- `test-filter-by-search`
- `test-aggregate-stats`

#### `test/shared/comparison_test.cljs` (4 tests)
- `test-compare-metric`
- `test-compare-metric-improvement-direction`
- `test-compare-breathing`
- `test-compare-sessions`

#### `test/shared/trends_test.cljs` (3 tests)
- `test-linear-regression`
- `test-linear-regression-perfect-fit`
- `test-compute-trend`

### Integration Tests (2 Tests)

#### End-to-End Flow
1. **Session Browser Flow**
   - Load 10+ sessions
   - Filter by date
   - Sort by duration
   - Select 2 sessions
   - Trigger comparison

2. **Analytics Dashboard Flow**
   - Load trend analysis
   - Render charts
   - Verify trend direction
   - Check R² values

---

## PERFORMANCE TARGETS

### Session Index
- **Load time**: <100ms for 100 sessions
- **File size**: ~10KB for 100 sessions (vs ~2MB for full sessions)

### Session Browser
- **Render time**: <200ms to display 100 sessions
- **Filter/sort**: <50ms to update view
- **Memory**: <50MB for browser state

### Comparison
- **Load sessions**: <500ms to load 2 full sessions
- **Compute comparison**: <10ms (pure function)
- **Render view**: <100ms

### Trend Analysis
- **Compute regression**: <5ms for 100 data points
- **Render chart**: <100ms

---

## IMPLEMENTATION STRATEGY

### Phase 1: Foundation (Day 1, 3h)
**Task 7.1**: Session Index & Metadata
- Modify `persistence.cljs`
- Create `analytics.cljs`
- Implement index file I/O
- **Milestone**: Can save and load session index

### Phase 2: Browser UI (Day 1-2, 5h)
**Task 7.2**: Session Browser UI
- Create `session_browser.cljs`
- Implement filter/sort logic
- Wire up re-frame state
- **Milestone**: Can browse all sessions with filtering

### Phase 3: Comparison (Day 2-3, 8h)
**Tasks 7.3 + 7.4**: Comparison Logic + UI
- Create `comparison.cljs` (pure logic)
- Create `comparison_view.cljs` (UI)
- Implement side-by-side display
- **Milestone**: Can compare any 2 sessions

### Phase 4: Trends (Day 3-4, 6h)
**Task 7.5**: Trend Analysis & Graphs
- Create `trends.cljs` (regression)
- Create `charts.cljs` (SVG graphs)
- Create `analytics_view.cljs` (dashboard)
- **Milestone**: Can view trend graphs for all metrics

### Phase 5: Polish & Test (Day 4, 2h)
- Run all tests
- Fix bugs
- Performance profiling
- Documentation updates

---

## SUCCESS CRITERIA

At the end of LOD 6 implementation:

### Functional Requirements ✅
- [ ] Session browser loads 100+ sessions fast
- [ ] Can filter sessions by date, search text
- [ ] Can sort sessions by date, duration, name
- [ ] Can select 2 sessions and compare
- [ ] Comparison shows accurate deltas and % changes
- [ ] Trend graphs display for all key metrics
- [ ] Linear regression trend lines overlay correctly
- [ ] Analytics dashboard shows aggregate stats

### Technical Requirements ✅
- [ ] All 10 unit tests pass
- [ ] Both integration tests pass
- [ ] Session index file created on first save
- [ ] Index loads <100ms
- [ ] Comparison computation <10ms
- [ ] Charts render <100ms
- [ ] No console errors

### User Experience ✅
- [ ] Browsing sessions is intuitive
- [ ] Comparison view is easy to read
- [ ] Trend graphs tell a clear story
- [ ] Improvement is visually obvious
- [ ] Handles 100+ sessions smoothly

---

## NEXT STEPS

### To Start LOD 6 Implementation:

1. **Read Planning Docs**
   ```bash
   # Review architecture and context
   cat LOD6_CONTEXT.md

   # Review task breakdown
   cat LOD6_TASKS.md
   ```

2. **Set Up Branch** (if using git)
   ```bash
   git checkout -b lod6-multi-session-analytics
   ```

3. **Begin Task 7.1** (Session Index)
   - Open `src/renderer/persistence.cljs`
   - Add session index functions
   - Create `src/shared/analytics.cljs`
   - Write tests

4. **Follow Task Order**
   - 7.1 → 7.2 → 7.3 → 7.4 → 7.5
   - Or parallelize 7.2 and 7.3

5. **Test Continuously**
   - Run tests after each task
   - Verify in browser UI
   - Check performance metrics

---

## NOTES FOR IMPLEMENTER

### Architectural Decisions

1. **Why Session Index?**
   - Loading 100 full sessions = ~200MB, takes 5+ seconds
   - Index file = ~10KB, loads <100ms
   - Trade-off: Metadata only in index, load full session on demand

2. **Why Linear Regression?**
   - Simple, interpretable
   - R² tells us if trend is real or noise
   - Fast to compute (O(n))
   - Alternative (moving average) doesn't give slope

3. **Why SVG Charts?**
   - No external dependencies
   - Lightweight
   - Fully customizable
   - Works in Electron without issues
   - Alternative (Recharts, Vega) adds 100KB+ bundle size

### Common Pitfalls to Avoid

1. **Loading All Sessions into Memory**
   - ❌ Bad: Load all 100 sessions on app start
   - ✅ Good: Load index only, lazy-load full sessions

2. **Mutable State in Comparison**
   - ❌ Bad: Mutate sessions during comparison
   - ✅ Good: Pure functions, return new comparison report

3. **Synchronous Chart Rendering**
   - ❌ Bad: Block UI thread for large datasets
   - ✅ Good: Render charts in requestAnimationFrame (already fast for <100 points)

4. **Hardcoded Metric Paths**
   - ❌ Bad: `get-in session [:session/analysis :breathing :rate-bpm]` everywhere
   - ✅ Good: Define metric configs centrally, use paths dynamically

---

## RESOURCES

### Reference Implementations
- **Linear Regression**: `src/shared/trends.cljs` (from LOD6_TASKS.md)
- **Comparison Logic**: `src/shared/comparison.cljs` (from LOD6_TASKS.md)
- **SVG Charts**: `src/renderer/charts.cljs` (from LOD6_TASKS.md)

### Existing Code to Study
- **Persistence Pattern**: `src/renderer/persistence.cljs` (LOD 5)
- **State Management**: `src/renderer/state.cljs` (LOD 1-5)
- **UI Components**: `src/renderer/views.cljs` (LOD 1-5)

### Testing Examples
- **Pure Functions**: `test/shared/breathing_test.cljs` (LOD 2)
- **UI Components**: `test/renderer/views_test.cljs` (LOD 4)

---

## QUESTIONS & ANSWERS

### Q: Do I need to implement all charts at once?
**A**: No. Start with breathing rate trend only. Add posture, depth, etc. incrementally.

### Q: What if R² is very low (<0.3)?
**A**: Show trend anyway, but add note: "Low confidence trend (R² = 0.28)". User data may be noisy.

### Q: Should I store comparison reports?
**A**: LOD 6.0: No, compute on-demand. LOD 6.1: Consider caching recent comparisons.

### Q: What about mobile responsiveness?
**A**: Defer to LOD 7. Desktop-first for LOD 6.

### Q: Should trend analysis handle missing data?
**A**: Yes. Skip sessions where metric is nil. Filter before regression.

---

## FINAL CHECKLIST BEFORE STARTING

- [x] LOD 5 is complete and tested
- [x] Planning documents reviewed (CONTEXT + TASKS)
- [x] Development environment ready
- [x] Tests can run (`npx shadow-cljs test`)
- [x] App can build (`npm run compile`)
- [ ] **Ready to implement Task 7.1** ✅

---

**Status**: 📋 Planning Complete
**Next Action**: Begin Task 7.1 (Session Index & Metadata Extraction)
**Estimated Completion**: 4 days (20-22 hours)

**Let's build analytics that motivate users to keep training.** 📈
