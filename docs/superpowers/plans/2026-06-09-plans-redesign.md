# Plans Redesign — match the Claude Design mockup

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement
> this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Plans feature so it matches the approved design mockup (`plans.jsx` + the M3
stylesheet in `index.html`, design bundle at `/tmp/ll_design2/liftlog/project/`) — a **draft-then-save
New-Plan flow**, a **Plans list that nests training days under each plan with play-to-start**, and a
**multi-select Exercise Picker** — while keeping the data layer untouched.

**Architecture:** Replace the three immediate-persist M3 screens (PlansScreen → PlanDetailScreen →
TemplateEditorScreen, each persisting every edit instantly via name-dialogs) with the design's
structure: a Plans list (groups of days) → a single **draft** `PlanEditor` (name + add/reorder/remove
days, each day a sub-editor over the same in-memory draft) that commits **atomically on Save**.
Nothing persists until Save; Cancel discards. The Room schema is **unchanged** — targets stay
`targetSets` / `targetRepsMin` / `targetRepsMax` (owner decision: keep the min/max steppers, no rep
presets, no AMRAP, no migration). Save reconciles the draft against the DB preserving entity IDs
(sync-ready: UUIDs, `updatedAt`, soft-delete removed rows).

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Navigation Compose (type-safe routes), Hilt,
Coroutines/Flow, Room, `sh.calvin.reorderable` (already a dependency), kotlinx.serialization (already
a dependency, used for the SavedStateHandle-backed draft).

---

## Design reference (what we are matching)

Source: `/tmp/ll_design2/liftlog/project/plans.jsx` and the `index.html` stylesheet. M3 color **roles
only** (no hex); the mockup's `--md-*` vars map 1:1 to `MaterialTheme.colorScheme` roles. Keep the
existing theme/seed (owner doesn't care about seed color).

**PlansScreen (list)** — top-bar "Plans" + a "New plan" add icon button (right). Body = one
**plan-group** card per plan (`surfaceContainerHigh`, radius 22dp, padding 8/8/12dp):
- `plan-group-head` (whole-width button → **edit plan**): `pgh-name` (plan name, 18sp, bold,
  `onSurface`) over `plan-group-sub` ("N days · tap to edit", 13sp, `onSurfaceVariant`); trailing
  edit (pencil) icon 19dp `onSurfaceVariant`.
- one `plan-row` per day (radius 16dp): `plan-row-main` button (→ **start day**) with `plan-row-name`
  (day name, 16sp/600 `onSurface`) over `plan-row-sub` ("N exercises[ · group · group · group]",
  13sp `onSurfaceVariant`, up to 3 distinct muscle groups); trailing `row-play` = a 44dp circular
  button (`primary` bg / `onPrimary` filled play icon) → **start day**.
- bottom `ghost-btn full` "New plan" (outline border, `primary` text, radius 100, 48dp, top 14dp).
- Empty (no plans): keep the existing centered empty state (dumbbell icon + "No plans yet" + sub),
  but ALSO show the "New plan" affordance.

**PlanEditor (New plan / Edit plan)** — full-screen, draft, commit with **Save**:
- `editor-header` (60dp): close "X" (cancel) · title "New plan"/"Edit plan" (20sp/600) ·
  `editor-save` "Save" pill (40dp, `primary`/`onPrimary`; disabled → `surfaceContainerHighest`/
  `onSurfaceVariant`, `opacity .7`). Save enabled iff name non-blank AND ≥1 day.
- `editor-body`: `field-label` "Plan name"; `text-field` (54dp, radius 14dp, 1.5dp `outline` border,
  focus 2dp `primary`; placeholder "e.g. Upper / Lower, PPL, 5×5"); `field-label spaced` "Training
  days" + `field-count` pill (count; `primary` 14% bg, `primary` text); if no days, `editor-empty`
  ("No days yet. Add a training day to build your split.", `onSurfaceVariant`, `surfaceContainerLow`
  bg, radius 16dp, centered); `day-list` of reorderable `day-row`s (drag handle 40×48 `onSurfaceVariant`
  · `day-row-main` button → edit day [name/600 + "N exercises" sub] · `icon-btn sm` close → remove
  day); `add-row` dashed button "Add training day" (1.5dp dashed `outline`, `primary`, radius 16dp,
  54dp). Tapping a day or "Add training day" switches to the **day-editor mode** of the same screen.

**Day editor (New day / Edit day)** — same screen, different mode, **same in-memory draft**, commit
with **Done** (back arrow = also commit-to-draft on a valid day, or just return; see Task 6):
- header: back arrow · title "New day"/"Edit day" · "Done" pill (disabled until name non-blank AND
  ≥1 exercise).
- body: `field-label` "Day name"; `text-field` (placeholder "e.g. Push Day, Lower A"); `field-label
  spaced` "Exercises" + count; if empty, `editor-empty` ("No exercises yet. Add a few and set
  targets — these pre-fill your session."); `ex-edit-list` of reorderable `ex-edit-row`s
  (`surfaceContainerHigh`, radius 18dp): top row = drag handle · name (16/600) + "{group} · {equip}"
  (12sp `onSurfaceVariant`) · close (remove); targets row = **three steppers** (Sets / Reps min /
  Reps max) styled like the mockup's `target-stepper` (`surfaceContainer`, radius 12dp, 46dp; − / value
  / +). NOTE: the mockup shows a sets stepper + a rep-range *dropdown*; per owner decision we keep our
  three min/max steppers instead. `add-row` dashed "Add exercise" → opens the picker (multi-select).

**ExercisePicker (multi-select)** — reached from the day editor:
- header: back · "Add exercise".
- `picker-search` pill (`surfaceContainerHigh`, radius 100, 50dp) with search icon + "Search
  exercises" + clear.
- `filter-chips` (outline chips, radius 10dp, 36dp; selected → `secondaryContainer`). Keep both our
  existing muscle-group and equipment filter rows (functional superset of the mockup's single group
  row — do not regress filtering).
- `picker-list`: `create-row` at top ("Create new exercise[ "q"]", `primary`, `create-plus`
  `primaryContainer` circle) → opens the create-exercise form/sheet; "Recent" section
  (`picker-section`, `primary`) when query+filters empty; rows = `pick-row` with a 24dp checkbox
  (`pick-check`, radius 7dp, `outline` → `primary` when checked), name (16/500) + "{group} · {equip}"
  (12sp), divider `outlineVariant`; already-added exercises show an "Added" badge and are disabled.
- `picker-foot`: when ≥1 selected, a filled "Add N exercise(s)" button returns the **list** of ids.
- Active Session keeps using the picker in **single-select** mode (tap row → returns one id) —
  unchanged.

---

## File map

**New:**
- `domain/model/PlanDraft.kt` — `PlanDraft` / `DayDraft` / `ItemDraft` (@Serializable, in-memory).
- `ui/plans/PlanEditorScreen.kt` + `ui/plans/PlanEditorViewModel.kt` — the draft editor (plan + day
  modes). Replaces PlanDetail*/TemplateEditor*.
- `ui/components/DashedBorder.kt` — extract the duplicated private `Modifier.dashedBorder`
  (currently in `HomeScreen.kt:544` and `ActiveSessionScreen.kt:294`) into one shared helper; both
  existing call sites and the new Plans editor use it (DRY).
- `app/src/test/.../ui/plans/PlanEditorViewModelTest.kt` — draft behavior + reconciliation via
  `FakePlanRepository`.
- `app/src/test/.../data/repository/PlanRepositorySaveDraftTest.kt` — `savePlanDraft` reconciliation
  against `FakePlanDao` + `FakeTransactor` (new/edit/reorder/remove; history-isolation assertion).

**Modified:**
- `data/dao/PlanDao.kt` — add `observeAllDayTemplates()` + `observeAllTemplateExercises()` flows
  (for the list); nothing else (reconciliation reuses existing insert/update/softDelete + suspend
  reads).
- `domain/repository/PlanRepository.kt` + `data/repository/PlanRepositoryImpl.kt` — add
  `savePlanDraft(draft): String` (atomic reconcile) and `observePlansWithDays(): Flow<List<PlanWithDays>>`
  (+ `PlanWithDays` / `DaySummary` types). Keep all existing methods (still used: softDeletePlan,
  startSessionFromTemplate path, etc.). The fine-grained create/rename/add/reorder methods become
  unused by the UI but stay for now (don't delete — out of scope; note in PR).
- `ui/plans/PlansScreen.kt` + `ui/plans/PlansViewModel.kt` — list redesign (groups + day rows +
  play-to-start + edit + New plan); VM exposes plans-with-days and `startDay(templateId, onOpen)`.
- `ui/exercises/ExercisePickerScreen.kt` + `ExercisePickerViewModel.kt` — add multi-select mode;
  restyle to the mockup.
- `ui/navigation/Destinations.kt` — `PlanEditorRoute(planId: String? = null)`,
  `ExercisePickerRoute(multiSelect: Boolean = false)`, `PICKED_EXERCISE_IDS` key; remove
  `PlanDetailRoute` + `TemplateEditorRoute`.
- `ui/navigation/LiftLogNavHost.kt` — wire the new routes; remove PlanDetail/TemplateEditor
  composables; ExercisePicker writes single id OR id-list per mode.
- `ui/UiTestTags.kt` — add Plans-editor tags; keep/repoint existing ones (see Task 7).
- `res/values/strings.xml` — add the new strings; reuse existing where possible; remove ones left
  unreferenced (lint UnusedResources is an error gate).
- `testing/FakePlanRepository.kt` — implement `savePlanDraft` + `observePlansWithDays`.

**Deleted:**
- `ui/plans/PlanDetailScreen.kt`, `ui/plans/PlanDetailViewModel.kt`,
  `ui/plans/TemplateEditorScreen.kt`, `ui/plans/TemplateEditorViewModel.kt`.
- `app/src/test/.../ui/plans/PlanDetailViewModelTest.kt`,
  `app/src/test/.../ui/plans/TemplateEditorViewModelTest.kt`.

**Unchanged (do NOT touch):** all entities/Room schema, `domain/logging/Targets.kt`, Home
(`HomeViewModel`/`HomeScreen` chips + `startFromTemplate`), Active Session, Session repository,
`SessionFromTemplateTest`, `TemplateStartPathTest`, `CriticalLoggingPathTest`, theme/colors,
bottom-nav (`LiftLogApp`).

---

## Draft model (Task 1)

```kotlin
package de.simiil.liftlog.domain.model

import kotlinx.serialization.Serializable

/** In-memory, unsaved plan being edited. Persisted atomically by PlanRepository.savePlanDraft. */
@Serializable
data class PlanDraft(
    val planId: String? = null,          // null = new plan; else the plan being edited
    val name: String = "",
    val days: List<DayDraft> = emptyList(),
)

@Serializable
data class DayDraft(
    val key: String,                     // stable UI/reorder key (existing = templateId; new = UUID)
    val templateId: String? = null,      // null = not yet persisted
    val name: String = "",
    val items: List<ItemDraft> = emptyList(),
)

@Serializable
data class ItemDraft(
    val key: String,                     // stable UI/reorder key (existing = template_exercise id; new = UUID)
    val templateExerciseId: String? = null,
    val exerciseId: String,
    val targetSets: Int? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
)
```
Exercise display fields (name, equipment, muscle group) are NOT stored in the draft — the ViewModel
resolves them by combining the draft with `ExerciseRepository.observeAll()`.

---

## Reconciliation algorithm (`savePlanDraft`, Task 2)

Run entirely inside `transactor.immediate { }`. `now = clock.millis()`. Returns the plan id.

1. **Plan:** if `draft.planId == null` → insert `WorkoutPlanEntity(id = UUID, name = draft.name.trim(),
   position = (dao.maxPlanPosition() ?: -1) + 1, now, now, null)` and use its id. Else → load
   `dao.findPlan(planId)`; if name changed, `dao.updatePlan(existing.copy(name=…, updatedAt=now))`.
2. **Days:** `existingDays = dao.dayTemplatesForPlan(planId)` (by id). For each `dayDraft` at
   `index`:
   - resolve `templateId`: if `dayDraft.templateId == null` → new UUID, `dao.insertDayTemplate(
     PlanDayTemplateEntity(id, planId, name.trim(), position=index, now, now, null))`. Else → load
     existing; `dao.updateDayTemplate(existing.copy(name=name.trim(), position=index, updatedAt=now))`.
   - **Exercises for that day:** `existingTe = dao.templateExercisesFor(templateId)`. For each
     `item` at `pos`: if `item.templateExerciseId == null` → `dao.insertTemplateExercise(
     TemplateExerciseEntity(UUID, templateId, item.exerciseId, position=pos, item.targetSets,
     item.targetRepsMin, item.targetRepsMax, now, now, null))`. Else → load existing;
     `dao.updateTemplateExercise(existing.copy(position=pos, targetSets=…, targetRepsMin=…,
     targetRepsMax=…, updatedAt=now))`. Then **soft-delete** any `existingTe` whose id is not in the
     draft day's `templateExerciseId`s: `dao.softDeleteTemplateExercise(id, now)`.
3. **Removed days:** for each `existingDays` id not present in the draft → `dao.softDeleteDayTemplate(
   id, now)` (cascades its exercises via the existing per-template soft-delete — call
   `dao.softDeleteTemplateExercisesForTemplate(id, now)` then `dao.softDeleteDayTemplate(id, now)`,
   mirroring `softDeleteDayTemplate` in the impl).

**History isolation (must hold):** this only writes `workout_plans` / `plan_day_templates` /
`template_exercises`. It never touches `sessions` / `session_exercises` / `logged_sets`. Sessions
already snapshot templates (`startSessionFromTemplate`), so editing/deleting a plan cannot rewrite
past sessions. Assert this in `PlanRepositorySaveDraftTest`.

---

## Plans-with-days read (`observePlansWithDays`, Task 2)

```kotlin
data class PlanWithDays(val id: String, val name: String, val days: List<DaySummary>)
data class DaySummary(
    val templateId: String,
    val name: String,
    val exerciseCount: Int,
    val exerciseIds: List<String>,   // ordered; VM maps to muscle groups for the sub-line
)
```
Impl: `combine(dao.observePlans(), dao.observeAllDayTemplates(), dao.observeAllTemplateExercises())`
→ group days by `planId`, group template-exercises by `templateId`, assemble. Keep plan/day order by
`position`. (No ExerciseRepository dependency in the repo — the VM resolves muscle groups.)

`PlansViewModel` then `combine(planRepository.observePlansWithDays(),
exerciseRepository.observeAll())` to produce the UI rows, deriving up to 3 distinct muscle-group
labels per day from `exerciseIds`.

---

## Tasks

### Task 1 — PlanDraft domain types
**Files:** Create `domain/model/PlanDraft.kt` (code above).
- [ ] Add the three `@Serializable` data classes exactly as specified.
- [ ] `./gradlew :app:compileDebugKotlin` compiles.
- [ ] Commit: `feat(plans): add in-memory PlanDraft/DayDraft/ItemDraft model`.

### Task 2 — Repository: atomic save + plans-with-days read
**Files:** `data/dao/PlanDao.kt`, `domain/repository/PlanRepository.kt`,
`data/repository/PlanRepositoryImpl.kt`, `testing/FakePlanRepository.kt`,
`app/src/test/.../data/repository/PlanRepositorySaveDraftTest.kt`.
- [ ] PlanDao: add `observeAllDayTemplates()` and `observeAllTemplateExercises()` flows
      (`WHERE deletedAt IS NULL ORDER BY position`).
- [ ] PlanRepository: add `suspend fun savePlanDraft(draft: PlanDraft): String` and
      `fun observePlansWithDays(): Flow<List<PlanWithDays>>` (+ `PlanWithDays`/`DaySummary` types,
      placed in `domain/repository/PlanRepository.kt` or a small `domain/model` file).
- [ ] PlanRepositoryImpl: implement both — reconciliation in `transactor.immediate { }` per the
      algorithm above; `observePlansWithDays` via `combine` of the three DAO flows.
- [ ] FakePlanRepository: implement both against its in-memory maps (reconcile preserving ids;
      soft-delete removed; assign UUIDs to new). Keep existing methods working.
- [ ] **Tests** (`PlanRepositorySaveDraftTest`, JVM, `FakePlanDao` + `FakeTransactor` + a fixed
      `Clock`): (a) new plan with 2 days × 2 exercises → correct rows, positions, targets; (b) edit:
      rename plan, reorder days, add/remove an exercise, change targets → ids of unchanged rows
      preserved, removed rows soft-deleted (deletedAt set), positions reindexed; (c) Cancel path is a
      VM concern (no call) — assert that NOT calling savePlanDraft leaves the DB untouched; (d)
      history isolation: seed a session that snapshotted a template, run savePlanDraft that removes
      that day → the session's `session_exercises` are unchanged.
- [ ] `./gradlew testDebugUnitTest` green.
- [ ] Commit: `feat(plans): atomic savePlanDraft reconciliation + plans-with-days read`.

### Task 3 — Exercise Picker: multi-select mode + restyle
**Files:** `ui/exercises/ExercisePickerScreen.kt`, `ui/exercises/ExercisePickerViewModel.kt`,
`res/values/strings.xml`, `ui/UiTestTags.kt`.
- [ ] Add `multiSelect: Boolean = false` to `ExercisePickerScreen`; add a new callback
      `onSelectedMany: (List<String>) -> Unit` (used only in multi mode). Single mode keeps
      `onSelected: (String) -> Unit` unchanged.
- [ ] Multi mode: hold a `rememberSaveable` selection set; rows render a leading checkbox
      (`pick-check` style) and toggle on tap; already-present exercises (a new optional
      `existingIds: Set<String>` param) show "Added" and are disabled; a bottom bar shows a filled
      "Add N exercise(s)" button → `onSelectedMany(selection.toList())`. Create-exercise in multi
      mode adds the created id to the selection (don't auto-return).
- [ ] Restyle search + chips + rows toward the mockup (`picker-search` pill, `filter-chip`,
      `pick-row` + divider, `create-row` with `create-plus`). Keep both muscle + equipment filter
      rows.
- [ ] Tag the "Add N" button `UiTestTags.PICKER_ADD_SELECTED`; keep create affordance working.
- [ ] `./gradlew :app:compileDebugKotlin` + `testDebugUnitTest` green (ExercisePickerViewModel test,
      if any, still passes; single-select path unchanged).
- [ ] Commit: `feat(plans): multi-select Exercise Picker (single-select preserved for sessions)`.

### Task 4 — Navigation restructure
**Files:** `ui/navigation/Destinations.kt`, `ui/navigation/LiftLogNavHost.kt`.
- [ ] Destinations: add `@Serializable data class PlanEditorRoute(val planId: String? = null)` and
      `@Serializable data class ExercisePickerRoute(val multiSelect: Boolean = false)`; add
      `const val PICKED_EXERCISE_IDS = "picked_exercise_ids"`. Remove `PlanDetailRoute` and
      `TemplateEditorRoute`.
- [ ] NavHost: `PlansRoute` → `PlansScreen(onEditPlan = { id -> nav PlanEditorRoute(id) },
      onNewPlan = { nav PlanEditorRoute(null) }, onStartDay = …)`. Add `composable<PlanEditorRoute>`
      → `PlanEditorScreen(onClose = popBackStack, onSaved = popBackStack, onAddExercises = { nav
      ExercisePickerRoute(multiSelect = true) }, pickedExerciseIds = …, onPickedConsumed = …)`
      reading `PICKED_EXERCISE_IDS` from this entry's savedStateHandle. Update
      `composable<ExercisePickerRoute>` to read `multiSelect`; in single mode write
      `PICKED_EXERCISE_ID` (unchanged for Active Session), in multi mode write `PICKED_EXERCISE_IDS`
      to `previousBackStackEntry`. Remove the PlanDetail/TemplateEditor composables.
- [ ] `./gradlew :app:compileDebugKotlin` (will fail until Tasks 5–6 add the screens — acceptable
      mid-task; the implementer should land Tasks 4–6 together so the module compiles before commit).
- [ ] Commit with Task 6 (nav + screens compile together).

### Task 5 — Plans list redesign
**Files:** `ui/plans/PlansScreen.kt`, `ui/plans/PlansViewModel.kt`, `res/values/strings.xml`,
`ui/UiTestTags.kt`.
- [ ] PlansViewModel: depend on `PlanRepository` + `ExerciseRepository` + `SessionRepository`.
      Expose `uiState` built from `combine(observePlansWithDays(), exerciseRepository.observeAll())`
      → `List<PlanCardUi>` where each has `id`, `name`, and `days: List<PlanDayUi>` (templateId,
      name, sub-line "N exercises[ · g · g · g]"). Add `startDay(templateId: String, onOpen:
      (String) -> Unit)` mirroring `HomeViewModel.startFromTemplate` (resume-guard:
      `observeActiveSession().first()?.id ?: startSessionFromTemplate(templateId).id`).
- [ ] PlansScreen: TopAppBar "Plans" + a trailing "New plan" `IconButton` (Add) tagged
      `UiTestTags.PLANS_CREATE` → `onNewPlan`. Body: `LazyColumn` of plan-group cards. Each group:
      a clickable header (→ `onEditPlan(id)`, tagged `UiTestTags.PLAN_ROW`) + a `PlanDayRow` per day
      (tap or play → `viewModel.startDay(day.templateId, onOpenSession)`; play button tagged
      `UiTestTags.PLAN_DAY_START`). Trailing bottom "New plan" ghost button → `onNewPlan`. Keep the
      empty state. Match the mockup styling (roles + dp from the design reference).
- [ ] NavHost passes `onOpenSession = { id -> navController.navigate(ActiveSessionRoute(id)) }` into
      PlansScreen.
- [ ] Previews (light/dark, empty + populated) like the other screens.
- [ ] `./gradlew :app:compileDebugKotlin` + `testDebugUnitTest` green (after Task 8 rewrites the VM
      test).
- [ ] Commit with Task 6.

### Task 6 — PlanEditor (draft editor: plan + day modes)
**Files:** `ui/plans/PlanEditorScreen.kt`, `ui/plans/PlanEditorViewModel.kt`,
`ui/components/DashedBorder.kt` (extract), `res/values/strings.xml`, `ui/UiTestTags.kt`.
- [ ] PlanEditorViewModel (`@HiltViewModel`, `SavedStateHandle` + `PlanRepository` +
      `ExerciseRepository`): read `planId: String?` from the handle. Hold the draft in a
      `MutableStateFlow<PlanDraft>` AND mirror a serialized copy into SavedStateHandle on each
      mutation (kotlinx.serialization JSON under key `"draft"`), restoring it on init so the draft
      survives process death; if no saved draft and `planId != null`, load the plan
      (`observePlan`/`observeDayTemplates`/`observeTemplateExercises` once via `.first()`) into a
      draft; if `planId == null`, start an empty draft. Track `editingDayKey: String?` (also in
      SavedStateHandle). Expose a `uiState` that joins the draft with `exerciseRepository.observeAll()`
      for display names/equipment. Provide: `setPlanName`, `addDay`, `editDay(key)`, `removeDay(key)`,
      `reorderDays(keys)`, `closeDayEditor`; and day-scoped: `setDayName`, `addExercises(ids)`,
      `removeItem(key)`, `reorderItems(keys)`, `setTargets(key, sets, min, max)`; plus
      `save(onSaved: (String) -> Unit)` → `viewModelScope.launch { onSaved(repo.savePlanDraft(draft)) }`.
      `canSave` = name non-blank && days non-empty (and only days with name+≥1 exercise count — define
      precisely: a day must have a non-blank name AND ≥1 exercise to be considered valid; Save is
      enabled when plan name non-blank and there is ≥1 valid day; invalid/empty days are dropped on
      save). `canDone` (day) = day name non-blank && ≥1 exercise.
- [ ] PlanEditorScreen: render plan-mode when `editingDayKey == null`, else day-mode (same
      composable tree, design styling). Plan-mode: header (close X tagged
      `UiTestTags.PLAN_EDITOR_CANCEL`, title, Save tagged `UiTestTags.PLAN_EDITOR_SAVE`), plan-name
      `OutlinedTextField`/text-field, reorderable day list (drag handle, row → `editDay(key)`, remove
      X), dashed "Add training day" (→ `addDay()` then `editDay(newKey)`). Day-mode: header (back →
      `closeDayEditor`, title, "Done" tagged `UiTestTags.DAY_EDITOR_DONE`), day-name field,
      reorderable exercise list with the **three min/max steppers** (reuse the M3
      `LabeledStepper`/`TargetEditors` pattern from the old TemplateEditorScreen — copy it into this
      file, keep the design's `target-stepper` look), remove X, dashed "Add exercise" (→
      `onAddExercises()` which navigates to the multi-select picker; tagged
      `UiTestTags.TEMPLATE_ADD_EXERCISE`). Each exercise row tagged
      `UiTestTags.TEMPLATE_EXERCISE_ROW`; each day row `UiTestTags.PLAN_DAY_ROW`.
- [ ] Consume picker results: `LaunchedEffect(pickedExerciseIds) { if (!null) {
      viewModel.addExercises(it); onPickedConsumed() } }` (mirrors the M2/M3
      `pickedExerciseId` consume pattern, but for a list).
- [ ] Extract `Modifier.dashedBorder` into `ui/components/DashedBorder.kt` (internal/public);
      repoint HomeScreen + ActiveSessionScreen to it; remove their private copies.
- [ ] Reorder uses `sh.calvin.reorderable` (same idiom as the old TemplateEditorScreen:
      `rememberReorderableLazyListState`, `ReorderableItem`, `draggableHandle(onDragStopped = …)`),
      driving `reorderDays`/`reorderItems` on the draft.
- [ ] Previews (light/dark; new plan, plan with days, day editor with exercises).
- [ ] `./gradlew lint testDebugUnitTest assembleDebug` green (with Tasks 4–5 landed together).
- [ ] Commit: `feat(plans): draft-then-save PlanEditor (plan + day modes) matching the design`.

### Task 7 — Strings, test tags, cleanup
**Files:** `res/values/strings.xml`, `ui/UiTestTags.kt`, delete the four replaced files.
- [ ] strings.xml — add: `plan_editor_new` ("New plan"), `plan_editor_edit` ("Edit plan"),
      `plan_name_field_hint` ("e.g. Upper / Lower, PPL, 5×5"), `plan_training_days` ("Training days"),
      `plan_days_empty` ("No days yet. Add a training day to build your split."), `plan_add_day`
      ("Add training day"), `day_editor_new` ("New day"), `day_editor_edit` ("Edit day"),
      `day_name_field_hint` ("e.g. Push Day, Lower A"), `day_exercises` ("Exercises"),
      `day_exercises_empty` ("No exercises yet. Add a few and set targets — these pre-fill your
      session."), `editor_save` ("Save"), `editor_done` ("Done"), `plan_days_count` (plurals: "%d
      day"/"%d days"), `plan_exercises_count` (reuse `set_count`? no — add `exercise_count` plurals
      "%d exercise"/"%d exercises"), `picker_add_selected` ("Add %d"), `plan_start_day_cd` ("Start
      %s"), `plan_edit_cd` ("Edit %s"). Reuse existing: `tab_plans`, `plans_create` ("New plan"),
      `plans_empty_title`/`plans_empty_sub`, `navigate_back`, `template_remove_exercise`,
      `template_target_sets`, `template_target_reps`, `template_drag_handle_cd`, `dialog_cancel`,
      equipment_* , set_count.
- [ ] Remove now-unreferenced strings (`plan_name_hint`, `plan_rename`, `plan_delete`,
      `plan_delete_confirm_*`, `plan_detail_add_day`, `plan_detail_empty_*`, `day_name_hint`,
      `day_rename`, `day_delete`, `day_delete_confirm_*`, `day_template_empty`, `template_set_targets`,
      `template_targets_summary`) — but ONLY after grepping that nothing else references them; lint
      `UnusedResources` is an error gate.
- [ ] UiTestTags — keep `PLANS_CREATE`, `PLAN_ROW`, `TEMPLATE_ADD_EXERCISE`, `TEMPLATE_EXERCISE_ROW`,
      `HOME_TEMPLATE_CHIP`; add `PLAN_DAY_ROW`, `PLAN_DAY_START`, `PLAN_EDITOR_SAVE`,
      `PLAN_EDITOR_CANCEL`, `DAY_EDITOR_DONE`, `PICKER_ADD_SELECTED`. Remove `PLAN_DETAIL_ADD_DAY`,
      `DAY_TEMPLATE_ROW` (their screens are gone) after confirming no test references them.
- [ ] Delete `PlanDetailScreen.kt`, `PlanDetailViewModel.kt`, `TemplateEditorScreen.kt`,
      `TemplateEditorViewModel.kt`.
- [ ] Commit (folded into Task 6's commit is fine, or its own `chore(plans): strings/tags/cleanup`).

### Task 8 — Tests
**Files:** delete `PlanDetailViewModelTest.kt`, `TemplateEditorViewModelTest.kt`; rewrite
`PlansViewModelTest.kt`; add `PlanEditorViewModelTest.kt`; touch `FakePlanRepository` usages.
- [ ] Delete the two obsolete VM tests.
- [ ] Rewrite `PlansViewModelTest`: seed the fake (createPlan + savePlanDraft or
      createDayTemplate/addExerciseToTemplate) and assert `uiState.plans` exposes the plan with its
      day rows + counts; assert `startDay` resumes the active session if one exists, else starts from
      template (use `FakeSessionRepository`).
- [ ] Add `PlanEditorViewModelTest`: new-plan draft (set name, addDay, edit day name, addExercises,
      setTargets, save → FakePlanRepository.savePlanDraft produced the expected plan/days/exercises);
      edit-existing (construct VM with `planId`, draft loads from fake, reorder/remove, save →
      reconciled); canSave/canDone gating; reorder updates draft order; removeDay/removeItem.
- [ ] `./gradlew lint testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL, 0 lint errors, all unit
      tests green. `./gradlew assembleDebugAndroidTest` compiles (instrumented tests still build;
      `TemplateStartPathTest`/`CriticalLoggingPathTest` untouched and must still compile + their
      tags exist).
- [ ] Commit: `test(plans): cover PlanEditor draft + reconciliation; update Plans list test`.

### Task 9 — Manual emulator verification (controller, not a subagent)
Done by the orchestrator after Task 8 (see Verification). Not a code task.

---

## Verification

Run from the worktree (`local.properties` already copied; `sdk.dir=/home/samuel/Android/Sdk`).

1. **Static (CI parity):** `./gradlew lint testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL, lint
   0 errors (no `UnusedResources`), unit tests green. `./gradlew assembleDebugAndroidTest` compiles.
2. **Instrumented (regression):** the existing Compose tests must still pass on `emulator-5554`:
   `./gradlew connectedDebugAndroidTest
   -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.ui.TemplateStartPathTest`
   and `…class=de.simiil.liftlog.ui.CriticalLoggingPathTest` (Home→ActiveSession paths are
   unchanged; confirms the multi-select picker change didn't break single-select add).
3. **Visual (the point of this change):** install on `emulator-5554`
   (`adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`; `pm clear` for a
   clean state), drive with `adb` (tap via `uiautomator dump` bounds; `exec-out screencap`). Walk and
   screenshot, comparing each to the mockup (`plans.jsx`):
   - Plans empty → "New plan".
   - New-plan flow: name → add day → day editor → add exercise (multi-select picker, select ≥2,
     "Add N") → min/max steppers → Done → back on plan editor with the day row → **Save** → Plans
     list shows the plan **group with its day(s)** and a **play button**.
   - Tap a day's play button → an Active Session starts from that template (resume-guarded).
   - Re-open the plan via the header → edit (rename, reorder a day, remove an exercise) → Save →
     verify the change persisted and a previously-started session is unaffected.
   - Check **light and dark**.
   Iterate until it matches the design.
4. **Finish:** the work is on branch `plans-redesign` in the worktree; push and open a PR (owner
   reviews/merges and removes the worktree — do NOT merge or remove it).
