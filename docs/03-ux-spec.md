# 03 — UX Spec

> **Status:** Draft for review · 2026-06-07
> The data-entry experience **is** the product ([HANDOFF.md](../HANDOFF.md) §5). Layout direction for the logging screen ("exercise card stack") was selected by the project owner from three mocked alternatives in the design session.

## 1. Principles → mechanisms

| Principle (HANDOFF §5) | Mechanism in this spec |
|---|---|
| Minimum taps to log | Pre-fill everywhere; 1-tap LOG SET (§4, §5) |
| Big, thumb-reachable controls | ≥56dp logging controls, bottom-anchored actions, steppers + inline numpad — never the system keyboard (§4) |
| No flow-breaking modals | Everything inline on the session screen; RPE/notes via expanding row (§4) |
| Rest-timer-friendly | Timer **deferred to v2**; reserved slot directly under LOG SET (§4) |
| Resumable | Session + sets persist to Room on every action; Home shows a Resume card ([02-data-spec](02-data-spec.md) §3 `sessions.endedAt`) |
| Defaults that learn | Pre-fill rules in §4.2 |

## 2. Navigation map

```
            ┌─────────────────────────────────────────────┐
            │  Bottom navigation (4 destinations)          │
            │  Home │ Plans │ Analytics │ History          │
            └──┬───────┬─────────┬───────────┬─────────────┘
               │       │         │           │
               ▼       ▼         ▼           ▼
             Home    Plans    Analytics   History
               │       │ list    │ browser    │ list
               │       ▼         ▼            ▼
               │    Template   Exercise    Session
               │    editor     detail      detail
               │       │
               │       └──▶ Exercise picker ◀── (also from active session)
               ▼
        ╔═══════════════════╗
        ║  ACTIVE SESSION    ║   full-screen, above bottom bar (a mode, not a place)
        ╚═══════════════════╝
        Settings: gear icon in top app bar (units, theme, export/import)
```

**Screen inventory (8):** Home · Active Session · Exercise Picker (shared) · Plans list + Template editor · Analytics browser + Exercise detail · History list + Session detail · Settings. No onboarding screens — first launch lands on Home, usable immediately ([00-product-spec](00-product-spec.md) selling point 1).

## 3. Home

```
┌──────────────────────────────┐
│ LiftLog                   ⚙  │
│                              │
│ ╔══════════════════════════╗ │   shown ONLY when a session is live
│ ║ ▶ RESUME — Push Day      ║ │   (sessions.endedAt IS NULL);
│ ║   3 exercises · 24 min   ║ │   survives process death
│ ╚══════════════════════════╝ │
│  Start training              │
│ ┌──────────┐ ┌──────────┐    │   day templates of the most recently
│ │ PUSH DAY │ │ PULL DAY │    │   used plan, one-tap start
│ └──────────┘ └──────────┘    │
│ ┌──────────┐ ┌──────────┐    │
│ │ LEGS DAY │ │ + empty  │    │
│ └──────────┘ └──────────┘    │
│  Recent                      │
│  · Push Day — Fri, 5 Jun     │
│  · Legs Day — Wed, 3 Jun     │
│ ──────────────────────────── │
│  Home  Plans  Analytics  Hist│
└──────────────────────────────┘
```

## 4. Active Session — the make-or-break screen

### 4.1 Wireframe (exercise card stack)

```
┌──────────────────────────────┐
│ ✕  Push Day        41:32  ✔  │  ✕ discard (confirm) · ✔ finish session
│                              │
│ ┌──────────────────────────┐ │
│ │ ✓ Barbell Bench Press    │ │  COLLAPSED (done): tap to re-expand
│ │   82.5 kg × 5·5·4        │ │
│ └──────────────────────────┘ │
│ ╔══════════════════════════╗ │
│ ║ Incline DB Press   2/3 ⋮ ║ │  ACTIVE (expanded) — ⋮: remove/replace
│ ║ last: 30 kg × 10·10·8    ║ │  ghost row = last session (pre-fill src)
│ ║ ① 30 kg × 10          ✓  ║ │  logged rows; long-press → RPE/note
│ ║ ② 30 kg × 9           ✓  ║ │
│ ║ ┌────────┐  ┌────────┐   ║ │
│ ║ │− 30.0 +│  │−  8   +│   ║ │  steppers ≥56dp; tapping the NUMBER
│ ║ │   kg   │  │  reps  │   ║ │  opens inline numpad (§4.3)
│ ║ └────────┘  └────────┘   ║ │
│ ║ ┌──────────────────────┐ ║ │
│ ║ │      LOG SET         │ ║ │  primary, full-width, ≥56dp
│ ║ └──────────────────────┘ ║ │
│ ║ ┄┄┄ (reserved: v2 rest┄┄ ║ │  reserved rest-timer slot — empty in v1
│ ╚══════════════════════════╝ │
│ ┌──────────────────────────┐ │
│ │ Cable Fly       target 3×│ │  UPCOMING (collapsed): tap to activate
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │ + Add exercise           │ │  → Exercise picker (returns inline)
│ └──────────────────────────┘ │
└──────────────────────────────┘
```

Compose breakdown: `SessionScreen` → `LazyColumn` of `ExerciseCard(collapsed|active)`;
active card hosts `GhostRow`, `LoggedSetRow*`, `WeightStepper`, `RepsStepper`, `LogSetButton`.
One card is active at a time; logging the target number of sets auto-collapses it and
expands the next unfinished card (overridable by tapping any card).

### 4.2 Pre-fill rules ("defaults that learn")

Entry steppers are **always pre-filled**, priority order:

1. **Previous set of this session-exercise entry** — covers "log another set of what I just did" (and keeps duplicate entries — heavy vs. light bench — independent).
2. Else **same set-number from the last completed session** containing this exercise (ghost row source); if this session already has more sets than last time, use last session's final set.
3. Else (exercise never performed): weight empty + numpad auto-opens on first interaction, reps defaults to 10. LOG SET disabled until weight is set. This is the **only** situation requiring numeric entry.

Ghost row ("last: 30 kg × 10·10·8") gives the at-a-glance target without opening analytics.

### 4.3 Inline numpad (never the system keyboard)

Bottom-sheet-style pad replacing the stepper area inline (the card stays visible — not a dialog): large 4×3 grid (1-9, 0, ".", ⌫), quick chips (+10 / +5 / +2.5 / −2.5 in display unit), confirm. Stepper increments: 2.5 kg / 5 lb; reps step 1 ([02-data-spec](02-data-spec.md) §5).

### 4.4 Behaviors

- **LOG SET** (1 tap): writes the set to Room immediately (`completedAt = now`) — kill-safe from that instant; row appears above; steppers re-prime per §4.2.
- **Edit/RPE/note**: long-press a logged row → row expands inline: weight/reps editable, RPE chips (6–10 in 0.5 steps), note field, delete (soft). Collapses on tap-away. Never required, never on the hot path.
- **Targets**: template-started exercises show `2/3` set progress and target rep range as hint text in the reps stepper.
- **Finish (✔)**: sets `endedAt`, shows summary snackbar ("Push Day — 12 sets, 3 PRs"), returns Home. **Discard (✕)**: confirm dialog (the one acceptable dialog — destructive action), soft-deletes the session.
- **Backgrounding**: nothing to save — everything already persisted. Re-opening the app lands on the active session (via Home's Resume card or directly if it was the foreground screen).

### 4.5 Tap math (HANDOFF §5 walkthrough)

| Scenario | Taps |
|---|---|
| Another set, same weight/reps | **1** — LOG SET |
| Another set, +2.5 kg | **2** — stepper `+`, LOG SET |
| First set of next planned exercise | **2** — tap card (auto-expanded anyway after target sets → often just **1**), LOG SET |
| Cold start → first set of planned workout | **3** — app icon → template chip → LOG SET |
| Unplanned exercise mid-session | 3 + search — Add exercise → pick → LOG SET |

## 5. Analytics screens

(Validated as browser mockups in the design session; chart/metric definitions in [04-analytics-spec](04-analytics-spec.md).)

### 5.1 Exercise browser — "am I progressing?" at a glance

```
┌──────────────────────────────┐
│ Analytics                    │
│ ┌ This week: 3 sessions ───┐ │   header summary card
│ │ 86 sets · 14 250 kg      │ │   (04-analytics-spec §6, chart 3)
│ └──────────────────────────┘ │
│ 🔍 search                    │   sorted by most recently trained;
│ ┌──────────────────────────┐ │   only exercises with ≥1 logged set
│ │ Barbell Bench Press      │ │
│ │ e1RM 102.5 kg   ↑ +4.2%  │ │   trend badge (04-analytics-spec §3):
│ │            ▁▂▂▄▅▆▇       │ │   ↑ green / → neutral / ↓ red
│ ├──────────────────────────┤ │   + 90-day e1RM sparkline
│ │ Squat                    │ │
│ │ e1RM 142 kg     ↓ −2.1%  │ │
│ │            ▆▇▆▅▅▄▄       │ │
│ ├──────────────────────────┤ │
│ │ Deadlift                 │ │
│ │ not trained in 3 weeks   │ │   staleness state
│ └──────────────────────────┘ │
└──────────────────────────────┘
```

### 5.2 Exercise detail

```
┌──────────────────────────────┐
│ ← Barbell Bench Press        │
│ [e1RM] [Top set] [Volume]    │  metric chips (selected: filled)
│  30d │ 90d │ 1y │ all        │  range selector
│ ┌──────────────────────────┐ │
│ │        ·     ● PR        │ │  line chart, real time axis,
│ │    ·  ·  ·               │ │  gold dots = PRs, no interpolation
│ │ ·  ·                     │ │  (04-analytics-spec §6)
│ └──────────────────────────┘ │
│ e1RM 102.5 kg    ↑ +4.2%/90d │
│ RECENT SESSIONS              │
│ Mon 2 Jun — 85×5·5·4    PR   │  tap → session detail
│ Thu 29 May — 82.5×5·5·5      │
└──────────────────────────────┘
```

Bodyweight exercises (0 kg added) swap weight metrics for max-reps/total-reps chips ([04-analytics-spec](04-analytics-spec.md) §4).

## 6. Remaining screens (brief)

- **Exercise picker**: search-first list, filter chips (muscle group / equipment), recent on top; "+ create exercise" inline (name + group + equipment, 3 fields). Returns to caller — no nav stack detour.
- **Plans / template editor**: plan list → templates → editor: reorderable exercise list (drag handle), optional target sets/rep-range per row. Edits never touch past sessions ([02-data-spec](02-data-spec.md) §1).
- **History**: reverse-chronological session cards (name, date, sets, PR count) → session detail (read-only card stack; sets editable via the same long-press row, e.g. fixing a typo after the fact).
- **Settings**: unit toggle (kg/lb), theme (system/light/dark), export / import (SAF file picker), library version + licenses. One screen, no nesting.

## 7. Accessibility

- **Touch targets**: logging path ≥56dp (steppers, LOG SET, numpad keys); everything else ≥48dp (M3 minimum).
- **Content descriptions**: stateful and specific — "Increase weight, 2.5 kilograms", "Log set: 30 kilograms, 8 reps", "Set 2 logged: 30 kilograms, 9 reps, double-tap to edit".
- **Dynamic type**: logging controls must survive 200% font scale without clipping or losing the 1-tap path (steppers stack vertically if needed; test at 200% in the M2 Compose test).
- **TalkBack order** on the active card: exercise name → ghost row → logged sets → weight → reps → LOG SET.
- **Contrast**: M3 color roles only (dynamic color keeps roles compliant); trend badges pair color with a glyph (↑→↓) — never color alone.
- **No time-based UI** in v1 (timer deferred), so no timing-related a11y constraints yet.
