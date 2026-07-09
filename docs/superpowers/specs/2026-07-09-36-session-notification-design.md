# Design: persistent notification for running sessions (issue #36)

Owner-approved 2026-07-09.

## Problem

While a workout session is running, the only way to see where you are or log a set
is to reopen the app and tap through. Issue #36 asks for a persistent notification
showing the current exercise and set progress, with a one-tap "Log set" action and
tap-to-open landing directly on the running session.

## Decision: the app's first permission

LiftLog is deliberately zero-permission (`PRIVACY.md`, `HANDOFF.md` §10,
`docs/00-product-spec.md` §6.4). This feature knowingly relaxes that stance — an
explicit owner decision. The app gains exactly one runtime prompt:
`POST_NOTIFICATIONS` (Android 13+), requested contextually when the user lands on the
Active Session screen without the permission, default-on, no custom rationale UI. The
cadence is OS-managed, not tracked by the app (owner decision 2026-07-09): Android
silences requests permanently after two explicit denials, so a denier sees at most two
dialogs ever while an accidental dismissal still gets re-asked. Android 12
(minSdk 31/32) needs no prompt. Manifest also gains
`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` (install-time, promptless).
Docs and privacy copy are updated accordingly. minSdk stays 31 — the feature is
identical across 31–36 apart from the single SDK-33 prompt guard.

## Design

1. **Mechanism: foreground service.** `SessionNotificationService`,
   `foregroundServiceType="specialUse"` (the `health` type would require an extra
   runtime permission such as `ACTIVITY_RECOGNITION`). On Android 14+ the
   notification remains swipe-dismissible — OS behavior, accepted.
2. **Content.** Title = current exercise (via `ExerciseNameResolver` so built-ins
   localize). Body = set progress + the exact values LOG SET would log:
   `Set 3 of 5 · next: 80 kg × 8`; ad-hoc (null targets) `3 sets · next: 80 kg × 8`;
   never-performed exercise (no weight known) `No sets yet · tap to log` with the
   LOG SET action hidden. Weight respects the kg/lb display setting. System
   chronometer anchored at `session.startedAt`. LOG SET action + tap-to-open.
3. **Current-exercise/entry sync.** A `@Singleton` `ActiveEntryTracker` holds the
   active session-exercise id and currently dialed weight/reps;
   `ActiveSessionViewModel` writes it, the service reads it. After process death it
   falls back to shared pure logic: first-incomplete-else-last (extracted
   `pickDefault`) + `Prefill.forNextSet()` — LOG SET always logs exactly what the
   app would.
4. **LOG SET action.** PendingIntent back into the service; calls the existing
   `SessionRepository.logSet()`; the notification refreshes reactively and advances
   to the next exercise when target sets are reached (mirrors in-app auto-advance).
   No undo in the notification — the set is visible and editable in the app.
5. **Tap-to-open.** Nav deep link on `ActiveSessionRoute` (internal
   `liftlog://session/{sessionId}` URI), `MainActivity` `launchMode="singleTop"`,
   `addOnNewIntentListener` bridge in `LiftLogApp`.
6. **Lifecycle.** An app-scoped coordinator observes
   `SessionRepository.observeActiveSession()` (gated on `ProcessLifecycleOwner`
   STARTED) and starts the service when a live session exists and notifications are
   enabled; the service self-observes the same flow and stops + removes the
   notification on finish or discard.
7. **i18n.** All strings in `values/` and `values-de/` (lint gates enforce it).
8. **Testing.** The service stays thin; a pure-JVM
   `SessionNotificationModelProducer` maps (session details + tracker + settings)
   flows → `SessionNotificationModel`, unit-tested with the existing hand-written
   fakes + Turbine. Deep link pinned by an instrumented test; full manual checklist
   on-device.

## Out of scope

Rest timer (v2 candidate; the service is a natural future home), reboot-survival
(`RECEIVE_BOOT_COMPLETED`), notification undo, custom rationale UI.
