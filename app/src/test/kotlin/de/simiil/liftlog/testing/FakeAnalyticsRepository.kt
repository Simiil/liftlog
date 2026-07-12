package de.simiil.liftlog.testing

import de.simiil.liftlog.domain.analytics.ExerciseSummary
import de.simiil.liftlog.domain.analytics.SetWithExercise
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.TrainedExercise
import de.simiil.liftlog.domain.repository.WeekSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAnalyticsRepository : AnalyticsRepository {
    val weekSummary: MutableStateFlow<WeekSummary> =
        MutableStateFlow(WeekSummary(sessions = 0, sets = 0, volumeKg = 0.0, prevVolumeKg = 0.0))
    val trainedExercises = MutableStateFlow<List<TrainedExercise>>(emptyList())
    val exerciseSummaries = MutableStateFlow<Map<String, ExerciseSummary?>>(emptyMap())
    val prSessionIds = MutableStateFlow<Set<String>>(emptySet())
    val rows = MutableStateFlow<List<SetWithExercise>>(emptyList())

    override fun observeWeekSummary(): Flow<WeekSummary> = weekSummary

    override fun observeTrainedExercises(): Flow<List<TrainedExercise>> = trainedExercises

    override fun observeExerciseSummary(exerciseId: String): Flow<ExerciseSummary?> = exerciseSummaries.map { it[exerciseId] }

    override fun observePrSessionIds(): Flow<Set<String>> = prSessionIds

    override fun observeSetsWithExercise(): Flow<List<SetWithExercise>> = rows
}
