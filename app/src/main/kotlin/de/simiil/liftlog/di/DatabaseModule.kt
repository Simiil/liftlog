package de.simiil.liftlog.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.simiil.liftlog.BuildConfig
import de.simiil.liftlog.data.backup.AppInfo
import de.simiil.liftlog.data.dao.AnalyticsDao
import de.simiil.liftlog.data.dao.BackupDao
import de.simiil.liftlog.data.dao.ExerciseDao
import de.simiil.liftlog.data.dao.PlanDao
import de.simiil.liftlog.data.dao.PrefillDao
import de.simiil.liftlog.data.dao.SeedStateDao
import de.simiil.liftlog.data.dao.SessionDao
import de.simiil.liftlog.data.db.AppDatabase
import de.simiil.liftlog.data.db.DB_SCHEMA_VERSION
import de.simiil.liftlog.data.db.MIGRATION_1_2
import de.simiil.liftlog.data.db.MIGRATION_2_3
import de.simiil.liftlog.data.db.RoomTransactor
import de.simiil.liftlog.data.db.Transactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, "liftlog.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()

    @Provides fun providePlanDao(db: AppDatabase): PlanDao = db.planDao()

    @Provides fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides fun provideAnalyticsDao(db: AppDatabase): AnalyticsDao = db.analyticsDao()

    @Provides fun providePrefillDao(db: AppDatabase): PrefillDao = db.prefillDao()

    @Provides fun provideBackupDao(db: AppDatabase): BackupDao = db.backupDao()

    @Provides fun provideSeedStateDao(db: AppDatabase): SeedStateDao = db.seedStateDao()

    @Provides @Singleton
    fun provideAppInfo(): AppInfo = AppInfo(name = "LiftLog", versionName = BuildConfig.VERSION_NAME, dbSchemaVersion = DB_SCHEMA_VERSION)

    @Provides @Singleton
    fun provideTransactor(db: AppDatabase): Transactor = RoomTransactor(db)

    @Provides @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
