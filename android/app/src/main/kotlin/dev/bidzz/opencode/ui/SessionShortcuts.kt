package dev.bidzz.opencode.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import dev.bidzz.opencode.MainActivity
import dev.bidzz.opencode.R

/** Extra carrying which launcher shortcut was tapped. */
const val EXTRA_OPENCODE_ACTION = "opencode_action"

/** Extra carrying the session id for [ACTION_OPEN_SESSION]. */
const val EXTRA_SESSION_ID = "session_id"

/** Static shortcut: start a fresh session. */
const val ACTION_NEW_SESSION = "new_session"

/** Dynamic shortcut: open the last session. */
const val ACTION_OPEN_SESSION = "open_session"

/**
 * Label for the dynamic "last session" shortcut: the session title when
 * the engine has named it, the fallback otherwise. Pure, so it is the
 * part of this file a unit test can pin down.
 */
fun lastSessionShortcutLabel(title: String?, fallback: String): String =
    title?.takeIf { it.isNotBlank() } ?: fallback

/**
 * Publishes the dynamic shortcut for the last opened session (Native C).
 * Replaces the whole dynamic list — the app keeps exactly one. Rate
 * limits, a missing ShortcutManager and old API levels are swallowed:
 * shortcuts are a convenience, never a crash.
 */
fun updateLastSessionShortcut(context: Context, sessionID: String, title: String?) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
    runCatching {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val label = lastSessionShortcutLabel(title, context.getString(R.string.shortcut_last_session))
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            putExtra(EXTRA_OPENCODE_ACTION, ACTION_OPEN_SESSION)
            putExtra(EXTRA_SESSION_ID, sessionID)
        }
        val info = ShortcutInfo.Builder(context, "last_session")
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(Icon.createWithResource(context, R.drawable.ic_logomark))
            .setIntent(intent)
            .build()
        manager.dynamicShortcuts = listOf(info)
    }
}
