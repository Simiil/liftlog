package de.simiil.liftlog.domain.model

import java.time.Instant

data class LoggedSet(
    val id: String,
    val sessionExerciseId: String,
    val weightKg: Double,
    val reps: Int,
    val position: Int,
    val completedAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
