package de.simiil.liftlog.domain.model

import kotlin.time.Instant

data class WorkoutPlan(
    val id: String,
    val name: String,
    val position: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
