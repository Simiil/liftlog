package de.simiil.liftlog.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2_addsSessionRpe_dropsPerSetRpeNote_keepsSetData() {
        helper.createDatabase("migration-test", 1).apply {
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

        val db = helper.runMigrationsAndValidate("migration-test", 2, true, MIGRATION_1_2)

        db
            .query(
                "SELECT weightKg, reps, position, completedAt, createdAt, updatedAt, deletedAt FROM logged_sets WHERE id = 'ls1'",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(82.5, c.getDouble(0), 0.0)
                assertEquals(5, c.getInt(1))
                assertEquals(1, c.getInt(2))
                assertEquals(1500L, c.getLong(3))
                assertEquals(1000L, c.getLong(4))
                assertEquals(1100L, c.getLong(5))
                assertTrue(c.isNull(6))
            }
        db.query("SELECT deletedAt FROM logged_sets WHERE id = 'ls2'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1800L, c.getLong(0))
        }
        db.query("SELECT COUNT(*) FROM logged_sets").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }
        db.query("PRAGMA foreign_key_check(`logged_sets`)").use { c -> assertEquals(0, c.count) }
        db.query("SELECT rpe, note FROM sessions WHERE id = 's1'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0)) // ALTER TABLE ADD COLUMN leaves existing rows null
            assertEquals("session note", c.getString(1))
        }
    }

    @Test
    fun migrate2To3_addsClassificationColumns_andSeedStateTable() {
        helper.createDatabase("migration-test-v3", 2).apply {
            execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, equipment, isBuiltIn, isHidden, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('ex1', 'Bench', 'CHEST', 'BARBELL', 1, 0, 1000, 1000, NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate("migration-test-v3", 3, true, MIGRATION_2_3)

        db.query("SELECT force, secondaryMuscleGroups FROM exercises WHERE id = 'ex1'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("existing rows get NULL force", c.isNull(0))
            assertEquals("existing rows get empty secondaries", "[]", c.getString(1))
        }
        db.query("SELECT COUNT(*) FROM seed_state").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("seed_state starts empty so the seeder re-converges", 0, c.getInt(0))
        }
    }
}
