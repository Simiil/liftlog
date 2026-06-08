package de.simiil.liftlog.domain.repository

import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.MuscleGroup
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun observeAll(): Flow<List<Exercise>>
    fun observeVisible(): Flow<List<Exercise>>
    /** Creates a custom (non-built-in) exercise. Throws IllegalArgumentException on blank or duplicate (case-insensitive live) name. */
    suspend fun createCustom(name: String, muscleGroup: MuscleGroup, equipment: Equipment): Exercise
    suspend fun setHidden(id: String, hidden: Boolean)
}
