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
            Plan("7a0737bd-d46f-4dd1-9dad-ed3e4a83869a", 80.0, 1.6, 52, listOf(5, 5, 4)),   // Barbell Bench Press
            Plan("3c993aaa-dac3-45ef-8c70-35e7504b25f4", 110.0, 2.0, 52, listOf(5, 5, 5)),  // Back Squat
            Plan("246718b2-905d-4afe-b802-90435d0809d5", 140.0, 2.2, 40, listOf(5, 3, 3)),  // Deadlift
            Plan("b5e67fab-4860-4279-b4d9-d95cb6ea3983", 47.5, 0.6, 48, listOf(6, 6, 5)),   // Overhead Press
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
