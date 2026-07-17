package de.simiil.liftlog.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbFile: File =
        InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath("migration-test.db")

    // Room 2.8 driver-based MigrationTestHelper: bound to one real on-disk file (unlike the old
    // SupportSQLiteDatabase-based constructor, the name is no longer per-call) and a SQLiteDriver.
    // `databaseFactory` and `autoMigrationSpecs` are left at their defaults — the default factory
    // reflectively instantiates AppDatabase's generated `_Impl`, which is exactly what Room's
    // classic `MigrationTestHelper(instrumentation, databaseClass)` constructor also did.
    @get:Rule
    val helper =
        MigrationTestHelper(
            instrumentation = InstrumentationRegistry.getInstrumentation(),
            file = dbFile,
            driver = BundledSQLiteDriver(),
            databaseClass = AppDatabase::class,
        )

    @Before
    fun deleteDbFile() {
        // createDatabase(version) requires the configured file to not exist yet; the fixed path is
        // shared by all tests in the class, so clear it (and WAL siblings) before each one.
        dbFile.delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
    }

    @Test
    fun migrate1To2_addsSessionRpe_dropsPerSetRpeNote_keepsSetData() {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, equipment, isBuiltIn, isHidden, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('ex1', 'Bench', 'CHEST', 'BARBELL', 1, 0, 1000, 1000, NULL)",
            )
            execSQL(
                "INSERT INTO sessions (id, templateId, templateNameSnapshot, startedAt, endedAt, note, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('s1', NULL, NULL, 1000, 2000, 'session note', 1000, 2000, NULL)",
            )
            execSQL(
                "INSERT INTO session_exercises " +
                    "(id, sessionId, exerciseId, position, targetSets, targetRepsMin, targetRepsMax, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('se1', 's1', 'ex1', 1, NULL, NULL, NULL, 1000, 1000, NULL)",
            )
            execSQL(
                "INSERT INTO logged_sets " +
                    "(id, sessionExerciseId, weightKg, reps, position, completedAt, rpe, note, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('ls1', 'se1', 82.5, 5, 1, 1500, 8.0, 'grip slipped', 1000, 1100, NULL)",
            )
            execSQL(
                "INSERT INTO logged_sets " +
                    "(id, sessionExerciseId, weightKg, reps, position, completedAt, rpe, note, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('ls2', 'se1', 60.0, 10, 2, 1600, NULL, NULL, 1600, 1700, 1800)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2))

        db
            .prepare(
                "SELECT weightKg, reps, position, completedAt, createdAt, updatedAt, deletedAt FROM logged_sets WHERE id = 'ls1'",
            ).use { stmt ->
                assertTrue(stmt.step())
                assertEquals(82.5, stmt.getDouble(0), 0.0)
                assertEquals(5, stmt.getInt(1))
                assertEquals(1, stmt.getInt(2))
                assertEquals(1500L, stmt.getLong(3))
                assertEquals(1000L, stmt.getLong(4))
                assertEquals(1100L, stmt.getLong(5))
                assertTrue(stmt.isNull(6))
            }
        db.prepare("SELECT deletedAt FROM logged_sets WHERE id = 'ls2'").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(1800L, stmt.getLong(0))
        }
        db.prepare("SELECT COUNT(*) FROM logged_sets").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(2, stmt.getInt(0))
        }
        db.prepare("PRAGMA foreign_key_check(`logged_sets`)").use { stmt ->
            var rowCount = 0
            while (stmt.step()) rowCount++
            assertEquals(0, rowCount)
        }
        db.prepare("SELECT rpe, note FROM sessions WHERE id = 's1'").use { stmt ->
            assertTrue(stmt.step())
            assertTrue(stmt.isNull(0)) // ALTER TABLE ADD COLUMN leaves existing rows null
            assertEquals("session note", stmt.getText(1))
        }
    }

    @Test
    fun migrate2To3_addsClassificationColumns_andSeedStateTable() {
        helper.createDatabase(2).apply {
            execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, equipment, isBuiltIn, isHidden, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('ex1', 'Bench', 'CHEST', 'BARBELL', 1, 0, 1000, 1000, NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3))

        db.prepare("SELECT force, secondaryMuscleGroups FROM exercises WHERE id = 'ex1'").use { stmt ->
            assertTrue(stmt.step())
            assertTrue("existing rows get NULL force", stmt.isNull(0))
            assertEquals("existing rows get empty secondaries", "[]", stmt.getText(1))
        }
        db.prepare("SELECT COUNT(*) FROM seed_state").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("seed_state starts empty so the seeder re-converges", 0, stmt.getInt(0))
        }
    }
}
