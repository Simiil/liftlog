package de.simiil.liftlog.domain.repository

import de.simiil.liftlog.domain.model.LoggedSet
import de.simiil.liftlog.domain.model.Session
import de.simiil.liftlog.domain.model.SessionExercise
import de.simiil.liftlog.domain.model.SessionWithDetails
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeActiveSession(): Flow<Session?>
    fun observeHistory(): Flow<List<Session>>
    fun observeSessionDetails(id: String): Flow<SessionWithDetails?>
    /** Starts an empty ad-hoc session. Throws IllegalStateException if one is already in progress (single live endedAt IS NULL). */
    suspend fun startEmptySession(): Session
    suspend fun finishSession(id: String)
    /** Soft-deletes the session and cascades to session_exercises and logged_sets (atomic). */
    suspend fun softDeleteSession(id: String)
    suspend fun addExerciseToSession(sessionId: String, exerciseId: String): SessionExercise
    suspend fun logSet(sessionExerciseId: String, weightKg: Double, reps: Int): LoggedSet
    suspend fun updateSet(setId: String, weightKg: Double, reps: Int, rpe: Double?, note: String?)
    suspend fun deleteSet(setId: String)                                   // soft
    suspend fun removeExercise(sessionExerciseId: String)                  // soft + cascade its sets
    suspend fun replaceExercise(sessionExerciseId: String, newExerciseId: String): SessionExercise
    suspend fun lastPerformance(exerciseId: String): List<LoggedSet>
    /** Returns a map of sessionId → live set count for all sessions with at least one live set. */
    fun observeSetCountsBySession(): Flow<Map<String, Int>>

    /**
     * Starts a session by SNAPSHOTTING a day template (02-data-spec §1): copies the template's
     * live exercises into session_exercises (order + targets frozen) and records templateNameSnapshot.
     * Throws IllegalStateException if a session is already in progress.
     */
    suspend fun startSessionFromTemplate(templateId: String): Session
}
