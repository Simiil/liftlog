package de.simiil.liftlog.testing

import de.simiil.liftlog.domain.model.PlanDayTemplate
import de.simiil.liftlog.domain.model.PlanDraft
import de.simiil.liftlog.domain.model.TemplateExercise
import de.simiil.liftlog.domain.model.WorkoutPlan
import de.simiil.liftlog.domain.repository.DaySummary
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.domain.repository.PlanWithDays
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/**
 * In-memory fake for [PlanRepository]. Backed by [MutableStateFlow] maps so
 * all observe-methods emit whenever state changes — ViewModels under test see
 * real emissions without needing Room.
 *
 * Shared across PlansViewModelTest, PlanEditorViewModelTest, and HomeViewModelTest (M3 redesign).
 *
 * **Seeding:** use the suspend API (createPlan / createDayTemplate / addExerciseToTemplate …).
 * The read-only snapshot properties ([plans], [dayTemplates], [templateExercises]) are exposed
 * only for post-write inspection — they are not mutable from the outside, so the backing
 * StateFlows always stay in sync.
 *
 * **Test hook:** [mostUsedOrFirstPlanId] is a public MutableStateFlow intentionally exposed
 * so Task-9 / HomeViewModelTest can control exactly which plan id the Home chips observe.
 */
class FakePlanRepository : PlanRepository {
    // ── backing state ─────────────────────────────────────────────────────

    private val plansFlow = MutableStateFlow<Map<String, WorkoutPlan>>(emptyMap())
    private val dayTemplatesFlow = MutableStateFlow<Map<String, PlanDayTemplate>>(emptyMap())
    private val templateExercisesFlow = MutableStateFlow<Map<String, TemplateExercise>>(emptyMap())

    private val _plans: MutableMap<String, WorkoutPlan> = mutableMapOf()
    private val _dayTemplates: MutableMap<String, PlanDayTemplate> = mutableMapOf()
    private val _templateExercises: MutableMap<String, TemplateExercise> = mutableMapOf()

    /** Read-only snapshot of plans for post-write inspection in tests. */
    val plans: Map<String, WorkoutPlan> get() = _plans

    /** Read-only snapshot of day templates for post-write inspection in tests. */
    val dayTemplates: Map<String, PlanDayTemplate> get() = _dayTemplates

    /** Read-only snapshot of template exercises for post-write inspection in tests. */
    val templateExercises: Map<String, TemplateExercise> get() = _templateExercises

    /**
     * Explicit test hook for [observeMostUsedOrFirstPlanId]. Set this before constructing the
     * ViewModel under test to control which plan id the Home chips observe.
     */
    val mostUsedOrFirstPlanId: MutableStateFlow<String?> = MutableStateFlow(null)

    private fun notifyPlans() {
        plansFlow.value = _plans.toMap()
    }

    private fun notifyDayTemplates() {
        dayTemplatesFlow.value = _dayTemplates.toMap()
    }

    private fun notifyTemplateExercises() {
        templateExercisesFlow.value = _templateExercises.toMap()
    }

    private fun nowInstant(): Instant = Instant.EPOCH

    // ── observe methods ───────────────────────────────────────────────────

    override fun observePlans(): Flow<List<WorkoutPlan>> =
        plansFlow.map { map ->
            map.values.filter { it.deletedAt == null }.sortedBy { it.position }
        }

    override fun observePlan(id: String): Flow<WorkoutPlan?> = plansFlow.map { map -> map[id]?.takeIf { it.deletedAt == null } }

    override fun observeDayTemplates(planId: String): Flow<List<PlanDayTemplate>> =
        dayTemplatesFlow.map { map ->
            map.values
                .filter { it.planId == planId && it.deletedAt == null }
                .sortedBy { it.position }
        }

    override fun observeTemplateExercises(templateId: String): Flow<List<TemplateExercise>> =
        templateExercisesFlow.map { map ->
            map.values
                .filter { it.templateId == templateId && it.deletedAt == null }
                .sortedBy { it.position }
        }

    /**
     * Emits the most-recently-affected (or first live) plan id, or null if none.
     *
     * In the fake we expose [mostUsedOrFirstPlanId] as a settable flow so tests can control
     * exactly what Home chips see. We also derive a best-effort default: the first live plan
     * by position (mirrors the "first plan" fallback in the real impl).
     *
     * Tests that need specific behaviour should set [mostUsedOrFirstPlanId] before construction.
     */
    override fun observeMostUsedOrFirstPlanId(): Flow<String?> =
        combine(mostUsedOrFirstPlanId, plansFlow) { explicit, planMap ->
            explicit
                ?: planMap.values
                    .filter { it.deletedAt == null }
                    .minByOrNull { it.position }
                    ?.id
        }

    override fun observePlansWithDays(): Flow<List<PlanWithDays>> =
        combine(plansFlow, dayTemplatesFlow, templateExercisesFlow) { planMap, dayMap, teMap ->
            val daysByPlan =
                dayMap.values
                    .filter { it.deletedAt == null }
                    .sortedBy { it.position }
                    .groupBy { it.planId }
            val exercisesByTemplate =
                teMap.values
                    .filter { it.deletedAt == null }
                    .sortedBy { it.position }
                    .groupBy { it.templateId }
            planMap.values
                .filter { it.deletedAt == null }
                .sortedBy { it.position }
                .map { plan ->
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

    // ── plan writes ───────────────────────────────────────────────────────

    override suspend fun savePlanDraft(draft: PlanDraft): String {
        val now = nowInstant()

        // 1. Plan: insert new, or rename existing if the name changed.
        val planId =
            if (draft.planId == null) {
                val maxPos = _plans.values.filter { it.deletedAt == null }.maxOfOrNull { it.position } ?: -1
                val plan =
                    WorkoutPlan(
                        id = UUID.randomUUID().toString(),
                        name = draft.name.trim(),
                        position = maxPos + 1,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                    )
                _plans[plan.id] = plan
                plan.id
            } else {
                _plans[draft.planId]?.let { existing ->
                    if (existing.name != draft.name.trim()) {
                        _plans[draft.planId] = existing.copy(name = draft.name.trim(), updatedAt = now)
                    }
                }
                draft.planId
            }
        notifyPlans()

        // 2. Days: reconcile against the live day templates of this plan.
        val existingDayIds =
            _dayTemplates.values
                .filter { it.planId == planId && it.deletedAt == null }
                .map { it.id }
                .toSet()
        val keptDayIds = mutableSetOf<String>()
        draft.days.forEachIndexed { index, dayDraft ->
            val templateId =
                if (dayDraft.templateId == null) {
                    val day =
                        PlanDayTemplate(
                            id = UUID.randomUUID().toString(),
                            planId = planId,
                            name = dayDraft.name.trim(),
                            position = index,
                            createdAt = now,
                            updatedAt = now,
                            deletedAt = null,
                        )
                    _dayTemplates[day.id] = day
                    day.id
                } else {
                    _dayTemplates[dayDraft.templateId]?.let { existing ->
                        _dayTemplates[dayDraft.templateId] =
                            existing.copy(name = dayDraft.name.trim(), position = index, updatedAt = now)
                    }
                    dayDraft.templateId
                }
            keptDayIds += templateId

            // Exercises for that day: insert new, update existing (position + targets).
            val existingTeIds =
                _templateExercises.values
                    .filter { it.templateId == templateId && it.deletedAt == null }
                    .map { it.id }
                    .toSet()
            val keptTeIds = mutableSetOf<String>()
            dayDraft.items.forEachIndexed { pos, item ->
                if (item.templateExerciseId == null) {
                    val te =
                        TemplateExercise(
                            id = UUID.randomUUID().toString(),
                            templateId = templateId,
                            exerciseId = item.exerciseId,
                            position = pos,
                            targetSets = item.targetSets,
                            targetRepsMin = item.targetRepsMin,
                            targetRepsMax = item.targetRepsMax,
                            createdAt = now,
                            updatedAt = now,
                            deletedAt = null,
                        )
                    _templateExercises[te.id] = te
                    keptTeIds += te.id
                } else {
                    _templateExercises[item.templateExerciseId]?.let { existing ->
                        _templateExercises[item.templateExerciseId] =
                            existing.copy(
                                position = pos,
                                targetSets = item.targetSets,
                                targetRepsMin = item.targetRepsMin,
                                targetRepsMax = item.targetRepsMax,
                                updatedAt = now,
                            )
                    }
                    keptTeIds += item.templateExerciseId
                }
            }
            // Soft-delete template-exercises removed from this day.
            existingTeIds.forEach { id ->
                if (id !in keptTeIds) {
                    _templateExercises[id]?.let { _templateExercises[id] = it.copy(deletedAt = now, updatedAt = now) }
                }
            }
        }

        // 3. Removed days: soft-delete (cascading to their template-exercises first).
        existingDayIds.forEach { dayId ->
            if (dayId !in keptDayIds) {
                _templateExercises.keys.toList().forEach { teId ->
                    val te = _templateExercises[teId]!!
                    if (te.templateId == dayId && te.deletedAt == null) {
                        _templateExercises[teId] = te.copy(deletedAt = now, updatedAt = now)
                    }
                }
                _dayTemplates[dayId]?.let { _dayTemplates[dayId] = it.copy(deletedAt = now, updatedAt = now) }
            }
        }

        notifyDayTemplates()
        notifyTemplateExercises()
        return planId
    }

    override suspend fun createPlan(name: String): WorkoutPlan {
        val maxPos =
            _plans.values
                .filter { it.deletedAt == null }
                .maxOfOrNull { it.position } ?: -1
        val now = nowInstant()
        val plan =
            WorkoutPlan(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                position = maxPos + 1,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        _plans[plan.id] = plan
        notifyPlans()
        return plan
    }

    override suspend fun renamePlan(
        id: String,
        name: String,
    ) {
        val existing = _plans[id]?.takeIf { it.deletedAt == null } ?: return
        _plans[id] = existing.copy(name = name.trim(), updatedAt = nowInstant())
        notifyPlans()
    }

    override suspend fun softDeletePlan(id: String) {
        val now = nowInstant()
        // cascade: soft-delete all template exercises then day templates then the plan
        val templateIds =
            _dayTemplates.values
                .filter { it.planId == id }
                .map { it.id }
                .toSet()
        _templateExercises.keys.toList().forEach { teId ->
            val te = _templateExercises[teId]!!
            if (te.templateId in templateIds && te.deletedAt == null) {
                _templateExercises[teId] = te.copy(deletedAt = now, updatedAt = now)
            }
        }
        notifyTemplateExercises()
        _dayTemplates.keys.toList().forEach { dtId ->
            val dt = _dayTemplates[dtId]!!
            if (dt.planId == id && dt.deletedAt == null) {
                _dayTemplates[dtId] = dt.copy(deletedAt = now, updatedAt = now)
            }
        }
        notifyDayTemplates()
        _plans[id]?.let { _plans[id] = it.copy(deletedAt = now, updatedAt = now) }
        notifyPlans()
    }

    override suspend fun ensureDefaultPlan(name: String) {
        if (_plans.values.any { it.deletedAt == null }) return
        insertDefaultPlan(name)
    }

    override suspend fun softDeletePlanAndEnsureDefault(
        id: String,
        defaultName: String,
    ) {
        softDeletePlan(id)
        if (_plans.values.none { it.deletedAt == null }) {
            insertDefaultPlan(defaultName)
        }
    }

    private fun insertDefaultPlan(name: String) {
        val now = nowInstant()
        val maxPos = _plans.values.filter { it.deletedAt == null }.maxOfOrNull { it.position } ?: -1
        val plan =
            WorkoutPlan(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                position = maxPos + 1,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        _plans[plan.id] = plan
        notifyPlans()
    }

    // ── day template writes ───────────────────────────────────────────────

    override suspend fun getDayTemplate(id: String): PlanDayTemplate? = _dayTemplates[id]?.takeIf { it.deletedAt == null }

    override suspend fun createDayTemplate(
        planId: String,
        name: String,
    ): PlanDayTemplate {
        val maxPos =
            _dayTemplates.values
                .filter { it.planId == planId && it.deletedAt == null }
                .maxOfOrNull { it.position } ?: -1
        val now = nowInstant()
        val template =
            PlanDayTemplate(
                id = UUID.randomUUID().toString(),
                planId = planId,
                name = name.trim(),
                position = maxPos + 1,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        _dayTemplates[template.id] = template
        notifyDayTemplates()
        return template
    }

    override suspend fun renameDayTemplate(
        id: String,
        name: String,
    ) {
        val existing = _dayTemplates[id]?.takeIf { it.deletedAt == null } ?: return
        _dayTemplates[id] = existing.copy(name = name.trim(), updatedAt = nowInstant())
        notifyDayTemplates()
    }

    override suspend fun softDeleteDayTemplate(id: String) {
        val now = nowInstant()
        // cascade: soft-delete template exercises first
        _templateExercises.keys.toList().forEach { teId ->
            val te = _templateExercises[teId]!!
            if (te.templateId == id && te.deletedAt == null) {
                _templateExercises[teId] = te.copy(deletedAt = now, updatedAt = now)
            }
        }
        notifyTemplateExercises()
        _dayTemplates[id]?.let { _dayTemplates[id] = it.copy(deletedAt = now, updatedAt = now) }
        notifyDayTemplates()
    }

    // ── template exercise writes ──────────────────────────────────────────

    override suspend fun addExerciseToTemplate(
        templateId: String,
        exerciseId: String,
    ): TemplateExercise {
        val maxPos =
            _templateExercises.values
                .filter { it.templateId == templateId && it.deletedAt == null }
                .maxOfOrNull { it.position } ?: -1
        val now = nowInstant()
        val te =
            TemplateExercise(
                id = UUID.randomUUID().toString(),
                templateId = templateId,
                exerciseId = exerciseId,
                position = maxPos + 1,
                targetSets = null,
                targetRepsMin = null,
                targetRepsMax = null,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        _templateExercises[te.id] = te
        notifyTemplateExercises()
        return te
    }

    override suspend fun updateTemplateExerciseTargets(
        id: String,
        targetSets: Int?,
        targetRepsMin: Int?,
        targetRepsMax: Int?,
    ) {
        val existing = _templateExercises[id]?.takeIf { it.deletedAt == null } ?: return
        _templateExercises[id] =
            existing.copy(
                targetSets = targetSets,
                targetRepsMin = targetRepsMin,
                targetRepsMax = targetRepsMax,
                updatedAt = nowInstant(),
            )
        notifyTemplateExercises()
    }

    override suspend fun removeTemplateExercise(id: String) {
        val now = nowInstant()
        _templateExercises[id]?.let {
            _templateExercises[id] = it.copy(deletedAt = now, updatedAt = now)
        }
        notifyTemplateExercises()
    }

    override suspend fun reorderTemplateExercises(orderedTemplateExerciseIds: List<String>) {
        val now = nowInstant()
        orderedTemplateExerciseIds.forEachIndexed { index, id ->
            _templateExercises[id]?.let {
                _templateExercises[id] = it.copy(position = index, updatedAt = now)
            }
        }
        notifyTemplateExercises()
    }

    // ── plan-tab selection (Task 30/PR1) ────────────────────────────────────

    private val selectedPlanIdFlow = MutableStateFlow<String?>(null)

    /** Read-only snapshot of the persisted selection, for post-write inspection in tests. */
    val selectedPlanId: String? get() = selectedPlanIdFlow.value

    override suspend fun selectPlan(id: String) {
        selectedPlanIdFlow.value = id
    }

    override fun observeSelectedOrFallbackPlanId(): Flow<String?> =
        combine(selectedPlanIdFlow, plansFlow, observeMostUsedOrFirstPlanId()) { selected, planMap, fallbackId ->
            val liveIds = planMap.values.filter { it.deletedAt == null }.map { it.id }.toSet()
            if (selected != null && selected in liveIds) selected else fallbackId
        }.distinctUntilChanged()
}
