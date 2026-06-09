package de.simiil.liftlog.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.analytics.ExerciseSummary
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.SettingsRepository
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
    val unit: WeightUnit = WeightUnit.KG,
)

@HiltViewModel
class AnalyticsBrowserViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<AnalyticsBrowserUiState> = combine(
        analyticsRepository.observeWeekSummary(),
        analyticsRepository.observeTrainedExercises(),
        query,
        settingsRepository.weightUnit,
    ) { week, exercises, q, unit ->
        AnalyticsBrowserUiState(
            week = week,
            query = q,
            exercises = if (q.isBlank()) exercises
                else exercises.filter { it.name.contains(q, ignoreCase = true) },
            unit = unit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsBrowserUiState())

    fun onQueryChange(value: String) { query.value = value }

    /** Per-row summary flow — collected lazily by each visible browser row (04-analytics-spec §7). */
    fun summary(exerciseId: String): Flow<ExerciseSummary?> =
        analyticsRepository.observeExerciseSummary(exerciseId)
}
