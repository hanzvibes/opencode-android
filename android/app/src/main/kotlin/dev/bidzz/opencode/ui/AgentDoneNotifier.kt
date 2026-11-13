package dev.bidzz.opencode.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import dev.bidzz.opencode.MainActivity
import dev.bidzz.opencode.R

private const val TAG = "AgentDoneNotifier"
private const val CHANNEL_ID = "agent_done"

/**
 * Whether an engine event type marks the end of an agent run (Fase 5,
 * 55-F6). The service-side watcher and the UI trigger agree on this
 * set: `succeeded` and `failed` mean the answer is final, while
 * `interrupted` is a user Stop — the user who stopped it needs no
 * "your run finished" notification.
 */
internal fun isAgentRunEndEvent(type: String): Boolean =
    type == dev.bidzz.opencode.core.api.dto.EventTypes.EXECUTION_SUCCEEDED ||
        type == dev.bidzz.opencode.core.api.dto.EventTypes.EXECUTION_FAILED

/**
 * Tells the user an agent run finished while the app was in the
 * background (Native C). Tapping the notification opens that session
 * through the opencode:// deep link, so the path is the same one a
 * link from outside the app takes.
 *
 * Never throws: a missing permission, a dead manager or a bad session
 * id just means no notification. The caller decides whether the app
 * is in the foreground — this function only posts. A failure is logged
 * (55-F9): the old code swallowed the `SecurityException` of a denied
 * `POST_NOTIFICATIONS` without a trace, so a user who once tapped
 * "Don't allow" lost the whole feature with no explanation.
 *
 * The notification id and the PendingIntent request code come from
 * [AgentDoneIdStore] (55-F8/56-F5), a stable id per session — never
 * `sessionID.hashCode()`, whose 32-bit collisions silently replaced one
 * session's notification with another's and opened the wrong session.
 */
fun notifyAgentDone(context: Context, sessionID: String, title: String?) {
    runCatching {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_agent_done_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("opencode://session/$sessionID")
        }
        val notificationId = AgentDoneIdStore(context).idFor(sessionID)
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(context.getString(R.string.notification_agent_done_title))
            .setContentText(
                title?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.notification_agent_done_body),
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId, notification)
    }.onFailure {
        Log.w(TAG, "could not post agent-done notification for session $sessionID", it)
    }
}

/**
 * Cancels the agent-done notification for [sessionID] (55-F7).
 *
 * Called when the session is opened through any path — deep link,
 * notification tap, widget, drawer, resume — so a stale entry does not
 * linger in the drawer after the user already read the answer. No-op
 * when the session never got a notification id. Only this session's
 * entry is cancelled: another session's unread notification stays.
 */
fun cancelAgentDone(context: Context, sessionID: String) {
    val id = AgentDoneIdStore(context).idIfPresent(sessionID) ?: return
    runCatching {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id)
    }.onFailure {
        Log.w(TAG, "could not cancel agent-done notification for session $sessionID", it)
    }
}
