# iPhone port via Kotlin Multiplatform: design

> **Status:** Designed with owner 2026-07-15 on `feature/ios-port` (based on
> v0.5.0). Tracking issue to be filed; delivered as roadmap milestones **M7**
> (multiplatform-ready) and **M8** (iOS app), each a reviewable PR series with
> a review gate at its exit criteria. Sizing was measured against v0.5.0:
> ~14.2k LOC main Kotlin (ui 9.6k, domain 1.2k, data 2.5k), 13.9k LOC tests,
> Hilt in 35 files, `java.time` in 35 files, 232 `stringResource` call sites,
> 315 strings × 2 locales, Vico in exactly 1 file, true platform code in ~5
> files. **M7 starts only after M6 (i18n German) lands** — the strings
> migration must not race the translation PRs.

**Goal:** LiftLog runs on iPhone from the same codebase — same offline-first
Room data, same screens, same logging speed — with Android remaining the
primary platform and unchanged in behavior throughout the port. iOS v1 is
"real iPhones via TestFlight", not an App Store listing.

## Decisions taken this session

1. **Kotlin Multiplatform + Compose Multiplatform with full UI share** — not
   a SwiftUI rewrite, not a cross-platform framework rewrite. The
   architecture is already KMP-shaped (pure domain with `nowMillis` params,
   ViewModels never touch Room, repository seams); UI is 9.6k of 14.2k LOC,
   so per-platform UI would double the bulk of maintenance for the
   *secondary* platform.
2. **Both platforms shipped; Android stays the absolute main focus.** Every
   M7 PR leaves the Android app behaviorally identical and green on existing
   CI; iOS work can pause at any point without stranding Android.
3. **Look on iOS: Material 3 as-is, plus a targeted idiom-polish budget** —
   edge back-swipe, safe-area insets, numeric keyboard types, launch screen,
   app icon. Fix what would feel *broken*, accept what merely looks
   *Material*. Native-feel theming is a post-v1 taste decision, revisited
   after TestFlight soak.
4. **DI: Hilt → Koin** (koin-core in common, `koinViewModel()` via
   koin-compose-viewmodel). Runtime-resolution risk is mitigated with Koin's
   `verify()` in a unit test. This amends the fixed-decisions list in
   CLAUDE.md (Hilt is Android-only, so *some* swap was forced; Koin beats
   kotlin-inject on ecosystem maturity and CMP integration, and beats manual
   DI on ViewModel wiring).
5. **Datetime: `java.time` → kotlinx-datetime** in common code, with
   locale-sensitive *rendering* behind an expect/actual `LocaleFormatters`
   seam (Android keeps today's `java.time`/`java.text` output verbatim; iOS
   uses `NSDateFormatter`/`NSNumberFormatter`).
6. **Module layout: one KMP module + Xcode shell.** `:app` becomes a KMP
   module whose `androidTarget` builds the same APK as today, plus a thin
   `iosApp/` Xcode project. No `:shared`/`:androidApp` split — with ~95% of
   code shared, shell modules are empty ceremony.
7. **Session notification on iOS v1: nothing.** The active session already
   survives backgrounding/process death via Room, so nothing breaks. Live
   Activity (the idiomatic analog, needs iOS 16.1 + real Swift/ActivityKit
   work) is a tracked post-v1 enhancement.
8. **Tests/CI: JVM-first plus one iOS-simulator job.** Shared-logic tests
   stay fast JVM tests on Linux CI; a new macOS job builds the iOS app and
   runs the common suite once on a simulator per PR. Android instrumented +
   Compose UI tests unchanged. No iOS UI test automation in v1.
9. **Distribution: TestFlight first.** Needs the paid Apple Developer
   account and signing, but defers listing work (real app name — still a
   placeholder — screenshots, privacy labels, review) to a later project.
10. **Process: GitHub tracking issue + two milestones (M7, M8)**, matching
    the repo's established milestone-as-PR-series convention.
11. **iOS deployment target: 16.0.** Modern baseline for 2026; leaves room
    for Live Activity (16.1+) without a target bump.

## Approaches considered

- **KMP + CMP, full UI share (chosen)** — one codebase including UI; port is
  a refactor, not a rewrite; M3 look on iOS is the accepted trade.
- **KMP logic + native SwiftUI (rejected)** — best iOS feel, but rewrites
  9.6k LOC in a second language and doubles UI maintenance forever;
  backwards given Android-first.
- **Cross-platform rewrite, e.g. Flutter/RN (rejected)** — discards 14.2k
  LOC of working Kotlin and its 13.9k LOC test suite; strictly dominated by
  the chosen option here.

## Target architecture

**Module shape.** `:app` keeps its Gradle path and directory but becomes a
KMP module (`kotlin("multiplatform")` + `com.android.application` + the CMP
plugin) with targets `androidTarget`, `iosArm64`, `iosSimulatorArm64`
(no `iosX64`). Source sets:

- `commonMain` — `domain`, `data`, `ui`, `di` (~95% of code), plus
  `composeResources/` (strings en+de).
- `androidMain` — the `notification/` stack (533 lines, 5 files),
  `MainActivity`, Android actuals.
- `iosMain` — iOS actuals plus a `MainViewController()` entry function
  (`ComposeUIViewController`).
- `commonTest` / `androidUnitTest` / `androidInstrumentedTest` — see Testing.
- `iosApp/` (repo root) — thin Swift shell (~50 lines) embedding the
  generated framework; owns Info.plist (`CFBundleLocalizations` en+de),
  launch screen, asset catalog, signing.

**The swaps.**

- **DI:** Hilt modules → Koin modules (`dataModule`, `viewModelModule`,
  per-platform `platformModule`). `@HiltViewModel`/`hiltViewModel()` call
  sites → `koinViewModel()`. The four ViewModels using `SavedStateHandle`
  (`DayEditorViewModel`, `ExerciseDetailViewModel`, `ActiveSessionViewModel`,
  `SessionDetailViewModel`) keep it — SavedStateHandle is multiplatform via
  the JetBrains lifecycle/navigation stack. A `verify()` unit test gates the
  graph.
- **Datetime:** kotlinx-datetime everywhere in common (35 files today).
  Week-start math re-anchored on `isoDayNumber` with Monday semantics
  preserved and pinned by unit tests. Rendering goes through
  `LocaleFormatters` (see seams): the three screens using
  `DateTimeFormatter` (`SettingsScreen`, `EditWorkoutSheet`,
  `SessionDetailScreen`) and the M6 locale-correct number entry/formatting
  in `domain/units/Decimals.kt`. Existing formatting behavior is pinned by
  tests *before* the swap.
- **Persistence:** Room 2.7 KMP — entities, DAOs, and migrations unchanged;
  add `RoomDatabaseConstructor`; **BundledSQLiteDriver on both platforms**
  so the SQLite version is identical everywhere (behavioral parity is worth
  ~1.5MB of APK). DataStore 1.1 KMP with an expect/actual file path. Backup
  codec and seeder are already kotlinx.serialization — they move to common
  untouched, and the export format is byte-compatible across platforms.
- **Navigation:** androidx Navigation Compose → the JetBrains multiplatform
  fork (same API, routes preserved). iOS edge-swipe-back comes from this
  stack; exact versions pinned at plan time.
- **Resources:** `values/strings.xml` + `values-de/strings.xml` (315
  strings) → CMP resources (`Res.string.*`); all 232 `stringResource` call
  sites rewritten mechanically. Plurals are supported. `values-night` theme
  colors fold into the Compose theme. App icons stay per-platform.
- **Charts:** Vico (used only in `ProgressLineChart`) → hand-rolled Canvas
  line chart in the style of the existing `Sparkline`/`RadarChart`;
  dependency deleted.

## Platform seams (complete expect/actual inventory)

Six seams; everything else compiles common.

| Seam | Android actual | iOS actual |
|---|---|---|
| Room DB builder (path + driver) | Context-based path | `NSFileManager` path |
| DataStore path | Context-based path | `NSFileManager` path |
| `LocaleFormatters` (dates, weekday names, decimal entry/format) | today's `java.time`/`java.text` code | `NSDateFormatter` / `NSNumberFormatter` |
| `DocumentIo` (export/import; 42 lines today) | existing SAF code | `UIDocumentPickerViewController` via Kotlin/Native UIKit interop (no extra Swift) |
| `SessionNotifier` (interface in common) | existing notification stack, unchanged | no-op in v1 |
| App entry | `MainActivity` | `MainViewController()` + Swift shell |

Haptics and back-handling need no seam — the UI already uses Compose-level
abstractions that CMP maps per-platform.

## Delivery plan

**M7 — Multiplatform-ready (still Android-only).** Every PR leaves the
Android app behaviorally identical and green on existing CI.

1. **PR1:** Hilt → Koin + `verify()` test; CLAUDE.md fixed-decisions and
   architecture docs amended.
2. **PR2:** kotlinx-datetime swap + `LocaleFormatters` seam (Android impl
   only), behavior pinned by tests before/after.
3. **PR3:** Vico → hand-rolled Canvas line chart; dependency deleted.
4. **PR4:** KMP module conversion — all code initially to `androidMain`,
   then `domain` + `data` to `commonMain` with Room/DataStore KMP wiring and
   the path seams; iOS targets added **compile-only**; unit tests to
   `commonTest`. (May split into two PRs at the scaffold/data boundary if
   review size demands.)
5. **PR5:** `ui` + `di` to `commonMain`; strings → CMP resources (en+de);
   navigation swapped to the JetBrains fork; M6 hardcoded-strings lint gate
   adapted to the new resource format.

**Exit gate M7:** Android app unchanged in behavior; common tests pass on
JVM **and** `iosSimulatorArm64` (run locally — the CI macOS job arrives in
M8-PR4); iOS framework compiles. Review checkpoint before M8 starts.

**M8 — iOS app, TestFlight.**

1. **PR1:** `iosApp/` Xcode shell + entry point + DB/DataStore actuals —
   app boots and full flows run in the simulator.
2. **PR2:** `DocumentIo` + `LocaleFormatters` iOS actuals — export/import
   round-trips on iOS; de+en render correctly (incl.
   `CFBundleLocalizations`).
3. **PR3:** Idiom polish: edge back-swipe verified, safe areas, numeric
   keyboard types, launch screen, app icon.
4. **PR4:** CI macOS job: build iOS app + run common tests on simulator.
5. **PR5:** Signing + TestFlight upload; manual steps documented in
   `docs/` (no Fastlane — fails the dependency-justification bar for a solo
   project).

**Exit gate M8:** TestFlight build installed on a real iPhone; manual pass
of core flows — log a workout end-to-end, plans, history, analytics,
export/import, both locales.

## Testing & CI

- **JVM stays the inner loop:** unit tests move to `commonTest` alongside
  their code and keep running on the JVM in the existing Linux pipeline.
- **iOS coverage:** the same `commonTest` suite runs once per PR on an iOS
  simulator in the new macOS job — this catches iOS-specific runtime drift
  (formatter actuals, SQLite driver, kotlinx-datetime edge cases).
- **Android instrumented tests (Room/DAO, seeder) and Compose UI tests are
  untouched** — same devices, same espresso pinning, same CI emulator job.
- **No iOS UI test automation in v1** (CMP-iOS UI testing is young); the M8
  exit gate's manual flow pass covers it, and the shared UI logic is already
  exercised by the Android Compose tests.

## Risks

- **Room-KMP / CMP-on-iOS are stable but young** → versions pinned at plan
  time; phase order keeps Android shippable at every commit; worst case,
  iOS work pauses without stranding Android.
- **M3 look on iOS** → accepted consciously (Android-first). TestFlight
  soak is the checkpoint; if it grates, the post-v1 lever is theming
  polish, not architecture.
- **i18n lint gate may not parse CMP resources** → adapted in M7-PR5; if
  Android Lint can't see common code, replace with an equivalent custom
  check so the gate never silently disappears.
- **Per-app language on iOS** works via iOS Settings (needs
  `CFBundleLocalizations`), not Android's `localeConfig` — a documented
  behavioral difference, not a bug.

## Out of scope for iOS v1

Tracked as follow-ups in the port issue: Live Activity, App Store listing
and the real-name decision, widgets, Apple Watch, HealthKit.
