# Refactor Training Plans (issue #30) — Implementation Plan

## Context

Issue #30 names two problems: (1) multiple plans × multiple days makes the Plans UI confusing — users get lost while editing; (2) the plan editor is the app's last save-on-Done surface (in-memory `PlanDraft`, persisted only via the Save pill) while everything else persists on change.

The fix: a **single-plan-first UI** (tab renamed "Plan", shows exactly one plan whose days are edited in place), a **seeded default plan** so the tab is never empty (zero-setup, hard constraint 1), **multi-plan support demoted** to an overflow menu + title-bar switcher dropdown, and **persist-on-change editing** per the approved autosave spec `docs/superpowers/specs/2026-06-12-plan-editor-autosave-design.md`.

**Spec supersession note:** the autosave spec's *day-editor and repository mechanics* (DB-backed observation, granular mutations, ~400 ms debounced text + flush-on-stop, pending-edit overlay, remove-day confirm only when non-empty, empty days persist but hidden from Home chips, delete `PlanDraft`/`savePlanDraft`) are followed. Its *plan-mode editor* parts (plans list → editor navigation, "New Plan creates a blank plan" + pristine discard, plan-mode Save-pill removal) are superseded by the single-plan UI and are **not** built.

## Owner decisions (confirmed 2026-07-04, via AskUserQuestion)

1. **Default plan name:** localized at seed time via app/device locale (EN "Default", DE "Standard"); stored as a plain renameable name. No display-time resolution.
2. **"Most used plan" (Home):** keep the shipped recency rule `observeMostUsedOrFirstPlanId()` (most-recently-started template session's plan, else first by position). Home does NOT follow the tab selection.
3. **Deleting the last plan:** auto-create a fresh empty default (new UUID) — the tab is never empty; no empty-state UI. Seeding invariant everywhere: **zero live plans → create default**.
4. **Plan rename / New plan:** overflow menu items opening a small name dialog; persisted on confirm (confirm disabled when blank — no untitled plans from this path).

Additional design decisions:
- **Selection representation:** no schema change, no `isDefault` column, no backup-format bump. The Plan tab's current plan = DataStore key `selected_plan_id` (excluded from backup by construction), validated against live plans on every emission, falling back to `observeMostUsedOrFirstPlanId()` when unset/stale.
- **Home first-launch predicate** (⚠ flagged for owner sign-off in PR1): `HomeUiState.hasPlans` becomes `hasPlanContent` (= any plan has a day with ≥1 exercise). Without this, the seeded default plan makes `hasPlans` always true and fresh installs lose the FirstLaunch welcome — the seed would not be "invisible".

## PR slicing (4 stacked PRs; each independently green; earlier PRs "Part of #30", last "Fixes #30")

Deliberate deviation from the ticket's example slicing ("seeding → editor autosave → single-plan UI"): implementing the autosave spec's plan-mode editor verbatim and then deleting it in the next PR is throwaway work, and a half-converted editor (DB-backed day mode over a draft-based plan mode) is incoherent. Instead PR3 lands day-editor autosave *together with* the single-plan tab (they are one seam: the tab's add/edit-day requires DB-backed days), and multi-plan chrome splits into PR4.

⚠ Do not cut a release between PR3 and PR4 — a multi-plan user would temporarily lack the switcher (data intact).

| PR | Branch | Scope |
|---|---|---|
| PR1 | `refactor/30-training-plans-1-seeding` | Default-plan seeding + selection plumbing (data only, invisible) |
| PR2 | `refactor/30-training-plans-2-repo` | Autosave repository primitives (spec §3) |
| PR3 | `refactor/30-training-plans-3-single-plan-ui` | Single-plan tab + DB-backed day editor; deletions |
| PR4 | `refactor/30-training-plans-4-multi-plan` | Title switcher dropdown + New plan |

Task 0 (with PR1): commit this plan to `docs/superpowers/plans/2026-07-04-30-plans-refactor.md` (repo convention).

---

## PR1 — data: default-plan seeding + selection plumbing

**New files**
- `domain/plan/DefaultPlanNameProvider.kt` — `fun interface DefaultPlanNameProvider { fun defaultPlanName(): String }`
- `domain/plan/DefaultPlanEnsurer.kt` — `@Singleton`, injects `PlanRepository` + `DefaultPlanNameProvider`:
  - `suspend fun ensure()` → `planRepository.ensureDefaultPlan(nameProvider.defaultPlanName())`
  - `suspend fun deletePlan(planId)` → `planRepository.softDeletePlanAndEnsureDefault(planId, name)` (atomic delete+reseed; observers never see zero plans)
- `ui/plans/ResourceDefaultPlanNameProvider.kt` — `context.getString(R.string.default_plan_name)`; respects per-app locale (M6 `localeConfig`). Bind via `@Binds` in `di/UiBindingsModule.kt` (mirrors `ExerciseNameResolver` pattern).

**Modified**
- `data/dao/PlanDao.kt`: `@Query("SELECT COUNT(*) FROM workout_plans WHERE deletedAt IS NULL") suspend fun countLivePlans(): Int`
- `domain/repository/PlanRepository.kt` + `data/repository/PlanRepositoryImpl.kt` (impl gains `DataStore<Preferences>` constructor param — the existing "settings" store; selection deliberately lives here, not in `SettingsRepository`, which mirrors the exportable settings object):
  - `suspend fun ensureDefaultPlan(name)` — in `transactor.immediate {}`: no-op if `countLivePlans() > 0`, else insert `WorkoutPlanEntity(UUID.randomUUID(), name.trim(), position = maxPlanPosition+1, now, now, null)`
  - `suspend fun softDeletePlanAndEnsureDefault(id, defaultName)` — same cascade as `softDeletePlan` + conditional reseed, one transaction
  - `suspend fun selectPlan(id)` — DataStore write to `stringPreferencesKey("selected_plan_id")`
  - `fun observeSelectedOrFallbackPlanId(): Flow<String?>` — stored id validated against `dao.observePlans()`; invalid/unset → `observeMostUsedOrFirstPlanId()`; `distinctUntilChanged()`. Stale ids (import, deletion) self-heal with no cleanup writes.
- `LiftLogApplication.onCreate`: `appScope.launch { seeder.seed(); defaultPlanEnsurer.ensure() }`
- Backup import apply path (`BackupRepositoryImpl`): call `defaultPlanEnsurer.ensure()` after `replaceAll` + settings restore (covers zero-plan backups; `replaceAll` is one atomic transaction so no wiped intermediate state is observable).
- `HomeViewModel`/`HomeScreen`: `hasPlans` → `hasPlanContent` (derive from `observePlansWithDays()`: any day with `exerciseCount > 0`); FirstLaunch predicate updated.
- Strings (both `values/` **and** `values-de/`): `default_plan_name` = "Default" / "Standard".

**Tests (TDD — red first, per behavior)**
- JVM `PlanRepositoryTest` (add in-memory `DataStore` fake, extract `testing/InMemoryPreferencesDataStore.kt` from `SettingsRepositoryTest`'s pattern): ensure creates when none live / no-op when live / fresh UUID after delete-all; atomic delete+reseed cascades and seeds / doesn't seed when another plan remains; selectPlan persists + resolution emits selected; fallback when unset / stale; recovery when id reappears.
- JVM new `domain/plan/DefaultPlanEnsurerTest` (FakePlanRepository).
- JVM `HomeViewModelTest`: `hasPlanContent` false with only empty days / true once a day has an exercise.
- Update `testing/FakePlanRepository`, `testing/fakes/FakePlanDao`.
- Instrumented (scope with `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>` on emulator-5554): `PlanDaoTest.countLivePlans excludes tombstones`; `BackupRoundTripTest.applyImport with zero plans reseeds a localized default plan`.

---

## PR2 — data: autosave repository primitives (spec §3)

- `PlanDao`: `updateDayTemplatePosition(id, position, now)`; `observeDayTemplate(id): Flow<PlanDayTemplateEntity?>` (live-only).
- `PlanRepository` + impl + fakes:
  - `suspend fun reorderDayTemplates(orderedTemplateIds: List<String>)` — atomic position rewrite, mirrors `reorderTemplateExercises`
  - `suspend fun addExercisesToTemplate(templateId, exerciseIds)` — one transaction; dedupes against the day's live rows AND within input; appends after max position (dedup moves out of the ViewModel per spec)
  - `fun observeDayTemplate(id): Flow<PlanDayTemplate?>` — emits null once tombstoned (drives day-editor auto-close)
- `savePlanDraft` **stays** until PR3 (old editor still uses it).

**Tests:** JVM `PlanRepositoryTest`: reorder reassigns positions + bumps `updatedAt`; add appends preserving input order; dedupes vs live rows; dedupes within input; re-adds after soft-delete; observe emits then null. Instrumented `PlanDaoTest`: position write; observe-null-after-tombstone (Turbine idiom).

---

## PR3 — ui: single-plan tab + DB-backed day editor

**Navigation** (`Destinations.kt`, `LiftLogNavHost.kt`, `LiftLogApp.kt`)
- `PlansRoute` → `PlanRoute`; delete `PlanEditorRoute`; add `@Serializable data class DayEditorRoute(val templateId: String, val isNew: Boolean = false)` (`isNew` drives only the "New day"/"Edit day" title).
- Move the `PICKED_EXERCISE_IDS` savedStateHandle read/consume block from the old editor entry to `composable<DayEditorRoute>` verbatim (picker side unchanged).
- Tab label: keep key `tab_plans`, change values to "Plan"/"Plan".

**New `ui/plans/PlanScreen.kt` + `PlanViewModel.kt`** (replace `PlansScreen`/`PlansViewModel`)
- State: `PlanTabUiState(loading, plan: CurrentPlanUi?, planChoices: List<PlanChoiceUi>)`; `CurrentPlanUi(id, name, days: List<PlanDayUi>)`. Built from `combine(observeSelectedOrFallbackPlanId(), observePlansWithDays(), exerciseRepository.observeAll())`. Days include empty ones (hidden only from Home chips).
- Methods (persist-on-change): `startDay` (ported verbatim — resume-else-start, **play stays one tap**), `addDay(onCreated)` → `createDayTemplate(planId, "")` then navigate `DayEditorRoute(id, isNew=true)`, `removeDay` → `softDeleteDayTemplate` (confirm dialog only when `exerciseCount > 0`), `reorderDays` → `reorderDayTemplates` on drag stop, `renamePlan` (dialog confirm), `deletePlan()` → `DefaultPlanEnsurer.deletePlan(currentId)` (atomic reseed — tab never empty).
- UI: `TopAppBar` title = plan name (`ifBlank` → `plan_untitled`); overflow `MoreVert` → `DropdownMenu` with Rename/Delete (copy `ui/session/ExerciseCard.kt` ~L254–306 pattern). Body: reorderable day rows (drag handle · name + "N exercises · groups", tap → day editor · X remove · 48 dp play, `sh.calvin.reorderable` pattern from old `PlanModeContent`), dashed `AddRow` "Add training day". Shared `PlanNameDialog` (OutlinedTextField reusing `plan_name_field_*`; confirm disabled when blank) for Rename (PR4 reuses for New).

**New `ui/plans/DayEditorScreen.kt` + `DayEditorViewModel.kt`** (extracted from old `DayModeContent`/`ExerciseEditorRow`/`TargetStepper`; shared pieces → `ui/plans/EditorComponents.kt`)
- `DayEditorUiState(loading, dayName, exercises, dayGone)`; combine `observeDayTemplate`, `observeTemplateExercises`, `exerciseRepository.observeAll()`, pending overlays. `dayGone` → `LaunchedEffect` auto-close.
- Spec §2 mechanics: `setDayName` — synchronous overlay + 400 ms debounced `renameDayTemplate` (inject debounce duration with 400 ms default for testability); `flushPendingEdits()` on lifecycle ON_STOP + onDispose; `setTargets` — synchronous overlay + immediate write (rapid stepper taps can't drop); `addExercises` → `addExercisesToTemplate`; `removeExercise`, `reorderExercises` immediate. The DB is the draft; no `KEY_DRAFT`.
- Header: back arrow + always-enabled "Done" (both pure `popBackStack`; keep tag `DAY_EDITOR_DONE`).

**Deletions:** `PlansScreen.kt`, `PlansViewModel.kt`, `PlanEditorScreen.kt`, `PlanEditorViewModel.kt`, `domain/model/PlanDraft.kt`; `savePlanDraft` from interface/impl/fake; tests `PlanEditorViewModelTest`, `PlansViewModelTest`, `PlanRepositorySaveDraftTest`.

**Home:** filter `exerciseCount > 0` from template chips; chip label `ifBlank` → `plan_untitled_day`. `observeMostUsedOrFirstPlanId()` untouched.

**Strings** (every key in `values/` AND `values-de/`; lint `MissingTranslation` is an error gate): changed `tab_plans`; new `plan_menu_rename`, `plan_overflow_cd`, `plan_untitled`, `plan_day_remove_confirm_title`, `plan_day_remove_confirm_message`, `common_save`, `common_remove`; removed `plans_empty_*`, `plan_group_sub`, `plan_edit_cd`, `plan_editor_new/edit`, `editor_save`, `plans_create` (returns in PR4).

**UiTestTags:** remove `PLANS_CREATE`, `PLAN_ROW`, `PLAN_EDITOR_SAVE`, `PLAN_EDITOR_CANCEL`, `PLAN_EDITOR_DELETE`; add `PLAN_OVERFLOW`, `PLAN_MENU_RENAME`, `PLAN_MENU_DELETE`, `PLAN_RENAME_FIELD`, `PLAN_RENAME_CONFIRM`, `PLAN_ADD_DAY`, `PLAN_DAY_REMOVE`, `PLAN_DAY_REMOVE_CONFIRM`, `DAY_NAME_FIELD`.

**Tests (TDD)**
- JVM new `DayEditorViewModelTest` (SavedStateHandle `{"templateId" to id}`; Turbine + `MainDispatcherRule`; `advanceTimeBy` for debounce): load; overlay-instant name; debounced persist; rapid retypes collapse; flush persists immediately; rapid stepper taps don't drop; targets persist immediately; add delegates to repo; remove immediate; reorder persists; `dayGone` on tombstone.
- JVM new `PlanViewModelTest` (ports `PlansViewModelTest` cases): fallback plan + day rows incl. empty; counts + ≤3 muscle groups; startDay start/resume; addDay creates + emits id; removeDay; reorderDays; renamePlan trims; deletePlan reseeds default.
- JVM `HomeViewModelTest`: chips exclude zero-exercise days.
- Instrumented: `PlanDeletePathTest` rewritten (overflow → delete → confirm → title = localized default, exactly 1 live plan, new id); **new `PlanEditPathTest`** — the no-Save persistence path: add day → name → add exercise via picker → back (flush) → row shows "1 exercise" → play → session shows exercise, asserting repo persistence at each step; `TemplateStartPathTest` + `CriticalLoggingPathTest` kept — verify green.

---

## PR4 — ui: multi-plan chrome (switcher + New plan)

- `PlanViewModel`: populate `planChoices` from `observePlans()` (position order); `createPlan(name)` → `repo.createPlan` + `repo.selectPlan(new.id)`; `selectPlan(id)` → PR1 persistence.
- `PlanScreen`: with ≥2 live plans the title becomes a clickable `Row(name + ArrowDropDown)` (tag `PLAN_SWITCHER`, cd `plan_switcher_cd`) anchoring a `DropdownMenu` of plans (tag `PLAN_SWITCHER_ITEM`, current marked); with exactly 1 plan → plain title (affordance hidden). Overflow gains "New plan" (`PLAN_MENU_NEW`) → `PlanNameDialog` (tags `PLAN_NEW_FIELD`/`PLAN_NEW_CONFIRM`).
- Strings: `plans_create` "New plan"/"Neuer Plan", `plan_switcher_cd` "Switch plan"/"Plan wechseln", `common_create` "Create"/"Erstellen".
- Tests — JVM `PlanViewModelTest`: choices in position order; selectPlan persists + current follows; createPlan creates+selects+shows; deleting current with others live falls back without seeding. New instrumented `PlanSwitchPathTest`: New plan → dialog → create → switcher appears → switch → title changes.

---

## Edge cases (designed-in)

- **Import**: selection never exported; stale id self-heals via live-validation → fallback; zero-plan backup → `ensure()` after apply. Day editor open across an import that removes its template → `dayGone` auto-close.
- **Delete last plan**: single transaction delete+reseed → one emission, no zero-plan frame.
- **Debounce**: only day name is debounced; flushed on ON_STOP/dispose; plan rename is dialog-atomic; worst case ~400 ms typed text lost on hard kill mid-keystroke (spec-accepted).
- **Active session + switcher**: switching changes only tab display + DataStore; play resumes the active session (existing semantics).
- **Process death**: route args restore day editor; DB is the draft; dialogs use `rememberSaveable`; selection in DataStore.

## Risks / notes

1. `hasPlans` → `hasPlanContent` changes when the FirstLaunch welcome shows — required for invisible seeding; owner sign-off in PR1 review.
2. Title-anchored dropdown is net-new UI — ellipsize long names, cap menu width.
3. No schema migration, no backup format bump anywhere (verify `CURRENT_FORMAT_VERSION` untouched in review).
4. Release gap PR3→PR4 (above).

## Verification (per PR, before commit; ktlintFormat first)

1. `./gradlew ktlintFormat` then the exact CI chain: `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug`
2. Scoped instrumented tests on emulator-5554: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>` for each touched instrumented class (`PlanDaoTest`, `BackupRoundTripTest`, `PlanDeletePathTest`, `PlanEditPathTest`, `PlanSwitchPathTest`, `TemplateStartPathTest`, `CriticalLoggingPathTest`).
3. On-device walkthrough (`./gradlew installDebug`, drive via adb):
   - **Fresh install** (`adb shell pm clear`): Plan tab shows "Default" (or "Standard" with German app locale) with zero days; Home still shows the FirstLaunch welcome.
   - **Upgrade path**: install base v0.2.0 APK, create a plan with days, install new APK over it → that plan is shown on the tab, untouched; no extra "Default" appears.
   - **Persist-on-change**: add day → type name → add exercise → background the app → `adb shell am kill de.simiil.liftlog` → relaunch → everything persisted.
   - **Delete last plan** → fresh default appears immediately. Create second plan → switcher appears; switch → restart app → selection retained. Delete one of two → switcher hides.
   - German locale spot-check of all new strings.

## Workflow

- TDD mandatory (superpowers:test-driven-development): red test first per behavior; instrumented tests scoped on emulator-5554.
- Conventional commits `refactor(plans): …` / `feat(plans): …` / `test(plans): …`; never include Claude session URLs.
- Stacked branches as per table; **ask the owner before opening each PR**; never merge. PRs 1–3 "Part of #30", PR4 "Fixes #30".
