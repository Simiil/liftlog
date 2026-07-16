package de.simiil.liftlog.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.simiil.liftlog.data.backup.AppInfo
import de.simiil.liftlog.data.db.AppDatabase
import de.simiil.liftlog.data.db.DB_SCHEMA_VERSION
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.UUID

/**
 * Overrides for instrumented tests, layered on top of [appModules] in
 * [de.simiil.liftlog.KoinTestApplication]. Everything else in `infraModule` (DAOs,
 * [de.simiil.liftlog.data.db.Transactor], `Clock`, `Json`, the [AppScope] `CoroutineScope`) is
 * identical between prod and test, and resolves against whichever [AppDatabase] instance is bound
 * — so only the three definitions that must differ per-test are overridden here:
 * - [AppDatabase]: an in-memory Room DB instead of the on-disk "liftlog.db" (DAOs above resolve
 *   through `get<AppDatabase>()`, so they automatically pick up this override).
 * - [AppInfo]: a fixed "test" version name instead of [de.simiil.liftlog.BuildConfig.VERSION_NAME].
 * - `DataStore<Preferences>`: a unique file per test instance. Koin's default `startKoin` scope is
 *   process-wide, so without a unique file per test run, Preferences DataStore throws "There are
 *   multiple DataStores active for the same file" once more than one test/app instance touches the
 *   same "settings" file.
 */
val testOverrideModules: List<Module> =
    listOf(
        module {
            single {
                Room
                    .inMemoryDatabaseBuilder(androidContext(), AppDatabase::class.java)
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            }
            single { AppInfo(name = "LiftLog", versionName = "test", dbSchemaVersion = DB_SCHEMA_VERSION) }
            single<DataStore<Preferences>> {
                PreferenceDataStoreFactory.create {
                    androidContext().preferencesDataStoreFile("settings_test_${UUID.randomUUID()}")
                }
            }
        },
    )
