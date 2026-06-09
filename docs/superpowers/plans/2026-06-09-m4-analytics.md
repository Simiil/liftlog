# M4 — Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the Analytics tab — an exercise browser (this-week card, search, per-exercise trend badge + sparkline) and an exercise detail (metric chips, range selector, progress chart with PR markers, recent sessions) — driven by tested pure formulas over existing Room data.

**Architecture:** Pure `domain/analytics` functions (e1RM, session metrics, OLS trend, downsample, per-exercise summary) computed over `AnalyticsDao` flows, exposed through a new `AnalyticsRepository`, consumed by two screens' ViewModels. Charts use Vico behind a `ui/components/charts` wrapper (simple config). UI ↔ ViewModel `StateFlow` ↔ Repository ↔ Room — ViewModels never touch DAOs.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Room, Hilt, Coroutines/Flow, Vico (new), JUnit4 + Turbine.

**Spec:** `docs/superpowers/specs/2026-06-09-m4-analytics-design.md`. Formula reference: the design bundle's `data.js`. Fixtures: `docs/04-analytics-spec.md §5`.

**Conventions to follow:**
- Kotlin sources live under `app/src/main/kotlin/de/simiil/liftlog/…`; JVM tests under `app/src/test/kotlin/…`; instrumented tests under `app/src/androidTest/kotlin/…`.
- Run JVM tests: `./gradlew testDebugUnitTest`. CI parity: `./gradlew lint testDebugUnitTest assembleDebug`.
- Instrumented (DAO) tests run locally on `emulator-5554`; scope with `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.
- Commit trailer on EVERY commit: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- Double asserts use a delta: `assertEquals(expected, actual, 0.01)`.

---

## File structure (what each new file owns)

**domain/analytics** (pure, no Android):
- `Epley.kt` — `e1rm(weightKg, reps): Double?`
- `SessionMetrics.kt` — `SetEntry`, `SessionMetrics`, `sessionMetrics(sets)`
- `Trend.kt` — `TrendDirection`, `TrendPoint`, `TrendResult`, `trend(points, nowMillis)`
- `Downsample.kt` — `Aggregation`, `downsample(points, aggregation, maxPoints)`
- `ExerciseAnalytics.kt` — `DatedSet`, `SessionPoint`, `ExerciseSummary`, `summarize(equipment, sets, nowMillis)`

**data**:
- `data/dao/AnalyticsDao.kt` (modify) — add `observeAllSetsSince`, `observeTrainedExercises`
- `data/dao/Relations.kt` (modify) — add `TrainedExerciseRow`
- `data/repository/AnalyticsRepositoryImpl.kt` (new)
- `data/seed/SyntheticHistorySeeder.kt` (new, debug-gated use)

**domain/repository**:
- `AnalyticsRepository.kt` (new) — interface + `WeekSummary`, `TrainedExercise`

**ui**:
- `ui/theme/ExtendedColors.kt` (new) — `LiftLogExtendedColors`, `LocalLiftLogColors`
- `ui/theme/Theme.kt` (modify) — provide extended colors
- `ui/components/charts/ProgressLineChart.kt` (new)
- `ui/components/charts/Sparkline.kt` (new)
- `ui/analytics/TrendBadge.kt` (new)
- `ui/analytics/AnalyticsBrowserViewModel.kt` (new)
- `ui/analytics/AnalyticsScreen.kt` (replace placeholder → browser)
- `ui/analytics/ExerciseDetailViewModel.kt` (new)
- `ui/analytics/ExerciseDetailScreen.kt` (new)
- `ui/navigation/Destinations.kt` + `LiftLogNavHost.kt` (modify) — detail route
- `ui/settings/SettingsScreen.kt` + `SettingsViewModel.kt` (modify) — debug seeder button

**di**:
- `RepositoryModule.kt` (modify) — bind `AnalyticsRepository`

**build**:
- `gradle/libs.versions.toml` + `app/build.gradle.kts` — Vico + `buildConfig`

---

## Task 1: Add Vico dependency + enable BuildConfig

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:38-40` (buildFeatures), `:85` (deps)

- [ ] **Step 1: Add Vico to the version catalog**

In `[versions]` add:
```toml
vico = "2.1.3"
```
In `[libraries]` add:
```toml
vico-compose-m3 = { group = "com.patrykandpatrick.vico", name = "compose-m3", version.ref = "vico" }
```

- [ ] **Step 2: Enable BuildConfig + add the Vico dependency**

In `app/build.gradle.kts`, `buildFeatures { compose = true }` becomes:
```kotlin
    buildFeatures {
        compose = true
        buildConfig = true
    }
```
In `dependencies`, after `implementation(libs.reorderable)`:
```kotlin
    implementation(libs.vico.compose.m3)
```

- [ ] **Step 3: Verify the build resolves**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -i vico`
Expected: a `com.patrykandpatrick.vico:compose-m3:<version>` line resolves. If `2.1.3` fails to resolve, bump to the latest stable `2.x` (`./gradlew assembleDebug` will report an unresolved-dependency error pointing at the catalog) and retry. Then `./gradlew assembleDebug` → BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**
```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build(m4): add Vico charting dependency + enable BuildConfig

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: e1RM (Epley, guardrails)

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/analytics/Epley.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/domain/analytics/EpleyTest.kt`

- [ ] **Step 1: Write the failing test** (fixtures from `docs/04-analytics-spec.md §2, §5`)

```kotlin
package de.simiil.liftlog.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpleyTest {
    @Test fun oneRep_returnsWeight() = assertEquals(102.5, e1rm(102.5, 1)!!, 0.0001)

    @Test fun midRange_appliesEpley() {
        assertEquals(116.6667, e1rm(100.0, 5)!!, 0.001)   // 100·(1+5/30)
        assertEquals(120.3333, e1rm(95.0, 8)!!, 0.001)    // 95·(1+8/30)
    }

    @Test fun twelveReps_included() = assertEquals(140.0, e1rm(100.0, 12)!!, 0.001) // 100·(1+12/30)

    @Test fun aboveTwelveReps_excluded() = assertNull(e1rm(60.0, 15))

    @Test fun zeroWeight_isZeroNotNull() = assertEquals(0.0, e1rm(0.0, 8)!!, 0.0)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.domain.analytics.EpleyTest"`
Expected: compile failure / unresolved reference `e1rm`.

- [ ] **Step 3: Implement**

```kotlin
package de.simiil.liftlog.domain.analytics

/**
 * Estimated 1-rep-max (Epley) with guardrails (04-analytics-spec §2).
 * reps == 1 → weight; 2..12 → weight·(1 + reps/30); > 12 → null (excluded from e1RM).
 */
fun e1rm(weightKg: Double, reps: Int): Double? = when {
    reps == 1 -> weightKg
    reps in 2..12 -> weightKg * (1 + reps / 30.0)
    else -> null
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.domain.analytics.EpleyTest"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/kotlin/de/simiil/liftlog/domain/analytics/Epley.kt app/src/test/kotlin/de/simiil/liftlog/domain/analytics/EpleyTest.kt
git commit -m "feat(analytics): e1RM (Epley with guardrails) + fixtures

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Per-session metrics

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/analytics/SessionMetrics.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/domain/analytics/SessionMetricsTest.kt`

- [ ] **Step 1: Write the failing test** (worked examples from §5)

```kotlin
package de.simiil.liftlog.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionMetricsTest {
    @Test fun weightedSession_matchesFixture() {
        // 100×5, 102.5×3, 95×8
        val m = sessionMetrics(listOf(SetEntry(100.0, 5), SetEntry(102.5, 3), SetEntry(95.0, 8)))
        assertEquals(102.5, m.topSetKg, 0.001)
        assertEquals(120.3333, m.e1rmKg, 0.001)   // best of the three e1RMs (95×8)
        assertEquals(1567.5, m.volumeKg, 0.001)   // 500 + 307.5 + 760
        assertEquals(8, m.maxReps)
        assertEquals(16, m.totalReps)
    }

    @Test fun highRepExcludedFromE1rm_butCountsElsewhere() {
        // 60×15, 80×1
        val m = sessionMetrics(listOf(SetEntry(60.0, 15), SetEntry(80.0, 1)))
        assertEquals(80.0, m.topSetKg, 0.001)
        assertEquals(80.0, m.e1rmKg, 0.001)        // 15-rep set excluded; r=1 → w
        assertEquals(980.0, m.volumeKg, 0.001)     // 900 + 80
        assertEquals(15, m.maxReps)
        assertEquals(16, m.totalReps)
    }

    @Test fun bodyweightSession_zeroWeightMetrics_repMetricsHold() {
        // 0×12, 0×10
        val m = sessionMetrics(listOf(SetEntry(0.0, 12), SetEntry(0.0, 10)))
        assertEquals(0.0, m.topSetKg, 0.0)
        assertEquals(0.0, m.e1rmKg, 0.0)
        assertEquals(12, m.maxReps)
        assertEquals(22, m.totalReps)
    }

    @Test fun empty_isAllZero() {
        val m = sessionMetrics(emptyList())
        assertEquals(0.0, m.topSetKg, 0.0); assertEquals(0.0, m.e1rmKg, 0.0)
        assertEquals(0.0, m.volumeKg, 0.0); assertEquals(0, m.maxReps); assertEquals(0, m.totalReps)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.domain.analytics.SessionMetricsTest"`
Expected: unresolved references.

- [ ] **Step 3: Implement**

```kotlin
package de.simiil.liftlog.domain.analytics

/** One logged set reduced to the fields analytics needs (weight canonical kg). */
data class SetEntry(val weightKg: Double, val reps: Int)

/** Per-exercise, per-session metrics (04-analytics-spec §1). */
data class SessionMetrics(
    val topSetKg: Double,
    val volumeKg: Double,
    val e1rmKg: Double,
    val maxReps: Int,
    val totalReps: Int,
)

fun sessionMetrics(sets: List<SetEntry>): SessionMetrics {
    var top = 0.0; var volume = 0.0; var bestE1rm = 0.0; var maxReps = 0; var totalReps = 0
    for (s in sets) {
        top = maxOf(top, s.weightKg)
        volume += s.weightKg * s.reps
        totalReps += s.reps
        maxReps = maxOf(maxReps, s.reps)
        e1rm(s.weightKg, s.reps)?.let { bestE1rm = maxOf(bestE1rm, it) }
    }
    return SessionMetrics(top, volume, bestE1rm, maxReps, totalReps)
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.domain.analytics.SessionMetricsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/kotlin/de/simiil/liftlog/domain/analytics/SessionMetrics.kt app/src/test/kotlin/de/simiil/liftlog/domain/analytics/SessionMetricsTest.kt
git commit -m "feat(analytics): per-session metrics (top/volume/e1RM/reps)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Trend (OLS over trailing 90 days)

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/analytics/Trend.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/domain/analytics/TrendTest.kt`

- [ ] **Step 1: Write the failing test** (the four §5 trend fixtures)

```kotlin
package de.simiil.liftlog.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendTest {
    private val day = 86_400_000L
    private val now = 1_000_000_000_000L // fixed "today"

    private fun pointsEndingNow(values: List<Double>, everyDays: Int): List<TrendPoint> {
        val n = values.size
        return values.mapIndexed { i, v -> TrendPoint(now - (n - 1 - i).toLong() * everyDays * day, v) }
    }

    @Test fun risingSeries_isUpAroundFourPercent() {
        // 100 → 104 linearly over 5 weekly points ⇒ +4.0% ↑
        val pts = pointsEndingNow(listOf(100.0, 101.0, 102.0, 103.0, 104.0), everyDays = 7)
        val r = trend(pts, now) as TrendResult.Ok
        assertEquals(TrendDirection.UP, r.direction)
        assertEquals(4.0, r.percent, 0.001)
    }

    @Test fun flatNoisySeries_isFlat() {
        val pts = pointsEndingNow(listOf(100.0, 99.5, 100.5, 99.8, 100.2), everyDays = 7)
        val r = trend(pts, now) as TrendResult.Ok
        assertEquals(TrendDirection.FLAT, r.direction)
        assertTrue(kotlin.math.abs(r.percent) <= 1.0)
    }

    @Test fun twoSessions_isInsufficient() {
        val pts = pointsEndingNow(listOf(100.0, 102.0), everyDays = 7)
        assertEquals(TrendResult.Insufficient, trend(pts, now))
    }

    @Test fun lastPointThirtyDaysOld_isStale() {
        // newest point 30 days ago ⇒ stale, ~4 weeks
        val pts = listOf(
            TrendPoint(now - 44 * day, 100.0),
            TrendPoint(now - 37 * day, 101.0),
            TrendPoint(now - 30 * day, 102.0),
        )
        val r = trend(pts, now) as TrendResult.Stale
        assertEquals(4, r.weeks)
    }

    @Test fun empty_isInsufficient() = assertEquals(TrendResult.Insufficient, trend(emptyList(), now))
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.domain.analytics.TrendTest"`
Expected: unresolved references.

- [ ] **Step 3: Implement** (mirrors `data.js trend()`)

```kotlin
package de.simiil.liftlog.domain.analytics

import kotlin.math.roundToInt

enum class TrendDirection { UP, FLAT, DOWN }

data class TrendPoint(val timeMillis: Long, val value: Double)

sealed interface TrendResult {
    /** Fitted-endpoint percent change over the window + classified direction. */
    data class Ok(val percent: Double, val direction: TrendDirection) : TrendResult
    /** Not trained recently (> 21 days since the last point). */
    data class Stale(val weeks: Int) : TrendResult
    /** Fewer than 3 points in the 90-day window. */
    data object Insufficient : TrendResult
}

private const val DAY = 86_400_000.0

/**
 * OLS trend over the trailing 90 days of the LAST point (04-analytics-spec §3).
 * Stale if the last point is > 21 days before [nowMillis]; insufficient if < 3 points in-window.
 */
fun trend(points: List<TrendPoint>, nowMillis: Long): TrendResult {
    if (points.isEmpty()) return TrendResult.Insufficient
    val lastT = points.maxOf { it.timeMillis }
    val daysSinceLast = (nowMillis - lastT) / DAY
    if (daysSinceLast > 21) return TrendResult.Stale((daysSinceLast / 7).roundToInt())

    val window = points.filter { it.timeMillis >= lastT - 90 * DAY }.sortedBy { it.timeMillis }
    if (window.size < 3) return TrendResult.Insufficient

    val t0 = window.first().timeMillis
    val xs = window.map { (it.timeMillis - t0) / DAY }
    val ys = window.map { it.value }
    val n = xs.size
    val mx = xs.average(); val my = ys.average()
    var num = 0.0; var den = 0.0
    for (i in 0 until n) { num += (xs[i] - mx) * (ys[i] - my); den += (xs[i] - mx) * (xs[i] - mx) }
    val b = if (den != 0.0) num / den else 0.0
    val a = my - b * mx
    fun f(x: Double) = a + b * x
    val fStart = f(xs.first()); val fEnd = f(xs.last())
    val percent = if (fStart != 0.0) (fEnd - fStart) / fStart * 100 else 0.0
    val direction = when {
        percent > 1.0 -> TrendDirection.UP
        percent < -1.0 -> TrendDirection.DOWN
        else -> TrendDirection.FLAT
    }
    return TrendResult.Ok(percent, direction)
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.domain.analytics.TrendTest"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/kotlin/de/simiil/liftlog/domain/analytics/Trend.kt app/src/test/kotlin/de/simiil/liftlog/domain/analytics/TrendTest.kt
git commit -m "feat(analytics): OLS 90-day trend badge (stale/insufficient/ok)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Downsample (ISO-week bucketing)

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/analytics/Downsample.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/domain/analytics/DownsampleTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package de.simiil.liftlog.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownsampleTest {
    private val day = 86_400_000L

    @Test fun underThreshold_returnsInput() {
        val pts = (0 until 10).map { TrendPoint(it.toLong() * day, it.toDouble()) }
        assertEquals(pts, downsample(pts, Aggregation.MAX, maxPoints = 200))
    }

    @Test fun overThreshold_bucketsByWeek_maxAggregation() {
        // 3 points in the same ISO week ⇒ one bucket carrying the MAX value
        val base = 100L * day // arbitrary week
        val pts = listOf(
            TrendPoint(base, 5.0), TrendPoint(base + day, 9.0), TrendPoint(base + 2 * day, 7.0),
        )
        val out = downsample(pts, Aggregation.MAX, maxPoints = 2)
        assertEquals(1, out.size)
        assertEquals(9.0, out.first().value, 0.0)
    }

    @Test fun overThreshold_sumAggregation_sumsBucket() {
        val base = 100L * day
        val pts = listOf(TrendPoint(base, 5.0), TrendPoint(base + day, 9.0), TrendPoint(base + 2 * day, 7.0))
        val out = downsample(pts, Aggregation.SUM, maxPoints = 2)
        assertEquals(1, out.size)
        assertEquals(21.0, out.first().value, 0.0)
    }

    @Test fun bucketsAreChronological() {
        val week = 7L * day
        val pts = (0 until 400).map { TrendPoint(it.toLong() * day, it.toDouble()) }
        val out = downsample(pts, Aggregation.MAX, maxPoints = 200)
        assertTrue(out.size < pts.size)
        assertTrue(out.zipWithNext().all { (a, b) -> a.timeMillis < b.timeMillis })
        assertTrue(week > 0) // sanity
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.domain.analytics.DownsampleTest"`
Expected: unresolved references.

- [ ] **Step 3: Implement**

```kotlin
package de.simiil.liftlog.domain.analytics

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.IsoFields

enum class Aggregation { MAX, SUM }

/**
 * Caps a series at [maxPoints] by bucketing into ISO weeks (04-analytics-spec §7).
 * MAX for e1RM/top-set, SUM for volume. Bucket timestamp = the bucket's last point.
 * Returns the input unchanged when already within the cap.
 */
fun downsample(points: List<TrendPoint>, aggregation: Aggregation, maxPoints: Int = 200): List<TrendPoint> {
    if (points.size <= maxPoints) return points
    val sorted = points.sortedBy { it.timeMillis }
    val buckets = LinkedHashMap<Long, MutableList<TrendPoint>>()
    for (p in sorted) {
        val date = Instant.ofEpochMilli(p.timeMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val key = date.get(IsoFields.WEEK_BASED_YEAR) * 100L + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        buckets.getOrPut(key) { mutableListOf() }.add(p)
    }
    return buckets.values.map { bucket ->
        val value = when (aggregation) {
            Aggregation.MAX -> bucket.maxOf { it.value }
            Aggregation.SUM -> bucket.sumOf { it.value }
        }
        TrendPoint(bucket.last().timeMillis, value)
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.domain.analytics.DownsampleTest"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/kotlin/de/simiil/liftlog/domain/analytics/Downsample.kt app/src/test/kotlin/de/simiil/liftlog/domain/analytics/DownsampleTest.kt
git commit -m "feat(analytics): ISO-week downsampling for large ranges

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: Per-exercise summary assembler (sessions + PR + bodyweight + trend)

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/analytics/ExerciseAnalytics.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/domain/analytics/ExerciseAnalyticsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package de.simiil.liftlog.domain.analytics

import de.simiil.liftlog.domain.model.Equipment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseAnalyticsTest {
    private val day = 86_400_000L
    private val now = 1_000_000_000_000L

    @Test fun noSets_returnsNull() =
        assertNull(summarize(Equipment.BARBELL, emptyList(), now))

    @Test fun singleSessionEver_everyMetricIsPr() {
        val sets = listOf(DatedSet("s1", now - 5 * day, 100.0, 5))
        val s = summarize(Equipment.BARBELL, sets, now)!!
        assertEquals(1, s.sessions.size)
        assertTrue(s.sessions.first().isPr)
        assertFalse(s.bodyweight)
    }

    @Test fun pr_isStrictlyGreaterThanAllEarlier() {
        val sets = listOf(
            DatedSet("s1", now - 20 * day, 100.0, 5),  // e1RM 116.67 — PR (first)
            DatedSet("s2", now - 13 * day, 100.0, 5),  // equal — NOT a PR (not strictly greater)
            DatedSet("s3", now - 6 * day, 105.0, 5),   // higher — PR
        )
        val s = summarize(Equipment.BARBELL, sets, now)!!
        assertTrue(s.sessions[0].isPr)
        assertFalse(s.sessions[1].isPr)
        assertTrue(s.sessions[2].isPr)
    }

    @Test fun bodyweight_swapsToRepMetrics() {
        val sets = listOf(
            DatedSet("s1", now - 12 * day, 0.0, 8),
            DatedSet("s2", now - 5 * day, 0.0, 10),
        )
        val s = summarize(Equipment.BODYWEIGHT, sets, now)!!
        assertTrue(s.bodyweight)
        assertEquals(10.0, s.currentValue, 0.0)          // last session's maxReps as primary
        assertTrue(s.sessions[1].isPr)                    // reps PR
    }

    @Test fun weightedBodyweightEntry_usesWeightMetrics() {
        // BODYWEIGHT equipment but added load ⇒ NOT treated as bodyweight
        val sets = listOf(DatedSet("s1", now - 5 * day, 20.0, 5))
        val s = summarize(Equipment.BODYWEIGHT, sets, now)!!
        assertFalse(s.bodyweight)
    }

    @Test fun sessionsGroupedBySessionId() {
        val sets = listOf(
            DatedSet("s1", now - 6 * day, 100.0, 5),
            DatedSet("s1", now - 6 * day, 100.0, 5),
            DatedSet("s1", now - 6 * day, 95.0, 8),
        )
        val s = summarize(Equipment.BARBELL, sets, now)!!
        assertEquals(1, s.sessions.size)
        assertEquals(3, s.sessions.first().sets.size)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.domain.analytics.ExerciseAnalyticsTest"`
Expected: unresolved references.

- [ ] **Step 3: Implement** (mirrors `data.js exerciseSummary()`)

```kotlin
package de.simiil.liftlog.domain.analytics

import de.simiil.liftlog.domain.model.Equipment

/** One logged set with its session + session start time (1:1 with AnalyticsDao.SetRow). */
data class DatedSet(val sessionId: String, val startedAt: Long, val weightKg: Double, val reps: Int)

/** One session's reduced view + PR flags + the chart "primary" value. */
data class SessionPoint(
    val sessionId: String,
    val timeMillis: Long,
    val sets: List<SetEntry>,
    val metrics: SessionMetrics,
    val primary: Double,
    val isPrE1rm: Boolean,
    val isPrTopSet: Boolean,
    val isPrReps: Boolean,
    val isPr: Boolean,
)

data class ExerciseSummary(
    val bodyweight: Boolean,
    val sessions: List<SessionPoint>,        // chronological
    val trend: TrendResult,
    val currentValue: Double,                // last session's primary
    val lastTrainedAt: Long,
)

/**
 * Builds the per-exercise analytics summary from set-level rows (04-analytics-spec §1–§4).
 * [sets] need not be sorted. Returns null when there are no sets.
 */
fun summarize(equipment: Equipment, sets: List<DatedSet>, nowMillis: Long): ExerciseSummary? {
    if (sets.isEmpty()) return null
    // Group by session, preserving chronological order by the session's startedAt.
    val grouped = sets.groupBy { it.sessionId }
        .map { (id, rows) -> id to rows }
        .sortedBy { (_, rows) -> rows.first().startedAt }

    val bodyweight = equipment == Equipment.BODYWEIGHT && sets.all { it.weightKg == 0.0 }

    var bestE1rm = 0.0; var bestTop = 0.0; var bestReps = 0
    val sessions = grouped.map { (id, rows) ->
        val entries = rows.map { SetEntry(it.weightKg, it.reps) }
        val m = sessionMetrics(entries)
        val prE1rm = m.e1rmKg > bestE1rm; if (prE1rm) bestE1rm = m.e1rmKg
        val prTop = m.topSetKg > bestTop; if (prTop) bestTop = m.topSetKg
        val prReps = m.maxReps > bestReps; if (prReps) bestReps = m.maxReps
        SessionPoint(
            sessionId = id,
            timeMillis = rows.first().startedAt,
            sets = entries,
            metrics = m,
            primary = if (bodyweight) m.maxReps.toDouble() else m.e1rmKg,
            isPrE1rm = prE1rm,
            isPrTopSet = prTop,
            isPrReps = prReps,
            isPr = if (bodyweight) prReps else prE1rm,
        )
    }

    val trendResult = trend(sessions.map { TrendPoint(it.timeMillis, it.primary) }, nowMillis)
    val last = sessions.last()
    return ExerciseSummary(
        bodyweight = bodyweight,
        sessions = sessions,
        trend = trendResult,
        currentValue = last.primary,
        lastTrainedAt = last.timeMillis,
    )
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.domain.analytics.ExerciseAnalyticsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/kotlin/de/simiil/liftlog/domain/analytics/ExerciseAnalytics.kt app/src/test/kotlin/de/simiil/liftlog/domain/analytics/ExerciseAnalyticsTest.kt
git commit -m "feat(analytics): per-exercise summary (PR detection, bodyweight swap, trend)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: DAO queries — week aggregate + trained exercises

**Files:**
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/dao/Relations.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/dao/AnalyticsDao.kt`
- Test: `app/src/androidTest/kotlin/de/simiil/liftlog/data/dao/AnalyticsDaoTest.kt` (extend)

- [ ] **Step 1: Add the projection row**

In `Relations.kt`, after `SetRow`:
```kotlin
/** Analytics browser projection: an exercise that has ≥1 logged set, with its most-recent session start. */
data class TrainedExerciseRow(val exerciseId: String, val lastTrainedAt: Long)
```

- [ ] **Step 2: Write the failing instrumented tests**

Append to `AnalyticsDaoTest.kt` (reuses its `insertFullGraph()` — sessions sA@1000 completed, sB@2000 completed, sC@3000 in-progress; sA also has a soft-deleted set):

```kotlin
    @Test fun observeAllSetsSince_completedOnly_excludesInProgressAndDeleted() = runTest {
        insertFullGraph()
        analyticsDao.observeAllSetsSince(fromMillis = 0L).test {
            val rows = awaitItem()
            // sA live + sB live only (sC in-progress excluded; sA dead set excluded)
            assertEquals(2, rows.size)
            assertTrue(rows.none { it.weightKg == 70.0 && it.reps == 5 })  // dead
            assertTrue(rows.none { it.weightKg == 90.0 })                  // sC in-progress
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun observeAllSetsSince_fromMillis_filtersEarlySession() = runTest {
        insertFullGraph()
        analyticsDao.observeAllSetsSince(fromMillis = 1500L).test {
            val rows = awaitItem()
            assertEquals(1, rows.size)
            assertEquals("sB", rows[0].sessionId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun observeTrainedExercises_groupsAndTakesMaxStartedAt() = runTest {
        insertFullGraph()
        analyticsDao.observeTrainedExercises().test {
            val rows = awaitItem()
            assertEquals(1, rows.size)
            assertEquals("ex1", rows[0].exerciseId)
            // MAX over completed sessions with live sets: sB (2000) > sA (1000); sC excluded
            assertEquals(2000L, rows[0].lastTrainedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.data.dao.AnalyticsDaoTest`
Expected: compile failure (unresolved `observeAllSetsSince` / `observeTrainedExercises`).

- [ ] **Step 4: Implement the queries**

In `AnalyticsDao.kt`, add inside the `@Dao interface` (and import `TrainedExerciseRow`):
```kotlin
    @Query(
        """SELECT s.id AS sessionId, s.startedAt AS startedAt, ls.weightKg AS weightKg, ls.reps AS reps
           FROM logged_sets ls
           JOIN session_exercises se ON se.id = ls.sessionExerciseId AND se.deletedAt IS NULL
           JOIN sessions s          ON s.id = se.sessionId          AND s.deletedAt IS NULL
           WHERE ls.deletedAt IS NULL
             AND s.startedAt >= :fromMillis AND s.endedAt IS NOT NULL
           ORDER BY s.startedAt"""
    )
    fun observeAllSetsSince(fromMillis: Long): Flow<List<SetRow>>

    @Query(
        """SELECT se.exerciseId AS exerciseId, MAX(s.startedAt) AS lastTrainedAt
           FROM logged_sets ls
           JOIN session_exercises se ON se.id = ls.sessionExerciseId AND se.deletedAt IS NULL
           JOIN sessions s          ON s.id = se.sessionId          AND s.deletedAt IS NULL
           WHERE ls.deletedAt IS NULL AND s.endedAt IS NOT NULL
           GROUP BY se.exerciseId
           ORDER BY lastTrainedAt DESC"""
    )
    fun observeTrainedExercises(): Flow<List<TrainedExerciseRow>>
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.data.dao.AnalyticsDaoTest`
Expected: all `AnalyticsDaoTest` tests PASS (existing + 3 new).

- [ ] **Step 6: Commit**
```bash
git add app/src/main/kotlin/de/simiil/liftlog/data/dao/Relations.kt app/src/main/kotlin/de/simiil/liftlog/data/dao/AnalyticsDao.kt app/src/androidTest/kotlin/de/simiil/liftlog/data/dao/AnalyticsDaoTest.kt
git commit -m "feat(analytics): DAO queries for week aggregate + trained-exercise list

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: AnalyticsRepository (week summary, trained list, per-exercise summary)

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/repository/AnalyticsRepository.kt`
- Create: `app/src/main/kotlin/de/simiil/liftlog/data/repository/AnalyticsRepositoryImpl.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/di/RepositoryModule.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/data/repository/AnalyticsRepositoryImplTest.kt`

**Note:** `Clock` is already DI-provided (`DatabaseModule:39`, `Clock.systemUTC()`). Inject it; week boundaries are computed in the clock's zone (UTC) via `LocalDate.now(clock)` — acceptable for v1.

- [ ] **Step 1: Define the interface + models**

`AnalyticsRepository.kt`:
```kotlin
package de.simiil.liftlog.domain.repository

import de.simiil.liftlog.domain.analytics.ExerciseSummary
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import kotlinx.coroutines.flow.Flow

interface AnalyticsRepository {
    fun observeWeekSummary(): Flow<WeekSummary>
    fun observeTrainedExercises(): Flow<List<TrainedExercise>>
    fun observeExerciseSummary(exerciseId: String): Flow<ExerciseSummary?>
}

/** Analytics browser header card (04-analytics-spec §6, chart 3). */
data class WeekSummary(val sessions: Int, val sets: Int, val volumeKg: Double, val prevVolumeKg: Double)

/** A browser list entry: an exercise with ≥1 logged set + identity for display/search. */
data class TrainedExercise(
    val id: String,
    val name: String,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val lastTrainedAt: Long,
)
```

- [ ] **Step 2: Write the failing test** (fakes; fixed `Clock`)

```kotlin
package de.simiil.liftlog.data.repository

import app.cash.turbine.test
import de.simiil.liftlog.data.dao.SetRow
import de.simiil.liftlog.data.dao.TrainedExerciseRow
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AnalyticsRepositoryImplTest {
    private val day = 86_400_000L
    // Fixed "now" = Thursday 2026-06-04 12:00 UTC (ISO week starts Monday 2026-06-01).
    private val now = Instant.parse("2026-06-04T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val nowMs = now.toEpochMilli()

    private fun ex(id: String, eq: Equipment = Equipment.BARBELL) = Exercise(
        id = id, name = "Ex $id", muscleGroup = MuscleGroup.CHEST, equipment = eq,
        isBuiltIn = true, isHidden = false,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH, deletedAt = null,
    )

    private class FakeAnalyticsDao(
        val allSets: List<SetRow>,
        val trained: List<TrainedExerciseRow>,
        val perExercise: Map<String, List<SetRow>>,
    ) {
        fun observeAllSetsSince(fromMillis: Long): Flow<List<SetRow>> =
            flowOf(allSets.filter { it.startedAt >= fromMillis })
        fun observeTrainedExercises(): Flow<List<TrainedExerciseRow>> = flowOf(trained)
        fun observeSetsForExercise(id: String, fromMillis: Long): Flow<List<SetRow>> =
            flowOf(perExercise[id].orEmpty())
    }

    private class FakeExerciseRepository(private val list: List<Exercise>) : ExerciseRepository {
        override fun observeAll() = flowOf(list)
        override fun observeVisible() = flowOf(list)
        override suspend fun createCustom(name: String, muscleGroup: MuscleGroup, equipment: Equipment) = error("unused")
        override suspend fun setHidden(id: String, hidden: Boolean) {}
        override fun observeRecentlyUsedIds() = flowOf(emptyList<String>())
    }

    @Test fun weekSummary_splitsThisVsPreviousWeek() = runTest {
        // this week (after Mon 2026-06-01): one session, 2 sets, volume 100·5+100·5=1000
        // last week (Mon 2026-05-25..): one session, volume 50·10=500
        val thisWeek = nowMs - 1 * day
        val lastWeek = nowMs - 8 * day
        val dao = FakeAnalyticsDao(
            allSets = listOf(
                SetRow("a", thisWeek, 100.0, 5), SetRow("a", thisWeek, 100.0, 5),
                SetRow("b", lastWeek, 50.0, 10),
            ),
            trained = emptyList(), perExercise = emptyMap(),
        )
        val repo = AnalyticsRepositoryImpl(FakeAdapter(dao), FakeExerciseRepository(emptyList()), clock)
        repo.observeWeekSummary().test {
            val w = awaitItem()
            assertEquals(1, w.sessions)
            assertEquals(2, w.sets)
            assertEquals(1000.0, w.volumeKg, 0.001)
            assertEquals(500.0, w.prevVolumeKg, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun trainedExercises_joinsNameAndSortsByRecentDesc() = runTest {
        val dao = FakeAnalyticsDao(
            allSets = emptyList(),
            trained = listOf(TrainedExerciseRow("e1", 1000L), TrainedExerciseRow("e2", 2000L)),
            perExercise = emptyMap(),
        )
        val repo = AnalyticsRepositoryImpl(FakeAdapter(dao), FakeExerciseRepository(listOf(ex("e1"), ex("e2"))), clock)
        repo.observeTrainedExercises().test {
            val list = awaitItem()
            assertEquals(listOf("e2", "e1"), list.map { it.id })  // most-recent first
            assertEquals("Ex e2", list.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun exerciseSummary_assemblesFromRows() = runTest {
        val dao = FakeAnalyticsDao(
            allSets = emptyList(), trained = emptyList(),
            perExercise = mapOf("e1" to listOf(SetRow("s1", nowMs - 5 * day, 100.0, 5))),
        )
        val repo = AnalyticsRepositoryImpl(FakeAdapter(dao), FakeExerciseRepository(listOf(ex("e1"))), clock)
        repo.observeExerciseSummary("e1").test {
            val s = awaitItem()!!
            assertEquals(1, s.sessions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

> **Implementer note:** `FakeAdapter` is a thin shim so the fake DAO satisfies the real `AnalyticsDao` type. Simplest path: make `AnalyticsRepositoryImpl`'s constructor take the real `AnalyticsDao`, and have `FakeAnalyticsDao` *implement* `AnalyticsDao` directly (override the 3 used methods; the interface has exactly `observeSetsForExercise`, `observeAllSetsSince`, `observeTrainedExercises`). Delete the `FakeAdapter` indirection and pass `FakeAnalyticsDao` where `AnalyticsDao` is expected. Adjust the test accordingly — the three method signatures already match.

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.data.repository.AnalyticsRepositoryImplTest"`
Expected: unresolved `AnalyticsRepositoryImpl`.

- [ ] **Step 4: Implement**

`AnalyticsRepositoryImpl.kt`:
```kotlin
package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.dao.AnalyticsDao
import de.simiil.liftlog.domain.analytics.DatedSet
import de.simiil.liftlog.domain.analytics.ExerciseSummary
import de.simiil.liftlog.domain.analytics.summarize
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.TrainedExercise
import de.simiil.liftlog.domain.repository.WeekSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepositoryImpl @Inject constructor(
    private val analyticsDao: AnalyticsDao,
    private val exerciseRepository: ExerciseRepository,
    private val clock: Clock,
) : AnalyticsRepository {

    override fun observeWeekSummary(): Flow<WeekSummary> {
        val zone: ZoneId = clock.zone
        val thisWeekStart = LocalDate.now(clock).with(DayOfWeek.MONDAY)
        val thisWeekStartMs = thisWeekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val prevWeekStartMs = thisWeekStart.minusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return analyticsDao.observeAllSetsSince(prevWeekStartMs).map { rows ->
            val thisWeek = rows.filter { it.startedAt >= thisWeekStartMs }
            val prevWeek = rows.filter { it.startedAt < thisWeekStartMs }
            WeekSummary(
                sessions = thisWeek.map { it.sessionId }.distinct().size,
                sets = thisWeek.size,
                volumeKg = thisWeek.sumOf { it.weightKg * it.reps },
                prevVolumeKg = prevWeek.sumOf { it.weightKg * it.reps },
            )
        }
    }

    override fun observeTrainedExercises(): Flow<List<TrainedExercise>> =
        combine(analyticsDao.observeTrainedExercises(), exerciseRepository.observeAll()) { rows, exercises ->
            val byId = exercises.associateBy { it.id }
            rows.mapNotNull { r ->
                byId[r.exerciseId]?.let { ex ->
                    TrainedExercise(ex.id, ex.name, ex.muscleGroup, ex.equipment, r.lastTrainedAt)
                }
            }.sortedByDescending { it.lastTrainedAt }
        }

    override fun observeExerciseSummary(exerciseId: String): Flow<ExerciseSummary?> =
        combine(
            analyticsDao.observeSetsForExercise(exerciseId, 0L),
            exerciseRepository.observeAll(),
        ) { rows, exercises ->
            val equipment = exercises.firstOrNull { it.id == exerciseId }?.equipment ?: return@combine null
            summarize(equipment, rows.map { DatedSet(it.sessionId, it.startedAt, it.weightKg, it.reps) }, clock.millis())
        }
}
```

- [ ] **Step 5: Bind it in Hilt**

In `RepositoryModule.kt`, add the import + binding:
```kotlin
import de.simiil.liftlog.data.repository.AnalyticsRepositoryImpl
import de.simiil.liftlog.domain.repository.AnalyticsRepository
```
```kotlin
    @Binds abstract fun bindAnalyticsRepository(impl: AnalyticsRepositoryImpl): AnalyticsRepository
```

- [ ] **Step 6: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.data.repository.AnalyticsRepositoryImplTest"`
Expected: PASS.

- [ ] **Step 7: Commit**
```bash
git add app/src/main/kotlin/de/simiil/liftlog/domain/repository/AnalyticsRepository.kt app/src/main/kotlin/de/simiil/liftlog/data/repository/AnalyticsRepositoryImpl.kt app/src/main/kotlin/de/simiil/liftlog/di/RepositoryModule.kt app/src/test/kotlin/de/simiil/liftlog/data/repository/AnalyticsRepositoryImplTest.kt
git commit -m "feat(analytics): AnalyticsRepository (week summary, trained list, summary)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 9: Extended theme colors (success green; PR accent uses scheme tertiary)

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/ui/theme/ExtendedColors.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/theme/Theme.kt`

- [ ] **Step 1: Create the extended-colors holder + CompositionLocal**

```kotlin
package de.simiil.liftlog.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors beyond the M3 baseline scheme. The baseline has `error` (down/red) but
 * no green, so the up-trend badge needs an explicit `success`. The PR accent is NOT here —
 * it reuses the scheme's `tertiary` so it harmonizes with the seed (design decision 2026-06-09).
 */
data class LiftLogExtendedColors(val success: Color)

private val LightExtended = LiftLogExtendedColors(success = Color(0xFF1E8E3E)) // green 600-ish
private val DarkExtended = LiftLogExtendedColors(success = Color(0xFF81C995))  // green 300-ish

internal fun extendedColorsFor(darkTheme: Boolean) = if (darkTheme) DarkExtended else LightExtended

val LocalLiftLogColors = staticCompositionLocalOf { LightExtended }
```

- [ ] **Step 2: Provide it in the theme**

In `Theme.kt`, wrap the `MaterialTheme` content with the provider. Replace the final line:
```kotlin
    MaterialTheme(colorScheme = colorScheme, content = content)
```
with:
```kotlin
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLiftLogColors provides extendedColorsFor(darkTheme),
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/kotlin/de/simiil/liftlog/ui/theme/ExtendedColors.kt app/src/main/kotlin/de/simiil/liftlog/ui/theme/Theme.kt
git commit -m "feat(theme): extended success color; PR accent reuses scheme tertiary

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 10: TrendBadge component

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/ui/analytics/TrendBadge.kt`
- Strings: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add strings** (in `<resources>`):
```xml
    <string name="trend_up">↑ %1$s%%</string>
    <string name="trend_down">↓ %1$s%%</string>
    <string name="trend_flat">→ %1$s%%</string>
    <string name="trend_stale">not trained in %1$d wk</string>
    <string name="trend_insufficient">need 3+ sessions</string>
```

- [ ] **Step 2: Implement the badge** (color + glyph; a11y §7)

```kotlin
package de.simiil.liftlog.ui.analytics

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.analytics.TrendDirection
import de.simiil.liftlog.domain.analytics.TrendResult
import de.simiil.liftlog.ui.theme.LocalLiftLogColors
import kotlin.math.abs
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor

@Composable
fun TrendBadge(trend: TrendResult, modifier: Modifier = Modifier, large: Boolean = false) {
    val success = LocalLiftLogColors.current.success
    val size: TextUnit = if (large) 15.sp else 13.sp
    val (text, color, weight) = when (trend) {
        is TrendResult.Stale ->
            Triple(stringResource(R.string.trend_stale, trend.weeks), MaterialTheme.colorScheme.onSurfaceVariant, FontWeight.Medium)
        TrendResult.Insufficient ->
            Triple(stringResource(R.string.trend_insufficient), MaterialTheme.colorScheme.onSurfaceVariant, FontWeight.Medium)
        is TrendResult.Ok -> {
            val pct = formatPercent(trend.percent)
            when (trend.direction) {
                TrendDirection.UP -> Triple(stringResource(R.string.trend_up, pct), success, FontWeight.Bold)
                TrendDirection.DOWN -> Triple(stringResource(R.string.trend_down, pct), MaterialTheme.colorScheme.error, FontWeight.Bold)
                TrendDirection.FLAT -> Triple(stringResource(R.string.trend_flat, pct), MaterialTheme.colorScheme.onSurfaceVariant, FontWeight.Bold)
            }
        }
    }
    Text(text = text, color = color, fontWeight = weight, fontSize = size, modifier = modifier)
}

/** "+4.2" / "-2.1" — one decimal, explicit sign for non-negative. */
private fun formatPercent(percent: Double): String {
    val sign = if (percent >= 0) "+" else ""
    return sign + (kotlin.math.round(percent * 10) / 10.0).let { if (it == it.toLong().toDouble()) "${it.toLong()}.0" else it.toString() }
}
```

> **Implementer note:** `formatPercent` must yield e.g. `+4.0`, `-2.1`. If the inline rounding reads poorly, use `String.format(java.util.Locale.US, "%+.1f", percent)` instead (drop the manual sign). Keep one decimal place. The `abs`/`LocalContentColor`/`CompositionLocalProvider`/`Color` imports are only needed if you extend styling — remove unused imports before committing (lint will flag them).

- [ ] **Step 3: Verify it compiles + lint is clean**

Run: `./gradlew assembleDebug lint`
Expected: BUILD SUCCESSFUL; no unused-import / UnusedResources lint errors.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/kotlin/de/simiil/liftlog/ui/analytics/TrendBadge.kt app/src/main/res/values/strings.xml
git commit -m "feat(analytics): TrendBadge (glyph + semantic color)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 11: Chart wrappers (Vico) — ProgressLineChart + Sparkline

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/ui/components/charts/ProgressLineChart.kt`
- Create: `app/src/main/kotlin/de/simiil/liftlog/ui/components/charts/Sparkline.kt`

> **Vico API caveat:** Vico's Compose API differs across 2.x releases. The code below targets the canonical Vico 2.x `CartesianChartHost` API. **Verify imports against the resolved version** (`./gradlew :app:dependencies | grep vico`); if a symbol is missing, consult the artifact's classes — the *shape* (model producer + `lineSeries` + `CartesianChartHost`) is stable even when package paths move. Keep config minimal per the design decision (simple line now; PR markers / zoomed-Y are nice-to-have, defer if fiddly).

- [ ] **Step 1: Implement ProgressLineChart**

```kotlin
package de.simiil.liftlog.ui.components.charts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

/** A single data point: x = session time (epoch ms), y = the selected metric value. */
data class ChartPoint(val x: Float, val y: Float, val isPr: Boolean)

/**
 * Progress line chart (04-analytics-spec §6, chart 2). Simple Vico line per the M4 decision.
 * Straight segments between session points (no interpolation/binning of gaps).
 */
@Composable
fun ProgressLineChart(points: List<ChartPoint>, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineSeries { series(points.map { it.x }, points.map { it.y }) }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis(),
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(188.dp),
    )
}
```

- [ ] **Step 2: Implement Sparkline**

```kotlin
package de.simiil.liftlog.ui.components.charts

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

/** Tiny 90-day e1RM sparkline (no axes). Renders only with ≥2 points. */
@Composable
fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        modelProducer.runTransaction { lineSeries { series(values) } }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(rememberLineCartesianLayer()),
        modelProducer = modelProducer,
        modifier = modifier.width(120.dp).height(34.dp),
    )
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (If Vico imports don't resolve, fix per the API caveat above, then re-run.)

- [ ] **Step 4: Commit**
```bash
git add app/src/main/kotlin/de/simiil/liftlog/ui/components/charts/
git commit -m "feat(analytics): Vico chart wrappers (progress line + sparkline)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 12: Analytics browser (ViewModel + Screen)

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/ui/analytics/AnalyticsBrowserViewModel.kt`
- Replace: `app/src/main/kotlin/de/simiil/liftlog/ui/analytics/AnalyticsScreen.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/ui/analytics/AnalyticsBrowserViewModelTest.kt`
- Strings: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Write the failing VM test**

```kotlin
package de.simiil.liftlog.ui.analytics

import app.cash.turbine.test
import de.simiil.liftlog.domain.analytics.ExerciseSummary
import de.simiil.liftlog.domain.analytics.TrendResult
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.TrainedExercise
import de.simiil.liftlog.domain.repository.WeekSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsBrowserViewModelTest {
    private fun trained(id: String, name: String) =
        TrainedExercise(id, name, MuscleGroup.CHEST, Equipment.BARBELL, 0L)

    private class FakeRepo(val list: List<TrainedExercise>) : AnalyticsRepository {
        override fun observeWeekSummary(): Flow<WeekSummary> = flowOf(WeekSummary(3, 86, 14250.0, 12980.0))
        override fun observeTrainedExercises(): Flow<List<TrainedExercise>> = flowOf(list)
        override fun observeExerciseSummary(exerciseId: String): Flow<ExerciseSummary?> =
            flowOf(ExerciseSummary(false, emptyList(), TrendResult.Insufficient, 0.0, 0L))
    }

    @Test fun search_filtersByNameCaseInsensitive() = runTest {
        val vm = AnalyticsBrowserViewModel(FakeRepo(listOf(trained("a", "Bench Press"), trained("b", "Squat"))))
        vm.uiState.test {
            awaitItem() // initial
            vm.onQueryChange("squ")
            val s = awaitItem()
            assertEquals(listOf("b"), s.exercises.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun weekSummary_isExposed() = runTest {
        val vm = AnalyticsBrowserViewModel(FakeRepo(emptyList()))
        vm.uiState.test {
            // collect until week summary populates
            var s = awaitItem()
            while (s.week == null) s = awaitItem()
            assertEquals(86, s.week!!.sets)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.ui.analytics.AnalyticsBrowserViewModelTest"`
Expected: unresolved `AnalyticsBrowserViewModel`.

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package de.simiil.liftlog.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.analytics.ExerciseSummary
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.TrainedExercise
import de.simiil.liftlog.domain.repository.WeekSummary
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AnalyticsBrowserUiState(
    val week: WeekSummary? = null,
    val query: String = "",
    val exercises: List<TrainedExercise> = emptyList(),
)

@HiltViewModel
class AnalyticsBrowserViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<AnalyticsBrowserUiState> = combine(
        analyticsRepository.observeWeekSummary(),
        analyticsRepository.observeTrainedExercises(),
        query,
    ) { week, exercises, q ->
        AnalyticsBrowserUiState(
            week = week,
            query = q,
            exercises = if (q.isBlank()) exercises
                else exercises.filter { it.name.contains(q, ignoreCase = true) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsBrowserUiState())

    fun onQueryChange(value: String) { query.value = value }

    /** Per-row summary flow — collected lazily by each visible browser row (04-analytics-spec §7). */
    fun summary(exerciseId: String): Flow<ExerciseSummary?> =
        analyticsRepository.observeExerciseSummary(exerciseId)
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.ui.analytics.AnalyticsBrowserViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Add browser strings**
```xml
    <string name="analytics_week_head">This week</string>
    <string name="analytics_stat_sessions">sessions</string>
    <string name="analytics_stat_sets">sets</string>
    <string name="analytics_stat_volume">volume</string>
    <string name="analytics_week_delta">%1$s%% volume vs last week</string>
    <string name="analytics_search_hint">Search exercises</string>
    <string name="analytics_empty">Log a session to see progress</string>
    <string name="analytics_e1rm_value">e1RM %1$s %2$s</string>
    <string name="analytics_reps_value">%1$d reps</string>
```

- [ ] **Step 6: Implement the browser Screen** (replace the placeholder body)

`AnalyticsScreen.kt` — full-file replacement. Renders: week card (`surfaceContainerHigh`, radius 22), search bar, and a `LazyColumn` of rows where **each row collects its own summary flow** (lazy). Uses `Weights.format` + `Weights.label` (inject unit via `SettingsRepository`? No — the VM doesn't expose unit; keep kg display by passing `WeightUnit` from a `collectAsStateWithLifecycle` of a new small flow). To avoid scope creep, the browser shows the user's unit by collecting `settingsRepository.weightUnit` **inside the VM** and exposing it on the state.

Add `weightUnit` to the state + combine (extend `AnalyticsBrowserViewModel`):
```kotlin
// constructor gains: private val settingsRepository: SettingsRepository
// add to combine (use combine(a,b,c,d){...} — 4 flows):
//   settingsRepository.weightUnit
// state gains: val unit: WeightUnit = WeightUnit.KG
```
> **Implementer note:** Update `AnalyticsBrowserViewModelTest`'s fake to also satisfy the added `SettingsRepository` constructor param (a fake returning `flowOf(WeightUnit.KG)`), and assert `s.unit == WeightUnit.KG`. Re-run the test after wiring.

Screen:
```kotlin
package de.simiil.liftlog.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.analytics.TrendDirection
import de.simiil.liftlog.domain.analytics.TrendResult
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.TrainedExercise
import de.simiil.liftlog.domain.units.Weights
import de.simiil.liftlog.ui.components.charts.Sparkline

@Composable
fun AnalyticsScreen(
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnalyticsBrowserViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Analytics", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 12.dp))
        ui.week?.let { WeekCard(it, ui.unit) }
        Spacer(Modifier.height(12.dp))
        SearchBar(ui.query, viewModel::onQueryChange)
        if (ui.exercises.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.analytics_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn {
                items(ui.exercises, key = { it.id }) { ex ->
                    ExerciseRow(ex, ui.unit, viewModel, onOpenExercise)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun WeekCard(week: de.simiil.liftlog.domain.repository.WeekSummary, unit: WeightUnit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(stringResource(R.string.analytics_week_head), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(week.sessions.toString(), stringResource(R.string.analytics_stat_sessions), Modifier.weight(1f))
                Stat(week.sets.toString(), stringResource(R.string.analytics_stat_sets), Modifier.weight(1f))
                Stat("%.1ft".format(week.volumeKg / 1000), stringResource(R.string.analytics_stat_volume), Modifier.weight(1f))
            }
            val delta = if (week.prevVolumeKg > 0)
                ((week.volumeKg - week.prevVolumeKg) / week.prevVolumeKg * 100).toInt() else 0
            val sign = if (delta >= 0) "+" else ""
            Text(stringResource(R.string.analytics_week_delta, "$sign$delta"),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (delta >= 0) de.simiil.liftlog.ui.theme.LocalLiftLogColors.current.success
                        else MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(100.dp)) {
        Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(stringResource(R.string.analytics_search_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(value = query, onValueChange = onChange,
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface))
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    ex: TrainedExercise,
    unit: WeightUnit,
    viewModel: AnalyticsBrowserViewModel,
    onOpen: (String) -> Unit,
) {
    val summary by viewModel.summary(ex.id).collectAsStateWithLifecycle(initialValue = null)
    Row(Modifier.fillMaxWidth().clickableRow { onOpen(ex.id) }.padding(vertical = 16.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(ex.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
            val s = summary
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (s != null && s.trend !is TrendResult.Stale) {
                    val metric = if (s.bodyweight) stringResource(R.string.analytics_reps_value, s.currentValue.toInt())
                        else stringResource(R.string.analytics_e1rm_value, Weights.format(s.currentValue, unit), Weights.label(unit))
                    Text(metric, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (s != null) TrendBadge(s.trend)
            }
        }
        val s = summary
        if (s != null && s.trend !is TrendResult.Stale && s.sessions.size >= 2) {
            val downColor = (s.trend as? TrendResult.Ok)?.direction == TrendDirection.DOWN
            Sparkline(values = s.sessions.map { it.primary.toFloat() })
        }
    }
}

// Tiny helper to keep clickable import local.
@Composable
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable { onClick() })
```

> **Implementer note:** `downColor` is computed but the simple `Sparkline` ignores color in Task 11; either thread a color param into `Sparkline` or drop the unused `val` (lint will flag it). Prefer dropping it now (refine color later, per the M4 decision).

- [ ] **Step 7: Run unit tests + build**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.ui.analytics.AnalyticsBrowserViewModelTest"` then `./gradlew assembleDebug lint`
Expected: tests PASS; BUILD SUCCESSFUL; lint clean. (The nav host still calls `AnalyticsScreen()` with no arg — Task 14 fixes the signature; until then `assembleDebug` may fail on that call site. If so, do Task 14's nav edit in the same step, or temporarily default `onOpenExercise = {}` — but prefer completing Task 14 next.)

- [ ] **Step 8: Commit**
```bash
git add app/src/main/kotlin/de/simiil/liftlog/ui/analytics/AnalyticsBrowserViewModel.kt app/src/main/kotlin/de/simiil/liftlog/ui/analytics/AnalyticsScreen.kt app/src/test/kotlin/de/simiil/liftlog/ui/analytics/AnalyticsBrowserViewModelTest.kt app/src/main/res/values/strings.xml
git commit -m "feat(analytics): browser screen (week card, search, lazy trend rows)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 13: Exercise detail (ViewModel + Screen)

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/ui/analytics/ExerciseDetailViewModel.kt`
- Create: `app/src/main/kotlin/de/simiil/liftlog/ui/analytics/ExerciseDetailScreen.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/ui/analytics/ExerciseDetailViewModelTest.kt`
- Strings: `app/src/main/res/values/strings.xml`

The detail VM holds `exerciseId` (from `SavedStateHandle`), the summary flow, and selected `metric`/`range` state; it computes the chart points (range filter → `<2` fallback → downsample) and recent-5 list.

- [ ] **Step 1: Write the failing VM test**

```kotlin
package de.simiil.liftlog.ui.analytics

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import de.simiil.liftlog.domain.analytics.*
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.TrainedExercise
import de.simiil.liftlog.domain.repository.WeekSummary
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseDetailViewModelTest {
    private val day = 86_400_000L
    private val now = 1_000_000_000_000L

    private fun summaryWith(n: Int, bodyweight: Boolean = false): ExerciseSummary {
        val sessions = (0 until n).map {
            val m = sessionMetrics(listOf(SetEntry(100.0 + it, 5)))
            SessionPoint("s$it", now - (n - it).toLong() * day, listOf(SetEntry(100.0 + it, 5)), m,
                if (bodyweight) m.maxReps.toDouble() else m.e1rmKg, it == n - 1, it == n - 1, it == n - 1, it == n - 1)
        }
        return ExerciseSummary(bodyweight, sessions, TrendResult.Insufficient, sessions.last().primary, sessions.last().timeMillis)
    }

    private fun vm(summary: ExerciseSummary?, name: String = "Bench") = ExerciseDetailViewModel(
        SavedStateHandle(mapOf("exerciseId" to "e1")),
        object : AnalyticsRepository {
            override fun observeWeekSummary() = flowOf(WeekSummary(0, 0, 0.0, 0.0))
            override fun observeTrainedExercises() = flowOf(listOf(TrainedExercise("e1", name, de.simiil.liftlog.domain.model.MuscleGroup.CHEST, Equipment.BARBELL, 0L)))
            override fun observeExerciseSummary(exerciseId: String) = flowOf(summary)
        },
        FakeSettings(),
    )

    private class FakeSettings : de.simiil.liftlog.domain.repository.SettingsRepository {
        override val themePreference = flowOf(de.simiil.liftlog.domain.model.ThemePreference.SYSTEM)
        override val weightUnit = flowOf(WeightUnit.KG)
        override suspend fun setThemePreference(preference: de.simiil.liftlog.domain.model.ThemePreference) {}
    }

    @Test fun weightedExercise_offersWeightMetrics() = runTest {
        vm(summaryWith(5)).uiState.test {
            var s = awaitItem(); while (s.summary == null) s = awaitItem()
            assertEquals(listOf(Metric.E1RM, Metric.TOP_SET, Metric.VOLUME), s.metrics)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun bodyweightExercise_offersRepMetrics() = runTest {
        vm(summaryWith(5, bodyweight = true)).uiState.test {
            var s = awaitItem(); while (s.summary == null) s = awaitItem()
            assertEquals(listOf(Metric.MAX_REPS, Metric.TOTAL_REPS), s.metrics)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun fewerThanTwoPointsInRange_fallsBackToLastTwo() = runTest {
        val v = vm(summaryWith(5))
        v.onRangeChange(Range.D30) // 30d window may include <2; chart still shows ≥2
        v.uiState.test {
            var s = awaitItem(); while (s.summary == null) s = awaitItem()
            assertTrue(s.chartPoints.size >= 2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun emptySummary_marksInsufficient() = runTest {
        vm(summaryWith(1)).uiState.test {
            var s = awaitItem(); while (s.summary == null) s = awaitItem()
            assertTrue(s.chartPoints.size >= 2 || s.notEnoughData)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.ui.analytics.ExerciseDetailViewModelTest"`
Expected: unresolved `ExerciseDetailViewModel`, `Metric`, `Range`.

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package de.simiil.liftlog.ui.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.analytics.*
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.SettingsRepository
import de.simiil.liftlog.ui.components.charts.ChartPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.*

enum class Metric { E1RM, TOP_SET, VOLUME, MAX_REPS, TOTAL_REPS }
enum class Range(val days: Long) { D30(30), D90(90), Y1(365), ALL(99_999) }

data class ExerciseDetailUiState(
    val name: String = "",
    val summary: ExerciseSummary? = null,
    val metrics: List<Metric> = emptyList(),
    val selectedMetric: Metric = Metric.E1RM,
    val selectedRange: Range = Range.D90,
    val chartPoints: List<ChartPoint> = emptyList(),
    val currentValueLabel: String = "",
    val recent: List<RecentSessionRow> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
    val notEnoughData: Boolean = false,
)

data class RecentSessionRow(val dateMillis: Long, val summary: String, val isPr: Boolean)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    analyticsRepository: AnalyticsRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val exerciseId: String = checkNotNull(savedStateHandle["exerciseId"])
    private val selectedMetric = MutableStateFlow<Metric?>(null)
    private val selectedRange = MutableStateFlow(Range.D90)

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        analyticsRepository.observeExerciseSummary(exerciseId),
        analyticsRepository.observeTrainedExercises(),
        selectedMetric,
        selectedRange,
        settingsRepository.weightUnit,
    ) { summary, trained, metricOrNull, range, unit ->
        val name = trained.firstOrNull { it.id == exerciseId }?.name ?: ""
        if (summary == null) return@combine ExerciseDetailUiState(name = name, notEnoughData = true, unit = unit)
        val metrics = if (summary.bodyweight) listOf(Metric.MAX_REPS, Metric.TOTAL_REPS)
            else listOf(Metric.E1RM, Metric.TOP_SET, Metric.VOLUME)
        val metric = metricOrNull?.takeIf { it in metrics } ?: metrics.first()

        val cutoff = nowFallbackCutoff(summary, range)
        var inRange = summary.sessions.filter { it.timeMillis >= cutoff }
        if (inRange.size < 2) inRange = summary.sessions.takeLast(2)

        val zeroBased = metric == Metric.VOLUME || metric == Metric.TOTAL_REPS
        val agg = if (zeroBased) Aggregation.SUM else Aggregation.MAX
        val raw = inRange.map { TrendPoint(it.timeMillis, valueOf(it, metric)) }
        val pts = downsample(raw, agg).let { ds ->
            // keep PR flags only when not downsampled (1:1 with sessions)
            if (ds.size == inRange.size) inRange.map { ChartPoint(it.timeMillis.toFloat(), valueOf(it, metric).toFloat(), prFor(it, metric)) }
            else ds.map { ChartPoint(it.timeMillis.toFloat(), it.value.toFloat(), false) }
        }

        val last = summary.sessions.last()
        ExerciseDetailUiState(
            name = name, summary = summary, metrics = metrics,
            selectedMetric = metric, selectedRange = range,
            chartPoints = pts,
            currentValueLabel = label(valueOf(last, metric), metric, unit),
            recent = summary.sessions.takeLast(5).reversed().map { sp ->
                RecentSessionRow(sp.timeMillis, recentSummary(sp, summary.bodyweight, unit), sp.isPr)
            },
            unit = unit,
            notEnoughData = summary.sessions.size < 2,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    fun onMetricChange(m: Metric) { selectedMetric.value = m }
    fun onRangeChange(r: Range) { selectedRange.value = r }

    private fun nowFallbackCutoff(summary: ExerciseSummary, range: Range): Long {
        val newest = summary.sessions.maxOf { it.timeMillis }
        return newest - range.days * 86_400_000L
    }
    private fun valueOf(s: SessionPoint, m: Metric): Double = when (m) {
        Metric.E1RM -> s.metrics.e1rmKg
        Metric.TOP_SET -> s.metrics.topSetKg
        Metric.VOLUME -> s.metrics.volumeKg
        Metric.MAX_REPS -> s.metrics.maxReps.toDouble()
        Metric.TOTAL_REPS -> s.metrics.totalReps.toDouble()
    }
    private fun prFor(s: SessionPoint, m: Metric): Boolean = when (m) {
        Metric.TOP_SET -> s.isPrTopSet
        Metric.E1RM -> s.isPrE1rm
        Metric.MAX_REPS -> s.isPrReps
        else -> false
    }
    private fun label(v: Double, m: Metric, unit: WeightUnit): String = when (m) {
        Metric.VOLUME -> "${v.toLong()} ${de.simiil.liftlog.domain.units.Weights.label(unit)}"
        Metric.MAX_REPS, Metric.TOTAL_REPS -> "${v.toInt()} reps"
        else -> "${de.simiil.liftlog.domain.units.Weights.format(v, unit)} ${de.simiil.liftlog.domain.units.Weights.label(unit)}"
    }
    private fun recentSummary(s: SessionPoint, bodyweight: Boolean, unit: WeightUnit): String {
        if (bodyweight) return "${s.metrics.maxReps} reps best"
        val topW = s.sets.maxOfOrNull { it.weightKg } ?: 0.0
        val reps = s.sets.take(3).joinToString("·") { it.reps.toString() }
        return "${de.simiil.liftlog.domain.units.Weights.format(topW, unit)} ${de.simiil.liftlog.domain.units.Weights.label(unit)} × $reps"
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.ui.analytics.ExerciseDetailViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Add detail strings**
```xml
    <string name="metric_e1rm">e1RM</string>
    <string name="metric_top_set">Top set</string>
    <string name="metric_volume">Volume</string>
    <string name="metric_max_reps">Max reps</string>
    <string name="metric_total_reps">Total reps</string>
    <string name="range_30d">30d</string>
    <string name="range_90d">90d</string>
    <string name="range_1y">1y</string>
    <string name="range_all">all</string>
    <string name="analytics_recent_sessions">Recent sessions</string>
    <string name="analytics_pr">PR</string>
    <string name="analytics_need_two">Need 2+ sessions for a chart</string>
```

- [ ] **Step 6: Implement the detail Screen**

```kotlin
package de.simiil.liftlog.ui.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.R
import de.simiil.liftlog.ui.components.charts.ProgressLineChart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text(ui.name) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
            }
        })
    }) { inner ->
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // metric chips
            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.metrics.forEach { m ->
                    FilterChip(selected = m == ui.selectedMetric, onClick = { viewModel.onMetricChange(m) },
                        label = { Text(metricLabel(m)) })
                }
            }
            // range row
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Range.entries.forEach { r ->
                    val selected = r == ui.selectedRange
                    Surface(onClick = { viewModel.onRangeChange(r) }, modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(rangeLabel(r), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            if (ui.notEnoughData) {
                Text(stringResource(R.string.analytics_need_two), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp))
            } else {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(22.dp)) {
                    Box(Modifier.padding(vertical = 14.dp, horizontal = 10.dp)) {
                        ProgressLineChart(ui.chartPoints)
                    }
                }
            }
            // current value + trend
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(ui.currentValueLabel, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                ui.summary?.let { if (!it.bodyweight) TrendBadge(it.trend, large = true) }
            }
            Text(stringResource(R.string.analytics_recent_sessions), color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 8.dp))
            val fmt = remember0()
            ui.recent.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(fmt.format(Date(row.dateMillis)), fontSize = 14.sp, modifier = Modifier.width(92.dp))
                    Text(row.summary, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (row.isPr) Text(stringResource(R.string.analytics_pr), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable private fun remember0() = SimpleDateFormat("EEE d MMM", Locale.getDefault())
@Composable private fun metricLabel(m: Metric) = stringResource(when (m) {
    Metric.E1RM -> R.string.metric_e1rm; Metric.TOP_SET -> R.string.metric_top_set
    Metric.VOLUME -> R.string.metric_volume; Metric.MAX_REPS -> R.string.metric_max_reps
    Metric.TOTAL_REPS -> R.string.metric_total_reps
})
@Composable private fun rangeLabel(r: Range) = stringResource(when (r) {
    Range.D30 -> R.string.range_30d; Range.D90 -> R.string.range_90d
    Range.Y1 -> R.string.range_1y; Range.ALL -> R.string.range_all
})
```

> **Implementer note:** the recent-session row is the place to wire `onOpenSession` — the design says tap → session detail, but `SessionPoint` doesn't carry a sessionId in the UI row. Add `sessionId` to `RecentSessionRow` (it's available on `SessionPoint.sessionId`) and make the row clickable → `onOpenSession(row.sessionId)`. Do this when implementing; the VM/state change is one field. Replace the `remember0()` helper name with something descriptive (e.g. `rememberDateFormat()`).

- [ ] **Step 7: Run + build**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.ui.analytics.ExerciseDetailViewModelTest"` then `./gradlew assembleDebug lint`
Expected: tests PASS; BUILD SUCCESSFUL; lint clean.

- [ ] **Step 8: Commit**
```bash
git add app/src/main/kotlin/de/simiil/liftlog/ui/analytics/ExerciseDetailViewModel.kt app/src/main/kotlin/de/simiil/liftlog/ui/analytics/ExerciseDetailScreen.kt app/src/test/kotlin/de/simiil/liftlog/ui/analytics/ExerciseDetailViewModelTest.kt app/src/main/res/values/strings.xml
git commit -m "feat(analytics): exercise detail (chips, ranges, chart, recent sessions)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 14: Navigation wiring

**Files:**
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/navigation/Destinations.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/navigation/LiftLogNavHost.kt`

- [ ] **Step 1: Add the detail route**

In `Destinations.kt`:
```kotlin
/** Analytics exercise detail. Reached from the Analytics browser; not a top-level tab. */
@Serializable data class AnalyticsExerciseDetailRoute(val exerciseId: String)
```

- [ ] **Step 2: Wire the browser + detail composables**

In `LiftLogNavHost.kt`, replace `composable<AnalyticsRoute> { AnalyticsScreen() }` with:
```kotlin
        composable<AnalyticsRoute> {
            AnalyticsScreen(onOpenExercise = { id -> navController.navigate(AnalyticsExerciseDetailRoute(id)) })
        }
        composable<AnalyticsExerciseDetailRoute> {
            ExerciseDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenSession = { id -> navController.navigate(SessionDetailRoute(id)) },
            )
        }
```
Add imports:
```kotlin
import de.simiil.liftlog.ui.analytics.ExerciseDetailScreen
```
(`AnalyticsScreen` is already imported.)

- [ ] **Step 3: Build + verify bottom bar hides on detail**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (Detail route is not in `LiftLogApp.topLevelDestinations`, so the bottom bar auto-hides — no change needed there.)

- [ ] **Step 4: Commit**
```bash
git add app/src/main/kotlin/de/simiil/liftlog/ui/navigation/Destinations.kt app/src/main/kotlin/de/simiil/liftlog/ui/navigation/LiftLogNavHost.kt
git commit -m "feat(analytics): navigation — browser → exercise detail → session detail

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 15: Debug-only synthetic-history seeder + Settings trigger

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/data/seed/SyntheticHistorySeeder.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/settings/SettingsScreen.kt`
- Strings: `app/src/main/res/values/strings.xml`

> Entity field names below are taken from `AnalyticsDaoTest` (the canonical builders).

- [ ] **Step 1: Implement the seeder** (uses existing seeded exercise ids; mirrors `data.js gen()`)

```kotlin
package de.simiil.liftlog.data.seed

import de.simiil.liftlog.data.dao.SessionDao
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * DEBUG-ONLY perf fixture: ~1 year of completed sessions for a few seed exercises, so the
 * Analytics browser/detail can be perf-checked on device (05-roadmap M4 exit criterion).
 * Never invoked in release builds (the trigger is BuildConfig.DEBUG-gated in Settings).
 */
@Singleton
class SyntheticHistorySeeder @Inject constructor(
    private val sessionDao: SessionDao,
    private val clock: Clock,
) {
    private val day = 86_400_000L

    suspend fun seed() {
        val now = clock.millis()
        // (exerciseId from seed/exercises.v1.json, startWeight, weeklyStep, sessions, reps)
        val plans = listOf(
            Plan("barbell-bench-press", 80.0, 1.6, 52, listOf(5, 5, 4)),
            Plan("barbell-back-squat", 110.0, 2.0, 52, listOf(5, 5, 5)),
            Plan("deadlift", 140.0, 2.2, 40, listOf(5, 3, 3)),
            Plan("overhead-press", 47.5, 0.6, 48, listOf(6, 6, 5)),
        )
        for (p in plans) {
            for (i in 0 until p.sessions) {
                val daysAgo = (p.sessions - 1 - i).toLong() * 7 + 1
                val startedAt = now - daysAgo * day
                val sessionId = UUID.randomUUID().toString()
                sessionDao.insertSession(SessionEntity(
                    id = sessionId, templateId = null, templateNameSnapshot = "Synthetic",
                    startedAt = startedAt, endedAt = startedAt + 3_600_000L, note = null,
                    createdAt = startedAt, updatedAt = startedAt, deletedAt = null,
                ))
                val seId = UUID.randomUUID().toString()
                sessionDao.insertSessionExercise(SessionExerciseEntity(
                    id = seId, sessionId = sessionId, exerciseId = p.exerciseId, position = 1,
                    targetSets = p.reps.size, targetRepsMin = null, targetRepsMax = null,
                    createdAt = startedAt, updatedAt = startedAt, deletedAt = null,
                ))
                val jitter = ((i * 53 % 7) - 3) * 1.0
                val top = (max(20.0, p.startW + p.stepW * i + jitter) / 2.5).roundToInt() * 2.5
                p.reps.forEachIndexed { k, r ->
                    sessionDao.insertLoggedSet(LoggedSetEntity(
                        id = UUID.randomUUID().toString(), sessionExerciseId = seId,
                        weightKg = max(20.0, top - 2.5 * k), reps = r, position = k + 1,
                        completedAt = startedAt + k * 120_000L, rpe = null, note = null,
                        createdAt = startedAt, updatedAt = startedAt, deletedAt = null,
                    ))
                }
            }
        }
    }

    private data class Plan(val exerciseId: String, val startW: Double, val stepW: Double, val sessions: Int, val reps: List<Int>)
}
```

> **Implementer note:** confirm the four `exerciseId`s exist in `app/src/main/assets/seed/exercises.v1.json` (grep the file). If the ids differ, use whatever ids the seed file actually defines for bench/squat/deadlift/OHP. The seeder is harmless if an id is absent (FK is on `exerciseId` → must exist; pick ids that DO exist).

- [ ] **Step 2: Add the debug trigger to SettingsViewModel**

Add to `SettingsViewModel` constructor + a method:
```kotlin
// constructor gains: private val syntheticHistorySeeder: de.simiil.liftlog.data.seed.SyntheticHistorySeeder
    fun seedDemoData() {
        viewModelScope.launch { syntheticHistorySeeder.seed() }
    }
```

- [ ] **Step 3: Add the BuildConfig.DEBUG-gated button to SettingsScreen**

Add strings:
```xml
    <string name="settings_seed_demo">Seed demo data (debug)</string>
```
In `SettingsScreen`'s `Column`, after the theme rows:
```kotlin
            if (de.simiil.liftlog.BuildConfig.DEBUG) {
                TextButton(
                    onClick = viewModel::seedDemoData,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(stringResource(R.string.settings_seed_demo)) }
            }
```
Add import `androidx.compose.material3.TextButton`.

- [ ] **Step 4: Build + verify**

Run: `./gradlew assembleDebug lint`
Expected: BUILD SUCCESSFUL; lint clean. (`BuildConfig` is available because Task 1 enabled it.)

- [ ] **Step 5: Commit**
```bash
git add app/src/main/kotlin/de/simiil/liftlog/data/seed/SyntheticHistorySeeder.kt app/src/main/kotlin/de/simiil/liftlog/ui/settings/SettingsViewModel.kt app/src/main/kotlin/de/simiil/liftlog/ui/settings/SettingsScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(analytics): debug-only synthetic history seeder + Settings trigger

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 16: Integration — CI parity, on-device visual + perf check

**Files:** none (verification task).

- [ ] **Step 1: Full CI-parity build**

Run: `./gradlew lint testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL; 0 lint errors; all unit tests green.

- [ ] **Step 2: Instrumented DAO tests**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.data.dao.AnalyticsDaoTest`
Expected: PASS on `emulator-5554`.

- [ ] **Step 3: Install + visual check (light + dark)**

```bash
./gradlew installDebug
adb -s emulator-5554 shell am start -n de.simiil.liftlog/.MainActivity
```
- Open Settings → "Seed demo data (debug)". Return to Analytics tab.
- Screenshot the browser (week card, search, rows with trend badges + sparklines) and compare to `analytics.jsx` / the design mockup. Tap a row → detail; verify chips, ranges, chart (PR markers in `tertiary`, no clash), current value + trend, recent list.
- Toggle dark mode: `adb -s emulator-5554 shell cmd uimode night yes` (and `no`); re-screenshot both screens.
- Confirm the `tertiary` PR accent reads well in both themes; if not, switch to `secondary` in the chart + recent PR tag (single color reference each).

- [ ] **Step 4: Perf check (exit criterion)**

With the seeder run (a year × 4 exercises) plus the full seed library, scroll the browser and open a detail. Confirm smooth scrolling (no visible jank). If per-row Vico sparklines stutter, note it and (follow-up) swap `Sparkline` for a Compose `Canvas` polyline behind the same wrapper — do not over-tune Vico now.

- [ ] **Step 5: Optional Compose smoke test**

If time permits, add `app/src/androidTest/kotlin/de/simiil/liftlog/ui/AnalyticsBrowsePathTest.kt` (createAndroidComposeRule): assert the week head text shows; (with seeded data) a row is clickable → detail title appears. Run with `-Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.ui.AnalyticsBrowsePathTest`.

- [ ] **Step 6: Finish**

Use **superpowers:finishing-a-development-branch** → push `m4-analytics`, open a PR (owner reviews/merges & removes the worktree).

---

## Self-review notes (plan author)

- **Spec coverage:** §1 metrics (Task 3), §2 e1RM (Task 2), §3 trend (Task 4), §4 bodyweight (Task 6), §5 fixtures (Tasks 2/3/4/6), §6 charts 1–4 (Tasks 11/12/13), §7 queries/laziness/downsample (Tasks 5/7/8/12). UX §5.1 browser (Task 12), §5.2 detail (Task 13). Decisions: Vico (Tasks 1/11), debug seeder (Task 15), tertiary PR accent (Tasks 9/11/13).
- **Known soft spots flagged inline:** Vico API version drift (Task 11), `Sparkline` color param + PR markers deferred per decision, recent-row `sessionId` wiring (Task 13), seed exercise-id confirmation (Task 15). Each has an implementer note.
- **Clock:** reuse the existing `Clock.systemUTC()` provider (UTC week boundaries) — no new DI.
