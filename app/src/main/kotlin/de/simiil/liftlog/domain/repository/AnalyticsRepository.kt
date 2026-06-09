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
