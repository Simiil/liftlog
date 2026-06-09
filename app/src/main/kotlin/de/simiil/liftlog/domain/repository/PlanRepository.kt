package de.simiil.liftlog.domain.repository

import de.simiil.liftlog.domain.model.PlanDayTemplate
import de.simiil.liftlog.domain.model.TemplateExercise
import de.simiil.liftlog.domain.model.WorkoutPlan
import kotlinx.coroutines.flow.Flow

interface PlanRepository {
    fun observePlans(): Flow<List<WorkoutPlan>>
    fun observePlan(id: String): Flow<WorkoutPlan?>
    fun observeDayTemplates(planId: String): Flow<List<PlanDayTemplate>>
    fun observeTemplateExercises(templateId: String): Flow<List<TemplateExercise>>
    /** Day templates' owning plan for Home quick-start: most-recently-used plan, else first plan, else null. */
    fun observeMostUsedOrFirstPlanId(): Flow<String?>

    suspend fun createPlan(name: String): WorkoutPlan
    suspend fun renamePlan(id: String, name: String)
    /** Soft-deletes the plan and cascades to its day templates and their template-exercises (atomic). */
    suspend fun softDeletePlan(id: String)

    suspend fun getDayTemplate(id: String): PlanDayTemplate?
    suspend fun createDayTemplate(planId: String, name: String): PlanDayTemplate
    suspend fun renameDayTemplate(id: String, name: String)
    /** Soft-deletes the day template and cascades to its template-exercises (atomic). */
    suspend fun softDeleteDayTemplate(id: String)

    suspend fun addExerciseToTemplate(templateId: String, exerciseId: String): TemplateExercise
    suspend fun updateTemplateExerciseTargets(
        id: String, targetSets: Int?, targetRepsMin: Int?, targetRepsMax: Int?,
    )
    suspend fun removeTemplateExercise(id: String)
    /** Persists a new order: position = index of each id in [orderedTemplateExerciseIds]. Atomic. */
    suspend fun reorderTemplateExercises(orderedTemplateExerciseIds: List<String>)
}
