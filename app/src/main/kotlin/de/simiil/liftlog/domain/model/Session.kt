package de.simiil.liftlog.domain.model

import java.time.Instant

data class Session(
    val id: String,
    val templateId: String?,
    val templateNameSnapshot: String?,
    val startedAt: Instant,
    val endedAt: Instant?,
    val note: String?,
    val rpe: Double?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
