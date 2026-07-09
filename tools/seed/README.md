# Seed pipeline

Tooling that produced (and can reproduce) the built-in exercise catalog
(`app/src/main/assets/seed/exercises.v<N>.json`). History and rationale:
`docs/superpowers/specs/2026-07-09-37-seed-v2-catalog-design.md`,
`docs/02-data-spec.md` Appendix A.

## External inputs (not checked in, live in `~/Code/`)

| File | What | Provenance |
|---|---|---|
| `exercises.json` | free-exercise-db dump, 873 entries | provided by owner |
| `exercise-judged.json` | fedb id → relevance (1 = seed, 2 = cut) | model pass under owner rubric, owner-reviewed via the debug CSV (2026-07-09) |

## Checked-in data

- `existing-id-map.json` — fedb id → `{uuid, appName}` for built-ins the app already
  shipped. **These UUIDs are permanent** (user history references them; sync-readiness).
- `names-de.json` — English seed name → German display name for every seeded exercise.
  German first pass is model-translated; native-speaker review requested.

## Scripts (run with `python3`, any CWD)

- `match_existing.py` — (historical) matched the shipped seed's names against fedb to
  produce `existing-id-map.json`; 22 exact matches + 47 curated overrides inline.
- `convert_seed_v2.py` — fedb + judgments → `~/Code/exercises.v2.json`. Shipped built-ins
  keep uuid/name/muscleGroup/equipment from the CURRENT asset (so reruns never mutate
  shipped identity); new entries get deterministic UUIDv5 ids
  (`uuid5(NAMESPACE_URL, "liftlog:seed:" + fedb_id)`). Validates the SeedAssetTest
  contracts. Copy the output into `assets/seed/` (and bump
  `ExerciseSeeder.SEED_VERSION`) to ship.
- `export_csv.py` — `~/Code/exercise-export.csv`: all 873 fedb entries with raw + mapped
  fields, relevance, and seed UUID. Owner's review/debug artifact (Excel-friendly).
- `gen_i18n.py` — regenerates the three i18n artifacts from the asset + `names-de.json`:
  EN `exercise_*` strings block, DE block, `BuiltInExerciseNames.kt`. Never edit those
  outputs by hand; `BuiltInExerciseNamesTest` locks them in sync with the seed.

## Changing the catalog

1. Edit `~/Code/exercise-judged.json` (and/or the fedb dump), or add name/equipment
   fixes in `seed_common.py`.
2. `python3 tools/seed/convert_seed_v2.py` → review `~/Code/exercise-export.csv`
   (`python3 tools/seed/export_csv.py`).
3. Copy the output over the asset, renamed to the NEXT version
   (`exercises.v<N+1>.json`, delete the old one), bump `SEED_VERSION`, add any new
   names to `names-de.json`, run `gen_i18n.py`, fix test counts.
