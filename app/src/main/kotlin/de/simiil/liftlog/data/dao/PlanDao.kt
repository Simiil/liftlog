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
}
