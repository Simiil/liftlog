package de.simiil.liftlog.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import de.simiil.liftlog.data.dao.AnalyticsDao
import de.simiil.liftlog.data.dao.BackupDao
import de.simiil.liftlog.data.dao.ExerciseDao
import de.simiil.liftlog.data.dao.PlanDao
import de.simiil.liftlog.data.dao.PrefillDao
import de.simiil.liftlog.data.dao.SeedStateDao
import de.simiil.liftlog.data.dao.SessionDao
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.SeedStateEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity

/** Room schema version. Stamped into backup headers (02-data-spec §6) as dbSchemaVersion. */
const val DB_SCHEMA_VERSION = 3

@Database(
    entities = [
        ExerciseEntity::class, WorkoutPlanEntity::class, PlanDayTemplateEntity::class,
        TemplateExerciseEntity::class, SessionEntity::class, SessionExerciseEntity::class,
        LoggedSetEntity::class, SeedStateEntity::class,
    ],
    version = DB_SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    abstract fun planDao(): PlanDao

    abstract fun sessionDao(): SessionDao

    abstract fun analyticsDao(): AnalyticsDao

    abstract fun prefillDao(): PrefillDao

    abstract fun backupDao(): BackupDao

    abstract fun seedStateDao(): SeedStateDao
}

// Room's KSP processor generates the per-target `actual object AppDatabaseConstructor` (the
// initialize() that news up the generated AppDatabase_Impl). @Suppress silences the IDE/compiler
// "expect declaration has no actual" warning since the actual is code-generated, not hand-written.
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
