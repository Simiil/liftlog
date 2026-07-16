package de.simiil.liftlog.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.simiil.liftlog.data.backup.AppInfo
import de.simiil.liftlog.data.db.AppDatabase
import de.simiil.liftlog.data.db.DB_SCHEMA_VERSION
import de.simiil.liftlog.data.db.MIGRATION_1_2
import de.simiil.liftlog.data.db.MIGRATION_2_3
import de.simiil.liftlog.ui.settings.DocumentIo
import de.simiil.liftlog.ui.settings.IosDocumentIo
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * iOS platform leaf — compile-only in M7 (no Xcode toolchain here; the gate is the klib compile,
 * not a simulator run). Every line below is exercised/verified on-device in M8. The mandated
 * DB/DataStore/AppInfo triple plus `DocumentIo` (PR5 Task 3) are bound. The common
 * [viewModelModule] (PR5 Task 4) now depends on `LocaleFormatters` (ExercisePickerViewModel) and
 * `NotificationPermissionTick` (ActiveSessionViewModel), so the iOS Koin graph is intentionally
 * incomplete until PR5 Task 5 adds those bindings — see the marker below. KoinGraphTest runs on
 * Android (graph complete there); the iOS gate here is the klib compile, not graph resolution.
 */
actual val platformModule: Module =
    module {
        // TODO(PR5 Task 5): bind IosLocaleFormatters (single<LocaleFormatters>) and the
        // NotificationPermissionTick single so the iOS graph resolves the common viewModelModule.
        single {
            val dbPath =
                NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
                    .first()
                    .toString() + "/liftlog.db"
            Room
                .databaseBuilder<AppDatabase>(name = dbPath)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .setDriver(BundledSQLiteDriver())
                // Dispatchers.IO is JVM/Android-only (internal on Native); Default is the Native-safe
                // query context. M8 revisits (a dedicated background dispatcher) when this runs on-device.
                .setQueryCoroutineContext(Dispatchers.Default)
                .build()
        }
        single<DataStore<Preferences>> {
            PreferenceDataStoreFactory.createWithPath {
                (
                    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
                        .first()
                        .toString() + "/settings.preferences_pb"
                ).toPath()
            }
        }
        // M8: read the real version from the app bundle (CFBundleShortVersionString).
        single { AppInfo(name = "LiftLog", versionName = "0.5.0", dbSchemaVersion = DB_SCHEMA_VERSION) }
        factory<DocumentIo> { IosDocumentIo() } // unscoped, mirrors the Android binding
    }
