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
import de.simiil.liftlog.domain.format.LocaleFormatters
import de.simiil.liftlog.domain.logging.NotificationPermissionTick
import de.simiil.liftlog.ui.format.IosLocaleFormatters
import de.simiil.liftlog.ui.settings.DocumentIo
import de.simiil.liftlog.ui.settings.IosDocumentIo
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal fun iosAppInfo(): AppInfo =
    AppInfo(
        name = "LiftLog",
        versionName =
            (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
                ?: "dev",
        dbSchemaVersion = DB_SCHEMA_VERSION,
    )

/**
 * iOS platform leaf — compile-only in M7 (no Xcode toolchain here; the gate is the klib compile,
 * not a simulator run). Every line below is exercised/verified on-device in M8. The mandated
 * DB/DataStore/AppInfo triple plus `DocumentIo` (PR5 Task 3) and `LocaleFormatters`/
 * `NotificationPermissionTick` (PR5 Task 5) are bound, completing the common [viewModelModule]'s
 * (PR5 Task 4) dependency graph: `LocaleFormatters` feeds `ExercisePickerViewModel`,
 * `NotificationPermissionTick` feeds `ActiveSessionViewModel`. [NotificationPermissionTick] is a
 * pure common class (like on Android) but its single stays per-platform since there is no
 * Android-style notification coordinator to share it with here — no session notification ships on
 * iOS in v1 (see `DeepLinkEffect.ios.kt`/`NotificationPermissionEffect.ios.kt`), so this binding
 * exists solely to satisfy `ActiveSessionViewModel`'s constructor. KoinGraphTest runs on Android
 * (graph complete there); the iOS gate here is the klib compile, not graph resolution — there is
 * no runtime verify() on iOS yet (M8 revisits).
 */
actual val platformModule: Module =
    module {
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
        single { iosAppInfo() }
        factory<DocumentIo> { IosDocumentIo() } // unscoped, mirrors the Android binding
        single<LocaleFormatters> { IosLocaleFormatters() }
        single { NotificationPermissionTick() }
    }
