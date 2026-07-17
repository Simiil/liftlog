package de.simiil.liftlog.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.simiil.liftlog.BuildConfig
import de.simiil.liftlog.data.backup.AppInfo
import de.simiil.liftlog.data.db.AppDatabase
import de.simiil.liftlog.data.db.DB_SCHEMA_VERSION
import de.simiil.liftlog.data.db.MIGRATION_1_2
import de.simiil.liftlog.data.db.MIGRATION_2_3
import de.simiil.liftlog.domain.format.LocaleFormatters
import de.simiil.liftlog.domain.logging.NotificationPermissionTick
import de.simiil.liftlog.notification.SessionNotificationBuilder
import de.simiil.liftlog.notification.SessionNotificationCoordinator
import de.simiil.liftlog.notification.SessionNotificationModelProducer
import de.simiil.liftlog.ui.format.AndroidLocaleFormatters
import de.simiil.liftlog.ui.settings.AndroidDocumentIo
import de.simiil.liftlog.ui.settings.DocumentIo
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android platform leaf. Beyond the mandated DB/DataStore/AppInfo triple it binds the two
 * platform-backed interfaces ([LocaleFormatters], [DocumentIo]) and the Android-only notification
 * services. Everything portable — repositories, seeders, name resolvers, and all ViewModels — lives
 * in the common modules ([infraModule]/[dataModule]/[uiModule]/[viewModelModule]) as of PR5.
 * [NotificationPermissionTick] is a pure common class but its single stays here alongside the other
 * notification bindings (it feeds the Android-only [SessionNotificationCoordinator]).
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

        // ── Platform-backed interfaces (interface common, impl Android) ─────────
        factory<DocumentIo> { AndroidDocumentIo(androidContext()) } // unscoped
        single<LocaleFormatters> { AndroidLocaleFormatters(androidContext()) }

        // ── Android-only notification services (stay androidMain forever) ───────
        single { SessionNotificationCoordinator(androidContext(), get(), get(), get(), get(AppScope)) }
        single { SessionNotificationBuilder(androidContext()) }
        single { NotificationPermissionTick() }
        single { SessionNotificationModelProducer(get(), get(), get(), get(), get()) }
    }
