package de.simiil.liftlog.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import de.simiil.liftlog.BuildConfig
import de.simiil.liftlog.MainViewModel
import de.simiil.liftlog.data.backup.AppInfo
import de.simiil.liftlog.data.db.AppDatabase
import de.simiil.liftlog.data.db.DB_SCHEMA_VERSION
import de.simiil.liftlog.data.db.MIGRATION_1_2
import de.simiil.liftlog.data.db.MIGRATION_2_3
import de.simiil.liftlog.data.db.RoomTransactor
import de.simiil.liftlog.data.db.Transactor
import de.simiil.liftlog.data.repository.AnalyticsRepositoryImpl
import de.simiil.liftlog.data.repository.BackupRepositoryImpl
import de.simiil.liftlog.data.repository.ExerciseRepositoryImpl
import de.simiil.liftlog.data.repository.PlanRepositoryImpl
import de.simiil.liftlog.data.repository.SessionRepositoryImpl
import de.simiil.liftlog.data.repository.SettingsRepositoryImpl
import de.simiil.liftlog.data.seed.ExerciseSeeder
import de.simiil.liftlog.data.seed.SyntheticHistorySeeder
import de.simiil.liftlog.domain.format.LocaleFormatters
import de.simiil.liftlog.domain.logging.ActiveEntryTracker
import de.simiil.liftlog.domain.plan.DefaultPlanEnsurer
import de.simiil.liftlog.domain.plan.DefaultPlanNameProvider
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.BackupRepository
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.domain.repository.SessionRepository
import de.simiil.liftlog.domain.repository.SettingsRepository
import de.simiil.liftlog.notification.NotificationPermissionTick
import de.simiil.liftlog.notification.SessionNotificationBuilder
import de.simiil.liftlog.notification.SessionNotificationCoordinator
import de.simiil.liftlog.notification.SessionNotificationModelProducer
import de.simiil.liftlog.ui.analytics.AnalyticsBrowserViewModel
import de.simiil.liftlog.ui.analytics.ExerciseDetailViewModel
import de.simiil.liftlog.ui.exercises.ExerciseNameResolver
import de.simiil.liftlog.ui.exercises.ExercisePickerViewModel
import de.simiil.liftlog.ui.exercises.ResourceExerciseNameResolver
import de.simiil.liftlog.ui.format.AndroidLocaleFormatters
import de.simiil.liftlog.ui.history.HistoryViewModel
import de.simiil.liftlog.ui.home.HomeViewModel
import de.simiil.liftlog.ui.plans.DayEditorViewModel
import de.simiil.liftlog.ui.plans.PlanViewModel
import de.simiil.liftlog.ui.plans.ResourceDefaultPlanNameProvider
import de.simiil.liftlog.ui.session.ActiveSessionViewModel
import de.simiil.liftlog.ui.session.SessionDetailViewModel
import de.simiil.liftlog.ui.settings.AndroidDocumentIo
import de.simiil.liftlog.ui.settings.DocumentIo
import de.simiil.liftlog.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

/** Qualifier for the app-lifetime CoroutineScope. */
val AppScope = named("applicationScope")

/** Infra singletons — DB, DAOs, Clock, Json, app scope. (PR4: DB/DataStore providers move to platformModule.) */
val infraModule =
    module {
        single {
            Room
                .databaseBuilder(androidContext(), AppDatabase::class.java, "liftlog.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
        single { get<AppDatabase>().exerciseDao() }
        single { get<AppDatabase>().planDao() }
        single { get<AppDatabase>().sessionDao() }
        single { get<AppDatabase>().analyticsDao() }
        single { get<AppDatabase>().prefillDao() }
        single { get<AppDatabase>().backupDao() }
        single { get<AppDatabase>().seedStateDao() }
        single { AppInfo(name = "LiftLog", versionName = BuildConfig.VERSION_NAME, dbSchemaVersion = DB_SCHEMA_VERSION) }
        single<Transactor> { RoomTransactor(get()) }
        single<Clock> { Clock.System }
        single { Json { ignoreUnknownKeys = true } }
        single(AppScope) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        single<DataStore<Preferences>> {
            PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile("settings") }
        }
    }

/** Repositories, seeders, domain services. */
val dataModule =
    module {
        single<SettingsRepository> { SettingsRepositoryImpl(get()) }
        single<ExerciseRepository> { ExerciseRepositoryImpl(get(), get(), get()) }
        single<PlanRepository> { PlanRepositoryImpl(get(), get(), get(), get()) }
        single<SessionRepository> { SessionRepositoryImpl(get(), get(), get(), get(), get()) }
        single<AnalyticsRepository> { AnalyticsRepositoryImpl(get(), get(), get()) }
        factory<BackupRepository> { BackupRepositoryImpl(get(), get(), get(), get(), get(), get()) } // unscoped
        single { ExerciseSeeder(androidContext(), get(), get(), get(), get(), get()) }
        single { SyntheticHistorySeeder(get(), get()) }
        single { DefaultPlanEnsurer(get(), get()) }
        single { ActiveEntryTracker() }
    }

/** UI-adjacent bindings. */
val uiModule =
    module {
        factory<DocumentIo> { AndroidDocumentIo(androidContext()) } // unscoped
        single<ExerciseNameResolver> { ResourceExerciseNameResolver(androidContext()) }
        single<DefaultPlanNameProvider> { ResourceDefaultPlanNameProvider(androidContext()) }
        single<LocaleFormatters> { AndroidLocaleFormatters(androidContext()) }
    }

/** Android-only services (stays androidMain forever). */
val androidPlatformModule =
    module {
        single { SessionNotificationCoordinator(androidContext(), get(), get(), get(), get(AppScope)) }
        single { SessionNotificationBuilder(androidContext()) }
        single { NotificationPermissionTick() }
        single { SessionNotificationModelProducer(get(), get(), get(), get(), get()) }
    }

val viewModelModule =
    module {
        viewModelOf(::MainViewModel)
        viewModelOf(::SettingsViewModel)
        viewModelOf(::HomeViewModel)
        viewModelOf(::PlanViewModel)
        viewModelOf(::ExercisePickerViewModel)
        viewModelOf(::HistoryViewModel)
        viewModelOf(::AnalyticsBrowserViewModel)
        // SavedStateHandle VMs: resolve the handle from the ViewModel factory extras via get().
        viewModel { DayEditorViewModel(get(), get(), get(), get()) } // debounceMs uses its default
        viewModel { ExerciseDetailViewModel(get(), get(), get(), get(), get()) }
        viewModel { ActiveSessionViewModel(get(), get(), get(), get(), get(), get(), get()) }
        viewModel { SessionDetailViewModel(get(), get(), get(), get(), get()) }
    }

val appModules: List<Module> = listOf(infraModule, dataModule, uiModule, androidPlatformModule, viewModelModule)
