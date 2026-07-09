package de.simiil.liftlog.ui.analytics

import app.cash.turbine.test
import de.simiil.liftlog.domain.analytics.ExerciseSummary
import de.simiil.liftlog.domain.analytics.TrendResult
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.SettingsRepository
import de.simiil.liftlog.domain.repository.TrainedExercise
import de.simiil.liftlog.domain.repository.WeekSummary
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AnalyticsBrowserViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val names =
        de.simiil.liftlog.ui.exercises
            .ExerciseNameResolver { _, fallback -> fallback }

    private fun trained(
        id: String,
        name: String,
    ) = TrainedExercise(id, name, MuscleGroup.CHEST, Equipment.BARBELL, 0L)

    private class FakeRepo(
        val list: List<TrainedExercise>,
    ) : AnalyticsRepository {
        override fun observeWeekSummary(): Flow<WeekSummary> = flowOf(WeekSummary(3, 86, 14250.0, 12980.0))

        override fun observeTrainedExercises(): Flow<List<TrainedExercise>> = flowOf(list)

        override fun observeExerciseSummary(exerciseId: String): Flow<ExerciseSummary?> =
            flowOf(ExerciseSummary(false, emptyList(), TrendResult.Insufficient, 0.0, 0L))

        override fun observePrSessionIds(): Flow<Set<String>> = flowOf(emptySet())
    }

    private class FakeSettings : SettingsRepository {
        override val themePreference: Flow<ThemePreference> = flowOf(ThemePreference.SYSTEM)
        override val weightUnit: Flow<WeightUnit> = flowOf(WeightUnit.KG)

        override suspend fun setThemePreference(preference: ThemePreference) {}

        override suspend fun setWeightUnit(unit: WeightUnit) {}

        override val notificationPromptShown: Flow<Boolean> = flowOf(false)

        override suspend fun setNotificationPromptShown() {}
    }

    @Test fun search_filtersByNameCaseInsensitive() =
        runTest {
            val vm =
                AnalyticsBrowserViewModel(
                    FakeRepo(listOf(trained("a", "Bench Press"), trained("b", "Squat"))),
                    FakeSettings(),
                    names,
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
        runTest {
            val vm = AnalyticsBrowserViewModel(FakeRepo(emptyList()), FakeSettings(), names)
            vm.uiState.test {
                var s = awaitItem()
                while (s.week == null) s = awaitItem()
                assertEquals(86, s.week!!.sets)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun unit_isExposedFromSettings() =
        runTest {
            val vm = AnalyticsBrowserViewModel(FakeRepo(emptyList()), FakeSettings(), names)
            vm.uiState.test {
                var s = awaitItem()
                while (s.week == null) s = awaitItem()
                assertEquals(WeightUnit.KG, s.unit)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
