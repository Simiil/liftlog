package de.simiil.liftlog.di

import de.simiil.liftlog.MainViewModel
import de.simiil.liftlog.data.db.AppDatabase
import de.simiil.liftlog.data.db.RoomTransactor
import de.simiil.liftlog.data.db.Transactor
import de.simiil.liftlog.data.repository.AnalyticsRepositoryImpl
import de.simiil.liftlog.data.repository.BackupRepositoryImpl
import de.simiil.liftlog.data.repository.ExerciseRepositoryImpl
import de.simiil.liftlog.data.repository.PlanRepositoryImpl
import de.simiil.liftlog.data.repository.SessionRepositoryImpl
import de.simiil.liftlog.data.repository.SettingsRepositoryImpl
import de.simiil.liftlog.data.seed.ExerciseSeeder
import de.simiil.liftlog.data.seed.SyntheticHistorySeeder
import de.simiil.liftlog.domain.logging.ActiveEntryTracker
import de.simiil.liftlog.domain.plan.DefaultPlanEnsurer
import de.simiil.liftlog.domain.plan.DefaultPlanNameProvider
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
import de.simiil.liftlog.ui.exercises.ResourceExerciseNameResolver
import de.simiil.liftlog.ui.history.HistoryViewModel
import de.simiil.liftlog.ui.home.HomeViewModel
import de.simiil.liftlog.ui.plans.DayEditorViewModel
import de.simiil.liftlog.ui.plans.PlanViewModel
import de.simiil.liftlog.ui.plans.ResourceDefaultPlanNameProvider
import de.simiil.liftlog.ui.session.ActiveSessionViewModel
import de.simiil.liftlog.ui.session.SessionDetailViewModel
import de.simiil.liftlog.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

/** Qualifier for the app-lifetime CoroutineScope. */
val AppScope = named("applicationScope")

/**
 * Cross-platform infra singletons — DAOs (resolved from the platform's [AppDatabase]), the
 * [Transactor], `Clock`, `Json`, and the [AppScope] `CoroutineScope`. The `AppDatabase`,
 * `DataStore`, and `AppInfo` singles they depend on are bound by each platform's [platformModule]
 * (the DB builder pins `Dispatchers.IO`, which is not a common API).
 */
val infraModule =
    module {
        single { get<AppDatabase>().exerciseDao() }
        single { get<AppDatabase>().planDao() }
        single { get<AppDatabase>().sessionDao() }
        single { get<AppDatabase>().analyticsDao() }
        single { get<AppDatabase>().prefillDao() }
        single { get<AppDatabase>().backupDao() }
        single { get<AppDatabase>().seedStateDao() }
        single<Transactor> { RoomTransactor(get()) }
        single<Clock> { Clock.System }
        single { Json { ignoreUnknownKeys = true } }
        single(AppScope) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    }

/** Repositories, the seeders, and domain services. `ExerciseSeeder` and `BackupRepositoryImpl`
 *  became common in PR5 (the seed file is a CMP bundled resource; BackupRepositoryImpl swapped
 *  Dispatchers.IO → Default), so their binds moved here from the Android platformModule. */
val dataModule =
    module {
        single<SettingsRepository> { SettingsRepositoryImpl(get()) }
        single<ExerciseRepository> { ExerciseRepositoryImpl(get(), get(), get()) }
        single<PlanRepository> { PlanRepositoryImpl(get(), get(), get(), get()) }
        single<SessionRepository> { SessionRepositoryImpl(get(), get(), get(), get(), get()) }
        single<AnalyticsRepository> { AnalyticsRepositoryImpl(get(), get(), get()) }
        factory<BackupRepository> { BackupRepositoryImpl(get(), get(), get(), get(), get(), get()) } // unscoped
        single { ExerciseSeeder(get(), get(), get(), get(), get()) }
        single { SyntheticHistorySeeder(get(), get()) }
        single { DefaultPlanEnsurer(get(), get()) }
        single { ActiveEntryTracker() }
    }

/** UI-adjacent bindings whose implementations became common in PR5 (they resolve strings via
 *  Compose Multiplatform resources rather than an Android Context). */
val uiModule =
    module {
        single<ExerciseNameResolver> { ResourceExerciseNameResolver() }
        single<DefaultPlanNameProvider> { ResourceDefaultPlanNameProvider() }
    }

/**
 * All 11 ViewModels. Common as of PR5 Task 4 (the UI + ViewModels moved to commonMain), unparked
 * from the Android platformModule. `viewModelOf`/`viewModel` come from koin-core's multiplatform
 * DSL, and `androidx.lifecycle.ViewModel` is a KMP type. The SavedStateHandle-backed VMs resolve
 * their handle via `get()` from the ViewModel factory extras (KoinGraphTest passes it as an
 * extraType).
 */
val viewModelModule =
    module {
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

/**
 * Platform leaf: the `AppDatabase` builder + `DataStore` + `AppInfo`, plus (temporarily, on
 * Android) the UI/notification/ViewModel bindings that still reference androidMain-only types.
 * PR5 moves the UI/VM definitions back into a common module once their types are common-visible.
 */
expect val platformModule: Module

val appModules: List<Module> = listOf(infraModule, dataModule, uiModule, viewModelModule, platformModule)
