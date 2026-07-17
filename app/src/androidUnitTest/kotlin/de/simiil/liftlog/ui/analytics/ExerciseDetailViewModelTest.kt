package de.simiil.liftlog.ui.analytics

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import de.simiil.liftlog.domain.analytics.ExerciseSummary
import de.simiil.liftlog.domain.analytics.SessionPoint
import de.simiil.liftlog.domain.analytics.SetEntry
import de.simiil.liftlog.domain.analytics.TrendResult
import de.simiil.liftlog.domain.analytics.sessionMetrics
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.TrainedExercise
import de.simiil.liftlog.domain.repository.WeekSummary
import de.simiil.liftlog.testing.FixedClock
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

class ExerciseDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val names =
        de.simiil.liftlog.ui.exercises
            .ExerciseNameResolver { _, fallback -> fallback }

    private val day = 86_400_000L
    private val now = 1_000_000_000_000L

    private fun summaryWith(
        n: Int,
        bodyweight: Boolean = false,
    ): ExerciseSummary {
        val sessions =
            (0 until n).map {
                val m = sessionMetrics(listOf(SetEntry(100.0 + it, 5)))
                SessionPoint(
                    "s$it",
                    now - (n - it).toLong() * day,
                    listOf(SetEntry(100.0 + it, 5)),
                    m,
                    if (bodyweight) m.totalReps.toDouble() else m.volumeKg,
                    it == n - 1, // isPrE1rm
                    it == n - 1, // isPrTopSet
                    it == n - 1, // isPrReps
                    it == n - 1, // isPrVolume
                    it == n - 1, // isPrTotalReps
                    it == n - 1, // isPr
                )
            }
        return ExerciseSummary(bodyweight, sessions, TrendResult.Insufficient, sessions.last().primary, sessions.last().timeMillis)
    }

    private fun vm(
        summary: ExerciseSummary?,
        name: String = "Bench",
    ) = ExerciseDetailViewModel(
        SavedStateHandle(mapOf("exerciseId" to "e1")),
        object : AnalyticsRepository {
            override fun observeWeekSummary() = flowOf(WeekSummary(0, 0, 0.0, 0.0))

            override fun observeTrainedExercises() =
                flowOf(
                    listOf(TrainedExercise("e1", name, de.simiil.liftlog.domain.model.MuscleGroup.CHEST, Equipment.BARBELL, 0L)),
                )

            override fun observeExerciseSummary(exerciseId: String) = flowOf(summary)

            override fun observePrSessionIds() = flowOf(emptySet<String>())

            override fun observeSetsWithExercise() = flowOf(emptyList<de.simiil.liftlog.domain.analytics.SetWithExercise>())
        },
        FakeSettings(),
        FixedClock(Instant.fromEpochMilliseconds(now)),
        names,
    )

    private class FakeSettings : de.simiil.liftlog.domain.repository.SettingsRepository {
        override val themePreference = flowOf(de.simiil.liftlog.domain.model.ThemePreference.SYSTEM)
        override val weightUnit = flowOf(WeightUnit.KG)

        override suspend fun setThemePreference(preference: de.simiil.liftlog.domain.model.ThemePreference) {}

        override suspend fun setWeightUnit(unit: WeightUnit) {}
    }

    @Test fun weightedExercise_offersWeightMetrics_volumeFirst() =
        runTest {
            vm(summaryWith(5)).uiState.test {
                var s = awaitItem()
                while (s.summary == null) s = awaitItem()
                // Volume is the new default headline; e1RM is kept but demoted to last.
                assertEquals(listOf(Metric.VOLUME, Metric.TOP_SET, Metric.E1RM), s.metrics)
                assertEquals(Metric.VOLUME, s.selectedMetric)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun bodyweightExercise_offersRepMetrics_totalRepsFirst() =
        runTest {
            vm(summaryWith(5, bodyweight = true)).uiState.test {
                var s = awaitItem()
                while (s.summary == null) s = awaitItem()
                assertEquals(listOf(Metric.TOTAL_REPS, Metric.MAX_REPS), s.metrics)
                assertEquals(Metric.TOTAL_REPS, s.selectedMetric)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun fewerThanTwoPointsInRange_fallsBackToLastTwo() =
        runTest {
            val v = vm(summaryWith(5))
            v.onRangeChange(Range.D30) // 30d window may include <2; chart still shows ≥2
            v.uiState.test {
                var s = awaitItem()
                while (s.summary == null) s = awaitItem()
                assertTrue(s.chartPoints.size >= 2)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun emptySummary_marksInsufficient() =
        runTest {
            vm(summaryWith(1)).uiState.test {
                var s = awaitItem()
                while (s.summary == null) s = awaitItem()
                assertTrue(s.chartPoints.size >= 2 || s.notEnoughData)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun summaryOfSets(
        setsPerSession: List<List<SetEntry>>,
        bodyweight: Boolean = false,
    ): ExerciseSummary {
        val n = setsPerSession.size
        val sessions =
            setsPerSession.mapIndexed { i, sets ->
                val m = sessionMetrics(sets)
                SessionPoint(
                    "s$i",
                    now - (n - i).toLong() * day,
                    sets,
                    m,
                    if (bodyweight) m.totalReps.toDouble() else m.volumeKg,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                )
            }
        return ExerciseSummary(bodyweight, sessions, TrendResult.Insufficient, sessions.last().primary, sessions.last().timeMillis)
    }

    @Test fun recentRows_pairEachWeightWithItsReps() =
        runTest {
            // Issue #28 repro: 55×10, 60×9, 60×5, 55×10 must not collapse to "60 kg × 10·9·5"
            // (max weight paired with the wrong reps, 4th set dropped).
            val sets = listOf(SetEntry(55.0, 10), SetEntry(60.0, 9), SetEntry(60.0, 5), SetEntry(55.0, 10))
            vm(summaryOfSets(listOf(listOf(SetEntry(50.0, 8)), sets))).uiState.test {
                var s = awaitItem()
                while (s.summary == null) s = awaitItem()
                // recent is newest-first; the mixed-weight session is the newest.
                assertEquals("55 kg × 10, 60 kg × 9·5, 55 kg × 10", s.recent.first().summary)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun recentRows_bodyweight_bareRepsList() =
        runTest {
            val sets = listOf(SetEntry(0.0, 12), SetEntry(0.0, 10))
            vm(summaryOfSets(listOf(listOf(SetEntry(0.0, 8)), sets), bodyweight = true)).uiState.test {
                var s = awaitItem()
                while (s.summary == null) s = awaitItem()
                assertEquals("12·10", s.recent.first().summary)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun recentRows_carrySessionId() =
        runTest {
            vm(summaryWith(3)).uiState.test {
                var s = awaitItem()
                while (s.summary == null) s = awaitItem()
                // recent rows should have non-blank sessionIds sourced from SessionPoint.sessionId
                assertTrue(s.recent.isNotEmpty())
                assertTrue(s.recent.all { it.sessionId.isNotBlank() })
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Regression: real sessions land at millisecond-precision wall-clock times (unlike the
    // midnight-aligned fixtures above). Chart x values must survive Vico's x-delta GCD check or
    // CartesianChartHost crashes with "The x values are too precise. The maximum precision is
    // four decimal places."
    @Test fun chartPoints_atMillisecondTimestamps_surviveVicoPrecisionCheck() =
        runTest {
            val times =
                listOf(
                    now - 30 * day + 37_043_123,
                    now - 20 * day + 67_511_741,
                    now - 10 * day + 51_804_007,
                )
            vm(summaryAt(times)).uiState.test {
                var s = awaitItem()
                while (s.summary == null) s = awaitItem()
                val xs = s.chartPoints.map { it.x }
                assertEquals(times.size, xs.size)
                assertTrue("Vico would crash: x-delta GCD rounds to 0 for xs=$xs", vicoXDeltaGcd(xs) >= 1e-4)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun summaryAt(times: List<Long>): ExerciseSummary {
        val sessions =
            times.mapIndexed { i, t ->
                val m = sessionMetrics(listOf(SetEntry(100.0 + i, 5)))
                SessionPoint("s$i", t, listOf(SetEntry(100.0 + i, 5)), m, m.volumeKg, false, false, false, false, false, false)
            }
        return ExerciseSummary(false, sessions, TrendResult.Insufficient, sessions.last().primary, sessions.last().timeMillis)
    }

    // Mirrors Vico 2.1.3's getXDeltaGcd (CartesianLayerModel.kt) + gcdWith (Math.kt): Euclid's
    // algorithm with a 1e-5 cutoff, each result rounded to the nearest 1e-4. A final result of
    // 0.0 is exactly the condition under which Vico throws.
    private fun vicoXDeltaGcd(xs: List<Float>): Double {
        var prev = xs.first().toDouble()
        var gcd: Double? = null
        for (i in 1 until xs.size) {
            val x = xs[i].toDouble()
            val delta = kotlin.math.abs(x - prev)
            prev = x
            if (delta != 0.0) gcd = gcd?.let { vicoGcdWith(it, delta) } ?: delta
        }
        return gcd ?: 1.0
    }

    private fun vicoGcdWith(
        a: Double,
        b: Double,
    ): Double {
        var x = a
        var y = b
        while (true) {
            if (x < y) {
                val t = x
                x = y
                y = t
            }
            if (kotlin.math.abs(y) < 1e-5) break
            val r = x - kotlin.math.floor(x / y) * y
            x = y
            y = r
        }
        return kotlin.math.round(x * 10_000f) / 10_000.0
    }
}
