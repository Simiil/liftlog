# 04 — Analytics Spec

> **Status:** Draft for review · 2026-06-07
> All formulas are pure functions in `domain/analytics` ([01-architecture](01-architecture.md) §7) — exhaustively unit-tested against the fixtures in §5. Nothing here is implicit ([HANDOFF.md](../HANDOFF.md) §6).

## 1. Metric definitions

All metrics are computed **per exercise, per session**, from that session's live (`deletedAt IS NULL`) logged sets. Weights are canonical kg ([02-data-spec](02-data-spec.md) §5); conversion to display units happens after computation.

| Metric | Definition |
|---|---|
| **Top set** | `max(weightKg)` over the session's sets of that exercise (any reps ≥ 1) |
| **e1RM** | `max(e1rm(set))` over sets with `1 ≤ reps ≤ 12` (see §2) |
| **Volume** | `Σ (weightKg × reps)` over all sets |
| **PR** | A session value **strictly greater** than the same metric in *every* earlier session for that exercise (per metric: e1RM-PR, top-set-PR) |

All logged sets count in v1 — there is no warm-up flag yet (v2 candidate; noted in [05-roadmap](05-roadmap.md)).

## 2. e1RM formula — Epley, with guardrails

```
e1rm(weightKg, reps) =
    weightKg                          if reps == 1
    weightKg * (1 + reps / 30.0)      if 2 <= reps <= 12
    excluded from e1RM               if reps > 12
```

**Why Epley:**
- The de-facto standard (most lifters' mental model and most apps' default) — values are comparable to what users expect.
- Monotonic and well-behaved across the meaningful 1–12 rep range; trivially testable.
- Brzycki (`w × 36/(37 − r)`) was rejected: diverges sharply above ~10 reps and is undefined at r = 37; its only advantage (exact `w` at r = 1) is recovered by the explicit `reps == 1` case above.

**Why the 12-rep cap:** all linear 1RM estimators lose validity at high reps (error >10% beyond ~12). High-rep sets still count toward *volume* and *top set* — they're only excluded from e1RM.

## 3. Trend badge — "am I progressing on X?"

Computed over the trailing **90 days** of per-session e1RM points (bodyweight exercises: max-reps points, §4):

1. If the exercise has no set in the last **21 days** → badge = **stale** ("not trained in N weeks").
2. Else if **< 3 sessions** in the window → badge = **insufficient data**.
3. Else: ordinary least-squares fit `e1RM = a + b·t` (t in days) over the window's points.
   `percentChange = (f(t_end) − f(t_start)) / f(t_start) × 100` where `f` is the fitted line.
4. Classification: `↑` if > +1% · `↓` if < −1% · `→` otherwise.

Regression (not first-vs-last comparison) so a single bad day doesn't flip the badge; fitted-endpoint percent (not raw slope) so the number is human-meaningful ("+4.2% over 90 days").

## 4. Bodyweight exercises

For `equipment == BODYWEIGHT` **and** all sets at `weightKg == 0`, weight metrics are meaningless. These exercises swap their metric set:

| Replaced metric | Bodyweight metric |
|---|---|
| Top set / e1RM | **Max reps** (best single set) |
| Volume | **Total reps** |

A weighted bodyweight exercise (dips +20 kg) uses the normal weight metrics (weightKg = added load). Mixed history (some weighted, some not): weight metrics computed over weighted sets only; unweighted sets count in rep metrics. The UI surfaces whichever metric family has data ([03-ux-spec](03-ux-spec.md) §5.2).

## 5. Test fixtures (the contract for `domain/analytics` tests)

Hand-computed examples that MUST be encoded as unit tests at M4:

| Input sets (kg × reps) | Top set | e1RM | Volume |
|---|---|---|---|
| 100×5, 102.5×3, 95×8 | 102.5 | 116.67 (= 100·(1+5/30)) vs 112.75 (102.5) vs 120.33 (95·(1+8/30)) → **120.33** | 1567.5 |
| 60×15, 80×1 | 80 | **80** (15-rep set excluded; r=1 → w) | 980 |
| 0×12, 0×10 (bodyweight) | — | max reps **12** | total reps **22** |
| single session ever, any sets | every metric is a PR | | |

Trend fixtures: synthetic 90-day series with known slope (e.g. 100 → 104 linear ⇒ +4.0% ↑), a flat noisy series (⇒ →), a 2-session window (⇒ insufficient data), last point 30 days old (⇒ stale).

## 6. Chart-by-chart definitions

| # | Chart | Where | Series & axes | Specifics |
|---|---|---|---|---|
| 1 | **e1RM sparkline** | Analytics browser rows | 90d per-session e1RM; no axes, no labels | Renders ≥2 points; otherwise badge text only |
| 2 | **Progress line chart** | Exercise detail | x = real time (session `startedAt`), y = selected metric (e1RM / top set / volume — chips) | Ranges 30d/90d/1y/all. Points = sessions, straight segments between, **no interpolation/binning of gaps** (HANDOFF §6 sparse-data honesty). Gold markers on PR sessions. Y axis zoomed to data (not zero-based) for weight metrics; zero-based for volume |
| 3 | **Browser header card** | Top of Analytics browser | "This week": session count, set count, total volume vs. previous week | Pure aggregate, no chart in v1 |
| 4 | **Recent sessions list** | Exercise detail | Last 5 sessions: date, top sets summary, PR tag | Tap → session detail |

Empty states: never blank screens — "Log a session to see progress" (browser), "Need 2+ sessions for a chart" (detail).

## 7. Queries & performance (HANDOFF §6 requirement)

**Strategy: no pre-aggregation tables.** Justified by arithmetic: a heavy user logs ~5 sessions/week ≈ 260/year; a single exercise accrues ~100–150 sessions/year ≈ a few hundred set rows. The hot query (`AnalyticsDao.observeSetsForExercise`, [02-data-spec](02-data-spec.md) §4) is two indexed joins returning hundreds of rows — sub-millisecond in SQLite at any realistic scale. Pre-aggregation would add invalidation complexity for zero observable gain.

- **SQL fetches set-level rows; Kotlin reduces** to per-session metrics. Keeps formula logic (§1–§4) in tested pure functions, and SQL trivial.
- **Reactive**: queries are Room `Flow`s — logging a set mid-session updates any visible chart/badge automatically; `WhileSubscribed` stops collection when screens leave composition.
- **Downsampling** (the only big-data guard): when a range resolves to > 200 points (years of "all"), domain layer buckets by ISO week — max for e1RM/top-set, sum for volume — before charting. Pure function, fixture-tested.
- Trend badges on the browser compute from the same per-exercise flows, lazily per visible row (`LazyColumn`), so the browser never runs an all-exercises scan eagerly.
