package de.simiil.liftlog.testing

import de.simiil.liftlog.domain.model.LoggedSet
import de.simiil.liftlog.domain.model.Session
import de.simiil.liftlog.domain.model.SessionExercise
import de.simiil.liftlog.domain.model.SessionWithDetails
import de.simiil.liftlog.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

class FakeSessionRepository : SessionRepository {
    // --- Observable state ---

    val activeSession: MutableStateFlow<Session?> = MutableStateFlow(null)
    val history: MutableStateFlow<List<Session>> = MutableStateFlow(emptyList())
    val setCounts: MutableStateFlow<Map<String, Int>> = MutableStateFlow(emptyMap())

    /**
     * Per-session detail flows. [observeSessionDetails] returns (or creates) the flow for [id].
     * Tests can pre-populate via `details["some-id"]?.value = ...` or by calling
     * [setSessionDetails].
     */
    val details: MutableMap<String, MutableStateFlow<SessionWithDetails?>> = mutableMapOf()

    /** Convenience setter. */
    fun setSessionDetails(
        id: String,
        value: SessionWithDetails?,
    ) {
        details.getOrPut(id) { MutableStateFlow(null) }.value = value
    }

    // --- Call records ---

    val startEmptySessionCalls = mutableListOf<Unit>()
    val finishSessionCalls = mutableListOf<String>()
    val softDeleteSessionCalls = mutableListOf<String>()
    val addExerciseCalls = mutableListOf<Pair<String, String>>()
    val logSetCalls = mutableListOf<Triple<String, Double, Int>>()
    val updateSetCalls = mutableListOf<Triple<String, Double, Int>>()
    val deleteSetCalls = mutableListOf<String>()
    val removeExerciseCalls = mutableListOf<String>()
    val replaceExerciseCalls = mutableListOf<Pair<String, String>>()

    // --- Controllable return values ---

    /** Returned by [lastPerformance]. Tests can set this before calling. */
    var lastPerformanceResult: List<LoggedSet> = emptyList()

    /** Exercise ids passed to [lastPerformance], in call order. */
    val lastPerformanceCalls = mutableListOf<String>()

    private var sessionIdCounter = 0

    // --- SessionRepository implementation ---

    override fun observeActiveSession(): Flow<Session?> = activeSession

    override fun observeHistory(): Flow<List<Session>> = history

    override fun observeSessionDetails(id: String): Flow<SessionWithDetails?> = details.getOrPut(id) { MutableStateFlow(null) }

    override fun observeSetCountsBySession(): Flow<Map<String, Int>> = setCounts

    override suspend fun startEmptySession(): Session {
        startEmptySessionCalls += Unit
        val id = "new-session-${++sessionIdCounter}"
        val now = Instant.now()
        val session =
            Session(
                id = id,
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
        activeSession.value = session
        return session
    }

    override suspend fun finishSession(id: String) {
        finishSessionCalls += id
        val current = activeSession.value
        if (current?.id == id) activeSession.value = null
    }

    override suspend fun softDeleteSession(id: String) {
        softDeleteSessionCalls += id
        if (activeSession.value?.id == id) activeSession.value = null
    }

    override suspend fun addExerciseToSession(
        sessionId: String,
        exerciseId: String,
    ): SessionExercise {
        addExerciseCalls += sessionId to exerciseId
        val now = Instant.now()
        return SessionExercise(
            id = "se-$sessionId-$exerciseId",
            sessionId = sessionId,
            exerciseId = exerciseId,
            position = 0,
            targetSets = null,
            targetRepsMin = null,
            targetRepsMax = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
    }

    override suspend fun logSet(
        sessionExerciseId: String,
        weightKg: Double,
        reps: Int,
    ): LoggedSet {
        logSetCalls += Triple(sessionExerciseId, weightKg, reps)
        val now = Instant.now()
        return LoggedSet(
            id = "set-${logSetCalls.size}",
            sessionExerciseId = sessionExerciseId,
            weightKg = weightKg,
            reps = reps,
            position = logSetCalls.size,
            completedAt = now,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
    }

    override suspend fun updateSet(
        setId: String,
        weightKg: Double,
        reps: Int,
    ) {
        updateSetCalls += Triple(setId, weightKg, reps)
    }

    override suspend fun deleteSet(setId: String) {
        deleteSetCalls += setId
    }

    override suspend fun removeExercise(sessionExerciseId: String) {
        removeExerciseCalls += sessionExerciseId
    }

    override suspend fun replaceExercise(
        sessionExerciseId: String,
        newExerciseId: String,
    ): SessionExercise {
        replaceExerciseCalls += sessionExerciseId to newExerciseId
        val now = Instant.now()
        return SessionExercise(
            id = "se-$sessionExerciseId-replaced",
            sessionId = "unknown",
            exerciseId = newExerciseId,
            position = 0,
            targetSets = null,
            targetRepsMin = null,
            targetRepsMax = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
    }

    override suspend fun lastPerformance(exerciseId: String): List<LoggedSet> {
        lastPerformanceCalls += exerciseId
        return lastPerformanceResult
    }

    val updateSessionRpeCalls = mutableListOf<Pair<String, Double?>>()
    val updateSessionNoteCalls = mutableListOf<Pair<String, String?>>()
    val updateSessionDetailsCalls = mutableListOf<UpdateSessionDetailsCall>()

    data class UpdateSessionDetailsCall(
        val sessionId: String,
        val startedAt: Instant,
        val endedAt: Instant,
        val rpe: Double?,
        val note: String?,
    )

    override suspend fun updateSessionRpe(
        sessionId: String,
        rpe: Double?,
    ) {
        updateSessionRpeCalls += sessionId to rpe
    }

    override suspend fun updateSessionNote(
        sessionId: String,
        note: String?,
    ) {
        updateSessionNoteCalls += sessionId to note
    }

    override suspend fun updateSessionDetails(
        sessionId: String,
        startedAt: Instant,
        endedAt: Instant,
        rpe: Double?,
        note: String?,
    ) {
        updateSessionDetailsCalls += UpdateSessionDetailsCall(sessionId, startedAt, endedAt, rpe, note)
    }

    val startFromTemplateCalls = mutableListOf<String>()

    override suspend fun startSessionFromTemplate(templateId: String): Session {
        startFromTemplateCalls += templateId
        val id = "new-session-${++sessionIdCounter}"
        val now = Instant.now()
        val session =
            Session(
                id = id,
                templateId = templateId,
                templateNameSnapshot = "Snapshot $templateId",
                startedAt = now,
                endedAt = null,
                note = null,
                rpe = null,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        activeSession.value = session
        return session
    }
}
