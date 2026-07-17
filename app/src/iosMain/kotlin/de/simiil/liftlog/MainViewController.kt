package de.simiil.liftlog

import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.data.seed.ExerciseSeeder
import de.simiil.liftlog.di.AppScope
import de.simiil.liftlog.di.appModules
import de.simiil.liftlog.domain.plan.DefaultPlanEnsurer
import de.simiil.liftlog.ui.LiftLogApp
import de.simiil.liftlog.ui.theme.LiftLogTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools
import platform.UIKit.UIViewController

/**
 * iOS entry point: the M8 Xcode shell's SwiftUI/UIKit host calls this to obtain the root
 * `UIViewController` for the Compose Multiplatform UI tree. Mirrors `MainActivity`'s composition
 * (`LiftLogTheme` fed by `MainViewModel.themePreference`, wrapping `LiftLogApp()`, which builds its
 * own `NavHostController` and wires `DeepLinkEffect` internally — see `ui/LiftLogApp.kt` — so there
 * is nothing platform-specific left for this function to pass in). Two differences from
 * `MainActivity` are forced by there being no `Application.onCreate()` hook on iOS: (1) Koin is
 * started — and the one-time startup seeding (exercise catalog + default plan) run — here, via
 * [startKoinOnce] below, instead of an `Application` subclass; (2) there is no
 * edge-to-edge/status-bar-contrast dance — that machinery (`enableEdgeToEdge`, scrim colors) is
 * Android-only.
 *
 * `koinViewModel()` and `collectAsStateWithLifecycle()` resolve here with no extra `KoinContext`/
 * `KoinApplication` wrapper composable needed:
 * - koin-compose 4.1.1's `currentKoinScope()` (consulted by `koinViewModel()`) falls back to a
 *   process-wide default context when no `LocalKoinScope`/`LocalKoinApplication` has been provided
 *   — `org.koin.compose.KoinContext`'s own doc says "KoinContext is not needed anymore ... Compose
 *   Koin context is setup with StartKoin()" — so a plain top-level [startKoin] (as done here and on
 *   Android in `LiftLogApplication`) is sufficient.
 * - `collectAsStateWithLifecycle()` needs `LocalViewModelStoreOwner`/`LocalLifecycleOwner`, which
 *   Compose Multiplatform's `ComposeUIViewController` supplies itself; every commonMain screen
 *   composable already calls `collectAsStateWithLifecycle()`/`koinViewModel()` the same way (e.g.
 *   `LiftLogNavHost`, `HomeScreen`) and that code was already proven to compile for the iOS targets
 *   in PR5 Task 4, so no fallback to plain `collectAsState()` is needed here either.
 *
 * PascalCase is the standard Compose Multiplatform convention for iOS entry-point factory
 * functions (e.g. JetBrains' own project templates); the `@Suppress` below is needed because
 * ktlint's function-naming rule doesn't special-case it the way it does for `@Composable`
 * functions (this one isn't `@Composable` and doesn't return its own type).
 */
@Suppress("ktlint:standard:function-naming")
fun MainViewController(): UIViewController =
    ComposeUIViewController {
        startKoinOnce()
        val viewModel: MainViewModel = koinViewModel()
        val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
        LiftLogTheme(themePreference = themePreference) {
            LiftLogApp()
        }
    }

/**
 * Guards [startKoin] against `KoinAppAlreadyStartedException` if this function is ever entered
 * again in the same process (e.g. the Xcode host recreating its root view controller). Checks the
 * actual global context — via the same `KoinPlatformTools.defaultContext()` koin-compose itself
 * consults — rather than a local boolean flag, so the guard stays correct even if something else
 * started Koin first.
 *
 * Also runs the one-time startup seeding that `LiftLogApplication.onCreate()` performs on Android:
 * seed the built-in exercise catalog and ensure the default plan exists. Both run inside this
 * once-guard so they fire only on the first Koin start (mirroring a single `onCreate`), and both are
 * idempotent anyway (the seeder is version-gated; `ensure()` no-ops once a default plan exists). The
 * Android-only session-notification coordinator has no iOS counterpart (no session notification in
 * v1), so it is deliberately absent here.
 */
private fun startKoinOnce() {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return
    val koin = startKoin { modules(appModules) }.koin
    koin.get<CoroutineScope>(AppScope).launch {
        koin.get<ExerciseSeeder>().seed()
        koin.get<DefaultPlanEnsurer>().ensure()
    }
}
