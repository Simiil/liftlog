# Muscle-Balance Radar Chart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A "Muscle balance" card on the Analytics screen: an 8-spoke radar chart of sets-per-week per merged muscle group with trend-colored vertices, a dashed 10-sets/week target ring, and a 30d/90d/1y/all range selector (spec: `docs/superpowers/specs/2026-07-10-radar-chart-analytics-design.md`).

**Architecture:** Room flow → repository join (`observeAllSetsSince(0)` × exercise metadata) → pure `domain/analytics` reduction (`muscleBalance()`) → `AnalyticsBrowserViewModel` `StateFlow` → custom-`Canvas` radar composable. No schema change, no new DAO query, no new dependency.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room, Hilt, JUnit4 + Turbine. Radar is hand-drawn `Canvas` (Vico is Cartesian-only; `Sparkline.kt` is the precedent).

## Global Constraints

- Run `./gradlew ktlintFormat` before every commit; CI = `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug`.
- Every new user-facing string lands in BOTH `app/src/main/res/values/strings.xml` and `app/src/main/res/values-de/strings.xml` (M6 i18n is in progress).
- Locale-sensitive number formatting must use `String.format(java.util.Locale.getDefault(), …)` (M6 PR1 lint gate; see `WeekCard` in `AnalyticsScreen.kt:107`).
- Domain functions take `nowMillis: Long` as a parameter — never call `System.currentTimeMillis()`/`Clock` in `domain/` (M4 pattern; deterministic tests).
- ViewModels never touch Room/DAOs directly (UI ↔ ViewModel ↔ Repository ↔ Room).
- Weight is canonical kg end-to-end; bodyweight sets have `weightKg == 0.0`.
- No new third-party dependencies.
- Spoke labels come from string resources, never `enum.name`.

---

### Task 1: Spec amendments (align spec to codebase facts)

Two lines of the approved spec contradict the code / themselves and were approved for correction:
1. The spec says per-exercise progress uses "e1RM for weighted"; the *actual* existing headline swap (`ExerciseAnalytics.kt:81`, which the design intent was to reuse) is **volume for weighted, total reps for bodyweight**. Trend therefore runs on the same per-session `primary` series every other trend badge in the app uses.
2. The spec says cutoff "mirrors `ExerciseDetailViewModel`'s `nowFallbackCutoff`" (newest-session-anchored) but also defines the effective window as ending at *now*. Resolution: the radar uses a calendar-true window — `cutoff = now − range.days` — because the card has an honest empty state (the detail screen's newest-anchoring exists only to keep a sparse chart drawable).

**Files:**
- Modify: `docs/superpowers/specs/2026-07-10-radar-chart-analytics-design.md`

- [ ] **Step 1: Edit the two spec passages**

In the "Metric semantics" per-group-trend bullet, replace:

```
  range, build per-session points of its **headline metric** (e1RM for
  weighted, total reps for bodyweight — the existing swap in
  `ExerciseAnalytics.summarize`) and run the existing OLS `trend()` with
```

with:

```
  range, build per-session points of its **headline metric** (volume for
  weighted, total reps for bodyweight — the existing swap in
  `ExerciseAnalytics.summarize`, the same series every trend badge uses)
  and run the existing OLS `trend()` with
```

In the "ViewModel" section, replace:

```
`MuscleBalanceUiState` (list of spokes: label res + fraction + vertex color
role; footnote count; empty flag). Cutoff derivation mirrors
`ExerciseDetailViewModel`'s `nowFallbackCutoff`.
```

with:

```
`MuscleBalanceUiState` (balance + selected range; presentational mapping to
spokes lives in the card). Cutoff is calendar-true: `now − range.days`
(unlike the detail screen's newest-session anchoring — the card has an
honest empty state instead).
```

Also in the "ViewModel" section, correct the class name — replace
`Extend the existing `AnalyticsViewModel`:` with
`Extend the existing `AnalyticsBrowserViewModel`:` (that is the browser
ViewModel's actual name).

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/specs/2026-07-10-radar-chart-analytics-design.md
git commit -m "docs: align radar spec with actual headline-metric swap and calendar window"
```

---

### Task 2: Domain — `muscleBalance()` (pure, TDD)

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/analytics/MuscleBalance.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/domain/analytics/Trend.kt` (extract `trendDirection()` helper)
- Test: `app/src/test/kotlin/de/simiil/liftlog/domain/analytics/MuscleBalanceTest.kt`

**Interfaces:**
- Consumes: `trend()`, `TrendPoint`, `TrendResult`, `TrendDirection` (Trend.kt); `summarize()`, `DatedSet` (ExerciseAnalytics.kt); `MuscleGroup`, `Equipment` (domain/model).
- Produces (used by Tasks 3–5, 7):
  - `enum class RadarGroup { CHEST, BACK, SHOULDERS, ARMS, QUADS, HAMS_GLUTES, CALVES, CORE }`
  - `fun MuscleGroup.toRadarGroup(): RadarGroup?`
  - `const val TARGET_SETS_PER_WEEK = 10.0`
  - `data class SetWithExercise(sessionId: String, exerciseId: String, startedAt: Long, weightKg: Double, reps: Int, muscleGroup: MuscleGroup, equipment: Equipment)`
  - `data class GroupBalance(group: RadarGroup, setsPerWeek: Double, fraction: Double, direction: TrendDirection?)`
  - `data class MuscleBalance(groups: List<GroupBalance>, rimSetsPerWeek: Double, targetFraction: Double, unclassifiedSets: Int, isEmpty: Boolean)`
  - `fun muscleBalance(rows: List<SetWithExercise>, rangeDays: Long, nowMillis: Long): MuscleBalance`
  - `fun trendDirection(percent: Double): TrendDirection` (in Trend.kt)

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/de/simiil/liftlog/domain/analytics/MuscleBalanceTest.kt`:

```kotlin
package de.simiil.liftlog.domain.analytics

import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleBalanceTest {
    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    private fun row(
        group: MuscleGroup,
        startedAt: Long,
        sessionId: String = "s$startedAt",
        exerciseId: String = "e-$group",
        weightKg: Double = 100.0,
        reps: Int = 5,
        equipment: Equipment = Equipment.BARBELL,
    ) = SetWithExercise(sessionId, exerciseId, startedAt, weightKg, reps, group, equipment)

    @Test fun mapping_mergesArmsLegsCore_excludesOther() {
        assertEquals(RadarGroup.CHEST, MuscleGroup.CHEST.toRadarGroup())
        assertEquals(RadarGroup.BACK, MuscleGroup.BACK.toRadarGroup())
        assertEquals(RadarGroup.SHOULDERS, MuscleGroup.SHOULDERS.toRadarGroup())
        assertEquals(RadarGroup.ARMS, MuscleGroup.BICEPS.toRadarGroup())
        assertEquals(RadarGroup.ARMS, MuscleGroup.TRICEPS.toRadarGroup())
        assertEquals(RadarGroup.ARMS, MuscleGroup.FOREARMS.toRadarGroup())
        assertEquals(RadarGroup.QUADS, MuscleGroup.QUADS.toRadarGroup())
        assertEquals(RadarGroup.HAMS_GLUTES, MuscleGroup.HAMSTRINGS.toRadarGroup())
        assertEquals(RadarGroup.HAMS_GLUTES, MuscleGroup.GLUTES.toRadarGroup())
        assertEquals(RadarGroup.CALVES, MuscleGroup.CALVES.toRadarGroup())
        assertEquals(RadarGroup.CORE, MuscleGroup.ABS.toRadarGroup())
        assertNull(MuscleGroup.OTHER.toRadarGroup())
    }

    @Test fun setsPerWeek_dividesByEffectiveWindow() {
        // 4 sessions × 2 sets over 4 weeks; first-ever set at now-28d inside a 30d range:
        // effective window = 28d = 4 weeks → 8 sets / 4 weeks = 2.0.
        val rows =
            listOf(28, 21, 14, 7).flatMap { d ->
                List(2) { row(MuscleGroup.CHEST, now - d * day, sessionId = "s$d") }
            }
        val b = muscleBalance(rows, rangeDays = 30, nowMillis = now)
        val chest = b.groups.first { it.group == RadarGroup.CHEST }
        assertEquals(2.0, chest.setsPerWeek, 1e-9)
        assertEquals(10.0, b.rimSetsPerWeek, 1e-9) // rim floors at the target
        assertEquals(0.2, chest.fraction, 1e-9)
        assertEquals(1.0, b.targetFraction, 1e-9)
        assertFalse(b.isEmpty)
    }

    @Test fun singleRecentSession_flooredAtOneWeek_rimAboveTarget() {
        // 12 sets yesterday, nothing else ever: window floors at 1 week → 12 sets/week,
        // rim = max(12, 10) = 12, chest hits the rim, target ring sits at 10/12.
        val rows = List(12) { row(MuscleGroup.CHEST, now - day, sessionId = "s1") }
        val b = muscleBalance(rows, 30, now)
        val chest = b.groups.first { it.group == RadarGroup.CHEST }
        assertEquals(12.0, chest.setsPerWeek, 1e-9)
        assertEquals(12.0, b.rimSetsPerWeek, 1e-9)
        assertEquals(1.0, chest.fraction, 1e-9)
        assertEquals(10.0 / 12.0, b.targetFraction, 1e-9)
    }

    @Test fun outOfRangeSets_dontCountTowardDose() {
        val rows =
            listOf(
                row(MuscleGroup.CHEST, now - 40 * day),
                row(MuscleGroup.BACK, now - day),
            )
        val b = muscleBalance(rows, 30, now)
        assertEquals(0.0, b.groups.first { it.group == RadarGroup.CHEST }.setsPerWeek, 1e-9)
        // History predates the window → effective window is the full 30d.
        assertEquals(7.0 / 30.0, b.groups.first { it.group == RadarGroup.BACK }.setsPerWeek, 1e-9)
    }

    @Test fun otherSets_feedFootnoteNotSpokes_aloneMeansEmpty() {
        val b = muscleBalance(List(2) { row(MuscleGroup.OTHER, now - day, sessionId = "s1") }, 30, now)
        assertTrue(b.isEmpty)
        assertEquals(2, b.unclassifiedSets)
        assertTrue(b.groups.all { it.setsPerWeek == 0.0 })
    }

    @Test fun emptyInput_isEmpty() {
        val b = muscleBalance(emptyList(), 30, now)
        assertTrue(b.isEmpty)
        assertEquals(0, b.unclassifiedSets)
        assertEquals(8, b.groups.size)
    }

    @Test fun groupTrend_setWeightedMeanOfExerciseTrends() {
        // ex-rise: volumes 100→110→120 (+20%), ex-fall: 100→95→90 (−10%), 3 sets each
        // → weighted mean (20·3 − 10·3)/6 = +5% → UP.
        val rising =
            listOf(14 to 100.0, 7 to 110.0, 0 to 120.0).map { (d, w) ->
                row(MuscleGroup.CHEST, now - d * day, sessionId = "r$d", exerciseId = "ex-rise", weightKg = w, reps = 1)
            }
        val falling =
            listOf(14 to 100.0, 7 to 95.0, 0 to 90.0).map { (d, w) ->
                row(MuscleGroup.CHEST, now - d * day, sessionId = "f$d", exerciseId = "ex-fall", weightKg = w, reps = 1)
            }
        val b = muscleBalance(rising + falling, 90, now)
        assertEquals(TrendDirection.UP, b.groups.first { it.group == RadarGroup.CHEST }.direction)
    }

    @Test fun groupTrend_balancedOpposition_isFlat() {
        // +20% vs −20% with equal set counts → 0% → FLAT.
        val rising =
            listOf(14 to 100.0, 7 to 110.0, 0 to 120.0).map { (d, w) ->
                row(MuscleGroup.CHEST, now - d * day, sessionId = "r$d", exerciseId = "ex-rise", weightKg = w, reps = 1)
            }
        val falling =
            listOf(14 to 100.0, 7 to 90.0, 0 to 80.0).map { (d, w) ->
                row(MuscleGroup.CHEST, now - d * day, sessionId = "f$d", exerciseId = "ex-fall", weightKg = w, reps = 1)
            }
        val b = muscleBalance(rising + falling, 90, now)
        assertEquals(TrendDirection.FLAT, b.groups.first { it.group == RadarGroup.CHEST }.direction)
    }

    @Test fun groupTrend_tooFewSessions_isNull() {
        val rows =
            listOf(
                row(MuscleGroup.BACK, now - 7 * day, sessionId = "a", exerciseId = "ex-b"),
                row(MuscleGroup.BACK, now - day, sessionId = "b", exerciseId = "ex-b"),
            )
        val b = muscleBalance(rows, 90, now)
        assertNull(b.groups.first { it.group == RadarGroup.BACK }.direction)
    }

    @Test fun groupTrend_staleExercise_isNull_doseStillCounts() {
        // 3 sessions, last one 30d ago: inside a 90d dose window but stale for trend (>21d).
        val rows =
            listOf(44, 37, 30).map { d ->
                row(MuscleGroup.QUADS, now - d * day, sessionId = "q$d", exerciseId = "ex-q", weightKg = 100.0 + d)
            }
        val b = muscleBalance(rows, 90, now)
        val quads = b.groups.first { it.group == RadarGroup.QUADS }
        assertNull(quads.direction)
        assertTrue(quads.setsPerWeek > 0.0)
    }

    @Test fun bodyweightGroup_getsDoseAndRepsTrend() {
        // weightKg = 0 throughout; total reps 8→10→12 (+50%) → dose counted, trend UP.
        val rows =
            listOf(14 to 8, 7 to 10, 0 to 12).map { (d, reps) ->
                row(
                    MuscleGroup.ABS,
                    now - d * day,
                    sessionId = "c$d",
                    exerciseId = "ex-abs",
                    weightKg = 0.0,
                    reps = reps,
                    equipment = Equipment.BODYWEIGHT,
                )
            }
        val b = muscleBalance(rows, 90, now)
        val core = b.groups.first { it.group == RadarGroup.CORE }
        assertTrue(core.setsPerWeek > 0.0)
        assertEquals(TrendDirection.UP, core.direction)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.domain.analytics.MuscleBalanceTest"`
Expected: FAIL — compilation errors (`SetWithExercise`, `RadarGroup`, `muscleBalance` unresolved). A red-by-compile-error is the expected TDD red here.

- [ ] **Step 3: Extract the `trendDirection()` helper in Trend.kt**

In `Trend.kt`, add below the `DAY` constant:

```kotlin
/** ±1% direction classification (04-analytics-spec §3) — shared with muscle-balance aggregation. */
fun trendDirection(percent: Double): TrendDirection =
    when {
        percent > 1.0 -> TrendDirection.UP
        percent < -1.0 -> TrendDirection.DOWN
        else -> TrendDirection.FLAT
    }
```

and replace the `val direction = when { … }` block at the end of `trend()` with:

```kotlin
    return TrendResult.Ok(percent, trendDirection(percent))
```

(deleting the now-unused local `direction`).

- [ ] **Step 4: Write the implementation**

Create `app/src/main/kotlin/de/simiil/liftlog/domain/analytics/MuscleBalance.kt`:

```kotlin
package de.simiil.liftlog.domain.analytics

import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import kotlin.math.max

/**
 * Radar spokes: merged muscle groups (spec 2026-07-10). 12 raw groups would crowd the chart and
 * manufacture fake "undertrained" alarms for isolation-only groups under single-group attribution.
 */
enum class RadarGroup {
    CHEST,
    BACK,
    SHOULDERS,
    ARMS,
    QUADS,
    HAMS_GLUTES,
    CALVES,
    CORE,
}

/** Chart-facing merge of the 12 storage groups; OTHER → null = excluded from the radar. */
fun MuscleGroup.toRadarGroup(): RadarGroup? =
    when (this) {
        MuscleGroup.CHEST -> RadarGroup.CHEST
        MuscleGroup.BACK -> RadarGroup.BACK
        MuscleGroup.SHOULDERS -> RadarGroup.SHOULDERS
        MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.FOREARMS -> RadarGroup.ARMS
        MuscleGroup.QUADS -> RadarGroup.QUADS
        MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES -> RadarGroup.HAMS_GLUTES
        MuscleGroup.CALVES -> RadarGroup.CALVES
        MuscleGroup.ABS -> RadarGroup.CORE
        MuscleGroup.OTHER -> null
    }

/** Evidence-based weekly dose landmark; drawn as the radar's dashed target ring. */
const val TARGET_SETS_PER_WEEK = 10.0

/** One logged set joined with its exercise's classification (input row for [muscleBalance]). */
data class SetWithExercise(
    val sessionId: String,
    val exerciseId: String,
    val startedAt: Long,
    val weightKg: Double,
    val reps: Int,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
)

data class GroupBalance(
    val group: RadarGroup,
    val setsPerWeek: Double,
    /** Spoke length as a fraction of the rim (0..1). */
    val fraction: Double,
    /** Set-weighted trend over the window; null = no valid trend (hollow vertex). */
    val direction: TrendDirection?,
)

data class MuscleBalance(
    /** All 8 groups, in [RadarGroup] declaration order. */
    val groups: List<GroupBalance>,
    val rimSetsPerWeek: Double,
    /** Radius of the target ring as a fraction of the rim. */
    val targetFraction: Double,
    /** OTHER-classified sets in range (footnote only; not on the chart). */
    val unclassifiedSets: Int,
    /** True when no radar-classified sets exist in range (card shows the empty state). */
    val isEmpty: Boolean,
)

private const val DAY_MS = 86_400_000L
private const val WEEK_MS = 604_800_000.0

private data class ExerciseTrend(
    val group: RadarGroup,
    val weight: Int,
    val percent: Double,
)

/**
 * Reduces set rows to the radar model (spec 2026-07-10 §Metric semantics).
 *
 * Dose = sets/week over the calendar window `[nowMillis − rangeDays, nowMillis]`; the divisor is
 * the effective window (starts at the first-ever set), floored at one week. Trend = per
 * contributing exercise on its headline-metric series ([summarize]'s `primary`: volume for
 * weighted, total reps for bodyweight) via [trend] with `windowDays = rangeDays`, combined as a
 * set-count-weighted mean of percent changes and classified with the shared ±1% thresholds.
 *
 * [rows] is the FULL set history (repository passes everything since 0); range filtering happens
 * here so per-exercise trend series keep their pre-window context ([trend] windows itself off
 * its last point).
 */
fun muscleBalance(
    rows: List<SetWithExercise>,
    rangeDays: Long,
    nowMillis: Long,
): MuscleBalance {
    val cutoff = nowMillis - rangeDays * DAY_MS
    val inRange = rows.filter { it.startedAt >= cutoff }
    val classified = inRange.filter { it.muscleGroup.toRadarGroup() != null }
    val unclassified = inRange.size - classified.size

    if (classified.isEmpty()) {
        return MuscleBalance(
            groups = RadarGroup.entries.map { GroupBalance(it, 0.0, 0.0, null) },
            rimSetsPerWeek = TARGET_SETS_PER_WEEK,
            targetFraction = 1.0,
            unclassifiedSets = unclassified,
            isEmpty = true,
        )
    }

    val firstEver = rows.minOf { it.startedAt }
    val effectiveStart = max(cutoff, firstEver)
    val weeks = max(1.0, (nowMillis - effectiveStart) / WEEK_MS)

    val setsPerWeek: Map<RadarGroup, Double> =
        classified
            .groupingBy { it.muscleGroup.toRadarGroup()!! }
            .eachCount()
            .mapValues { it.value / weeks }
    val rim = max(setsPerWeek.values.max(), TARGET_SETS_PER_WEEK)

    val exerciseTrends: List<ExerciseTrend> =
        classified
            .groupBy { it.exerciseId }
            .mapNotNull { (id, inRangeSets) ->
                val group = inRangeSets.first().muscleGroup.toRadarGroup()!!
                val history = rows.filter { it.exerciseId == id }
                val summary =
                    summarize(
                        equipment = history.first().equipment,
                        sets = history.map { DatedSet(it.sessionId, it.startedAt, it.weightKg, it.reps) },
                        nowMillis = nowMillis,
                    ) ?: return@mapNotNull null
                val ok =
                    trend(
                        summary.sessions.map { TrendPoint(it.timeMillis, it.primary) },
                        nowMillis,
                        windowDays = rangeDays,
                    ) as? TrendResult.Ok ?: return@mapNotNull null
                ExerciseTrend(group, inRangeSets.size, ok.percent)
            }
    val directionByGroup: Map<RadarGroup, TrendDirection> =
        exerciseTrends
            .groupBy { it.group }
            .mapValues { (_, ts) ->
                trendDirection(ts.sumOf { it.percent * it.weight } / ts.sumOf { it.weight })
            }

    return MuscleBalance(
        groups =
            RadarGroup.entries.map { g ->
                val spw = setsPerWeek[g] ?: 0.0
                GroupBalance(g, spw, spw / rim, directionByGroup[g])
            },
        rimSetsPerWeek = rim,
        targetFraction = TARGET_SETS_PER_WEEK / rim,
        unclassifiedSets = unclassified,
        isEmpty = false,
    )
}
```

- [ ] **Step 5: Run the tests to verify they pass (plus the trend regression suite)**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.domain.analytics.MuscleBalanceTest" --tests "de.simiil.liftlog.domain.analytics.TrendTest"`
Expected: PASS (all MuscleBalanceTest cases + TrendTest unaffected by the helper extraction).

- [ ] **Step 6: Format and commit**

```bash
./gradlew ktlintFormat
git add app/src/main/kotlin/de/simiil/liftlog/domain/analytics/MuscleBalance.kt \
        app/src/main/kotlin/de/simiil/liftlog/domain/analytics/Trend.kt \
        app/src/test/kotlin/de/simiil/liftlog/domain/analytics/MuscleBalanceTest.kt
git commit -m "feat(analytics): muscle-balance domain model — sets/week dose + set-weighted trend (#43)"
```

---

### Task 3: Repository — `observeSetsWithExercise()`

**Files:**
- Modify: `app/src/main/kotlin/de/simiil/liftlog/domain/repository/AnalyticsRepository.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/repository/AnalyticsRepositoryImpl.kt`
- Modify: `app/src/test/kotlin/de/simiil/liftlog/ui/analytics/AnalyticsBrowserViewModelTest.kt` (FakeRepo)
- Modify: `app/src/test/kotlin/de/simiil/liftlog/ui/analytics/ExerciseDetailViewModelTest.kt` (anonymous fake)

**Interfaces:**
- Consumes: `SetWithExercise` (Task 2), `AnalyticsDao.observeAllSetsSince(0L)`, `ExerciseRepository.observeAll()`.
- Produces: `AnalyticsRepository.observeSetsWithExercise(): Flow<List<SetWithExercise>>` (used by Task 4).

No new unit test: the method is a two-flow join with no logic beyond the map, matching `observePrSessionIds` which is likewise covered indirectly (data-layer correctness is instrumented-DAO-test territory; the join's consumers are tested in Tasks 2 and 4).

- [ ] **Step 1: Add the method to the interface**

In `AnalyticsRepository.kt`, add the import `de.simiil.liftlog.domain.analytics.SetWithExercise` and, below `observePrSessionIds()`:

```kotlin
    /** Every logged set (finished sessions, soft-deletes excluded) + its exercise's classification. */
    fun observeSetsWithExercise(): Flow<List<SetWithExercise>>
```

- [ ] **Step 2: Implement it**

In `AnalyticsRepositoryImpl.kt`, add the import `de.simiil.liftlog.domain.analytics.SetWithExercise` and, below `observePrSessionIds()` (same `combine` + `flowOn(Dispatchers.Default)` pattern):

```kotlin
        override fun observeSetsWithExercise(): Flow<List<SetWithExercise>> =
            combine(
                analyticsDao.observeAllSetsSince(0L),
                exerciseRepository.observeAll(),
            ) { rows, exercises ->
                val byId = exercises.associateBy { it.id }
                rows.mapNotNull { r ->
                    byId[r.exerciseId]?.let { ex ->
                        SetWithExercise(r.sessionId, r.exerciseId, r.startedAt, r.weightKg, r.reps, ex.muscleGroup, ex.equipment)
                    }
                }
            }.flowOn(Dispatchers.Default)
```

- [ ] **Step 3: Extend the two test fakes (compilation break otherwise)**

`AnalyticsBrowserViewModelTest.kt` — give `FakeRepo` a rows parameter (new tests in Task 4 will use it) and the override; add imports `de.simiil.liftlog.domain.analytics.SetWithExercise`:

```kotlin
    private class FakeRepo(
        val list: List<TrainedExercise>,
        val rows: List<SetWithExercise> = emptyList(),
    ) : AnalyticsRepository {
```

and below `observePrSessionIds()`:

```kotlin
        override fun observeSetsWithExercise(): Flow<List<SetWithExercise>> = flowOf(rows)
```

`ExerciseDetailViewModelTest.kt` — in the anonymous `object : AnalyticsRepository` inside `vm(...)`, add:

```kotlin
            override fun observeSetsWithExercise() = flowOf(emptyList<de.simiil.liftlog.domain.analytics.SetWithExercise>())
```

- [ ] **Step 4: Run the unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (everything compiles; no behavior change).

- [ ] **Step 5: Format and commit**

```bash
./gradlew ktlintFormat
git add app/src/main/kotlin/de/simiil/liftlog/domain/repository/AnalyticsRepository.kt \
        app/src/main/kotlin/de/simiil/liftlog/data/repository/AnalyticsRepositoryImpl.kt \
        app/src/test/kotlin/de/simiil/liftlog/ui/analytics/AnalyticsBrowserViewModelTest.kt \
        app/src/test/kotlin/de/simiil/liftlog/ui/analytics/ExerciseDetailViewModelTest.kt
git commit -m "feat(analytics): expose set rows joined with exercise classification (#43)"
```

---

### Task 4: ViewModel — balance state on `AnalyticsBrowserViewModel` (TDD)

**Files:**
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/analytics/AnalyticsBrowserViewModel.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/ui/analytics/AnalyticsBrowserViewModelTest.kt`

**Interfaces:**
- Consumes: `observeSetsWithExercise()` (Task 3), `muscleBalance()`/`MuscleBalance` (Task 2), `Range` (ExerciseDetailViewModel.kt), `java.time.Clock` (Hilt binding exists — `ExerciseDetailViewModel` already injects it).
- Produces (used by Task 7):
  - `data class MuscleBalanceUiState(val balance: MuscleBalance? = null, val selectedRange: Range = Range.D90)`
  - `AnalyticsBrowserViewModel.balanceState: StateFlow<MuscleBalanceUiState>`
  - `AnalyticsBrowserViewModel.onBalanceRangeChange(r: Range)`
  - **Constructor gains a 4th parameter** `clock: Clock` — existing test call sites must add it.

- [ ] **Step 1: Write the failing tests**

In `AnalyticsBrowserViewModelTest.kt`, add imports:

```kotlin
import de.simiil.liftlog.domain.analytics.RadarGroup
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
```

add fixtures at class level:

```kotlin
    private val now = 1_000_000_000_000L
    private val day = 86_400_000L
    private val fixedClock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
```

and add the tests:

```kotlin
    @Test fun muscleBalance_exposedAndRecomputesOnRangeChange() =
        runTest {
            // 8 chest sets yesterday → effective window floors at 1 week → 8.0 sets/week.
            val rows =
                List(8) {
                    SetWithExercise("s1", "e1", now - day, 100.0, 5, MuscleGroup.CHEST, Equipment.BARBELL)
                }
            val vm = AnalyticsBrowserViewModel(FakeRepo(emptyList(), rows), FakeSettings(), names, fixedClock)
            vm.balanceState.test {
                var s = awaitItem()
                while (s.balance == null) s = awaitItem()
                assertEquals(Range.D90, s.selectedRange)
                assertEquals(8.0, s.balance!!.groups.first { it.group == RadarGroup.CHEST }.setsPerWeek, 1e-9)
                vm.onBalanceRangeChange(Range.D30)
                s = awaitItem()
                assertEquals(Range.D30, s.selectedRange)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun muscleBalance_noSets_flagsEmpty() =
        runTest {
            val vm = AnalyticsBrowserViewModel(FakeRepo(emptyList()), FakeSettings(), names, fixedClock)
            vm.balanceState.test {
                var s = awaitItem()
                while (s.balance == null) s = awaitItem()
                assertEquals(true, s.balance!!.isEmpty)
                cancelAndIgnoreRemainingEvents()
            }
        }
```

Update the three existing `AnalyticsBrowserViewModel(...)` constructions in this file to pass `fixedClock` as the 4th argument.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.ui.analytics.AnalyticsBrowserViewModelTest"`
Expected: FAIL — compilation error (`balanceState` unresolved; constructor arity).

- [ ] **Step 3: Implement the ViewModel additions**

In `AnalyticsBrowserViewModel.kt`, add imports:

```kotlin
import de.simiil.liftlog.domain.analytics.MuscleBalance
import de.simiil.liftlog.domain.analytics.muscleBalance
import java.time.Clock
```

Add below `AnalyticsBrowserUiState`:

```kotlin
data class MuscleBalanceUiState(
    val balance: MuscleBalance? = null,
    val selectedRange: Range = Range.D90,
)
```

Add `private val clock: Clock,` as the 4th constructor parameter, and inside the class:

```kotlin
        private val balanceRange = MutableStateFlow(Range.D90)

        /** Muscle-balance radar state; separate from [uiState] so range taps don't re-emit the browser list. */
        val balanceState: StateFlow<MuscleBalanceUiState> =
            combine(analyticsRepository.observeSetsWithExercise(), balanceRange) { rows, range ->
                MuscleBalanceUiState(
                    balance = muscleBalance(rows, range.days, clock.millis()),
                    selectedRange = range,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MuscleBalanceUiState())

        fun onBalanceRangeChange(r: Range) {
            balanceRange.value = r
        }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.ui.analytics.AnalyticsBrowserViewModelTest"`
Expected: PASS (new tests + the three pre-existing tests with the added clock argument).

- [ ] **Step 5: Format and commit**

```bash
./gradlew ktlintFormat
git add app/src/main/kotlin/de/simiil/liftlog/ui/analytics/AnalyticsBrowserViewModel.kt \
        app/src/test/kotlin/de/simiil/liftlog/ui/analytics/AnalyticsBrowserViewModelTest.kt
git commit -m "feat(analytics): muscle-balance state with range selection in browser VM (#43)"
```

---

### Task 5: Extract shared `RangePills` (refactor, no behavior change)

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/ui/analytics/RangePills.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/analytics/ExerciseDetailScreen.kt` (lines ~93–133 pill row + `rangeLabel` at ~243)

**Interfaces:**
- Produces: `@Composable fun RangePills(selected: Range, onChange: (Range) -> Unit, modifier: Modifier = Modifier)` (used by Task 7).

- [ ] **Step 1: Create the shared composable**

Create `RangePills.kt` — the pill row moved verbatim from `ExerciseDetailScreen.kt` (visual behavior must not change):

```kotlin
package de.simiil.liftlog.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.simiil.liftlog.R

/** Equal-width 30d/90d/1y/all pills, shared by the exercise-detail screen and the muscle-balance card. */
@Composable
fun RangePills(
    selected: Range,
    onChange: (Range) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Range.entries.forEach { r ->
            val isSelected = r == selected
            Surface(
                onClick = { onChange(r) },
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(10.dp),
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        rangeLabel(r),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
            }
        }
    }
}
```

Then MOVE the existing `rangeLabel` function from `ExerciseDetailScreen.kt` (~line 243) into `RangePills.kt`, changing only `private` → `internal` (its body is unchanged):

```kotlin
@Composable
internal fun rangeLabel(r: Range) =
    stringResource(
        when (r) {
            Range.D30 -> R.string.range_30d
            Range.D90 -> R.string.range_90d
            Range.Y1 -> R.string.range_1y
            Range.ALL -> R.string.range_all
        },
    )
```

(Before moving, diff against the current source at `ExerciseDetailScreen.kt:243` — if it differs from the above, the source file wins; move it verbatim.)

- [ ] **Step 2: Replace the inline pill row in ExerciseDetailScreen**

Replace the whole `item { Row(...) { Range.entries.forEach { ... } } }` block (the range pills item, ~lines 93–133) with:

```kotlin
            item {
                RangePills(
                    selected = ui.selectedRange,
                    onChange = viewModel::onRangeChange,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
            }
```

Delete the old private `rangeLabel` from `ExerciseDetailScreen.kt` and any imports that become unused.

- [ ] **Step 3: Verify no behavior change**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 4: Format and commit**

```bash
./gradlew ktlintFormat
git add app/src/main/kotlin/de/simiil/liftlog/ui/analytics/RangePills.kt \
        app/src/main/kotlin/de/simiil/liftlog/ui/analytics/ExerciseDetailScreen.kt
git commit -m "refactor(analytics): extract shared RangePills from exercise detail (#43)"
```

---

### Task 6: `RadarChart` composable (custom Canvas)

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/ui/components/charts/RadarChart.kt`

**Interfaces:**
- Produces (used by Task 7):
  - `data class RadarSpoke(val label: String, val fraction: Float, val vertexColor: Color, val vertexFilled: Boolean)`
  - `@Composable fun RadarChart(spokes: List<RadarSpoke>, targetFraction: Float, modifier: Modifier = Modifier, contentDescription: String? = null)`

No unit test (pure rendering, like `Sparkline.kt`); compile-verified here, visually verified in Task 8.

- [ ] **Step 1: Write the composable**

```kotlin
package de.simiil.liftlog.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** One radar axis: label at the spoke tip, spoke length as a 0..1 rim fraction, trend-colored vertex. */
data class RadarSpoke(
    val label: String,
    val fraction: Float,
    val vertexColor: Color,
    /** false → hollow vertex (no trend data); true → filled (up/down/flat). */
    val vertexFilled: Boolean,
)

/**
 * Muscle-balance radar (spec 2026-07-10 §UI). Hand-drawn Canvas — Vico is Cartesian-only;
 * follows Sparkline.kt's precedent. Shape = dose polygon, vertex color = trend, dashed ring =
 * the sets/week target. Spoke 0 sits at 12 o'clock; order proceeds clockwise in list order.
 */
@Composable
fun RadarChart(
    spokes: List<RadarSpoke>,
    targetFraction: Float,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (spokes.size < 3) return
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val ringColor = MaterialTheme.colorScheme.onSurfaceVariant
    val polygonColor = MaterialTheme.colorScheme.primary
    // Hollow vertices get a hole in the card's surface color so the polygon doesn't show through.
    val holeColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val labelStyle =
        MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier
            .aspectRatio(1f)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val rim = min(size.width, size.height) / 2f - 34.dp.toPx() // label margin
        val n = spokes.size

        fun angleRad(i: Int) = (-90.0 + i * 360.0 / n) * PI / 180.0

        fun point(
            i: Int,
            fraction: Float,
        ): Offset {
            val a = angleRad(i)
            return center + Offset((cos(a) * rim * fraction).toFloat(), (sin(a) * rim * fraction).toFloat())
        }

        // Grid: 3 faint rings + one spoke line per axis.
        for (ring in 1..3) {
            drawCircle(gridColor, radius = rim * ring / 3f, center = center, style = Stroke(1.dp.toPx()))
        }
        for (i in 0 until n) {
            drawLine(gridColor, center, point(i, 1f), strokeWidth = 1.dp.toPx())
        }

        // Dashed target ring.
        drawCircle(
            ringColor,
            radius = rim * targetFraction.coerceIn(0f, 1f),
            center = center,
            style =
                Stroke(
                    1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                ),
        )

        // Dose polygon: translucent fill + solid stroke.
        val path = Path()
        spokes.forEachIndexed { i, s ->
            val p = point(i, s.fraction.coerceIn(0f, 1f))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(path, polygonColor.copy(alpha = 0.25f))
        drawPath(path, polygonColor, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))

        // Trend vertices: filled = up/down/flat, hollow = no trend data.
        spokes.forEachIndexed { i, s ->
            val p = point(i, s.fraction.coerceIn(0f, 1f))
            if (s.vertexFilled) {
                drawCircle(s.vertexColor, radius = 4.dp.toPx(), center = p)
            } else {
                drawCircle(holeColor, radius = 4.dp.toPx(), center = p)
                drawCircle(s.vertexColor, radius = 4.dp.toPx(), center = p, style = Stroke(1.5.dp.toPx()))
            }
        }

        // Labels just outside the rim, width-constrained so long labels ("Beinbeuger & Gesäß")
        // wrap instead of overflowing the canvas. The (0.5 − 0.5·cos/sin) factors slide the
        // anchor from left/top-aligned (right/bottom of chart) to right/bottom-aligned (left/top).
        spokes.forEachIndexed { i, s ->
            val a = angleRad(i)
            val anchor =
                center + Offset((cos(a) * (rim + 8.dp.toPx())).toFloat(), (sin(a) * (rim + 8.dp.toPx())).toFloat())
            val measured =
                textMeasurer.measure(
                    s.label,
                    labelStyle,
                    constraints = Constraints(maxWidth = 76.dp.roundToPx()),
                )
            drawText(
                measured,
                topLeft =
                    Offset(
                        anchor.x - measured.size.width * (0.5f - 0.5f * cos(a).toFloat()),
                        anchor.y - measured.size.height * (0.5f - 0.5f * sin(a).toFloat()),
                    ),
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Format and commit**

```bash
./gradlew ktlintFormat
git add app/src/main/kotlin/de/simiil/liftlog/ui/components/charts/RadarChart.kt
git commit -m "feat(charts): custom-canvas radar chart composable (#43)"
```

---

### Task 7: Strings + `MuscleBalanceCard` + Analytics screen wiring

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-de/strings.xml`
- Create: `app/src/main/kotlin/de/simiil/liftlog/ui/analytics/MuscleBalanceCard.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/analytics/AnalyticsScreen.kt`

**Interfaces:**
- Consumes: `MuscleBalanceUiState`, `onBalanceRangeChange` (Task 4), `RangePills` (Task 5), `RadarChart`/`RadarSpoke` (Task 6), `MuscleBalance`/`RadarGroup`/`TARGET_SETS_PER_WEEK` (Task 2), `LocalLiftLogColors.current.success`, existing `muscle_*` strings.
- Produces: `@Composable fun MuscleBalanceCard(state: MuscleBalanceUiState, onRangeChange: (Range) -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Add string resources**

`app/src/main/res/values/strings.xml`, after the `analytics_need_two` string:

```xml
    <!-- Muscle-balance radar card (spec 2026-07-10) -->
    <string name="balance_title">Muscle balance</string>
    <string name="balance_empty">Log workouts to see your muscle balance</string>
    <string name="balance_group_arms">Arms</string>
    <string name="balance_group_hams_glutes">Hams &amp; Glutes</string>
    <string name="balance_group_core">Core</string>
    <string name="balance_legend_up">up</string>
    <string name="balance_legend_down">down</string>
    <string name="balance_legend_flat">flat</string>
    <string name="balance_legend_no_trend">no data</string>
    <string name="balance_legend_target">target %1$d sets/week</string>
    <string name="balance_cd_group">%1$s: %2$s sets per week, %3$s</string>
    <string name="balance_cd_up">trending up</string>
    <string name="balance_cd_down">trending down</string>
    <string name="balance_cd_flat">flat</string>
    <string name="balance_cd_no_trend">no trend data</string>
    <plurals name="balance_unclassified">
        <item quantity="one">%1$d set in “Other” exercises not shown</item>
        <item quantity="other">%1$d sets in “Other” exercises not shown</item>
    </plurals>
```

`app/src/main/res/values-de/strings.xml`, same position (flag for native-speaker review per `docs/09-i18n-german-spot-check.md` conventions):

```xml
    <!-- Muscle-balance radar card (spec 2026-07-10) -->
    <string name="balance_title">Muskelbalance</string>
    <string name="balance_empty">Logge Einheiten, um deine Muskelbalance zu sehen</string>
    <string name="balance_group_arms">Arme</string>
    <string name="balance_group_hams_glutes">Beinbeuger &amp; Gesäß</string>
    <string name="balance_group_core">Rumpf</string>
    <string name="balance_legend_up">steigend</string>
    <string name="balance_legend_down">fallend</string>
    <string name="balance_legend_flat">stabil</string>
    <string name="balance_legend_no_trend">keine Daten</string>
    <string name="balance_legend_target">Ziel: %1$d Sätze/Woche</string>
    <string name="balance_cd_group">%1$s: %2$s Sätze pro Woche, %3$s</string>
    <string name="balance_cd_up">steigender Trend</string>
    <string name="balance_cd_down">fallender Trend</string>
    <string name="balance_cd_flat">stabil</string>
    <string name="balance_cd_no_trend">keine Trend-Daten</string>
    <plurals name="balance_unclassified">
        <item quantity="one">%1$d Satz in Übungen der Gruppe „Sonstige“ wird nicht angezeigt</item>
        <item quantity="other">%1$d Sätze in Übungen der Gruppe „Sonstige“ werden nicht angezeigt</item>
    </plurals>
```

- [ ] **Step 2: Create the card**

Create `app/src/main/kotlin/de/simiil/liftlog/ui/analytics/MuscleBalanceCard.kt`:

```kotlin
package de.simiil.liftlog.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.analytics.MuscleBalance
import de.simiil.liftlog.domain.analytics.RadarGroup
import de.simiil.liftlog.domain.analytics.TARGET_SETS_PER_WEEK
import de.simiil.liftlog.domain.analytics.TrendDirection
import de.simiil.liftlog.ui.components.charts.RadarChart
import de.simiil.liftlog.ui.components.charts.RadarSpoke
import de.simiil.liftlog.ui.theme.LocalLiftLogColors

/** Chart display order: chest at 12 o'clock, clockwise; push right, pull/posterior left. */
private val displayOrder =
    listOf(
        RadarGroup.CHEST,
        RadarGroup.SHOULDERS,
        RadarGroup.ARMS,
        RadarGroup.QUADS,
        RadarGroup.CALVES,
        RadarGroup.HAMS_GLUTES,
        RadarGroup.CORE,
        RadarGroup.BACK,
    )

@Composable
fun MuscleBalanceCard(
    state: MuscleBalanceUiState,
    onRangeChange: (Range) -> Unit,
    modifier: Modifier = Modifier,
) {
    val balance = state.balance ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                stringResource(R.string.balance_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            RangePills(state.selectedRange, onRangeChange)
            if (balance.isEmpty) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.balance_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                RadarChart(
                    spokes = radarSpokes(balance),
                    targetFraction = balance.targetFraction.toFloat(),
                    contentDescription = balanceDescription(balance),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Legend()
            }
            if (balance.unclassifiedSets > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    pluralStringResource(
                        R.plurals.balance_unclassified,
                        balance.unclassifiedSets,
                        balance.unclassifiedSets,
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun radarSpokes(balance: MuscleBalance): List<RadarSpoke> {
    val byGroup = balance.groups.associateBy { it.group }
    val success = LocalLiftLogColors.current.success
    return displayOrder.map { g ->
        val gb = byGroup.getValue(g)
        RadarSpoke(
            label = stringResource(groupLabel(g)),
            fraction = gb.fraction.toFloat(),
            vertexColor =
                when (gb.direction) {
                    TrendDirection.UP -> success
                    TrendDirection.DOWN -> MaterialTheme.colorScheme.error
                    TrendDirection.FLAT, null -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            vertexFilled = gb.direction != null,
        )
    }
}

private fun groupLabel(g: RadarGroup) =
    when (g) {
        RadarGroup.CHEST -> R.string.muscle_chest
        RadarGroup.BACK -> R.string.muscle_back
        RadarGroup.SHOULDERS -> R.string.muscle_shoulders
        RadarGroup.ARMS -> R.string.balance_group_arms
        RadarGroup.QUADS -> R.string.muscle_quads
        RadarGroup.HAMS_GLUTES -> R.string.balance_group_hams_glutes
        RadarGroup.CALVES -> R.string.muscle_calves
        RadarGroup.CORE -> R.string.balance_group_core
    }

/**
 * Spoken summary for TalkBack (chart is non-text content; ProgressLineChart convention).
 * NOTE: composable calls must stay inside the `map` (inline) — `joinToString`'s lambda is not
 * inline, so `stringResource` inside it would not compile.
 */
@Composable
private fun balanceDescription(balance: MuscleBalance): String {
    val byGroup = balance.groups.associateBy { it.group }
    val parts =
        displayOrder.map { g ->
            val gb = byGroup.getValue(g)
            stringResource(
                R.string.balance_cd_group,
                stringResource(groupLabel(g)),
                String.format(java.util.Locale.getDefault(), "%.1f", gb.setsPerWeek),
                stringResource(
                    when (gb.direction) {
                        TrendDirection.UP -> R.string.balance_cd_up
                        TrendDirection.DOWN -> R.string.balance_cd_down
                        TrendDirection.FLAT -> R.string.balance_cd_flat
                        null -> R.string.balance_cd_no_trend
                    },
                ),
            )
        }
    return parts.joinToString(", ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LegendDot(LocalLiftLogColors.current.success, filled = true, stringResource(R.string.balance_legend_up))
        LegendDot(MaterialTheme.colorScheme.error, filled = true, stringResource(R.string.balance_legend_down))
        LegendDot(neutral, filled = true, stringResource(R.string.balance_legend_flat))
        LegendDot(neutral, filled = false, stringResource(R.string.balance_legend_no_trend))
        LegendDash(neutral, stringResource(R.string.balance_legend_target, TARGET_SETS_PER_WEEK.toInt()))
    }
}

@Composable
private fun LegendDot(
    color: Color,
    filled: Boolean,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(8.dp)) {
            if (filled) drawCircle(color) else drawCircle(color, style = Stroke(1.5.dp.toPx()))
        }
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendDash(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.width(14.dp).height(8.dp)) {
            drawLine(
                color,
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
        }
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

- [ ] **Step 3: Restructure `AnalyticsScreen` to a single scrollable list and slot the card in**

The screen currently stacks `WeekCard` + `SearchBar` in a fixed `Column` above the `LazyColumn`. With a ~360dp card added, the fixed header would crush the exercise list on small screens, so headers become `LazyColumn` items. In `AnalyticsScreen.kt`, replace the body of `AnalyticsScreen` (the `Column(...) { ... }` block) with:

```kotlin
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val balance by viewModel.balanceState.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.analytics_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        LazyColumn {
            ui.week?.let { week ->
                item(key = "week") {
                    WeekCard(week, ui.unit)
                    Spacer(Modifier.height(12.dp))
                }
            }
            item(key = "balance") {
                MuscleBalanceCard(balance, viewModel::onBalanceRangeChange, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }
            item(key = "search") {
                SearchBar(ui.query, viewModel::onQueryChange)
            }
            if (ui.exercises.isEmpty()) {
                item(key = "empty") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.analytics_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(ui.exercises, key = { it.id }) { ex ->
                    ExerciseRow(ex, ui.unit, viewModel, onOpenExercise)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
```

(`MuscleBalanceCard` returns early while `balance.balance == null`, so the item renders nothing until the first emission. Exercise-row keys are UUIDs — no collision with the string header keys.)

- [ ] **Step 4: Build and run all checks**

Run: `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, all tests pass, lint clean. (If `FlowRow`'s `ExperimentalLayoutApi` opt-in trips a lint/ktlint rule, keep the `@OptIn` annotation at the `Legend` function as written — that is the sanctioned form.)

- [ ] **Step 5: Format and commit**

```bash
./gradlew ktlintFormat
git add app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml \
        app/src/main/kotlin/de/simiil/liftlog/ui/analytics/MuscleBalanceCard.kt \
        app/src/main/kotlin/de/simiil/liftlog/ui/analytics/AnalyticsScreen.kt
git commit -m "feat(analytics): muscle-balance radar card on the Analytics screen (#43)"
```

---

### Task 8: On-device verification

**Files:** none (verification only).

- [ ] **Step 1: Install and seed**

```bash
./gradlew installDebug
```

Launch the app on `emulator-5554`. If the database is empty, use Settings → "Load demo data (Debug)" (`settings_seed_demo`) to seed synthetic history.

- [ ] **Step 2: Drive the Analytics tab and screenshot**

Open the Analytics tab (`adb shell` + `uiautomator dump` to resolve bounds, tap, `screencap`). Verify against the spec:
- Radar card renders below the week card with 8 labeled spokes (Chest at 12 o'clock).
- Range pills switch 30d/90d/1y/all and the polygon changes.
- Dashed target ring visible; vertices colored (green/red/neutral, hollow where no trend).
- Empty state: wipe data (fresh install) → card shows "Log workouts to see your muscle balance".
- Switch device language to German → all card strings render in German, layout survives the longer labels ("Beinbeuger & Gesäß").

- [ ] **Step 3: TalkBack spot check (optional but recommended)**

With TalkBack on, focus the radar: it should read the per-group summary ("Chest: 12.0 sets per week, trending up, …").

- [ ] **Step 4: Record findings**

Note any visual defects (label collisions, ring clipping) and fix within this task before proceeding to the PR.
