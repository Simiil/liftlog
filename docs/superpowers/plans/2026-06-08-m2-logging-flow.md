# M2 — Logging Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A complete, fast, kill-proof training diary — Home, the Active Session screen, the shared Exercise Picker, and History (list + read/edit detail) — usable for real training from this milestone on (05-roadmap M2).

**Architecture:** MVVM + UDF on the M1 data layer. One ViewModel per screen exposing an immutable `StateFlow<XyzUiState>`; ViewModels depend only on `domain/repository` interfaces (never DAOs/`Context`); reads are `Flow`, writes are `suspend`; every logged set persists to Room immediately (process-death safety comes from Room, not `SavedStateHandle`). Stateless, previewable composables (01-architecture §1, §3).

**Tech Stack:** Jetpack Compose + Material 3, Navigation Compose (type-safe `@Serializable` routes), Hilt (`hiltViewModel()`), Coroutines/Flow, the M1 Room layer. Tests: JUnit + Turbine + coroutines-test (JVM, local) for ViewModels/repos/pure logic; instrumented Room tests (CI) for new DAO queries; one deep Compose UI test (CI) for the critical logging + process-death-resume path.

---

## The shape of M2 (read before starting)

M2 builds **on top of** the M1 data layer. The hard constraint that shaped M1 still applies: **no local emulator/KVM**, so anything instrumented (new DAO queries, the Compose UI test) is verified locally only by *compiling* (`./gradlew assembleDebugAndroidTest`) and proven by CI. Push as much behavior as possible into plain-JVM-testable layers (pure functions, ViewModels with fake repositories, repository orchestration with fake DAOs).

What M1 already gives us (the contract — do not re-derive, read the real files):
- `SessionRepository`: `observeActiveSession()`, `observeHistory()`, `observeSessionDetails(id)`, `startEmptySession()`, `finishSession(id)`, `softDeleteSession(id)`.
- `ExerciseRepository`: `observeAll()`, `observeVisible()`, `createCustom(name, muscleGroup, equipment)`, `setHidden(id, hidden)`.
- `SettingsRepository`: `themePreference: Flow<ThemePreference>`, `setThemePreference(...)`.
- Domain models (`Instant` timestamps): `Exercise`, `Session`, `SessionExercise`, `LoggedSet`, `SessionWithDetails(session, exercises: List<SessionExerciseWithSets(sessionExercise, sets)>)`, enums `MuscleGroup`, `Equipment`, `ThemePreference`.
- Conventions baked into repos: UUID PKs (`UUID.randomUUID().toString()`), injected `java.time.Clock` (`clock.millis()`), `Transactor.immediate { }` for atomic cascades, soft-delete only, `updatedAt` on every write.
- DAOs: `SessionDao` (insert/update/find session, `insertSessionExercise`, `insertLoggedSet`, cascade soft-deletes, `activeSessionId()`, `observeSessionWithDetails` relation), `PrefillDao` (`lastCompletedSessionIdFor(exerciseId)`, `setsForExerciseInSession(sessionId, exerciseId)` — building blocks only), `ExerciseDao`, `AnalyticsDao`.
- UI scaffold: `LiftLogApp` (outer `Scaffold` + bottom bar shown **only on top-level tabs** — new full-screen destinations automatically hide it), `LiftLogNavHost` (type-safe `composable<Route>`), `Destinations.kt` (`@Serializable data object` routes), `PlaceholderScreen`, `LiftLogTheme`, `hiltViewModel()` available (hilt-navigation-compose 1.2.0).
- Test infra: JVM fakes at `app/src/test/kotlin/de/simiil/liftlog/testing/fakes/` (`FakeSessionDao`, `FakeExerciseDao`, `FakePlanDao`, `FakeTransactor`, a `FixedClock` helper); instrumented `DbRule` + `AppDatabase.tombstoneOf(table, id)` raw-cursor helper.

What M2 must add to the data layer (gaps): logging write methods on `SessionRepository` (`logSet`, `addExerciseToSession`, `updateSet`, `deleteSet`, `removeExercise`, `replaceExercise`, `lastPerformance`) + the `SessionDao` queries they need; a units conversion layer; the pre-fill selection function; a "recently used exercises" query; and a per-session set-count query.

Package root: `de.simiil.liftlog`. UI base: `app/src/main/kotlin/de/simiil/liftlog/ui/`. JVM tests: `app/src/test/kotlin/de/simiil/liftlog/`. Instrumented: `app/src/androidTest/kotlin/de/simiil/liftlog/`.

---

## Decisions flagged for owner review (milestone gate)

Reasoned defaults where the docs leave room; flagged per CLAUDE.md rather than blocking. Reject any at the gate and the change is localized.

1. **Units: build + test the kg↔lb layer, observe-only in M2 (default KG); the kg/lb toggle UI + setter ship in M5.** Per owner answer (M2 kickoff). `SettingsRepository` gains a read-only `weightUnit: Flow<WeightUnit>` (DataStore key, default `KG`); all logging UI renders via the conversion layer, so M5 only adds a setter + one Settings row. The conversion/format functions are fully unit-tested now (pure logic is cheap to lock down) even though the UI path is KG-only until M5.
2. **`lastPerformance(exerciseId)` is a `suspend` snapshot, not a `Flow`.** 02-data-spec §4 sketches `observeLastPerformance(): Flow`; M1 flagged composing it at M2. The "last completed session for an exercise" is immutable during an active session (you can't complete another session while one is live), so a one-shot fetch when a card becomes active is correct and avoids a synthetic reactive query. The live "previous set of *this* entry" pre-fill source comes from the already-reactive `observeSessionDetails` flow.
3. **`replaceExercise` = remove-old (cascade its sets) + insert-new at the same position, atomically.** The `⋮` menu's "replace" (03-ux-spec §4.1) means "I picked the wrong slot." Keeping logged sets attached to a different exercise would corrupt history, so replace discards the old entry's sets. Acceptable for "wrong exercise"; flagged.
4. **Exercise name/equipment for session cards is joined in the ViewModel**, by combining `observeSessionDetails(id)` with `observeAll()` (an `id → Exercise` map). No new relation/DAO — `SessionWithDetails` stays as M1 shipped it.
5. **History cards show set count (new DAO query), not PR count.** PR detection is analytics (M4); the history card's "PR count" lands when M4 exists. Flagged so the omission is deliberate.
6. **"Recent on top" in the picker** uses a new `MAX(completedAt)`-grouped DAO query (instrumented-tested).
7. **The picker returns its result via `NavBackStackEntry.savedStateHandle`** (the idiomatic Navigation-Compose result pattern). Creating a custom exercise inline returns the new exercise as the selection. The picker is shared (Plans reuses it at M3), so it returns a result rather than mutating a session directly.
8. **Visual fidelity = M3 baseline.** The 06 design-handoff commissions hi-fi tokens "needed by M2," but no delivered tokens exist in the repo yet. M2 implements the 03-ux-spec layouts + 06 §3 hard constraints (M3 color **roles only**, no hardcoded hex; ≥56dp logging-path targets, ≥48dp elsewhere; dark-first via existing theme; dynamic type to 200%; trend/status by glyph+color; reserved rest-timer slot stays visually empty). Components are structured so design tokens drop into the theme/components later without touching call sites.
9. **Target-driven auto-advance is implemented but dormant in M2.** "Logging the target set count auto-collapses and advances" (03-ux-spec §4.1) only fires when `targetSets != null`; all M2 session exercises are ad-hoc (added via the picker → null targets). Templates arrive at M3, which lights this up. The logic ships now (and is unit-tested with a synthetic target) so M3 needs no VM change.
10. **Empty (ad-hoc) sessions display as "Quick workout"** (`templateNameSnapshot` is null). New string `session_untitled`.
11. **Process-death is tested via activity `recreate()` + real Room persistence.** True process kill isn't directly drivable in instrumentation; `recreate()` on a Hilt activity, with state re-hydrated from Room (not `SavedStateHandle`), is the faithful proxy and exactly exercises the "Room is the source of truth" guarantee.

---

## File structure (created/modified across all tasks)

```
gradle/libs.versions.toml                         (M) hilt-android-testing, compose-ui-test deps
app/build.gradle.kts                              (M) androidTest Hilt + compose-ui-test, custom test runner

app/src/main/kotlin/de/simiil/liftlog/
  domain/
    model/WeightUnit.kt                           (C) enum + fromStorageValue
    units/Weights.kt                              (C) kg<->display conversion, formatting, step increments
    logging/Prefill.kt                            (C) pure pre-fill selection (03-ux-spec §4.2)
    repository/
      SessionRepository.kt                        (M) logging write methods + lastPerformance
      ExerciseRepository.kt                        (M) observeRecentlyUsedIds
      SettingsRepository.kt                        (M) weightUnit read-side
  data/
    dao/SessionDao.kt                             (M) max-position, single soft-deletes, single-exercise cascade
    dao/ExerciseDao.kt                            (M) recently-used-exercise-ids query
    dao/AnalyticsDao.kt (or SessionDao)           (M) set-counts-by-session query  (+ SetCount projection in Relations.kt)
    repository/
      SessionRepositoryImpl.kt                    (M) implement new methods (+ PrefillDao injection)
      ExerciseRepositoryImpl.kt                   (M) recently-used
      SettingsRepositoryImpl.kt                   (M) weightUnit key
  ui/
    components/
      WeightStepper.kt RepsStepper.kt             (C) ≥56dp steppers
      InlineNumpad.kt                             (C) 4x3 pad + quick chips
      LoggedSetRow.kt                             (C) logged set row + long-press edit (shared active/detail)
    navigation/Destinations.kt                    (M) ActiveSession/SessionDetail/ExercisePicker routes + result key
    navigation/LiftLogNavHost.kt                  (M) register new destinations + picker-result plumbing
    home/HomeScreen.kt HomeViewModel.kt           (C) real Home (resume + empty start + recent)
    session/
      ActiveSessionScreen.kt ActiveSessionViewModel.kt  (C) the make-or-break screen
      ExerciseCard.kt                             (C) collapsed/active/upcoming card composables
      SessionDetailScreen.kt SessionDetailViewModel.kt  (C) read + edit
    exercises/
      ExercisePickerScreen.kt ExercisePickerViewModel.kt (C) shared picker + create-custom
    history/HistoryScreen.kt HistoryViewModel.kt  (M/C) real History list
  res/values/strings.xml                          (M) all new strings

app/src/test/kotlin/de/simiil/liftlog/            (JVM, local)
  domain/units/WeightsTest.kt
  domain/logging/PrefillTest.kt
  data/repository/SessionRepositoryTest.kt        (M) new-method cases
  data/repository/ExerciseRepositoryTest.kt       (M) recently-used
  data/repository/SettingsRepositoryTest.kt       (M/C) weightUnit default
  ui/home/HomeViewModelTest.kt
  ui/session/ActiveSessionViewModelTest.kt
  ui/exercises/ExercisePickerViewModelTest.kt
  ui/history/HistoryViewModelTest.kt
  ui/session/SessionDetailViewModelTest.kt
  testing/fakes/FakeSessionDao.kt                 (M) new methods
  testing/fakes/FakePrefillDao.kt                 (C)
  testing/fakes/Fake*Repository.kt                (C) for ViewModel tests

app/src/androidTest/kotlin/de/simiil/liftlog/     (instrumented, CI)
  data/dao/SessionLoggingDaoTest.kt               (C) max-position, single soft-deletes, single-exercise cascade
  data/dao/PickerQueriesDaoTest.kt                (C) recently-used + set-counts
  HiltTestRunner.kt + di/TestDatabaseModule.kt    (C) Hilt instrumented harness (in-memory DB)
  ui/CriticalLoggingPathTest.kt                   (C) start -> prefill -> adjust -> log -> recreate -> resume
```

---

# PHASE 1 — Data + domain foundation (no UI)

## Task 1: Units — WeightUnit + conversion/format (pure)

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/model/WeightUnit.kt`
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/units/Weights.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/domain/units/WeightsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package de.simiil.liftlog.domain.units

import de.simiil.liftlog.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightsTest {
    @Test fun kg_displaysWithoutConversion() =
        assertEquals("82.5", Weights.format(82.5, WeightUnit.KG))

    @Test fun kg_stripsTrailingZeros() {
        assertEquals("30", Weights.format(30.0, WeightUnit.KG))
        assertEquals("27.5", Weights.format(27.5, WeightUnit.KG))
    }

    @Test fun lb_convertsAndDisplaysCleanly() {
        // 60 lb stored as 27.2155422 kg must re-display as "60"
        val kg = Weights.displayToKg(60.0, WeightUnit.LB)
        assertEquals(27.2155422, kg, 1e-7)
        assertEquals("60", Weights.format(kg, WeightUnit.LB))
    }

    @Test fun roundTripIsStable() {
        val kg = Weights.displayToKg(42.5, WeightUnit.LB)
        assertEquals(42.5, Weights.kgToDisplay(kg, WeightUnit.LB), 1e-9)
    }

    @Test fun stepIncrementIsPerUnit() {
        assertEquals(2.5, Weights.stepIncrementDisplay(WeightUnit.KG), 0.0)
        assertEquals(5.0, Weights.stepIncrementDisplay(WeightUnit.LB), 0.0)
    }

    @Test fun unitLabel() {
        assertEquals("kg", Weights.label(WeightUnit.KG))
        assertEquals("lb", Weights.label(WeightUnit.LB))
    }

    @Test fun fromStorageValue_fallsBackToKg() {
        assertEquals(WeightUnit.KG, WeightUnit.fromStorageValue(null))
        assertEquals(WeightUnit.KG, WeightUnit.fromStorageValue("STONE"))
        assertEquals(WeightUnit.LB, WeightUnit.fromStorageValue("LB"))
    }
}
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew testDebugUnitTest --tests "*WeightsTest"` → unresolved references.

- [ ] **Step 3: Implement**

```kotlin
// WeightUnit.kt
package de.simiil.liftlog.domain.model

/** Display/entry unit. Storage is always kg (02-data-spec §5). */
enum class WeightUnit {
    KG, LB;

    companion object {
        fun fromStorageValue(value: String?): WeightUnit =
            entries.firstOrNull { it.name == value } ?: KG
    }
}
```

```kotlin
// Weights.kt
package de.simiil.liftlog.domain.units

import de.simiil.liftlog.domain.model.WeightUnit
import java.math.BigDecimal
import java.math.RoundingMode

/** kg<->display conversion + formatting (02-data-spec §5). Pure; no Android deps. */
object Weights {
    /** Exact factor: 1 lb = 0.45359237 kg. */
    const val KG_PER_LB: Double = 0.45359237

    fun kgToDisplay(weightKg: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> weightKg
        WeightUnit.LB -> weightKg / KG_PER_LB
    }

    fun displayToKg(value: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> value
        WeightUnit.LB -> value * KG_PER_LB
    }

    /** Stepper increment expressed in the display unit (03-ux-spec §4.3): 2.5 kg / 5 lb. */
    fun stepIncrementDisplay(unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> 2.5
        WeightUnit.LB -> 5.0
    }

    fun label(unit: WeightUnit): String = when (unit) {
        WeightUnit.KG -> "kg"
        WeightUnit.LB -> "lb"
    }

    /** Display value rounded to <=2 decimals, trailing zeros stripped ("82.5", "30", "27.22"). */
    fun format(weightKg: Double, unit: WeightUnit): String =
        BigDecimal(kgToDisplay(weightKg, unit))
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
}
```

- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `M2: units — WeightUnit + kg/lb conversion & formatting`

---

## Task 2: SettingsRepository — weightUnit read-side

**Files:**
- Modify: `domain/repository/SettingsRepository.kt`
- Modify: `data/repository/SettingsRepositoryImpl.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/data/repository/SettingsRepositoryTest.kt` (create if absent; otherwise extend)

Read `SettingsRepositoryImpl.kt` first to mirror its exact DataStore-key + `fromStorageValue` pattern used for theme.

- [ ] **Step 1: Add to the interface**

```kotlin
// SettingsRepository.kt — add alongside themePreference
val weightUnit: Flow<WeightUnit>
// NOTE: setWeightUnit + the Settings toggle UI are deferred to M5 (flagged decision #1).
```

- [ ] **Step 2: Write the failing test** (use the same fake/in-memory DataStore approach the theme test uses; if the existing theme test constructs a real `DataStore` over a temp file, copy that harness)

```kotlin
@Test fun weightUnit_defaultsToKg_whenUnset() = runTest {
    val repo = SettingsRepositoryImpl(emptyDataStore())
    assertEquals(WeightUnit.KG, repo.weightUnit.first())
}
```

- [ ] **Step 3: Implement in `SettingsRepositoryImpl`**

```kotlin
private object Keys {
    // ...existing theme key...
    val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
}

override val weightUnit: Flow<WeightUnit> =
    dataStore.data.map { WeightUnit.fromStorageValue(it[Keys.WEIGHT_UNIT]) }
```

- [ ] **Step 4: Run, expect PASS** — `./gradlew testDebugUnitTest --tests "*SettingsRepositoryTest"`.
- [ ] **Step 5: Commit** — `M2: SettingsRepository.weightUnit (read-side, default KG)`

---

## Task 3: Pre-fill selection logic (pure)

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/logging/Prefill.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/domain/logging/PrefillTest.kt`

Implements 03-ux-spec §4.2 priority order. Pure function over domain `LoggedSet`s.

- [ ] **Step 1: Write the failing test**

```kotlin
package de.simiil.liftlog.domain.logging

import de.simiil.liftlog.domain.model.LoggedSet
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

private fun set(weightKg: Double, reps: Int, position: Int) = LoggedSet(
    id = "s$position", sessionExerciseId = "se", weightKg = weightKg, reps = reps,
    position = position, completedAt = Instant.EPOCH, rpe = null, note = null,
    createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH, deletedAt = null,
)

class PrefillTest {
    @Test fun rule1_previousSetOfThisEntry_wins() {
        val result = Prefill.forNextSet(
            setsThisEntry = listOf(set(30.0, 10, 1), set(32.5, 8, 2)),
            lastPerformance = listOf(set(20.0, 12, 1)), // ignored — rule 1 takes priority
        )
        assertEquals(32.5, result.weightKg!!, 0.0)
        assertEquals(8, result.reps)
    }

    @Test fun rule2_firstSetFromLastSession_whenEntryEmpty() {
        val result = Prefill.forNextSet(
            setsThisEntry = emptyList(),
            lastPerformance = listOf(set(30.0, 10, 1), set(30.0, 10, 2), set(30.0, 8, 3)),
        )
        assertEquals(30.0, result.weightKg!!, 0.0)
        assertEquals(10, result.reps)
    }

    @Test fun rule3_neverPerformed_emptyWeightReps10() {
        val result = Prefill.forNextSet(setsThisEntry = emptyList(), lastPerformance = emptyList())
        assertEquals(null, result.weightKg)
        assertEquals(10, result.reps)
    }
}
```

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: Implement**

```kotlin
package de.simiil.liftlog.domain.logging

import de.simiil.liftlog.domain.model.LoggedSet

data class PrefillValues(val weightKg: Double?, val reps: Int)

/** Pre-fill rules for the next set's steppers (03-ux-spec §4.2). Pure. */
object Prefill {
    const val DEFAULT_REPS = 10

    fun forNextSet(
        setsThisEntry: List<LoggedSet>,
        lastPerformance: List<LoggedSet>,
    ): PrefillValues {
        // 1. Previous set of THIS session-exercise entry (keeps duplicate entries independent).
        setsThisEntry.lastOrNull()?.let { return PrefillValues(it.weightKg, it.reps) }
        // 2. Same set-number from the last completed session; clamp to its final set if we've
        //    already exceeded it (defensive — rule 1 covers the in-session continuation case).
        if (lastPerformance.isNotEmpty()) {
            val index = minOf(setsThisEntry.size, lastPerformance.lastIndex)
            val src = lastPerformance[index]
            return PrefillValues(src.weightKg, src.reps)
        }
        // 3. Never performed: empty weight (numpad must be used), reps default 10.
        return PrefillValues(weightKg = null, reps = DEFAULT_REPS)
    }
}
```

- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `M2: pre-fill selection logic (03-ux-spec §4.2)`

---

## Task 4: SessionDao logging queries + instrumented tests

**Files:**
- Modify: `data/dao/SessionDao.kt`
- Test: `app/src/androidTest/kotlin/de/simiil/liftlog/data/dao/SessionLoggingDaoTest.kt`

Read the existing `SessionDao.kt` and an existing `*DaoTest.kt` + `DbRule.kt` (incl. `tombstoneOf`) first to match style.

- [ ] **Step 1: Add the new DAO methods**

```kotlin
// Append to SessionDao (insertSessionExercise/insertLoggedSet/findSession/updateSession already exist):

@Query("SELECT MAX(position) FROM session_exercises WHERE sessionId = :sessionId AND deletedAt IS NULL")
suspend fun maxExercisePosition(sessionId: String): Int?

@Query("SELECT MAX(position) FROM logged_sets WHERE sessionExerciseId = :sessionExerciseId AND deletedAt IS NULL")
suspend fun maxSetPosition(sessionExerciseId: String): Int?

@Query("SELECT * FROM session_exercises WHERE id = :id AND deletedAt IS NULL")
suspend fun findSessionExercise(id: String): SessionExerciseEntity?

@Query("SELECT * FROM logged_sets WHERE id = :id AND deletedAt IS NULL")
suspend fun findLoggedSet(id: String): LoggedSetEntity?

@Update suspend fun updateLoggedSet(set: LoggedSetEntity)

@Query("UPDATE logged_sets SET deletedAt = :now, updatedAt = :now WHERE id = :id")
suspend fun softDeleteLoggedSet(id: String, now: Long)

@Query("UPDATE session_exercises SET deletedAt = :now, updatedAt = :now WHERE id = :id")
suspend fun softDeleteSessionExercise(id: String, now: Long)

@Query("""UPDATE logged_sets SET deletedAt = :now, updatedAt = :now
          WHERE sessionExerciseId = :sessionExerciseId AND deletedAt IS NULL""")
suspend fun softDeleteLoggedSetsForSessionExercise(sessionExerciseId: String, now: Long)
```

- [ ] **Step 2: Write instrumented tests** (`@RunWith(AndroidJUnit4::class)`, use `DbRule` + `tombstoneOf` for soft-delete assertions). Cover, each as its own `@Test`:
  - `maxSetPosition` returns null with no sets, max live position otherwise (insert positions out of order — `2,1,3` — so MAX is load-bearing).
  - `maxExercisePosition` analogous.
  - `maxSetPosition` ignores soft-deleted sets (insert 3, soft-delete the position-3 one, expect 2).
  - `softDeleteLoggedSet` sets both `deletedAt` and `updatedAt` (read back via `tombstoneOf`) and leaves siblings untouched.
  - `softDeleteSessionExercise` + `softDeleteLoggedSetsForSessionExercise` together tombstone the exercise and its sets, and DO NOT touch a *sibling* exercise's sets in the same session.

  Sketch for the discriminating cascade test:

```kotlin
@Test fun softDeletingOneExercise_doesNotTouchSiblingsSets() = runTest {
    // session S; exercise A (set a1), exercise B (set b1)
    // ...insert via dao...
    val now = 999L
    dao.softDeleteLoggedSetsForSessionExercise(seA, now)
    dao.softDeleteSessionExercise(seA, now)

    assertEquals(now, db.tombstoneOf("logged_sets", "a1")) // tombstoned
    assertEquals(null, db.tombstoneOf("logged_sets", "b1")) // sibling untouched
    assertEquals(now, db.tombstoneOf("session_exercises", seA))
    assertEquals(null, db.tombstoneOf("session_exercises", seB))
}
```

- [ ] **Step 3: Compile the instrumented source locally** — `./gradlew assembleDebugAndroidTest` (no local emulator; semantics proven in CI). Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit** — `M2: SessionDao logging queries (positions, single soft-deletes, per-exercise cascade) + instrumented tests`

---

## Task 5: SessionRepository logging methods

**Files:**
- Modify: `domain/repository/SessionRepository.kt`
- Modify: `data/repository/SessionRepositoryImpl.kt`
- Modify: `app/src/test/kotlin/de/simiil/liftlog/testing/fakes/FakeSessionDao.kt`
- Create: `app/src/test/kotlin/de/simiil/liftlog/testing/fakes/FakePrefillDao.kt`
- Modify: `app/src/test/kotlin/de/simiil/liftlog/data/repository/SessionRepositoryTest.kt`

Read `SessionRepositoryImpl.kt`, `FakeSessionDao.kt`, and the existing `SessionRepositoryTest.kt` first. `SessionRepositoryImpl` must gain a constructor-injected `PrefillDao` (already `@Provides`-ed in `DatabaseModule`).

- [ ] **Step 1: Add to the interface**

```kotlin
// SessionRepository.kt — add:
suspend fun addExerciseToSession(sessionId: String, exerciseId: String): SessionExercise
suspend fun logSet(sessionExerciseId: String, weightKg: Double, reps: Int): LoggedSet
suspend fun updateSet(setId: String, weightKg: Double, reps: Int, rpe: Double?, note: String?)
suspend fun deleteSet(setId: String)                                   // soft
suspend fun removeExercise(sessionExerciseId: String)                  // soft + cascade its sets
suspend fun replaceExercise(sessionExerciseId: String, newExerciseId: String): SessionExercise
suspend fun lastPerformance(exerciseId: String): List<LoggedSet>       // snapshot (flagged #2)
// finishSession already exists — add an "already ended" guard (M1 forward note).
```

- [ ] **Step 2: Write the failing JVM tests** (extend `SessionRepositoryTest`, fakes inline-run the transactor). Cover:
  - `logSet` assigns position `(maxSetPosition ?: 0) + 1`, `completedAt`/`createdAt`/`updatedAt` = `clock.millis()`, a fresh UUID, returns the domain `LoggedSet`.
  - `logSet` rejects `weightKg < 0` and `reps < 1` (`IllegalArgumentException`).
  - `addExerciseToSession` assigns position `(maxExercisePosition ?: 0) + 1`, null targets, fresh UUID.
  - `updateSet` writes new weight/reps/rpe/note + `updatedAt`, preserves `position`/`completedAt`/`createdAt`.
  - `deleteSet` soft-deletes (sets `deletedAt`), `removeExercise` soft-deletes the exercise AND its sets atomically (assert via fake state).
  - `replaceExercise` tombstones the old entry (+ its sets) and inserts a new entry at the **same position** with the new `exerciseId`.
  - `finishSession` is a no-op when `endedAt` is already set (guard); sets `endedAt` when live.
  - `lastPerformance` returns `emptyList()` when `lastCompletedSessionIdFor` is null, else maps the building-block sets to domain.

- [ ] **Step 3: Implement in `SessionRepositoryImpl`** (mirror existing UUID/clock/transactor/mapper conventions)

```kotlin
override suspend fun addExerciseToSession(sessionId: String, exerciseId: String): SessionExercise {
    val now = clock.millis()
    val entity = SessionExerciseEntity(
        id = UUID.randomUUID().toString(),
        sessionId = sessionId,
        exerciseId = exerciseId,
        position = (dao.maxExercisePosition(sessionId) ?: 0) + 1,
        targetSets = null, targetRepsMin = null, targetRepsMax = null,
        createdAt = now, updatedAt = now, deletedAt = null,
    )
    dao.insertSessionExercise(entity)
    return entity.toDomain()
}

override suspend fun logSet(sessionExerciseId: String, weightKg: Double, reps: Int): LoggedSet {
    require(weightKg >= 0.0) { "weightKg must be >= 0" }
    require(reps >= 1) { "reps must be >= 1" }
    val now = clock.millis()
    val entity = LoggedSetEntity(
        id = UUID.randomUUID().toString(),
        sessionExerciseId = sessionExerciseId,
        weightKg = weightKg, reps = reps,
        position = (dao.maxSetPosition(sessionExerciseId) ?: 0) + 1,
        completedAt = now, rpe = null, note = null,
        createdAt = now, updatedAt = now, deletedAt = null,
    )
    dao.insertLoggedSet(entity)
    return entity.toDomain()
}

override suspend fun updateSet(setId: String, weightKg: Double, reps: Int, rpe: Double?, note: String?) {
    require(weightKg >= 0.0) { "weightKg must be >= 0" }
    require(reps >= 1) { "reps must be >= 1" }
    val existing = dao.findLoggedSet(setId) ?: return
    dao.updateLoggedSet(existing.copy(
        weightKg = weightKg, reps = reps, rpe = rpe, note = note, updatedAt = clock.millis(),
    ))
}

override suspend fun deleteSet(setId: String) {
    dao.softDeleteLoggedSet(setId, clock.millis())
}

override suspend fun removeExercise(sessionExerciseId: String) {
    val now = clock.millis()
    transactor.immediate {
        dao.softDeleteLoggedSetsForSessionExercise(sessionExerciseId, now)
        dao.softDeleteSessionExercise(sessionExerciseId, now)
    }
}

override suspend fun replaceExercise(sessionExerciseId: String, newExerciseId: String): SessionExercise {
    val now = clock.millis()
    return transactor.immediate {
        val old = dao.findSessionExercise(sessionExerciseId)
            ?: error("session exercise not found: $sessionExerciseId")
        dao.softDeleteLoggedSetsForSessionExercise(sessionExerciseId, now)
        dao.softDeleteSessionExercise(sessionExerciseId, now)
        val replacement = SessionExerciseEntity(
            id = UUID.randomUUID().toString(),
            sessionId = old.sessionId, exerciseId = newExerciseId, position = old.position,
            targetSets = null, targetRepsMin = null, targetRepsMax = null,
            createdAt = now, updatedAt = now, deletedAt = null,
        )
        dao.insertSessionExercise(replacement)
        replacement.toDomain()
    }
}

override suspend fun finishSession(id: String) {
    val session = dao.findSession(id) ?: return
    if (session.endedAt != null) return  // already-ended guard
    val now = clock.millis()
    dao.updateSession(session.copy(endedAt = now, updatedAt = now))
}

override suspend fun lastPerformance(exerciseId: String): List<LoggedSet> {
    val sessionId = prefillDao.lastCompletedSessionIdFor(exerciseId) ?: return emptyList()
    return prefillDao.setsForExerciseInSession(sessionId, exerciseId).map { it.toDomain() }
}
```

- [ ] **Step 4: Add the matching methods to `FakeSessionDao` and create `FakePrefillDao`.** The fakes back an in-memory `MutableList<Entity>`; `maxSetPosition`/`maxExercisePosition` filter `deletedAt == null` and return `maxOfOrNull { it.position }`. Verify each fake method against the real `@Query` SQL (do not let fakes drift — M1 review lesson).

- [ ] **Step 5: Run, expect PASS** — `./gradlew testDebugUnitTest --tests "*SessionRepositoryTest"`.
- [ ] **Step 6: Commit** — `M2: SessionRepository logging methods (log/add/update/delete/remove/replace/lastPerformance) + finish guard`

---

## Task 6: Picker recency + history set-count queries

**Files:**
- Modify: `data/dao/ExerciseDao.kt` (recently-used ids)
- Modify: `data/dao/AnalyticsDao.kt` + `data/dao/Relations.kt` (set-counts projection)
- Modify: `domain/repository/ExerciseRepository.kt` + impl (recently-used)
- Modify: `domain/repository/SessionRepository.kt` + impl (set-counts; reuses existing SessionDao access)
- Test: `app/src/androidTest/kotlin/de/simiil/liftlog/data/dao/PickerQueriesDaoTest.kt`
- Test (JVM): extend `ExerciseRepositoryTest` for the recently-used mapping (fake DAO).

- [ ] **Step 1: Add the DAO queries**

```kotlin
// ExerciseDao.kt — most-recently-used exercise ids (by latest logged set), live data only.
@Query("""
    SELECT se.exerciseId AS exerciseId, MAX(ls.completedAt) AS lastUsed
    FROM session_exercises se
    JOIN logged_sets ls ON ls.sessionExerciseId = se.id AND ls.deletedAt IS NULL
    JOIN sessions s     ON s.id = se.sessionId           AND s.deletedAt IS NULL
    WHERE se.deletedAt IS NULL
    GROUP BY se.exerciseId
    ORDER BY lastUsed DESC
""")
fun observeRecentlyUsedExerciseIds(): Flow<List<RecentExercise>>
```

```kotlin
// Relations.kt — projections
data class RecentExercise(val exerciseId: String, val lastUsed: Long)
data class SessionSetCount(val sessionId: String, val setCount: Int)
```

```kotlin
// AnalyticsDao.kt (or SessionDao) — per-session live set counts.
@Query("""
    SELECT se.sessionId AS sessionId, COUNT(ls.id) AS setCount
    FROM session_exercises se
    JOIN logged_sets ls ON ls.sessionExerciseId = se.id AND ls.deletedAt IS NULL
    WHERE se.deletedAt IS NULL
    GROUP BY se.sessionId
""")
fun observeSetCountsBySession(): Flow<List<SessionSetCount>>
```

- [ ] **Step 2: Expose via repositories**

```kotlin
// ExerciseRepository — returns ids newest-first.
fun observeRecentlyUsedIds(): Flow<List<String>>
// impl: dao.observeRecentlyUsedExerciseIds().map { rows -> rows.map { it.exerciseId } }

// SessionRepository — sessionId -> set count (live).
fun observeSetCountsBySession(): Flow<Map<String, Int>>
// impl: sessionDao... .observeSetCountsBySession().map { it.associate { r -> r.sessionId to r.setCount } }
```

(If `observeSetCountsBySession` is added to `AnalyticsDao`, inject `AnalyticsDao` into `SessionRepositoryImpl`; otherwise add to `SessionDao` to avoid a new dependency. Prefer adding it to `SessionDao` since `SessionRepositoryImpl` already holds it.)

- [ ] **Step 3: Write instrumented tests** (`PickerQueriesDaoTest`) — each discriminating:
  - recently-used orders by most recent `completedAt` across sessions, returns one row per exercise (GROUP BY), excludes exercises with only soft-deleted sets, excludes soft-deleted exercises/sessions.
  - set-counts counts only live sets, groups by session, omits sessions with zero live sets.

- [ ] **Step 4: JVM test** the `ExerciseRepository.observeRecentlyUsedIds` mapping with a fake DAO (ids preserved in order). Run `./gradlew testDebugUnitTest --tests "*ExerciseRepositoryTest"`.

- [ ] **Step 5: Compile instrumented** — `./gradlew assembleDebugAndroidTest`.
- [ ] **Step 6: Commit** — `M2: recently-used + per-session set-count queries (+ instrumented tests)`

---

# PHASE 2 — Shared UI components

> All components: stateless, previewable (`@Preview` light + dark), M3 roles only (no hardcoded hex), logging-path targets ≥56dp, content descriptions per 03-ux-spec §7. Add `@Preview`s; previews are the "test" for layout (architecture §7: layout screens get previews, not tests).

## Task 7: WeightStepper + RepsStepper

**Files:**
- Create: `ui/components/WeightStepper.kt`
- Create: `ui/components/RepsStepper.kt`
- Modify: `res/values/strings.xml` (content-description templates)

- [ ] **Step 1: Implement `WeightStepper`**

Signature + behavior (write idiomatic Compose against this):

```kotlin
@Composable
fun WeightStepper(
    valueKg: Double?,            // null = empty (never-performed); render placeholder, value not tappable-empty
    unit: WeightUnit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onValueClick: () -> Unit,   // opens the inline numpad on the NUMBER
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
)
```

- Layout: `[ − ]  [ value unit ]  [ + ]`. The `−`/`+` are `≥56dp` square `IconButton`s/`FilledTonalButton`s; the center shows `Weights.format(valueKg, unit)` + `Weights.label(unit)` (or a placeholder like "—" when null) and is itself a `≥56dp` clickable region calling `onValueClick`.
- Content descriptions (stateful, 03-ux-spec §7): decrement = "Decrease weight, {increment} {unit-long}" (e.g. "2.5 kilograms"), increment analogous, center = "Weight {value} {unit-long}, tap to enter exactly". Provide unit-long via strings (`kilograms`/`pounds`).
- `enabled = false` greys the steppers (used while numpad is open, or pre-weight).
- `@Preview`: KG with value, LB with value, empty (null) state.

- [ ] **Step 2: Implement `RepsStepper`**

```kotlin
@Composable
fun RepsStepper(
    reps: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onValueClick: () -> Unit,
    modifier: Modifier = Modifier,
    targetHint: String? = null, // rep-range hint text (e.g. "8–12"); null in M2 (targets land M3)
    enabled: Boolean = true,
)
```

- Same shape; center shows the integer + optional `targetHint` as supporting text. Decrement floors at 1. Content descriptions analogous ("Decrease reps", "Reps {n}, tap to enter exactly").
- `@Preview`: plain, with target hint.

- [ ] **Step 3: Build** — `./gradlew assembleDebug` (composables compile; previews render in IDE). Expected `BUILD SUCCESSFUL`.
- [ ] **Step 4: Commit** — `M2: WeightStepper + RepsStepper components`

---

## Task 8: InlineNumpad

**Files:**
- Create: `ui/components/InlineNumpad.kt`
- Modify: `res/values/strings.xml`

03-ux-spec §4.3: bottom-sheet-style pad that **replaces the stepper area inline** (the card stays visible — NOT a dialog). 4×3 grid (1–9, 0, ".", ⌫), quick chips (+10 / +5 / +2.5 / −2.5 in display unit), confirm. Keys ≥56dp. The system keyboard is never used.

- [ ] **Step 1: Implement**

```kotlin
@Composable
fun InlineNumpad(
    initialText: String,        // current display value as text, e.g. "82.5"
    allowDecimal: Boolean,      // true for weight, false for reps
    quickChips: List<Double>,   // display-unit deltas e.g. [10.0, 5.0, 2.5, -2.5]; empty for reps
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- Internal `remember` of the working text seeded from `initialText`. Keys: digits append; `.` appends once (only if `allowDecimal` and not already present); `⌫` deletes last char; quick chips add their delta to the parsed current value (clamp ≥ 0) and rewrite the text. A prominent current-value display sits above the grid. Confirm calls `onConfirm(text)`; a dismiss affordance (tap-away handled by the host, plus a visible close) calls `onDismiss`.
- All grid keys + chips + confirm are `≥56dp`; content descriptions name each key ("Digit 7", "Decimal point", "Backspace", "Add 2.5", "Confirm").
- The numpad does **not** know about kg/lb — it edits display-unit text. The caller converts.
- `@Preview`: weight mode (with chips), reps mode (no decimal, no chips).

- [ ] **Step 2: Build** — `./gradlew assembleDebug`.
- [ ] **Step 3: Commit** — `M2: InlineNumpad component (inline, no system keyboard)`

---

# PHASE 3 — Navigation + screens

## Task 9: Navigation routes + picker-result plumbing + strings

**Files:**
- Modify: `ui/navigation/Destinations.kt`
- Modify: `ui/navigation/LiftLogNavHost.kt`
- Modify: `res/values/strings.xml`

- [ ] **Step 1: Add routes** (type-safe, matching the M0 `@Serializable` pattern)

```kotlin
@Serializable data class ActiveSessionRoute(val sessionId: String)
@Serializable data class SessionDetailRoute(val sessionId: String)
@Serializable data object ExercisePickerRoute

/** savedStateHandle key the picker writes its result to (flagged decision #7). */
const val PICKED_EXERCISE_ID = "picked_exercise_id"
```

These are NOT added to `topLevelDestinations`, so `LiftLogApp` automatically hides the bottom bar on them (Active Session is "a mode, not a place", 03-ux-spec §2).

- [ ] **Step 2: Register destinations + result plumbing in `LiftLogNavHost`**

```kotlin
composable<ActiveSessionRoute> { entry ->
    val pickedId by entry.savedStateHandle
        .getStateFlow<String?>(PICKED_EXERCISE_ID, null)
        .collectAsStateWithLifecycle()
    ActiveSessionScreen(
        onFinished = { navController.popBackStack() },
        onDiscarded = { navController.popBackStack() },
        onAddExercise = { navController.navigate(ExercisePickerRoute) },
        pickedExerciseId = pickedId,
        onPickedExerciseConsumed = { entry.savedStateHandle[PICKED_EXERCISE_ID] = null },
    )
}
composable<ExercisePickerRoute> {
    ExercisePickerScreen(
        onSelected = { exerciseId ->
            navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_EXERCISE_ID, exerciseId)
            navController.popBackStack()
        },
        onCancel = { navController.popBackStack() },
    )
}
composable<SessionDetailRoute> {
    SessionDetailScreen(onBack = { navController.popBackStack() })
}
```

And update Home's registration to navigate:

```kotlin
composable<HomeRoute> {
    HomeScreen(
        onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
        onOpenSession = { id -> navController.navigate(ActiveSessionRoute(id)) },
        onOpenSessionDetail = { id -> navController.navigate(SessionDetailRoute(id)) },
    )
}
```

History gets `onOpenSessionDetail` similarly (Task 15).

- [ ] **Step 3: Add all new strings** to `strings.xml` (names; implementer fills natural copy): `session_untitled` ("Quick workout"), `home_resume`, `home_start_training`, `home_start_empty`, `home_recent`, `home_no_history`, `session_discard`, `session_discard_confirm_title`, `session_discard_confirm_message`, `session_finish`, `session_finish_summary` (param: set count), `session_add_exercise`, `session_log_set`, `session_remove_exercise`, `session_replace_exercise`, `set_logged_cd` (params), `picker_title`, `picker_search`, `picker_recent`, `picker_create`, `picker_create_name`, `picker_create_muscle`, `picker_create_equipment`, `picker_duplicate_name`, `history_title`, `history_set_count` (param), plus stepper/numpad CD strings from Tasks 7–8, plus `weight_kilograms`/`weight_pounds` long forms.

- [ ] **Step 4: Build** — `./gradlew assembleDebug` will FAIL until the referenced screens exist; that's expected. Instead just confirm `Destinations.kt` compiles by keeping the NavHost edits in a compilable state OR land Task 9's NavHost wiring together with the first screen it references. **Practical sequencing:** commit `Destinations.kt` + strings now; apply the `LiftLogNavHost` wiring incrementally as each screen task lands (each screen task re-states the exact `composable<...>` block it adds). Mark this step done when `Destinations.kt` + strings compile (`assembleDebug` green with NavHost still pointing at placeholders).
- [ ] **Step 5: Commit** — `M2: navigation routes + picker-result key + M2 strings`

---

## Task 10: Home screen (real)

**Files:**
- Create: `ui/home/HomeViewModel.kt`
- Modify: `ui/home/HomeScreen.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/ui/home/HomeViewModelTest.kt`
- Create fakes: `app/src/test/kotlin/de/simiil/liftlog/testing/fakes/FakeSessionRepository.kt` (and reuse for later VM tests)

03-ux-spec §3: resume card (only when a session is live), "Start training" with the empty-session entry (**template chips deferred to M3**), recent list.

- [ ] **Step 1: Define UiState + ViewModel**

```kotlin
data class HomeUiState(
    val resume: ResumeCardUi? = null,
    val recent: List<RecentSessionUi> = emptyList(),
)
data class ResumeCardUi(val sessionId: String, val title: String, val exerciseCount: Int, val startedAt: Instant)
data class RecentSessionUi(val sessionId: String, val title: String, val startedAt: Instant, val setCount: Int)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    // resume: the live session (endedAt == null) + its live exercise count
    private val resume: Flow<ResumeCardUi?> =
        sessionRepository.observeActiveSession().flatMapLatest { active ->
            if (active == null) flowOf(null)
            else sessionRepository.observeSessionDetails(active.id).map { details ->
                ResumeCardUi(
                    sessionId = active.id,
                    title = active.templateNameSnapshot ?: /* R.string.session_untitled resolved in UI */ "",
                    exerciseCount = details?.exercises?.size ?: 0,
                    startedAt = active.startedAt,
                )
            }
        }

    val uiState: StateFlow<HomeUiState> = combine(
        resume,
        sessionRepository.observeHistory(),
        sessionRepository.observeSetCountsBySession(),
    ) { resumeCard, history, counts ->
        HomeUiState(
            resume = resumeCard,
            recent = history.filter { it.endedAt != null }.take(5).map {
                RecentSessionUi(it.id, it.templateNameSnapshot ?: "", it.startedAt, counts[it.id] ?: 0)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Resume the live session if present, else start a fresh empty one; returns the id to navigate to. */
    fun startOrResume(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val existing = uiState.value.resume?.sessionId
            onReady(existing ?: sessionRepository.startEmptySession().id)
        }
    }
}
```

(Title null → resolve `R.string.session_untitled` in the composable, not the VM. Keep VM Android-free; pass the empty string and let UI substitute, OR expose a `titleRes`/nullable name. Simplest: store nullable `name: String?` in UiState and the composable shows `name ?: stringResource(R.string.session_untitled)`. Adjust the data classes to carry `name: String?`.)

- [ ] **Step 2: Write ViewModel tests** (Turbine + fakes): resume null when no active session; resume populated (title fallback, exercise count) when active; recent excludes the live session and limits to 5, newest first, with set counts; `startOrResume` returns the existing id when live, else creates a new session.

- [ ] **Step 3: Implement `HomeScreen`** — keep the existing `PlaceholderScreen`-style top bar with the settings gear; signature:

```kotlin
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSessionDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
)
```

Body (Scaffold + `TopAppBar` "LiftLog" + gear action): if `resume != null` a prominent Resume card ("▶ RESUME — {title}", "{n} exercises · {elapsed} min" computed from `startedAt` to now) → `onOpenSession(id)`. "Start training" section with a single "+ empty" entry tile → `viewModel.startOrResume(onOpenSession)` (template chips are M3). "Recent" list of `RecentSessionUi` (title — relative date, set count) → `onOpenSessionDetail(id)`; empty-state text when no history. Relative date formatting via `java.time`/`DateUtils` in the composable.

- [ ] **Step 4: Build + test** — `./gradlew testDebugUnitTest --tests "*HomeViewModelTest" assembleDebug`.
- [ ] **Step 5: Commit** — `M2: Home screen (resume card, empty-session start, recent list)`

---

## Task 11: Exercise Picker (shared) + create-custom

**Files:**
- Create: `ui/exercises/ExercisePickerViewModel.kt`, `ui/exercises/ExercisePickerScreen.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/ui/exercises/ExercisePickerViewModelTest.kt`
- Create fake: `testing/fakes/FakeExerciseRepository.kt`

03-ux-spec §6: search-first list, filter chips (muscle group / equipment), recent on top, "+ create exercise" inline (3 fields). Returns the selection to the caller.

- [ ] **Step 1: UiState + ViewModel**

```kotlin
data class ExercisePickerUiState(
    val query: String = "",
    val muscleFilter: MuscleGroup? = null,
    val equipmentFilter: Equipment? = null,
    val recent: List<Exercise> = emptyList(),     // top, when no query/filter active
    val results: List<Exercise> = emptyList(),    // filtered + sorted (recent-first then name)
    val createError: Int? = null,                 // string res id, e.g. duplicate name
)

@HiltViewModel
class ExercisePickerViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val muscle = MutableStateFlow<MuscleGroup?>(null)
    private val equipment = MutableStateFlow<Equipment?>(null)
    private val createError = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<ExercisePickerUiState> = combine(
        exerciseRepository.observeVisible(),
        exerciseRepository.observeRecentlyUsedIds(),
        query, muscle, equipment, createError,
    ) { ... build state ... }.stateIn(...)
    // results: visible.filter(name contains query, matches muscle, matches equipment).
    //   When query+filters empty: recent = visible ordered by recentIds (top N), results = the rest.
    //   When searching/filtering: recent empty, results = matches sorted (recent-first, then name).

    fun onQueryChange(q: String) { query.value = q }
    fun onMuscleFilter(m: MuscleGroup?) { muscle.value = m }
    fun onEquipmentFilter(e: Equipment?) { equipment.value = e }

    /** Create custom, return the new exercise id via [onCreated], or surface a duplicate-name error. */
    fun createCustom(name: String, muscleGroup: MuscleGroup, equipment: Equipment, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val created = exerciseRepository.createCustom(name, muscleGroup, equipment)
                onCreated(created.id)
            } catch (e: IllegalArgumentException) {
                createError.value = R.string.picker_duplicate_name  // (use a res id constant, resolve in UI)
            }
        }
    }
}
```

(Keep VM Android-free: instead of `R.string`, expose a small sealed `CreateError` enum and map to a string in the composable.)

- [ ] **Step 2: ViewModel tests** (Turbine + `FakeExerciseRepository`): query filters by name (case-insensitive substring); muscle/equipment filters; recent surfaces recently-used ids first when no query; `createCustom` happy path returns the new id; duplicate-name surfaces an error and does not call `onCreated`.

- [ ] **Step 3: Implement `ExercisePickerScreen`**

```kotlin
@Composable
fun ExercisePickerScreen(
    onSelected: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExercisePickerViewModel = hiltViewModel(),
)
```

- Top bar: title + close (`onCancel`). Search `TextField` (system keyboard is fine here — not the logging hot path). Filter `FilterChip` rows for muscle group + equipment. "Recent" section (when present) then the results `LazyColumn`; each row → `onSelected(exercise.id)`. A "+ create exercise" entry opens an inline form (name field + muscle `DropdownMenu`/chips + equipment chips + confirm); on confirm calls `viewModel.createCustom(...) { id -> onSelected(id) }`; show the duplicate-name error inline. ≥48dp rows.

- [ ] **Step 4: Build + test** — `./gradlew testDebugUnitTest --tests "*ExercisePickerViewModelTest" assembleDebug`.
- [ ] **Step 5: Commit** — `M2: Exercise picker (search, filters, recent, create-custom)`

---

## Task 12: Active Session ViewModel

**Files:**
- Create: `ui/session/ActiveSessionViewModel.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/ui/session/ActiveSessionViewModelTest.kt`

The brain of the make-or-break screen. Read 03-ux-spec §4 in full before implementing. ViewModel reads `sessionId` from `SavedStateHandle` (the type-safe route arg) via `savedStateHandle.toRoute<ActiveSessionRoute>()`.

- [ ] **Step 1: Define the UiState**

```kotlin
enum class CardState { COMPLETED, ACTIVE, UPCOMING }

data class ActiveSessionUiState(
    val sessionId: String = "",
    val name: String? = null,            // null -> "Quick workout" in UI
    val startedAt: Instant? = null,
    val unit: WeightUnit = WeightUnit.KG,
    val cards: List<ExerciseCardUi> = emptyList(),
    val entry: EntryUi? = null,          // present iff there is an ACTIVE card
    val loading: Boolean = true,
    val finished: Boolean = false,       // signals navigation away
    val discarded: Boolean = false,
    val lastFinishedSetCount: Int = 0,   // for the finish snackbar
)

data class ExerciseCardUi(
    val sessionExerciseId: String,
    val exerciseId: String,
    val name: String,
    val equipment: Equipment,
    val targetSets: Int?,                // null in M2 (ad-hoc); drives dormant auto-advance
    val state: CardState,
    val sets: List<LoggedSet>,           // live, sorted by position (weights in kg)
    val ghostSets: List<LoggedSet>,      // last-performance snapshot for the ACTIVE card (else empty)
    val editingSetId: String? = null,    // long-press edit row target
)

data class EntryUi(
    val sessionExerciseId: String,
    val weightKg: Double?,               // null -> empty, numpad must be used; LOG SET disabled
    val reps: Int,
    val numpad: NumpadUi? = null,        // null -> closed
)
data class NumpadUi(val target: NumpadTarget, val text: String)
enum class NumpadTarget { WEIGHT, REPS }
```

- [ ] **Step 2: Compose the reactive state**

- Combine `sessionRepository.observeSessionDetails(sessionId)`, `exerciseRepository.observeAll()` (→ `id→Exercise` map for name/equipment), and `settingsRepository.weightUnit`.
- Build `cards` from `details.exercises` (already position-sorted by the M1 mapper): join names/equipment from the map; `sets` from each `SessionExerciseWithSets`.
- Active-card selection: keep `activeSessionExerciseId` in `SavedStateHandle` (trivial transient UI state, architecture §3). Default = first card with `sets.size < (targetSets ?: Int.MAX_VALUE)` i.e. first not-yet-"complete" card, else the last card. `CardState`: the active id → ACTIVE; cards before it with sets → COMPLETED; the rest → UPCOMING. (Refine: COMPLETED = has ≥1 set and not active; UPCOMING = no sets and not active.)
- `ghostSets`: fetched via `sessionRepository.lastPerformance(exerciseId)` when a card becomes active; cache `Map<exerciseId, List<LoggedSet>>` in the VM; trigger a state rebuild when it arrives.
- `entry`: when there is an active card, seed from `Prefill.forNextSet(setsThisEntry = activeCard.sets, lastPerformance = ghostSets)` — but only re-seed when the active card changes or after a successful log; preserve the user's in-progress edits otherwise (hold `entry` in a `MutableStateFlow`, re-seed explicitly).

- [ ] **Step 3: Events**

```kotlin
fun onActivateCard(sessionExerciseId: String)     // set active, seed entry from prefill, fetch ghost
fun onWeightStep(deltaDisplay: Double)            // entry.weightKg ± displayToKg(delta); clamp >=0
fun onRepsStep(delta: Int)                         // entry.reps ± delta; clamp >=1
fun onOpenNumpad(target: NumpadTarget)             // seed numpad text from current entry (display unit)
fun onNumpadText(text: String)
fun onNumpadConfirm()                              // parse text -> entry (weight via displayToKg)
fun onNumpadDismiss()
fun onLogSet()                                     // require entry.weightKg != null
fun onAddExercise(exerciseId: String)             // repo.addExerciseToSession; activate it (used by picker result)
fun onLongPressSet(setId: String)                 // open inline edit row
fun onEditSetSave(setId, weightKg, reps, rpe, note)
fun onDeleteSet(setId: String)
fun onRemoveExercise(sessionExerciseId: String)
fun onReplaceExercise(sessionExerciseId, newExerciseId)  // used by picker result when replacing
fun onFinish()                                     // repo.finishSession; set finished=true + lastFinishedSetCount
fun onDiscard()                                    // repo.softDeleteSession; set discarded=true
```

`onLogSet` logic: `val w = entry.weightKg ?: return`; `viewModelScope.launch { sessionRepository.logSet(activeId, w, entry.reps); reseedEntryFromPrefill(); maybeAutoAdvance() }`. The new set arrives via the `observeSessionDetails` flow → rule 1 prefill now uses it. `maybeAutoAdvance`: if `targetSets != null && sets.size >= targetSets` → collapse + activate next UPCOMING (dormant in M2 since targets are null — flagged #9).

- [ ] **Step 4: ViewModel tests** (Turbine + fakes — `FakeSessionRepository`, `FakeExerciseRepository`, `FakeSettingsRepository`). Seed the fake session repo with a session + one ad-hoc exercise and a `lastPerformance` for it. Assert:
  - On load, the active card's `entry` is pre-filled from `lastPerformance` first set (rule 2).
  - `onWeightStep(+2.5)` (KG) increments `entry.weightKg` by 2.5; floors at 0.
  - `onLogSet` calls the repo and the next `entry` re-primes to the just-logged values (rule 1) — assert via the fake recording the logged set and the emitted state.
  - `onLogSet` is a no-op / disabled when `entry.weightKg == null` (never-performed rule 3).
  - Auto-advance fires when a synthetic `targetSets` is met (construct a card with `targetSets = 1`) — collapses and activates the next card.
  - `onFinish` sets `finished = true` with the correct `lastFinishedSetCount`.

- [ ] **Step 5: Run** — `./gradlew testDebugUnitTest --tests "*ActiveSessionViewModelTest"`.
- [ ] **Step 6: Commit** — `M2: ActiveSessionViewModel (prefill, log, steppers/numpad, finish/discard)`

---

## Task 13: Active Session screen — card stack, logging, finish/discard

**Files:**
- Create: `ui/session/ActiveSessionScreen.kt`
- Create: `ui/session/ExerciseCard.kt`

Implements 03-ux-spec §4.1 wireframe. `ActiveSessionScreen` signature matches the NavHost wiring (Task 9):

```kotlin
@Composable
fun ActiveSessionScreen(
    onFinished: () -> Unit,
    onDiscarded: () -> Unit,
    onAddExercise: () -> Unit,
    pickedExerciseId: String?,
    onPickedExerciseConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
)
```

- [ ] **Step 1: Screen scaffold + picker-result + navigation effects**
  - `LaunchedEffect(pickedExerciseId)`: if non-null → `viewModel.onAddExercise(it)` (or `onReplaceExercise` when a replace is pending — track a "pending replace target" in the VM and branch) then `onPickedExerciseConsumed()`.
  - `LaunchedEffect(uiState.finished)`: when true → show finish snackbar (`session_finish_summary` with `lastFinishedSetCount`) then `onFinished()`. `LaunchedEffect(uiState.discarded)` → `onDiscarded()`.
  - Top bar (custom, not `PlaceholderScreen`): `✕` (discard, opens confirm dialog) · title (`name ?: session_untitled`) · running timer (`startedAt`→now, a 1s ticking `produceState`/`rememberCoroutineScope` clock) · `✔` (finish). The discard dialog is the one allowed dialog (destructive).

- [ ] **Step 2: Card stack (`LazyColumn`)** of `ExerciseCard`s + an "+ Add exercise" row (→ `onAddExercise()`):
  - **COMPLETED** card (`ExerciseCard.kt`): collapsed; shows "✓ {name}" and a compact summary "{weight} {unit} × {r·r·r}" (format each set's weight via `Weights.format`, reps joined by "·"). Tap → `onActivateCard`.
  - **UPCOMING** card: collapsed; "{name}" + optional "target {n}×" (null in M2). Tap → `onActivateCard`.
  - **ACTIVE** card: expanded. Header "{name}  {setsLogged}/{target or —}  ⋮" (⋮ overflow → remove / replace; replace navigates to picker with a pending-replace flag). Ghost row "last: {summary}" from `ghostSets` (omit when empty). Logged-set rows (`LoggedSetRow`, Task 14) for each set. Then the entry area: `WeightStepper` + `RepsStepper` (Task 7) OR — when `entry.numpad != null` — the `InlineNumpad` (Task 8) replacing the stepper area (card stays visible). Then a full-width `LOG SET` button (≥56dp, disabled when `entry.weightKg == null`) → `onLogSet()`. Then the **reserved rest-timer slot**: a fixed-height empty spacer with a subtle dashed placeholder — **stays visually empty in v1** (06 §3); do not fill or remove it.

- [ ] **Step 3: Wire entry interactions** — stepper `−/+` → `onWeightStep(∓stepIncrementDisplay(unit))` / `onRepsStep(∓1)`; tapping the weight number → `onOpenNumpad(WEIGHT)`; reps number → `onOpenNumpad(REPS)`; numpad confirm/dismiss → VM. Convert display↔kg only at this boundary using `Weights`.

- [ ] **Step 4: Add `@Preview`s** for COMPLETED, ACTIVE (with ghost + 2 sets), UPCOMING, and ACTIVE-with-numpad-open, and first-ever (empty weight, disabled LOG SET) — these are the 06 §4A commissioned states and double as visual smoke tests.

- [ ] **Step 5: Build** — `./gradlew assembleDebug`. (No VM logic change here; behavior covered by Task 12 + the Task 18 UI test.)
- [ ] **Step 6: Commit** — `M2: Active Session screen — card stack, steppers/numpad, LOG SET, finish/discard`

---

## Task 14: LoggedSetRow + inline edit (RPE / note / edit / delete), remove/replace

**Files:**
- Create: `ui/components/LoggedSetRow.kt` (shared by Active Session + Session Detail)
- Modify: `ui/session/ActiveSessionScreen.kt` / `ExerciseCard.kt` (use it; wire ⋮ remove/replace)

03-ux-spec §4.4: long-press a logged row → expands inline (weight/reps editable, RPE chips 6–10 in 0.5 steps, note field, delete-soft). Collapses on tap-away. Never required, never on the hot path.

- [ ] **Step 1: Implement `LoggedSetRow`**

```kotlin
@Composable
fun LoggedSetRow(
    index: Int,                 // 1-based set number
    set: LoggedSet,             // weight in kg
    unit: WeightUnit,
    expanded: Boolean,
    onLongPress: () -> Unit,
    onSave: (weightKg: Double, reps: Int, rpe: Double?, note: String?) -> Unit,
    onDelete: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- Collapsed: "① {weight} {unit} × {reps}  ✓" (+ a small RPE/note indicator when present). `Modifier.combinedClickable(onLongClick = onLongPress)`.
- Expanded: editable weight/reps (reuse the steppers, or compact numeric fields with the numpad — steppers are fine here since it's off the hot path), a row of RPE `FilterChip`s (6.0–10.0 by 0.5), a note `TextField`, `Delete` (calls `onDelete`, soft) and `Save` (`onSave`). Tap-away → `onCollapse`.
- Content descriptions: "Set {n} logged: {weight} {unit-long}, {reps} reps, double-tap to edit" (03-ux-spec §7).

- [ ] **Step 2: Wire into the active card** — track `editingSetId` per card in the VM (`onLongPressSet`, `onEditSetSave`→`updateSet`, `onDeleteSet`→`deleteSet`, collapse on tap-away). The ⋮ overflow on the active card header: "Remove exercise" → `onRemoveExercise`; "Replace exercise" → set a pending-replace target in the VM and `onAddExercise()` navigation (the screen's picker-result effect routes to `onReplaceExercise` when a replace is pending).

- [ ] **Step 3: Build + (re-run) tests** — `./gradlew testDebugUnitTest assembleDebug` (VM edit/remove/replace behavior is covered by Task 12 tests; add cases there if the VM gained `pendingReplace` state).
- [ ] **Step 4: Commit** — `M2: logged-set row inline edit (RPE/note/delete) + remove/replace exercise`

---

## Task 15: History list (real)

**Files:**
- Create/Modify: `ui/history/HistoryViewModel.kt`, `ui/history/HistoryScreen.kt`
- Modify: `ui/navigation/LiftLogNavHost.kt` (pass `onOpenSessionDetail`)
- Test: `app/src/test/kotlin/de/simiil/liftlog/ui/history/HistoryViewModelTest.kt`

03-ux-spec §6: reverse-chronological session cards (name, date, set count → tap → session detail). PR count omitted in M2 (flagged #5).

- [ ] **Step 1: UiState + ViewModel**

```kotlin
data class HistoryUiState(val sessions: List<HistorySessionUi> = emptyList())
data class HistorySessionUi(val sessionId: String, val name: String?, val startedAt: Instant, val setCount: Int)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    val uiState: StateFlow<HistoryUiState> = combine(
        sessionRepository.observeHistory(),
        sessionRepository.observeSetCountsBySession(),
    ) { history, counts ->
        HistoryUiState(
            history.filter { it.endedAt != null }
                .map { HistorySessionUi(it.id, it.templateNameSnapshot, it.startedAt, counts[it.id] ?: 0) }
        ) // observeHistory already orders by startedAt DESC
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
```

- [ ] **Step 2: ViewModel test** — finished sessions only, newest-first, set counts joined; empty when no finished sessions.

- [ ] **Step 3: `HistoryScreen`** (replace the placeholder): top bar "History"; `LazyColumn` of cards (title `name ?: session_untitled` — relative date · "{n} sets") → `onOpenSessionDetail(id)`. Empty-state text. Signature:

```kotlin
@Composable
fun HistoryScreen(
    onOpenSessionDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
)
```

Update the `composable<HistoryRoute>` registration to pass `onOpenSessionDetail = { navController.navigate(SessionDetailRoute(it)) }`.

- [ ] **Step 4: Build + test** — `./gradlew testDebugUnitTest --tests "*HistoryViewModelTest" assembleDebug`.
- [ ] **Step 5: Commit** — `M2: History list (finished sessions, set counts)`

---

## Task 16: Session detail (read + edit)

**Files:**
- Create: `ui/session/SessionDetailViewModel.kt`, `ui/session/SessionDetailScreen.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/ui/session/SessionDetailViewModelTest.kt`

03-ux-spec §6: read-only card stack; sets editable via the same long-press row (e.g. fixing a typo after the fact). Reuses `LoggedSetRow` + `SessionRepository.updateSet`/`deleteSet`.

- [ ] **Step 1: UiState + ViewModel** — `sessionId` from `savedStateHandle.toRoute<SessionDetailRoute>()`. Combine `observeSessionDetails(id)` + `observeAll()` (names/equipment) + `weightUnit`:

```kotlin
data class SessionDetailUiState(
    val name: String? = null,
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
    val unit: WeightUnit = WeightUnit.KG,
    val exercises: List<DetailExerciseUi> = emptyList(),
    val editingSetId: String? = null,
)
data class DetailExerciseUi(val name: String, val equipment: Equipment, val sets: List<LoggedSet>)
```

Events: `onLongPressSet`, `onEditSetSave(...)`→`updateSet`, `onDeleteSet`→`deleteSet`, `onCollapseEdit`.

- [ ] **Step 2: ViewModel test** — maps details to UI with names joined; `onEditSetSave` calls `updateSet`; `onDeleteSet` calls `deleteSet`.

- [ ] **Step 3: `SessionDetailScreen`** — top bar with back arrow + title; a non-interactive card stack (every exercise expanded showing its sets) where each `LoggedSetRow` supports long-press edit. NO active-entry steppers / LOG SET (the session is finished). Signature:

```kotlin
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionDetailViewModel = hiltViewModel(),
)
```

- [ ] **Step 4: Build + test** — `./gradlew testDebugUnitTest --tests "*SessionDetailViewModelTest" assembleDebug`.
- [ ] **Step 5: Commit** — `M2: Session detail (read-only stack + long-press set edit)`

---

# PHASE 4 — Critical UI test + polish

## Task 17: Hilt instrumented-test harness

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `app/src/androidTest/kotlin/de/simiil/liftlog/HiltTestRunner.kt`
- Create: `app/src/androidTest/kotlin/de/simiil/liftlog/di/TestDatabaseModule.kt`

The existing instrumented tests use Room directly (no Hilt). The critical UI test drives the real app graph, so we need a Hilt test runner + an in-memory DB override.

- [ ] **Step 1: Add deps** to the catalog + `app/build.gradle.kts`:

```kotlin
androidTestImplementation(libs.hilt.android.testing)
kspAndroidTest(libs.hilt.compiler)
androidTestImplementation(libs.androidx.compose.ui.test.junit4)
debugImplementation(libs.androidx.compose.ui.test.manifest)
```

Set the runner: `testInstrumentationRunner = "de.simiil.liftlog.HiltTestRunner"`.

- [ ] **Step 2: Custom runner** (swaps in `HiltTestApplication`):

```kotlin
package de.simiil.liftlog
import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
```

- [ ] **Step 3: In-memory DB override** for tests (deterministic; survives `recreate()` within the process):

```kotlin
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    // Provide an in-memory AppDatabase + the same DAOs/Transactor/Clock/Json/scope DatabaseModule provides.
    // (Mirror DatabaseModule exactly, but Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries() off.)
}
```

> Note: replacing the whole `DatabaseModule` means re-providing everything it did (DB, 5 DAOs, Transactor, Clock, Json, `@ApplicationScope` scope). Read `DatabaseModule.kt` and reproduce its provider set against an in-memory DB. The seeder still runs (HiltTestApplication → `LiftLogApplication`? No — HiltTestApplication is a plain app; the seeder won't auto-run, so tests seed exactly the exercises they need).

- [ ] **Step 4: Compile** — `./gradlew assembleDebugAndroidTest`. Expected `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** — `M2: Hilt instrumented-test harness (runner + in-memory DB module)`

---

## Task 18: Critical-path Compose UI test (the headline exit criterion)

**Files:**
- Create: `app/src/androidTest/kotlin/de/simiil/liftlog/ui/CriticalLoggingPathTest.kt`

05-roadmap M2 exit criterion: "Compose UI test covers start → log pre-filled → adjust → log → **process-death → resume** path; backgrounding mid-session loses nothing." (Templates are M3, so M2 starts from an empty session + a pre-seeded prior session as the pre-fill source — flagged #2/#11.)

- [ ] **Step 1: Test setup** — `@HiltAndroidTest`, `createAndroidComposeRule<MainActivity>()`, `HiltAndroidRule`. Inject the real `SessionRepository`/`ExerciseRepository` (from the in-memory graph). In `@Before`, seed the DB to create the pre-fill source:
  - create a custom exercise (or insert a known one),
  - create + finish a prior session containing that exercise with known sets (e.g. 30 kg × 10, 10, 8) via the repository,
  so `lastPerformance(exerciseId)` has data.

- [ ] **Step 2: Drive the flow** (use `testTag`s added to the key controls — LOG SET, weight `+`, the weight value, the logged-set rows, the resume card):
  1. From Home, tap "+ empty" → lands on Active Session.
  2. Tap "+ Add exercise" → picker → select the seeded exercise → returns to the card (ACTIVE).
  3. Assert the entry steppers are **pre-filled** from the prior session's first set (30 kg × 10) — rule 2.
  4. Tap LOG SET once → assert a logged-set row "30 kg × 10" appears and the entry re-primes to 30 × 10 (rule 1).
  5. Tap weight `+` once → assert the entry shows 32.5 kg (KG increment).
  6. Tap LOG SET → assert a second logged-set row "32.5 kg × 10".
  7. **Process-death proxy:** `composeTestRule.activityRule.scenario.recreate()`.
  8. Assert after recreate: the app resumes the active session (either directly or via Home's Resume card → tap it), and **both logged sets are still present** (state rehydrated from Room, not `SavedStateHandle`).

- [ ] **Step 3: Add the `testTag`s** the test needs to the relevant composables (Tasks 10, 11, 13). Keep tags as string constants in a small `ui/testTags` object or per-screen `const`.

- [ ] **Step 4: Compile locally** — `./gradlew assembleDebugAndroidTest` (no local emulator; the test executes in CI). Expected `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** — `M2: critical logging-path Compose UI test (start → prefill → adjust → log → recreate → resume)`

---

## Task 19: Accessibility + tap-math pass; final review; CI; revision log

**Files:**
- Touch-ups across `ui/` as the audit finds them
- Modify: `CLAUDE.md` (note new instrumented Compose-UI test in the build/test section if commands change)
- Modify: `docs/superpowers/plans/2026-06-08-m2-logging-flow.md` (revision log)

- [ ] **Step 1: Accessibility audit** against 03-ux-spec §7 / 06 §3:
  - All logging-path targets ≥56dp (steppers, LOG SET, numpad keys); everything else ≥48dp — verify.
  - Content descriptions stateful + specific (steppers, LOG SET, each logged set row).
  - TalkBack order on the active card: name → ghost → logged sets → weight → reps → LOG SET (use `Modifier.semantics`/traversal order where needed).
  - Trend/status never color-alone (no trend badges in M2, but confirm any status uses glyph+text).
  - Dynamic type to 200%: verify the active card's steppers stack/reflow without losing the 1-tap path (a `@Preview` at `fontScale = 2f`; adjust layout to stack steppers vertically if they clip).
  - M3 roles only — grep for hardcoded `Color(` / hex in new `ui/` files; replace with `MaterialTheme.colorScheme.*`.

- [ ] **Step 2: Tap-math sanity (03-ux-spec §4.5)** — confirm in code/preview that "another set, same weight/reps" = **1 tap** (LOG SET, pre-filled), "+2.5 kg" = **2 taps** (stepper +, LOG SET). Note the on-device confirmation belongs to the owner's device-verification gate.

- [ ] **Step 3: Full CI command locally** — `./gradlew lint testDebugUnitTest assembleDebug` → green; `./gradlew assembleDebugAndroidTest` → compiles. Fix any lint (unused resources, etc.).

- [ ] **Step 4: Final holistic review** — dispatch a fresh reviewer over the whole M2 diff (spec compliance vs 03-ux-spec §3/§4/§6 + architecture §1/§3 layering: ViewModels touch only domain interfaces; no Android imports in `domain/`; reads are Flow, writes suspend). Fix findings.

- [ ] **Step 5: Write the revision log** at the end of this plan file (deviations, review-driven fixes, forward notes for M3/M4/M5 — e.g. M3 lights up targets/auto-advance + template-start; M4 adds PR count to history cards + analytics; M5 adds the kg/lb toggle setter+UI).

- [ ] **Step 6: Commit** — `M2: a11y + tap-math pass, final review fixes, plan revision log`

---

## Self-review (plan vs spec)

- **Home** (resume card / empty start / recent) → Tasks 9–10. Template chips correctly deferred to M3 (roadmap).
- **Active Session** (card stack, steppers, inline numpad, pre-fill, long-press RPE/note, finish/discard, add/remove/replace) → Tasks 7, 8, 12, 13, 14. Reserved rest-timer slot stays empty (Task 13). ✓
- **Exercise picker incl. create-custom** → Task 11. ✓
- **History list + session detail (read/edit)** → Tasks 15–16. ✓
- **Pre-fill rules §4.2** → Task 3 (pure) + Task 12 (wired). **Units §5** → Tasks 1–2 (KG-only UI, flagged). **Tap math §4.5** → Task 19. ✓
- **Exit criteria:** tap-math (Task 19 + owner device gate), the deep Compose test with process-death→resume (Tasks 17–18), backgrounding loses nothing (Room-immediate writes — Task 5 `logSet`, verified by Task 18). ✓
- **Data-layer gaps** (logging writes, positions, prefill composition, recency, set counts) → Tasks 4–6. ✓
- **Architecture/layering** invariants enforced in Task 19 review. **No new third-party deps** beyond test-only Hilt-testing + compose-ui-test (already-justified Jetpack test stack, architecture §5). ✓

Type-consistency check: `EntryUi.weightKg: Double?`, `LoggedSet.weightKg: Double`, `Weights.format(Double, WeightUnit)`, `Prefill.forNextSet(List<LoggedSet>, List<LoggedSet>): PrefillValues`, `SessionRepository.logSet(String, Double, Int): LoggedSet` — consistent across tasks.
