# Muscle-balance radar chart: implementation design

> **Status:** Designed with owner 2026-07-10. Implements
> [issue #43](https://github.com/Simiil/liftlog/issues/43) — "radar chart
> analytics". Extends the M4 analytics layer
> (`docs/04-analytics-spec.md`, `docs/superpowers/specs/2026-06-09-m4-analytics-design.md`);
> no schema change, no new DAO query.

**Goal:** one glance at the Analytics tab answers "which muscle groups are
over/underrepresented in my training, and are they progressing?" — as a radar
chart whose **shape** is training dose and whose **vertex colors** are progress.

## Decisions taken this session

1. **Dose metric: sets per week**, not volume and not frequency. Volume
   (Σ weight×reps) is load-dependent (legs dwarf arms) and zeroes out
   bodyweight work (`weightKg == 0`); frequency ignores how much work a
   session contained. Hard-set counting is the standard normalization in
   hypertrophy literature and is the honest granularity our data supports
   (no per-set RPE, no warm-up flag: **a logged set = one unit of stimulus**).
2. **Progress as color-coded vertices**, not a second polygon and not a
   blended score. Spoke length and vertex color are independent channels;
   no unit-mixing. Colors reuse the existing trend-badge language.
3. **Normalization: dynamic rim + fixed target ring.**
   `rim = max(highest group's sets/week, TARGET)` with a dashed reference
   ring at `TARGET = 10` sets/week (evidence-based meaningful-dose landmark;
   a documented constant, candidate for user configuration post-v1). Shape
   shows imbalance; the ring gives rim-distance absolute meaning; no spoke
   ever clips.
4. **8 fixed spokes** (12 raw enum values would crowd the chart and
   manufacture fake "undertrained" alarms for isolation-only groups, since
   attribution is single-group): Chest, Back, Shoulders,
   **Arms** (BICEPS+TRICEPS+FOREARMS), Quads,
   **Hams & Glutes** (HAMSTRINGS+GLUTES), Calves, **Core** (ABS).
   `OTHER` is excluded from the chart and surfaced as a footnote.
5. **Placement: a card on the Analytics screen**, below the week-header
   card. No new destination, no new tab. A drill-down screen is a possible
   later enhancement.
6. **One time span for everything.** The card has its own range selector
   (reusing the `Range` enum: 30d/90d/1y/all, default `D90`); both dose and
   progress are computed over the selected span. No mixed windows.

## Metric semantics

- **Which sets count:** logged sets in finished sessions
  (`endedAt IS NOT NULL`), not soft-deleted, whose session `startedAt` falls
  inside the selected range — identical filter rules to all existing
  analytics.
- **Sets/week per group:**
  `count(sets in group in range) / effectiveWeeks`, where the **effective
  window** runs from `max(range cutoff, first-ever logged set)` to now,
  floored at 1 week — early training history isn't diluted by empty weeks,
  and `ALL` means "since I started logging".
- **Spoke fraction:** `setsPerWeek / rim` with
  `rim = max(maxGroup, TARGET_SETS_PER_WEEK = 10.0)`.
  Target ring radius = `TARGET / rim`.
- **Per-group trend:** for each exercise contributing sets to the group in
  range, build per-session points of its **headline metric** (e1RM for
  weighted, total reps for bodyweight — the existing swap in
  `ExerciseAnalytics.summarize`) and run the existing OLS `trend()` with
  `windowDays = selected range`. Existing guards apply (≥3 sessions in
  window, stale if last point > 21 days old). Group direction = set-count-
  weighted mean of the contributing exercises' percent changes, thresholded
  at the existing ±1% → `UP` / `FLAT` / `DOWN`; if **no** contributing
  exercise has a valid trend → no-trend (hollow neutral vertex).

## Architecture (top-down)

UI ↔ ViewModel `StateFlow` ↔ Repository ↔ Room, formulas in pure
`domain/analytics` functions with `nowMillis` as a parameter — the M4
pattern throughout.

### `domain/analytics/MuscleBalance.kt` (pure; tests written first)

- `enum class RadarGroup { CHEST, BACK, SHOULDERS, ARMS, QUADS, HAMS_GLUTES, CALVES, CORE }`
  plus `fun MuscleGroup.toRadarGroup(): RadarGroup?` (`OTHER → null`).
- Input row: set data joined with its exercise's `muscleGroup` + `equipment`
  (shape mirrors `AnalyticsDao.SetRow` + exercise metadata).
- `fun muscleBalance(rows, rangeCutoffMillis, nowMillis): MuscleBalance` →
  per-group `setsPerWeek` + trend direction (nullable), `rimValue`,
  `targetFraction`, `unclassifiedSetCount`, `isEmpty`.
- `const val TARGET_SETS_PER_WEEK = 10.0`.

### Repository

`AnalyticsRepository.observeSetsWithExercise(): Flow<List<SetWithExercise>>`
— `combine(analyticsDao.observeAllSetsSince(0), exerciseRepository.observeAll())`,
join on `exerciseId`, computed on `Dispatchers.Default`. Copy of the
established `observePrSessionIds` pattern in `AnalyticsRepositoryImpl`.

### ViewModel

Extend the existing `AnalyticsViewModel`:
`MutableStateFlow<Range>` (default `D90`) `combine`d with
`observeSetsWithExercise()`, mapped through `muscleBalance(...)` into
`MuscleBalanceUiState` (list of spokes: label res + fraction + vertex color
role; footnote count; empty flag). Cutoff derivation mirrors
`ExerciseDetailViewModel`'s `nowFallbackCutoff`.

### UI

- **`ui/components/charts/RadarChart.kt`** — dumb custom-`Canvas` composable
  (Vico is Cartesian-only; `Sparkline.kt` is the hand-drawn precedent).
  Takes `List<RadarSpoke(label, fraction 0..1, vertexColor)>` +
  `targetRingFraction`. Draws 2–3 faint concentric grid rings, 8 spokes,
  the dashed target ring (`DashedBorder.kt` precedent), the translucent
  filled + solid-stroked dose polygon, trend-colored vertex dots, labels
  outside the spoke tips. `contentDescription` summarizes the data
  ("Chest 12 sets per week, trending up; …") per the `ProgressLineChart`
  convention.
- **`ui/analytics/MuscleBalanceCard.kt`** — title, range pills (same style
  as exercise detail), chart, one-line legend
  (`● up · ● down · ● flat · ○ no data · ┄ target 10 sets/week`),
  conditional footnote. Slotted into `AnalyticsScreen` below the week card.
- **Spoke order:** Chest at 12 o'clock, clockwise: Chest, Shoulders, Arms,
  Quads, Calves, Hams & Glutes, Core, Back (push right, pull/posterior left).
- **Colors:** Material 3 scheme roles only (dark mode for free). Vertex
  up = extended `success`, down = `error`, flat = neutral
  (`onSurfaceVariant`) **filled**, no-trend = neutral **hollow** (outline
  only) — flat means "trained but plateaued", hollow means "can't tell";
  the fill/hollow glyph distinction keeps this color-blind-safe, matching
  the badge-plus-glyph a11y approach from M4. Polygon fill/stroke =
  `primary` at reduced alpha / `primary`.

### i18n

All new strings in `values/strings.xml` **and** `values-de/strings.xml`
(M6 in progress — new UI ships bilingual). Spoke labels via string
resources keyed off `RadarGroup`, never enum `.name`.

## Edge cases

| Case | Behavior |
|---|---|
| No sets in selected range | Empty state in card ("Log workouts to see your muscle balance"); no chart drawn |
| Group with zero sets | Spoke length 0 (vertex at center), hollow neutral vertex |
| Group trained, trend invalid (<3 sessions / stale) | Normal spoke length, hollow neutral vertex |
| Bodyweight-only group | Full dose credit; trend from total-reps headline metric |
| `OTHER` sets in range | Footnote "N sets from unclassified exercises not shown" |
| In-progress session | Excluded (`endedAt IS NULL`) |

## Testing

- **`domain/analytics/MuscleBalanceTest.kt`** (unit, written first): group
  mapping incl. `OTHER → null`; sets/week with effective-window rule
  (early-history case, 1-week floor); rim/target normalization below and
  above 10 sets/week; set-weighted trend aggregation (mixed up/down,
  all-invalid → gray); empty input.
- **ViewModel test:** range switching recomputes state; empty flag.
- **UI:** no new instrumented test — rendering over tested state; verified
  on-device (emulator) before the PR.

## Out of scope (recorded, not precluded)

- Secondary/synergist muscle attribution (needs a data-model change:
  new field, migration from schema v2, seed re-authoring, backup codec).
- User-configurable per-muscle targets (zero-setup constraint; the `10.0`
  constant is the hook).
- Drill-down "Muscle balance" screen with per-group breakdown.
- Macro ↔ detailed spoke toggle.
