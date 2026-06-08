package de.simiil.liftlog.testing.fakes

import de.simiil.liftlog.data.dao.PlanDao
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import kotlinx.coroutines.flow.Flow

class FakePlanDao : PlanDao {
    val plans = linkedMapOf<String, WorkoutPlanEntity>()
    val dayTemplates = linkedMapOf<String, PlanDayTemplateEntity>()
    val templateExercises = linkedMapOf<String, TemplateExerciseEntity>()

    override fun observePlans(): Flow<List<WorkoutPlanEntity>> = TODO("not used in repository write tests")

    override suspend fun findPlan(id: String): WorkoutPlanEntity? =
        plans[id]?.takeIf { it.deletedAt == null }

    override suspend fun insertPlan(plan: WorkoutPlanEntity) {
        plans[plan.id] = plan
    }

    override suspend fun updatePlan(plan: WorkoutPlanEntity) {
        plans[plan.id] = plan
    }

    override suspend fun insertDayTemplate(template: PlanDayTemplateEntity) {
        dayTemplates[template.id] = template
    }

    override suspend fun insertTemplateExercise(templateExercise: TemplateExerciseEntity) {
        templateExercises[templateExercise.id] = templateExercise
    }

    override suspend fun dayTemplatesForPlan(planId: String): List<PlanDayTemplateEntity> =
        dayTemplates.values.filter { it.planId == planId && it.deletedAt == null }

    override suspend fun templateExercisesFor(templateId: String): List<TemplateExerciseEntity> =
        templateExercises.values.filter { it.templateId == templateId && it.deletedAt == null }

    override suspend fun softDeletePlan(id: String, now: Long) {
        plans[id]?.let { plans[id] = it.copy(deletedAt = now, updatedAt = now) }
    }

    override suspend fun softDeleteDayTemplatesForPlan(planId: String, now: Long) {
        dayTemplates.keys.toList().forEach { id ->
            val t = dayTemplates[id]!!
            if (t.planId == planId && t.deletedAt == null) {
                dayTemplates[id] = t.copy(deletedAt = now, updatedAt = now)
            }
        }
    }

    override suspend fun softDeleteTemplateExercisesForPlan(planId: String, now: Long) {
        // Collect templateIds that belong to the plan (regardless of their own deletedAt)
        val templateIds = dayTemplates.values.filter { it.planId == planId }.map { it.id }.toSet()
        templateExercises.keys.toList().forEach { id ->
            val te = templateExercises[id]!!
            if (te.templateId in templateIds && te.deletedAt == null) {
                templateExercises[id] = te.copy(deletedAt = now, updatedAt = now)
            }
        }
    }
}
