# M4 — Analytics: implementation design

> **Status:** Approved by owner 2026-06-09. Translates the already-approved
> `docs/04-analytics-spec.md` (formulas, fixtures, chart defs) and
> `docs/03-ux-spec.md §5` (browser + detail layout) into concrete Kotlin
> architecture, plus the two implementation decisions taken this session.

**Goal:** "The logging habit pays off." Ship the Analytics tab: an exercise
**browser** (this-week summary card, search, per-exercise current value + trend
badge + 90-day sparkline) and an exercise **detail** screen (metric chips, range
selector, progress line chart with PR markers, recent-sessions list) — all driven
by exhaustively-tested pure formulas over existing Room data.

**Reference design:** the Claude Design bundle
(`https://api.anthropic.com/v1/design/h/3U_t049_jROWVbFA-QKfpA`) — `analytics.jsx`,
`data.js` (reference implementation of every formula), and the `index.html` M3
stylesheet. Design px → Compose dp 1:1; `--md-*` vars map 1:1 to
`MaterialTheme.colorScheme` roles.

## Decisions taken this session

1. **Charting: Vico** (`com.patrykandpatrick.vico`), behind a `ui/components/charts`
   wrapper. Keep the config **simple** — basic line chart now, refine later. Do not
   over-invest fighting the library to match every design pixel.
2. **Perf check: a debug-only synthetic-history seeder** gated on `BuildConfig.DEBUG`,
   so the exit-criterion perf check ("full seed library + a year of synthetic data")
   is a reproducible artifact compiled out of release builds.
3. **Chart accent from the color scheme, not gold.** Standalone gold clashes with the
   seed palette. PR markers and the recent-list "PR" tag use **`tertiary`** (M3's
   harmonizing accent); regular data points stay hollow (`surface` fill, `primary`
   stroke). The `gold` color is removed. Trend badges keep semantic green
   (`success`, an extended color) / red (`error`), each paired with a glyph (a11y §7).

## Architecture (top-down)

UI ↔ ViewModel `StateFlow` ↔ Repository ↔ Room. ViewModels never touch Room or DAOs
directly. All formula logic lives in pure `domain/analytics` functions (no Android
deps), tested first against `docs/04-analytics-spec.md §5` fixtures.

### Layer 1 — `domain/analytics/` (pure functions; **tests written first**)

Faithful port of `data.js`. `nowMillis` is always a **parameter** (deterministic tests).

- **`Epley.kt`** — `fun e1rm(weightKg: Double, reps: Int): Double?`
  - `reps == 1` → `weightKg`; `2..12` → `weightKg * (1 + reps / 30.0)`; `> 12` → `null`.
- **`SessionMetrics.kt`**
  - `data class SetEntry(val weightKg: Double, val reps: Int)`
  - `data class SessionMetrics(val topSetKg: Double, val volumeKg: Double, val e1rmKg: Double, val maxReps: Int, val totalReps: Int)`
  - `fun sessionMetrics(sets: List<SetEntry>): SessionMetrics` — top = max weight; volume = Σ(w·r); e1rm = max over non-null `e1rm`; maxReps/totalReps over all sets.
- **`Trend.kt`**
  - `enum class TrendDirection { UP, FLAT, DOWN }`
  - `data class TrendPoint(val timeMillis: Long, val value: Double)`
  - `sealed interface TrendResult { data class Ok(val percent: Double, val direction: TrendDirection) : TrendResult; data class Stale(val weeks: Int) : TrendResult; data object Insufficient : TrendResult }`
  - `fun trend(points: List<TrendPoint>, nowMillis: Long): TrendResult` — last point > 21d old → `Stale(round(daysSince/7))`; window (trailing 90d of the last point) < 3 → `Insufficient`; else OLS `y = a + b·t` (t in days), `percent = (f(tEnd) − f(tStart)) / f(tStart) · 100`, dir UP > +1% / DOWN < −1% / FLAT.
- **`Downsample.kt`**
  - `enum class Aggregation { MAX, SUM }`
  - `fun downsample(points: List<TrendPoint>, aggregation: Aggregation, maxPoints: Int = 200): List<TrendPoint>` — when `points.size > maxPoints`, bucket by ISO week (MAX for e1RM/top-set, SUM for volume); bucket timestamp = bucket's last point. Pure, fixture-tested.
- **`ExerciseAnalytics.kt`** — the assembler
  - `data class DatedSet(val sessionId: String, val startedAt: Long, val weightKg: Double, val reps: Int)` (1:1 with `AnalyticsDao.SetRow`)
  - `data class SessionPoint(val sessionId: String, val timeMillis: Long, val sets: List<SetEntry>, val metrics: SessionMetrics, val primary: Double, val isPrE1rm: Boolean, val isPrTopSet: Boolean, val isPrReps: Boolean, val isPr: Boolean)`
  - `data class ExerciseSummary(val bodyweight: Boolean, val sessions: List<SessionPoint>, val trend: TrendResult, val currentValue: Double, val lastTrainedAt: Long)`
  - `fun summarize(equipment: Equipment, sets: List<DatedSet>, nowMillis: Long): ExerciseSummary?` — null if no sets. Groups rows by `sessionId` (input ordered by `startedAt`), computes per-session metrics, PR flags (strictly greater than ALL earlier sessions per metric), bodyweight swap (`equipment == BODYWEIGHT && every set weightKg == 0` ⇒ primary = maxReps, PR on reps), trend over primary points. `currentValue` = last session's primary.

### Layer 2 — `data/dao/AnalyticsDao.kt` (one query exists; add two)

- *(exists)* `observeSetsForExercise(exerciseId, fromMillis): Flow<List<SetRow>>` — detail.
- **add** `observeAllSetsSince(fromMillis): Flow<List<SetRow>>` — every live set in a
  **completed** session since `fromMillis` (same joins, no `exerciseId` filter). Drives
  the week summary (Kotlin reduces). `SetRow.sessionId`+`startedAt` are sufficient to
  group/aggregate.
- **add** `observeTrainedExercises(): Flow<List<TrainedExerciseRow>>` —
  `data class TrainedExerciseRow(val exerciseId: String, val lastTrainedAt: Long)`;
  `SELECT se.exerciseId, MAX(s.startedAt) ... WHERE completed & live sets GROUP BY se.exerciseId`.
  Light query for the browser list/sort; per-row summaries are computed lazily.

New row types live in `data/dao/Relations.kt` beside the existing `SetRow`.

### Layer 3 — `domain/repository/AnalyticsRepository` (+ `data/repository/AnalyticsRepositoryImpl`)

```kotlin
interface AnalyticsRepository {
    fun observeWeekSummary(): Flow<WeekSummary>
    fun observeTrainedExercises(): Flow<List<TrainedExercise>>
    fun observeExerciseSummary(exerciseId: String): Flow<ExerciseSummary?>
}
data class WeekSummary(val sessions: Int, val sets: Int, val volumeKg: Double, val prevVolumeKg: Double)
data class TrainedExercise(val id: String, val name: String, val muscleGroup: MuscleGroup, val equipment: Equipment, val lastTrainedAt: Long)
```

- **`observeWeekSummary`** — `observeAllSetsSince(start of *previous* calendar week)` reduced
  into this-week vs last-week buckets. Week boundary = ISO week (Monday 00:00) in the
  device zone, from an **injected `java.time.Clock`** (Hilt-provided
  `Clock.systemDefaultZone()`; tests inject a fixed clock). `sessions` = distinct
  sessionIds this week; `sets` = count this week; volume = Σ(w·r) per week.
- **`observeTrainedExercises`** — `combine(dao.observeTrainedExercises(), exerciseRepository.observeAll())`,
  join on id for name/group/equipment, sort `lastTrainedAt` DESC.
- **`observeExerciseSummary`** — `combine(dao.observeSetsForExercise(id, 0), exerciseRepository.observeAll())`
  → `summarize(equipment, rows.map{DatedSet}, clock.millis())`. The **heavy** flow; collected
  lazily per visible browser row and by the detail screen.

Hilt: `AnalyticsRepositoryImpl` bound in the existing repository module; `Clock` provided
in a DI module.

### Layer 4 — `ui/analytics/`

- **`AnalyticsBrowserScreen` + `AnalyticsBrowserViewModel`**
  - State: `weekSummary: WeekSummary?`, `query: String`, `exercises: List<TrainedExercise>` (filtered by name, case-insensitive).
  - Week card (`surfaceContainerHigh`, radius 22): "This week" head; 3 stats (sessions / sets / volume as `{t}t`); delta line `±N% volume vs last week` (`success`/`error` + ↑/↓ glyph).
  - Search bar (pill, `surfaceContainerHigh`-ish, search icon + `TextField`).
  - `LazyColumn` of rows. **Each row composable collects `observeExerciseSummary(id)` itself**
    (VM exposes `fun summary(id): Flow<ExerciseSummary?>`, `shareIn`/cached), so only visible
    rows compute (spec §7 laziness). Row: name (16/500); sub-line = current metric
    (`e1RM {w} {unit}` or `{reps} reps`) + `TrendBadge`; trailing `Sparkline` (hidden when
    stale or < 2 points — badge text only). Tap → detail.
  - Empty state (no trained exercises): centered "Log a session to see progress."
- **`ExerciseDetailScreen` + `ExerciseDetailViewModel`** (`exerciseId` from `SavedStateHandle`)
  - Top bar: back + exercise name (no bottom bar — not a top-level route).
  - Metric chips: weighted → `[e1RM] [Top set] [Volume]`; bodyweight → `[Max reps] [Total reps]`. Selected = `secondaryContainer` + check icon.
  - Range row: `30d / 90d / 1y / all`; selected = `primary`/`onPrimary`.
  - Chart card (`surfaceContainerHigh`, radius 22) → `ProgressLineChart`.
  - `detail-metric`: current value (30sp/600) + large `TrendBadge` (weighted only).
  - "Recent sessions" section: last 5 (reverse-chron) — date, top-sets summary
    (`{topW} {unit} × r·r·r` or `{maxReps} reps best`), `tertiary` "PR" tag when `isPr`;
    tap → existing `SessionDetailRoute`.
  - Range filter + metric select happen in the VM over the summary; `< 2` points in range
    falls back to the last 2 sessions (per `analytics.jsx`); `> 200` points → `downsample`.
  - Empty state (< 2 sessions ever): "Need 2+ sessions for a chart."

### Layer 5 — `ui/components/charts/` (Vico, simple config)

- **`ProgressLineChart(points, prFlags, zeroBased)`** — Vico `CartesianChart` line. Line =
  `primary`. Point markers: regular = hollow (`surface` fill, `primary` stroke); PR =
  filled `tertiary`, larger. Time x-axis (first/last date labels), y zoomed to data unless
  `zeroBased` (volume/total-reps). Gold removed. Keep config minimal — PR markers / zoom
  applied only as far as Vico makes straightforward; anything fiddly is deferred (noted as
  follow-up, not blocking).
- **`Sparkline(points, color)`** — minimal Vico line, axes/guidelines hidden; `color` =
  `error` when trend down else `primary`. ⚠️ **Perf watch:** per-row Vico charts in the
  `LazyColumn` are evaluated against the smooth-scroll exit criterion. If they regress
  scrolling with a year of data, a Compose `Canvas` polyline behind the same wrapper is the
  documented fallback (follow-up, still behind `ui/components`).
- Version catalog: add `vico` (latest stable 2.x; verify the build resolves and pin it).

### Layer 6 — supporting

- **`ui/theme`** — `LiftLogExtendedColors(val success: Color)` + `LocalLiftLogColors`
  `CompositionLocal`; provided for light/dark in `LiftLogTheme` (baseline M3 has `error`
  but no green). Up-trend badge uses `success`; PR accent uses scheme `tertiary` (no new
  color). Values: a readable green per scheme (light ≈ green 600, dark ≈ green 300).
- **`data/seed/SyntheticHistorySeeder`** — debug-only (`BuildConfig.DEBUG`). Mirrors
  `data.js gen()`: for a handful of seed exercises, generate ~1 year of dated, **completed**
  sessions (sessionExercise + logged sets), inserted via existing DAOs. Triggered from a
  `BuildConfig.DEBUG`-gated "Seed demo data" button in Settings; no-op/absent in release.
- **Navigation** — add `@Serializable data class AnalyticsExerciseDetailRoute(val exerciseId: String)`;
  `AnalyticsScreen` (browser) gains `onOpenExercise`; detail composable wired with back +
  recent-tap → `SessionDetailRoute`. Bottom bar auto-hides (route not in `topLevelDestinations`).
- **`res/values/strings.xml`** — analytics labels/plurals (week head/stats/delta, search hint,
  `e1RM`/metric labels, range labels, recent header, trend badge strings: "not trained in %d wk",
  "need 3+ sessions", PR, empty-state copy). Reuse existing `set_count` plural where possible.

## Testing

- **Unit (written first):** §5 fixtures — `e1rm` (3 cases incl. >12 excluded), `sessionMetrics`
  (the two worked examples), PR detection (single-session-ever ⇒ all PRs), `trend` (4 cases:
  +4% ↑, flat noisy →, 2-session ⇒ Insufficient, 30-day-old ⇒ Stale), `downsample` (>200 →
  ISO-week buckets, MAX vs SUM), bodyweight swap.
- **DAO instrumented** (emulator-5554 / CI API 34): extend `AnalyticsDaoTest` — `observeAllSetsSince`
  (completed-only, soft-delete excluded, window boundary) and `observeTrainedExercises`
  (grouping, MAX(startedAt), excludes in-progress).
- **Repository** (fakes + fixed `Clock`): week summary this-vs-prev split; trained-exercise join+sort;
  summary assembly.
- **ViewModel:** browser (search filter, empty state, week card) and detail (metric/range select,
  `< 2`-point fallback, bodyweight metric set, empty state).
- **Compose UI** (optional smoke, local emulator per `local-emulator-compose-ui-test`): browser shows
  week card + a row; tapping a row opens detail.

## Out of scope (per spec / roadmap)

Warm-up flag, pre-aggregation tables, settings unit toggle (M5), export/import (M5), any cloud.

## Delivery

One `m4-analytics` branch → one PR (multiple commits, TDD). Owner reviews, merges, and removes
the worktree. Exit criteria (roadmap M4): all formula/trend/downsampling fixtures green; charts
live-update while logging; browser scrolls smoothly with full seed library + a year of synthetic
data (manual on-device perf check).
