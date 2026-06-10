# PR chips on Home & History — design

- **Date:** 2026-06-10
- **Status:** design approved in discussion; spec pending owner review
- **Decisions (owner):** simple boolean "PR" chip (no count, no exercise names); keep current
  first-session-counts-as-PR semantics; approach A (repository one-pass scan reusing
  `summarize()`), with the scan moved off the main thread via `flowOn`.

## Goal

Surface personal records outside the Analytics tab: a session card in the Home view's
"recent workouts" list and a row in the History view get a small "PR" chip when that session
set at least one personal record.

## Background (current behavior, unchanged)

PRs are derived on read, never stored. `domain/analytics/ExerciseAnalytics.kt#summarize()`
walks one exercise's sessions chronologically keeping running bests; a session's headline
`isPr` is the e1RM PR for weighted exercises and the max-reps PR for bodyweight ones
(equipment == `BODYWEIGHT` and all logged weights are 0). The Epley e1RM excludes sets over
12 reps. Ties are not PRs (strict `>`). The first-ever session of an exercise is always a PR
(it beats "nothing") — deliberately kept, consistent with the Exercise Detail screen.

**Chip rule:** a session shows the chip iff at least one exercise in it has headline
`isPr == true` for that session.

## Changes

### 1. Data layer (projection only — no schema change, no migration)

`data/dao/Relations.kt` `SetRow` gains `exerciseId: String`. Both `AnalyticsDao`
queries (`observeSetsForExercise`, `observeAllSetsSince`) add `se.exerciseId AS exerciseId`
to their SELECT — the `session_exercises` join is already there. Existing callers ignore the
new field.

### 2. Domain: pure PR-session function

New file `domain/analytics/PrSessions.kt`:

```kotlin
fun prSessionIds(
    setsByExercise: Map<String, List<DatedSet>>,  // exerciseId → its sets (unsorted ok)
    equipmentById: Map<String, Equipment>,
    nowMillis: Long,
): Set<String>
```

For each exercise: call the existing `summarize(equipment, sets, nowMillis)` and collect
`sessions.filter { it.isPr }.map { it.sessionId }`; union across exercises. Exercises missing
from `equipmentById` (e.g. soft-deleted) are skipped — mirrors the detail screen, which has
no summary for them either. `summarize()` itself is untouched, so Home/History chips agree
with the Analytics screen by construction.

### 3. Repository

`domain/repository/AnalyticsRepository` gains:

```kotlin
fun observePrSessionIds(): Flow<Set<String>>
```

`AnalyticsRepositoryImpl`: combine `analyticsDao.observeAllSetsSince(0L)` with
`exerciseRepository.observeAll()`; group rows by `exerciseId`, map to `DatedSet`, delegate to
`prSessionIds(…, clock.millis())`. Apply **`.flowOn(Dispatchers.Default)`** so the scan never
runs on the main thread (the combine/map transform otherwise executes in the collector's
context, which is `viewModelScope`'s main dispatcher).

### 4. ViewModels

- `RecentSessionUi` (Home) and `HistorySessionUi` (History) gain `val isPr: Boolean = false`.
- `HomeViewModel` and `HistoryViewModel` inject `AnalyticsRepository`, add
  `observePrSessionIds()` to their existing `combine`, and set
  `isPr = session.id in prIds`.
- Home's `combine` grows from 5 to 6 flows — switch to the array/vararg overload.

### 5. UI

New shared composable `ui/components/PrBadge.kt`: bold 12sp text "PR" in
`MaterialTheme.colorScheme.tertiary`, string `R.string.analytics_pr`. Used in three places:

1. `ExerciseDetailScreen` session-history rows — replaces the existing inline `Text`.
2. Home recent-workout card — trailing position.
3. History row — trailing position.

## Edge cases

- **Active sessions:** never flagged — the DAO query filters `endedAt IS NOT NULL`, and both
  screens already show finished sessions only.
- **Sessions with zero sets:** no chip.
- **Stability:** running-max flags for old sessions don't change when later sessions beat
  them; set edits/deletes/backfills produce correct flags on the next read (nothing stored).
- **Soft-deleted exercises:** their sets produce no chips (skipped group).

## Performance

A full-history scan per emission is inherent (PR = running max over everything) and cheap:
~30k set rows after five heavy training years → millisecond-scale SQLite query plus a
low-tens-of-ms Kotlin pass, off the main thread via `flowOn`. Room invalidation is
table-level, so every set insert re-triggers the flow — but `WhileSubscribed(5_000)` means
Home/History collectors are inactive while the user is on the Active Session screen, so the
hot logging path never pays the recompute; it runs once on navigating back.

Future levers if profiling ever demands (explicitly **not now**): `shareIn` on the singleton
repository to dedupe Home+History subscriptions; factoring a lighter PR-only scan out of
`summarize()` (it currently also computes trend data the chip doesn't need).

## Testing

- JVM unit tests for `prSessionIds`: union across exercises; bodyweight reps-PR branch; ties
  don't flag; first session flags; unknown exercise skipped; empty input.
- ViewModel tests (Home + History): card models carry `isPr` for the flagged session ids.
- Existing `summarize()` tests untouched.

## Out of scope

PR counts or exercise names on the chip; chips on the Session Detail screen; persisting PR
flags; any change to PR semantics.
