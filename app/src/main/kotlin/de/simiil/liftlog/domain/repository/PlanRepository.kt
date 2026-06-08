package de.simiil.liftlog.domain.repository

import de.simiil.liftlog.domain.model.WorkoutPlan
import kotlinx.coroutines.flow.Flow

interface PlanRepository {
    fun observePlans(): Flow<List<WorkoutPlan>>
    suspend fun createPlan(name: String): WorkoutPlan
    /** Soft-deletes the plan and cascades to its day templates and their template-exercises (atomic). */
    suspend fun softDeletePlan(id: String)
}
