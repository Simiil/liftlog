package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.dao.AnalyticsDao
import de.simiil.liftlog.domain.analytics.DatedSet
import de.simiil.liftlog.domain.analytics.ExerciseSummary
import de.simiil.liftlog.domain.analytics.prSessionIds
import de.simiil.liftlog.domain.analytics.SetEntry
import de.simiil.liftlog.domain.analytics.summarize
import de.simiil.liftlog.domain.analytics.volumeKg
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.TrainedExercise
import de.simiil.liftlog.domain.repository.WeekSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
                volumeKg = volumeKg(thisWeek.map { SetEntry(it.weightKg, it.reps) }),
                prevVolumeKg = volumeKg(prevWeek.map { SetEntry(it.weightKg, it.reps) }),
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

    override fun observePrSessionIds(): Flow<Set<String>> =
        combine(
            analyticsDao.observeAllSetsSince(0L),
            exerciseRepository.observeAll(),
        ) { rows, exercises ->
            prSessionIds(
                setsByExercise = rows.groupBy(
                    { it.exerciseId },
                    { DatedSet(it.sessionId, it.startedAt, it.weightKg, it.reps) },
                ),
                equipmentById = exercises.associate { it.id to it.equipment },
                nowMillis = clock.millis(),
            )
        }.flowOn(Dispatchers.Default)
}
