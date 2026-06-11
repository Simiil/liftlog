package de.simiil.liftlog.data.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {
    @Query(
        """SELECT s.id AS sessionId, se.exerciseId AS exerciseId, s.startedAt AS startedAt, ls.weightKg AS weightKg, ls.reps AS reps
           FROM logged_sets ls
           JOIN session_exercises se ON se.id = ls.sessionExerciseId AND se.deletedAt IS NULL
           JOIN sessions s          ON s.id = se.sessionId          AND s.deletedAt IS NULL
           WHERE se.exerciseId = :exerciseId AND ls.deletedAt IS NULL
             AND s.startedAt >= :fromMillis AND s.endedAt IS NOT NULL
           ORDER BY s.startedAt""",
    )
    fun observeSetsForExercise(
        exerciseId: String,
        fromMillis: Long,
    ): Flow<List<SetRow>>

    @Query(
        """SELECT s.id AS sessionId, se.exerciseId AS exerciseId, s.startedAt AS startedAt, ls.weightKg AS weightKg, ls.reps AS reps
           FROM logged_sets ls
           JOIN session_exercises se ON se.id = ls.sessionExerciseId AND se.deletedAt IS NULL
           JOIN sessions s          ON s.id = se.sessionId          AND s.deletedAt IS NULL
           WHERE ls.deletedAt IS NULL
             AND s.startedAt >= :fromMillis AND s.endedAt IS NOT NULL
           ORDER BY s.startedAt""",
    )
    fun observeAllSetsSince(fromMillis: Long): Flow<List<SetRow>>

    @Query(
        """SELECT se.exerciseId AS exerciseId, MAX(s.startedAt) AS lastTrainedAt
           FROM logged_sets ls
           JOIN session_exercises se ON se.id = ls.sessionExerciseId AND se.deletedAt IS NULL
           JOIN sessions s          ON s.id = se.sessionId          AND s.deletedAt IS NULL
           WHERE ls.deletedAt IS NULL AND s.endedAt IS NOT NULL
           GROUP BY se.exerciseId
           ORDER BY lastTrainedAt DESC""",
    )
    fun observeTrainedExercises(): Flow<List<TrainedExerciseRow>>
}
