package de.simiil.liftlog

import android.app.Application
import de.simiil.liftlog.di.appModules
import de.simiil.liftlog.di.testOverrideModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Test-only Application, installed by [KoinTestRunner]: starts Koin with the production modules
 * plus [testOverrideModules] layered on top so later definitions win — the in-memory DB, test
 * [de.simiil.liftlog.data.backup.AppInfo], and a unique-file DataStore replace their production
 * counterparts. DI only: no seeding, no notification coordinator (those are started explicitly by
 * [LiftLogApplication], not here).
 */
class KoinTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@KoinTestApplication)
            // Later definitions win: overrides replace the prod DB/DataStore.
            modules(appModules + testOverrideModules)
        }
    }
}
