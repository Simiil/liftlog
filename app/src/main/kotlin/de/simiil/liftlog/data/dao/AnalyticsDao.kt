package de.simiil.liftlog.data.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {
    @Query(
        """SELECT s.id AS sessionId, s.startedAt AS startedAt, ls.weightKg AS weightKg, ls.reps AS reps
           FROM logged_sets ls
           JOIN session_exercises se ON se.id = ls.sessionExerciseId AND se.deletedAt IS NULL
           JOIN sessions s          ON s.id = se.sessionId          AND s.deletedAt IS NULL
           WHERE se.exerciseId = :exerciseId AND ls.deletedAt IS NULL
             AND s.startedAt >= :fromMillis AND s.endedAt IS NOT NULL
           ORDER BY s.startedAt"""
    )
    fun observeSetsForExercise(exerciseId: String, fromMillis: Long): Flow<List<SetRow>>
}
