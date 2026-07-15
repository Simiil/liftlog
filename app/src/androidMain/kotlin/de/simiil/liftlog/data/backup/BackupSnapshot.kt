package de.simiil.liftlog.data.backup

import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.ParsedBackup

/** App identity stamped into the export header (02-data-spec §6). DI-provided. */
data class AppInfo(
    val name: String,
    val versionName: String,
    val dbSchemaVersion: Int,
)

/** Lossless in-memory image of the whole DB + the two persisted settings.
 *  Holds entities (not domain models) so round-trips are byte-for-byte. */
data class BackupSnapshot(
    val exercises: List<ExerciseEntity>,
    val workoutPlans: List<WorkoutPlanEntity>,
    val planDayTemplates: List<PlanDayTemplateEntity>,
    val templateExercises: List<TemplateExerciseEntity>,
    val sessions: List<SessionEntity>,
    val sessionExercises: List<SessionExerciseEntity>,
    val loggedSets: List<LoggedSetEntity>,
    val weightUnit: WeightUnit,
    val theme: ThemePreference,
) : ParsedBackup
