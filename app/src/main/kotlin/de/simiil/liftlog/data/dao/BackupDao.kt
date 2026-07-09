package de.simiil.liftlog.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import de.simiil.liftlog.data.backup.BackupSnapshot
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity

/** Backup-only DAO: reads rows WITHOUT the `deletedAt IS NULL` filter (full-fidelity snapshot;
 *  the seeder's converge pass also reads through tombstones) and the sole place that
 *  hard-deletes (full-replace import). */
@Dao
interface BackupDao {
    @Query("SELECT * FROM exercises")
    suspend fun getAllExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM workout_plans")
    suspend fun getAllWorkoutPlans(): List<WorkoutPlanEntity>

    @Query("SELECT * FROM plan_day_templates")
    suspend fun getAllPlanDayTemplates(): List<PlanDayTemplateEntity>

    @Query("SELECT * FROM template_exercises")
    suspend fun getAllTemplateExercises(): List<TemplateExerciseEntity>

    @Query("SELECT * FROM sessions")
    suspend fun getAllSessions(): List<SessionEntity>

    @Query("SELECT * FROM session_exercises")
    suspend fun getAllSessionExercises(): List<SessionExerciseEntity>

    @Query("SELECT * FROM logged_sets")
    suspend fun getAllLoggedSets(): List<LoggedSetEntity>

    @Query("SELECT * FROM sessions WHERE endedAt IS NULL AND deletedAt IS NULL LIMIT 1")
    suspend fun getActiveSession(): SessionEntity?

    @Insert suspend fun insertExercises(rows: List<ExerciseEntity>)

    @Insert suspend fun insertWorkoutPlans(rows: List<WorkoutPlanEntity>)

    @Insert suspend fun insertPlanDayTemplates(rows: List<PlanDayTemplateEntity>)

    @Insert suspend fun insertTemplateExercises(rows: List<TemplateExerciseEntity>)

    @Insert suspend fun insertSessions(rows: List<SessionEntity>)

    @Insert suspend fun insertSessionExercises(rows: List<SessionExerciseEntity>)

    @Insert suspend fun insertLoggedSets(rows: List<LoggedSetEntity>)

    @Query("DELETE FROM exercises")
    suspend fun deleteAllExercises()

    /** Restore clears the applied seed version so the post-import reseed re-converges built-ins. */
    @Query("DELETE FROM seed_state")
    suspend fun deleteSeedState()

    @Query("DELETE FROM workout_plans")
    suspend fun deleteAllWorkoutPlans()

    @Query("DELETE FROM plan_day_templates")
    suspend fun deleteAllPlanDayTemplates()

    @Query("DELETE FROM template_exercises")
    suspend fun deleteAllTemplateExercises()

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM session_exercises")
    suspend fun deleteAllSessionExercises()

    @Query("DELETE FROM logged_sets")
    suspend fun deleteAllLoggedSets()

    /** Atomic full replace. Delete children→parents, insert parents→children. */
    @Transaction
    suspend fun replaceAll(s: BackupSnapshot) {
        deleteAllLoggedSets()
        deleteAllSessionExercises()
        deleteAllSessions()
        deleteAllTemplateExercises()
        deleteAllPlanDayTemplates()
        deleteAllWorkoutPlans()
        deleteAllExercises()
        deleteSeedState()
        insertExercises(s.exercises)
        insertWorkoutPlans(s.workoutPlans)
        insertPlanDayTemplates(s.planDayTemplates)
        insertTemplateExercises(s.templateExercises)
        insertSessions(s.sessions)
        insertSessionExercises(s.sessionExercises)
        insertLoggedSets(s.loggedSets)
    }
}
