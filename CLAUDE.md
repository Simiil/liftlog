# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**LiftLog** (name is a placeholder, TBD) — an offline-first Android app for tracking weightlifting workouts: training diary (log weight/reps/sets), training plans, and progress analytics. The full project brief is in `HANDOFF.md` — read it before doing substantive work.

## Current phase: design docs, not code

The project is **pre-implementation**. The first deliverables are design/specification documents under `/docs` (see HANDOFF.md §8 for the full list: product spec, architecture, data spec, UX spec, analytics spec, roadmap). **Do not write application code** until the design docs have been produced and reviewed. Once implementation starts, update this file with the Gradle build/test commands.

## Fixed technical decisions (do not relitigate)

- Native Android: **Kotlin + Jetpack Compose**, Material 3
- **Room** (SQLite) as single source of truth; offline-first
- MVVM + unidirectional data flow; repository pattern (UI ↔ ViewModel `StateFlow` ↔ Repository ↔ Room — ViewModels never talk to Room directly)
- Kotlin Coroutines + Flow; **Hilt** for DI
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
