# Project Handoff: "LiftLog" — Offline Weightlifting Training Diary

> **For:** Claude Code
> **From:** Project owner
> **Status:** Pre-implementation. Your first deliverables are *design documents and specifications*, not code.

---

## 0. How to use this document

This is the brief. **Do not start writing application code yet.** Your first job is to turn this brief into a set of design and specification documents (listed in §8), get them reviewed, and only then begin implementation in a later session.

Work in this order:
1. Read this entire document.
2. Ask clarifying questions where something is genuinely ambiguous or underspecified (see §9 for known open questions — start there).
3. Produce the design-doc set in §8 as Markdown files under `/docs`.
4. Stop and request review before any implementation.

Treat every "decision" in §2 as fixed unless you flag a concrete problem with it. Treat everything in §9 as open for you to propose answers to.

---

## 1. Product summary

**LiftLog** is an Android app for tracking weightlifting workouts. A user records what they lifted (weight, reps, sets) on which exercise or machine, follows a training plan that says which exercises to do on which day, and views analytics showing progress over time.

### Core features (v1 scope)
1. **Training diary** — log weight / reps / sets per exercise per session.
2. **Training plan** — define which exercises belong to which workout day; use the plan to pre-populate a session.
3. **Analytics** — progress charts per exercise (e.g. weight and volume over time) and simple summaries.

### Selling points (these are the design north stars — optimize for them)
1. **No cloud, no account. Install and go.** Zero onboarding friction. The app is fully functional the moment it's installed, with no sign-up, no network call, no permissions prompts beyond what's strictly required (the one optional prompt: the in-workout notification permission, issue #36 — deniable, one-time).
2. **Ultra-fast data entry that doesn't break training stride.** Logging a set mid-workout must be near-instant: minimal taps, large touch targets, sensible defaults, no modal dialogs that interrupt flow. A user with sweaty hands, resting 60 seconds between sets, should be able to log without thinking.

### What v1 is NOT
- No social features, sharing, or leaderboards.
- No account, login, or cloud sync (but see §2 — architect so sync *could* be added later).
- No coaching/AI features, no exercise video library.
- No wearable/health-platform integration (Health Connect) in v1 — note it as a future candidate.
- Not a general fitness app (no cardio/nutrition tracking). Strictly resistance-training logging.

---

## 2. Fixed technical decisions

These are decided. Build the design around them.

| Area | Decision | Rationale |
|---|---|---|
| Platform | **Native Android, Kotlin + Jetpack Compose** | Best data-entry UX, widgets, performance, lowest long-term risk; Android-only is fine. |
| Min SDK | Propose one in the design doc (lean modern, e.g. API 26+) | Balance reach vs. modern APIs; justify the choice. |
| Persistence | **Room** (SQLite) as the single source of truth | Offline-first, mature, integrates with Coroutines/Flow. |
| Architecture | MVVM + unidirectional data flow; repository pattern | UI ↔ ViewModel (`StateFlow`) ↔ Repository ↔ Room. |
| Async | Kotlin Coroutines + Flow | Standard; pairs with Compose recomposition. |
| DI | Hilt | Standard for native Android. |
| Design system | Material 3 / Material You | Native look, dynamic color, accessibility for free. |
| Charts | Compose-native charting (evaluate options in design doc — e.g. Vico, or hand-rolled Canvas) | No heavy/abandoned dependencies. |
| Cloud | **None in v1**, but **architect so cloud sync can be added later** | See §3 — this is a hard constraint on the data layer. |

### The "sync-ready but sync-free" constraint (important)
v1 ships with **no network code and no backend**. But the data model and architecture must not paint us into a corner. Specifically, the design doc must show how the schema and repository layer would accommodate a future sync engine **without a rewrite**. Concretely, account for:
- **Stable, globally-unique IDs** for every entity (UUIDs, not just autoincrement ints) so records can later be reconciled across devices.
- **Timestamps** (`createdAt`, `updatedAt`) on syncable entities.
- **Soft-delete** capability (a `deletedAt`/`isDeleted` flag) rather than hard deletes, so deletions can propagate later.
- A **repository abstraction** that a future remote data source could slot behind, so ViewModels never talk to Room directly.
- Keep all of this **invisible and zero-cost to the v1 user** — no sync UI, no account, no nags. It's purely an architectural insurance policy.

Do **not** build the sync engine. Just don't preclude it.

---

## 3. Data export / backup (v1)

Because there's no cloud, the user's data lives only on their device — which is a data-loss risk. v1 must include **local export/import**:
- Export the full database to a single user-accessible file (propose format — JSON is the obvious candidate; justify).
- Import/restore from that file.
- This doubles as a manual "backup" and a manual "move to new phone" path.
- Design the export format to be human-readable and forward-compatible (versioned).

Specify this fully in the data spec.

---

## 4. Domain model (starting point — refine in the spec)

This is a first sketch, not the final schema. The data spec should formalize, correct, and extend it.

- **Exercise** — a movement or machine. e.g. "Barbell Bench Press", "Leg Press (Machine 4)". Has a name, a category/muscle group, and a type (barbell / dumbbell / machine / bodyweight / cable). Users can create custom exercises. Ship a sensible built-in starter list.
- **WorkoutPlan** — a named plan (e.g. "Push/Pull/Legs").
- **PlanDay** — a day within a plan (e.g. "Push Day"), containing an ordered list of exercises (with optional target sets/reps).
- **Session** — an actual training session on a date, optionally based on a PlanDay. Contains the logged work.
- **LoggedSet** — one set: links to an Exercise within a Session, with weight, reps, and optionally RPE/notes. The atomic unit of the diary.

Think carefully about:
- The relationship between *planned* exercises and *logged* sets (a session may deviate from its plan — that must be fine).
- Units (kg vs lb) — store canonically, display per user preference. Specify how.
- How "progress" is derived for analytics (e.g. estimated 1RM, top set, total volume per exercise per session).

---

## 5. UX principles (the data-entry bar)

The data-entry experience is the product. The UX spec must demonstrate, screen by screen, how these are met:
- **Minimum taps to log a set.** Walk through the exact tap sequence for "log another set of what I just did" — it should be ~1–2 taps with weight/reps pre-filled from the previous set.
- **Big, glanceable, thumb-reachable controls.** Steppers and quick-adjust buttons over free-text keyboards where possible. Numeric entry should use a custom fast input, not the default soft keyboard, where it helps.
- **No flow-breaking modals.** Logging happens inline.
- **Rest-timer-friendly.** Consider (don't necessarily build) an auto-rest-timer after logging a set.
- **Resumable.** If the app is backgrounded mid-session (it will be — phone goes in pocket), state is never lost.
- **Defaults that learn.** Pre-fill from the last time this exercise was performed.

Produce **low-fidelity wireframes** (described in text/ASCII or as a Compose-component breakdown) for the active-logging screen specifically, since it's the make-or-break surface.

---

## 6. Analytics scope (v1)

Rich analytics is a stated priority. Specify, at minimum:
- Per-exercise progress over time: top-set weight, estimated 1RM, total volume.
- A way to see "am I progressing on X?" at a glance.
- Sensible handling of sparse/irregular data (people skip workouts).
- Performance: charts must render smoothly even with months of history. Specify how queries are kept cheap (pre-aggregation? indexed queries? Flow-backed reactive updates?).

Define the exact metrics and their formulas (e.g. which 1RM estimation formula — Epley, Brzycki — and why) in the spec. Don't leave formulas implicit.

---

## 7. Quality, testing, and tooling expectations

The design docs should establish, and later implementation should follow:
- **Testing strategy:** unit tests for domain logic and analytics formulas (these are pure functions — high-value, easy wins); Room DAO tests; Compose UI tests for the critical logging flow.
- **Module structure:** propose a package/module layout (e.g. `data`, `domain`, `ui`, `analytics`) consistent with the repository pattern.
- **No premature dependencies:** every third-party library must be justified. Prefer Jetpack and well-maintained libraries; avoid anything abandoned or that fights the offline/sync-ready constraints.
- **Accessibility:** content descriptions, touch-target sizes, dynamic type — call these out in the UX spec.
- **CI:** propose a minimal GitHub Actions setup (build + test) in the architecture doc; don't implement yet.

---

## 8. Your deliverables (first session) — design docs only

Produce the following as Markdown under `/docs`. **No application code.** Keep each focused; cross-reference rather than repeat.

1. **`docs/00-product-spec.md`** — Refined product spec: finalized v1 scope, explicit non-goals, user stories for the three core features, and how each maps to the two selling points.
2. **`docs/01-architecture.md`** — High-level architecture: layer diagram, module/package structure, MVVM + repository design, the sync-ready strategy (§2), dependency list with justifications, min-SDK proposal, and the CI proposal.
3. **`docs/02-data-spec.md`** — Full data model: entities, fields, types, relationships, indexes, Room schema sketch (entity + DAO signatures, not full implementations), units strategy, soft-delete/ID/timestamp conventions, and the export/import format (versioned, with an example).
4. **`docs/03-ux-spec.md`** — UX spec: screen inventory, navigation map, and detailed low-fi wireframes for (a) the active-logging screen and (b) the analytics screen. Must include the explicit tap-sequence walkthrough for fast logging (§5). Accessibility notes.
5. **`docs/04-analytics-spec.md`** — Analytics spec: exact metrics, formulas (with chosen 1RM formula justified), the queries/aggregations behind them, and chart-by-chart definitions.
6. **`docs/05-roadmap.md`** — A phased implementation plan broken into reviewable milestones (e.g. M1 data layer + tests, M2 logging flow, M3 plans, M4 analytics, M5 export/import + polish). Each milestone: goal, deliverables, exit criteria.

End the session with a short summary and the open questions you still need answered before implementation.

---

## 9. Known open questions (propose answers in the docs)

Don't block on these — propose a reasoned default for each and flag it for review:
1. **App name** — "LiftLog" is a placeholder. Note it as TBD.
2. **Min SDK / target devices** — phones only, or tablets too? Propose.
3. **Units** — default kg or lb? Per-exercise or global preference? Propose.
4. **Built-in exercise library** — how big a starter set, and sourced how (you can author a reasonable default list)? Propose.
5. **Plan flexibility** — fixed weekly schedule vs. freeform "day templates" the user picks from? Lean toward whatever best serves fast, low-friction use.
6. **RPE / notes** — include in v1 logging or defer? Propose based on the "don't break stride" principle.
7. **Rest timer** — in v1 or future? Propose.
8. **Theming** — Material You dynamic color only, or also a manual dark/light toggle? Propose.

---

## 10. Guardrails — non-negotiables to keep checking against

- **It must work with zero setup, zero account, zero network.** If any design choice violates this for v1, it's wrong.
- **Logging must be fast.** If a flow adds taps to the core logging path, justify it or cut it.
- **Don't build cloud sync** — but never make a data-layer decision that would require a rewrite to add it.
- **No data loss.** Backgrounding, process death, and "I got a new phone" must all be survivable (via state preservation and export/import).
- **Justify every dependency.**

When in doubt, optimize for the two selling points over feature breadth.
