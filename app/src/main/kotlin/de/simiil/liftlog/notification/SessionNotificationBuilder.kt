package de.simiil.liftlog.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import de.simiil.liftlog.MainActivity
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.units.Weights
import de.simiil.liftlog.ui.navigation.SESSION_DEEP_LINK_BASE

/** Builds the session notification from a [SessionNotificationModel]. The only class
 *  that knows about Android notification APIs — all content rules live in the model. */
class SessionNotificationBuilder(
    private val context: Context,
) {
    fun ensureChannel() {
        val channel =
            NotificationChannelCompat
                .Builder(SessionNotificationService.CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.session_notification_channel_name))
                .setDescription(context.getString(R.string.session_notification_channel_description))
                .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** Shown only for the instant between startForeground and the first model emission. */
    fun placeholder(): Notification =
        base()
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.session_notification_placeholder))
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    RC_CONTENT,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            ).build()

    fun build(model: SessionNotificationModel): Notification {
        val title = model.exerciseName ?: model.sessionName ?: context.getString(R.string.session_untitled)
        val body =
            when (model.bodyState()) {
                NotificationBodyState.EMPTY ->
                    context.getString(R.string.session_notification_body_empty)

                NotificationBodyState.TARGET_PROGRESS ->
                    context.getString(
                        R.string.session_notification_body_target,
                        model.setsDone + 1,
                        model.targetSets,
                        weightText(model),
                        model.nextReps,
                    )

                NotificationBodyState.SET_COUNT ->
                    context.resources.getQuantityString(
                        R.plurals.session_notification_body_count,
                        model.setsDone,
                        model.setsDone,
                        weightText(model),
                        model.nextReps,
                    )
            }
        return base()
            .setContentTitle(title)
            .setContentText(body)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setWhen(model.startedAt.toEpochMilli())
            .setContentIntent(contentIntent(model.sessionId))
            .apply {
                if (model.showLogSet) {
                    addAction(0, context.getString(R.string.session_log_set), logSetIntent())
                }
            }.build()
    }

    private fun base(): NotificationCompat.Builder =
        NotificationCompat
            .Builder(context, SessionNotificationService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_session)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            // Android 12+ otherwise delays FGS notifications up to ~10 s.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

    /** kg stored canonically; display follows the unit setting, e.g. "80 kg". */
    private fun weightText(model: SessionNotificationModel): String =
        "${Weights.format(model.nextWeightKg!!, model.unit)} ${Weights.label(model.unit)}"

    private fun contentIntent(sessionId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            RC_CONTENT,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData("$SESSION_DEEP_LINK_BASE/$sessionId".toUri()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun logSetIntent(): PendingIntent =
        PendingIntent.getForegroundService(
            context,
            RC_LOG_SET,
            Intent(context, SessionNotificationService::class.java)
                .setAction(SessionNotificationService.ACTION_LOG_SET),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private companion object {
        const val RC_CONTENT = 0
        const val RC_LOG_SET = 1
    }
}
