# Design: seed v2 exercise catalog (issue #37, part 2)

Owner-approved 2026-07-09. Builds on the model extension
(`2026-07-09-37-exercise-model-extension-design.md`, PR #40); branch
`feature/37-seed-exercises` stacks on `feature/37-more-seeded-exercises`.

## Problem

Issue #37 wants a much larger built-in catalog. The model extension made the
seed swap possible; this part ships the data: 331 exercises (69 existing +
262 new), curated from the 873-entry free-exercise-db dump via an owner-reviewed
relevance pass, with force/equipment/secondary-muscle classification and full
EN + DE name localization.

## Curation pipeline (already run; owner reviewed the CSV)

Deterministic scripts — committed under `tools/seed/` so the pipeline is
re-runnable when judgments change:

- `match_existing.py` — maps the 69 shipped built-ins to free-exercise-db
  entries (22 exact name matches + 47 curated overrides). **Shipped UUIDs,
  names, muscle groups, and equipment never change**; the match only sources
  `force` + `secondaryMuscleGroups`. One curated exception: Kettlebell
  Swing's equipment moves `DUMBBELL` → `KETTLEBELL` (pre-vocabulary
  compromise). Output: `existing-id-map.json`.
- Relevance judgments (`exercise-judged.json`, 873 ids → 1/2) authored by a
  model pass under owner rubric: keep a large catalog; cut only
  manufacturer-specific, very weird/uncommon, near-duplicates, and pure
  cardio (not loggable as sets × reps × weight). Owner reviewed via
  `export_csv.py`'s debug CSV and approved: 331 in, 542 out.
- `convert_seed_v2.py` — emits `exercises.v2.json` (`seedVersion: 2`), new
  entries with **deterministic UUIDv5** ids
  (`uuid5(NAMESPACE_URL, "liftlog:seed:" + fedb_id)`), secondaries mapped to
  the coarse 12 vocabulary, deduped, primary excluded. Validates the
  SeedAssetTest contracts at build time.

## Decisions

- **Asset swap**: `exercises.v2.json` lands in `assets/seed/`;
  `ExerciseSeeder.SEED_VERSION = 2`; the **v1 asset is deleted** (dead weight
  in the APK; git history keeps it). JVM tests derive the asset path from
  `SEED_VERSION` instead of hardcoding `v1`.
- **Name fix in seed data**: `Landmine 180's` → `Landmine 180s` (grammar).
  The other apostrophe names stay correct: `Farmer's Walk`, `Child's Pose`,
  `World's Greatest Stretch`.
- **Apostrophes vs the i18n sync test**: Android requires `\'` escaping in
  string resources, while `BuiltInExerciseNamesTest` reads values verbatim.
  The test's regex comparison learns to unescape `\'` (comment updated);
  seed names remain unescaped. This is a deliberate, minimal contract change.
- **i18n at full parity**: all 331 built-ins get `exercise_<slug>` string
  resources in `values/` and `values-de/` plus `BuiltInExerciseNames` map
  entries — required by the existing sync tests. The 262 new EN strings equal
  the seed names verbatim (test contract). German first pass is
  model-translated under the existing convention (established English gym
  terms stay English), **flagged for native-speaker review** like M6 PR3.
  The existing 69 DE names are untouched.
- **Generated, not hand-typed**: a new `tools/seed/gen_i18n.py` generates the
  EN block, DE block (from a checked-in translation table
  `tools/seed/names-de.json`), and `BuiltInExerciseNames.kt` from
  `exercises.v2.json`. Slugs are `[a-z0-9_]`, collision-checked (verified:
  none among the 331).
- **PR #40 follow-ups land here** (assigned by its final review):
  - Seeder converge does **one bulk read** (all rows by id into a map)
    instead of a point query per seed entry.
  - The three `?: Equipment.MACHINE` missing-exercise display placeholders
    (DayEditorViewModel, ActiveSessionViewModel, SessionDetailViewModel)
    become `?: Equipment.OTHER`.
  - `docs/02-data-spec.md` **Appendix A** is replaced by a per-muscle-group
    summary (counts, equipment spread) pointing at the seed asset as the
    source of truth — no 331-name listing.

## Design

1. **Asset + version**: add `exercises.v2.json` (from `~/Code/`), delete
   `exercises.v1.json`, bump `SEED_VERSION` to 2.
2. **Bulk converge**: `ExerciseDao` gains `findAllAny(): List<ExerciseEntity>`
   (unfiltered full read — the table is small; avoids SQLite's 999-variable
   limit that a by-id `IN` list would eventually hit). The seeder builds an
   id-keyed map once; `findByIdAny` becomes unused and is removed (with its
   `FakeExerciseDao` override). `BackupDao`'s "sole place that reads without
   the deletedAt filter" doc comment is softened accordingly.
3. **i18n generation**: `gen_i18n.py` outputs the three blocks; EN/DE
   `strings.xml` sections and `BuiltInExerciseNames.kt` are replaced by the
   generated content (69 existing EN/DE entries and their resource names are
   preserved by keying the generator on the existing map first).
4. **Placeholder switch**: 3 ViewModels, `MACHINE` → `OTHER`.
5. **Tests**: SeedAssetTest count 331 + path derived from constant;
   BuiltInExerciseNamesTest path derived + apostrophe unescape;
   ExerciseSeederTest 69 → 331; BackupRoundTripTest 71 → 333;
   GermanExerciseSearchTest unchanged (existing DE names stable). All
   SeedAssetTest data-quality contracts (unique ids/names, valid enums,
   secondaries deduped/primary-excluded, seedVersion ↔ constant) now bite
   against real data.
6. **Docs**: 02-data-spec §7 asset reference + Appendix A summary rewrite.

## Out of scope

Surfacing force/secondary muscles in UI, picker UX for the larger catalog
(explicit owner stance: improve UI later, don't shrink data), further seed
expansions (pipeline reruns), native-speaker review itself.
