# M5 (PR1) — Export / Import — Design

> **Status:** Approved for planning · 2026-06-09
> First of three focused PRs for milestone **M5 — Export/import + polish**
> ([docs/05-roadmap.md](../../05-roadmap.md)). PR2 = settings + a11y polish; PR3 = release
> readiness (app name, icon, R8, signing). This spec covers **export/import only**.

## Goal

Lossless, human-readable, versioned **JSON backup**: export the entire database (plus the
two persisted settings) to a user-chosen file via the system file picker, and import it
back as a **full replace**. This is hard-constraint #4 (no data loss) made real, and the
M5 exit criterion *"export → wipe → import round-trip is lossless (automated test);
importer rejects newer-version and corrupt files gracefully."*

The wire format, the full-replace-no-merge decision, SAF usage, and validation-rejects-the-
whole-file are already fixed by **[02-data-spec §6](../../02-data-spec.md)**; this spec turns
that contract into a layered, testable implementation.

## Scope

**In:** `BackupRepository` + impl; pure `BackupCodec` (encode/decode/validate); `@Serializable`
DTOs; `BackupDao` (snapshot + atomic replace); SAF export/import wired into the existing
Settings screen with a confirmation dialog and error dialogs; golden-file, validation,
round-trip, live-session-block, and ViewModel tests.

**Out (later M5 PRs / post-v1):** weight-unit toggle + `setWeightUnit`, licenses/about (PR2);
accessibility audit (PR2); R8/minify, signing, app icon, final app-name (PR3); any "Share"
affordance; any merge/partial/selective import (merge ≈ a sync engine — deliberately out of
scope per data-spec §6).

**Zero new dependencies.** kotlinx.serialization is already on the classpath (type-safe nav
routes use it), Room DAOs are already `suspend` (room-ktx present), and SAF uses
`androidx.activity` result contracts (already present). Satisfies constraint #5.

## Architecture

Approach **A — pure codec + String-based repository**. The serialization/validation logic is
a pure, Android-free unit (`BackupCodec`) operating on a `String` ⇄ `BackupSnapshot` boundary,
so the no-data-loss tests sit on a clean seam. The repository handles the DB snapshot and the
replace transaction; the ViewModel owns the SAF `Uri` ⇄ `String` I/O via a thin
`ContentResolver` wrapper, keeping the **domain interface Android-free** (ViewModels depend
only on `domain/`, per [01-architecture §1](../../01-architecture.md)).

Backups are small (a year of real data is well under a few MB), so holding the whole JSON in a
`String` in memory is acceptable and buys the cleanest testable boundary.

### File structure

```
data/backup/BackupModels.kt        @Serializable DTOs mirroring data-spec §6 (timestamps = ISO-8601 strings)
data/backup/BackupSnapshot.kt      plain holder: the 7 entity lists + settings (weightUnit, theme)
data/backup/BackupCodec.kt         PURE object: encode(snapshot,…)→String; decode(json)→ParseOutcome;
                                   ISO-8601 ↔ epoch-millis; all validation. No Android imports.
data/dao/BackupDao.kt              getAll×7 (incl. tombstones), getActiveSession(), @Transaction replaceAll(...)
domain/repository/BackupRepository.kt   interface (Android-free)
data/repository/BackupRepositoryImpl.kt bound in di/RepositoryModule.kt
di/DatabaseModule.kt               provide BackupDao + AppInfo(versionName, dbSchemaVersion)
ui/settings/DocumentIo.kt          ContentResolver wrapper: readText(uri):String / writeText(uri,text)
ui/settings/SettingsViewModel.kt   export/import orchestration + dialog state
ui/settings/SettingsScreen.kt      Export/Import rows, SAF launchers, confirm dialog, error dialog
```

`BackupDao` is a chunky DAO, but its single responsibility is backup snapshot/replace — it is
the only place that reads rows **ignoring** the `deletedAt IS NULL` filter and the only place
that hard-deletes. Keeping that power in one named DAO is deliberate.

## Components

### 1. DTOs — `data/backup/BackupModels.kt`

`@Serializable` classes mirroring data-spec §6 exactly. Timestamps are **ISO-8601 UTC strings**
in the file (`createdAt`/`updatedAt`/`completedAt`/`startedAt`/`endedAt`/`deletedAt`/`exportedAt`),
epoch-millis in the DB — the codec converts. Sketch:

```kotlin
@Serializable data class BackupFile(
    val formatVersion: Int,
    val exportedAt: String,
    val app: AppInfoDto,
    val settings: SettingsDto,
    val data: BackupData,
)
@Serializable data class AppInfoDto(val name: String, val versionName: String, val dbSchemaVersion: Int)
@Serializable data class SettingsDto(val weightUnit: String, val theme: String)
@Serializable data class BackupData(
    val exercises: List<ExerciseDto>,
    val workoutPlans: List<WorkoutPlanDto>,
    val planDayTemplates: List<PlanDayTemplateDto>,
    val templateExercises: List<TemplateExerciseDto>,
    val sessions: List<SessionDto>,
    val sessionExercises: List<SessionExerciseDto>,
    val loggedSets: List<LoggedSetDto>,
)
// per-entity DTOs mirror the entity columns 1:1, with Long timestamps rendered as String.
// nullable columns (deletedAt, endedAt, note, rpe, targets, templateId, templateNameSnapshot) are nullable DTO fields.
```

The `data.*` key order and the field order within each DTO are part of the golden file and
must stay stable.

### 2. Snapshot holder — `data/backup/BackupSnapshot.kt`

```kotlin
data class BackupSnapshot(
    val exercises: List<ExerciseEntity>,
    val workoutPlans: List<WorkoutPlanEntity>,
    val planDayTemplates: List<PlanDayTemplateEntity>,
    val templateExercises: List<TemplateExerciseEntity>,
    val sessions: List<SessionEntity>,
    val sessionExercises: List<SessionExerciseEntity>,
    val loggedSets: List<LoggedSetEntity>,
    val weightUnit: WeightUnit,
    val theme: ThemePreference,
)
```

Holds **entities** (not domain models) so the round-trip is byte-for-byte lossless and the
repository does no model mapping.

### 3. Codec — `data/backup/BackupCodec.kt` (PURE — the testable seam)

```kotlin
object BackupCodec {
    const val CURRENT_FORMAT_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun encode(snapshot: BackupSnapshot, exportedAt: Instant, app: AppInfo): String
    fun decode(text: String): ParseOutcome
}

sealed interface ParseOutcome {
    data class Ok(val snapshot: BackupSnapshot, val summary: ImportSummary) : ParseOutcome
    data class Newer(val fileVersion: Int) : ParseOutcome
    data class Invalid(val reason: InvalidReason) : ParseOutcome
}
enum class InvalidReason { MALFORMED, MISSING_FIELDS, BAD_TIMESTAMP, FK_ORPHAN, UNKNOWN_ENUM }
```

- `encode`: snapshot → `BackupFile` (millis→ISO strings, enums→`name`) → `json.encodeToString`.
- `decode`: `json.decodeFromString<BackupFile>` then validate. `ignoreUnknownKeys = true` gives
  the forward-compat rule (importers ignore unknown fields). `encodeDefaults = true` so the
  shape is explicit in the golden file.
- **Live-session check is NOT here** — it is a current-DB concern, done in the repository.

`AppInfo` is a tiny data class (`name`, `versionName`, `dbSchemaVersion`) provided by DI so the
codec stays free of `BuildConfig`.

### 4. Validation (inside `decode`)

Reject the **whole file** — return before producing any `Ok`:

| Condition | Outcome |
|---|---|
| JSON won't parse / wrong shape / missing required (non-null) field | `Invalid(MALFORMED)` or `Invalid(MISSING_FIELDS)` |
| `formatVersion > CURRENT_FORMAT_VERSION` | `Newer(fileVersion)` |
| Any timestamp string not ISO-8601 parseable | `Invalid(BAD_TIMESTAMP)` |
| FK orphan: a child references an id absent from the file | `Invalid(FK_ORPHAN)` |
| Unknown **entity** enum (`muscleGroup`, `equipment`) | `Invalid(UNKNOWN_ENUM)` |

FK checks (all ids resolved within the file — tombstones count as present rows):
`planDayTemplates.planId ∈ workoutPlans`; `templateExercises.templateId ∈ planDayTemplates` &
`.exerciseId ∈ exercises`; `sessions.templateId` (when non-null) `∈ planDayTemplates`;
`sessionExercises.sessionId ∈ sessions` & `.exerciseId ∈ exercises`;
`loggedSets.sessionExerciseId ∈ sessionExercises`.

**Settings enums are lenient:** unknown `weightUnit`/`theme` fall back to defaults via the
existing `WeightUnit.fromStorageValue` / `ThemePreference.fromStorageValue` — settings are
cosmetic and must never block a data restore.

### 5. DAO — `data/dao/BackupDao.kt`

```kotlin
@Dao interface BackupDao {
    @Query("SELECT * FROM exercises")        suspend fun getAllExercises(): List<ExerciseEntity>
    // …getAll for the other 6 tables (NO deletedAt filter — full fidelity)…
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL AND deletedAt IS NULL LIMIT 1")
    suspend fun getActiveSession(): SessionEntity?

    @Transaction
    suspend fun replaceAll(s: BackupSnapshot) {
        deleteAllLoggedSets(); deleteAllSessionExercises(); deleteAllSessions()
        deleteAllTemplateExercises(); deleteAllPlanDayTemplates(); deleteAllWorkoutPlans()
        deleteAllExercises()
        insertExercises(s.exercises); insertWorkoutPlans(s.workoutPlans)
        insertPlanDayTemplates(s.planDayTemplates); insertTemplateExercises(s.templateExercises)
        insertSessions(s.sessions); insertSessionExercises(s.sessionExercises)
        insertLoggedSets(s.loggedSets)
    }
    // @Query("DELETE FROM <t>") deleteAll*(); @Insert insert*(rows) for all 7 tables
}
```

`@Transaction` makes the wipe+insert atomic. Delete order is child→parent, insert order is
parent→child (FKs declared `NO_ACTION`, but ordering keeps it clean and migration-safe).
Settings (DataStore) are written by the repository immediately after the transaction — they are
not Room rows.

### 6. Repository — `domain/repository/BackupRepository.kt` + impl

```kotlin
interface BackupRepository {
    suspend fun exportToJson(): String                  // snapshot (incl. tombstones) → codec.encode
    suspend fun parseImport(json: String): ParseResult  // codec.decode + live-session check — NO writes
    suspend fun applyImport(snapshot: BackupSnapshot): ImportSummary  // replaceAll + settings write
}
sealed interface ParseResult {
    data class Ready(val snapshot: BackupSnapshot, val summary: ImportSummary) : ParseResult
    data object BlockedByLiveSession : ParseResult
    data class Newer(val fileVersion: Int) : ParseResult
    data class Invalid(val reason: InvalidReason) : ParseResult
}
data class ImportSummary(val exportedAt: Instant, val sessions: Int, val exercises: Int, val sets: Int)
```

`ParseResult` is the domain-facing mirror of the codec's `ParseOutcome`, plus the
`BlockedByLiveSession` state the repository adds:

- `exportToJson` (on `Dispatchers.IO`): gather all rows via `BackupDao.getAll*` + read current
  `weightUnit`/`theme` from `SettingsRepository` → `BackupSnapshot` → `codec.encode(snapshot,
  clock.instant(), appInfo)`.
- `parseImport`: `codec.decode(json)` → map `Newer`/`Invalid` straight through; on `Ok`, if
  `BackupDao.getActiveSession() != null` return `BlockedByLiveSession`, else `Ready`.
- `applyImport`: `dao.replaceAll(snapshot)` then write `snapshot.weightUnit`/`snapshot.theme`
  to DataStore (needs `SettingsRepository.setWeightUnit`/`setThemePreference`; **`setWeightUnit`
  is added here** — its toggle UI still waits for PR2). Returns the summary.

**Two-step import (parse → confirm → apply)** is what lets the confirmation dialog show the
summary while honoring "validate the whole file before any write."

`Clock` is already provided (`Clock.systemUTC()` in `DatabaseModule`) and injected for
`exportedAt`. `AppInfo` is provided from `BuildConfig` (`name = "LiftLog"`,
`versionName = BuildConfig.VERSION_NAME`, `dbSchemaVersion = 1`).

### 7. SAF I/O — `ui/settings/DocumentIo.kt`

```kotlin
class DocumentIo @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun writeText(uri: Uri, text: String)   // contentResolver.openOutputStream(uri).use { ... }
    suspend fun readText(uri: Uri): String          // contentResolver.openInputStream(uri).use { ... }
}
```

Keeps `Uri`/`ContentResolver` out of the repository and out of `domain/`. I/O on
`Dispatchers.IO`.

### 8. Settings UI — `ui/settings/SettingsScreen.kt` + `SettingsViewModel.kt`

Add a **Data** section under the existing Theme section with two ≥48dp rows (a11y §7):
**Export data** and **Import data**, styled like the existing list rows.

- **Export**: `rememberLauncherForActivityResult(CreateDocument("application/json"))`, launched
  with default name `liftlog-backup-YYYY-MM-DD.json` (date from injected `Clock`). On `Uri`:
  VM `export()` → `json = repo.exportToJson()` → `documentIo.writeText(uri, json)` → snackbar
  *"Backup exported"*. Null Uri (user cancelled) = no-op. Write failure → snackbar
  *"Export failed"*.
- **Import**: `rememberLauncherForActivityResult(OpenDocument())` with `arrayOf("application/json")`.
  On `Uri`: VM `prepareImport(uri)` → `documentIo.readText` → `repo.parseImport`. Branch:
  - `Ready(summary)` → **confirm dialog**: *"Replace all current data with the backup from
    {date}? {n} sessions · {m} exercises. This can't be undone."* Confirm → `repo.applyImport`
    → snackbar *"Backup imported"* + **pop to Home**. Dismiss → nothing happened.
  - `BlockedByLiveSession` → **error dialog**: *"Finish or discard your active session before
    importing."*
  - `Newer(v)` → **error dialog**: *"This backup was created by a newer version of LiftLog."*
  - `Invalid(reason)` → **error dialog**: reason-specific copy (corrupt / incomplete file).

VM state additions:

```kotlin
data class SettingsUiState(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val pendingImport: ImportSummary? = null,   // non-null → show confirm dialog
    val dialog: SettingsDialog? = null,         // error dialog (sealed: Blocked/Newer/Invalid)
    val message: SettingsMessage? = null,       // one-shot snackbar (exported / imported / export-failed)
)
```

The confirm dialog is the **only** dialog allowed (destructive action), consistent with the
session-discard dialog (UX spec §4.4). `pop to Home` uses the existing nav callback wiring; the
`SettingsScreen` signature gains an `onImported: () -> Unit` (or reuses `onBack` semantics —
decided in the plan against the actual nav graph). Reactive flows re-emit after the replace, so
**no app restart** is forced.

## Data flow

```
EXPORT  row tap → CreateDocument(name) → uri
        → VM.export() → repo.exportToJson() (IO: DAO snapshot + settings → codec.encode)
        → documentIo.writeText(uri, json) → snackbar

IMPORT  row tap → OpenDocument(["application/json"]) → uri
        → VM.prepareImport(uri) → documentIo.readText(uri) → repo.parseImport(json)
            Ready    → confirm dialog → (confirm) → repo.applyImport(snapshot)
                       → DAO.replaceAll (atomic wipe+insert) + settings write → snackbar + pop Home
            Blocked  → error dialog (live session)
            Newer    → error dialog (newer version)
            Invalid  → error dialog (corrupt/incomplete)
```

## Error handling

- **Whole-file-or-nothing**: every rejection path returns before `applyImport`; the DB is never
  partially written. `replaceAll` is a single `@Transaction` (all-or-nothing even on insert
  failure).
- **Live session blocks import** (data-spec §6): `parseImport` checks `getActiveSession()`.
  Export is always allowed (it snapshots the in-progress session faithfully; `endedAt = null`
  round-trips).
- **SAF cancellation**: launcher returns null Uri → silent no-op (not an error).
- **I/O failure** (unreadable/unwritable stream): caught in the VM → export → *"Export failed"*
  snackbar; import read failure → `Invalid(MALFORMED)` error dialog.

## Testing

| Test | Type | Asserts |
|---|---|---|
| `BackupCodecTest` golden encode | JVM unit | fixed `BackupSnapshot` + fixed `exportedAt`/`AppInfo` → `encode` **equals** committed `golden-backup.json` test resource |
| `BackupCodecTest` golden decode | JVM unit | `decode(golden-backup.json)` → `Ok` whose snapshot **equals** the fixture (round-trips through the pure boundary) |
| `BackupCodecTest` validation | JVM unit | malformed / missing-field / newer-version / bad-timestamp / FK-orphan / unknown-entity-enum each → the right `ParseOutcome`; unknown **settings** enum falls back, still `Ok` |
| `BackupRoundTripTest` | instrumented | seed DB incl. a **tombstone** + **hidden exercise** → `exportToJson` → `replaceAll(empty)` (wipe) → `parseImport`+`applyImport` → every table equals the original row-for-row (**M5 exit criterion**) |
| `BackupRoundTripTest` live-session block | instrumented | with an `endedAt IS NULL` session present, `parseImport(validFile)` → `BlockedByLiveSession`; DB untouched |
| `SettingsViewModelTest` | JVM unit (fakes) | export happy path emits snackbar; import `Ready` shows confirm then apply pops Home; each error `ParseResult` surfaces its dialog; cancel does nothing |

Golden file lives at `app/src/test/resources/backup/golden-backup.json`, hand-checked once
against data-spec §6, then locked. The fixture uses fixed UUIDs and timestamps so the golden
encode is deterministic.

## Decisions / notes for the plan

- **`setWeightUnit` is added to `SettingsRepository`** in this PR (import needs to restore the
  unit); the kg/lb **toggle UI** stays deferred to PR2. This resolves data-spec's "flagged
  decision #1" on the persistence side only.
- The exported file **includes built-in exercises** by their fixed UUIDs; re-importing wipes and
  re-inserts them, and the first-launch seeder (insert-if-id-absent) stays idempotent afterward.
- `dbSchemaVersion` in the header is informational in v1 (single schema version); it is recorded
  but not used as an import gate beyond `formatVersion`.
- Plan must confirm the exact `SettingsScreen`/nav-callback wiring for "pop to Home" against
  `LiftLogNavHost.kt`.
```
