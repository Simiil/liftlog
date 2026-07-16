package de.simiil.liftlog.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.simiil.liftlog.domain.analytics.ExerciseSummary
import de.simiil.liftlog.domain.analytics.MuscleBalance
import de.simiil.liftlog.domain.analytics.muscleBalance
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.SettingsRepository
import de.simiil.liftlog.domain.repository.TrainedExercise
import de.simiil.liftlog.domain.repository.WeekSummary
import de.simiil.liftlog.ui.exercises.ExerciseNameResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

data class AnalyticsBrowserUiState(
    val week: WeekSummary? = null,
    val query: String = "",
    val exercises: List<TrainedExercise> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
)

data class MuscleBalanceUiState(
    val balance: MuscleBalance? = null,
    val selectedRange: Range = Range.D90,
)

class AnalyticsBrowserViewModel(
    private val analyticsRepository: AnalyticsRepository,
    private val settingsRepository: SettingsRepository,
    private val names: ExerciseNameResolver,
    private val clock: Clock,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val uiState: StateFlow<AnalyticsBrowserUiState> =
        combine(
            analyticsRepository.observeWeekSummary(),
            analyticsRepository.observeTrainedExercises(),
            query,
            settingsRepository.weightUnit,
        ) { week, exercises, q, unit ->
            AnalyticsBrowserUiState(
                week = week,
                query = q,
                exercises =
                    if (q.isBlank()) {
                        exercises
                    } else {
                        exercises.filter {
                            names.displayName(it.id, it.name).contains(q, ignoreCase = true) ||
                                it.name.contains(q, ignoreCase = true)
                        }
                    },
                unit = unit,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsBrowserUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

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

    /** Per-row summary flow — collected lazily by each visible browser row (04-analytics-spec §7). */
    fun summary(exerciseId: String): Flow<ExerciseSummary?> = analyticsRepository.observeExerciseSummary(exerciseId)
}
