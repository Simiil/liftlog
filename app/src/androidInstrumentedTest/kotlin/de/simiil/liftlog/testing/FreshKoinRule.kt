package de.simiil.liftlog.testing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import de.simiil.liftlog.di.appModules
import de.simiil.liftlog.di.testOverrideModules
import org.junit.rules.ExternalResource
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Restarts Koin with fresh [testOverrideModules] (a brand-new in-memory DB, DataStore file, etc.)
 * before every test method.
 *
 * [de.simiil.liftlog.KoinTestApplication] starts Koin exactly once, in `Application.onCreate()` —
 * but a single instrumentation run executes every test class in ONE process, so without this rule
 * every test after the first would share the same in-memory DB / DataStore singletons, leaking
 * seeded data (exercises, plans, sessions, the selected-plan preference) across tests. This gives
 * each test method its own fresh DI graph, the same isolation guarantee the previous DI harness
 * provided per test.
 *
 * Must run OUTSIDE (a lower `@Rule` order than) any rule that launches the Activity — e.g.
 * `createAndroidComposeRule` — so the fresh graph is in place before Koin-backed ViewModels
 * resolve.
 */
class FreshKoinRule : ExternalResource() {
    override fun before() {
        if (GlobalContext.getOrNull() != null) stopKoin()
        startKoin {
            androidContext(ApplicationProvider.getApplicationContext<Application>())
            modules(appModules + testOverrideModules)
        }
    }
}
