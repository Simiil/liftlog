package de.simiil.liftlog.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.simiil.liftlog.R
import de.simiil.liftlog.data.db.AppDatabase
import de.simiil.liftlog.data.db.RoomTransactor
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import de.simiil.liftlog.data.repository.BackupRepositoryImpl
import de.simiil.liftlog.data.repository.PlanRepositoryImpl
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.plan.DefaultPlanEnsurer
import de.simiil.liftlog.domain.repository.ParseResult
import de.simiil.liftlog.domain.repository.SettingsRepository
import de.simiil.liftlog.testing.newInMemoryDb
import de.simiil.liftlog.ui.plans.ResourceDefaultPlanNameProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    private lateinit var db: AppDatabase

    // tiny in-file SettingsRepository fake (the unit-test fake lives in src/test, not visible here)
    private class FakeSettings(
        unit: WeightUnit,
        theme: ThemePreference,
    ) : SettingsRepository {
        val unitFlow = MutableStateFlow(unit)
        val themeFlow = MutableStateFlow(theme)
        override val themePreference: Flow<ThemePreference> = themeFlow
        override val weightUnit: Flow<WeightUnit> = unitFlow

        override suspend fun setThemePreference(preference: ThemePreference) {
            themeFlow.value = preference
        }

        override suspend fun setWeightUnit(unit: WeightUnit) {
            unitFlow.value = unit
        }
    }

    // tiny in-file DataStore fake — PlanRepositoryImpl requires one, but selection isn't
    // exercised by this test (the unit-test fake lives in src/test, not visible here).
    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private val appInfo = AppInfo("LiftLog", "0.1.0", 1)
    private val clock = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneOffset.UTC)

    /** Wires a real [DefaultPlanEnsurer] over the test [db] so `applyImport` exercises the same
     *  seeding path production does. */
    private fun defaultPlanEnsurer(): DefaultPlanEnsurer {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val planRepository = PlanRepositoryImpl(db.planDao(), RoomTransactor(db), clock, FakeDataStore())
        return DefaultPlanEnsurer(planRepository, ResourceDefaultPlanNameProvider(context))
    }

    @Before fun setUp() {
        db = newInMemoryDb()
    }

    @After fun tearDown() {
        db.close()
    }

    private suspend fun seed() {
        val dao = db.backupDao()
        dao.insertExercises(
            listOf(
                ExerciseEntity("ex1", "Bench", MuscleGroup.CHEST, Equipment.BARBELL, true, false, 1, 2, null),
                ExerciseEntity("ex2", "Hidden", MuscleGroup.BICEPS, Equipment.DUMBBELL, false, true, 1, 2, null),
                ExerciseEntity("ex3", "Tombstoned", MuscleGroup.BACK, Equipment.CABLE, false, false, 1, 9, 9), // soft-deleted
            ),
        )
        dao.insertWorkoutPlans(
            listOf(
                WorkoutPlanEntity("pl1", "PPL", 0, createdAt = 1, updatedAt = 1, deletedAt = null),
            ),
        )
        dao.insertPlanDayTemplates(
            listOf(
                PlanDayTemplateEntity("td1", "pl1", "Push", 0, createdAt = 1, updatedAt = 1, deletedAt = null),
            ),
        )
        dao.insertTemplateExercises(
            listOf(
                TemplateExerciseEntity(
                    "te1",
                    "td1",
                    "ex1",
                    0,
                    targetSets = 3,
                    targetRepsMin = 5,
                    targetRepsMax = 8,
                    createdAt = 1,
                    updatedAt = 1,
                    deletedAt = null,
                ),
            ),
        )
        dao.insertSessions(
            listOf(
                SessionEntity(
                    "s1",
                    templateId = "td1",
                    templateNameSnapshot = "Push",
                    startedAt = 100,
                    endedAt = 200,
                    note = null,
                    rpe = null,
                    createdAt = 100,
                    updatedAt = 200,
                    deletedAt = null,
                ),
            ),
        )
        dao.insertSessionExercises(
            listOf(
                SessionExerciseEntity("se1", "s1", "ex1", 0, null, null, null, 100, 200, null),
            ),
        )
        dao.insertLoggedSets(
            listOf(
                LoggedSetEntity(
                    "ls1",
                    "se1",
                    82.5,
                    5,
                    1,
                    completedAt = 150,
                    createdAt = 150,
                    updatedAt = 150,
                    deletedAt = null,
                ),
            ),
        )
    }

    @Test
    fun `export then wipe then import restores every row and the settings`() =
        runTest {
            seed()
            val dao = db.backupDao()
            val settings = FakeSettings(WeightUnit.LB, ThemePreference.DARK)
            val repo = BackupRepositoryImpl(dao, settings, clock, appInfo, defaultPlanEnsurer())

            val before = snapshotTables(dao)
            val json = repo.exportToJson()

            // wipe everything and scramble settings
            dao.deleteAllLoggedSets()
            dao.deleteAllSessionExercises()
            dao.deleteAllSessions()
            dao.deleteAllTemplateExercises()
            dao.deleteAllPlanDayTemplates()
            dao.deleteAllWorkoutPlans()
            dao.deleteAllExercises()
            settings.unitFlow.value = WeightUnit.KG
            settings.themeFlow.value = ThemePreference.SYSTEM
            assertTrue(dao.getAllExercises().isEmpty())

            val parsed = repo.parseImport(json)
            assertTrue(parsed is ParseResult.Ready)
            repo.applyImport((parsed as ParseResult.Ready).parsed)

            assertEquals(before, snapshotTables(dao)) // row-for-row, incl. tombstone + hidden
            assertEquals(WeightUnit.LB, settings.unitFlow.value) // settings restored
            assertEquals(ThemePreference.DARK, settings.themeFlow.value)
            assertEquals(2, parsed.summary.exercises) // summary from parseImport: live exercises only (ex3 tombstoned)
        }

    @Test
    fun `import is blocked while a session is in progress`() =
        runTest {
            seed()
            val dao = db.backupDao()
            val settings = FakeSettings(WeightUnit.KG, ThemePreference.SYSTEM)
            val repo = BackupRepositoryImpl(dao, settings, clock, appInfo, defaultPlanEnsurer())
            val json = repo.exportToJson()

            dao.insertSessions(
                listOf(
                    SessionEntity(
                        "live",
                        null,
                        null,
                        startedAt = 300,
                        endedAt = null,
                        note = null,
                        rpe = null,
                        createdAt = 300,
                        updatedAt = 300,
                        deletedAt = null,
                    ),
                ),
            )

            assertEquals(ParseResult.BlockedByLiveSession, repo.parseImport(json))
        }

    @Test
    fun `applyImport with zero plans reseeds a localized default plan`() =
        runTest {
            // No seed() call: db starts empty, so the exported backup has zero plans.
            val dao = db.backupDao()
            val settings = FakeSettings(WeightUnit.KG, ThemePreference.SYSTEM)
            val repo = BackupRepositoryImpl(dao, settings, clock, appInfo, defaultPlanEnsurer())
            val json = repo.exportToJson()

            val parsed = repo.parseImport(json)
            assertTrue(parsed is ParseResult.Ready)
            repo.applyImport((parsed as ParseResult.Ready).parsed)

            val livePlans = db.planDao().observePlans().first()
            assertEquals(1, livePlans.size)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assertEquals(context.getString(R.string.default_plan_name), livePlans[0].name)
        }

    private suspend fun snapshotTables(dao: de.simiil.liftlog.data.dao.BackupDao) =
        listOf(
            dao.getAllExercises(),
            dao.getAllWorkoutPlans(),
            dao.getAllPlanDayTemplates(),
            dao.getAllTemplateExercises(),
            dao.getAllSessions(),
            dao.getAllSessionExercises(),
            dao.getAllLoggedSets(),
        )
}
