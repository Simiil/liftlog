package de.simiil.liftlog.notification

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.simiil.liftlog.domain.logging.ActiveEntryTracker
import de.simiil.liftlog.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Starts [SessionNotificationService] whenever a live session exists, notifications are
 * enabled, and the app is foregrounded (FGS start restriction). The STARTED-gated repeat
 * also re-attempts on every app foreground, which covers "notification permission granted
 * later in system settings". Stopping is the service's own job (it observes the session).
 */
class SessionNotificationCoordinator(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val tracker: ActiveEntryTracker,
    private val permissionTick: NotificationPermissionTick,
    private val appScope: CoroutineScope,
) {
    fun start() {
        appScope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    sessionRepository.observeActiveSession().map { it != null }.distinctUntilChanged(),
                    permissionTick.ticks,
                ) { active, _ -> active }
                    .collect { active ->
                        if (!active) {
                            tracker.clear() // hygiene: no session, no stale entry
                            return@collect
                        }
                        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return@collect
                        try {
                            context.startForegroundService(Intent(context, SessionNotificationService::class.java))
                        } catch (_: ForegroundServiceStartNotAllowedException) {
                            // Lost the foreground race; the STARTED repeat retries on next app foreground.
                        }
                    }
            }
        }
    }
}
