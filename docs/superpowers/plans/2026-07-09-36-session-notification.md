# Persistent notification for running sessions (issue #36) — Implementation Plan

## Context

Issue #36: while a workout session is running, show a persistent notification with
the current exercise, set progress, a "Log set" action, and tap-to-open landing on
the running session. Approved design: `docs/superpowers/specs/2026-07-09-36-session-notification-design.md`.

This is the app's first permission (owner decision, spec §"Decision"): contextual
`POST_NOTIFICATIONS` prompt on first Active Session visit (API 33+), plus
install-time `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE`. minSdk stays 31.

## Verified Android constraints

- **`specialUse` FGS type on API 31–33:** manifest attribute is inert pre-34;
  `ServiceCompat.startForeground(service, id, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`
  masks the type to NONE on 31–33 (androidx source). One unconditional call, no branches.
- **Android 12+ can delay FGS notifications ~10 s** → set
  `setForegroundServiceBehavior(FOREGROUND_SERVICE_BEHAVIOR_IMMEDIATE)`.
- **Chronometer:** default style, single-line body (BigTextStyle unnecessary, quirky with chronometer).
- **Deep link (Navigation 2.9.3):** `navDeepLink<ActiveSessionRoute>(basePath = "liftlog://session")`
  → `liftlog://session/{sessionId}`. No `<intent-filter>` needed (explicit PendingIntent).
  Cold start automatic via `setGraph`; **`onNewIntent` is NOT automatic with `singleTop`** →
  bridge via `addOnNewIntentListener` in a `DisposableEffect` in `LiftLogApp` calling
  `navController.handleDeepLink(intent)`. Guard: skip when already on `ActiveSessionRoute`
  with the same sessionId (`handleDeepLink` rebuilds the back stack → would recreate the VM
  and lose the dialed entry). No `FLAG_ACTIVITY_NEW_TASK` on the content intent.
- **FGS start restrictions:** gate the coordinator on `ProcessLifecycleOwner` STARTED
  (new dep `androidx.lifecycle:lifecycle-process`, version ref `lifecycle`) + catch
  `ForegroundServiceStartNotAllowedException`. The STARTED-repeat re-attempts on every app
  foreground → covers "permission granted later in system settings".
- **Permission revoked mid-session** force-kills the process; nothing to build.
- **LOG SET PendingIntent:** `PendingIntent.getForegroundService`; `onStartCommand` calls
  `startForeground` unconditionally before dispatching actions (race-proof for the
  notification-alive-but-service-dead window; action taps carry an FGS start exemption).
- **`START_STICKY`:** null-intent restart is safe — the service resolves everything from
  `observeActiveSession()`; normal stops go through `stopSelf()`.
- **Play Console** requires a special-use FGS declaration if ever published — docs note only.

## Steps (one commit each)

### Step 1 — Domain: `ActiveEntryTracker` + extract `pickDefault`

- New `domain/logging/ActiveEntryTracker.kt`: `data class ActiveEntry(sessionExerciseId,
  weightKg: Double?, reps: Int)`; `@Singleton class ActiveEntryTracker` wrapping
  `MutableStateFlow<ActiveEntry?>` with `update()`/`clear()`.
- New `domain/logging/ActiveExerciseDefaults.kt`: pure object with `pickDefault(details)`
  (first incomplete else last) and `isComplete(ews)` extracted verbatim from
  `ActiveSessionViewModel` l.436–445.
- `ui/session/ActiveSessionViewModel.kt`: inject tracker; delegate to
  `ActiveExerciseDefaults`; in `init` collect `entryFlow` → `tracker.update(...)`
  (entryFlow always tracks the active card — covers activation/dialing/numpad/re-prime);
  `tracker.clear()` in `onFinish()`/`onDiscard()`.
- Tests: tracker assertions in `ActiveSessionViewModelTest` (real tracker); new
  `ActiveExerciseDefaultsTest`.

### Step 2 — Producer: flows → `SessionNotificationModel`

- New `notification/SessionNotificationModelProducer.kt` (new package
  `de.simiil.liftlog.notification`; NO `android.*` imports — pure-JVM testable):
  - `data class SessionNotificationModel(sessionId, startedAt, sessionName?, exerciseName?,
    sessionExerciseId?, setsDone, targetSets?, nextWeightKg?, nextReps, unit)`.
  - `@Singleton`, injects `SessionRepository`, `ExerciseRepository`, `SettingsRepository`,
    `ActiveEntryTracker`, `ExerciseNameResolver`. `fun models(sessionId):
    Flow<SessionNotificationModel?>` — `combine(observeSessionDetails,
    exerciseRepository.observeAll(), settingsRepository.weightUnit, tracker.state)` +
    `mapLatest` for the suspend `lastPerformance` with a ghost-cache map (once per exercise).
  - Emit **null** when details null / `endedAt != null` / deleted (finish AND discard).
  - Current exercise: tracker's seId iff live and `!isComplete`; else `pickDefault`
    (survives process death; stale-complete tracker falls through = auto-advance).
  - Values: tracker's weight/reps when matched and weight non-null; else
    `Prefill.forNextSet(card.sets, lastPerformance)`. `nextWeightKg == null` only for
    never-performed.
  - Body-state rules (KDoc): no exercises or (weight null, 0 sets) → empty state, LOG SET
    hidden; targets & `setsDone < targetSets` → "Set {n+1} of {N} · next: …"; ad-hoc or
    all-complete → "{n} sets · next: …" (plurals).
- Tests: `SessionNotificationModelProducerTest` — existing fakes + real tracker + trivial
  fake resolver, Turbine, MainDispatcherRule. Cases: target/ad-hoc/never-performed, tracker
  override, stale-complete advance, lb passthrough, null on finish and discard,
  prefill-after-process-death.

### Step 3 — Service, channel, builder, coordinator, manifest, icon

- `gradle/libs.versions.toml` + `app/build.gradle.kts`: add
  `androidx.lifecycle:lifecycle-process` (version ref `lifecycle`).
- New `res/drawable/ic_stat_session.xml`: 24dp monochrome barbell (rescale paths from
  `ic_launcher_monochrome.xml`, drop launcher inset).
- New `notification/SessionNotificationBuilder.kt` (`@Singleton`, `@ApplicationContext`):
  `ensureChannel()` (`NotificationChannelCompat`, `IMPORTANCE_LOW`), `placeholder()`,
  `build(model)` — small icon, title = `exerciseName ?: sessionName ?:
  getString(R.string.session_untitled)`, body per state rules with weight text
  `"${Weights.format(kg, unit)} ${Weights.label(unit)}"`,
  `setUsesChronometer(true).setShowWhen(true).setWhen(startedAt)`,
  `setOngoing(true).setOnlyAlertOnce(true).setSilent(true)`, `CATEGORY_WORKOUT`,
  `FOREGROUND_SERVICE_BEHAVIOR_IMMEDIATE`, content intent + conditional LOG SET action.
  PendingIntents both `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`: content rc0 =
  `getActivity(Intent(ctx, MainActivity).setAction(ACTION_VIEW)
  .setData("liftlog://session/$sessionId"))`; LOG SET rc1 =
  `getForegroundService(Intent(ctx, SessionNotificationService).setAction(ACTION_LOG_SET))`
  — **no weight/reps extras**; the service logs from its latest collected model (exactly
  what the notification displays).
- New `notification/SessionNotificationService.kt` (`@AndroidEntryPoint : Service`):
  injects producer/builder/`SessionRepository`; own scope (`SupervisorJob` +
  `Main.immediate`); `onStartCommand`: unconditional `ServiceCompat.startForeground(...,
  FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` with latest-model-or-placeholder, then dispatch
  `ACTION_LOG_SET` → `logCurrentSet()`, start observer once; observer:
  `observeActiveSession().flatMapLatest { producer.models(it.id) or flowOf(null) }` →
  null: `stopForeground(REMOVE)` + `stopSelf()`; model: `notify()` guarded by
  `areNotificationsEnabled()` (lint). `START_STICKY`; `scope.cancel()` in `onDestroy`.
  Constants: `NOTIFICATION_ID=1`, `CHANNEL_ID="active_session"`, `ACTION_LOG_SET`.
- New `notification/SessionNotificationCoordinator.kt` (`@Singleton`): `start()` on
  `@ApplicationScope` + Main: `ProcessLifecycleOwner…repeatOnLifecycle(STARTED) {
  combine(activeSession.map{it!=null}.distinctUntilChanged(), permissionTick).collect {
  active -> if active && areNotificationsEnabled() → startForegroundService (catch
  ForegroundServiceStartNotAllowedException); if !active → tracker.clear() } }`;
  `onNotificationPermissionMaybeChanged()` bumps `permissionTick`.
- `LiftLogApplication.kt`: inject coordinator, `coordinator.start()` in `onCreate`.
- `AndroidManifest.xml`: replace zero-permission comment; add `POST_NOTIFICATIONS`,
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`; `launchMode="singleTop"` on
  MainActivity; `<service .notification.SessionNotificationService exported=false
  foregroundServiceType="specialUse">` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property.
- Strings (`values/` + `values-de/`; reuse `session_log_set`, `session_untitled`):
  `session_notification_channel_name/description`, `session_notification_placeholder`,
  `session_notification_body_target`, plurals `session_notification_body_adhoc`,
  `session_notification_body_empty`.

### Step 4 — Deep link + singleTop wiring

- `ui/navigation/Destinations.kt`: `const val SESSION_DEEP_LINK_BASE = "liftlog://session"`.
- `ui/navigation/LiftLogNavHost.kt`: `composable<ActiveSessionRoute>(deepLinks =
  listOf(navDeepLink<ActiveSessionRoute>(basePath = SESSION_DEEP_LINK_BASE)))`.
- `ui/LiftLogApp.kt`: `DisposableEffect` adding `addOnNewIntentListener`
  (`androidx.core.util.Consumer<Intent>`): skip if already on `ActiveSessionRoute` with
  same sessionId, else `navController.handleDeepLink(intent)`. `MainActivity.kt` unchanged.
- Instrumented `androidTest/…/ui/SessionDeepLinkTest.kt`: seed active session via injected
  repo, launch `MainActivity` with `ACTION_VIEW liftlog://session/{id}`, assert Active
  Session screen shown — pins the cold-start deep link.

### Step 5 — Contextual permission prompt

- `domain/repository/SettingsRepository.kt` + `data/repository/SettingsRepositoryImpl.kt`:
  DataStore one-shot flag `notificationPromptShown: Flow<Boolean>` /
  `suspend setNotificationPromptShown()` (`booleanPreferencesKey("notification_prompt_shown")`;
  device-local, NOT in the backup format).
- `ActiveSessionViewModel.kt`: inject coordinator;
  `suspend fun consumeNotificationPromptOpportunity(): Boolean` (true + latch exactly once
  ever); `fun onNotificationPermissionResult()` → `coordinator.onNotificationPermissionMaybeChanged()`.
- `ActiveSessionScreen.kt`: `rememberLauncherForActivityResult(RequestPermission())` +
  `LaunchedEffect(Unit)` — request only if `SDK_INT >= 33` && not granted &&
  `consumeNotificationPromptOpportunity()`. No custom rationale UI, never re-prompt.
- Tests: `FakeSettingsRepository` additions; VM test for the once-ever latch.
  `GrantPermissionRule.grant(POST_NOTIFICATIONS)` on Active-Session Compose tests
  (`CriticalLoggingPathTest` etc.) so the new prompt can't obscure them on API 34 CI.

### Step 6 — Docs

- `PRIVACY.md`: replace "No permissions" bullet — one optional permission (notifications,
  Android 13+), display-only, deniable; bump "Last updated".
- `docs/00-product-spec.md` §6 criterion 4 and `HANDOFF.md` §10: same parenthetical.
- Note the Play special-use FGS declaration requirement where release process is documented.

## Verification

Gate: `./gradlew ktlintFormat` then `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug`.

Instrumented (scoped): `SessionDeepLinkTest`, `CriticalLoggingPathTest`.

Manual on `emulator-5554`: fresh-install prompt → grant → chronometer ticks; in-app logging
and dialed weight mirror into the notification; LOG SET from shade increments + advances at
target; tap opens session (Back → Home), no state loss when already there; finish AND
discard both remove it; `am kill` mid-session → reopen → notification returns with correct
prefill; deny path stays silent forever, `pm grant` mid-session + foreground → appears;
lb unit honored; `dumpsys` shows service gone after finish.
