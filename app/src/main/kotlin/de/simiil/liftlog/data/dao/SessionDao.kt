package de.simiil.liftlog.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL AND deletedAt IS NULL LIMIT 1")
    fun observeActiveSession(): Flow<SessionEntity?>

    @Query("SELECT id FROM sessions WHERE endedAt IS NULL AND deletedAt IS NULL LIMIT 1")
    suspend fun activeSessionId(): String?

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id AND deletedAt IS NULL")
    fun observeSessionWithDetails(id: String): Flow<SessionWithDetailsRelation?>

    @Query("SELECT * FROM sessions WHERE deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeHistory(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id AND deletedAt IS NULL")
    suspend fun findSession(id: String): SessionEntity?

    @Insert suspend fun insertSession(session: SessionEntity)

    @Update suspend fun updateSession(session: SessionEntity)

    @Insert suspend fun insertSessionExercise(sessionExercise: SessionExerciseEntity)

    @Insert suspend fun insertLoggedSet(loggedSet: LoggedSetEntity)

    @Query("UPDATE sessions SET rpe = :rpe, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun updateSessionRpe(
        id: String,
        rpe: Double?,
        now: Long,
    )

    @Query("UPDATE sessions SET note = :note, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun updateSessionNote(
        id: String,
        note: String?,
        now: Long,
    )

    @Query("UPDATE sessions SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteSession(
        id: String,
        now: Long,
    )

    @Query("UPDATE session_exercises SET deletedAt = :now, updatedAt = :now WHERE sessionId = :sessionId AND deletedAt IS NULL")
    suspend fun softDeleteSessionExercisesFor(
        sessionId: String,
        now: Long,
    )

    @Query(
        """UPDATE logged_sets SET deletedAt = :now, updatedAt = :now
           WHERE deletedAt IS NULL
             AND sessionExerciseId IN (SELECT id FROM session_exercises WHERE sessionId = :sessionId)""",
    )
    suspend fun softDeleteLoggedSetsForSession(
        sessionId: String,
        now: Long,
    )

    @Query("SELECT MAX(position) FROM session_exercises WHERE sessionId = :sessionId AND deletedAt IS NULL")
    suspend fun maxExercisePosition(sessionId: String): Int?

    @Query("SELECT MAX(position) FROM logged_sets WHERE sessionExerciseId = :sessionExerciseId AND deletedAt IS NULL")
    suspend fun maxSetPosition(sessionExerciseId: String): Int?

    @Query("SELECT * FROM session_exercises WHERE id = :id AND deletedAt IS NULL")
    suspend fun findSessionExercise(id: String): SessionExerciseEntity?

    @Query("SELECT * FROM logged_sets WHERE id = :id AND deletedAt IS NULL")
    suspend fun findLoggedSet(id: String): LoggedSetEntity?

    @Update suspend fun updateLoggedSet(set: LoggedSetEntity)

    @Query("UPDATE logged_sets SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteLoggedSet(
        id: String,
        now: Long,
    )

    @Query("UPDATE session_exercises SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteSessionExercise(
        id: String,
        now: Long,
    )

    @Query(
        """UPDATE logged_sets SET deletedAt = :now, updatedAt = :now
              WHERE sessionExerciseId = :sessionExerciseId AND deletedAt IS NULL""",
    )
    suspend fun softDeleteLoggedSetsForSessionExercise(
        sessionExerciseId: String,
        now: Long,
    )

    @Query(
        """
        SELECT se.sessionId AS sessionId, COUNT(ls.id) AS setCount
        FROM session_exercises se
        JOIN logged_sets ls ON ls.sessionExerciseId = se.id AND ls.deletedAt IS NULL
        JOIN sessions s     ON s.id = se.sessionId           AND s.deletedAt IS NULL
        WHERE se.deletedAt IS NULL
        GROUP BY se.sessionId
    """,
    )
    fun observeSetCountsBySession(): Flow<List<SessionSetCount>>
}
