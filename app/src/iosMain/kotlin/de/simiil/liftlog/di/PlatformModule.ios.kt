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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

// Dedicated DB context: Default is shared with domain computation; DB IO deserves
// its own threads (M7 debt item). 4 matches Room's Android IO parallelism in practice.
@OptIn(DelicateCoroutinesApi::class)
private val dbContext = newFixedThreadPoolContext(4, "liftlog-db")

internal fun iosAppInfo(): AppInfo =
    AppInfo(
        name = "LiftLog",
        versionName =
            (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
                ?: "dev",
        dbSchemaVersion = DB_SCHEMA_VERSION,
    )

/**
 * iOS platform leaf. Runtime behavior is verified manually: the app boots and exercises
 * full flows on the iOS simulator (M8-PR1). `iosSimulatorArm64Test` runs the common suites
 * (fakes/in-memory) plus iosTest unit tests, including automated Room/DataStore/Koin wiring
 * coverage on iOS via `IosKoinGraphTest` (M8-PR2). The mandated DB/DataStore/AppInfo triple
 * plus `DocumentIo`, `LocaleFormatters`, and `NotificationPermissionTick` complete the common
 * dependency graph.
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
                .setQueryCoroutineContext(dbContext)
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
