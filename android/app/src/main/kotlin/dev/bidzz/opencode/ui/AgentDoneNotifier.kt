package dev.bidzz.opencode.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dev.bidzz.opencode.MainActivity
import dev.bidzz.opencode.R

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
 * is in the foreground — this function only posts.
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
        val pending = PendingIntent.getActivity(
            context,
            sessionID.hashCode(),
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
        manager.notify(sessionID.hashCode(), notification)
    }
}
