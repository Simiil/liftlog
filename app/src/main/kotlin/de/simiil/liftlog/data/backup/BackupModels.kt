package de.simiil.liftlog.data.backup

import kotlinx.serialization.Serializable

// Wire format for the versioned JSON backup (02-data-spec §6). DTO field order and the
// BackupData key order ARE the serialized layout and are locked by BackupCodecTest's golden
// file — do not reorder fields without regenerating golden-backup.json.

@Serializable
data class BackupFile(
    val formatVersion: Int,
    val exportedAt: String,
    val app: AppInfoDto,
    val settings: SettingsDto,
    val data: BackupData,
)

@Serializable
data class AppInfoDto(
    val name: String,
    val versionName: String,
    val dbSchemaVersion: Int,
)

@Serializable
data class SettingsDto(
    val weightUnit: String,
    val theme: String,
)

@Serializable
data class BackupData(
    val exercises: List<ExerciseDto>,
    val workoutPlans: List<WorkoutPlanDto>,
    val planDayTemplates: List<PlanDayTemplateDto>,
    val templateExercises: List<TemplateExerciseDto>,
    val sessions: List<SessionDto>,
    val sessionExercises: List<SessionExerciseDto>,
    val loggedSets: List<LoggedSetDto>,
)

@Serializable
data class ExerciseDto(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val equipment: String,
    val isBuiltIn: Boolean,
    val isHidden: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

@Serializable
data class WorkoutPlanDto(
    val id: String,
    val name: String,
    val position: Int,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

@Serializable
data class PlanDayTemplateDto(
    val id: String,
    val planId: String,
    val name: String,
    val position: Int,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

@Serializable
data class TemplateExerciseDto(
    val id: String,
    val templateId: String,
    val exerciseId: String,
    val position: Int,
    val targetSets: Int?,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

@Serializable
data class SessionDto(
    val id: String,
    val templateId: String?,
    val templateNameSnapshot: String?,
    val startedAt: String,
    val endedAt: String?,
    val note: String?,
    val rpe: Double? = null, // default = v1-import compat; still always on the wire because the codec sets encodeDefaults = true
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

@Serializable
data class SessionExerciseDto(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val position: Int,
    val targetSets: Int?,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

@Serializable
data class LoggedSetDto(
    val id: String,
    val sessionExerciseId: String,
    val weightKg: Double,
    val reps: Int,
    val position: Int,
    val completedAt: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)
