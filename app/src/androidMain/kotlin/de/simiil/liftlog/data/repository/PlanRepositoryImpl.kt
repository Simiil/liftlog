package de.simiil.liftlog.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.simiil.liftlog.data.dao.PlanDao
import de.simiil.liftlog.data.db.Transactor
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import de.simiil.liftlog.data.mapper.toDomain
import de.simiil.liftlog.domain.model.PlanDayTemplate
import de.simiil.liftlog.domain.model.TemplateExercise
import de.simiil.liftlog.domain.model.WorkoutPlan
import de.simiil.liftlog.domain.repository.DaySummary
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.domain.repository.PlanWithDays
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.time.Clock

class PlanRepositoryImpl(
    private val dao: PlanDao,
    private val transactor: Transactor,
    private val clock: Clock,
    private val dataStore: DataStore<Preferences>,
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
                    days =
                        (daysByPlan[plan.id] ?: emptyList()).map { day ->
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

    override suspend fun createPlan(name: String): WorkoutPlan {
        val now = clock.now().toEpochMilliseconds()
        val entity =
            WorkoutPlanEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                position = (dao.maxPlanPosition() ?: -1) + 1,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        dao.insertPlan(entity)
        return entity.toDomain()
    }

    override suspend fun renamePlan(
        id: String,
        name: String,
    ) {
        val existing = dao.findPlan(id) ?: return
        dao.updatePlan(existing.copy(name = name.trim(), updatedAt = clock.now().toEpochMilliseconds()))
    }

    override suspend fun softDeletePlan(id: String) =
        transactor.immediate {
            cascadeSoftDeletePlan(id, clock.now().toEpochMilliseconds())
        }

    override suspend fun ensureDefaultPlan(name: String) =
        transactor.immediate {
            if (dao.countLivePlans() > 0) return@immediate
            insertDefaultPlan(name, clock.now().toEpochMilliseconds())
        }

    override suspend fun softDeletePlanAndEnsureDefault(
        id: String,
        defaultName: String,
    ) = transactor.immediate {
        val now = clock.now().toEpochMilliseconds()
        cascadeSoftDeletePlan(id, now)
        if (dao.countLivePlans() == 0) {
            insertDefaultPlan(defaultName, now)
        }
    }

    /** Cascade shared by [softDeletePlan] and [softDeletePlanAndEnsureDefault]: template exercises -> day templates -> plan. */
    private suspend fun cascadeSoftDeletePlan(
        id: String,
        now: Long,
    ) {
        dao.softDeleteTemplateExercisesForPlan(id, now)
        dao.softDeleteDayTemplatesForPlan(id, now)
        dao.softDeletePlan(id, now)
    }

    /** Inserts a fresh plan named [name] at the end of the manual order. Caller owns the transaction. */
    private suspend fun insertDefaultPlan(
        name: String,
        now: Long,
    ) {
        val plan =
            WorkoutPlanEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                position = (dao.maxPlanPosition() ?: -1) + 1,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        dao.insertPlan(plan)
    }

    override suspend fun getDayTemplate(id: String): PlanDayTemplate? = dao.findDayTemplate(id)?.toDomain()

    override suspend fun createDayTemplate(
        planId: String,
        name: String,
    ): PlanDayTemplate {
        val now = clock.now().toEpochMilliseconds()
        val entity =
            PlanDayTemplateEntity(
                id = UUID.randomUUID().toString(),
                planId = planId,
                name = name.trim(),
                position = (dao.maxDayTemplatePosition(planId) ?: -1) + 1,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        dao.insertDayTemplate(entity)
        return entity.toDomain()
    }

    override suspend fun renameDayTemplate(
        id: String,
        name: String,
    ) {
        val existing = dao.findDayTemplate(id) ?: return
        dao.updateDayTemplate(existing.copy(name = name.trim(), updatedAt = clock.now().toEpochMilliseconds()))
    }

    override suspend fun softDeleteDayTemplate(id: String) =
        transactor.immediate {
            val now = clock.now().toEpochMilliseconds()
            dao.softDeleteTemplateExercisesForTemplate(id, now)
            dao.softDeleteDayTemplate(id, now)
        }

    override suspend fun addExerciseToTemplate(
        templateId: String,
        exerciseId: String,
    ): TemplateExercise {
        val now = clock.now().toEpochMilliseconds()
        val entity =
            TemplateExerciseEntity(
                id = UUID.randomUUID().toString(),
                templateId = templateId,
                exerciseId = exerciseId,
                position = (dao.maxTemplateExercisePosition(templateId) ?: -1) + 1,
                targetSets = null,
                targetRepsMin = null,
                targetRepsMax = null,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        dao.insertTemplateExercise(entity)
        return entity.toDomain()
    }

    override suspend fun updateTemplateExerciseTargets(
        id: String,
        targetSets: Int?,
        targetRepsMin: Int?,
        targetRepsMax: Int?,
    ) {
        val existing = dao.findTemplateExercise(id) ?: return
        dao.updateTemplateExercise(
            existing.copy(
                targetSets = targetSets,
                targetRepsMin = targetRepsMin,
                targetRepsMax = targetRepsMax,
                updatedAt = clock.now().toEpochMilliseconds(),
            ),
        )
    }

    override suspend fun removeTemplateExercise(id: String) {
        dao.softDeleteTemplateExercise(id, clock.now().toEpochMilliseconds())
    }

    override suspend fun reorderTemplateExercises(orderedTemplateExerciseIds: List<String>) {
        val now = clock.now().toEpochMilliseconds()
        transactor.immediate {
            orderedTemplateExerciseIds.forEachIndexed { index, id ->
                dao.updateTemplateExercisePosition(id, index, now)
            }
        }
    }

    override suspend fun reorderDayTemplates(orderedTemplateIds: List<String>) {
        val now = clock.now().toEpochMilliseconds()
        transactor.immediate {
            orderedTemplateIds.forEachIndexed { index, id ->
                dao.updateDayTemplatePosition(id, index, now)
            }
        }
    }

    override suspend fun addExercisesToTemplate(
        templateId: String,
        exerciseIds: List<String>,
    ) {
        transactor.immediate {
            val now = clock.now().toEpochMilliseconds()
            val liveExerciseIds = dao.templateExercisesFor(templateId).map { it.exerciseId }.toSet()
            var nextPosition = (dao.maxTemplateExercisePosition(templateId) ?: -1) + 1
            val seen = mutableSetOf<String>()
            exerciseIds.forEach { exerciseId ->
                if (exerciseId in liveExerciseIds || !seen.add(exerciseId)) return@forEach
                dao.insertTemplateExercise(
                    TemplateExerciseEntity(
                        id = UUID.randomUUID().toString(),
                        templateId = templateId,
                        exerciseId = exerciseId,
                        position = nextPosition,
                        targetSets = null,
                        targetRepsMin = null,
                        targetRepsMax = null,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                    ),
                )
                nextPosition++
            }
        }
    }

    override fun observeDayTemplate(id: String): Flow<PlanDayTemplate?> = dao.observeDayTemplate(id).map { it?.toDomain() }

    override suspend fun selectPlan(id: String) {
        dataStore.edit { it[KEY_SELECTED_PLAN_ID] = id }
    }

    override fun observeSelectedOrFallbackPlanId(): Flow<String?> =
        combine(
            dataStore.data.map { it[KEY_SELECTED_PLAN_ID] },
            dao.observePlans(),
            observeMostUsedOrFirstPlanId(),
        ) { selectedId, livePlans, fallbackId ->
            if (selectedId != null && livePlans.any { it.id == selectedId }) selectedId else fallbackId
        }.distinctUntilChanged()

    private companion object {
        val KEY_SELECTED_PLAN_ID = stringPreferencesKey("selected_plan_id")
    }
}
