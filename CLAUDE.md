# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**LiftLog** (name is a placeholder, TBD) — an offline-first Android app for tracking weightlifting workouts: training diary (log weight/reps/sets), training plans, and progress analytics. The full project brief is in `HANDOFF.md` — read it before doing substantive work.

## Current phase: implementation

Docs under `/docs` are approved (owner green-lit implementation 2026-06-07).
Implementation follows the milestones in `docs/05-roadmap.md`; M0 (scaffold),
M1 (data layer), M2 (logging flow — Home, Active Session, Exercise Picker,
History), M3 (Plans), M4 (Analytics), M5 (export/import + polish), and M6
(internationalization — German) are done and merged. **M7 (multiplatform-ready
refactor for the iPhone port) is in progress** per
`docs/superpowers/specs/2026-07-15-ios-port-design.md` and the plan in
`docs/superpowers/plans/2026-07-15-m7-multiplatform-ready.md`, tracked in issue
#47, delivered as a five-PR series (PR1 Hilt→Koin, PR2 kotlinx-datetime, PR3
Canvas chart, PR4 KMP module conversion, PR5 CMP resources + UI to common). Each
milestone is a reviewable PR series with a review gate at its exit criteria.

## Build & test

- `./gradlew assembleDebug` — build the debug APK
- `./gradlew testDebugUnitTest` — JVM unit tests
- `./gradlew lint` — Android lint
- `./gradlew ktlintFormat` — auto-format Kotlin (ktlint, style in `.editorconfig`); run before committing
- `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug` — exactly what CI runs
- `./gradlew connectedDebugAndroidTest` — instrumented Room/DAO + seeder tests. **These run
  locally now**: KVM works on this machine and an emulator is usually up (`emulator-5554`, a
  Pixel 9 Pro XL AVD); a physical Pixel 9 is sometimes attached over adb too. Note this task
  fans out to **every** attached device — scope it with
  `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`. CI runs the same tests on a
  headless **API-34** emulator via the `reactivecircus/android-emulator-runner` action. (A
  Gradle-managed device `pixelApi34DebugAndroidTest` is also configured but its snapshot
  emulator won't boot on GitHub's hosted runners — hence the action.)
- **Compose UI tests now run locally too** (since M3). They used to crash on this machine's
  **Android 16** targets with `NoSuchMethodException:
  android.hardware.input.InputManager.getInstance` (a known Espresso/API-36 incompatibility);
  pinning **`espresso-core` 3.7.0** (`androidx-test-espresso-core` in the catalog) fixes that
  reflection call, so `createAndroidComposeRule` tests (e.g. `CriticalLoggingPathTest`,
  `TemplateStartPathTest`) pass on `emulator-5554` as well as in CI (API 34). Note: on the
  Active Session screen (running 1s timer), poll-based `composeRule.waitUntil` reads a stale
  tree — use `waitForIdle()` + re-check (see `TemplateStartPathTest`'s `await` helper).
- Needs JDK 17+ and an Android SDK (`local.properties` → `sdk.dir`); the Gradle wrapper is
  committed. To see a change on-device: `./gradlew installDebug` (or `assembleDebug` then
  `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`), launch, and
  drive with `adb shell` (tap/screencap; resolve element bounds via `uiautomator dump`).

## Fixed technical decisions (do not relitigate)

- Native Android: **Kotlin + Jetpack Compose**, Material 3
- **Room** (SQLite) as single source of truth; offline-first
- MVVM + unidirectional data flow; repository pattern (UI ↔ ViewModel `StateFlow` ↔ Repository ↔ Room — ViewModels never talk to Room directly)
- Kotlin Coroutines + Flow; **Koin** for DI (multiplatform-ready; was Hilt pre-M7)
- **No cloud, no backend, no network code in v1**

Open questions (min SDK, units default, rest timer, theming, etc.) are listed in HANDOFF.md §9 — propose reasoned defaults in the docs and flag them for review rather than blocking.

## Hard constraints (check every decision against these)

1. **Zero setup, zero account, zero network.** The app must be fully functional the moment it's installed.
2. **Logging a set must be ultra-fast.** ~1–2 taps with values pre-filled from the previous set; no flow-breaking modals. Any flow that adds taps to the core logging path must be justified or cut.
3. **Sync-ready but sync-free.** Don't build sync, but never preclude it: UUIDs (not autoincrement ints) for entity IDs, `createdAt`/`updatedAt` timestamps, soft-deletes instead of hard deletes, repository abstraction a future remote source could slot behind. All invisible and zero-cost to the v1 user.
4. **No data loss.** Backgrounding/process death must be survivable; v1 includes local export/import (versioned, human-readable format).
5. **Justify every third-party dependency.** Prefer Jetpack and well-maintained libraries.

## Out of scope for v1

No social features, no accounts/cloud sync, no AI/coaching, no Health Connect, no cardio/nutrition — strictly resistance-training logging. (Sync and Health Connect are future candidates; don't preclude them.)
