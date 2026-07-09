package de.simiil.liftlog.data.backup

import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Force
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.ImportSummary
import de.simiil.liftlog.domain.repository.InvalidReason
import de.simiil.liftlog.domain.repository.ParseResult
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DateTimeException
import java.time.Instant

/** Pure JSON codec for the versioned backup (02-data-spec §6). No Android, no DB. */
object BackupCodec {
    const val CURRENT_FORMAT_VERSION = 3

    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            encodeDefaults = true
            ignoreUnknownKeys = true // forward-compat: importers ignore unknown fields
        }

    private class UnknownEnumException : RuntimeException()

    fun encode(
        snapshot: BackupSnapshot,
        exportedAt: Instant,
        app: AppInfo,
    ): String {
        val file =
            BackupFile(
                formatVersion = CURRENT_FORMAT_VERSION,
                exportedAt = exportedAt.toString(),
                app = AppInfoDto(app.name, app.versionName, app.dbSchemaVersion),
                settings = SettingsDto(snapshot.weightUnit.name, snapshot.theme.name),
                data =
                    BackupData(
                        exercises = snapshot.exercises.map { it.toDto() },
                        workoutPlans = snapshot.workoutPlans.map { it.toDto() },
                        planDayTemplates = snapshot.planDayTemplates.map { it.toDto() },
                        templateExercises = snapshot.templateExercises.map { it.toDto() },
                        sessions = snapshot.sessions.map { it.toDto() },
                        sessionExercises = snapshot.sessionExercises.map { it.toDto() },
                        loggedSets = snapshot.loggedSets.map { it.toDto() },
                    ),
            )
        return json.encodeToString(file)
    }

    fun decode(text: String): ParseResult {
        val file =
            try {
                json.decodeFromString<BackupFile>(text)
            } catch (e: MissingFieldException) {
                return ParseResult.Invalid(InvalidReason.MISSING_FIELDS)
            } catch (e: SerializationException) {
                return ParseResult.Invalid(InvalidReason.MALFORMED)
            } catch (e: IllegalArgumentException) {
                return ParseResult.Invalid(InvalidReason.MALFORMED)
            }

        if (file.formatVersion > CURRENT_FORMAT_VERSION) return ParseResult.Newer(file.formatVersion)
        if (file.hasDuplicateIds()) return ParseResult.Invalid(InvalidReason.MALFORMED)
        if (!file.fkIntact()) return ParseResult.Invalid(InvalidReason.FK_ORPHAN)

        val snapshot: BackupSnapshot
        val exportedInstant: Instant
        try {
            snapshot = file.toSnapshot()
            exportedInstant = Instant.parse(file.exportedAt)
        } catch (e: UnknownEnumException) {
            return ParseResult.Invalid(InvalidReason.UNKNOWN_ENUM)
        } catch (e: DateTimeException) {
            return ParseResult.Invalid(InvalidReason.BAD_TIMESTAMP)
        } catch (e: ArithmeticException) {
            return ParseResult.Invalid(InvalidReason.BAD_TIMESTAMP)
        }

        val summary =
            ImportSummary(
                exportedAt = exportedInstant,
                sessions = snapshot.sessions.count { it.deletedAt == null },
                exercises = snapshot.exercises.count { it.deletedAt == null },
                sets = snapshot.loggedSets.count { it.deletedAt == null },
            )
        return ParseResult.Ready(snapshot, summary)
    }

    // --- timestamp helpers ---
    private fun Long.iso(): String = Instant.ofEpochMilli(this).toString()

    private fun String.millis(): Long = Instant.parse(this).toEpochMilli() // throws DateTimeException / ArithmeticException

    // --- strict entity-enum lookups ---
    private fun muscle(name: String): MuscleGroup = MuscleGroup.entries.firstOrNull { it.name == name } ?: throw UnknownEnumException()

    private fun equip(name: String): Equipment = Equipment.entries.firstOrNull { it.name == name } ?: throw UnknownEnumException()

    /** Two rows sharing a primary key would abort the import transaction — reject up front. */
    private fun BackupFile.hasDuplicateIds(): Boolean {
        fun dup(ids: List<String>) = ids.size != ids.toHashSet().size
        return dup(data.exercises.map { it.id }) ||
            dup(data.workoutPlans.map { it.id }) ||
            dup(data.planDayTemplates.map { it.id }) ||
            dup(data.templateExercises.map { it.id }) ||
            dup(data.sessions.map { it.id }) ||
            dup(data.sessionExercises.map { it.id }) ||
            dup(data.loggedSets.map { it.id })
    }

    // --- FK integrity (tombstones count as present rows) ---
    private fun BackupFile.fkIntact(): Boolean {
        val ex = data.exercises.mapTo(HashSet()) { it.id }
        val pl = data.workoutPlans.mapTo(HashSet()) { it.id }
        val td = data.planDayTemplates.mapTo(HashSet()) { it.id }
        val ss = data.sessions.mapTo(HashSet()) { it.id }
        val se = data.sessionExercises.mapTo(HashSet()) { it.id }
        if (data.planDayTemplates.any { it.planId !in pl }) return false
        if (data.templateExercises.any { it.templateId !in td || it.exerciseId !in ex }) return false
        if (data.sessions.any { it.templateId != null && it.templateId !in td }) return false
        if (data.sessionExercises.any { it.sessionId !in ss || it.exerciseId !in ex }) return false
        if (data.loggedSets.any { it.sessionExerciseId !in se }) return false
        return true
    }

    private fun BackupFile.toSnapshot() =
        BackupSnapshot(
            exercises = data.exercises.map { it.toEntity() },
            workoutPlans = data.workoutPlans.map { it.toEntity() },
            planDayTemplates = data.planDayTemplates.map { it.toEntity() },
            templateExercises = data.templateExercises.map { it.toEntity() },
            sessions = data.sessions.map { it.toEntity() },
            sessionExercises = data.sessionExercises.map { it.toEntity() },
            loggedSets = data.loggedSets.map { it.toEntity() },
            weightUnit = WeightUnit.fromStorageValue(settings.weightUnit), // lenient
            theme = ThemePreference.fromStorageValue(settings.theme), // lenient
        )

    // --- entity → DTO ---
    private fun ExerciseEntity.toDto() =
        ExerciseDto(
            id = id,
            name = name,
            muscleGroup = muscleGroup.name,
            equipment = equipment.name,
            isBuiltIn = isBuiltIn,
            isHidden = isHidden,
            createdAt = createdAt.iso(),
            updatedAt = updatedAt.iso(),
            deletedAt = deletedAt?.iso(),
            force = force?.name,
            secondaryMuscleGroups = secondaryMuscleGroups.map { it.name },
        )

    private fun WorkoutPlanEntity.toDto() =
        WorkoutPlanDto(
            id,
            name,
            position,
            createdAt.iso(),
            updatedAt.iso(),
            deletedAt?.iso(),
        )

    private fun PlanDayTemplateEntity.toDto() =
        PlanDayTemplateDto(
            id,
            planId,
            name,
            position,
            createdAt.iso(),
            updatedAt.iso(),
            deletedAt?.iso(),
        )

    private fun TemplateExerciseEntity.toDto() =
        TemplateExerciseDto(
            id,
            templateId,
            exerciseId,
            position,
            targetSets,
            targetRepsMin,
            targetRepsMax,
            createdAt.iso(),
            updatedAt.iso(),
            deletedAt?.iso(),
        )

    private fun SessionEntity.toDto() =
        SessionDto(
            id,
            templateId,
            templateNameSnapshot,
            startedAt.iso(),
            endedAt?.iso(),
            note,
            rpe,
            createdAt.iso(),
            updatedAt.iso(),
            deletedAt?.iso(),
        )

    private fun SessionExerciseEntity.toDto() =
        SessionExerciseDto(
            id,
            sessionId,
            exerciseId,
            position,
            targetSets,
            targetRepsMin,
            targetRepsMax,
            createdAt.iso(),
            updatedAt.iso(),
            deletedAt?.iso(),
        )

    private fun LoggedSetEntity.toDto() =
        LoggedSetDto(
            id,
            sessionExerciseId,
            weightKg,
            reps,
            position,
            completedAt.iso(),
            createdAt.iso(),
            updatedAt.iso(),
            deletedAt?.iso(),
        )

    // --- DTO → entity (may throw UnknownEnumException / DateTimeException / ArithmeticException) ---
    private fun ExerciseDto.toEntity() =
        ExerciseEntity(
            id = id,
            name = name,
            muscleGroup = muscle(muscleGroup),
            equipment = equip(equipment),
            isBuiltIn = isBuiltIn,
            isHidden = isHidden,
            createdAt = createdAt.millis(),
            updatedAt = updatedAt.millis(),
            deletedAt = deletedAt?.millis(),
            force = Force.fromStorageValue(force), // lenient: unknown → null
            secondaryMuscleGroups = secondaryMuscleGroups.map { MuscleGroup.fromStorageValue(it) }, // lenient: unknown → OTHER
        )

    private fun WorkoutPlanDto.toEntity() =
        WorkoutPlanEntity(
            id,
            name,
            position,
            createdAt.millis(),
            updatedAt.millis(),
            deletedAt?.millis(),
        )

    private fun PlanDayTemplateDto.toEntity() =
        PlanDayTemplateEntity(
            id,
            planId,
            name,
            position,
            createdAt.millis(),
            updatedAt.millis(),
            deletedAt?.millis(),
        )

    private fun TemplateExerciseDto.toEntity() =
        TemplateExerciseEntity(
            id,
            templateId,
            exerciseId,
            position,
            targetSets,
            targetRepsMin,
            targetRepsMax,
            createdAt.millis(),
            updatedAt.millis(),
            deletedAt?.millis(),
        )

    private fun SessionDto.toEntity() =
        SessionEntity(
            id,
            templateId,
            templateNameSnapshot,
            startedAt.millis(),
            endedAt?.millis(),
            note,
            rpe,
            createdAt.millis(),
            updatedAt.millis(),
            deletedAt?.millis(),
        )

    private fun SessionExerciseDto.toEntity() =
        SessionExerciseEntity(
            id,
            sessionId,
            exerciseId,
            position,
            targetSets,
            targetRepsMin,
            targetRepsMax,
            createdAt.millis(),
            updatedAt.millis(),
            deletedAt?.millis(),
        )

    private fun LoggedSetDto.toEntity() =
        LoggedSetEntity(
            id,
            sessionExerciseId,
            weightKg,
            reps,
            position,
            completedAt.millis(),
            createdAt.millis(),
            updatedAt.millis(),
            deletedAt?.millis(),
        )
}
