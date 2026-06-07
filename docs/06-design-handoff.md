# 06 — Visual Design Handoff Brief

> **Status:** Ready for handoff · 2026-06-07
> **Audience:** the visual-design pass (Claude Design or a human designer). Interaction design, data model, and scope are **locked** — this brief commissions visual design *within* that contract, not a redesign.
> Runs in parallel with implementation milestones M0–M1 ([05-roadmap](05-roadmap.md)); results are needed by **M2** (logging screen) and **M4** (analytics).

## 1. Product in two sentences

LiftLog (name TBD, see §6) is an offline-only Android weightlifting diary: log weight × reps per set, follow self-defined day templates, see per-exercise progress. The product's identity is **speed of data entry** — a resting lifter logs a set in one tap without thinking.

## 2. Design north stars

From [00-product-spec](00-product-spec.md): every visual decision is judged against

1. **Install and go** — no onboarding, nothing to configure; the UI must be self-evident on first launch.
2. **Never break training stride** — the logging screen is used mid-workout, sweaty-handed, at arm's length, often in poor gym lighting. Glanceability and reachability beat density and elegance.

## 3. Hard constraints (non-negotiable, the spec wins)

- **[03-ux-spec](03-ux-spec.md) is the contract.** Layouts, navigation, tap sequences, and component inventory are fixed. If a visual idea would add a tap to the logging path, introduce a dialog/modal into logging, or reorder the §4.1 wireframe's information hierarchy — it's out of scope. Flag it as a suggestion instead; do not design it in.
- **Material 3 with dynamic color** (Android 12+). All colors must be expressed as **M3 color roles** (`primary`, `surfaceContainerHigh`, `onSurfaceVariant`, …) — never hardcoded hex. A static fallback brand palette (for users who disable dynamic color) may be proposed: define it as a seed color + generated M3 scheme.
- **Dark mode is first-class** (gym usage, OLED). Design dark first or deliver both; both must work with arbitrary dynamic-color seeds.
- **Touch targets:** ≥56dp on the logging path (steppers, LOG SET, numpad keys), ≥48dp elsewhere. Specify all sizes in **dp**, type in **sp**.
- **Dynamic type to 200%** without losing the 1-tap logging path (specify the stacking/reflow behavior you intend).
- **Trend/status semantics never by color alone** (pair with glyphs: ↑ → ↓).
- The **reserved rest-timer slot** under LOG SET ([03-ux-spec](03-ux-spec.md) §4.1) stays visually empty in v1 — don't fill it, don't remove it.
- Charts must be reproducible with **Vico** (line charts, point markers, simple axes — no exotic chart types, no gradient-mesh fantasy renders).

## 4. Commissioned deliverables

### A. Active Session screen (priority 1 — the product)
Hi-fi design of [03-ux-spec](03-ux-spec.md) §4.1 in **all** of these states:
1. Mid-session: one active (expanded) card with ghost row + 2 logged sets, one completed card, two upcoming cards, add-exercise row.
2. Inline numpad open (replacing the stepper area — card still visible).
3. Long-press edit row expanded (weight/reps fields, RPE chips 6–10, note, delete).
4. First-ever exercise (no history): empty steppers, disabled LOG SET.
5. Session header states: timer running · finish (✔) confirmation snackbar with PR count.

### B. Analytics (priority 2)
Browser (header summary card, exercise rows with trend badge + sparkline, stale and insufficient-data states) and exercise detail (metric chips, range selector, line chart with PR markers, recent-sessions list, bodyweight-metric variant) per [03-ux-spec](03-ux-spec.md) §5 and [04-analytics-spec](04-analytics-spec.md) §6.

### C. Home (priority 3)
Resume-card state and idle state (template chips, empty-session entry, recent list) per [03-ux-spec](03-ux-spec.md) §3. Include the true first-launch state (no plans, no history yet — what invites the very first log?).

### D. Component & system specs
- Steppers, LOG SET button, ghost row, logged-set row, numpad, template chip, trend badge: dimensions, M3 role mapping, state layers (pressed/disabled), corner radii, elevation/tonal levels.
- Type scale mapped to M3 roles (`displaySmall`…`labelSmall`) with any overrides.
- Spacing system (4dp grid assumed; confirm or propose).
- Deliver as a **Compose-translatable spec** (tables/tokens + annotated frames), not only pixel mocks.

### E. Optional track — name + icon
"LiftLog" is a placeholder ([00-product-spec](00-product-spec.md) §5). Explore 5–10 name candidates (check obvious Play-Store collisions; offline/no-account positioning may inform it) and adaptive-icon concepts (themed-icon compatible, legible at 48dp). This track is independent — don't block A–D on it.

## 5. Source documents (read in this order)

1. [03-ux-spec](03-ux-spec.md) — wireframes, behaviors, accessibility (the contract)
2. [00-product-spec](00-product-spec.md) — scope, selling points, user stories
3. [04-analytics-spec](04-analytics-spec.md) §6 — chart definitions
4. [01-architecture](01-architecture.md) §6 — minSdk 31 / dynamic color context
5. [HANDOFF.md](../HANDOFF.md) — original brief (background only; the docs above supersede it)

## 6. Out of scope for this pass

Tablet layouts · widgets · rest-timer design (v2 — slot only) · onboarding/tutorial screens (there are none by design) · marketing/Play-Store assets (later, after the name decision) · any new screens or flows not in the [03-ux-spec](03-ux-spec.md) §2 inventory.

## 7. Acceptance checklist (how the result gets reviewed)

- [ ] Every screen state in §4 A–C delivered, dark + light.
- [ ] Zero hardcoded colors — every fill/stroke/text names an M3 role.
- [ ] Logging path unchanged: 1-tap repeat set, no modals, hierarchy of §4.1 intact.
- [ ] All logging-path targets ≥56dp; sizes annotated in dp/sp.
- [ ] 200% font-scale behavior specified for the active card.
- [ ] Charts buildable in Vico as specified in [04-analytics-spec](04-analytics-spec.md) §6.
- [ ] Component spec (§4 D) complete enough that M2 implementation needs no design guesses.
