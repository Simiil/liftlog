package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.dao.PlanDao
import de.simiil.liftlog.data.db.Transactor
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import de.simiil.liftlog.data.mapper.toDomain
import de.simiil.liftlog.domain.model.PlanDayTemplate
import de.simiil.liftlog.domain.model.TemplateExercise
import de.simiil.liftlog.domain.model.WorkoutPlan
import de.simiil.liftlog.domain.repository.PlanRepository
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
