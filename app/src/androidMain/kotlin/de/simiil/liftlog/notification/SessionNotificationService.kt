package de.simiil.liftlog.notification

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import de.simiil.liftlog.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Foreground service backing the in-workout notification (issue #36). Runs while a
 * session is active; observes the active session itself and stops (removing the
 * notification) the moment it is finished or discarded. LOG SET logs the values the
 * notification currently displays — the latest collected model, never intent extras.
 */
class SessionNotificationService :
    Service(),
    KoinComponent {
    private val producer: SessionNotificationModelProducer by inject()

    private val builder: SessionNotificationBuilder by inject()

    private val sessionRepository: SessionRepository by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val latestModel = MutableStateFlow<SessionNotificationModel?>(null)
    private var observing = false

    override fun onCreate() {
        super.onCreate()
        builder.ensureChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Contract: ALWAYS startForeground first — getForegroundService PendingIntents and
        // the 5-second FGS window both require it, whatever the action. ServiceCompat masks
        // SPECIAL_USE (API 34+) to NONE on 31–33.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            latestModel.value?.let(builder::build) ?: builder.placeholder(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (intent?.action == ACTION_LOG_SET) {
            scope.launch { logCurrentSet() }
        }
        if (!observing) {
            observing = true
            observeSession()
        }
        return START_STICKY
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSession() {
        scope.launch {
            sessionRepository
                .observeActiveSession()
                .flatMapLatest { session ->
                    if (session == null) flowOf(null) else producer.models(session.id)
                }.collect { model ->
                    latestModel.value = model
                    if (model == null) {
                        ServiceCompat.stopForeground(
                            this@SessionNotificationService,
                            ServiceCompat.STOP_FOREGROUND_REMOVE,
                        )
                        stopSelf()
                    } else {
                        val manager = NotificationManagerCompat.from(this@SessionNotificationService)
                        if (manager.areNotificationsEnabled()) {
                            @Suppress("MissingPermission") // guarded by areNotificationsEnabled()
                            manager.notify(NOTIFICATION_ID, builder.build(model))
                        }
                    }
                }
        }
    }

    /** Logs what the notification shows. Waits briefly for the first model when the
     *  service was cold-started by the action tap (stale notification after a kill). */
    private suspend fun logCurrentSet() {
        val model = withTimeoutOrNull(5_000) { latestModel.filterNotNull().first() } ?: return
        val sessionExerciseId = model.sessionExerciseId ?: return
        val weightKg = model.nextWeightKg ?: return // action hidden in this state; defensive
        sessionRepository.logSet(sessionExerciseId, weightKg, model.nextReps)
        // The DB emission drives the notification refresh (and any auto-advance).
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "active_session"
        const val ACTION_LOG_SET = "de.simiil.liftlog.action.LOG_SET"
    }
}
