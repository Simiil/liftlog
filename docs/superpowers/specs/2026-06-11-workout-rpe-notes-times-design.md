# Workout-level RPE & notes, editable workout times — design

**Date:** 2026-06-11
**Status:** approved by owner (brainstorming session 2026-06-11)

## Motivation

RPE and notes currently live on every logged set, entered through the expanded
set editor. In practice they describe the *workout*, not a single set: the
per-set chip strip (6–10 in 0.5 steps) is fiddly, unexplained, and clutters the
core logging path. Separately, sessions sometimes get logged after the fact in
one sitting, so the automatic `startedAt`/`endedAt` timestamps are wrong and
there is no way to correct them.

## Decisions (from brainstorming)

| Question | Decision |
|---|---|
| Per-set RPE & note | Removed entirely — set editor becomes weight + reps + Delete/Save |
| Existing on-device data | Throwaway; no rollup needed (migration still preserves weight/reps/timestamps) |
| RPE scale | Keep 6–10 in 0.5 steps, but the UI must explain values |
| RPE input control | Stepper (− value +) with live descriptor — matches the weight/reps idiom |
| Placement during a session | Slim collapsed row at the *bottom* of the Active Session list, after "Add exercise" |
| Time editing | Session Detail screen only (covers retroactive entry); no finish sheet, no in-session timer editing |

## 1. Data layer

### Entities & domain models

- `SessionEntity` / `Session` / `SessionDto`: add `rpe: Double?` (null = not
  rated). The session-level `note: String?` already exists and is reused — it
  was never wired to any UI before.
- `LoggedSetEntity` / `LoggedSet` / `LoggedSetDto`: remove `rpe` and `note`.
- `Mappers.kt` updated for both.

### DB migration (schema v1 → v2)

`DB_SCHEMA_VERSION` becomes 2 with a hand-written `Migration(1, 2)`:

1. `ALTER TABLE sessions ADD COLUMN rpe REAL` (nullable).
2. Recreate `logged_sets` without `rpe`/`note`: create new table, copy all
   remaining columns, drop old table, rename. Indices recreated to match the
   Room-expected schema.

No destructive fallback. Old per-set RPE/note values are discarded by design.

### Repository API (`SessionRepository`)

- `updateSet(setId, weightKg, reps)` — loses the `rpe`/`note` parameters.
- New `updateSessionRpe(sessionId, rpe: Double?)` — auto-save path for the
  slim row; called on every stepper change.
- New `updateSessionNote(sessionId, note: String?)` — auto-save path for the
  slim row; called on collapse/focus loss. Blank notes are trimmed to null.
- New `updateSessionDetails(sessionId, startedAt: Instant, endedAt: Instant, rpe: Double?, note: String?)`
  — single atomic write used by the detail edit sheet.
- Every update bumps `updatedAt` (sync-readiness rule). `finishSession` is
  unchanged.

### Backup format (v1 → v2)

- `CURRENT_FORMAT_VERSION` becomes 2.
- `SessionDto` gains `rpe`; `LoggedSetDto` loses `rpe`/`note`.
- Importing v1 files keeps working as-is: the codec already sets
  `ignoreUnknownKeys = true`, so stale per-set keys are silently dropped and
  imported sessions get `rpe = null`. Importing a v2 file into an older app
  already fails cleanly via the existing `Newer(version)` check.

### Analytics

Untouched. `AnalyticsDao` and `ExerciseAnalytics` never read RPE or notes.

## 2. UI

### `RpeStepper` (new, `ui/components`)

Reusable composable: − / + round buttons flanking a large value, one
descriptor line underneath. Range 6.0–10.0, step 0.5.

- **Unset state:** value shows "—", descriptor shows a hint ("Tap + to rate
  the workout"). First tap on either button starts at 8.
- **Clear:** a small "Clear" affordance is visible whenever a value is set and
  resets to null — RPE stays optional everywhere.
- **Descriptors:** two string resources per whole value, a short label
  (`rpe_short_*`) and a detail phrase (`rpe_detail_*`). Whole values render
  "Label — detail"; half values render a composed "Between {floor} and
  {ceil}" using the short labels.

| RPE | EN short | EN detail | DE short | DE detail |
|---|---|---|---|---|
| 6 | Easy | plenty left in the tank | Leicht | viel Reserve übrig |
| 7 | Moderate | challenging but far from the limit | Moderat | fordernd, aber weit vom Limit |
| 8 | Hard | tough, with some reserve left | Hart | anstrengend, mit Reserven |
| 9 | Very hard | close to the limit | Sehr hart | nah am Limit |
| 10 | Max effort | nothing left | Maximal | nichts mehr übrig |

German strings are first-pass drafts, flagged for the owner's native-speaker
review like the rest of M6.

### Active Session: slim meta row (`SessionMetaRow`)

Last item in the `LazyColumn`, after `AddExerciseRow`.

- **Collapsed, nothing set:** quiet dashed/tonal row reading "＋ Note · RPE".
- **Collapsed, values set:** compact one-line summary showing whichever
  values exist, joined with "·" — "RPE 8 · first words of the note…",
  "RPE 8", or just the note preview.
- **Expanded (tap):** note `OutlinedTextField` (multi-line, ~3 lines max) +
  `RpeStepper` + collapse affordance. No Save button: RPE persists on every
  change via `updateSessionRpe`, the note on collapse/focus loss via
  `updateSessionNote`.
- Values live on the session row and are re-observed on restore, so process
  death loses nothing.

### Set editor slim-down (`LoggedSetRow`)

Shared by Active Session and Session Detail:

- Expanded editor: weight stepper + reps stepper + Delete/Save only. RPE chip
  strip and note field removed.
- Collapsed pill: RPE badge and note dot removed.

### Session Detail

- `SummaryStrip` gains **RPE as a fourth stat** (duration · sets · volume ·
  RPE), showing "—" when unset.
- The workout note renders as a text block below the summary strip, only when
  present.
- `TopAppBar` gains a **pencil (Edit) action** opening an "Edit workout"
  `ModalBottomSheet`:
  - tappable start and end fields, each launching Material 3 date picker +
    time picker dialogs (local timezone, any date allowed);
  - `RpeStepper`;
  - note field;
  - Cancel / Save. **Validation:** end must be strictly after start — inline
    error text and disabled Save otherwise.
  - Save calls `updateSessionDetails`; duration, date strip, and History
    ordering recompute reactively.

### History

Unchanged in this refactor.

### Strings / i18n

Every new string lands in `values/` and `values-de/` in the same PR (M6 lint
gate enforces no hardcoded strings).

## 3. Edge cases

- **End ≤ start** in the edit sheet: Save disabled, inline error.
- **Cross-date edits:** allowed; times are picked in local timezone, stored as
  epoch millis like all existing timestamps.
- **History re-sort:** editing `startedAt` re-sorts the list automatically
  (ordered by `startedAt`, reactive flow).
- **RPE Clear** persists null; **notes** trim whitespace, empty → null.
- **Discard session:** slim-row values soft-delete with the session, as today.
- **v1 import:** per-set RPE/notes silently dropped; `sessions.rpe` null.
- **Process death:** slim-row values persisted on change; transient edit-sheet
  state is lost, which is acceptable (short-lived sheet).

## 4. Testing

- **Migration (instrumented):** `MigrationTestHelper` against the exported v1
  schema JSON — populate `logged_sets` with rpe/note, migrate, assert the
  columns are gone, weight/reps/positions/timestamps intact, `sessions.rpe`
  null.
- **BackupCodec (JVM):** v2 round-trip including session `rpe`; a v1 fixture
  (with per-set rpe/note) imports cleanly with those fields dropped.
- **ViewModels (JVM):**
  - ActiveSession: slim-row RPE/note persistence; `updateSet` without
    rpe/note.
  - SessionDetail: `updateSessionDetails` save path; end-after-start
    validation.
- **DAO (instrumented):** new update methods bump `updatedAt`.
- **Compose (instrumented):** existing critical-path tests adjusted where the
  set-editor change moves things they touch; one new compact test: enter
  RPE + note via the slim row, assert persistence in Room.

## 5. Documentation updates

- `docs/02-data-spec.md`: entity tables (`sessions.rpe`, removed
  `logged_sets.rpe/note`), schema version, backup format v2.
- `docs/03-ux-spec.md`: set editor, Active Session meta row, Session Detail
  edit sheet.

## Out of scope

- RPE anywhere in History list or Analytics (future candidates).
- Editing times during an active session or in a finish step.
- Any rollup/preservation of old per-set RPE/note values.
