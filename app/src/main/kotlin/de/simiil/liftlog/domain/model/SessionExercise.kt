package de.simiil.liftlog.domain.model

import kotlin.time.Instant

data class SessionExercise(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val position: Int,
    val targetSets: Int?,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
