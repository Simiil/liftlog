package de.simiil.liftlog.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v1 → v2 (2026-06-11 spec): RPE moves from logged sets to the session.
 * - sessions gains nullable `rpe`
 * - logged_sets drops `rpe` and `note` (recreate; old per-set values discarded by design)
 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE sessions ADD COLUMN rpe REAL")
            connection.execSQL(
                """CREATE TABLE IF NOT EXISTS `logged_sets_new` (
                   `id` TEXT NOT NULL, `sessionExerciseId` TEXT NOT NULL, `weightKg` REAL NOT NULL,
                   `reps` INTEGER NOT NULL, `position` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL,
                   `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER,
                   PRIMARY KEY(`id`),
                   FOREIGN KEY(`sessionExerciseId`) REFERENCES `session_exercises`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)""",
            )
            connection.execSQL(
                """INSERT INTO logged_sets_new
                   (id, sessionExerciseId, weightKg, reps, position, completedAt, createdAt, updatedAt, deletedAt)
                   SELECT id, sessionExerciseId, weightKg, reps, position, completedAt, createdAt, updatedAt, deletedAt
                   FROM logged_sets""",
            )
            connection.execSQL("DROP TABLE logged_sets")
            connection.execSQL("ALTER TABLE logged_sets_new RENAME TO logged_sets")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_logged_sets_sessionExerciseId` ON `logged_sets` (`sessionExerciseId`)")
        }
    }

/**
 * v2 → v3 (issue #37): exercise classification extension + seeder version gate.
 * - exercises gains nullable `force` and NOT NULL `secondaryMuscleGroups` (JSON array, default `[]`)
 * - new single-row `seed_state` table (created empty → the next seeder run converges and stamps it)
 */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE exercises ADD COLUMN force TEXT")
            connection.execSQL("ALTER TABLE exercises ADD COLUMN secondaryMuscleGroups TEXT NOT NULL DEFAULT '[]'")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `seed_state` (`id` INTEGER NOT NULL, `appliedSeedVersion` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
        }
    }
