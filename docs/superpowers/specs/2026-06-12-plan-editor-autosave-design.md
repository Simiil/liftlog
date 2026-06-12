# Plan editor auto-save — design

**Date:** 2026-06-12
**Status:** approved by owner (brainstorming session 2026-06-12)

## Motivation

The plan editor is the last explicit-save surface in the app. Today "New Plan"
opens an in-memory `PlanDraft`; nothing reaches Room until the Save button
(enabled only once the plan has a name and one "valid" day), and backing out —
or losing the back-stack entry — discards everything. Days with a blank name
or no exercises are silently dropped on save.

The owner wants the editor to behave like the rest of the app: **nothing is
ever explicitly saved, and nothing is ever lost.** Pressing "New Plan"
immediately creates a plan; adding a training day immediately creates the day;
back, X, app close, and process death never discard input.

## Decisions (from brainstorming)

| Question | Decision |
|---|---|
| Architecture | DB-backed editor: drop `PlanDraft`, observe Room directly, mutate via granular repository methods |
| Untouched new plan (blank name, zero days) on exit | Silently soft-deleted ("pristine discard"); any real input makes it persist |
| Removing a training day | Confirm dialog only when the day contains exercises; exercise-row removal stays instant |
| Days with zero exercises | Persist and show in editor + Plans list; hidden from Home quick-start chips |
| Save / Done buttons | Save pill removed; day-mode "Done" stays as pure navigation, always enabled |
| Delete-plan button | Always visible (every plan in the editor now exists in the DB); `isNewPlan` gating removed |

## 1. Flow & lifecycle

### New Plan

`PlansScreen`'s "New Plan" calls a new `PlansViewModel.createPlan()` →
`PlanRepository.createPlan("")`, then navigates to
`PlanEditorRoute(planId = <new id>, isNew = true)`.

- `PlanEditorRoute.planId` becomes **non-nullable**; the editor always works
  on a real, persisted plan.
- `isNew` drives only the title ("New plan" vs "Edit plan") and the pristine
  discard below. Editing an existing plan navigates with `isNew = false`.

### Add training day

"Add day" calls `createDayTemplate(planId, "")` and switches into day mode for
that real row. The day sub-editor remains a sub-mode of the single editor
screen (no new route), now keyed by the actual template id. "Done" and back
from day mode just navigate back to plan mode — no validity gate.

### Leaving the editor

X and system back close the screen; there is nothing to save. Exception: when
`isNew` and the plan is still *pristine* (blank name AND zero days), closing
soft-deletes it via `softDeletePlan`.

**Accepted edge case:** if the process is killed while a brand-new plan is
still pristine (user swipes the app away seconds after tapping "New Plan"),
the discard hook never runs and an "Untitled plan" survives in the Plans list.
Rare, and manually deletable. Avoiding it would require lazy plan creation,
which contradicts "New Plan immediately creates a plan."

### No data loss

Every change reaches Room within ~400 ms worst case (text debounce; structural
changes are immediate). Pending text writes are flushed when the screen stops
(backgrounding, navigation away) via a lifecycle hook in the screen, so the
debounce window survives normal app closure; only a hard process kill
mid-keystroke can lose up to ~400 ms of typed text. The `KEY_DRAFT` JSON
mirroring into `SavedStateHandle` is deleted — the database is the draft. Only
`editingDayKey` stays in `SavedStateHandle`, so process death restores the
user into the same day sub-editor.

## 2. ViewModel & data flow

### State is observed, not held

`PlanEditorViewModel` drops the `PlanDraft` flow. `uiState` combines:

- `observePlan(planId)`,
- `observeDayTemplates(planId)` flat-mapped with each day's
  `observeTemplateExercises`,
- `exerciseRepository.observeAll()` (names/equipment/muscle groups),
- `editingDayKey`,
- the pending-edit overlay (below).

Row keys in the UI are the real entity ids (stable across emissions, so
reorder animations and the day sub-mode keep working). `canSave`/`canDone`
disappear from `PlanEditorUiState`; `isNewPlan` comes from the route flag.

### Mutations are direct repository calls

| Editor action | Repository call | Timing |
|---|---|---|
| Edit plan/day name | `renamePlan` / `renameDayTemplate` | debounced ~400 ms; flushed on exit / sub-mode switch / screen stop |
| Add day | `createDayTemplate`, then enter day mode | immediate |
| Remove day | `softDeleteDayTemplate` | immediate (after confirm when non-empty) |
| Reorder days | `reorderDayTemplates` (new) | on drag stop |
| Add exercises (picker result) | `addExercisesToTemplate` (new) | immediate |
| Remove exercise | `removeTemplateExercise` | immediate |
| Reorder exercises | `reorderTemplateExercises` | on drag stop |
| Stepper tap (sets / reps min / reps max) | `updateTemplateExerciseTargets` | immediate |

### Pending-edit overlay

A DB-backed text field has a feedback loop: keystroke → write → flow emission
→ text field. With debouncing the emission lags and would yank the cursor
back; rapid stepper taps computed from a stale round-trip value would drop
increments. The ViewModel therefore keeps a small synchronous overlay:

- Name edits and target values update the overlay instantly; `uiState` is the
  DB state merged with the overlay, so the UI never waits on a round trip.
- Writes happen behind the overlay — debounced for text, immediate for
  targets. A confirmed write clears its overlay entry.
- The overlay is internal to the ViewModel; UI and repository know nothing
  about it.

## 3. Repository changes (`PlanRepository` / `PlanRepositoryImpl`)

- **Add** `reorderDayTemplates(orderedTemplateIds: List<String>)` — atomic
  position rewrite, mirroring `reorderTemplateExercises`.
- **Add** `addExercisesToTemplate(templateId: String, exerciseIds: List<String>)`
  — one transaction; appends after the current max position and dedupes
  against the day's live (non-deleted) rows. Dedup moves here from the
  ViewModel, where it could race.
- **Delete** `savePlanDraft` and the `PlanDraft`/`DayDraft`/`ItemDraft`
  models — verified editor-only; backup/import does not touch them.
- All other needed methods (`createPlan`, `renamePlan`, `softDeletePlan`,
  `createDayTemplate`, `renameDayTemplate`, `softDeleteDayTemplate`,
  `addExerciseToTemplate`, `updateTemplateExerciseTargets`,
  `removeTemplateExercise`, `reorderTemplateExercises`) already exist and are
  unit-tested; they finally gain production callers.

No schema or migration changes — same entities, UUIDs, timestamps,
soft-deletes. Every mutation already bumps `updatedAt` (sync-readiness rule).

## 4. UI changes

- **Plan mode header:** Save pill removed; X (and system back) is the only way
  out. Title from `isNew`: "New plan" / "Edit plan".
- **Delete plan button:** always visible; the `isNewPlan` gating (and its
  pinned test) is removed. Deleting a brand-new plan is a louder version of
  the pristine discard.
- **Day mode header:** back arrow and "Done" both close the sub-editor; "Done"
  is always enabled (pure navigation). The "New day"/"Edit day" title can no
  longer derive from `templateId == null` (every day is now a real row): the
  ViewModel instead remembers the key of the day it just created via "Add day"
  and shows "New day" for it until that sub-editor closes.
- **Remove-day confirm:** new dialog ("Remove this day?" + message) shown only
  when the day has ≥ 1 exercise, styled like the existing delete-plan confirm.
  New strings land in `values/` **and** `values-de/` (M6 in flight; German
  first pass included).
- **Test tags:** `PLAN_EDITOR_SAVE` removed; the close button keeps its tag.

## 5. Downstream effects

- **Home quick-start:** `HomeViewModel` filters days with `exerciseCount == 0`
  out of the chips. Blank-named days with exercises show as "Untitled day".
- **Plans list:** shows all days including empty ones; gains the missing
  `ifBlank` fallbacks ("Untitled plan" / "Untitled day") since blank names can
  now reach the DB.
- **Export/backup:** format unchanged; exports may now contain unnamed plans
  and empty days, which import already round-trips.

## 6. Edge cases

- At most ~400 ms of typed text can be lost, and only on a hard process kill
  mid-keystroke; normal backgrounding flushes.
- Rapid stepper taps cannot drop increments (synchronous overlay).
- Reorder mid-drag still buffers in the screen's local list and commits once
  on drag stop (unchanged).
- Deleting the plan while inside day mode closes the whole editor (unchanged).
- Exercise-picker dedup happens in the repository transaction against live
  rows, so a stale picker result cannot create duplicates.

## 7. Testing

- **`PlanEditorViewModelTest`** (rewrite): rename persists after debounce
  (virtual-time advance); add-day creates a row and enters day mode; stepper
  overlay survives rapid taps; picker dedup; pristine discard on close vs.
  kept once touched; flush-on-stop.
- **`PlansViewModelTest`**: "New Plan" creates a row and navigates with its id.
- **`HomeViewModelTest`**: empty-day chip filtering.
- **`PlanRepositoryTest`**: `reorderDayTemplates`, `addExercisesToTemplate`
  (dedup, positions); `savePlanDraft` tests deleted with the method.
- **Instrumented:** `PlanDeletePathTest` updated (delete always visible);
  `TemplateStartPathTest` updated to the no-Save flow (create → name → add day
  → add exercises → back → start session), asserting persistence at each step.
