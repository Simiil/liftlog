package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.backup.AppInfo
import de.simiil.liftlog.data.backup.BackupCodec
import de.simiil.liftlog.data.backup.BackupSnapshot
import de.simiil.liftlog.data.dao.BackupDao
import de.simiil.liftlog.data.seed.ExerciseSeeder
import de.simiil.liftlog.domain.plan.DefaultPlanEnsurer
import de.simiil.liftlog.domain.repository.BackupRepository
import de.simiil.liftlog.domain.repository.ImportSummary
import de.simiil.liftlog.domain.repository.ParseResult
import de.simiil.liftlog.domain.repository.ParsedBackup
import de.simiil.liftlog.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock

class BackupRepositoryImpl(
    private val backupDao: BackupDao,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val appInfo: AppInfo,
    private val defaultPlanEnsurer: DefaultPlanEnsurer,
    private val seeder: ExerciseSeeder,
) : BackupRepository {
    override suspend fun exportToJson(): String =
        withContext(Dispatchers.IO) {
            val snapshot =
                BackupSnapshot(
                    exercises = backupDao.getAllExercises(),
                    workoutPlans = backupDao.getAllWorkoutPlans(),
                    planDayTemplates = backupDao.getAllPlanDayTemplates(),
                    templateExercises = backupDao.getAllTemplateExercises(),
                    sessions = backupDao.getAllSessions(),
                    sessionExercises = backupDao.getAllSessionExercises(),
                    loggedSets = backupDao.getAllLoggedSets(),
                    weightUnit = settingsRepository.weightUnit.first(),
                    theme = settingsRepository.themePreference.first(),
                )
            BackupCodec.encode(snapshot, clock.instant(), appInfo)
        }

    override suspend fun parseImport(json: String): ParseResult =
        withContext(Dispatchers.IO) {
            val result = BackupCodec.decode(json)
            if (result is ParseResult.Ready && backupDao.getActiveSession() != null) {
                ParseResult.BlockedByLiveSession
            } else {
                result
            }
        }

    override suspend fun applyImport(parsed: ParsedBackup): ImportSummary =
        withContext(Dispatchers.IO) {
            val snapshot = parsed as BackupSnapshot
            backupDao.replaceAll(snapshot)
            // replaceAll cleared seed_state; re-converge so a restore from an old backup
            // can't leave stale or missing built-in rows behind (02-data-spec §7).
            seeder.seed()
            settingsRepository.setWeightUnit(snapshot.weightUnit)
            settingsRepository.setThemePreference(snapshot.theme)
            // replaceAll itself is one atomic transaction, but ensure() below runs as a
            // separate follow-up transaction, so a brief zero-plan emission between the two
            // is possible for a zero-plan backup. The UI is designed to tolerate that gap
            // (bare scaffold / day-editor auto-close) — this call reseeds the invisible
            // default-plan invariant immediately after.
            defaultPlanEnsurer.ensure()
            // exportedAt here is apply-time; the UI shows the parseImport summary, so this field is informational only.
            ImportSummary(
                exportedAt = clock.instant(),
                sessions = snapshot.sessions.count { it.deletedAt == null },
                exercises = snapshot.exercises.count { it.deletedAt == null },
                sets = snapshot.loggedSets.count { it.deletedAt == null },
            )
        }
}
