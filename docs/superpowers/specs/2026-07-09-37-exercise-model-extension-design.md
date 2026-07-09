# Design: exercise model extension (issue #37)

Owner-approved 2026-07-09.

## Problem

Issue #37 wants a much larger built-in exercise catalog. The candidate seed data
(free-exercise-db shaped, ~873 entries) carries attributes the model can't hold:
a force classification (push/pull/static), equipment types beyond the current five,
and per-exercise secondary muscle lists. This ticket extends the model and all
plumbing so a later ticket can land the refined seed list as a pure data swap.
**No new seed data ships here** — the owner is still refining the JSON.

## Decisions

- **`Force` is nullable.** New enum `Force { PUSH, PULL, STATIC }`;
  `fromStorageValue` returns `null` for unknown/absent (unlike the other enums'
  catch-all fallback — there is no sensible catch-all member, and the source data
  itself has `force: null` for stretches etc.). Custom exercises get `null`.
- **`Equipment` grows to the full source vocabulary** (owner decision over
  adding only the ticket's three): existing five plus `KETTLEBELL`,
  `MEDICINE_BALL`, `FOAM_ROLLER`, `BANDS`, `EXERCISE_BALL`, `OTHER`. The
  unknown-value fallback changes `MACHINE` → `OTHER` now that a real catch-all
  exists. Source values map at seed-refinement time: `e-z curl bar` → `BARBELL`,
  `body only`/null → `BODYWEIGHT`.
- **`MuscleGroup` stays the coarse 12.** The source's 17 fine-grained names map
  to coarse groups during seed refinement (lats/middle back/traps → `BACK`,
  neck/abductors/adductors → `OTHER`, dedupe). Primary stays singular;
  secondaries use the same enum — no second vocabulary.
- **Secondary muscles are one TEXT column, not a junction table.** Stored as a
  JSON string array (`["BACK","BICEPS"]`) via Room `@TypeConverter`
  (kotlinx.serialization is already a dependency). Decoded as `List<String>`
  then mapped per-element through `fromStorageValue` so unknown names degrade to
  `OTHER` and a malformed cell degrades to an empty list — never a crash. A
  junction table was rejected: no per-row metadata, no SQL-side query need
  (picker filtering is in-memory in the ViewModel), and it would bloat the
  backup format and every exercise read.
- **Seeding becomes version-gated convergence** (owner decision — content-diff
  on every launch won't scale to a big seed). Details below.
- **UI scope: data layer only.** `force`/`secondaryMuscleGroups` surface in no
  screen yet; `createCustom` keeps its signature. Only the compiler-forced
  labels for new `Equipment` values are added (EN + DE); they appear
  automatically in the picker's existing equipment filter row.

## Design

1. **Domain.** `Exercise` gains `force: Force?` and
   `secondaryMuscleGroups: List<MuscleGroup>` (empty = none; excludes the
   primary; no duplicates).
2. **Persistence (Room v2 → v3).** `ExerciseEntity` gains the same fields.
   Converters: `Force?` ↔ name string, `List<MuscleGroup>` ↔ JSON array (one
   shared default `Json` instance — Room instantiates `Converters` without DI).
   `MIGRATION_2_3`: `ALTER TABLE exercises ADD COLUMN force TEXT`,
   `ADD COLUMN secondaryMuscleGroups TEXT NOT NULL DEFAULT '[]'`, plus
   `CREATE TABLE seed_state` (below). `DB_SCHEMA_VERSION = 3`; exported schema
   `3.json` committed.
3. **Seed state.** New single-row table
   `seed_state(id INTEGER PK = 1, appliedSeedVersion INTEGER NOT NULL)` with a
   minimal entity + DAO. Local derived state — deliberately **not** in the
   backup format.
4. **Seeder.** Compile-time `SEED_VERSION = 1` constant; asset name derived
   (`seed/exercises.v$SEED_VERSION.json`). On launch: read the `seed_state` row
   only; if `appliedSeedVersion >= SEED_VERSION`, return — the asset is never
   opened or parsed. Otherwise (row missing — fresh install or first launch
   after this migration — or lower version) run the converge pass: insert
   missing rows; for existing built-in rows in the seed file, compare
   classification fields (`name, muscleGroup, equipment, force,
   secondaryMuscleGroups`) and update only when different, preserving
   `isHidden`/`createdAt` and bumping `updatedAt` only on real change. Then
   write `appliedSeedVersion = SEED_VERSION` in the same Room transaction — a
   crash mid-seed re-runs the idempotent converge next launch. Stored version
   higher than the constant (app downgrade): skip. Convergence never removes:
   a DB row absent from the seed file is left untouched (dropping an exercise
   from a future seed must not delete user history hanging off it).
5. **Seed schema.** `SeedExercise` gains `force: String? = null` and
   `secondaryMuscleGroups: List<String> = emptyList()`; the existing
   `exercises.v1.json` parses unchanged.
6. **Backup (format v2 → v3).** `ExerciseDto` gains `force: String? = null`
   and `secondaryMuscleGroups: List<String> = emptyList()` — defaults keep
   v1/v2 files importing cleanly (the `SessionDto.rpe` pattern);
   `encodeDefaults = true` keeps them always on the wire.
   `CURRENT_FORMAT_VERSION = 3`; golden backup regenerated. Import maps through
   the lenient `fromStorageValue` paths. **Restore must reset seeding**: with
   version gating, the seeder no longer self-heals every launch, so the restore
   path deletes the `seed_state` row inside the restore transaction and
   re-triggers the seeder afterwards (restoring an old backup may bring stale or
   missing built-ins).
7. **i18n.** Six new equipment strings in `values/` and `values-de/`
   (Kettlebell, Medizinball, Faszienrolle, Bänder, Gymnastikball, Sonstiges).
   Lint gates enforce completeness.
8. **Testing.** Converter round-trips (force null/values; secondary
   empty/multi/unknown-name/malformed-cell). Migration 2→3 (old rows get
   `NULL` force + `'[]'` secondaries; `seed_state` created empty). Seeder:
   skips without opening the asset when current, applies when row missing or
   version lower, stores version after apply, skips on downgrade, converge
   updates changed classification, preserves `isHidden`/`createdAt`, no-op
   run bumps nothing. Backup: golden regenerated; a v2-era file still imports;
   restore resets + re-runs seeding. `SeedAssetTest`: JSON `seedVersion`
   matches `SEED_VERSION`; secondaries valid enum names, deduped, never contain
   the primary.
9. **Docs.** `docs/02-data-spec.md` §3 (exercises columns + enum lists,
   `seed_state`), §6 (backup v3), §7 (seeder convergence semantics).

## Out of scope

New seed exercises (follow-up ticket once the JSON is refined), surfacing
force/secondary muscles in any UI, fine-grained muscle vocabulary, picker
filter changes, custom-exercise form inputs for the new fields.
