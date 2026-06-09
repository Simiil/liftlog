package de.simiil.liftlog.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {
    @Query("SELECT * FROM workout_plans WHERE deletedAt IS NULL ORDER BY position")
    fun observePlans(): Flow<List<WorkoutPlanEntity>>

    @Query("SELECT * FROM workout_plans WHERE id = :id AND deletedAt IS NULL")
    suspend fun findPlan(id: String): WorkoutPlanEntity?

    @Insert suspend fun insertPlan(plan: WorkoutPlanEntity)
    @Update suspend fun updatePlan(plan: WorkoutPlanEntity)
    @Insert suspend fun insertDayTemplate(template: PlanDayTemplateEntity)
    @Insert suspend fun insertTemplateExercise(templateExercise: TemplateExerciseEntity)

    @Query("SELECT * FROM plan_day_templates WHERE planId = :planId AND deletedAt IS NULL ORDER BY position")
    suspend fun dayTemplatesForPlan(planId: String): List<PlanDayTemplateEntity>

    @Query("SELECT * FROM template_exercises WHERE templateId = :templateId AND deletedAt IS NULL ORDER BY position")
    suspend fun templateExercisesFor(templateId: String): List<TemplateExerciseEntity>

    @Query("UPDATE workout_plans SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeletePlan(id: String, now: Long)

    @Query("UPDATE plan_day_templates SET deletedAt = :now, updatedAt = :now WHERE planId = :planId AND deletedAt IS NULL")
    suspend fun softDeleteDayTemplatesForPlan(planId: String, now: Long)

    @Query(
        """UPDATE template_exercises SET deletedAt = :now, updatedAt = :now
           WHERE deletedAt IS NULL
             AND templateId IN (SELECT id FROM plan_day_templates WHERE planId = :planId)"""
    )
    suspend fun softDeleteTemplateExercisesForPlan(planId: String, now: Long)

    // ── reactive reads for the editor UI ───────────────────────────────
    @Query("SELECT * FROM workout_plans WHERE id = :id AND deletedAt IS NULL")
    fun observePlan(id: String): Flow<WorkoutPlanEntity?>

    @Query("SELECT * FROM plan_day_templates WHERE planId = :planId AND deletedAt IS NULL ORDER BY position")
    fun observeDayTemplatesForPlan(planId: String): Flow<List<PlanDayTemplateEntity>>

    @Query("SELECT * FROM template_exercises WHERE templateId = :templateId AND deletedAt IS NULL ORDER BY position")
    fun observeTemplateExercisesFor(templateId: String): Flow<List<TemplateExerciseEntity>>

    // ── all-rows reactive reads for the Plans list (plans-with-days) ───
    @Query("SELECT * FROM plan_day_templates WHERE deletedAt IS NULL ORDER BY position")
    fun observeAllDayTemplates(): Flow<List<PlanDayTemplateEntity>>

    @Query("SELECT * FROM template_exercises WHERE deletedAt IS NULL ORDER BY position")
    fun observeAllTemplateExercises(): Flow<List<TemplateExerciseEntity>>

    // ── single-row finders (for rename/targets/snapshot title) ─────────
    @Query("SELECT * FROM plan_day_templates WHERE id = :id AND deletedAt IS NULL")
    suspend fun findDayTemplate(id: String): PlanDayTemplateEntity?

    @Query("SELECT * FROM template_exercises WHERE id = :id AND deletedAt IS NULL")
    suspend fun findTemplateExercise(id: String): TemplateExerciseEntity?

    // ── append-at-end positions ────────────────────────────────────────
    @Query("SELECT MAX(position) FROM workout_plans WHERE deletedAt IS NULL")
    suspend fun maxPlanPosition(): Int?

    @Query("SELECT MAX(position) FROM plan_day_templates WHERE planId = :planId AND deletedAt IS NULL")
    suspend fun maxDayTemplatePosition(planId: String): Int?

    @Query("SELECT MAX(position) FROM template_exercises WHERE templateId = :templateId AND deletedAt IS NULL")
    suspend fun maxTemplateExercisePosition(templateId: String): Int?

    // ── updates ────────────────────────────────────────────────────────
    @Update suspend fun updateDayTemplate(template: PlanDayTemplateEntity)
    @Update suspend fun updateTemplateExercise(templateExercise: TemplateExerciseEntity)

    @Query("UPDATE template_exercises SET position = :position, updatedAt = :now WHERE id = :id")
    suspend fun updateTemplateExercisePosition(id: String, position: Int, now: Long)

    // ── single-row / per-template soft deletes ─────────────────────────
    @Query("UPDATE plan_day_templates SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteDayTemplate(id: String, now: Long)

    @Query("""UPDATE template_exercises SET deletedAt = :now, updatedAt = :now
              WHERE templateId = :templateId AND deletedAt IS NULL""")
    suspend fun softDeleteTemplateExercisesForTemplate(templateId: String, now: Long)

    @Query("UPDATE template_exercises SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteTemplateExercise(id: String, now: Long)

    // ── Home quick-start plan selection ────────────────────────────────
    /** id of the LIVE plan owning the most-recently-used template session (newest first).
     *  Joins workout_plans so a soft-deleted plan (whose templates cascade-delete) is skipped. */
    @Query("""
        SELECT wp.id FROM sessions s
        JOIN plan_day_templates pdt ON pdt.id = s.templateId
        JOIN workout_plans wp ON wp.id = pdt.planId AND wp.deletedAt IS NULL
        WHERE s.deletedAt IS NULL AND s.templateId IS NOT NULL
        ORDER BY s.startedAt DESC LIMIT 1
    """)
    fun observeMostRecentlyUsedPlanId(): Flow<List<String>>

    /** id of the first plan by manual order — fallback when no template-started session exists. */
    @Query("SELECT id FROM workout_plans WHERE deletedAt IS NULL ORDER BY position LIMIT 1")
    fun observeFirstPlanId(): Flow<List<String>>
}
