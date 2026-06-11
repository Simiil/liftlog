package de.simiil.liftlog.domain.model

import kotlinx.serialization.Serializable

/** In-memory, unsaved plan being edited. Persisted atomically by PlanRepository.savePlanDraft. */
@Serializable
data class PlanDraft(
    val planId: String? = null, // null = new plan; else the plan being edited
    val name: String = "",
    val days: List<DayDraft> = emptyList(),
)

@Serializable
data class DayDraft(
    val key: String, // stable UI/reorder key (existing = templateId; new = UUID)
    val templateId: String? = null, // null = not yet persisted
    val name: String = "",
    val items: List<ItemDraft> = emptyList(),
)

@Serializable
data class ItemDraft(
    val key: String, // stable UI/reorder key (existing = template_exercise id; new = UUID)
    val templateExerciseId: String? = null,
    val exerciseId: String,
    val targetSets: Int? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
)
