# M7 — Multiplatform-Ready Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the codebase compile as a Kotlin Multiplatform module (Android + iOS targets) with the Android app behaviorally unchanged — Hilt→Koin, java.time→kotlinx-datetime, Vico→Canvas chart, KMP module conversion, strings→Compose Multiplatform resources.

**Architecture:** Five sequential PRs, each leaving the Android app green on existing CI. The single `:app` module becomes KMP (`commonMain`/`androidMain`/`iosMain`); platform variance is concentrated in one Koin `platformModule`, one DI-bound `LocaleFormatters` interface, and a handful of `expect` composables. iOS targets are **compile-only** in M7 (no Xcode app until M8). Spec: [2026-07-15-ios-port-design.md](../specs/2026-07-15-ios-port-design.md).

**Tech Stack:** Kotlin 2.3.x, Koin 4.1.x (BOM), kotlinx-datetime 0.8.0, Room 2.8.4 + BundledSQLiteDriver (androidx.sqlite 2.7.0), DataStore 1.1.7 (`-core`), Compose Multiplatform 1.11.1, JetBrains navigation-compose 2.9.2, kotlin.test + Turbine (commonTest), JUnit4 (androidUnitTest/instrumented).

## Global Constraints

- **Every PR leaves the Android app behaviorally identical** and green on `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug` (CI parity).
- **M7 starts only after M6 (i18n German) is merged to main** — PR5 moves both locales' strings.
- Run `./gradlew ktlintFormat` before every commit.
- Commit messages end with: `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` — no other trailers.
- Reference the tracking issue (`#NN` from Task 0) in every commit subject, M6-style.
- Instrumented runs must be scoped (they fan out to every attached device): `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.
- iOS deployment target 16.0; targets `iosArm64` + `iosSimulatorArm64` only (no `iosX64`).
- No new dependencies beyond those named in this plan.
- Backup JSON format must remain byte-compatible (`Instant.toString()` ISO-8601 round-trip).

---

## Task 0: Tracking issue + roadmap stub

**Files:**
- Modify: `docs/05-roadmap.md` (append M7/M8 rows to the milestone table)

- [ ] **Step 1: File the tracking issue**

```bash
gh issue create \
  --title "iPhone port via KMP + Compose Multiplatform (M7: multiplatform-ready, M8: iOS app)" \
  --body "$(cat <<'EOF'
Spec: docs/superpowers/specs/2026-07-15-ios-port-design.md
Plan (M7): docs/superpowers/plans/2026-07-15-m7-multiplatform-ready.md

**M7 — multiplatform-ready (Android-only, behavior identical)**
- [ ] PR1 Hilt → Koin
- [ ] PR2 java.time → kotlinx-datetime + LocaleFormatters seam
- [ ] PR3 Vico → hand-rolled Canvas line chart
- [ ] PR4 KMP module conversion (domain+data common, Room/DataStore KMP, iOS targets compile)
- [ ] PR5 UI+DI common, strings → CMP resources, JetBrains navigation

**M8 — iOS app (planned at M7 review gate)**
- [ ] Xcode shell, iOS actuals, idiom polish, CI macOS job, TestFlight

Out of scope (follow-ups): Live Activity, App Store listing/name, widgets, Watch, HealthKit.
EOF
)"
```

Expected: issue URL printed; note the number — it is `#NN` in all commit subjects below.

- [ ] **Step 2: Add M7/M8 to the roadmap table**

In `docs/05-roadmap.md`, append to the milestone list (matching the existing row format):

```markdown
| M7 | Multiplatform-ready (Android-only): Koin, kotlinx-datetime, Canvas charts, KMP module, CMP resources | #NN |
| M8 | iOS app: Xcode shell, platform actuals, idiom polish, macOS CI, TestFlight | #NN |
```

- [ ] **Step 3: Commit**

```bash
git add docs/05-roadmap.md
git commit -m "docs(roadmap): add M7/M8 iPhone-port milestones (#NN)"
```

---

# PR1 — Hilt → Koin

**Outcome:** Koin 4 owns the DI graph; Hilt fully removed; graph gated by a `verify()` unit test; instrumented tests use Koin module overrides. Branch: `m7-koin` (off `main`).

### Task 1: Add Koin dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: catalog aliases `libs.koin.bom`, `libs.koin.core`, `libs.koin.android`, `libs.koin.compose.viewmodel`, `libs.koin.test`, `libs.koin.test.junit4` used by later tasks.

- [ ] **Step 1: Catalog entries**

In `[versions]`: `koinBom = "4.1.1"`. In `[libraries]`:

```toml
koin-bom = { group = "io.insert-koin", name = "koin-bom", version.ref = "koinBom" }
koin-core = { group = "io.insert-koin", name = "koin-core" }
koin-android = { group = "io.insert-koin", name = "koin-android" }
koin-compose-viewmodel = { group = "io.insert-koin", name = "koin-compose-viewmodel" }
koin-test = { group = "io.insert-koin", name = "koin-test" }
koin-test-junit4 = { group = "io.insert-koin", name = "koin-test-junit4" }
```

- [ ] **Step 2: App dependencies** (Hilt stays for now — removal is Task 6)

```kotlin
implementation(platform(libs.koin.bom))
implementation(libs.koin.core)
implementation(libs.koin.android)
implementation(libs.koin.compose.viewmodel)
testImplementation(libs.koin.test)
testImplementation(libs.koin.test.junit4)
androidTestImplementation(libs.koin.test)
```

- [ ] **Step 3: Build check** — `./gradlew assembleDebug` → BUILD SUCCESSFUL.

- [ ] **Step 4: Commit** — `git commit -m "build(di): add Koin 4.1 BOM + artifacts (#NN)"`

### Task 2: Koin modules mirroring the Hilt graph, gated by `verify()`

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/di/AppModules.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/di/KoinGraphTest.kt`

**Interfaces:**
- Produces: `val appModules: List<Module>`, `val AppScope: StringQualifier` (replaces `@ApplicationScope`), modules `infraModule`, `dataModule`, `uiModule`, `viewModelModule`, `androidPlatformModule`. Task 3–5 and PR4's source-set split consume these names.

Scope-fidelity rule: Hilt `@Singleton` → Koin `single`; Hilt *unscoped* (`BackupRepositoryImpl`, `AndroidDocumentIo`) → Koin `factory` (new instance per injection, matching Hilt semantics).

- [ ] **Step 1: Write the failing test**

```kotlin
package de.simiil.liftlog.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import org.junit.Test
import org.koin.test.verify.verify

class KoinGraphTest {
    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test
    fun koinGraph_resolves() {
        appModules.forEach { module ->
            // Context comes from androidContext(); SavedStateHandle from the VM factory extras.
            module.verify(extraTypes = listOf(Context::class, SavedStateHandle::class, Long::class))
        }
    }
}
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.di.KoinGraphTest"` — FAIL (unresolved `appModules`).

- [ ] **Step 3: Write `AppModules.kt`**

Full content (imports elided to the interesting ones; mirror the Hilt inventory exactly — it is reproduced in the old `di/*.kt` files being replaced):

```kotlin
package de.simiil.liftlog.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** Qualifier for the app-lifetime CoroutineScope (was Hilt's @ApplicationScope). */
val AppScope = named("applicationScope")

/** Infra singletons — DB, DAOs, Clock, Json, app scope. (PR4: DB/DataStore providers move to platformModule.) */
val infraModule = module {
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, "liftlog.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }
    single { get<AppDatabase>().exerciseDao() }
    single { get<AppDatabase>().planDao() }
    single { get<AppDatabase>().sessionDao() }
    single { get<AppDatabase>().analyticsDao() }
    single { get<AppDatabase>().prefillDao() }
    single { get<AppDatabase>().backupDao() }
    single { get<AppDatabase>().seedStateDao() }
    single { AppInfo(name = "LiftLog", versionName = BuildConfig.VERSION_NAME, dbSchemaVersion = DB_SCHEMA_VERSION) }
    single<Transactor> { RoomTransactor(get()) }
    single<Clock> { Clock.systemUTC() }
    single { Json { ignoreUnknownKeys = true } }
    single(AppScope) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile("settings") }
    }
}

/** Repositories, seeders, domain services. */
val dataModule = module {
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single<ExerciseRepository> { ExerciseRepositoryImpl(get(), get(), get()) }
    single<PlanRepository> { PlanRepositoryImpl(get(), get(), get(), get()) }
    single<SessionRepository> { SessionRepositoryImpl(get(), get(), get(), get(), get()) }
    single<AnalyticsRepository> { AnalyticsRepositoryImpl(get(), get(), get()) }
    factory<BackupRepository> { BackupRepositoryImpl(get(), get(), get(), get(), get(), get()) }  // Hilt-unscoped
    single { ExerciseSeeder(androidContext(), get(), get(), get(), get(), get()) }
    single { SyntheticHistorySeeder(get(), get()) }
    single { DefaultPlanEnsurer(get(), get()) }
    single { ActiveEntryTracker() }
}

/** UI-adjacent bindings. */
val uiModule = module {
    factory<DocumentIo> { AndroidDocumentIo(androidContext()) }  // Hilt-unscoped
    single<ExerciseNameResolver> { ResourceExerciseNameResolver(androidContext()) }
    single<DefaultPlanNameProvider> { ResourceDefaultPlanNameProvider(androidContext()) }
}

/** Android-only services (stays androidMain forever). */
val androidPlatformModule = module {
    single { SessionNotificationCoordinator(androidContext(), get(), get(), get(), get(AppScope)) }
    single { SessionNotificationBuilder(androidContext()) }
    single { NotificationPermissionTick() }
    single { SessionNotificationModelProducer(get(), get(), get(), get(), get()) }
}

val viewModelModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::PlanViewModel)
    viewModelOf(::ExercisePickerViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::AnalyticsBrowserViewModel)
    // SavedStateHandle VMs: resolve the handle from the ViewModel factory extras via get().
    viewModel { DayEditorViewModel(get(), get(), get(), get()) }  // debounceMs uses its default
    viewModel { ExerciseDetailViewModel(get(), get(), get(), get(), get()) }
    viewModel { ActiveSessionViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SessionDetailViewModel(get(), get(), get(), get(), get()) }
}

val appModules = listOf(infraModule, dataModule, uiModule, androidPlatformModule, viewModelModule)
```

Also in this step: delete `DayEditorViewModel`'s secondary `@Inject constructor` (it existed only because Dagger can't skip `debounceMs`; Koin's lambda uses the primary constructor's default). Argument order in every `get()` call must match the constructor inventory in the replaced Hilt modules — cross-check each against the class's primary constructor.

- [ ] **Step 4: Run the test** — PASS. Then `./gradlew testDebugUnitTest` — all green (Hilt still active; Koin modules are inert data until Task 3).

- [ ] **Step 5: Commit** — `git commit -m "refactor(di): Koin modules mirroring the Hilt graph + verify() gate (#NN)"`

### Task 3: Swap app startup to Koin

**Files:**
- Modify: `app/src/main/kotlin/de/simiil/liftlog/LiftLogApplication.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/MainActivity.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/notification/SessionNotificationService.kt`

- [ ] **Step 1: Rewrite `LiftLogApplication`**

```kotlin
package de.simiil.liftlog

import android.app.Application
import de.simiil.liftlog.data.seed.ExerciseSeeder
import de.simiil.liftlog.di.AppScope
import de.simiil.liftlog.di.appModules
import de.simiil.liftlog.domain.plan.DefaultPlanEnsurer
import de.simiil.liftlog.notification.SessionNotificationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LiftLogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin =
            startKoin {
                androidContext(this@LiftLogApplication)
                modules(appModules)
            }.koin
        koin.get<CoroutineScope>(AppScope).launch {
            koin.get<ExerciseSeeder>().seed()
            koin.get<DefaultPlanEnsurer>().ensure()
        }
        koin.get<SessionNotificationCoordinator>().start()
    }
}
```

- [ ] **Step 2: `MainActivity`** — drop `@AndroidEntryPoint`; replace `by viewModels()`:

```kotlin
import org.koin.androidx.viewmodel.ext.android.viewModel
...
private val viewModel: MainViewModel by viewModel()
```

- [ ] **Step 3: `SessionNotificationService`** — drop `@AndroidEntryPoint`; replace the three `@Inject lateinit var` fields:

```kotlin
class SessionNotificationService : Service(), KoinComponent {
    private val producer: SessionNotificationModelProducer by inject()
    private val builder: SessionNotificationBuilder by inject()
    private val sessionRepository: SessionRepository by inject()
```

(`org.koin.core.component.KoinComponent` / `org.koin.core.component.inject`.)

- [ ] **Step 4: Build + install-smoke** — `./gradlew assembleDebug` then `./gradlew installDebug`, launch on `emulator-5554`: app opens, Home shows seeded state, start/finish a session, notification appears. (Hilt annotations elsewhere are now dead weight but harmless.)

- [ ] **Step 5: Commit** — `git commit -m "refactor(di): boot Koin from Application; Activity+Service off Hilt (#NN)"`

### Task 4: `hiltViewModel()` → `koinViewModel()` (10 call sites)

**Files (all Modify):** `ui/settings/SettingsScreen.kt:57`, `ui/home/HomeScreen.kt:75`, `ui/plans/DayEditorScreen.kt:48`, `ui/plans/PlanScreen.kt:77`, `ui/exercises/ExercisePickerScreen.kt:78`, `ui/history/HistoryScreen.kt:39`, `ui/analytics/ExerciseDetailScreen.kt:53`, `ui/analytics/AnalyticsScreen.kt:53`, `ui/session/ActiveSessionScreen.kt:83`, `ui/session/SessionDetailScreen.kt:64` (paths under `app/src/main/kotlin/de/simiil/liftlog/`).

- [ ] **Step 1: In each file**, replace import `androidx.hilt.navigation.compose.hiltViewModel` → `org.koin.compose.viewmodel.koinViewModel`, and the default parameter `= hiltViewModel()` → `= koinViewModel()`. (The multiplatform artifact — call sites won't change again in PR5.)

- [ ] **Step 2: Verify** — `./gradlew testDebugUnitTest assembleDebug` green; re-run the install-smoke from Task 3 step 4 and open every tab plus one detail screen (exercise detail = SavedStateHandle path).

- [ ] **Step 3: Commit** — `git commit -m "refactor(di): koinViewModel() at all screen entry points (#NN)"`

### Task 5: Instrumented tests on Koin overrides

**Files:**
- Create: `app/src/androidTest/kotlin/de/simiil/liftlog/KoinTestApplication.kt`
- Create: `app/src/androidTest/kotlin/de/simiil/liftlog/di/TestOverrideModules.kt`
- Modify: `app/src/androidTest/kotlin/de/simiil/liftlog/HiltTestRunner.kt` → rename `KoinTestRunner.kt`
- Delete: `app/src/androidTest/kotlin/de/simiil/liftlog/di/TestDatabaseModule.kt`, `TestDataStoreModule.kt`
- Modify: 8 UI test files under `app/src/androidTest/kotlin/de/simiil/liftlog/ui/` (list in §inventory: CriticalLoggingPath, PlanDeletePath, PlanEditPath, PlanSwitchPath, TemplateStartPath, SessionMetaPath, WorkoutDeletePath, SessionDeepLink)
- Modify: `app/build.gradle.kts` (`testInstrumentationRunner`)

**Interfaces:**
- Produces: `KoinTestRunner` (FQCN `de.simiil.liftlog.KoinTestRunner`) referenced from `defaultConfig`; `testOverrideModules: List<Module>`.

- [ ] **Step 1: Test application + runner** (mirrors today's HiltTestApplication behavior: DI only — no seeding, no coordinator):

```kotlin
// KoinTestApplication.kt
package de.simiil.liftlog

import android.app.Application
import de.simiil.liftlog.di.appModules
import de.simiil.liftlog.di.testOverrideModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

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

// KoinTestRunner.kt (was HiltTestRunner.kt — keep the file's git history via git mv)
class KoinTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, KoinTestApplication::class.java.name, context)
}
```

```kotlin
// TestOverrideModules.kt — port the bodies of the two deleted @TestInstallIn modules verbatim:
val testOverrideModules = listOf(
    module {
        single { Room.inMemoryDatabaseBuilder(androidContext(), AppDatabase::class.java).build() }
        single { AppInfo(name = "LiftLog", versionName = "test", dbSchemaVersion = DB_SCHEMA_VERSION) }
        single<DataStore<Preferences>> {
            PreferenceDataStoreFactory.create {
                androidContext().preferencesDataStoreFile("settings_test_${UUID.randomUUID()}")
            }
        }
    },
)
```

Set `testInstrumentationRunner = "de.simiil.liftlog.KoinTestRunner"` in `defaultConfig`.

- [ ] **Step 2: Migrate the 8 UI tests.** Pattern (identical in each): remove `@HiltAndroidTest`, the `HiltAndroidRule` (order-0 rule), and `hiltRule.inject()`; make the class `: KoinComponent`; replace each `@Inject lateinit var x: T` with `private val x: T by inject()`. Rule orders shift down by one (compose rule becomes order 0, GrantPermissionRule keeps its relative position). Example, `CriticalLoggingPathTest`:

```kotlin
class CriticalLoggingPathTest : KoinComponent {
    @get:Rule(order = 0) val composeRule = createAndroidComposeRule<MainActivity>()
    @get:Rule(order = 1) val grantPermission: GrantPermissionRule = ...  // unchanged
    private val sessionRepository: SessionRepository by inject()
    private val exerciseRepository: ExerciseRepository by inject()
    // @Before keeps its data-setup body, minus hiltRule.inject()
```

- [ ] **Step 3: Run scoped** — `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.ui.CriticalLoggingPathTest` → PASS. Then the full suite once: `./gradlew connectedDebugAndroidTest` (only `emulator-5554` attached) → PASS.

- [ ] **Step 4: Commit** — `git commit -m "test(di): instrumented tests on Koin overrides; KoinTestRunner (#NN)"`

### Task 6: Remove Hilt completely

**Files:**
- Delete: `di/DatabaseModule.kt`, `di/DataStoreModule.kt`, `di/RepositoryModule.kt`, `di/UiBindingsModule.kt`, `di/ApplicationScope.kt`
- Modify: the 17 `@Inject`-constructor classes + 11 ViewModels (strip `@Inject`/`@Singleton`/`@HiltViewModel`/`@ApplicationContext`/`@ApplicationScope` annotations and their imports; constructors themselves stay)
- Modify: `app/build.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`

- [ ] **Step 1: Strip annotations.** `grep -rln 'javax.inject\|dagger' app/src` gives the authoritative list; expected ≈ 30 files. Remove annotation lines + imports only — no signature changes. `ResourceExerciseNameResolver`-style nested-indent classes flatten back to normal class declarations where the annotation forced the indented form (ktlintFormat will settle formatting).

- [ ] **Step 2: Build files.** Remove from `app/build.gradle.kts`: `alias(libs.plugins.hilt)`, `implementation(libs.hilt.android)`, `ksp(libs.hilt.compiler)`, `implementation(libs.androidx.hilt.navigation.compose)`, `androidTestImplementation(libs.hilt.android.testing)`, `kspAndroidTest(libs.hilt.compiler)`. **Keep `alias(libs.plugins.ksp)`** — Room needs it. Remove the hilt line from root `build.gradle.kts` plugins. Remove `hilt`, `hiltNavigationCompose` versions + the 4 hilt library entries + hilt plugin from the catalog.

- [ ] **Step 3: Verify no Hilt remains** — `grep -rn 'hilt\|dagger\|javax.inject' app/src gradle/libs.versions.toml --include='*.kt' --include='*.toml' -i` → no hits.

- [ ] **Step 4: Full verification** — `./gradlew ktlintFormat` then `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug` → green. Scoped instrumented re-run (CriticalLoggingPathTest) → PASS.

- [ ] **Step 5: Commit** — `git commit -m "refactor(di)!: remove Hilt (#NN)"`

### Task 7: Docs + PR

- [ ] **Step 1:** CLAUDE.md fixed-decisions bullet: `**Hilt** for DI` → `**Koin** for DI (multiplatform; was Hilt pre-M7)`. Same amendment in `docs/01-architecture.md` where Hilt is named (grep `Hilt docs/`).
- [ ] **Step 2:** `git commit -m "docs: DI fixed decision Hilt→Koin (#NN)"`, push branch `m7-koin`, open PR titled `M7-PR1: Hilt → Koin (#NN)` with a body summarizing scope-fidelity rule + test-migration strategy. **Review gate: wait for owner merge before starting PR2.**

---

# PR2 — kotlinx-datetime + `LocaleFormatters` seam

**Outcome:** No `java.time`/`java.text`/`android.text.format` outside `AndroidLocaleFormatters` and `Decimals`; all date math on kotlinx-datetime; all locale-sensitive *rendering* behind one DI-bound interface. Branch: `m7-datetime`.

**Design note (spec refinement):** the spec says "expect/actual `LocaleFormatters`". We implement it as an **interface bound in Koin** instead — same seam, but the Android implementation needs a `Context` (for `DateUtils`/`is24HourFormat`), which DI provides naturally and expect/actual cannot. `Decimals` (also needs java.text) becomes expect/actual in PR4 because domain code calls it statically. Both fulfil the spec's seam table.

### Task 1: Dependency + FixedClock test helper

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `app/src/test/kotlin/de/simiil/liftlog/testing/FixedClock.kt`

- [ ] **Step 1:** Catalog: `kotlinxDatetime = "0.8.0"`; `kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinxDatetime" }`. App: `implementation(libs.kotlinx.datetime)`.

- [ ] **Step 2:** Test helper (replaces every `Clock.fixed(instant, ZoneOffset.UTC)`):

```kotlin
package de.simiil.liftlog.testing

import kotlin.time.Clock
import kotlin.time.Instant

class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
```

- [ ] **Step 3:** `./gradlew assembleDebug` green. Commit: `git commit -m "build(datetime): kotlinx-datetime 0.8 + FixedClock helper (#NN)"`

### Task 2: `LocaleFormatters` interface + Android implementation

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/format/LocaleFormatters.kt`
- Create: `app/src/main/kotlin/de/simiil/liftlog/ui/format/AndroidLocaleFormatters.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/di/AppModules.kt` (bind in `uiModule`)
- Test: `app/src/test/kotlin/de/simiil/liftlog/ui/format/AndroidLocaleFormattersTest.kt`

**Interfaces:**
- Produces (consumed by Task 3 call-site rewiring, and re-bound per platform in PR4/M8):

```kotlin
package de.simiil.liftlog.domain.format

import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** Locale-sensitive rendering only — no date math. Android impl: java.time/java.text/DateUtils;
 *  iOS impl (M8): NSDateFormatter/NSNumberFormatter. Bound as a Koin single per platform. */
interface LocaleFormatters {
    fun mediumDate(instant: Instant, timeZone: TimeZone): String          // was ofLocalizedDate(MEDIUM)
    fun mediumDateShortTime(instant: Instant, timeZone: TimeZone): String // was ofLocalizedDateTime(MEDIUM, SHORT)
    fun weekdayDayMonth(instant: Instant, timeZone: TimeZone): String     // was "EEE d MMM"
    fun timeHm(instant: Instant, timeZone: TimeZone): String              // was "HH:mm"
    fun relativeDate(thenMillis: Long): String                            // was DateUtils.getRelativeTimeSpanString
    fun prefers24HourTime(): Boolean                                      // was DateFormat.is24HourFormat(context)
    fun oneDecimal(value: Double): String                                 // was String.format("%.1f", …)
    fun signedOneDecimal(value: Double): String                           // was String.format("%+.1f", …)
    fun nameComparator(): Comparator<String>                              // was java.text.Collator (per-call, picks up locale changes)
}
```

- [ ] **Step 1: Failing test** (JVM; locale pinned per-assertion via `Locale.setDefault` in try/finally — structural assertions to dodge CLDR drift):

```kotlin
class AndroidLocaleFormattersTest {
    private val fmt = AndroidLocaleFormatters(context = null) // JVM test: DateUtils paths not exercised
    private val instant = Instant.parse("2026-06-04T18:30:00Z")

    private fun <T> withLocale(l: Locale, block: () -> T): T {
        val old = Locale.getDefault()
        Locale.setDefault(l)
        try { return block() } finally { Locale.setDefault(old) }
    }

    @Test fun mediumDate_localized() {
        val en = withLocale(Locale.US) { fmt.mediumDate(instant, TimeZone.UTC) }
        val de = withLocale(Locale.GERMANY) { fmt.mediumDate(instant, TimeZone.UTC) }
        assertTrue(en.contains("2026")); assertTrue(en.contains("Jun"))
        assertTrue(de.contains("2026")); assertTrue(de.contains("Juni") || de.contains("06"))
    }
    @Test fun timeHm_is24h() { assertEquals("18:30", fmt.timeHm(instant, TimeZone.UTC)) }
    @Test fun oneDecimal_localeSeparator() {
        assertEquals("12.5", withLocale(Locale.US) { fmt.oneDecimal(12.5) })
        assertEquals("12,5", withLocale(Locale.GERMANY) { fmt.oneDecimal(12.5) })
        assertEquals("+12,5", withLocale(Locale.GERMANY) { fmt.signedOneDecimal(12.5) })
    }
    @Test fun nameComparator_collates() {
        val sorted = withLocale(Locale.GERMANY) { listOf("Übung", "Anfang").sortedWith(fmt.nameComparator()) }
        assertEquals(listOf("Anfang", "Übung"), sorted)  // Collator puts Ü after A, before Z-region
    }
}
```

- [ ] **Step 2: Run** — FAIL (class missing).

- [ ] **Step 3: Implement `AndroidLocaleFormatters`** — java.time conversion bridge `Instant.toJava()` = `java.time.Instant.ofEpochMilli(toEpochMilliseconds())`:

```kotlin
class AndroidLocaleFormatters(private val context: Context?) : LocaleFormatters {
    private fun kotlin.time.Instant.zoned(tz: TimeZone): java.time.ZonedDateTime =
        java.time.Instant.ofEpochMilli(toEpochMilliseconds()).atZone(java.time.ZoneId.of(tz.id))

    override fun mediumDate(instant: Instant, timeZone: TimeZone) =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(instant.zoned(timeZone))
    override fun mediumDateShortTime(instant: Instant, timeZone: TimeZone) =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(instant.zoned(timeZone))
    override fun weekdayDayMonth(instant: Instant, timeZone: TimeZone) =
        DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(instant.zoned(timeZone))
    override fun timeHm(instant: Instant, timeZone: TimeZone) =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(instant.zoned(timeZone))
    override fun relativeDate(thenMillis: Long): String =
        android.text.format.DateUtils.getRelativeTimeSpanString(thenMillis).toString()
    override fun prefers24HourTime(): Boolean =
        context?.let(android.text.format.DateFormat::is24HourFormat) ?: true
    override fun oneDecimal(value: Double) = String.format(Locale.getDefault(), "%.1f", value)
    override fun signedOneDecimal(value: Double) = String.format(Locale.getDefault(), "%+.1f", value)
    override fun nameComparator(): Comparator<String> {
        val collator = java.text.Collator.getInstance()  // per-call: picks up runtime locale change
        return Comparator { a, b -> collator.compare(a, b) }
    }
}
```

Formatters are created per call (matching the M6 `Collator` convention) so per-app language switches take effect — this deliberately fixes the stale top-level `DATE_FMT`/`TIME_FMT` capture in `SessionDetailScreen`.

Bind in `uiModule`: `single<LocaleFormatters> { AndroidLocaleFormatters(androidContext()) }`.
The three `relativeDate` call sites currently pass `(time, now, MINUTE_IN_MILLIS)` — check each original call; if the 3-arg form was used, keep its semantics by using the same overload inside the implementation.

- [ ] **Step 4: Run** — PASS. `KoinGraphTest` still green.

- [ ] **Step 5: Commit** — `git commit -m "feat(format): LocaleFormatters seam + Android impl (#NN)"`

### Task 3: Rewire all rendering call sites to `LocaleFormatters`

**Files (all Modify, paths under `app/src/main/kotlin/de/simiil/liftlog/`):**
`ui/settings/SettingsScreen.kt:143-151` (mediumDate) · `ui/session/EditWorkoutSheet.kt:243,298-309` (prefers24HourTime, mediumDateShortTime) · `ui/session/SessionDetailScreen.kt:216-230,255,372-375` (weekdayDayMonth+timeHm date strip, oneDecimal, relativeDate) · `ui/analytics/ExerciseDetailScreen.kt:43,160,195` (weekdayDayMonth replaces SimpleDateFormat) · `ui/home/HomeScreen.kt:439-442` (relativeDate) · `ui/history/HistoryScreen.kt:112-115` (relativeDate) · `ui/analytics/MuscleBalanceCard.kt:159`, `ui/analytics/TrendBadge.kt:53`, `ui/analytics/AnalyticsScreen.kt:123` (oneDecimal/signedOneDecimal) · `ui/exercises/ExercisePickerViewModel.kt:62-67` (nameComparator via constructor injection).

- [ ] **Step 1:** In composables, obtain the seam once per screen: `val formatters = koinInject<LocaleFormatters>()` (`org.koin.compose.koinInject`) and thread it down as a parameter to private composables that need it. In `ExercisePickerViewModel`, add `formatters: LocaleFormatters` as a constructor param (`viewModelOf(::ExercisePickerViewModel)` resolves the new dependency automatically — no module change) and replace the Collator block with `.thenComparator { a, b -> formatters.nameComparator().compare(a.second, b.second) }`. Delete now-unused imports (`DateTimeFormatter`, `FormatStyle`, `SimpleDateFormat`, `Collator`, `DateUtils`).

- [ ] **Step 2:** Compile-driven sweep: `./gradlew compileDebugKotlin` until no red; then `./gradlew testDebugUnitTest` (ExercisePickerViewModelTest needs the new param — pass `AndroidLocaleFormatters(null)`).

- [ ] **Step 3:** Manual check on emulator: Session detail date strip, Edit workout dialog, Settings import dialog, History relative dates, Analytics decimals — all render as before (switch device language to German and spot-check `,` separators + German month names).

- [ ] **Step 4: Commit** — `git commit -m "refactor(format): all locale rendering through LocaleFormatters (#NN)"`

### Task 4: Core Clock/Instant swap (domain, data, di, tests)

**Files:** every file in the §java.time inventory *except* the rendering sites done in Task 3 and the week-math sites (Tasks 5–7). Mechanical patterns:

| Old (java.time) | New (kotlin.time / kotlinx-datetime) |
|---|---|
| `import java.time.Instant` | `import kotlin.time.Instant` |
| `import java.time.Clock` | `import kotlin.time.Clock` |
| `Clock.systemUTC()` (DI provider) | `Clock.System` |
| `clock.millis()` | `clock.now().toEpochMilliseconds()` |
| `clock.instant()` | `clock.now()` |
| `Instant.ofEpochMilli(x)` | `Instant.fromEpochMilliseconds(x)` |
| `instant.toEpochMilli()` | `instant.toEpochMilliseconds()` |
| `Instant.now()` (UI un-seamed spots) | `Clock.System.now()` |
| `Duration.between(a, b)` | `(b - a)` (`kotlin.time.Duration`) |
| `instant.epochSecond` | `instant.epochSeconds` |
| `Clock.fixed(i, ZoneOffset.UTC)` (tests) | `FixedClock(i)` |
| `catch (e: DateTimeException)` (BackupCodec) | `catch (e: IllegalArgumentException)` (`kotlin.time.Instant.parse` throws IAE) |

- [ ] **Step 1:** Swap `di/AppModules.kt` Clock provider + all `data/`, `domain/`, `notification/` files (inventory list). `Instant.parse` in BackupCodec: `kotlin.time.Instant.parse(...)` — ISO-8601, output format unchanged (`toString()` is ISO with `Z`), so backup files round-trip byte-compatible; `BackupCodecTest` proves it.
- [ ] **Step 2:** Swap UI-layer files (ViewModels, screens with `Instant` state, `LoggedSetRow`, `ExerciseCard`) and all test files/fakes.
- [ ] **Step 3:** `./gradlew testDebugUnitTest` → green (except the week/DST tests owned by Tasks 5–7 — if any fail, stop and check the pattern table rather than improvising).
- [ ] **Step 4: Commit** — `git commit -m "refactor(datetime): kotlin.time Clock/Instant across domain+data+ui (#NN)"`

### Task 5: Week-start math (`AnalyticsRepositoryImpl`)

- [ ] **Step 1:** `AnalyticsRepositoryImplTest.weekSummary_splitsThisVsPreviousWeek` already migrated to `FixedClock` in Task 4 — confirm it currently FAILS to compile against the old `LocalDate.now(clock)` body (the repo file still uses `clock.zone`, which `kotlin.time.Clock` lacks).

- [ ] **Step 2:** Rewrite the boundary block in `observeWeekSummary()` (UTC week boundaries — exactly what `Clock.systemUTC().zone` produced before):

```kotlin
val tz = TimeZone.UTC
val today = clock.now().toLocalDateTime(tz).date
val thisWeekStart = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)  // ISO Monday
val thisWeekStartMs = thisWeekStart.atStartOfDayIn(tz).toEpochMilliseconds()
val prevWeekStartMs = thisWeekStart.minus(1, DateTimeUnit.WEEK).atStartOfDayIn(tz).toEpochMilliseconds()
```

- [ ] **Step 3:** `./gradlew testDebugUnitTest --tests "*AnalyticsRepositoryImplTest"` → PASS.
- [ ] **Step 4: Commit** — `git commit -m "refactor(datetime): ISO-Monday week boundary via kotlinx-datetime (#NN)"`

### Task 6: ISO-week helper + `Downsample`

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/analytics/IsoWeek.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/domain/analytics/IsoWeekTest.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/domain/analytics/Downsample.kt`

**Interfaces:**
- Produces: `fun isoWeekKey(date: LocalDate): Long` — `weekBasedYear * 100 + weekOfYear`, identical to the old `IsoFields`-derived key.

- [ ] **Step 1: Failing tests** (values pre-computed from the java.time behavior being replaced):

```kotlin
class IsoWeekTest {
    @Test fun midYear() { assertEquals(202623L, isoWeekKey(LocalDate(2026, 6, 4))) }        // Thu, week 23
    @Test fun jan1InPrevYearsWeek() { assertEquals(202053L, isoWeekKey(LocalDate(2021, 1, 1))) }  // Fri → 2020-W53
    @Test fun dec31InNextYearsWeek() { assertEquals(202501L, isoWeekKey(LocalDate(2024, 12, 30))) } // Mon → 2025-W01
    @Test fun week1Boundary() { assertEquals(202601L, isoWeekKey(LocalDate(2025, 12, 29))) }  // Mon → 2026-W01
}
```

- [ ] **Step 2: Run** — FAIL (unresolved `isoWeekKey`).

- [ ] **Step 3: Implement** (ISO 8601: a date's week is the week containing its Thursday):

```kotlin
package de.simiil.liftlog.domain.analytics

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/** ISO-8601 week key: weekBasedYear*100 + weekNumber (replaces java.time IsoFields). */
fun isoWeekKey(date: LocalDate): Long {
    val thursday = date.plus(4 - date.dayOfWeek.isoDayNumber, DateTimeUnit.DAY)
    val week = (thursday.dayOfYear - 1) / 7 + 1
    return thursday.year * 100L + week
}
```

- [ ] **Step 4: Run** — PASS. Then rewrite `Downsample.kt`'s bucketing line:

```kotlin
val date = Instant.fromEpochMilliseconds(p.timeMillis).toLocalDateTime(TimeZone.UTC).date
val key = isoWeekKey(date)
```

`./gradlew testDebugUnitTest --tests "*DownsampleTest"` → PASS (pins identical bucketing).

- [ ] **Step 5: Commit** — `git commit -m "refactor(datetime): isoWeekKey helper; Downsample off IsoFields (#NN)"`

### Task 7: `combineDateAndTime` + pickers (`EditWorkoutSheet`)

- [ ] **Step 1:** Migrate `CombineDateAndTimeTest.kt` imports to kotlinx types (`TimeZone.of("Europe/Berlin")`, `LocalTime`, `kotlin.time.Instant`) — **assertion values unchanged** (incl. the DST-gap case 02:30→03:30; kotlinx `LocalDateTime.toInstant(tz)` shifts forward across gaps, same as java.time SMART). Run: FAIL to compile.
- [ ] **Step 2:** Port `combineDateAndTime` in `EditWorkoutSheet.kt` preserving its exact signature shape (same params, kotlinx types): DatePicker's `selectedDateMillis` decodes via `Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date` (UTC-midnight contract), fallback date via `fallback.toLocalDateTime(zone).date`, recombine via `LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)`. Zone source becomes `TimeZone.currentSystemDefault()`.
- [ ] **Step 3:** `./gradlew testDebugUnitTest --tests "*CombineDateAndTimeTest"` → PASS, all tests green.
- [ ] **Step 4: Commit** — `git commit -m "refactor(datetime): combineDateAndTime on kotlinx-datetime, DST behavior pinned (#NN)"`

### Task 8: Guard + full verification + PR

- [ ] **Step 1: Leak check** — `grep -rn 'import java.time\|import java.text\|android.text.format' app/src/main/kotlin --include='*.kt'` → hits ONLY in `ui/format/AndroidLocaleFormatters.kt` and `domain/units/Decimals.kt` (Decimals goes expect/actual in PR4).
- [ ] **Step 2:** `./gradlew ktlintFormat && ./gradlew ktlintCheck lint testDebugUnitTest assembleDebug` → green. Scoped instrumented: `SessionMetaPathTest` (exercises EditWorkoutSheet) + `CriticalLoggingPathTest` → PASS. Emulator smoke in German locale (dates, decimals).
- [ ] **Step 3:** Push `m7-datetime`, PR `M7-PR2: kotlinx-datetime + LocaleFormatters seam (#NN)`. **Review gate.**

---

# PR3 — Vico → hand-rolled Canvas line chart

**Outcome:** `ProgressLineChart` drawn with plain Compose Canvas (same public signature — callers untouched); Vico dependency deleted. Branch: `m7-chart`.

### Task 1: `niceTicks` axis helper

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/ui/components/charts/NiceTicks.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/ui/components/charts/NiceTicksTest.kt`

**Interfaces:**
- Produces: `fun niceTicks(min: Double, max: Double, targetCount: Int = 5): List<Double>` — ascending ticks at 1/2/5×10ⁿ steps covering [min, max]; `fun tickLabel(v: Double): String` — integer rendering when whole (`"80"`), else one decimal (`"82.5"`).

- [ ] **Step 1: Failing test**

```kotlin
class NiceTicksTest {
    @Test fun spansRangeWithNiceSteps() {
        val t = niceTicks(72.4, 94.1)
        assertEquals(5.0, t[1] - t[0], 1e-9)             // step snaps to 5
        assertTrue(t.first() <= 72.4 && t.last() >= 94.1)
    }
    @Test fun zeroBasedVolume() {
        val t = niceTicks(0.0, 4200.0)
        assertEquals(0.0, t.first(), 1e-9)
        assertEquals(1000.0, t[1] - t[0], 1e-9)
    }
    @Test fun degenerateFlatSeries() {
        val t = niceTicks(50.0, 50.0)
        assertTrue(t.size >= 2)                           // never collapses to one tick
    }
    @Test fun labels() {
        assertEquals("80", tickLabel(80.0))
        assertEquals("82.5", tickLabel(82.5))
    }
}
```

- [ ] **Step 2: Run** — FAIL. **Step 3: Implement**

```kotlin
package de.simiil.liftlog.ui.components.charts

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Ascending axis ticks at nice (1/2/5×10ⁿ) steps; first ≤ min, last ≥ max (full coverage). */
fun niceTicks(min: Double, max: Double, targetCount: Int = 5): List<Double> {
    val end = if (max > min) max else min + 1.0
    val rawStep = (end - min) / (targetCount - 1)
    val mag = 10.0.pow(floor(log10(rawStep)))
    val fraction = rawStep / mag
    val step = mag * if (fraction < 1.5) 1.0 else if (fraction < 3.0) 2.0 else if (fraction < 7.0) 5.0 else 10.0
    val start = floor(min / step) * step
    return generateSequence(start) { it + step }.takeWhile { it < end + step }.toList()
}

fun tickLabel(v: Double): String {
    val rounded = v.roundToLong()
    return if (abs(v - rounded) < 1e-9) rounded.toString() else ((v * 10).roundToLong() / 10.0).toString()
}
```

(Heckbert-style step rounding: 5.425 → 5, not 10 — this is what makes the first test's step assertion hold. `takeWhile { it < end + step }` guarantees the last tick reaches ≥ max, which the Canvas relies on for its y-range.)

- [ ] **Step 4: Run** — PASS. **Step 5: Commit** — `git commit -m "feat(charts): niceTicks axis helper (#NN)"`

### Task 2: Canvas `ProgressLineChart`

**Files:**
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/components/charts/ProgressLineChart.kt` (full rewrite; `ChartPoint` data class and composable signature stay identical so `ExerciseDetailScreen` doesn't change)

- [ ] **Step 1: Rewrite.** Behavior to preserve: render only when ≥2 points; Y range = data min/max with 12% padding (zero-based lower bound when `zeroBased`); `primary` polyline; `tertiary` dot (7dp) per session, larger (11dp) on PRs; left axis labels; 188dp height; optional `contentDescription` semantics. New implementation sketch (complete file):

```kotlin
package de.simiil.liftlog.ui.components.charts

// imports: Canvas, MaterialTheme, TextMeasurer/rememberTextMeasurer, drawText, dp/sp, semantics

data class ChartPoint(val x: Float, val y: Float, val isPr: Boolean)

@Composable
fun ProgressLineChart(
    points: List<ChartPoint>,
    zeroBased: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (points.size < 2) return
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.tertiary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()

    val ys = points.map { it.y }
    val pad = ((ys.max() - ys.min()).takeIf { it > 0f } ?: (ys.max().takeIf { it != 0f } ?: 1f)) * 0.12f
    val minY = if (zeroBased) 0f else ys.min() - pad
    val maxY = ys.max() + pad
    val ticks = niceTicks(minY.toDouble(), maxY.toDouble())

    Canvas(
        modifier.fillMaxWidth().height(188.dp).then(
            if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier,
        ),
    ) {
        val labelWidth = ticks.maxOf { textMeasurer.measure(tickLabel(it), labelStyle).size.width } + 8.dp.toPx()
        val plot = Rect(labelWidth, 6.dp.toPx(), size.width, size.height - 6.dp.toPx())
        val xMin = points.first().x
        val xSpan = (points.last().x - xMin).takeIf { it > 0f } ?: 1f
        val ySpan = (ticks.last() - ticks.first()).toFloat().takeIf { it > 0f } ?: 1f
        fun px(p: ChartPoint) = Offset(
            plot.left + (p.x - xMin) / xSpan * plot.width,
            plot.bottom - (p.y - ticks.first().toFloat()) / ySpan * plot.height,
        )
        // Gridlines + labels
        ticks.forEach { t ->
            val y = plot.bottom - (t - ticks.first()).toFloat() / ySpan * plot.height
            drawLine(axisColor, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.dp.toPx())
            val layout = textMeasurer.measure(tickLabel(t), labelStyle)
            drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2f))
        }
        // Polyline
        val path = Path().apply {
            points.forEachIndexed { i, p -> val o = px(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
        }
        drawPath(path, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Dots: 7dp regular / 11dp PR (diameters — radius = half)
        points.forEach { p -> drawCircle(dotColor, radius = (if (p.isPr) 11.dp else 7.dp).toPx() / 2f, center = px(p)) }
    }
}
```

Note: `drawText`/`rememberTextMeasurer` come from `androidx.compose.ui.text` — Compose-common APIs (CMP-safe). Delete every `com.patrykandpatrick.vico` import; also delete the now-stale "x values must sit on a grid no finer than 1e-4" constraint comment on `ChartPoint` (that was a Vico crash workaround).

- [ ] **Step 2: Compile + unit tests** — `./gradlew testDebugUnitTest assembleDebug` green.

- [ ] **Step 3: Visual verification on emulator** — install, open Analytics → an exercise detail with history (seed synthetic history via Settings if needed): line + dots + PR dots + axis labels render sanely in light and dark; screenshot for the PR description (`adb -s emulator-5554 exec-out screencap -p > /tmp/chart.png`).

- [ ] **Step 4: Commit** — `git commit -m "feat(charts): Canvas ProgressLineChart replaces Vico (#NN)"`

### Task 3: Drop the dependency + PR

- [ ] **Step 1:** Remove `vico-compose-m3` from `app/build.gradle.kts` and both `vico` entries from the catalog. `grep -rn vico app gradle -i` → no hits.
- [ ] **Step 2:** `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug` green.
- [ ] **Step 3:** Commit `git commit -m "build(charts): remove Vico (#NN)"`, push `m7-chart`, PR `M7-PR3: hand-rolled Canvas line chart (#NN)` with before/after screenshots. **Review gate.**

---

# PR4 — KMP module conversion (domain + data common; iOS compiles)

**Outcome:** `:app` is a KMP module; `domain/` + `data/` (minus seeder + platform leaves) live in `commonMain`; Room 2.8 + BundledSQLiteDriver on both platforms; `iosArm64`/`iosSimulatorArm64` compile and run `commonTest`. UI stays `androidMain` until PR5. Branch: `m7-kmp`.

### Task 1: Kotlin 2.3 + KSP bump (isolated)

- [ ] **Step 1: Resolve exact versions** (CMP 1.11 requires Kotlin ≥2.3 for native targets):

```bash
curl -s https://api.github.com/repos/JetBrains/kotlin/releases/latest | grep tag_name
curl -s "https://api.github.com/repos/google/ksp/releases?per_page=15" | grep '"tag_name"' | head -15
```

Pick the latest stable Kotlin 2.3.x and the KSP release whose prefix matches it exactly (KSP tags are `<kotlin>-<ksp>`; e.g. for Kotlin `2.3.10` take the newest `2.3.10-*`).

- [ ] **Step 2:** Update `kotlin` and `ksp` in the catalog; `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug` → green (still a plain Android app — this isolates toolchain fallout from structure changes).
- [ ] **Step 3:** Commit — `git commit -m "build: Kotlin 2.3.x + matching KSP (#NN)"`

### Task 2: Convert `:app` to a KMP module (all code still Android)

**Files:**
- Modify: `app/build.gradle.kts` (full plugin/target restructure), root `build.gradle.kts`, `gradle/libs.versions.toml`
- Move (git mv, no content edits): `app/src/main/kotlin` → `app/src/androidMain/kotlin`; `app/src/main/res` → `app/src/androidMain/res`; `app/src/main/AndroidManifest.xml` → `app/src/androidMain/AndroidManifest.xml`; `app/src/main/assets` → `app/src/androidMain/assets`; `app/src/test/kotlin` → `app/src/androidUnitTest/kotlin`; `app/src/androidTest/kotlin` → `app/src/androidInstrumentedTest/kotlin` (and its assets)

- [ ] **Step 1: Catalog additions**

```toml
composeMultiplatform = "1.11.1"
sqlite = "2.7.0"
# room stays for now; bumped to 2.8.4 in Task 3
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
androidx-sqlite-bundled = { group = "androidx.sqlite", name = "sqlite-bundled", version.ref = "sqlite" }
kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin" }
```

Root `build.gradle.kts` plugins: replace `kotlin-android` line with `alias(libs.plugins.kotlin.multiplatform) apply false` and add `alias(libs.plugins.compose.multiplatform) apply false`.

- [ ] **Step 2: Restructure `app/build.gradle.kts`.** Target shape (android block, signing, buildTypes, ksp room args, testOptions all carry over unchanged):

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)          // compose compiler
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.androidx.navigation.compose)   // JetBrains fork replaces this in PR5
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.koin.android)
            implementation(libs.reorderable)                    // Android-only until PR5 verifies KMP artifact
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.ktx)
        }
        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.koin.test)
            implementation(libs.koin.test.junit4)
        }
        androidInstrumentedTest.dependencies { /* current androidTest deps, verbatim */ }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    debugImplementation(compose.uiTooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

Drop the androidx `compose-bom` and per-artifact androidx-compose deps (CMP supplies Compose; on the Android target it maps to androidx artifacts). Keep the `android { }` block as-is except: `sourceSets.getByName("androidTest")` asset srcDir line becomes `getByName("androidInstrumentedTest")` semantics via the same block (AGP maps it; verify schemas still resolve for the Room migration tests).

- [ ] **Step 3: git mv the source trees** (exact moves listed in Files above), then `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug` → green, and `./gradlew compileKotlinIosSimulatorArm64` → BUILD SUCCESSFUL (compiles nothing yet — no common code — but proves toolchain + targets).

- [ ] **Step 4: Behavior sanity** — install on emulator, launch, log a set. Note in the PR description: CMP's Material3 may resolve slightly newer than BOM 2025.09.00 — eyeball Home/Session/Settings for visual drift and screenshot anything suspicious for the reviewer.

- [ ] **Step 5: Commit** — `git commit -m "build!: convert :app to KMP module (androidMain-only), add iOS targets (#NN)"`

### Task 3: Room 2.8 + BundledSQLiteDriver (still androidMain)

**Files:**
- Modify: catalog (`room = "2.8.4"`), `app/build.gradle.kts`, `data/db/AppDatabase.kt`, `data/db/Migrations.kt` (or wherever `MIGRATION_1_2`/`MIGRATION_2_3` live), `data/db/RoomTransactor.kt`, `di/AppModules.kt`, `di/TestOverrideModules.kt` (androidInstrumentedTest), `data/db/MigrationTest.kt`

- [ ] **Step 1:** Bump room to 2.8.4; add `implementation(libs.androidx.sqlite.bundled)` to androidMain deps. Remove `libs.androidx.room.ktx` (driver mode replaces its `withTransaction`; keep the dep only if something else from ktx is imported — grep `androidx.room.withTransaction\|room.ktx`).
- [ ] **Step 2:** Migrations to the driver API — same SQL, new receiver:

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("…existing SQL, unchanged…")
    }
}
```

- [ ] **Step 3:** Builder gains the driver (both prod `infraModule` and instrumented override):

```kotlin
Room.databaseBuilder(androidContext(), AppDatabase::class.java, "liftlog.db")
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
```

`RoomTransactor` — replace the `withTransaction` body, keeping the `Transactor` interface untouched:

```kotlin
override suspend fun <T> inTransaction(block: suspend () -> T): T =
    db.useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
```

(Adapt the method name to the existing interface — the file is ~20 lines; the body swap is the whole change.)

- [ ] **Step 4:** `MigrationTest`: switch `MigrationTestHelper` to the driver-based KMP constructor (`androidx.room.testing` 2.8 — takes the database class + `BundledSQLiteDriver()`; consult the class's kdoc if the signature differs). Run scoped: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.data.db.MigrationTest` → PASS, then the DAO test classes → PASS.
- [ ] **Step 5:** **Upgrade-path check on device:** install the previous `main` build, create a workout, then `installDebug` this branch over it — data survives (bundled driver reads the framework-SQLite-written file; WAL carries over).
- [ ] **Step 6:** Full CI parity green. Commit — `git commit -m "build(data): Room 2.8 + BundledSQLiteDriver, driver-API migrations (#NN)"`

### Task 4: `domain/` → commonMain; `Decimals` expect/actual

**Files:**
- Move: `app/src/androidMain/kotlin/de/simiil/liftlog/domain/**` → `app/src/commonMain/kotlin/de/simiil/liftlog/domain/**` — EXCEPT `domain/units/Decimals.kt` (split below). `domain/format/LocaleFormatters.kt` moves as-is (its signature is already common-safe).
- Create: `app/src/commonMain/kotlin/de/simiil/liftlog/domain/units/Decimals.kt` (expect)
- Move+Modify: old `Decimals.kt` → `app/src/androidMain/kotlin/de/simiil/liftlog/domain/units/Decimals.android.kt` (actual)
- Create: `app/src/iosMain/kotlin/de/simiil/liftlog/domain/units/Decimals.ios.kt` (actual)
- Modify: `domain/units/Weights.kt`, `domain/analytics/SetSummary.kt` + their callers/tests (drop `java.util.Locale` params)

- [ ] **Step 1: The expect** (locale parameter dropped — implementations read the platform default; JVM tests pin locale via `Locale.setDefault`):

```kotlin
// commonMain
package de.simiil.liftlog.domain.units

expect object Decimals {
    fun separator(): Char
    fun format(value: Double): String   // ≤2 dp, HALF_UP, trailing zeros stripped, locale separator
    fun parse(text: String): Double?    // lenient: '.' and ',' both accepted
}
```

```kotlin
// androidMain — actual keeps today's java.text body with Locale.getDefault();
// ALSO keeps the existing locale-param overloads as extra (non-actual) members so
// DecimalsTest's US/GERMANY pinning still compiles in androidUnitTest.
actual object Decimals {
    actual fun separator(): Char = separator(Locale.getDefault())
    fun separator(locale: Locale): Char = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    actual fun format(value: Double): String = format(value, Locale.getDefault())
    fun format(value: Double, locale: Locale): String = /* existing DecimalFormat body */
    actual fun parse(text: String): Double? = parse(text, Locale.getDefault())
    fun parse(text: String, locale: Locale): Double? = /* existing body */
}
```

```kotlin
// iosMain — NSNumberFormatter; behavior verified on-device in M8, unit-covered by commonTest basics
actual object Decimals {
    actual fun separator(): Char =
        (NSNumberFormatter().decimalSeparator as String).first()
    actual fun format(value: Double): String {
        val f = NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
            maximumFractionDigits = 2u
            minimumFractionDigits = 0u
            roundingMode = NSNumberFormatterRoundHalfUp
            usesGroupingSeparator = false
        }
        return f.stringFromNumber(NSNumber(double = value)) ?: value.toString()
    }
    actual fun parse(text: String): Double? =
        text.replace(separator(), '.').replace(',', '.').toDoubleOrNull()
}
```

- [ ] **Step 2: Ripple the API change.** `Weights.format`/`formatSetSummary` drop their `Locale` params (they only forwarded to `Decimals`); their callers stop passing locale. `DecimalsTest`/`WeightsTest`/`SetSummaryTest` stay in **androidUnitTest**: locale-specific assertions call the Android actual's locale overloads (`Decimals.format(82.5, Locale.GERMANY)`), or wrap in `Locale.setDefault` for the `Weights`/`SetSummary` paths.
- [ ] **Step 3: Move pure domain tests to commonTest** (`app/src/commonTest/kotlin/...`): `Downsample`, `IsoWeek`, `Trend`, `MuscleBalance`, `PrSessions`, `Prefill`, `ActiveExerciseDefaults`, `SetSummary`-structure tests (non-locale parts), plus `testing/Fake*.kt` fakes (commonTest is visible to androidUnitTest, so JVM tests keep using them). Rewrite recipe per file: `org.junit.Test` → `kotlin.test.Test`; `org.junit.Assert.assertEquals/assertTrue/assertNull` → `kotlin.test.*` (same names, same `(expected, actual)` order); `@Before`/`@After` → `@BeforeTest`/`@AfterTest`. Double-delta asserts: `assertEquals(a, b, 1e-9)` → `kotlin.test.assertEquals(a, b, absoluteTolerance = 1e-9)`.
- [ ] **Step 4: Run everywhere** — `./gradlew testDebugUnitTest` green; `./gradlew iosSimulatorArm64Test` green (first real iOS execution — domain tests on the simulator).
- [ ] **Step 5: Commit** — `git commit -m "refactor(kmp): domain to commonMain; Decimals expect/actual (#NN)"`

### Task 5: `data/` → commonMain; Koin platform split

**Files:**
- Move to `commonMain`: `data/dao/**`, `data/db/**` (AppDatabase, migrations, RoomTransactor, Transactor), `data/entity/**`, `data/mapper/**`, `data/repository/**`, `data/backup/**`
- Stays `androidMain`: `data/seed/ExerciseSeeder.kt` (reads `context.assets` — moves in PR5 with `Res.readBytes`), `data/seed/SyntheticHistorySeeder.kt` moves common (no Context)
- Modify: `data/db/AppDatabase.kt` (`@ConstructedBy`), `di/AppModules.kt` (split), new `di/PlatformModule.android.kt` + `di/PlatformModule.ios.kt`
- Modify: `app/build.gradle.kts` (Room/DataStore/KSP per-target wiring)

- [ ] **Step 1: Build wiring.** Move to commonMain deps: `implementation(libs.androidx.room.runtime)` and swap `androidx-datastore-preferences` for the core artifact — catalog gains `androidx-datastore-preferences-core = { group = "androidx.datastore", name = "datastore-preferences-core", version.ref = "datastore" }` (commonMain); androidMain keeps `androidx-datastore-preferences` (the `preferencesDataStoreFile` helper). Move `androidx.sqlite.bundled` to commonMain. KSP per target:

```kotlin
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
```

- [ ] **Step 2: `AppDatabase` KMP constructor** (in the moved commonMain file):

```kotlin
@Database(/* unchanged */)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() { /* unchanged DAOs */ }

@Suppress("KotlinNoActualForExpect") // Room generates the actuals
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
```

- [ ] **Step 3: Koin split.** `AppModules.kt` moves to commonMain minus platform bits, with this exact PR4 shape:
  - commonMain: `infraModule` (DAOs, Transactor, Clock, Json, AppScope — its `AppDatabase`, `DataStore`, and `AppInfo` singles move out), `dataModule` (sans `ExerciseSeeder`), `expect val platformModule: Module`, and `val appModules = listOf(infraModule, dataModule, platformModule)`.
  - androidMain: `actual val platformModule` = DB builder + DataStore + `AppInfo` (`BuildConfig.VERSION_NAME` is Android-only) + `ExerciseSeeder` + everything that was `uiModule` (DocumentIo, resolvers, LocaleFormatters) + the notification singles + the `viewModelModule` contents. (Temporary parking — PR5 moves the UI/VM definitions back to a commonMain module.)
  - iosMain: `actual val platformModule` (skeleton below; DB + DataStore + `AppInfo(versionName = "0.5.0" /* M8: read from bundle */)` only — `DocumentIo`/`LocaleFormatters` types are still androidMain in PR4, so they cannot be bound here yet).
- [ ] **Step 4: iOS skeleton actuals** (`app/src/iosMain/kotlin/de/simiil/liftlog/di/PlatformModule.ios.kt`) — compile-only in M7, exercised in M8:

```kotlin
actual val platformModule: Module = module {
    single {
        val dbPath = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .first().toString() + "/liftlog.db"
        Room.databaseBuilder<AppDatabase>(name = dbPath)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath {
            (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
                .first().toString() + "/settings.preferences_pb").toPath()
        }
    }
    single { AppInfo(name = "LiftLog", versionName = "0.5.0", dbSchemaVersion = DB_SCHEMA_VERSION) } // M8: read bundle version
}
```

(Keep this file minimal — `IosLocaleFormatters` and the iOS `DocumentIo` binding are added in PR5 Tasks 3/5 when their common-visible types exist; every line here is M8-verified later.)

- [ ] **Step 5: Move data-layer unit tests** (BackupCodec, repository contract tests that run on JVM with fakes) to commonTest with the Task 4 recipe. DAO/DB instrumented tests stay androidInstrumentedTest untouched.
- [ ] **Step 6: Verify** — `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug` green; `./gradlew iosSimulatorArm64Test` green; `./gradlew compileKotlinIosArm64` green; scoped instrumented DAO class → PASS; emulator smoke (log a set; export a backup).
- [ ] **Step 7: Commit** — `git commit -m "refactor(kmp): data layer to commonMain; Room/DataStore KMP; platformModule split (#NN)"`

### Task 6: PR

- [ ] **Step 1:** Push `m7-kmp`, PR `M7-PR4: KMP module conversion — domain+data common, iOS compiles (#NN)`. Body: call out the Room driver swap + upgrade-path check result, the Decimals API change, and that UI/resources land in PR5. **Review gate.**

---

# PR5 — UI + DI to commonMain; CMP resources; JetBrains navigation

**Outcome:** everything except `MainActivity`, `LiftLogApplication`, `notification/`, and platform actuals lives in `commonMain`; strings served by CMP resources in en+de; navigation on the multiplatform fork; `MainViewController()` exists for M8. Branch: `m7-ui-common`.

### Task 1: CMP resources + string migration

**Files:**
- Move: `app/src/androidMain/res/values/strings.xml` → `app/src/commonMain/composeResources/values/strings.xml`; `res/values-de/strings.xml` → `composeResources/values-de/strings.xml`
- Create: minimal `app/src/androidMain/res/values/strings.xml` + `values-de/strings.xml` (Android-manifest-facing strings only)
- Modify: every `R.string`/`R.plurals` consumer (~50 files, 232 call sites), `ui/exercises/BuiltInExerciseNames.kt`, `ui/exercises/ResourceExerciseNameResolver.kt` (+ its interface + callers), `ui/plans/ResourceDefaultPlanNameProvider.kt`, `data/seed/ExerciseSeeder.kt` (moves common)
- Test: `app/src/androidUnitTest/kotlin/de/simiil/liftlog/i18n/StringsParityTest.kt`

- [ ] **Step 1: Split the manifest-facing strings.** `grep -n '@string' app/src/androidMain/AndroidManifest.xml` → keep those keys (at minimum `app_name`) plus any `res/xml/`-referenced strings and notification-channel strings used via `context.getString` in `notification/` (grep `R.string` under `notification/` — those stay Android too, or migrate to `Res.string`, which IS accessible from androidMain; prefer `Res.string` so the android res file shrinks to manifest-only keys). Both locales.

- [ ] **Step 2: Move + rewrite.** Move the two big `strings.xml` files (schema — `<string>`, `<plurals>` — is CMP-compatible as-is). Mechanical rewrite across UI files:
  - `import de.simiil.liftlog.R` → `import liftlog.app.generated.resources.Res` + `import liftlog.app.generated.resources.*` (the generated accessor package derives from the project name — confirm via the first build's generated sources and use that exact package).
  - `stringResource(R.string.x, …)` → `stringResource(Res.string.x, …)` with `org.jetbrains.compose.resources.stringResource`.
  - `pluralStringResource(R.plurals.x, n, …)` → `org.jetbrains.compose.resources.pluralStringResource(Res.plurals.x, n, …)`.
- [ ] **Step 3: Name resolvers go suspend.** `ExerciseNameResolver.displayName` becomes `suspend fun displayName(id: String, fallbackName: String): String`; `ResourceExerciseNameResolver` drops Context and uses `org.jetbrains.compose.resources.getString(res)`; `BuiltInExerciseNames.resById` regenerates as `Map<String, StringResource>` (same keys, `Res.string.` prefix — a scripted sed over the generated file). Call sites (`DayEditor`, `ExercisePicker`, `AnalyticsBrowser`, `ExerciseDetail`, `ActiveSession`, `SessionDetail` ViewModels; `SessionNotificationModelProducer`) all invoke it inside flow transforms or coroutines — suspend propagates without structural change; fakes in commonTest update to `suspend`. Same treatment for `DefaultPlanNameProvider`. Both classes move to commonMain; their Koin binds move from Android `platformModule` to `uiModule`.
- [ ] **Step 4: Seeder.** Move `exercises.v2.json` from `androidMain/assets/seed/` to `commonMain/composeResources/files/seed/exercises.v2.json`; `ExerciseSeeder` drops Context, reads `Res.readBytes("files/seed/exercises.v2.json").decodeToString()`, moves to commonMain; Koin bind moves to `dataModule`. Scoped instrumented run: `ExerciseSeederTest` → PASS.
- [ ] **Step 5: Parity gate (replaces Android Lint's MissingTranslation for moved strings):**

```kotlin
class StringsParityTest {
    private fun keys(path: String): Set<String> {
        val xml = File(path).readText()
        return (Regex("<string name=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] } +
                Regex("<plurals name=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] }).toSet()
    }
    @Test fun germanCoversEnglish() {
        val en = keys("src/commonMain/composeResources/values/strings.xml")
        val de = keys("src/commonMain/composeResources/values-de/strings.xml")
        assertEquals(emptySet<String>(), en - de, "untranslated keys")
        assertEquals(emptySet<String>(), de - en, "orphaned German keys")
    }
}
```

- [ ] **Step 6:** Full CI parity green (lint's `MissingTranslation`/`HardcodedText` still guard the residual android res). Emulator smoke in **German**: every screen renders translated. Commit — `git commit -m "refactor(i18n): strings to CMP resources; suspend name resolvers; parity gate (#NN)"`

### Task 2: JetBrains navigation + lifecycle

**Files:**
- Modify: catalog + `app/build.gradle.kts`, `ui/navigation/**` (imports unchanged — same package), `MainActivity.kt`

- [ ] **Step 1:** Catalog: `jbNavigation = "2.9.2"`; `jetbrains-navigation-compose = { group = "org.jetbrains.androidx.navigation", name = "navigation-compose", version.ref = "jbNavigation" }`. commonMain deps: add it; androidMain: remove `androidx-navigation-compose` and `androidx-lifecycle-*-compose` entries whose classes now arrive transitively from the fork (keep `lifecycle-process` — Android-only, used by the notification coordinator). Package names are identical (`androidx.navigation.*`, `androidx.lifecycle.*`) — zero import churn expected; the deep-link route on the session screen compiles as-is on the Android target.
- [ ] **Step 2:** `./gradlew testDebugUnitTest assembleDebug` green; scoped `SessionDeepLinkTest` → PASS (notification deep link through the fork).
- [ ] **Step 3: Commit** — `git commit -m "build(nav): JetBrains navigation-compose 2.9.2 (#NN)"`

### Task 3: Platform-seam composables (documents, permission, dynamic color)

**Files:**
- Create: `app/src/commonMain/kotlin/de/simiil/liftlog/ui/settings/DocumentPickers.kt` (expect), `app/src/androidMain/.../DocumentPickers.android.kt`, `app/src/iosMain/.../DocumentPickers.ios.kt`
- Create: `ui/session/NotificationPermissionEffect.kt` (expect/actual triple, same layout)
- Create: `ui/theme/PlatformColorScheme.kt` (expect/actual triple)
- Modify: `ui/settings/DocumentIo.kt` (Uri → DocumentHandle), `ui/settings/SettingsScreen.kt:62-69`, `ui/settings/SettingsViewModel.kt`, `ui/session/ActiveSessionScreen.kt:89-104`, `ui/theme/Theme.kt`

**Interfaces (produced here, consumed by Task 4's move):**

```kotlin
// DocumentHandle: common name for a platform document reference
expect class DocumentHandle                                  // androidMain: actual typealias DocumentHandle = android.net.Uri
                                                             // iosMain: actual class DocumentHandle(val url: NSURL) — M8 wires it
interface DocumentIo {                                       // unchanged shape, Uri → DocumentHandle
    suspend fun readText(handle: DocumentHandle): String
    suspend fun writeText(handle: DocumentHandle, text: String)
}

@Composable expect fun rememberCreateDocumentLauncher(mimeType: String, onResult: (DocumentHandle?) -> Unit): (suggestedName: String) -> Unit
@Composable expect fun rememberOpenDocumentLauncher(mimeTypes: List<String>, onResult: (DocumentHandle?) -> Unit): () -> Unit

@Composable expect fun NotificationPermissionEffect(onResult: () -> Unit)   // iOS actual: {} (no session notification in v1)

@Composable expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?  // Android: dynamic*ColorScheme(LocalContext); iOS: null
```

- [ ] **Step 1:** Android actuals wrap the existing code verbatim (`rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType))`, the SDK-33 permission block from `ActiveSessionScreen:92-104`, `dynamicDark/LightColorScheme(LocalContext.current)`). iOS actuals: launchers return no-op lambdas invoking `onResult(null)` is WRONG for UX but never runs in M7 — implement as `{ }` no-ops with a `// M8: UIDocumentPickerViewController` marker; permission effect empty; color scheme `null`.
- [ ] **Step 2:** Rewire consumers: `SettingsScreen` uses the two launchers; `viewModel.export/prepareImport` signatures take `DocumentHandle`; `ActiveSessionScreen` replaces its block with `NotificationPermissionEffect(viewModel::onNotificationPermissionResult)`; `Theme.kt`'s `when` becomes:

```kotlin
val colorScheme = platformDynamicColorScheme(darkTheme)
    ?: if (darkTheme) DarkColorScheme else LightColorScheme
```

(dynamicColor=false previews: keep the parameter, `if (dynamicColor) platformDynamicColorScheme(darkTheme) else null ?: …`.)

- [ ] **Step 3:** CI parity + scoped `CriticalLoggingPathTest` (permission effect) → PASS; manual export/import round-trip on emulator.
- [ ] **Step 4: Commit** — `git commit -m "refactor(kmp): expect/actual seams for documents, permission prompt, dynamic color (#NN)"`

### Task 4: Move `ui/` + remaining `di/` to commonMain

**Files:**
- Move to `commonMain`: `ui/**` (all screens, components, theme, navigation, `LiftLogApp`), `MainViewModel.kt`, `di/AppModules.kt` remnants (`viewModelModule` becomes common), `notification/NotificationPermissionTick.kt` → `domain/logging/` (it's a pure event tick consumed by a common ViewModel)
- Stays `androidMain`: `MainActivity.kt`, `LiftLogApplication.kt`, `notification/**` (minus the moved tick), all `*.android.kt` actuals, `ui/format/AndroidLocaleFormatters.kt`, minimal res

- [ ] **Step 1:** git mv the trees; fix the handful of imports that shift (`NotificationPermissionTick` package). Common code MUST NOT import `android.*`/`androidx.activity.*`/`androidx.hilt.*` — compile errors here mean a missed seam; route it through Task 3's expects rather than adding new platform code paths.
- [ ] **Step 2:** ViewModel unit tests: stay in androidUnitTest (they use JUnit4 + `MainDispatcherRule`); they compile against commonMain classes unchanged. (Optional later hardening, not this PR: port to commonTest with `Dispatchers.setMain`.)
- [ ] **Step 3:** `./gradlew testDebugUnitTest assembleDebug` green; `./gradlew iosSimulatorArm64Test` green; `./gradlew compileKotlinIosArm64` green.
- [ ] **Step 4: Commit** — `git commit -m "refactor(kmp): UI + ViewModels to commonMain (#NN)"`

### Task 5: iOS entry point + iOS `LocaleFormatters`

**Files:**
- Create: `app/src/iosMain/kotlin/de/simiil/liftlog/MainViewController.kt`
- Create: `app/src/iosMain/kotlin/de/simiil/liftlog/ui/format/IosLocaleFormatters.kt` (+ bind in iOS `platformModule`)

- [ ] **Step 1:**

```kotlin
// MainViewController.kt — consumed by the M8 Xcode shell; ThemePreference wiring mirrors MainActivity
fun MainViewController(): UIViewController = ComposeUIViewController {
    startKoinOnce()   // private fun with a global guard: startKoin { modules(appModules) }
    val viewModel: MainViewModel = koinViewModel()
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    LiftLogTheme(themePreference = themePreference) { LiftLogApp() }
}
```

```kotlin
// IosLocaleFormatters — NSDateFormatter/NSCalendar; M8 verifies on-device
class IosLocaleFormatters : LocaleFormatters {
    private fun fmt(dateStyle: NSDateFormatterStyle, timeStyle: NSDateFormatterStyle, tz: TimeZone) =
        NSDateFormatter().apply {
            this.dateStyle = dateStyle; this.timeStyle = timeStyle
            this.timeZone = NSTimeZone.timeZoneWithName(tz.id) ?: NSTimeZone.defaultTimeZone
        }
    private fun kotlin.time.Instant.ns() = NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble())
    override fun mediumDate(instant: Instant, timeZone: TimeZone) =
        fmt(NSDateFormatterMediumStyle, NSDateFormatterNoStyle, timeZone).stringFromDate(instant.ns())
    override fun mediumDateShortTime(instant: Instant, timeZone: TimeZone) =
        fmt(NSDateFormatterMediumStyle, NSDateFormatterShortStyle, timeZone).stringFromDate(instant.ns())
    override fun weekdayDayMonth(instant: Instant, timeZone: TimeZone) =
        NSDateFormatter().apply {
            setLocalizedDateFormatFromTemplate("EEE d MMM")
            timeZone.id.let { this.timeZone = NSTimeZone.timeZoneWithName(it) ?: NSTimeZone.defaultTimeZone }
        }.stringFromDate(instant.ns())
    override fun timeHm(instant: Instant, timeZone: TimeZone) =
        NSDateFormatter().apply {
            dateFormat = "HH:mm"
            this.timeZone = NSTimeZone.timeZoneWithName(timeZone.id) ?: NSTimeZone.defaultTimeZone
        }.stringFromDate(instant.ns())
    override fun relativeDate(thenMillis: Long): String =
        NSRelativeDateTimeFormatter().localizedStringForDate(
            NSDate.dateWithTimeIntervalSince1970(thenMillis / 1000.0), relativeToDate = NSDate.date(),
        )
    override fun prefers24HourTime(): Boolean =
        NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale)?.contains("a")?.not() ?: true
    private fun oneDecimalFormatter() = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterDecimalStyle
        minimumFractionDigits = 1u
        maximumFractionDigits = 1u
        usesGroupingSeparator = false
    }
    override fun oneDecimal(value: Double): String =
        oneDecimalFormatter().stringFromNumber(NSNumber(double = value)) ?: value.toString()
    override fun signedOneDecimal(value: Double): String =
        oneDecimalFormatter().apply { positivePrefix = "+" }.stringFromNumber(NSNumber(double = value)) ?: value.toString()
    override fun nameComparator(): Comparator<String> =
        Comparator { a, b -> (a as NSString).localizedCompare(b).toInt() }
}
```

(Exact Kotlin/Native API spellings may need small adjustments against the platform.Foundation stubs — the acceptance bar in M7 is `compileKotlinIosSimulatorArm64` green; behavior is M8-verified.)

- [ ] **Step 2:** `./gradlew compileKotlinIosSimulatorArm64 compileKotlinIosArm64` → green; also `./gradlew linkDebugFrameworkIosSimulatorArm64` → green (proves the whole app links as a framework).
- [ ] **Step 3: Commit** — `git commit -m "feat(ios): MainViewController entry + iOS LocaleFormatters (compile-only) (#NN)"`

### Task 6: M7 exit verification + docs + PR

- [ ] **Step 1: Full matrix, in order:**

```bash
./gradlew ktlintFormat
./gradlew ktlintCheck lint testDebugUnitTest assembleDebug          # CI parity — green
./gradlew iosSimulatorArm64Test                                     # common tests on iOS sim — green
./gradlew linkDebugFrameworkIosSimulatorArm64                       # framework links — green
./gradlew connectedDebugAndroidTest                                 # full instrumented suite — green
```

- [ ] **Step 2: Manual Android regression sweep** (emulator + Pixel 9 if attached): fresh-install seed → log workout end-to-end → plans edit → history → analytics (charts!) → export → wipe (clear data) → import → German locale spot-check. Any deviation from pre-M7 behavior is a blocker.
- [ ] **Step 3: Docs.** CLAUDE.md: build commands gain `./gradlew iosSimulatorArm64Test` + note the KMP layout; `docs/01-architecture.md` §modules updated (commonMain/androidMain/iosMain map); roadmap M7 row checked off. Tick the M7 boxes in issue #NN.
- [ ] **Step 4:** Push `m7-ui-common`, PR `M7-PR5: UI common, CMP resources, JetBrains navigation (#NN)`. **M7 review gate: after merge, write the M8 plan (Xcode shell, real iOS actuals, polish, CI macOS job, TestFlight) against the then-current versions.**

---

## Self-review checklist (ran at authoring time)

- Spec coverage: PR1→Koin ✓, PR2→datetime+seam ✓ (expect/actual refined to DI-bound interface, noted), PR3→Vico ✓, PR4→KMP+Room/DataStore+compile-only iOS ✓, PR5→resources+navigation+UI move+lint-gate replacement (StringsParityTest) ✓, session notification stays androidMain with no common interface (simpler realization of the spec's no-op seam, noted) ✓, exit gates match spec ✓.
- Versions verified 2026-07-15 via web: Koin BOM 4.1.1, kotlinx-datetime 0.8.0, CMP 1.11.1 (needs Kotlin ≥2.3 for native), JB navigation 2.9.2, Room 2.8.4 + sqlite 2.7.0. Kotlin/KSP exact patch resolved by procedure in PR4-Task 1.
- Known judgment calls an executor must not "fix" silently: UTC week boundaries (existing behavior, kept); `Decimals` locale params dropped from the common API (Android actual keeps overloads for tests); formatter objects created per call (fixes a latent stale-locale bug — the only intentional behavior change, invisible unless the user switches language mid-session).
