# LOD 6 INTEGRATION TEST PLAN
## Multi-Session Analytics Testing

**Date**: 2025-11-19
**LOD Level**: 6 (Multi-Session Analytics)
**Test Type**: End-to-End Integration Testing

---

## TEST ENVIRONMENT SETUP

### Prerequisites
1. Clean application state (clear existing sessions if needed)
2. Camera access enabled
3. MediaPipe pose detection working
4. All LOD 6 modules compiled successfully

### Test Data Requirements
- **Minimum**: 3 sessions for comparison testing
- **Recommended**: 10 sessions for trend analysis
- **Ideal**: 20+ sessions for comprehensive testing

---

## TEST SCENARIO 1: SESSION INDEX & PERSISTENCE

### Objective
Verify that session metadata is extracted and indexed correctly.

### Test Steps

1. **Record a new session** (30 seconds minimum)
   - [ ] Session completes successfully
   - [ ] Run "Analyze All" to generate analysis data

2. **Verify session saved to disk**
   - [ ] Check `sessions/` directory for new `.edn` file
   - [ ] Verify file size is reasonable (~100KB - 1MB for 30s session)

3. **Verify index file created**
   - [ ] Check `sessions/index.edn` exists
   - [ ] Open and verify structure contains:
     - `:session/id`
     - `:session/name`
     - `:session/created-at`
     - `:session/duration-ms`
     - `:session/summary-stats` with metrics

4. **Record 5 more sessions**
   - [ ] Each session adds to index
   - [ ] Index remains sorted by date (newest first)

### Expected Results
- ✓ Index file loads in < 100ms
- ✓ Metadata is ~100 bytes per session (vs ~900KB for full session)
- ✓ Summary stats include: breathing rate, depth, posture score, forward head
- ✓ No duplicate entries in index

---

## TEST SCENARIO 2: SESSION BROWSER UI

### Objective
Verify session browsing, filtering, sorting, and selection.

### Test Steps

1. **Navigate to Session Browser**
   - [ ] Click "Sessions" button in control panel
   - [ ] Session browser view loads

2. **Verify Session List Display**
   - [ ] All 6 sessions display as cards
   - [ ] Each card shows:
     - Session name
     - Date (formatted readably)
     - Duration
     - Breathing rate (bpm)
     - Posture score (%)
   - [ ] Aggregate stats header shows:
     - Total sessions count
     - Total training time (hours)
     - Average session duration
     - Average breathing rate

3. **Test Search Filter**
   - [ ] Type session name in search box
   - [ ] Results filter in real-time
   - [ ] Clear search shows all sessions again

4. **Test Date Range Filter**
   - [ ] Select "Last 7 Days"
   - [ ] Only recent sessions show
   - [ ] Select "Last 30 Days"
   - [ ] More sessions appear
   - [ ] Select "All Time"
   - [ ] All sessions visible

5. **Test Sort Options**
   - [ ] Sort by "Date (Newest First)" - verify newest on top
   - [ ] Sort by "Duration" - verify longest on top
   - [ ] Sort by "Name" - verify alphabetical

6. **Test Session Selection**
   - [ ] Click checkbox on session A
   - [ ] Session A highlights
   - [ ] Click checkbox on session B
   - [ ] Both A and B highlighted
   - [ ] "Compare Selected (2)" button appears
   - [ ] Try selecting 3rd session
   - [ ] Only 2 most recent selections remain checked

7. **Test Session Actions**
   - [ ] Click "View" on a session
   - [ ] Navigates to main analysis view with that session loaded
   - [ ] Return to session browser
   - [ ] Click "Delete" on a test session
   - [ ] Confirm deletion
   - [ ] Session removed from list
   - [ ] Index updated (check count)

### Expected Results
- ✓ Session list loads in < 200ms for 10 sessions
- ✓ Filtering and sorting work instantly (no lag)
- ✓ Selection limited to 2 sessions
- ✓ UI is responsive and intuitive

---

## TEST SCENARIO 3: SESSION COMPARISON

### Objective
Verify two-session comparison with accurate delta calculations.

### Test Steps

1. **Setup: Create Two Contrasting Sessions**
   - **Session A** (Baseline):
     - Record 30s session with normal breathing
     - Breathing rate ~18-20 bpm
     - Posture relaxed (moderate forward head)
   - **Session B** (Improved):
     - Record 30s session with focused breathing
     - Breathing rate ~22-24 bpm
     - Posture upright (less forward head)

2. **Analyze Both Sessions**
   - [ ] Run "Analyze All" on both
   - [ ] Verify both have complete analysis data

3. **Compare Sessions**
   - [ ] Navigate to Session Browser
   - [ ] Select Session A and Session B
   - [ ] Click "Compare Selected (2)"
   - [ ] Comparison view loads

4. **Verify Comparison Header**
   - [ ] Shows both session names
   - [ ] Shows both dates (formatted)
   - [ ] Shows overall assessment badge:
     - Should be "Significant Improvement" or "Slight Improvement"

5. **Verify Breathing Comparison Table**
   - [ ] Rate (bpm): Shows A → B with delta
   - [ ] Percent change calculated correctly
     - Formula: ((B - A) / A) * 100
   - [ ] Improvement indicator (✓) shows if rate increased
   - [ ] Depth Score: Shows A → B with delta
   - [ ] Fatigue Windows: Shows count comparison

6. **Verify Posture Comparison Table**
   - [ ] Overall Score: Shows A → B
   - [ ] Higher score shows ✓ indicator
   - [ ] Forward Head (cm): Shows A → B
   - [ ] **Lower** forward head shows ✓ (improvement)
   - [ ] Shoulder Imbalance: Shows A → B

7. **Verify Insights Panel**
   - [ ] At least 1 insight generated
   - [ ] Insights describe key changes in natural language
   - [ ] Example: "Breathing rate improved by X%"
   - [ ] Example: "Forward head posture improved by X cm"

8. **Test Edge Cases**
   - [ ] Compare two identical sessions
     - Should show "Stable" assessment
     - All deltas = 0
   - [ ] Compare session with itself
     - Should show "Stable" with zero changes

### Expected Results
- ✓ Deltas calculated correctly (B - A)
- ✓ Percent changes accurate to 1 decimal place
- ✓ Improvement logic respects "higher is better" vs "lower is better"
- ✓ Overall assessment matches actual changes
- ✓ Insights are relevant and accurate

---

## TEST SCENARIO 4: TREND ANALYSIS & GRAPHS

### Objective
Verify trend analysis with linear regression over multiple sessions.

### Test Steps

1. **Setup: Create Trending Data**
   - **Option A**: Record 10 sessions over time with gradual improvement
   - **Option B**: Use mock data with clear upward trend

   Suggested progression (if using real sessions):
   - Sessions 1-3: Breathing rate 18-19 bpm
   - Sessions 4-6: Breathing rate 20-21 bpm
   - Sessions 7-10: Breathing rate 22-23 bpm

2. **Navigate to Analytics Dashboard**
   - [ ] Click "📊 View Analytics Dashboard" in session browser
   - [ ] Analytics view loads

3. **Verify Analytics Header**
   - [ ] Shows "Analytics Dashboard" title
   - [ ] Shows session count (e.g., "Trends based on 10 sessions")

4. **Verify Breathing Rate Trend Chart**
   - [ ] Chart title: "Breathing Rate Over Time"
   - [ ] Y-axis label: "Rate (bpm)"
   - [ ] X-axis shows dates (formatted as MM/DD)
   - [ ] Data points plotted correctly (blue circles)
   - [ ] Data line connects all points (blue line)
   - [ ] **Trend line** overlays data (red dashed line)
   - [ ] Trend line has positive slope (upward)

5. **Verify Trend Statistics Panel (Breathing Rate)**
   - [ ] Trend direction: "↗ Improving" (green)
   - [ ] R² value displayed (should be > 0.7 for clear trend)
   - [ ] Slope displayed (should be positive, e.g., ~0.5 bpm per session)

6. **Verify Posture Score Trend Chart**
   - [ ] Chart renders correctly
   - [ ] Data points and trend line visible
   - [ ] Trend stats below chart

7. **Verify Additional Charts**
   - [ ] Breathing Depth chart renders
   - [ ] Forward Head Posture chart renders
   - [ ] All charts have consistent styling

8. **Test Insufficient Data Handling**
   - [ ] Clear sessions until < 3 remain
   - [ ] Navigate to Analytics
   - [ ] Should show "Insufficient Data" message
   - [ ] Message suggests recording more sessions
   - [ ] Button to return to main view

9. **Test Regression Accuracy**
   - Manually verify one trend calculation:
     - Pick breathing rate trend
     - Note the values: [18, 19, 20, 21, 22]
     - Expected slope: ~1.0 (perfect linear increase)
     - Expected R²: 1.0 (perfect fit)
     - [ ] Check stats panel matches expectations

10. **Test Edge Cases**
    - [ ] All sessions with same metric value
      - Trend should be "→ Stable"
      - Slope ≈ 0
      - R² = 1.0 (no variance)
    - [ ] Two sessions only
      - Should still render (straight line)
      - R² = 1.0

### Expected Results
- ✓ Linear regression uses Ordinary Least Squares
- ✓ Trend classification:
  - `slope > 0.05` → Improving
  - `slope < -0.05` → Declining
  - Otherwise → Stable
- ✓ Charts render smoothly (no lag)
- ✓ R² calculated correctly (1 - SS_res/SS_tot)
- ✓ Trend line matches regression equation (y = mx + b)

---

## TEST SCENARIO 5: CROSS-VIEW NAVIGATION

### Objective
Verify seamless navigation between all LOD 6 views.

### Test Steps

1. **Main View → Session Browser**
   - [ ] Click "Sessions" in control panel
   - [ ] Session browser loads

2. **Session Browser → Comparison View**
   - [ ] Select 2 sessions
   - [ ] Click "Compare Selected (2)"
   - [ ] Comparison view loads

3. **Comparison View → Session Browser**
   - [ ] Click "Back to Sessions" button
   - [ ] Returns to session browser
   - [ ] Selections preserved

4. **Session Browser → Analytics View**
   - [ ] Click "📊 View Analytics Dashboard"
   - [ ] Analytics view loads

5. **Analytics View → Session Browser**
   - [ ] Click "Back to Sessions" (or similar)
   - [ ] Returns to session browser

6. **Session Browser → Main View**
   - [ ] Click "View" on a session
   - [ ] Returns to main analysis view
   - [ ] Session loaded correctly

7. **Verify State Persistence**
   - [ ] Navigate away from session browser
   - [ ] Return to session browser
   - [ ] Filters/sort preserved (or reset intentionally)

### Expected Results
- ✓ All navigation buttons work
- ✓ No "page not found" errors
- ✓ State preserved where appropriate
- ✓ No console errors during navigation

---

## TEST SCENARIO 6: PERFORMANCE BENCHMARKS

### Objective
Verify performance targets are met.

### Test Steps

1. **Session Index Load Time**
   - [ ] Measure time to load session browser with 10 sessions
   - [ ] Expected: < 200ms
   - [ ] Measure time with 50 sessions (if available)
   - [ ] Expected: < 500ms

2. **Comparison Calculation Time**
   - [ ] Open browser dev console
   - [ ] Compare two sessions
   - [ ] Check console logs for timing
   - [ ] Expected: < 50ms (pure function, should be instant)

3. **Trend Analysis Computation Time**
   - [ ] Open analytics view with 10 sessions
   - [ ] Check console for timing
   - [ ] Expected: < 100ms

4. **Chart Render Time**
   - [ ] Observe analytics view loading
   - [ ] Charts should render without visible lag
   - [ ] Expected: < 100ms per chart

5. **Memory Usage**
   - [ ] Open browser dev tools → Memory
   - [ ] Load session browser
   - [ ] Check heap size (should be reasonable, <50MB for 10 sessions)

### Expected Results
- ✓ All operations feel instant (< 100ms)
- ✓ No UI freezing or lag
- ✓ Memory usage reasonable (<100MB total)

---

## TEST SCENARIO 7: ERROR HANDLING

### Objective
Verify graceful error handling.

### Test Steps

1. **Missing Session Data**
   - [ ] Manually delete a session file from `sessions/` directory
   - [ ] Keep entry in index
   - [ ] Try to view that session
   - [ ] Should show error message (not crash)

2. **Corrupted Index File**
   - [ ] Manually edit `sessions/index.edn` to introduce syntax error
   - [ ] Try to load session browser
   - [ ] Should show error or empty state (not crash)

3. **Session Without Analysis**
   - [ ] Record session but don't run "Analyze All"
   - [ ] Try to compare it with another session
   - [ ] Should handle missing analysis gracefully

4. **Single Session Comparison**
   - [ ] Try to select only 1 session
   - [ ] Compare button should be disabled or show message

### Expected Results
- ✓ No application crashes
- ✓ Helpful error messages shown to user
- ✓ Console errors are descriptive (for debugging)

---

## FINAL CHECKLIST

### Code Quality
- [x] All functions compile without warnings
- [x] All unit tests pass (15 tests in trends_test.cljs + existing tests)
- [ ] Integration tests executed successfully (this document)
- [x] Code follows ClojureScript idioms
- [x] All functions have docstrings

### Functionality
- [ ] Session index loads fast (<100ms for 100 sessions)
- [ ] Browser UI filters and sorts correctly
- [ ] Can select and compare any 2 sessions
- [ ] Comparison report shows accurate deltas
- [ ] Trend analysis computes regression correctly
- [ ] Charts render smoothly

### User Experience
- [ ] Session browser is intuitive
- [ ] Comparison view is easy to read
- [ ] Trend graphs tell a clear story
- [ ] No console errors
- [ ] Responsive UI (handles 100+ sessions)

### Documentation
- [ ] README updated with analytics features
- [ ] PLAN.md marked LOD 6 complete
- [x] Code comments explain algorithms

---

## BUG TRACKER

### Issues Found During Testing

| Issue # | Severity | Description | Status | Fix Commit |
|---------|----------|-------------|--------|------------|
| | | | | |

---

## SIGN-OFF

### Testing Completed By
- **Tester**: Claude Code (AI Assistant)
- **Date**: 2025-11-19
- **Version**: LOD 6 (Multi-Session Analytics)

### Test Results Summary
- **Total Test Scenarios**: 7
- **Scenarios Passed**: ___ / 7
- **Critical Bugs Found**: ___
- **Minor Issues Found**: ___
- **Performance Tests Met**: ___ / 5

### Recommendation
- [ ] **APPROVED** - Ready for production
- [ ] **APPROVED WITH NOTES** - Minor issues to address
- [ ] **NOT APPROVED** - Critical issues require fixes

### Notes
_[Add any additional notes here]_

---

**End of Integration Test Plan**
