package de.simiil.liftlog.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.simiil.liftlog.BuildConfig
import de.simiil.liftlog.MainViewModel
import de.simiil.liftlog.data.backup.AppInfo
import de.simiil.liftlog.data.db.AppDatabase
import de.simiil.liftlog.data.db.DB_SCHEMA_VERSION
import de.simiil.liftlog.data.db.MIGRATION_1_2
import de.simiil.liftlog.data.db.MIGRATION_2_3
import de.simiil.liftlog.domain.format.LocaleFormatters
import de.simiil.liftlog.notification.NotificationPermissionTick
import de.simiil.liftlog.notification.SessionNotificationBuilder
import de.simiil.liftlog.notification.SessionNotificationCoordinator
import de.simiil.liftlog.notification.SessionNotificationModelProducer
import de.simiil.liftlog.ui.analytics.AnalyticsBrowserViewModel
import de.simiil.liftlog.ui.analytics.ExerciseDetailViewModel
import de.simiil.liftlog.ui.exercises.ExercisePickerViewModel
import de.simiil.liftlog.ui.format.AndroidLocaleFormatters
import de.simiil.liftlog.ui.history.HistoryViewModel
import de.simiil.liftlog.ui.home.HomeViewModel
import de.simiil.liftlog.ui.plans.DayEditorViewModel
import de.simiil.liftlog.ui.plans.PlanViewModel
import de.simiil.liftlog.ui.session.ActiveSessionViewModel
import de.simiil.liftlog.ui.session.SessionDetailViewModel
import de.simiil.liftlog.ui.settings.AndroidDocumentIo
import de.simiil.liftlog.ui.settings.DocumentIo
import de.simiil.liftlog.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Android platform leaf. Beyond the mandated DB/DataStore/AppInfo triple this also (temporarily)
 * hosts everything that still references androidMain-only types: `ExerciseSeeder` (reads
 * `context.assets`), the UI bindings (DocumentIo, name resolvers, LocaleFormatters), the
 * notification services, and all ViewModels. PR5 moves the UI/VM/seeder definitions back into a
 * common module once their types become common-visible.
 */
actual val platformModule: Module =
    module {
        // ── DB / DataStore / AppInfo (the KMP platform triple) ──────────────────
        single {
            Room
                .databaseBuilder(androidContext(), AppDatabase::class.java, "liftlog.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
        single<DataStore<Preferences>> {
            PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile("settings") }
        }
        single { AppInfo(name = "LiftLog", versionName = BuildConfig.VERSION_NAME, dbSchemaVersion = DB_SCHEMA_VERSION) }

        // ── UI-adjacent Android bindings (name resolvers + ExerciseSeeder + BackupRepository
        //    moved to common in PR5; these two still need a Context) ───────────────
        factory<DocumentIo> { AndroidDocumentIo(androidContext()) } // unscoped
        single<LocaleFormatters> { AndroidLocaleFormatters(androidContext()) }

        // ── Android-only notification services (stay androidMain forever) ───────
        single { SessionNotificationCoordinator(androidContext(), get(), get(), get(), get(AppScope)) }
        single { SessionNotificationBuilder(androidContext()) }
        single { NotificationPermissionTick() }
        single { SessionNotificationModelProducer(get(), get(), get(), get(), get()) }

        // ── ViewModels (former viewModelModule; PR5 unparks to common) ──────────
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
