package de.simiil.liftlog.data.dao

import androidx.room.Dao
import androidx.room.Query
import de.simiil.liftlog.data.entity.LoggedSetEntity

@Dao
interface PrefillDao {
    /** Most recent COMPLETED, live session containing this exercise (03-ux-spec §4 pre-fill source). */
    @Query(
        """SELECT s.id FROM sessions s
           JOIN session_exercises se ON se.sessionId = s.id AND se.deletedAt IS NULL
           WHERE se.exerciseId = :exerciseId AND s.deletedAt IS NULL AND s.endedAt IS NOT NULL
           ORDER BY s.startedAt DESC LIMIT 1""",
    )
    suspend fun lastCompletedSessionIdFor(exerciseId: String): String?

    @Query(
        """SELECT ls.* FROM logged_sets ls
           JOIN session_exercises se ON se.id = ls.sessionExerciseId
           WHERE se.sessionId = :sessionId AND se.exerciseId = :exerciseId
             AND ls.deletedAt IS NULL AND se.deletedAt IS NULL
           ORDER BY ls.position""",
    )
    suspend fun setsForExerciseInSession(
        sessionId: String,
        exerciseId: String,
    ): List<LoggedSetEntity>
}
