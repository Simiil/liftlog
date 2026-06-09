package de.simiil.liftlog.testing.fakes

import de.simiil.liftlog.data.dao.PlanDao
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class FakePlanDao : PlanDao {
    val plans = linkedMapOf<String, WorkoutPlanEntity>()
    val dayTemplates = linkedMapOf<String, PlanDayTemplateEntity>()
    val templateExercises = linkedMapOf<String, TemplateExerciseEntity>()

    // Backing state flows for observable queries
    private val plansFlow = MutableStateFlow<Map<String, WorkoutPlanEntity>>(emptyMap())
    private val dayTemplatesFlow = MutableStateFlow<Map<String, PlanDayTemplateEntity>>(emptyMap())
    private val templateExercisesFlow = MutableStateFlow<Map<String, TemplateExerciseEntity>>(emptyMap())

    private fun notifyPlans() { plansFlow.value = plans.toMap() }
    private fun notifyDayTemplates() { dayTemplatesFlow.value = dayTemplates.toMap() }
    private fun notifyTemplateExercises() { templateExercisesFlow.value = templateExercises.toMap() }

    // ── existing methods ──────────────────────────────────────────────────

    override fun observePlans(): Flow<List<WorkoutPlanEntity>> =
        plansFlow.map { map ->
            map.values.filter { it.deletedAt == null }.sortedBy { it.position }
        }

    override suspend fun findPlan(id: String): WorkoutPlanEntity? =
        plans[id]?.takeIf { it.deletedAt == null }

    override suspend fun insertPlan(plan: WorkoutPlanEntity) {
        plans[plan.id] = plan
        notifyPlans()
    }

    override suspend fun updatePlan(plan: WorkoutPlanEntity) {
        plans[plan.id] = plan
        notifyPlans()
    }

    override suspend fun insertDayTemplate(template: PlanDayTemplateEntity) {
        dayTemplates[template.id] = template
        notifyDayTemplates()
    }

    override suspend fun insertTemplateExercise(templateExercise: TemplateExerciseEntity) {
        templateExercises[templateExercise.id] = templateExercise
        notifyTemplateExercises()
    }

    override suspend fun dayTemplatesForPlan(planId: String): List<PlanDayTemplateEntity> =
        dayTemplates.values.filter { it.planId == planId && it.deletedAt == null }
            .sortedBy { it.position }

    override suspend fun templateExercisesFor(templateId: String): List<TemplateExerciseEntity> =
        templateExercises.values.filter { it.templateId == templateId && it.deletedAt == null }
            .sortedBy { it.position }

    override suspend fun softDeletePlan(id: String, now: Long) {
        plans[id]?.let { plans[id] = it.copy(deletedAt = now, updatedAt = now) }
        notifyPlans()
    }

    override suspend fun softDeleteDayTemplatesForPlan(planId: String, now: Long) {
        dayTemplates.keys.toList().forEach { id ->
            val t = dayTemplates[id]!!
            if (t.planId == planId && t.deletedAt == null) {
                dayTemplates[id] = t.copy(deletedAt = now, updatedAt = now)
            }
        }
        notifyDayTemplates()
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
        notifyTemplateExercises()
    }

    // ── new methods (Task 2) ──────────────────────────────────────────────

    override fun observePlan(id: String): Flow<WorkoutPlanEntity?> =
        plansFlow.map { map -> map[id]?.takeIf { it.deletedAt == null } }

    override fun observeDayTemplatesForPlan(planId: String): Flow<List<PlanDayTemplateEntity>> =
        dayTemplatesFlow.map { map ->
            map.values.filter { it.planId == planId && it.deletedAt == null }.sortedBy { it.position }
        }

    override fun observeTemplateExercisesFor(templateId: String): Flow<List<TemplateExerciseEntity>> =
        templateExercisesFlow.map { map ->
            map.values.filter { it.templateId == templateId && it.deletedAt == null }.sortedBy { it.position }
        }

    override fun observeAllDayTemplates(): Flow<List<PlanDayTemplateEntity>> =
        dayTemplatesFlow.map { map ->
            map.values.filter { it.deletedAt == null }.sortedBy { it.position }
        }

    override fun observeAllTemplateExercises(): Flow<List<TemplateExerciseEntity>> =
        templateExercisesFlow.map { map ->
            map.values.filter { it.deletedAt == null }.sortedBy { it.position }
        }

    override suspend fun findDayTemplate(id: String): PlanDayTemplateEntity? =
        dayTemplates[id]?.takeIf { it.deletedAt == null }

    override suspend fun findTemplateExercise(id: String): TemplateExerciseEntity? =
        templateExercises[id]?.takeIf { it.deletedAt == null }

    override suspend fun maxPlanPosition(): Int? =
        plans.values.filter { it.deletedAt == null }.maxOfOrNull { it.position }

    override suspend fun maxDayTemplatePosition(planId: String): Int? =
        dayTemplates.values.filter { it.planId == planId && it.deletedAt == null }
            .maxOfOrNull { it.position }

    override suspend fun maxTemplateExercisePosition(templateId: String): Int? =
        templateExercises.values.filter { it.templateId == templateId && it.deletedAt == null }
            .maxOfOrNull { it.position }

    override suspend fun updateDayTemplate(template: PlanDayTemplateEntity) {
        dayTemplates[template.id] = template
        notifyDayTemplates()
    }

    override suspend fun updateTemplateExercise(templateExercise: TemplateExerciseEntity) {
        templateExercises[templateExercise.id] = templateExercise
        notifyTemplateExercises()
    }

    override suspend fun updateTemplateExercisePosition(id: String, position: Int, now: Long) {
        templateExercises[id]?.let {
            templateExercises[id] = it.copy(position = position, updatedAt = now)
        }
        notifyTemplateExercises()
    }

    override suspend fun softDeleteDayTemplate(id: String, now: Long) {
        dayTemplates[id]?.let { dayTemplates[id] = it.copy(deletedAt = now, updatedAt = now) }
        notifyDayTemplates()
    }

    override suspend fun softDeleteTemplateExercisesForTemplate(templateId: String, now: Long) {
        templateExercises.keys.toList().forEach { id ->
            val te = templateExercises[id]!!
            if (te.templateId == templateId && te.deletedAt == null) {
                templateExercises[id] = te.copy(deletedAt = now, updatedAt = now)
            }
        }
        notifyTemplateExercises()
    }

    override suspend fun softDeleteTemplateExercise(id: String, now: Long) {
        templateExercises[id]?.let {
            templateExercises[id] = it.copy(deletedAt = now, updatedAt = now)
        }
        notifyTemplateExercises()
    }

    // ── Home quick-start flows — not needed in JVM repo tests (tested at instrumented DAO level) ──
    override fun observeMostRecentlyUsedPlanId(): Flow<List<String>> =
        TODO("not used in JVM repository tests — tested at instrumented DAO level")

    override fun observeFirstPlanId(): Flow<List<String>> =
        TODO("not used in JVM repository tests — tested at instrumented DAO level")
}
