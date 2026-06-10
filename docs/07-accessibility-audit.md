# 07 — Accessibility Audit (M5)

> **Status:** In progress · 2026-06-10
> Audits the app against [03-ux-spec](03-ux-spec.md) §7. Covers the M5 exit
> criterion "a11y audit checklist passes (TalkBack walkthrough, 200% font scale,
> target sizes)". Static code review is complete; on-device verification in §3.

## 1. Checklist (03-ux-spec §7)

| # | Criterion | Status | Notes |
|---|-----------|--------|-------|
| 1 | Touch targets: logging path ≥56dp, else ≥48dp | ⚠️ Partial | Hot path mostly compliant; several secondary controls under 48dp (§2 F-07..F-10). |
| 2 | Content descriptions: stateful & specific | ⚠️ Partial | Steppers/sets exemplary; clickable rows not merged, a few unlabeled icons (§2 F-01..F-06). |
| 3 | Dynamic type survives 200% font, no clipping / 1-tap path intact | ✅ Pass (hot path) | Verified on-device §3.1: steppers + numpad clean at 200%. One cosmetic nav-label wrap (F-12). |
| 4 | TalkBack order on active card: name → ghost → logged sets → weight → reps → LOG SET | ✅ Pass | Verified on-device §3.2: a11y tree order is an exact match. |
| 5 | Contrast: M3 roles only; trend badges pair color **with a glyph** | ⚠️ Partial | TrendBadge ✓ (glyph+color). Custom `success` green is ~4.2:1 on light surface (§2 F-11). |
| 6 | No time-based UI (timer deferred) | ✅ N/A | No timing constraints in v1. |

## 2. Static findings

Severity: **BLOCKER** (breaks AT usage on a core path) · **MAJOR** (clear spec
violation, fix before release) · **MINOR** (polish). Line numbers verified
against `app/src/main/kotlin/de/simiil/liftlog/...` as of 2026-06-10.

### Content descriptions & semantics (criterion 2 / 4)

- **F-01 · MAJOR · Clickable rows not merged for TalkBack.** Clickable list/cards
  expose their child Texts as separate nodes, so TalkBack reads name, date, and
  metadata as disconnected items instead of one actionable element. Affects:
  `home/HomeScreen.kt` RecentSessionItem, `history/HistoryScreen.kt`
  HistorySessionCard, `exercises/ExercisePickerScreen.kt` PickerExerciseRow,
  `plans/PlansScreen.kt` PlanDayRow, `plans/PlanEditorScreen.kt` DayRow edit
  column. **Fix:** add `Modifier.semantics(mergeDescendants = true) { contentDescription = … }`
  (or `clearAndSetSemantics`) to the row root so it reads as a single
  "{name}, {summary}, double-tap to {action}".
- **F-02 · MAJOR · Unlabeled clear-search button.**
  `exercises/ExercisePickerScreen.kt` clear-search `IconButton` has
  `contentDescription = null` — TalkBack announces only "button". **Fix:** add a
  `picker_clear_search` string.
- **F-03 · MINOR · Generic overflow description.** `session/ExerciseCard.kt`
  overflow menu reads "More options" with no exercise context. **Fix:** "More
  options for {exercise}".
- **F-04 · MINOR · Reps stepper descriptions not stateful.**
  `components/RepsStepper.kt` uses "Increase reps" / "Decrease reps"; WeightStepper
  includes the value/step. **Fix:** mirror WeightStepper ("Increase reps to {n}").
- **F-05 · MINOR · Missing heading semantics.** `session/SessionDetailScreen.kt`
  ExerciseHeader and `analytics/AnalyticsScreen.kt` section labels lack
  `semantics { heading() }`, so TalkBack heading-jump can't reach them. (Settings
  already does this correctly — use it as the pattern.)
- **F-06 · MINOR · Charts have no text alternative.**
  `components/charts/ProgressLineChart.kt` and `components/charts/Sparkline.kt`
  render unlabeled Canvas/Vico content. The underlying numbers are shown as text
  nearby, so this is supplementary, not blocking. **Fix:** pass a summary
  `contentDescription` (e.g. "e1RM trend, {range}, {trend}") to the chart, and
  mark the decorative inline Sparkline with `clearAndSetSemantics {}`.

### Touch targets (criterion 1)

- **F-07 · MAJOR · Plan-editor stepper buttons 40dp.**
  `plans/PlanEditorScreen.kt` StepperButton is `size(40.dp)` (< 48dp); the
  TargetStepper row is `height(46.dp)`. **Fix:** `sizeIn(minWidth=48.dp, minHeight=48.dp)`.
- **F-08 · MAJOR · Plan day "start" affordance ~44dp.** `plans/PlansScreen.kt`
  PlanDayRow play button is below 48dp. **Fix:** enforce `heightIn(min = 48.dp)`.
- **F-09 · MINOR · Filter chips 36dp.** `exercises/ExercisePickerScreen.kt`
  FilterChip is `height(36.dp)`. M3 chips ship at 32dp, but the spec asks for
  ≥48dp touch targets — wrap with `heightIn(min = 48.dp)` (visual size can stay).
- **F-10 · MINOR · Collapsed set row & quick chips lack enforced minimum.**
  `components/LoggedSetRow.kt` CollapsedSetRow relies on padding (~46dp); the
  `components/InlineNumpad.kt` QuickChips are 48dp while the numpad keys are 56dp.
  Quick chips aren't on the spec's explicit ≥56 list, so 48dp is acceptable, but
  add `heightIn(min = 48.dp)` to the set row for safety.

### Contrast & color (criterion 5)

- **F-11 · MAJOR · Custom `success` green is marginal on light surface.**
  `theme/ExtendedColors.kt` `success = #1E8E3E` computes to **~4.2:1** on a
  near-white M3 surface — below the 4.5:1 WCAG AA threshold for small text (the
  TrendBadge "↑" text is 13–15sp). Dark-theme `#81C995` is ~9.5:1 (fine).
  **Fix:** darken the light green to ≥4.5:1 (e.g. `#1B7A35` ≈ 5.0:1) or render
  the badge at the large-text size (≥14sp bold → 3:1 suffices). Note this is the
  only non-M3-role color in the palette; everything else inherits compliant roles.

### Confirmed compliant (no action)

- **TrendBadge** pairs a glyph (↑ → ↓) with color in the string resources
  (`trend_up`/`trend_down`/`trend_flat`) — never color alone. ✅ (criterion 5)
- **Logging hot path sizes**: WeightStepper/RepsStepper shell 76dp with 56dp side
  buttons, numpad keys 56dp, LOG SET 60dp, Settings ActionRow 56dp. ✅ (criterion 1)
- **Set descriptions**: `LoggedSetRow` builds a full stateful summary ("Set 2
  logged: 85 kg, 8 reps, RPE 8.5, has note") and exposes a custom edit action;
  WeightStepper increment/decrement are stateful. ✅ (criterion 2)
- **Settings** uses `semantics { heading() }` and `selectableGroup()` correctly. ✅
- **Bottom nav** uses `NavigationBarItem` with text labels (M3 supplies the
  touch target + selected-state semantics). ✅

## 3. On-device verification (200% font + TalkBack)

> Device: `emulator-5554` (sdk_gphone16k, API 36, density 3.0). App installed
> from `installDebug`, `font_scale` set to `2.0`. Walked Home → start session →
> exercise picker → Active Session (steppers + numpad) → log a set. Accessibility
> tree read via `uiautomator dump`.

### 3.1 200% font scale (criterion 3) — ✅ PASS (hot path)

- **Active Session steppers**: the fixed-height 76dp shell holds the headline
  number and the unit label ("50" / "kg", "10" / "reps") with **no clipping** at
  200%; steppers stay side-by-side and the 1-tap path is intact. This **disproves**
  the static "StepperShell 200% clipping" concern (it was speculative; the headline
  + caption fit within 76dp even doubled).
- **Inline numpad**: the running value ("0 kg"), the quick chips (+10/+5/+2.5/−2.5
  on one row), all 12 keys, and Done render cleanly with no overflow at 200%.
- **Home / empty state**: body copy and the primary button wrap cleanly.
- **Minor observation (new):** the bottom-nav **"Analytics" label wraps to two
  lines** at 200% ("Analytic"/"s"). Cosmetic, not clipping — logged as **F-12 ·
  MINOR**. Consider `maxLines = 1` + `TextOverflow` or a shorter label at large scale.

### 3.2 TalkBack traversal order (criterion 4) — ✅ PASS

Accessibility-tree order on the active card (with one logged set) was, top to bottom:

1. `Back Extension` (exercise name)
2. `Set 1 logged: 50 kilograms, 10 reps` — **single merged node** (exemplary)
3. `Decrease weight, 2.5 kilograms` → `Weight 50 kg, tap to enter exactly` → `Increase weight, 2.5 kilograms`
4. `Decrease reps` → `Reps 10, tap to enter exactly` → `Increase reps`
5. `LOG SET`

This is an **exact match** to the spec order (name → ghost/logged → weight → reps
→ LOG SET); no extra `mergeDescendants` grouping is needed on the card. Confirmed
on-device:

- Numpad keys all carry descriptions ("Digit 5", "Add +10", "Backspace",
  "Decimal point", "Confirm"); top bar "Close session" / "Finish session". ✅
- Runtime sizes verified from node bounds: numpad keys & LOG SET = 168px (**56dp**),
  quick chips = 144px (**48dp**), stepper side buttons full-height. ✅
- **F-04 confirmed on-device**: weight stepper is stateful ("Decrease weight, 2.5
  kilograms") but **reps stepper is generic** ("Decrease reps" / "Increase reps").

## 4. Recommended fix batches

1. **PR-A — semantics (F-01, F-02, F-05):** merge clickable rows, label the
   clear-search button, add heading semantics. Highest TalkBack impact, low risk.
2. **PR-B — touch targets (F-07, F-08, F-09, F-10):** enforce ≥48dp minimums on
   secondary controls.
3. **PR-C — contrast (F-11):** fix the light `success` green; add a contrast note
   to [06-design-handoff](06-design-handoff.md).
4. **PR-D — charts & polish (F-03, F-04, F-06, F-12):** chart text alternatives,
   stateful reps descriptions, overflow context, bottom-nav label at large scale.

Dynamic type (criterion 3) and TalkBack order (criterion 4) on the logging hot
path **passed on-device** — no fixes required there beyond cosmetic F-12.

## 5. Resolution (branch `m5-a11y-fixes`)

All 12 findings addressed in a single PR (batches A–D collapsed into one branch):

| Finding | Fix |
|---|---|
| F-01 | `semantics(mergeDescendants = true)` on the clickable row root — Home recent, History card, Picker exercise row, Plans day row, Plan-editor day row, Analytics exercise row. |
| F-02 | `picker_clear_search` content description on the clear-search button. |
| F-03 | Overflow menu now reads "More options for {exercise}" (`session_overflow_for`). |
| F-04 | Reps stepper descriptions are stateful: "Increase/Decrease reps to {n}". |
| F-05 | `semantics { heading() }` on SessionDetail ExerciseHeader and the Analytics week header. |
| F-06 | `ProgressLineChart` takes a `contentDescription` ("{metric} progress chart, {n} sessions"); the inline Sparkline stays decorative (bare Canvas, no semantics node). |
| F-07 | Plan-editor: TargetStepper 46→48dp tall, −/+ buttons fill the 48dp height, remove button 40→48dp. **Caveat:** the −/+ buttons stay ~36dp **wide** — three steppers share one row, so a 48dp-wide button overflows; the touch target is 48dp in the vertical (swipe) axis only. Full 48×48 would need a targets-UI redesign (out of scope). |
| F-08 | Plans day "start" play button 44→48dp. |
| F-09 | Picker filter chips 36→48dp tall. |
| F-10 | Collapsed logged-set row `heightIn(min = 48.dp)`. |
| F-11 | Light `success` green #1E8E3E → #1B7A35 (~5.4:1, passes AA). |
| F-12 | Bottom-nav labels `maxLines = 1` + ellipsis (full label still spoken to TalkBack). |

Verification: `lint` + `testDebugUnitTest` + `assembleDebug` green; `CriticalLoggingPathTest`
and `TemplateStartPathTest` re-run on `emulator-5554` (the merges keep find-by-text
matchers resolving to the now-clickable merged row). The F-07 horizontal-target
limitation is the only residual, documented above.
