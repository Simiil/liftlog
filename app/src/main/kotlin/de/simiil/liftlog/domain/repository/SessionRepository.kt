package de.simiil.liftlog.domain.repository

import de.simiil.liftlog.domain.model.Session
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
}
