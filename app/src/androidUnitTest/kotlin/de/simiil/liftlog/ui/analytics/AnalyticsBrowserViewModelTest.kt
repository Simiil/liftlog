package de.simiil.liftlog.ui.analytics

import app.cash.turbine.test
import de.simiil.liftlog.domain.analytics.ExerciseSummary
import de.simiil.liftlog.domain.analytics.RadarGroup
import de.simiil.liftlog.domain.analytics.SetWithExercise
import de.simiil.liftlog.domain.analytics.TrendResult
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.SettingsRepository
import de.simiil.liftlog.domain.repository.TrainedExercise
import de.simiil.liftlog.domain.repository.WeekSummary
import de.simiil.liftlog.testing.FixedClock
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

class AnalyticsBrowserViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val names =
        de.simiil.liftlog.ui.exercises
            .ExerciseNameResolver { _, fallback -> fallback }

    private val now = 1_000_000_000_000L
    private val day = 86_400_000L
    private val fixedClock = FixedClock(Instant.fromEpochMilliseconds(now))

    private fun trained(
        id: String,
        name: String,
    ) = TrainedExercise(id, name, MuscleGroup.CHEST, Equipment.BARBELL, 0L)

    private class FakeRepo(
        val list: List<TrainedExercise>,
        val rows: List<SetWithExercise> = emptyList(),
    ) : AnalyticsRepository {
        override fun observeWeekSummary(): Flow<WeekSummary> = flowOf(WeekSummary(3, 86, 14250.0, 12980.0))

        override fun observeTrainedExercises(): Flow<List<TrainedExercise>> = flowOf(list)

        override fun observeExerciseSummary(exerciseId: String): Flow<ExerciseSummary?> =
            flowOf(ExerciseSummary(false, emptyList(), TrendResult.Insufficient, 0.0, 0L))

        override fun observePrSessionIds(): Flow<Set<String>> = flowOf(emptySet())

        override fun observeSetsWithExercise(): Flow<List<SetWithExercise>> = flowOf(rows)
    }

    private class FakeSettings : SettingsRepository {
        override val themePreference: Flow<ThemePreference> = flowOf(ThemePreference.SYSTEM)
        override val weightUnit: Flow<WeightUnit> = flowOf(WeightUnit.KG)

        override suspend fun setThemePreference(preference: ThemePreference) {}

        override suspend fun setWeightUnit(unit: WeightUnit) {}
    }

    @Test fun search_filtersByNameCaseInsensitive() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm =
                AnalyticsBrowserViewModel(
                    FakeRepo(listOf(trained("a", "Bench Press"), trained("b", "Squat"))),
                    FakeSettings(),
                    names,
                    fixedClock,
                )
            vm.uiState.test {
                awaitItem() // initial
                vm.onQueryChange("squ")
                val s = awaitItem()
                assertEquals(listOf("b"), s.exercises.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun weekSummary_isExposed() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = AnalyticsBrowserViewModel(FakeRepo(emptyList()), FakeSettings(), names, fixedClock)
            vm.uiState.test {
                var s = awaitItem()
                while (s.week == null) s = awaitItem()
                assertEquals(86, s.week!!.sets)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun unit_isExposedFromSettings() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = AnalyticsBrowserViewModel(FakeRepo(emptyList()), FakeSettings(), names, fixedClock)
            vm.uiState.test {
                var s = awaitItem()
                while (s.week == null) s = awaitItem()
                assertEquals(WeightUnit.KG, s.unit)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun muscleBalance_exposedAndRecomputesOnRangeChange() =
        runTest(mainDispatcherRule.dispatcher) {
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
                assertEquals(
                    8.0,
                    s.balance!!
                        .groups
                        .first { it.group == RadarGroup.CHEST }
                        .setsPerWeek,
                    1e-9,
                )
                vm.onBalanceRangeChange(Range.D30)
                s = awaitItem()
                assertEquals(Range.D30, s.selectedRange)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun muscleBalance_noSets_flagsEmpty() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = AnalyticsBrowserViewModel(FakeRepo(emptyList()), FakeSettings(), names, fixedClock)
            vm.balanceState.test {
                var s = awaitItem()
                while (s.balance == null) s = awaitItem()
                assertEquals(true, s.balance!!.isEmpty)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
