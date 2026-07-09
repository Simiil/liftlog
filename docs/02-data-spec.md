# 02 — Data Spec

> **Status:** Draft for review · 2026-06-07
> Room is the single source of truth ([HANDOFF.md](../HANDOFF.md) §2). Schema below is the contract; Kotlin snippets are **sketches** (signatures, not implementations).

## 1. Entity-relationship overview

```
WorkoutPlan 1 ──── * PlanDayTemplate 1 ──── * TemplateExercise * ──── 1 Exercise
                            ┆                                            ▲
                            ┆ "start session from template"              │
                            ┆ COPIES rows (snapshot, see §3)             │
                            ▼                                            │
Session 1 ──── * SessionExercise * ──────────────────────────────────────┘
                     │
                     1 ──── * LoggedSet
```

**The key structural decision — sessions snapshot, never reference, template content:**
starting a session copies the template's exercise list into `SessionExercise` rows
(order + targets frozen at start time). `Session.templateId` remains only as provenance
for analytics/history labeling. Consequences:

- Editing or deleting a template never rewrites workout history.
- A session can deviate freely: ad-hoc exercises are just extra `SessionExercise` rows.
- The same exercise can appear twice in one session (heavy + light bench) because
  `LoggedSet` attaches to a `SessionExercise` row, not to the exercise itself.

## 2. Conventions (every syncable entity)

| Convention | Spec | Rationale (HANDOFF §2 sync-ready) |
|---|---|---|
| Primary key | `id: String` — UUIDv4, generated client-side | Globally unique → future cross-device reconciliation; never autoincrement |
| Timestamps | `createdAt: Long`, `updatedAt: Long` — epoch **millis UTC**. Domain layer converts to `java.time.Instant` | Last-write-wins material for a future sync engine |
| Soft delete | `deletedAt: Long?` — `NULL` = live. No hard deletes anywhere in v1 repositories | Tombstones can propagate later. Every read query filters `deletedAt IS NULL` |
| Updates | Repositories set `updatedAt` on every write | — |

Tombstones are never purged in v1. Settings are **not** an entity (DataStore, §5/§6).

## 3. Entities

### `exercises`
| Column | Type | Notes |
|---|---|---|
| id | TEXT PK | Built-ins ship with **fixed** UUIDs (Appendix A / seed JSON) |
| name | TEXT | Unique among live rows (case-insensitive), enforced in repository |
| muscleGroup | TEXT | Enum: `CHEST BACK SHOULDERS BICEPS TRICEPS QUADS HAMSTRINGS GLUTES CALVES ABS FOREARMS OTHER` |
| equipment | TEXT | Enum: `BARBELL DUMBBELL MACHINE CABLE BODYWEIGHT KETTLEBELL MEDICINE_BALL FOAM_ROLLER BANDS EXERCISE_BALL OTHER` |
| force | TEXT? | Enum: `PUSH PULL STATIC`; `NULL` = unclassified (custom exercises, stretches) |
| secondaryMuscleGroups | TEXT | JSON string array of `muscleGroup` enum names (e.g. `["BACK","BICEPS"]`), `[]` = none; excludes the primary, no duplicates |
| isBuiltIn | INTEGER (bool) | Built-ins: not editable, not deletable — only hidable |
| isHidden | INTEGER (bool) | Hides from pickers; history/analytics unaffected |
| createdAt / updatedAt / deletedAt | INTEGER / INTEGER / INTEGER? | §2 |

### `seed_state`
| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK | Always `1` — single-row table |
| appliedSeedVersion | INTEGER | Seed version last converged into `exercises` (§7). Local derived state: **not** exported/imported; cleared by import so restores re-converge |

### `workout_plans`
| Column | Type | Notes |
|---|---|---|
| id | TEXT PK | |
| name | TEXT | e.g. "PPL" |
| position | INTEGER | Manual ordering on Plans screen |
| createdAt / updatedAt / deletedAt | | §2 |

### `plan_day_templates`
| Column | Type | Notes |
|---|---|---|
| id | TEXT PK | |
| planId | TEXT FK → workout_plans | |
| name | TEXT | e.g. "Push Day" |
| position | INTEGER | Order within plan |
| createdAt / updatedAt / deletedAt | | §2 |

### `template_exercises`
| Column | Type | Notes |
|---|---|---|
| id | TEXT PK | |
| templateId | TEXT FK → plan_day_templates | |
| exerciseId | TEXT FK → exercises | |
| position | INTEGER | Order within template |
| targetSets | INTEGER? | Optional prescription |
| targetRepsMin / targetRepsMax | INTEGER? / INTEGER? | Rep range; equal values = fixed reps |
| createdAt / updatedAt / deletedAt | | §2 |

### `sessions`
| Column | Type | Notes |
|---|---|---|
| id | TEXT PK | |
| templateId | TEXT? FK → plan_day_templates | Provenance only — content was snapshotted |
| templateNameSnapshot | TEXT? | Display name survives template rename/delete |
| startedAt | INTEGER | |
| endedAt | INTEGER? | **`NULL` = session in progress** — the resumability anchor. At most one live `NULL` row, enforced in repository |
| note | TEXT? | Workout-level note |
| rpe | REAL? | Workout-level RPE, 6.0–10.0 in 0.5 steps; `null` = not rated (since schema v2) |
| createdAt / updatedAt / deletedAt | | §2 |

### `session_exercises`
| Column | Type | Notes |
|---|---|---|
| id | TEXT PK | |
| sessionId | TEXT FK → sessions | |
| exerciseId | TEXT FK → exercises | |
| position | INTEGER | Order within session |
| targetSets / targetRepsMin / targetRepsMax | INTEGER? ×3 | **Snapshot** from template at session start; NULL for ad-hoc |
| createdAt / updatedAt / deletedAt | | §2 |

### `logged_sets`
| Column | Type | Notes |
|---|---|---|
| id | TEXT PK | |
| sessionExerciseId | TEXT FK → session_exercises | |
| weightKg | REAL | **Canonical kg** (§5). For BODYWEIGHT exercises: added external weight, `0.0` = unweighted. `>= 0` enforced |
| reps | INTEGER | `>= 1` |
| position | INTEGER | Set number within the exercise (1-based) |
| completedAt | INTEGER | When logged — written **immediately** on the LOG tap (process-death safety) |
| createdAt / updatedAt / deletedAt | | §2 |

> **Schema v1 → v2 note (2026-06-11):** per-set `rpe` and `note` columns existed in schema v1
> and were removed in `MIGRATION_1_2` (`data/db/Migrations.kt`). RPE and notes are
> session-level as of schema v2; old per-set values were discarded by design.

### Indexes

Targeting the two hot paths — *"this exercise's history"* (analytics + pre-fill) and *"sessions by date"*:

```
template_exercises:  (templateId), (exerciseId)
sessions:            (startedAt), (templateId)
session_exercises:   (sessionId), (exerciseId)
logged_sets:         (sessionExerciseId)
```

FKs declared with `onDelete = NO_ACTION` — deletes are soft, cascades are the repository's job (soft-deleting a session soft-deletes its children, etc.).

## 4. Room sketch (signatures, not implementations)

```kotlin
@Entity(tableName = "logged_sets",
        foreignKeys = [ForeignKey(SessionExerciseEntity::class, ["id"], ["sessionExerciseId"])],
        indices = [Index("sessionExerciseId")])
data class LoggedSetEntity(
    @PrimaryKey val id: String,
    val sessionExerciseId: String,
    val weightKg: Double,
    val reps: Int,
    val position: Int,
    val completedAt: Long,
    val createdAt: Long, val updatedAt: Long, val deletedAt: Long?,
)
// …remaining entities follow §3 verbatim
```

```kotlin
@Dao interface SessionDao {
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL AND deletedAt IS NULL LIMIT 1")
    fun observeActiveSession(): Flow<SessionEntity?>

    @Transaction @Query("SELECT * FROM sessions WHERE id = :id AND deletedAt IS NULL")
    fun observeSessionWithDetails(id: String): Flow<SessionWithDetails?>   // @Relation graph

    @Query("SELECT * FROM sessions WHERE deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeHistory(): Flow<List<SessionEntity>>

    @Insert suspend fun insert(session: SessionEntity)
    @Update suspend fun update(session: SessionEntity)
}

@Dao interface AnalyticsDao {
    // Deliberately set-level: e1RM math stays in pure Kotlin (04-analytics-spec §2).
    @Query("""
        SELECT s.id AS sessionId, s.startedAt, ls.weightKg, ls.reps
        FROM logged_sets ls
        JOIN session_exercises se ON se.id = ls.sessionExerciseId AND se.deletedAt IS NULL
        JOIN sessions s          ON s.id = se.sessionId          AND s.deletedAt IS NULL
        WHERE se.exerciseId = :exerciseId AND ls.deletedAt IS NULL
          AND s.startedAt >= :fromMillis AND s.endedAt IS NOT NULL
        ORDER BY s.startedAt
    """)
    fun observeSetsForExercise(exerciseId: String, fromMillis: Long): Flow<List<SetRow>>
}

@Dao interface PrefillDao {
    // Last completed session containing this exercise, with its sets (pre-fill source,
    // 03-ux-spec §4). Implemented as @Transaction over two indexed lookups.
    fun observeLastPerformance(exerciseId: String): Flow<List<LoggedSetEntity>>
}
```

Repository interfaces (in `domain/`, consumed by ViewModels — [01-architecture](01-architecture.md) §1):
`ExerciseRepository`, `PlanRepository`, `SessionRepository`, `AnalyticsRepository`,
`SettingsRepository`, `BackupRepository`.

## 5. Units strategy

- **Storage is always kg** (`weightKg: Double`). No unit column, no per-row ambiguity, analytics never converts.
- **Display/entry unit** is a single global setting in DataStore: `KG` (default) or `LB`. Conversion happens at the UI boundary only: factor `1 lb = 0.45359237 kg` (exact), display rounded to 2 decimals max (trailing zeros stripped: "82.5", not "82.50").
- Entry in lb is converted to kg on save — the kg value is canonical even if it's "ugly" (60 lb → 27.2155 kg). Round-tripping back to lb re-displays cleanly because display rounding is stable.
- Stepper increment: **2.5 kg** in kg mode, **5 lb** in lb mode ([03-ux-spec](03-ux-spec.md) §4).
- Changing the unit setting re-renders everything; stored data is untouched.

## 6. Export / import format

One JSON file, UTF-8, default name `liftlog-backup-YYYY-MM-DD.json`, written via the system file picker (SAF — no storage permission). **Why JSON:** human-readable (HANDOFF §3 requirement), diffable, schema-evolvable, and kotlinx.serialization gives compile-time-safe codecs. A SQLite file copy was rejected: opaque, ties the backup to Room schema versions, hostile to future import-into-anything.

```json
{
  "formatVersion": 3,
  "exportedAt": "2026-06-11T10:00:00Z",
  "app": { "name": "LiftLog", "versionName": "1.0.0", "dbSchemaVersion": 3 },
  "settings": { "weightUnit": "KG", "theme": "SYSTEM" },
  "data": {
    "exercises": [
      { "id": "6f1c9e9a-…", "name": "Barbell Bench Press", "muscleGroup": "CHEST",
        "equipment": "BARBELL", "force": "PUSH", "secondaryMuscleGroups": ["TRICEPS","SHOULDERS"],
        "isBuiltIn": true, "isHidden": false,
        "createdAt": "2026-06-01T09:00:00Z", "updatedAt": "2026-06-01T09:00:00Z",
        "deletedAt": null }
    ],
    "workoutPlans":      [ { "…": "…" } ],
    "planDayTemplates":  [ { "…": "…" } ],
    "templateExercises": [ { "…": "…" } ],
    "sessions": [
      { "id": "a3f1…", "templateId": null, "templateNameSnapshot": null,
        "startedAt": "2026-06-11T09:00:00Z", "endedAt": "2026-06-11T10:00:00Z",
        "note": "Felt strong today", "rpe": 8.0,
        "createdAt": "2026-06-11T09:00:00Z", "updatedAt": "2026-06-11T10:00:00Z",
        "deletedAt": null }
    ],
    "sessionExercises":  [ { "…": "…" } ],
    "loggedSets": [
      { "id": "0b2d…", "sessionExerciseId": "9ac1…", "weightKg": 82.5, "reps": 5,
        "position": 1, "completedAt": "2026-06-11T09:31:05Z",
        "createdAt": "2026-06-11T09:31:05Z", "updatedAt": "2026-06-11T09:31:05Z",
        "deletedAt": null }
    ]
  }
}
```

Rules:

- **Full fidelity**: includes tombstones (`deletedAt != null`) and hidden exercises — an import restores the exact state.
- Timestamps are ISO-8601 UTC strings in the file (human-readable), epoch millis in the DB.
- **Format versioning**: `formatVersion` bumps only on breaking changes. Current version: **3** (since 2026-07-09, issue #37; adds exercise `force` + `secondaryMuscleGroups`). The importer accepts `formatVersion <= current`; a `Newer(version)` check refuses files from a newer app with a clear message ("backup was created by a newer app version").
- **v1/v2 → v3 import compat**: `ExerciseDto.force` (string enum name or `null`) and `ExerciseDto.secondaryMuscleGroups` (string array) default to `null`/`[]` when absent, so older files import cleanly. Unlike `muscleGroup`/`equipment` (identity fields, strict `UNKNOWN_ENUM` rejection), the two new classification fields import **leniently**: unknown `force` → `null`, unknown secondary names → `OTHER`.
- **v1 → v2 import compat**: v1 files (`formatVersion: 1`) import cleanly — the codec sets `ignoreUnknownKeys = true`, so stale per-set `rpe`/`note` fields on `loggedSets` entries are silently dropped. Imported sessions receive `rpe = null` (the `SessionDto.rpe` field defaults to `null`). This drops any per-set RPE/note data by design; the user is not warned (the v2 UI no longer surfaces per-set RPE/note; old values are discarded by MIGRATION_1_2 by design).
- **Import = full replace**, single Room transaction: validate file → show summary ("Replace current data with backup from 11 Jun 2026? 412 sessions, 38 exercises…") → explicit confirm → wipe + insert. No merge in v1 (merge ≈ sync engine, deliberately out of scope). An in-progress session blocks import.
- Validation failures (malformed JSON, missing required fields, FK orphans) reject the **whole file** before any write.

## 7. Built-in exercise seeding

- `assets/seed/exercises.v<N>.json` asset (N = `ExerciseSeeder.SEED_VERSION`, locked by SeedAssetTest): built-in exercises, each with a **hardcoded fixed UUID**, name, muscleGroup, equipment, optional force, optional secondaryMuscleGroups.
- **Version-gated convergence** (since v3 / issue #37): the seeder reads the single `seed_state` row and returns immediately — without opening the asset — when `appliedSeedVersion >= SEED_VERSION`. Otherwise (fresh install, first launch after the v3 migration, or a seed bump) it converges in one transaction: inserts missing ids; updates changed classification fields (`name, muscleGroup, equipment, force, secondaryMuscleGroups`) on live built-in rows, preserving `isHidden`/`createdAt` and bumping `updatedAt` only on real change; then stamps `appliedSeedVersion = SEED_VERSION`. A crash mid-seed re-runs the idempotent converge next launch.
- Convergence never removes or resurrects: rows absent from the seed file are left untouched (user history may hang off them), tombstoned rows stay tombstoned. App downgrades (stored version > constant) skip seeding.
- Import clears `seed_state` inside the restore transaction and re-runs the seeder right after, so restoring an old backup cannot leave stale or missing built-ins.
- Built-in UUIDs are stable across all installs → exports from any device reference the same built-in IDs, keeping future sync/merge sane.

## Appendix A — Built-in exercise library (v1, ~70)

Authored list; final UUIDs assigned in the seed asset at M1.

| Muscle group | Exercises (equipment) |
|---|---|
| CHEST | Barbell Bench Press (BB) · Incline Barbell Bench Press (BB) · Dumbbell Bench Press (DB) · Incline Dumbbell Press (DB) · Machine Chest Press (M) · Pec Deck (M) · Cable Fly (C) · Push-up (BW) · Dips (BW) |
| BACK | Deadlift (BB) · Barbell Row (BB) · T-Bar Row (BB) · Pull-up (BW) · Chin-up (BW) · Lat Pulldown (C) · Seated Cable Row (C) · Straight-Arm Pulldown (C) · One-Arm Dumbbell Row (DB) · Machine Row (M) · Back Extension (BW) |
| SHOULDERS | Overhead Press (BB) · Seated Dumbbell Shoulder Press (DB) · Machine Shoulder Press (M) · Lateral Raise (DB) · Cable Lateral Raise (C) · Rear Delt Fly (M) · Face Pull (C) · Front Raise (DB) · Upright Row (BB) |
| BICEPS | Barbell Curl (BB) · EZ-Bar Curl (BB) · Dumbbell Curl (DB) · Hammer Curl (DB) · Incline Dumbbell Curl (DB) · Cable Curl (C) · Preacher Curl Machine (M) |
| TRICEPS | Close-Grip Bench Press (BB) · Skull Crusher (BB) · Triceps Pushdown (C) · Overhead Cable Extension (C) · Dumbbell Overhead Extension (DB) · Machine Triceps Extension (M) |
| QUADS | Back Squat (BB) · Front Squat (BB) · Goblet Squat (DB) · Leg Press (M) · Hack Squat (M) · Bulgarian Split Squat (DB) · Walking Lunge (DB) · Leg Extension (M) |
| HAMSTRINGS | Romanian Deadlift (BB) · Stiff-Leg Deadlift (BB) · Lying Leg Curl (M) · Seated Leg Curl (M) · Good Morning (BB) |
| GLUTES | Hip Thrust (BB) · Cable Glute Kickback (C) · Hip Abduction Machine (M) |
| CALVES | Standing Calf Raise (M) · Seated Calf Raise (M) · Calf Press on Leg Press (M) |
| ABS | Sit-up (BW) · Crunch (BW) · Cable Crunch (C) · Hanging Leg Raise (BW) · Ab Wheel Rollout (BW) |
| FOREARMS | Barbell Wrist Curl (BB) · Reverse Barbell Curl (BB) |
| OTHER | Kettlebell Swing (DB) |

(BB = barbell, DB = dumbbell, M = machine, C = cable, BW = bodyweight)
