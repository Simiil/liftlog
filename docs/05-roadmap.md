# 05 — Roadmap

> **Status:** Draft for review · 2026-06-07
> Six milestones, each independently reviewable and ending in a working state. Logging lands at **M2** — earliest possible dogfooding; everything after improves an already-usable app. Implementation starts only after this doc set is approved ([HANDOFF.md](../HANDOFF.md) §0).

## M0 — Scaffold

**Goal:** A building, CI-green, empty-but-runnable app embodying the architecture decisions.

**Deliverables:** Gradle project (Kotlin, Compose BOM, minSdk 31), Hilt wired, Navigation skeleton with bottom bar + placeholder screens, M3 theme (dynamic color + manual system/light/dark toggle, persisted in DataStore), GitHub Actions workflow ([01-architecture](01-architecture.md) §8), package layout per [01-architecture](01-architecture.md) §2.

**Exit criteria:** CI runs lint + unit tests + assembles on PR; app installs and navigates between empty tabs; theme toggle works.

## M1 — Data layer

**Goal:** The full schema, repositories, and seed — tested before any feature UI exists.

**Deliverables:** All entities/DAOs/`AppDatabase` per [02-data-spec](02-data-spec.md) §3–4; conventions (UUID, timestamps, soft-delete cascades) in repository implementations; domain models + repository interfaces; built-in exercise seeder (`exercises.v1.json` with final fixed UUIDs, Appendix A list); DAO + repository test suite; instrumented-test CI job.

**Exit criteria:** DAO tests green in CI (hot-path queries, soft-delete filtering, seeder idempotency); fresh install contains ~70 exercises, verifiable via test.

## M2 — Logging flow (the product)

**Goal:** A complete, fast, kill-proof diary — usable for real training from this milestone on.

**Deliverables:** Home (template chips deferred to M3 — empty-session start + resume card + recent list), Active Session screen per [03-ux-spec](03-ux-spec.md) §4 (card stack, steppers, inline numpad, pre-fill rules, long-press RPE/note edit, finish/discard), Exercise picker incl. create-custom, History list + session detail (read/edit).

**Exit criteria:** The [03-ux-spec](03-ux-spec.md) §4.5 tap math holds on-device (1-tap repeat set; pre-fill from previous session works); Compose UI test covers start → log pre-filled → adjust → log → **process-death → resume** path; backgrounding mid-session loses nothing.

## M3 — Plans

**Goal:** Templates make session start instant.

**Deliverables:** Plans list, template editor (reorder, targets) per [03-ux-spec](03-ux-spec.md) §6; template → session snapshot copy ([02-data-spec](02-data-spec.md) §1); Home template chips; target display (`2/3`, rep-range hints) in the logging UI.

**Exit criteria:** Cold start → template chip → first set = 3 taps, verified by UI test; editing/deleting a template provably leaves past sessions untouched (DAO test); ad-hoc deviation from a templated session works.

## M4 — Analytics

**Goal:** The logging habit pays off.

**Deliverables:** `domain/analytics` pure functions **with the [04-analytics-spec](04-analytics-spec.md) §5 fixture tests written first**; AnalyticsDao + flows; exercise browser (trend badges, sparklines, header card) and exercise detail (metric chips, ranges, PR markers, recent list) per [03-ux-spec](03-ux-spec.md) §5; Vico integration behind the `ui/components` chart wrapper; bodyweight metric swap; downsampling.

**Exit criteria:** All formula/trend/downsampling fixtures green; charts live-update while logging; browser scrolls smoothly with full seed library + a year of synthetic data (manual perf check on device).

## M5 — Export/import + polish

**Goal:** No data loss, accessible, releasable.

**Deliverables:** Versioned JSON export/import per [02-data-spec](02-data-spec.md) §6 (SAF picker, validation, full-replace confirmation flow); Settings screen complete; golden-file + round-trip tests; accessibility audit against [03-ux-spec](03-ux-spec.md) §7 (TalkBack walkthrough, 200% font scale, target sizes); release build config (R8, signing); app icon; final app-name decision (TBD → real name).

**Exit criteria:** Export → wipe → import round-trip is lossless (automated test); importer rejects newer-version and corrupt files gracefully; a11y audit checklist passes; release AAB builds in CI.

## Sequencing notes

- M1 before any UI: schema mistakes are cheapest before screens depend on them; tests lock conventions early.
- Analytics (M4) after Plans (M3) only because M3 is small and unblocks full dogfooding; M4 doesn't depend on M3 and could swap if priorities change.
- Each milestone = one reviewable PR series; review gate at every exit ([HANDOFF.md](../HANDOFF.md) §8).

## Post-v1 candidates (explicitly out of scope, recorded so they're not re-litigated)

In rough priority order: rest timer (UI slot already reserved) · home-screen widget (1-tap logging from launcher) · warm-up set flag (excluded from PRs/volume) · per-exercise stepper increments · plate calculator · cloud sync (architecture ready, [01-architecture](01-architecture.md) §4) · Health Connect · tablet layouts.
