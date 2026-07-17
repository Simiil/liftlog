package de.simiil.liftlog.domain.repository

import de.simiil.liftlog.domain.model.PlanDayTemplate
import de.simiil.liftlog.domain.model.TemplateExercise
import de.simiil.liftlog.domain.model.WorkoutPlan
import kotlinx.coroutines.flow.Flow

/** A plan with its training days, for the Plans list. */
data class PlanWithDays(
    val id: String,
    val name: String,
    val days: List<DaySummary>,
)

data class DaySummary(
    val templateId: String,
    val name: String,
    val exerciseCount: Int,
    val exerciseIds: List<String>, // ordered; VM maps to muscle groups for the sub-line
)

interface PlanRepository {
    fun observePlans(): Flow<List<WorkoutPlan>>

    fun observePlan(id: String): Flow<WorkoutPlan?>

    fun observeDayTemplates(planId: String): Flow<List<PlanDayTemplate>>

    fun observeTemplateExercises(templateId: String): Flow<List<TemplateExercise>>

    /** Day templates' owning plan for Home quick-start: most-recently-used plan, else first plan, else null. */
    fun observeMostUsedOrFirstPlanId(): Flow<String?>

    /** Plans with their nested training days for the Plans list. Live, ordered by position. */
    fun observePlansWithDays(): Flow<List<PlanWithDays>>

    suspend fun createPlan(name: String): WorkoutPlan

    suspend fun renamePlan(
        id: String,
        name: String,
    )

    /** Soft-deletes the plan and cascades to its day templates and their template-exercises (atomic). */
    suspend fun softDeletePlan(id: String)

    /**
     * Idempotent invariant: zero live plans -> create one. No-op if any plan is already live.
     * [name] is trimmed. Called on every app startup and after a backup import (issue #30).
     */
    suspend fun ensureDefaultPlan(name: String)

    /**
     * Soft-deletes the plan (same cascade as [softDeletePlan]) and, if that was the last live
     * plan, atomically seeds a fresh one named [defaultName] in the same transaction — observers
     * never see a zero-plan frame.
     */
    suspend fun softDeletePlanAndEnsureDefault(
        id: String,
        defaultName: String,
    )

    /** Persists [id] as the Plan tab's current selection. */
    suspend fun selectPlan(id: String)

    /**
     * The Plan tab's current plan id: the persisted selection if it still names a live plan,
     * else [observeMostUsedOrFirstPlanId]. Never writes back — a stale selection (soft-deleted
     * plan, unset, or a stale id from a backup import) self-heals purely by re-evaluating on
     * every emission, so a later re-appearance of the same id (e.g. an import round-trip) resumes
     * it automatically.
     */
    fun observeSelectedOrFallbackPlanId(): Flow<String?>

    suspend fun getDayTemplate(id: String): PlanDayTemplate?

    suspend fun createDayTemplate(
        planId: String,
        name: String,
    ): PlanDayTemplate

    suspend fun renameDayTemplate(
        id: String,
        name: String,
    )

    /** Soft-deletes the day template and cascades to its template-exercises (atomic). */
    suspend fun softDeleteDayTemplate(id: String)

    suspend fun addExerciseToTemplate(
        templateId: String,
        exerciseId: String,
    ): TemplateExercise

    suspend fun updateTemplateExerciseTargets(
        id: String,
        targetSets: Int?,
        targetRepsMin: Int?,
        targetRepsMax: Int?,
    )

    suspend fun removeTemplateExercise(id: String)

    /** Persists a new order: position = index of each id in [orderedTemplateExerciseIds]. Atomic. */
    suspend fun reorderTemplateExercises(orderedTemplateExerciseIds: List<String>)

    /** Persists a new order: position = index of each id in [orderedTemplateIds]. Atomic. */
    suspend fun reorderDayTemplates(orderedTemplateIds: List<String>)

    /**
     * Adds each exercise in [exerciseIds] to [templateId]'s day, appended after the current max
     * position in input order. One transaction: dedupes against the day's live (non-deleted)
     * template-exercise rows and against duplicates within [exerciseIds] itself, so the check and
     * the insert are race-free. New rows get null targets, a fresh UUID, and the current clock
     * time.
     */
    suspend fun addExercisesToTemplate(
        templateId: String,
        exerciseIds: List<String>,
    )

    /** Emits [id]'s day template, or null once it's tombstoned (or never existed). */
    fun observeDayTemplate(id: String): Flow<PlanDayTemplate?>
}
