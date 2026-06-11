package de.simiil.liftlog.testing.fakes

import de.simiil.liftlog.data.dao.SessionDao
import de.simiil.liftlog.data.dao.SessionSetCount
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSessionDao : SessionDao {
    val sessions = linkedMapOf<String, SessionEntity>()
    val sessionExercises = linkedMapOf<String, SessionExerciseEntity>()
    val loggedSets = linkedMapOf<String, LoggedSetEntity>()

    /** Settable backing store for set-count projection; preload in tests to drive observeSetCountsBySession(). */
    val setCounts = MutableStateFlow<List<SessionSetCount>>(emptyList())

    override fun observeActiveSession(): Flow<SessionEntity?> = TODO("not used in repository write tests")

    override fun observeHistory(): Flow<List<SessionEntity>> = TODO("not used in repository write tests")

    override fun observeSessionWithDetails(id: String) = TODO("not used in repository write tests")

    override suspend fun activeSessionId(): String? = sessions.values.firstOrNull { it.endedAt == null && it.deletedAt == null }?.id

    override suspend fun findSession(id: String): SessionEntity? = sessions[id]?.takeIf { it.deletedAt == null }

    override suspend fun insertSession(session: SessionEntity) {
        sessions[session.id] = session
    }

    override suspend fun updateSession(session: SessionEntity) {
        sessions[session.id] = session
    }

    override suspend fun insertSessionExercise(sessionExercise: SessionExerciseEntity) {
        sessionExercises[sessionExercise.id] = sessionExercise
    }

    override suspend fun insertLoggedSet(loggedSet: LoggedSetEntity) {
        loggedSets[loggedSet.id] = loggedSet
    }

    override suspend fun softDeleteSession(
        id: String,
        now: Long,
    ) {
        sessions[id]?.let { sessions[id] = it.copy(deletedAt = now, updatedAt = now) }
    }

    override suspend fun softDeleteSessionExercisesFor(
        sessionId: String,
        now: Long,
    ) {
        sessionExercises.keys.toList().forEach { id ->
            val se = sessionExercises[id]!!
            if (se.sessionId == sessionId && se.deletedAt == null) {
                sessionExercises[id] = se.copy(deletedAt = now, updatedAt = now)
            }
        }
    }

    override suspend fun softDeleteLoggedSetsForSession(
        sessionId: String,
        now: Long,
    ) {
        // Collect sessionExerciseIds that belong to the session (regardless of their own deletedAt)
        val seIds =
            sessionExercises.values
                .filter { it.sessionId == sessionId }
                .map { it.id }
                .toSet()
        loggedSets.keys.toList().forEach { id ->
            val ls = loggedSets[id]!!
            if (ls.sessionExerciseId in seIds && ls.deletedAt == null) {
                loggedSets[id] = ls.copy(deletedAt = now, updatedAt = now)
            }
        }
    }

    override suspend fun maxExercisePosition(sessionId: String): Int? =
        sessionExercises.values
            .filter { it.sessionId == sessionId && it.deletedAt == null }
            .maxOfOrNull { it.position }

    override suspend fun maxSetPosition(sessionExerciseId: String): Int? =
        loggedSets.values
            .filter { it.sessionExerciseId == sessionExerciseId && it.deletedAt == null }
            .maxOfOrNull { it.position }

    override suspend fun findSessionExercise(id: String): SessionExerciseEntity? = sessionExercises[id]?.takeIf { it.deletedAt == null }

    override suspend fun findLoggedSet(id: String): LoggedSetEntity? = loggedSets[id]?.takeIf { it.deletedAt == null }

    override suspend fun updateLoggedSet(set: LoggedSetEntity) {
        loggedSets[set.id] = set
    }

    override suspend fun softDeleteLoggedSet(
        id: String,
        now: Long,
    ) {
        loggedSets[id]?.let { loggedSets[id] = it.copy(deletedAt = now, updatedAt = now) }
    }

    override suspend fun softDeleteSessionExercise(
        id: String,
        now: Long,
    ) {
        sessionExercises[id]?.let { sessionExercises[id] = it.copy(deletedAt = now, updatedAt = now) }
    }

    override suspend fun softDeleteLoggedSetsForSessionExercise(
        sessionExerciseId: String,
        now: Long,
    ) {
        loggedSets.keys.toList().forEach { id ->
            val ls = loggedSets[id]!!
            if (ls.sessionExerciseId == sessionExerciseId && ls.deletedAt == null) {
                loggedSets[id] = ls.copy(deletedAt = now, updatedAt = now)
            }
        }
    }

    override fun observeSetCountsBySession(): Flow<List<SessionSetCount>> = setCounts
}
