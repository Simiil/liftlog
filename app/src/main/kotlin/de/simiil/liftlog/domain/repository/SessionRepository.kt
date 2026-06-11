package de.simiil.liftlog.domain.repository

import de.simiil.liftlog.domain.model.LoggedSet
import de.simiil.liftlog.domain.model.Session
import de.simiil.liftlog.domain.model.SessionExercise
import de.simiil.liftlog.domain.model.SessionWithDetails
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SessionRepository {
    fun observeActiveSession(): Flow<Session?>

    fun observeHistory(): Flow<List<Session>>

    fun observeSessionDetails(id: String): Flow<SessionWithDetails?>

    /** Starts an empty ad-hoc session. Throws IllegalStateException if one is already in progress (single live endedAt IS NULL). */
    suspend fun startEmptySession(): Session

    suspend fun finishSession(id: String)

    /** Soft-deletes the session and cascades to session_exercises and logged_sets (atomic). */
    suspend fun softDeleteSession(id: String)

    suspend fun addExerciseToSession(
        sessionId: String,
        exerciseId: String,
    ): SessionExercise

    suspend fun logSet(
        sessionExerciseId: String,
        weightKg: Double,
        reps: Int,
    ): LoggedSet

    suspend fun updateSet(
        setId: String,
        weightKg: Double,
        reps: Int,
    )

    suspend fun deleteSet(setId: String) // soft

    suspend fun removeExercise(sessionExerciseId: String) // soft + cascade its sets

    suspend fun replaceExercise(
        sessionExerciseId: String,
        newExerciseId: String,
    ): SessionExercise

    suspend fun lastPerformance(exerciseId: String): List<LoggedSet>

    /** Returns a map of sessionId → live set count for all sessions with at least one live set. */
    fun observeSetCountsBySession(): Flow<Map<String, Int>>

    /**
     * Starts a session by SNAPSHOTTING a day template (02-data-spec §1): copies the template's
     * live exercises into session_exercises (order + targets frozen) and records templateNameSnapshot.
     * Throws IllegalStateException if a session is already in progress.
     */
    suspend fun startSessionFromTemplate(templateId: String): Session

    /** Sets or clears the workout-level RPE, 6.0–10.0 (UI uses 0.5 steps; null = not rated). Bumps updatedAt. */
    suspend fun updateSessionRpe(
        sessionId: String,
        rpe: Double?,
    )

    /** Sets or clears the workout note. Blank input is stored as null. Bumps updatedAt. */
    suspend fun updateSessionNote(
        sessionId: String,
        note: String?,
    )

    /** Atomic header edit (detail screen): times + rpe + note. Requires endedAt > startedAt. */
    suspend fun updateSessionDetails(
        sessionId: String,
        startedAt: Instant,
        endedAt: Instant,
        rpe: Double?,
        note: String?,
    )
}
