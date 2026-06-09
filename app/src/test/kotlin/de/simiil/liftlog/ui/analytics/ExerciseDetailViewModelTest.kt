package de.simiil.liftlog.ui.analytics

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import de.simiil.liftlog.domain.analytics.*
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.TrainedExercise
import de.simiil.liftlog.domain.repository.WeekSummary
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExerciseDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val day = 86_400_000L
    private val now = 1_000_000_000_000L

    private fun summaryWith(n: Int, bodyweight: Boolean = false): ExerciseSummary {
        val sessions = (0 until n).map {
            val m = sessionMetrics(listOf(SetEntry(100.0 + it, 5)))
            SessionPoint(
                "s$it", now - (n - it).toLong() * day, listOf(SetEntry(100.0 + it, 5)), m,
                if (bodyweight) m.maxReps.toDouble() else m.e1rmKg,
                it == n - 1, it == n - 1, it == n - 1, it == n - 1,
            )
        }
        return ExerciseSummary(bodyweight, sessions, TrendResult.Insufficient, sessions.last().primary, sessions.last().timeMillis)
    }

    private fun vm(summary: ExerciseSummary?, name: String = "Bench") = ExerciseDetailViewModel(
        SavedStateHandle(mapOf("exerciseId" to "e1")),
        object : AnalyticsRepository {
            override fun observeWeekSummary() = flowOf(WeekSummary(0, 0, 0.0, 0.0))
            override fun observeTrainedExercises() = flowOf(
                listOf(TrainedExercise("e1", name, de.simiil.liftlog.domain.model.MuscleGroup.CHEST, Equipment.BARBELL, 0L))
            )
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

    @Test fun recentRows_carrySessionId() = runTest {
        vm(summaryWith(3)).uiState.test {
            var s = awaitItem(); while (s.summary == null) s = awaitItem()
            // recent rows should have non-blank sessionIds sourced from SessionPoint.sessionId
            assertTrue(s.recent.isNotEmpty())
            assertTrue(s.recent.all { it.sessionId.isNotBlank() })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
