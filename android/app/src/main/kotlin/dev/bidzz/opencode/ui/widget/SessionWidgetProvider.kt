package dev.bidzz.opencode.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.bidzz.opencode.MainActivity
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import dev.bidzz.opencode.ui.ACTION_NEW_SESSION
import dev.bidzz.opencode.ui.ACTION_OPEN_SESSION
import dev.bidzz.opencode.ui.EXTRA_OPENCODE_ACTION
import dev.bidzz.opencode.ui.EXTRA_SESSION_ID

/**
 * Home-screen widget (Native C): the last session's title, a button to
 * open it, and a button for a fresh session. The widget never talks to
 * the engine — the app pushes its state here whenever a session opens,
 * and [onUpdate] renders the default (app name, no session) for a
 * cold start or a reboot, when nothing has been pushed yet.
 */
class SessionWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Last session id from the same prefs the app restores from;
        // the title is unknown here, so the default shows the app name.
        val lastSessionID = context
            .getSharedPreferences(OpenCodeApplication.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(OpenCodeApplication.KEY_LAST_SESSION, null)
        pushWidget(context, lastSessionID, null)
    }
}

/**
 * Renders the widget everywhere it is placed. [sessionID] null disables
 * the Open button; [title] null shows the app name instead.
 */
fun pushWidget(context: Context, sessionID: String?, title: String?) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(
        ComponentName(context, SessionWidgetProvider::class.java),
    )
    if (ids.isEmpty()) return
    val views = RemoteViews(context.packageName, R.layout.widget_session).apply {
        setTextViewText(
            R.id.widget_title,
            title?.takeIf { it.isNotBlank() } ?: appLabel(context),
        )
        setTextViewText(
            R.id.widget_subtitle,
            if (sessionID != null) {
                context.getString(R.string.widget_open_session_hint)
            } else {
                context.getString(R.string.widget_no_session)
            },
        )
        setOnClickPendingIntent(
            R.id.widget_new_session,
            newSessionPendingIntent(context),
        )
        val openButton = R.id.widget_open_session
        if (sessionID != null) {
            setBoolean(openButton, "setEnabled", true)
            setOnClickPendingIntent(openButton, openSessionPendingIntent(context, sessionID))
        } else {
            setBoolean(openButton, "setEnabled", false)
        }
    }
    manager.updateAppWidget(ids, views)
}

/** Pushes the current session into the widget, if one is placed. */
fun updateSessionWidget(context: Context, sessionID: String?, title: String?) {
    runCatching { pushWidget(context, sessionID, title) }
}

/** The app label ("OpenCode") for the widget's default title. */
private fun appLabel(context: Context): String =
    runCatching {
        context.applicationInfo.loadLabel(context.packageManager).toString()
    }.getOrDefault("OpenCode")

private fun newSessionPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        putExtra(EXTRA_OPENCODE_ACTION, ACTION_NEW_SESSION)
    }
    return PendingIntent.getActivity(
        context,
        REQUEST_NEW_SESSION,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun openSessionPendingIntent(context: Context, sessionID: String): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        putExtra(EXTRA_OPENCODE_ACTION, ACTION_OPEN_SESSION)
        putExtra(EXTRA_SESSION_ID, sessionID)
    }
    return PendingIntent.getActivity(
        context,
        REQUEST_OPEN_SESSION,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private const val REQUEST_NEW_SESSION = 1
private const val REQUEST_OPEN_SESSION = 2
