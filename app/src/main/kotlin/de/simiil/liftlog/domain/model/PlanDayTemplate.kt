package de.simiil.liftlog.domain.model

import java.time.Instant

data class PlanDayTemplate(
    val id: String,
    val planId: String,
    val name: String,
    val position: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
