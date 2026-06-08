package de.simiil.liftlog.domain.model

import java.time.Instant

data class Exercise(
    val id: String,
    val name: String,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val isBuiltIn: Boolean,
    val isHidden: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
