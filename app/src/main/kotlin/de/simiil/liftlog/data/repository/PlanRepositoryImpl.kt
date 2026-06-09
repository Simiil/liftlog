package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.dao.PlanDao
import de.simiil.liftlog.data.db.Transactor
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import de.simiil.liftlog.data.mapper.toDomain
import de.simiil.liftlog.domain.model.PlanDayTemplate
import de.simiil.liftlog.domain.model.PlanDraft
import de.simiil.liftlog.domain.model.TemplateExercise
import de.simiil.liftlog.domain.model.WorkoutPlan
import de.simiil.liftlog.domain.repository.DaySummary
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.domain.repository.PlanWithDays
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class PlanRepositoryImpl @Inject constructor(
    private val dao: PlanDao,
    private val transactor: Transactor,
    private val clock: Clock,
) : PlanRepository {

    override fun observePlans() = dao.observePlans().map { it.map(WorkoutPlanEntity::toDomain) }

    override fun observePlan(id: String) = dao.observePlan(id).map { it?.toDomain() }

    override fun observeDayTemplates(planId: String) =
        dao.observeDayTemplatesForPlan(planId).map { it.map(PlanDayTemplateEntity::toDomain) }

    override fun observeTemplateExercises(templateId: String) =
        dao.observeTemplateExercisesFor(templateId).map { it.map(TemplateExerciseEntity::toDomain) }

    override fun observeMostUsedOrFirstPlanId(): Flow<String?> =
        combine(dao.observeMostRecentlyUsedPlanId(), dao.observeFirstPlanId()) { recent, first ->
            recent.firstOrNull() ?: first.firstOrNull()
        }

    override fun observePlansWithDays(): Flow<List<PlanWithDays>> =
        combine(
            dao.observePlans(),
            dao.observeAllDayTemplates(),
            dao.observeAllTemplateExercises(),
        ) { plans, days, templateExercises ->
            val daysByPlan = days.groupBy { it.planId }
            val exercisesByTemplate = templateExercises.groupBy { it.templateId }
            plans.map { plan ->
                PlanWithDays(
                    id = plan.id,
                    name = plan.name,
                    days = (daysByPlan[plan.id] ?: emptyList()).map { day ->
                        val exercises = exercisesByTemplate[day.id] ?: emptyList()
                        DaySummary(
                            templateId = day.id,
                            name = day.name,
                            exerciseCount = exercises.size,
                            exerciseIds = exercises.map { it.exerciseId },
                        )
                    },
                )
            }
        }

    override suspend fun savePlanDraft(draft: PlanDraft): String = transactor.immediate {
        val now = clock.millis()

        // 1. Plan: insert new, or rename existing if the name changed.
        val planId = if (draft.planId == null) {
            val plan = WorkoutPlanEntity(
                id = UUID.randomUUID().toString(), name = draft.name.trim(),
                position = (dao.maxPlanPosition() ?: -1) + 1,
                createdAt = now, updatedAt = now, deletedAt = null,
            )
            dao.insertPlan(plan)
            plan.id
        } else {
            val existing = dao.findPlan(draft.planId)
            if (existing != null && existing.name != draft.name.trim()) {
                dao.updatePlan(existing.copy(name = draft.name.trim(), updatedAt = now))
            }
            draft.planId
        }

        // 2. Days: reconcile against the live day templates of this plan.
        val existingDays = dao.dayTemplatesForPlan(planId).associateBy { it.id }
        val keptDayIds = mutableSetOf<String>()
        draft.days.forEachIndexed { index, dayDraft ->
            val templateId = if (dayDraft.templateId == null) {
                val day = PlanDayTemplateEntity(
                    id = UUID.randomUUID().toString(), planId = planId, name = dayDraft.name.trim(),
                    position = index, createdAt = now, updatedAt = now, deletedAt = null,
                )
                dao.insertDayTemplate(day)
                day.id
            } else {
                existingDays[dayDraft.templateId]?.let { existing ->
                    dao.updateDayTemplate(
                        existing.copy(name = dayDraft.name.trim(), position = index, updatedAt = now),
                    )
                }
                dayDraft.templateId
            }
            keptDayIds += templateId

            // Exercises for that day: insert new, update existing (position + targets).
            val existingTe = dao.templateExercisesFor(templateId).associateBy { it.id }
            val keptTeIds = mutableSetOf<String>()
            dayDraft.items.forEachIndexed { pos, item ->
                if (item.templateExerciseId == null) {
                    val te = TemplateExerciseEntity(
                        id = UUID.randomUUID().toString(), templateId = templateId,
                        exerciseId = item.exerciseId, position = pos,
                        targetSets = item.targetSets, targetRepsMin = item.targetRepsMin,
                        targetRepsMax = item.targetRepsMax,
                        createdAt = now, updatedAt = now, deletedAt = null,
                    )
                    dao.insertTemplateExercise(te)
                    keptTeIds += te.id
                } else {
                    existingTe[item.templateExerciseId]?.let { existing ->
                        dao.updateTemplateExercise(
                            existing.copy(
                                position = pos, targetSets = item.targetSets,
                                targetRepsMin = item.targetRepsMin, targetRepsMax = item.targetRepsMax,
                                updatedAt = now,
                            ),
                        )
                    }
                    keptTeIds += item.templateExerciseId
                }
            }
            // Soft-delete template-exercises removed from this day.
            existingTe.keys.forEach { id ->
                if (id !in keptTeIds) dao.softDeleteTemplateExercise(id, now)
            }
        }

        // 3. Removed days: soft-delete (cascading to their template-exercises first).
        existingDays.keys.forEach { id ->
            if (id !in keptDayIds) {
                dao.softDeleteTemplateExercisesForTemplate(id, now)
                dao.softDeleteDayTemplate(id, now)
            }
        }

        planId
    }

    override suspend fun createPlan(name: String): WorkoutPlan {
        val now = clock.millis()
        val entity = WorkoutPlanEntity(
            id = UUID.randomUUID().toString(), name = name.trim(),
            position = (dao.maxPlanPosition() ?: -1) + 1,
            createdAt = now, updatedAt = now, deletedAt = null,
        )
        dao.insertPlan(entity)
        return entity.toDomain()
    }

    override suspend fun renamePlan(id: String, name: String) {
        val existing = dao.findPlan(id) ?: return
        dao.updatePlan(existing.copy(name = name.trim(), updatedAt = clock.millis()))
    }

    override suspend fun softDeletePlan(id: String) = transactor.immediate {
        val now = clock.millis()
        dao.softDeleteTemplateExercisesForPlan(id, now)
        dao.softDeleteDayTemplatesForPlan(id, now)
        dao.softDeletePlan(id, now)
    }

    override suspend fun getDayTemplate(id: String): PlanDayTemplate? = dao.findDayTemplate(id)?.toDomain()

    override suspend fun createDayTemplate(planId: String, name: String): PlanDayTemplate {
        val now = clock.millis()
        val entity = PlanDayTemplateEntity(
            id = UUID.randomUUID().toString(), planId = planId, name = name.trim(),
            position = (dao.maxDayTemplatePosition(planId) ?: -1) + 1,
            createdAt = now, updatedAt = now, deletedAt = null,
        )
        dao.insertDayTemplate(entity)
        return entity.toDomain()
    }

    override suspend fun renameDayTemplate(id: String, name: String) {
        val existing = dao.findDayTemplate(id) ?: return
        dao.updateDayTemplate(existing.copy(name = name.trim(), updatedAt = clock.millis()))
    }

    override suspend fun softDeleteDayTemplate(id: String) = transactor.immediate {
        val now = clock.millis()
        dao.softDeleteTemplateExercisesForTemplate(id, now)
        dao.softDeleteDayTemplate(id, now)
    }

    override suspend fun addExerciseToTemplate(templateId: String, exerciseId: String): TemplateExercise {
        val now = clock.millis()
        val entity = TemplateExerciseEntity(
            id = UUID.randomUUID().toString(), templateId = templateId, exerciseId = exerciseId,
            position = (dao.maxTemplateExercisePosition(templateId) ?: -1) + 1,
            targetSets = null, targetRepsMin = null, targetRepsMax = null,
            createdAt = now, updatedAt = now, deletedAt = null,
        )
        dao.insertTemplateExercise(entity)
        return entity.toDomain()
    }

    override suspend fun updateTemplateExerciseTargets(
        id: String, targetSets: Int?, targetRepsMin: Int?, targetRepsMax: Int?,
    ) {
        val existing = dao.findTemplateExercise(id) ?: return
        dao.updateTemplateExercise(existing.copy(
            targetSets = targetSets, targetRepsMin = targetRepsMin, targetRepsMax = targetRepsMax,
            updatedAt = clock.millis(),
        ))
    }

    override suspend fun removeTemplateExercise(id: String) {
        dao.softDeleteTemplateExercise(id, clock.millis())
    }

    override suspend fun reorderTemplateExercises(orderedTemplateExerciseIds: List<String>) {
        val now = clock.millis()
        transactor.immediate {
            orderedTemplateExerciseIds.forEachIndexed { index, id ->
                dao.updateTemplateExercisePosition(id, index, now)
            }
        }
    }
}
