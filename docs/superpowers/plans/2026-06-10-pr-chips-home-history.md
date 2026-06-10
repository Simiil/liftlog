# PR Chips on Home & History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a small "PR" chip on Home recent-workout rows and History session cards when that session set at least one personal record.

**Architecture:** PRs stay derived-on-read. A new pure domain function `prSessionIds()` reuses the existing `summarize()` per exercise; `AnalyticsRepositoryImpl` exposes it as `observePrSessionIds(): Flow<Set<String>>` (computed off the main thread via `flowOn`); Home/History ViewModels join the set into their card models; a shared `PrBadge` composable renders the chip.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room, Hilt, kotlinx.coroutines Flow, JUnit4 + Turbine for tests.

**Spec:** `docs/superpowers/specs/2026-06-10-pr-chips-home-history-design.md` (approved). One refinement to spec §4: HomeViewModel chains a second `.combine` onto its existing 5-flow `combine` instead of switching to the vararg/Array overload — same behavior, no unchecked casts.

**Branch:** `pr-chips` (already created; spec committed as `276153d`).

**Conventions:** Commit messages use conventional-commit style with the trailer
`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
All gradle commands run from the repo root `/Users/sam/Code/liftlog`.

---

### Task 1: Add `exerciseId` to the `SetRow` projection

The all-sets analytics query can't attribute sets to exercises today. Add the column to the projection (no schema change — `session_exercises` is already joined).

**Files:**
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/dao/Relations.kt:23`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/dao/AnalyticsDao.kt:10,21`
- Modify: `app/src/test/kotlin/de/simiil/liftlog/data/repository/AnalyticsRepositoryImplTest.kt:62-63,96`
- Modify: `app/src/androidTest/kotlin/de/simiil/liftlog/data/dao/AnalyticsDaoTest.kt:182-195`

- [ ] **Step 1: Extend the projection class**

In `Relations.kt`, replace:

```kotlin
/** Analytics projection (02-data-spec §4): set-level rows; e1RM math stays in pure Kotlin (M4). */
data class SetRow(val sessionId: String, val startedAt: Long, val weightKg: Double, val reps: Int)
```

with:

```kotlin
/** Analytics projection (02-data-spec §4): set-level rows; e1RM math stays in pure Kotlin (M4). */
data class SetRow(
    val sessionId: String,
    val exerciseId: String,
    val startedAt: Long,
    val weightKg: Double,
    val reps: Int,
)
```

- [ ] **Step 2: Select the column in both DAO queries**

In `AnalyticsDao.kt`, change the SELECT line of **both** `observeSetsForExercise` and `observeAllSetsSince` from:

```sql
SELECT s.id AS sessionId, s.startedAt AS startedAt, ls.weightKg AS weightKg, ls.reps AS reps
```

to:

```sql
SELECT s.id AS sessionId, se.exerciseId AS exerciseId, s.startedAt AS startedAt, ls.weightKg AS weightKg, ls.reps AS reps
```

(Everything else in the queries stays identical.)

- [ ] **Step 3: Fix the three `SetRow` constructions in the unit test**

In `AnalyticsRepositoryImplTest.kt`, the fake DAO data gains the new second argument:

```kotlin
            allSets = listOf(
                SetRow("a", "e1", thisWeek, 100.0, 5), SetRow("a", "e1", thisWeek, 100.0, 5),
                SetRow("b", "e1", lastWeek, 50.0, 10),
            ),
```

and:

```kotlin
            perExercise = mapOf("e1" to listOf(SetRow("s1", "e1", nowMs - 5 * day, 100.0, 5))),
```

- [ ] **Step 4: Assert the new column in the instrumented DAO test**

In `AnalyticsDaoTest.kt`, test `observeSetsForExercise_returnsCorrectWeightAndReps`, add after the existing assertions inside the `test { … }` block:

```kotlin
            // exerciseId is projected through the session_exercises join
            assertEquals("ex1", rows[0].exerciseId)
            assertEquals("ex1", rows[1].exerciseId)
```

- [ ] **Step 5: Run the JVM unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL (Room validates query↔projection mapping at compile time via KSP, so a wrong column alias fails this build).

- [ ] **Step 6: Run the scoped instrumented test (emulator `emulator-5554` is usually up; skip if no device — CI covers it)**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.data.dao.AnalyticsDaoTest`
Expected: BUILD SUCCESSFUL, all `AnalyticsDaoTest` tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/de/simiil/liftlog/data/dao/Relations.kt \
        app/src/main/kotlin/de/simiil/liftlog/data/dao/AnalyticsDao.kt \
        app/src/test/kotlin/de/simiil/liftlog/data/repository/AnalyticsRepositoryImplTest.kt \
        app/src/androidTest/kotlin/de/simiil/liftlog/data/dao/AnalyticsDaoTest.kt
git commit -m "feat(analytics): project exerciseId in SetRow

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Pure domain function `prSessionIds()` (TDD)

**Files:**
- Test: `app/src/test/kotlin/de/simiil/liftlog/domain/analytics/PrSessionsTest.kt` (create)
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/analytics/PrSessions.kt`

- [ ] **Step 1: Write the failing test**

Create `PrSessionsTest.kt`:

```kotlin
package de.simiil.liftlog.domain.analytics

import de.simiil.liftlog.domain.model.Equipment
import org.junit.Assert.assertEquals
import org.junit.Test

class PrSessionsTest {
    private val day = 86_400_000L
    private val now = 1_000_000_000_000L

    @Test fun emptyInput_returnsEmptySet() =
        assertEquals(emptySet<String>(), prSessionIds(emptyMap(), emptyMap(), now))

    @Test fun firstSessionOfAnExercise_isAPr() {
        val result = prSessionIds(
            setsByExercise = mapOf("e1" to listOf(DatedSet("s1", now - 5 * day, 100.0, 5))),
            equipmentById = mapOf("e1" to Equipment.BARBELL),
            nowMillis = now,
        )
        assertEquals(setOf("s1"), result)
    }

    @Test fun tieDoesNotFlag() {
        val result = prSessionIds(
            setsByExercise = mapOf(
                "e1" to listOf(
                    DatedSet("s1", now - 10 * day, 100.0, 5),
                    DatedSet("s2", now - 5 * day, 100.0, 5),  // equal best — not a PR
                ),
            ),
            equipmentById = mapOf("e1" to Equipment.BARBELL),
            nowMillis = now,
        )
        assertEquals(setOf("s1"), result)
    }

    @Test fun unionsAcrossExercises() {
        val result = prSessionIds(
            setsByExercise = mapOf(
                "bench" to listOf(
                    DatedSet("s1", now - 20 * day, 100.0, 5),  // PR (first ever)
                    DatedSet("s2", now - 10 * day, 100.0, 5),  // tie — no flag from bench
                ),
                "row" to listOf(
                    DatedSet("s2", now - 10 * day, 60.0, 5),   // PR (first for row) — flags s2
                    DatedSet("s3", now - 5 * day, 65.0, 5),    // PR (heavier)
                ),
            ),
            equipmentById = mapOf("bench" to Equipment.BARBELL, "row" to Equipment.BARBELL),
            nowMillis = now,
        )
        assertEquals(setOf("s1", "s2", "s3"), result)
    }

    @Test fun bodyweightExercise_flagsRepsPr() {
        val result = prSessionIds(
            setsByExercise = mapOf(
                "pullup" to listOf(
                    DatedSet("s1", now - 15 * day, 0.0, 8),   // PR (first ever)
                    DatedSet("s2", now - 10 * day, 0.0, 10),  // PR (more reps)
                    DatedSet("s3", now - 5 * day, 0.0, 9),    // below best — no flag
                ),
            ),
            equipmentById = mapOf("pullup" to Equipment.BODYWEIGHT),
            nowMillis = now,
        )
        assertEquals(setOf("s1", "s2"), result)
    }

    @Test fun exerciseWithoutKnownEquipment_isSkipped() {
        val result = prSessionIds(
            setsByExercise = mapOf("ghost" to listOf(DatedSet("s1", now - 5 * day, 100.0, 5))),
            equipmentById = emptyMap(),
            nowMillis = now,
        )
        assertEquals(emptySet<String>(), result)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.domain.analytics.PrSessionsTest"`
Expected: FAIL — compilation error, `unresolved reference: prSessionIds`.

- [ ] **Step 3: Implement**

Create `PrSessions.kt`:

```kotlin
package de.simiil.liftlog.domain.analytics

import de.simiil.liftlog.domain.model.Equipment

/**
 * Ids of sessions containing ≥1 headline PR (04-analytics-spec §3 `isPr`), across all
 * exercises. Reuses [summarize] per exercise so Home/History chips agree with the Analytics
 * detail screen by construction. Exercises missing from [equipmentById] (e.g. soft-deleted)
 * are skipped — the detail screen has no summary for them either.
 */
fun prSessionIds(
    setsByExercise: Map<String, List<DatedSet>>,
    equipmentById: Map<String, Equipment>,
    nowMillis: Long,
): Set<String> = buildSet {
    for ((exerciseId, sets) in setsByExercise) {
        val equipment = equipmentById[exerciseId] ?: continue
        val summary = summarize(equipment, sets, nowMillis) ?: continue
        summary.sessions.forEach { if (it.isPr) add(it.sessionId) }
    }
}
```

- [ ] **Step 4: Run the test again**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.domain.analytics.PrSessionsTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/de/simiil/liftlog/domain/analytics/PrSessions.kt \
        app/src/test/kotlin/de/simiil/liftlog/domain/analytics/PrSessionsTest.kt
git commit -m "feat(analytics): pure prSessionIds() over all exercises

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: `AnalyticsRepository.observePrSessionIds()` (TDD)

**Files:**
- Test: `app/src/test/kotlin/de/simiil/liftlog/data/repository/AnalyticsRepositoryImplTest.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/domain/repository/AnalyticsRepository.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/repository/AnalyticsRepositoryImpl.kt`
- Modify (compile fixes): `app/src/test/kotlin/de/simiil/liftlog/ui/analytics/AnalyticsBrowserViewModelTest.kt:30-35`, `app/src/test/kotlin/de/simiil/liftlog/ui/analytics/ExerciseDetailViewModelTest.kt:44-50`

- [ ] **Step 1: Write the failing test**

Add to `AnalyticsRepositoryImplTest.kt` (after `exerciseSummary_assemblesFromRows`):

```kotlin
    @Test fun prSessionIds_unionsAcrossExercises() = runTest {
        val dao = FakeAnalyticsDao(
            allSets = listOf(
                SetRow("s1", "e1", nowMs - 20 * day, 100.0, 5),  // e1 first session — PR
                SetRow("s2", "e1", nowMs - 10 * day, 100.0, 5),  // tie — no flag from e1
                SetRow("s2", "e2", nowMs - 10 * day, 60.0, 5),   // e2 first session — flags s2
            ),
            trained = emptyList(), perExercise = emptyMap(),
        )
        val repo = AnalyticsRepositoryImpl(dao, FakeExerciseRepository(listOf(ex("e1"), ex("e2"))), clock)
        repo.observePrSessionIds().test {
            assertEquals(setOf("s1", "s2"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
```

- [ ] **Step 2: Run it — expect a compile failure**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.data.repository.AnalyticsRepositoryImplTest"`
Expected: FAIL — `unresolved reference: observePrSessionIds`.

- [ ] **Step 3: Add the interface method, a stub impl, and fix the two ad-hoc fakes**

In `AnalyticsRepository.kt`, add to the interface:

```kotlin
    /** Ids of sessions containing at least one headline PR, across all exercises. */
    fun observePrSessionIds(): Flow<Set<String>>
```

In `AnalyticsRepositoryImpl.kt`, add a deliberately-wrong stub (red phase):

```kotlin
    override fun observePrSessionIds(): Flow<Set<String>> = flowOf(emptySet())
```

with import `kotlinx.coroutines.flow.flowOf`.

Two test fakes implement the interface and now fail to compile — add one override to each.
`AnalyticsBrowserViewModelTest.kt`, inside `private class FakeRepo`:

```kotlin
        override fun observePrSessionIds(): Flow<Set<String>> = flowOf(emptySet())
```

`ExerciseDetailViewModelTest.kt`, inside the `object : AnalyticsRepository` in `vm(…)`:

```kotlin
            override fun observePrSessionIds() = flowOf(emptySet<String>())
```

(Both files already import `flowOf`.)

- [ ] **Step 4: Run the test — expect a real assertion failure**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.data.repository.AnalyticsRepositoryImplTest"`
Expected: FAIL — `prSessionIds_unionsAcrossExercises`: expected `[s1, s2]` but was `[]`. (The other three tests still pass.)

- [ ] **Step 5: Implement for real**

In `AnalyticsRepositoryImpl.kt`, replace the stub with:

```kotlin
    override fun observePrSessionIds(): Flow<Set<String>> =
        combine(
            analyticsDao.observeAllSetsSince(0L),
            exerciseRepository.observeAll(),
        ) { rows, exercises ->
            prSessionIds(
                setsByExercise = rows.groupBy(
                    { it.exerciseId },
                    { DatedSet(it.sessionId, it.startedAt, it.weightKg, it.reps) },
                ),
                equipmentById = exercises.associate { it.id to it.equipment },
                nowMillis = clock.millis(),
            )
        }.flowOn(Dispatchers.Default)
```

Imports to add (replace the now-unused `flowOf` import):

```kotlin
import de.simiil.liftlog.domain.analytics.prSessionIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
```

The `flowOn(Dispatchers.Default)` is load-bearing: without it the full-history scan runs in the collector's context, which is the main thread under `stateIn(viewModelScope)`.

- [ ] **Step 6: Run the repository tests**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.data.repository.AnalyticsRepositoryImplTest"`
Expected: PASS (4 tests).

- [ ] **Step 7: Run the full unit suite (catches the fake compile fixes)**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/de/simiil/liftlog/domain/repository/AnalyticsRepository.kt \
        app/src/main/kotlin/de/simiil/liftlog/data/repository/AnalyticsRepositoryImpl.kt \
        app/src/test/kotlin/de/simiil/liftlog/data/repository/AnalyticsRepositoryImplTest.kt \
        app/src/test/kotlin/de/simiil/liftlog/ui/analytics/AnalyticsBrowserViewModelTest.kt \
        app/src/test/kotlin/de/simiil/liftlog/ui/analytics/ExerciseDetailViewModelTest.kt
git commit -m "feat(analytics): observePrSessionIds() repository flow

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Home ViewModel carries the PR flag (TDD)

**Files:**
- Create: `app/src/test/kotlin/de/simiil/liftlog/testing/FakeAnalyticsRepository.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/ui/home/HomeViewModelTest.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/home/HomeViewModel.kt`

- [ ] **Step 1: Create the shared fake (mirrors `FakeExerciseRepository` style)**

Create `app/src/test/kotlin/de/simiil/liftlog/testing/FakeAnalyticsRepository.kt`:

```kotlin
package de.simiil.liftlog.testing

import de.simiil.liftlog.domain.analytics.ExerciseSummary
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.TrainedExercise
import de.simiil.liftlog.domain.repository.WeekSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAnalyticsRepository : AnalyticsRepository {

    val weekSummary = MutableStateFlow(WeekSummary(sessions = 0, sets = 0, volumeKg = 0.0, prevVolumeKg = 0.0))
    val trainedExercises = MutableStateFlow<List<TrainedExercise>>(emptyList())
    val exerciseSummaries = MutableStateFlow<Map<String, ExerciseSummary?>>(emptyMap())
    val prSessionIds = MutableStateFlow<Set<String>>(emptySet())

    override fun observeWeekSummary(): Flow<WeekSummary> = weekSummary
    override fun observeTrainedExercises(): Flow<List<TrainedExercise>> = trainedExercises
    override fun observeExerciseSummary(exerciseId: String): Flow<ExerciseSummary?> =
        exerciseSummaries.map { it[exerciseId] }
    override fun observePrSessionIds(): Flow<Set<String>> = prSessionIds
}
```

- [ ] **Step 2: Write the failing test**

In `HomeViewModelTest.kt`:

Extend `makeVm` (the single construction point) with the new dependency:

```kotlin
    private fun makeVm(
        sessionRepo: FakeSessionRepository = FakeSessionRepository(),
        planRepo: FakePlanRepository = FakePlanRepository(),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(),
        analyticsRepo: FakeAnalyticsRepository = FakeAnalyticsRepository(),
    ) = HomeViewModel(sessionRepo, planRepo, exerciseRepo, analyticsRepo)
```

Add imports:

```kotlin
import de.simiil.liftlog.testing.FakeAnalyticsRepository
import org.junit.Assert.assertFalse
```

Add the test:

```kotlin
    @Test
    fun `recent session carries PR flag from analytics`() = runTest {
        val sessionRepo = FakeSessionRepository()
        val analyticsRepo = FakeAnalyticsRepository()
        val ended = Instant.parse("2026-01-02T10:00:00Z")
        sessionRepo.history.value = listOf(
            makeSession("s-pr", endedAt = ended),
            makeSession("s-plain", endedAt = ended),
        )
        analyticsRepo.prSessionIds.value = setOf("s-pr")

        val vm = makeVm(sessionRepo = sessionRepo, analyticsRepo = analyticsRepo)

        vm.uiState.test {
            val byId = awaitItem().recent.associateBy { it.sessionId }
            assertTrue("s-pr should be flagged", byId.getValue("s-pr").isPr)
            assertFalse("s-plain should not be flagged", byId.getValue("s-plain").isPr)
            cancelAndIgnoreRemainingEvents()
        }
    }
```

- [ ] **Step 3: Run it — expect compile failure**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.ui.home.HomeViewModelTest"`
Expected: FAIL — `HomeViewModel` has no 4-arg constructor and `RecentSessionUi` has no `isPr`.

- [ ] **Step 4: Implement in `HomeViewModel.kt`**

Add the field to the UI model:

```kotlin
data class RecentSessionUi(
    val sessionId: String,
    val name: String?,
    val startedAt: Instant,
    val setCount: Int,
    val isPr: Boolean = false,
)
```

Add the dependency (after `exerciseRepository` to match `makeVm`'s positional args):

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val planRepository: PlanRepository,
    private val exerciseRepository: ExerciseRepository,
    private val analyticsRepository: AnalyticsRepository,
) : ViewModel() {
```

with import `de.simiil.liftlog.domain.repository.AnalyticsRepository`.

Chain the PR join between the existing 5-flow `combine` and `stateIn` (the existing `kotlinx.coroutines.flow.combine` import covers the chained extension too):

```kotlin
    }.combine(analyticsRepository.observePrSessionIds()) { state, prIds ->
        state.copy(recent = state.recent.map { it.copy(isPr = it.sessionId in prIds) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
```

(That is: the line `}.stateIn(viewModelScope, …)` becomes the three lines above. Nothing inside the original `combine` block changes.)

- [ ] **Step 5: Run the Home tests**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.ui.home.HomeViewModelTest"`
Expected: PASS (all existing tests + the new one).

- [ ] **Step 6: Commit**

```bash
git add app/src/test/kotlin/de/simiil/liftlog/testing/FakeAnalyticsRepository.kt \
        app/src/test/kotlin/de/simiil/liftlog/ui/home/HomeViewModelTest.kt \
        app/src/main/kotlin/de/simiil/liftlog/ui/home/HomeViewModel.kt
git commit -m "feat(home): PR flag on recent-session rows

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: History ViewModel carries the PR flag (TDD)

**Files:**
- Test: `app/src/test/kotlin/de/simiil/liftlog/ui/history/HistoryViewModelTest.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/history/HistoryViewModel.kt`

- [ ] **Step 1: Write the failing test**

In `HistoryViewModelTest.kt`:

Add a construction helper after the `makeSession` helper, and replace **all 7** existing `HistoryViewModel(repo)` call sites with `makeVm(repo)`:

```kotlin
    private fun makeVm(
        repo: FakeSessionRepository,
        analytics: FakeAnalyticsRepository = FakeAnalyticsRepository(),
    ) = HistoryViewModel(repo, analytics)
```

Add imports:

```kotlin
import de.simiil.liftlog.testing.FakeAnalyticsRepository
import org.junit.Assert.assertFalse
```

Add the test:

```kotlin
    @Test
    fun `session carries PR flag from analytics`() = runTest {
        val repo = FakeSessionRepository()
        val analytics = FakeAnalyticsRepository()
        repo.history.value = listOf(makeSession("s-pr"), makeSession("s-plain"))
        analytics.prSessionIds.value = setOf("s-pr")

        val vm = makeVm(repo, analytics)

        vm.uiState.test {
            val byId = awaitItem().sessions.associateBy { it.sessionId }
            assertTrue("s-pr should be flagged", byId.getValue("s-pr").isPr)
            assertFalse("s-plain should not be flagged", byId.getValue("s-plain").isPr)
            cancelAndIgnoreRemainingEvents()
        }
    }
```

- [ ] **Step 2: Run it — expect compile failure**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.ui.history.HistoryViewModelTest"`
Expected: FAIL — `HistoryViewModel` has no 2-arg constructor and `HistorySessionUi` has no `isPr`.

- [ ] **Step 3: Implement in `HistoryViewModel.kt`**

The full new file body (only the marked lines change):

```kotlin
data class HistorySessionUi(
    val sessionId: String,
    val name: String?,
    val startedAt: Instant,
    val setCount: Int,
    val isPr: Boolean = false,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val analyticsRepository: AnalyticsRepository,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        sessionRepository.observeHistory(),
        sessionRepository.observeSetCountsBySession(),
        analyticsRepository.observePrSessionIds(),
    ) { history, counts, prIds ->
        HistoryUiState(
            history.filter { it.endedAt != null }
                .map {
                    HistorySessionUi(
                        sessionId = it.id,
                        name = it.templateNameSnapshot,
                        startedAt = it.startedAt,
                        setCount = counts[it.id] ?: 0,
                        isPr = it.id in prIds,
                    )
                },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
```

with import `de.simiil.liftlog.domain.repository.AnalyticsRepository`.

- [ ] **Step 4: Run the History tests**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.ui.history.HistoryViewModelTest"`
Expected: PASS (all 7 existing tests + the new one).

- [ ] **Step 5: Commit**

```bash
git add app/src/test/kotlin/de/simiil/liftlog/ui/history/HistoryViewModelTest.kt \
        app/src/main/kotlin/de/simiil/liftlog/ui/history/HistoryViewModel.kt
git commit -m "feat(history): PR flag on session cards

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Shared `PrBadge` composable, wired into three screens

UI-only task; behavior is covered by the ViewModel tests above, rendering is verified by build + on-device smoke check.

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/ui/components/PrBadge.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/analytics/ExerciseDetailScreen.kt:205-212`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/home/HomeScreen.kt` (in `RecentSessionItem`, after the set-count `Text`, ~line 458)
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/history/HistoryScreen.kt:94-99`

- [ ] **Step 1: Create the component**

```kotlin
package de.simiil.liftlog.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.simiil.liftlog.R

/**
 * Shared "PR" marker (bold 12sp `tertiary`) — used on Analytics detail history rows, Home
 * recent-workout rows, and History session cards. Tertiary is the app-wide PR accent
 * (matches the PR dot in ProgressLineChart).
 */
@Composable
fun PrBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.analytics_pr),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = modifier,
    )
}
```

- [ ] **Step 2: Reuse it in `ExerciseDetailScreen.kt`**

Replace:

```kotlin
        if (row.isPr) {
            Text(
                stringResource(R.string.analytics_pr),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
```

with:

```kotlin
        if (row.isPr) {
            PrBadge()
        }
```

Add import `de.simiil.liftlog.ui.components.PrBadge`. Remove any imports the compiler now flags as unused (`FontWeight`/`sp` are likely still used elsewhere in this file — only remove what's actually unused).

- [ ] **Step 3: Add to `HomeScreen.kt`**

In `RecentSessionItem`, immediately after the set-count `Text` (the third `Text` in the `Row`), add:

```kotlin
            if (session.isPr) {
                Spacer(Modifier.size(12.dp))
                PrBadge()
            }
```

Add import `de.simiil.liftlog.ui.components.PrBadge` (`Spacer`/`size` are already imported).

- [ ] **Step 4: Add to `HistoryScreen.kt`**

In `HistorySessionCard`, replace the title `Text`:

```kotlin
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = session.name ?: stringResource(R.string.session_untitled),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
```

with a title row carrying the trailing badge:

```kotlin
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.name ?: stringResource(R.string.session_untitled),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (session.isPr) {
                    PrBadge()
                }
            }
```

Add imports:

```kotlin
import androidx.compose.foundation.layout.Row
import de.simiil.liftlog.ui.components.PrBadge
```

(`Alignment` is already imported.)

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: On-device smoke check (emulator `emulator-5554` if up; otherwise skip — CI Compose tests still gate)**

```bash
./gradlew installDebug
adb -s emulator-5554 shell am start -n de.simiil.liftlog/.MainActivity
adb -s emulator-5554 exec-out screencap -p > /tmp/home-pr-chip.png
```

Read `/tmp/home-pr-chip.png` and confirm recent-workout rows render, with a small "PR" in the accent color on record-setting sessions. If the device has no workout data (or none with a PR), first log two quick workouts for the same exercise with increasing weight via the app UI (`adb shell input tap`, resolving bounds via `uiautomator dump`) — the second one must show the chip. Then navigate to History and screencap again to confirm the card's title row shows the trailing badge.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/de/simiil/liftlog/ui/components/PrBadge.kt \
        app/src/main/kotlin/de/simiil/liftlog/ui/analytics/ExerciseDetailScreen.kt \
        app/src/main/kotlin/de/simiil/liftlog/ui/home/HomeScreen.kt \
        app/src/main/kotlin/de/simiil/liftlog/ui/history/HistoryScreen.kt
git commit -m "feat(ui): shared PrBadge on Home, History, and analytics detail

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Final verification (CI parity)

- [ ] **Step 1: Run exactly what CI runs**

Run: `./gradlew lint testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, no new lint errors.

- [ ] **Step 2: Scoped instrumented re-run (if a device is attached)**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.data.dao.AnalyticsDaoTest`
Expected: PASS.

- [ ] **Step 3: Confirm clean tree**

Run: `git status --short`
Expected: empty (every task committed its own files).
