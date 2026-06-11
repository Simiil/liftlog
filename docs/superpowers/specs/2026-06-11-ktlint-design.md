# ktlint formatting — design

**Date:** 2026-06-11 · **Status:** approved (owner, in-session)

## Goal

Mechanical, enforced Kotlin formatting so style never occupies review attention and
never drifts back into feature PRs.

## Decisions

- **Tool:** [ktlint](https://pinterest.github.io/ktlint/) via the
  `org.jlleitschuh.gradle.ktlint` Gradle plugin (v14.2.0), the de-facto ktlint wrapper.
  Added through the version catalog, applied to `:app` (the only module).
  Alternatives considered: Spotless + ktlint engine (broader file coverage, more config —
  not needed), raw ktlint CLI task (DIY maintenance for no gain).
- **Enforcement:** CI gate. The build job runs
  `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug`; locally,
  `./gradlew ktlintFormat` fixes. CLAUDE.md documents both.
- **Style:** ktlint defaults (`ktlint_official`) in a root `.editorconfig`, with one
  exception: `ktlint_function_naming_ignore_when_annotated_with = Composable`
  (PascalCase `@Composable` functions are the Compose convention). No further rule
  tuning until something proves annoying in practice.
- **One-time reformat:** `ktlintFormat` over the whole repo as its own pure-reformat
  commit, separate from the wiring commit, so the PR reviews as config + mechanical diff.
  Hand-fixes for the rules ktlint cannot auto-correct (wildcard imports → explicit;
  `seA_ex1`-style test locals → camelCase).

## Verification

- `ktlintCheck` fails on the unformatted tree (proves the gate bites), passes after.
- Full CI command green locally.
- Instrumented smoke (`CriticalLoggingPathTest`) green — the mechanical diff changes no
  behavior.
