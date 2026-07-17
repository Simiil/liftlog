package de.simiil.liftlog.domain.model

import kotlin.time.Instant

data class TemplateExercise(
    val id: String,
    val templateId: String,
    val exerciseId: String,
    val position: Int,
    val targetSets: Int?,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
