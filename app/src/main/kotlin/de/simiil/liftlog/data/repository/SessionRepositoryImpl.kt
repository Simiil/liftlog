package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.dao.PlanDao
import de.simiil.liftlog.data.dao.PrefillDao
import de.simiil.liftlog.data.dao.SessionDao
import de.simiil.liftlog.data.db.Transactor
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.data.mapper.toDomain
import de.simiil.liftlog.domain.model.LoggedSet
import de.simiil.liftlog.domain.model.Session
import de.simiil.liftlog.domain.model.SessionExercise
import de.simiil.liftlog.domain.repository.SessionRepository
import de.simiil.liftlog.domain.units.Rpe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl
    @Inject
    constructor(
        private val dao: SessionDao,
        private val transactor: Transactor,
        private val clock: Clock,
        private val prefillDao: PrefillDao,
        private val planDao: PlanDao,
    ) : SessionRepository {
        override fun observeActiveSession() = dao.observeActiveSession().map { it?.toDomain() }

        override fun observeHistory() = dao.observeHistory().map { it.map(SessionEntity::toDomain) }

        override fun observeSessionDetails(id: String) = dao.observeSessionWithDetails(id).map { it?.toDomain() }

        override suspend fun startEmptySession(): Session =
            transactor.immediate {
                check(dao.activeSessionId() == null) { "A session is already in progress" }
                val now = clock.millis()
                val session =
                    SessionEntity(
                        id = UUID.randomUUID().toString(),
                        templateId = null,
                        templateNameSnapshot = null,
                        startedAt = now,
                        endedAt = null,
                        note = null,
                        rpe = null,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                    )
                dao.insertSession(session)
                session.toDomain()
            }

        override suspend fun finishSession(id: String) {
            val session = dao.findSession(id) ?: return
            if (session.endedAt != null) return // already-ended guard
            val now = clock.millis()
            dao.updateSession(session.copy(endedAt = now, updatedAt = now))
        }

        override suspend fun softDeleteSession(id: String) =
            transactor.immediate {
                val now = clock.millis()
                dao.softDeleteLoggedSetsForSession(id, now)
                dao.softDeleteSessionExercisesFor(id, now)
                dao.softDeleteSession(id, now)
            }

        override suspend fun addExerciseToSession(
            sessionId: String,
            exerciseId: String,
        ): SessionExercise {
            val now = clock.millis()
            val entity =
                SessionExerciseEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    position = (dao.maxExercisePosition(sessionId) ?: 0) + 1,
                    targetSets = null,
                    targetRepsMin = null,
                    targetRepsMax = null,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                )
            dao.insertSessionExercise(entity)
            return entity.toDomain()
        }

        override suspend fun logSet(
            sessionExerciseId: String,
            weightKg: Double,
            reps: Int,
        ): LoggedSet {
            require(weightKg >= 0.0) { "weightKg must be >= 0" }
            require(reps >= 1) { "reps must be >= 1" }
            val now = clock.millis()
            val entity =
                LoggedSetEntity(
                    id = UUID.randomUUID().toString(),
                    sessionExerciseId = sessionExerciseId,
                    weightKg = weightKg,
                    reps = reps,
                    position = (dao.maxSetPosition(sessionExerciseId) ?: 0) + 1,
                    completedAt = now,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                )
            dao.insertLoggedSet(entity)
            return entity.toDomain()
        }

        override suspend fun updateSet(
            setId: String,
            weightKg: Double,
            reps: Int,
        ) {
            require(weightKg >= 0.0) { "weightKg must be >= 0" }
            require(reps >= 1) { "reps must be >= 1" }
            val existing = dao.findLoggedSet(setId) ?: return
            dao.updateLoggedSet(
                existing.copy(
                    weightKg = weightKg,
                    reps = reps,
                    updatedAt = clock.millis(),
                ),
            )
        }

        override suspend fun deleteSet(setId: String) {
            dao.softDeleteLoggedSet(setId, clock.millis())
        }

        override suspend fun removeExercise(sessionExerciseId: String) {
            val now = clock.millis()
            transactor.immediate {
                dao.softDeleteLoggedSetsForSessionExercise(sessionExerciseId, now)
                dao.softDeleteSessionExercise(sessionExerciseId, now)
            }
        }

        override suspend fun replaceExercise(
            sessionExerciseId: String,
            newExerciseId: String,
        ): SessionExercise {
            val now = clock.millis()
            return transactor.immediate {
                val old =
                    dao.findSessionExercise(sessionExerciseId)
                        ?: error("session exercise not found: $sessionExerciseId")
                dao.softDeleteLoggedSetsForSessionExercise(sessionExerciseId, now)
                dao.softDeleteSessionExercise(sessionExerciseId, now)
                val replacement =
                    SessionExerciseEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = old.sessionId,
                        exerciseId = newExerciseId,
                        position = old.position,
                        targetSets = null,
                        targetRepsMin = null,
                        targetRepsMax = null,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                    )
                dao.insertSessionExercise(replacement)
                replacement.toDomain()
            }
        }

        override suspend fun lastPerformance(exerciseId: String): List<LoggedSet> {
            val sessionId = prefillDao.lastCompletedSessionIdFor(exerciseId) ?: return emptyList()
            return prefillDao.setsForExerciseInSession(sessionId, exerciseId).map { it.toDomain() }
        }

        override fun observeSetCountsBySession(): Flow<Map<String, Int>> =
            dao.observeSetCountsBySession().map { rows -> rows.associate { it.sessionId to it.setCount } }

        override suspend fun updateSessionRpe(
            sessionId: String,
            rpe: Double?,
        ) {
            require(rpe == null || rpe in Rpe.MIN..Rpe.MAX) { "rpe must be within ${Rpe.MIN}..${Rpe.MAX}" }
            dao.updateSessionRpe(sessionId, rpe, clock.millis())
        }

        override suspend fun updateSessionNote(
            sessionId: String,
            note: String?,
        ) {
            dao.updateSessionNote(sessionId, note?.trim()?.takeIf { it.isNotEmpty() }, clock.millis())
        }

        override suspend fun updateSessionDetails(
            sessionId: String,
            startedAt: Instant,
            endedAt: Instant,
            rpe: Double?,
            note: String?,
        ) {
            require(endedAt.isAfter(startedAt)) { "endedAt must be after startedAt" }
            require(rpe == null || rpe in Rpe.MIN..Rpe.MAX) { "rpe must be within ${Rpe.MIN}..${Rpe.MAX}" }
            transactor.immediate {
                val session = dao.findSession(sessionId) ?: return@immediate
                dao.updateSession(
                    session.copy(
                        startedAt = startedAt.toEpochMilli(),
                        endedAt = endedAt.toEpochMilli(),
                        rpe = rpe,
                        note = note?.trim()?.takeIf { it.isNotEmpty() },
                        updatedAt = clock.millis(),
                    ),
                )
            }
        }

        override suspend fun startSessionFromTemplate(templateId: String): Session =
            transactor.immediate {
                check(dao.activeSessionId() == null) { "A session is already in progress" }
                val template =
                    planDao.findDayTemplate(templateId)
                        ?: error("day template not found: $templateId")
                val now = clock.millis()
                val session =
                    SessionEntity(
                        id = UUID.randomUUID().toString(),
                        templateId = templateId,
                        templateNameSnapshot = template.name,
                        startedAt = now,
                        endedAt = null,
                        note = null,
                        rpe = null,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                    )
                dao.insertSession(session)
                planDao.templateExercisesFor(templateId).forEach { te ->
                    // live, ordered by position
                    dao.insertSessionExercise(
                        SessionExerciseEntity(
                            id = UUID.randomUUID().toString(),
                            sessionId = session.id,
                            exerciseId = te.exerciseId,
                            position = te.position,
                            targetSets = te.targetSets,
                            targetRepsMin = te.targetRepsMin,
                            targetRepsMax = te.targetRepsMax,
                            createdAt = now,
                            updatedAt = now,
                            deletedAt = null,
                        ),
                    )
                }
                session.toDomain()
            }
    }
