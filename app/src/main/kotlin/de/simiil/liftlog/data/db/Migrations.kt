package de.simiil.liftlog.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2 (2026-06-11 spec): RPE moves from logged sets to the session.
 * - sessions gains nullable `rpe`
 * - logged_sets drops `rpe` and `note` (recreate; old per-set values discarded by design)
 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN rpe REAL")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `logged_sets_new` (
                   `id` TEXT NOT NULL, `sessionExerciseId` TEXT NOT NULL, `weightKg` REAL NOT NULL,
                   `reps` INTEGER NOT NULL, `position` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL,
                   `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER,
                   PRIMARY KEY(`id`),
                   FOREIGN KEY(`sessionExerciseId`) REFERENCES `session_exercises`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)""",
            )
            db.execSQL(
                """INSERT INTO logged_sets_new
                   (id, sessionExerciseId, weightKg, reps, position, completedAt, createdAt, updatedAt, deletedAt)
                   SELECT id, sessionExerciseId, weightKg, reps, position, completedAt, createdAt, updatedAt, deletedAt
                   FROM logged_sets""",
            )
            db.execSQL("DROP TABLE logged_sets")
            db.execSQL("ALTER TABLE logged_sets_new RENAME TO logged_sets")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_logged_sets_sessionExerciseId` ON `logged_sets` (`sessionExerciseId`)")
        }
    }
