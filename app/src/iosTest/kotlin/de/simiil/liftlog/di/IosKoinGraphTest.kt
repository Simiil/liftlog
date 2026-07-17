package de.simiil.liftlog.di

import androidx.lifecycle.SavedStateHandle
import de.simiil.liftlog.MainViewModel
import de.simiil.liftlog.data.seed.SyntheticHistorySeeder
import de.simiil.liftlog.domain.format.LocaleFormatters
import de.simiil.liftlog.domain.logging.ActiveEntryTracker
import de.simiil.liftlog.domain.logging.NotificationPermissionTick
import de.simiil.liftlog.domain.plan.DefaultPlanEnsurer
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.BackupRepository
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.domain.repository.SessionRepository
import de.simiil.liftlog.domain.repository.SettingsRepository
import de.simiil.liftlog.ui.analytics.AnalyticsBrowserViewModel
import de.simiil.liftlog.ui.analytics.ExerciseDetailViewModel
import de.simiil.liftlog.ui.exercises.ExerciseNameResolver
import de.simiil.liftlog.ui.exercises.ExercisePickerViewModel
import de.simiil.liftlog.ui.history.HistoryViewModel
import de.simiil.liftlog.ui.home.HomeViewModel
import de.simiil.liftlog.ui.plans.DayEditorViewModel
import de.simiil.liftlog.ui.plans.PlanViewModel
import de.simiil.liftlog.ui.session.ActiveSessionViewModel
import de.simiil.liftlog.ui.session.SessionDetailViewModel
import de.simiil.liftlog.ui.settings.DocumentIo
import de.simiil.liftlog.ui.settings.SettingsViewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.time.Clock

/**
 * iOS Koin-graph smoke test (M7 debt item: no `verify()` on iOS — `org.koin.test.verify`, used by
 * the androidUnitTest `KoinGraphTest`, isn't wired up for the iOS targets in this project, so the
 * graph has never been checked there). Builds the real [appModules] graph — iosMain's
 * `platformModule` included, so
 * the real `Room`/`DataStore`/`AppInfo`/`LocaleFormatters`/`DocumentIo`/notification-tick bindings
 * are exercised, not fakes — and resolves every binding the app's 11 ViewModels need.
 *
 * Nine of the eleven ViewModels are resolved directly via `koin.get<VM>()` (three of them —
 * [DayEditorViewModel]/[ExerciseDetailViewModel]/[SessionDetailViewModel] — need a
 * [SavedStateHandle], supplied the same way `koinViewModel()` does at runtime: as a Koin
 * resolution parameter via `parametersOf`, which nested `get()` calls inside the `viewModel {}`
 * module definitions pick up automatically). [SettingsViewModel] and [ActiveSessionViewModel] are
 * the other two SavedStateHandle-backed ViewModels, but are deliberately NOT constructed here:
 * both have an `init {}` block that unconditionally starts a `viewModelScope.launch { flow.collect
 * { ... } } }` — a live, never-completing collector of a real repository `Flow` — and this test has
 * no `ViewModelStore`/`onCleared()` to ever cancel it. Instantiating them would leak a background
 * coroutine for the rest of the test process for no additional coverage (the wiring risk this test
 * guards against — "does every binding this VM's constructor needs actually exist on iOS" — is
 * fully covered by resolving their constructor dependency types individually instead, which is
 * exactly what a plain `koin.get<VM>()` would need to succeed anyway).
 */
class IosKoinGraphTest {
    @Test
    fun allViewModelsResolve() {
        val koin = koinApplication { modules(appModules) }.koin
        try {
            koin.get<MainViewModel>()
            koin.get<HomeViewModel>()
            koin.get<PlanViewModel>()
            koin.get<ExercisePickerViewModel>()
            koin.get<HistoryViewModel>()
            koin.get<AnalyticsBrowserViewModel>()
            koin.get<DayEditorViewModel> { parametersOf(SavedStateHandle(mapOf("templateId" to "t"))) }
            koin.get<ExerciseDetailViewModel> { parametersOf(SavedStateHandle(mapOf("exerciseId" to "e"))) }
            koin.get<SessionDetailViewModel> { parametersOf(SavedStateHandle(mapOf("sessionId" to "s"))) }

            // SettingsViewModel: constructor dependencies only (see class doc for why).
            koin.get<SettingsRepository>()
            koin.get<SyntheticHistorySeeder>()
            koin.get<BackupRepository>()
            koin.get<DocumentIo>()
            koin.get<Clock>()

            // ActiveSessionViewModel: constructor dependencies only (see class doc for why).
            koin.get<SessionRepository>()
            koin.get<ExerciseRepository>()
            koin.get<ExerciseNameResolver>()
            koin.get<ActiveEntryTracker>()
            koin.get<NotificationPermissionTick>()

            // Remaining bindings the other ViewModels above need, not already covered:
            koin.get<PlanRepository>()
            koin.get<AnalyticsRepository>()
            koin.get<DefaultPlanEnsurer>()
            koin.get<LocaleFormatters>()
        } finally {
            koin.close()
        }
    }
}
