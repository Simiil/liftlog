# 00 — Product Spec

> **Status:** Draft for review · 2026-06-07
> **Source:** [HANDOFF.md](../HANDOFF.md) §1, §9, refined in design session.
> App name **"LiftLog" is a placeholder (TBD)** — used throughout the docs until decided.

## 1. Product summary

LiftLog is a native Android app for logging resistance training. A user records what they lifted (weight × reps per set) per exercise per session, optionally following a self-defined training plan, and sees per-exercise progress analytics. It is strictly offline: no account, no cloud, no network code.

### Selling points (design north stars)

1. **No cloud, no account. Install and go.** Fully functional the moment it's installed. No sign-up, no network calls, no onboarding wizard, no runtime permissions in v1 (sole exception: the optional in-workout notification permission, issue #36).
2. **Ultra-fast data entry that doesn't break training stride.** Logging a set mid-workout is 1–2 taps with values pre-filled from history. Big touch targets, no flow-breaking modals, state survives backgrounding and process death.

Every feature below must justify itself against these two points. When in doubt, the selling points win over feature breadth.

## 2. v1 scope

Four user-facing capabilities plus a minimal settings surface:

| # | Capability | Summary | Detailed spec |
|---|---|---|---|
| 1 | **Training diary** | Start a session (from a day template or empty); log sets with weight/reps pre-filled from the previous set / last session; 1 tap to repeat a set. Session-level RPE and a workout note are available via the `SessionMetaRow` at the bottom of the Active Session screen (and editable on Session Detail) — never on the hot path. | [03-ux-spec](03-ux-spec.md) |
| 2 | **Training plans** | Named plans (e.g. "PPL") containing reusable **day templates** ("Push Day") — an ordered exercise list with optional target sets × rep-range. **No weekday binding**: the user picks a template when training. Templates pre-populate sessions. | [02-data-spec](02-data-spec.md) §3, [03-ux-spec](03-ux-spec.md) |
| 3 | **Analytics** | Per-exercise progress (top-set weight, estimated 1RM, volume), an at-a-glance trend badge answering "am I progressing on X?", PR detection, weekly summary card. | [04-analytics-spec](04-analytics-spec.md) |
| 4 | **Export / import** | Full database to/from a single versioned, human-readable JSON file. Serves as manual backup and phone-migration path. Import is full-replace with confirmation. | [02-data-spec](02-data-spec.md) §6 |
| — | **Settings** | Weight unit (kg/lb, global), theme (system/light/dark), export/import entry point. Nothing else. | [03-ux-spec](03-ux-spec.md) |

## 3. User stories

### Training diary
- As a lifter mid-workout, I log another set of the exercise I just did in **one tap**, because weight and reps are pre-filled from my previous set. *(→ selling point 2)*
- As a lifter starting an exercise, I see what I did **last session** (ghost rows) and the entry is pre-filled from it, so I never need to remember numbers. *(→ 2)*
- As a lifter, I put my phone in my pocket between sets; when I unlock it the session is exactly where I left it — even if Android killed the app. *(→ 2)*
- As a lifter who deviates from plan, I add any exercise mid-session without leaving the logging screen flow. *(→ 2)*
- As a detail-oriented lifter, I can attach RPE or a note to a set after logging it, without those fields slowing down anyone else. *(→ 2)*

### Training plans
- As a lifter on a program, I define "Push Day" once — ordered exercises with target sets × reps — and starting that workout is one tap from Home. *(→ 2)*
- As a lifter with an irregular schedule, skipping three days never breaks anything: templates have no weekday binding, I just pick the next one. *(→ 2)*
- As a new user, I can ignore plans entirely and log freeform sessions forever. *(→ 1)*

### Analytics
- As a lifter, I see at a glance whether each exercise is trending up, flat, or down — without interpreting a chart. *(→ 2: makes the logging habit pay off)*
- As a lifter, I open an exercise and see top-set / e1RM / volume over time with my PRs marked. *(→ 1: all computed locally)*
- As a returning user, sparse or irregular history still renders honestly (real dates, no fabricated points). *(→ 1)*

### Export / import
- As a user who got a new phone, I export one file, copy it myself, import it, and have everything back. No account needed. *(→ 1)*
- As a cautious user, I keep periodic export files as backups wherever I want (it's my file). *(→ 1)*

## 4. Non-goals (v1)

- **Rest timer** — deferred to v2 by explicit decision; the logging screen reserves a UI slot for it ([03-ux-spec](03-ux-spec.md)).
- **Cloud sync / accounts** — excluded, but the data layer is sync-ready by design ([01-architecture](01-architecture.md) §4, [02-data-spec](02-data-spec.md) §2).
- Social features, sharing, leaderboards.
- Coaching/AI, exercise videos or images.
- Health Connect / wearables — future candidate, not v1.
- Cardio, nutrition, body-weight tracking — strictly resistance training.
- Tablets / large-screen layouts — phone-first; layouts are responsive but untuned for tablets.
- Home-screen widgets — good v2 candidate (fits selling point 2), not v1.

## 5. Resolved open questions (HANDOFF §9)

All decided in the design session of 2026-06-07 with the project owner; flagged here for review visibility.

| # | Question | Decision |
|---|---|---|
| 1 | App name | "LiftLog" placeholder, **TBD** before release |
| 2 | Min SDK / devices | **API 31+ (Android 12)**, phones only — see [01-architecture](01-architecture.md) §6 |
| 3 | Units | Global preference, **default kg**, canonical kg storage — see [02-data-spec](02-data-spec.md) §5 |
| 4 | Built-in exercise library | **~70 exercises**, authored in-repo as versioned seed JSON — see [02-data-spec](02-data-spec.md) Appendix A |
| 5 | Plan flexibility | **Day templates, no weekday binding** |
| 6 | RPE / notes | **In v1, off the hot path** (long-press on a logged set) |
| 7 | Rest timer | **Deferred to v2**; UI slot reserved |
| 8 | Theming | **Dynamic color + manual system/light/dark toggle** |

## 6. v1 success criteria

Qualitative bar for shipping:

1. The §5 tap math in [03-ux-spec](03-ux-spec.md) holds on a real device with a real program (1 tap to repeat a set; 3 taps cold-start → first set logged).
2. A mid-session process kill loses zero logged sets and resumes to the active session.
3. Export → wipe → import round-trip is lossless ([05-roadmap](05-roadmap.md) M5 exit criterion).
4. Zero network permission in the manifest. The only runtime prompt is the optional notification permission for the in-workout notification (issue #36) — one-time, deniable, and off the logging hot path.
