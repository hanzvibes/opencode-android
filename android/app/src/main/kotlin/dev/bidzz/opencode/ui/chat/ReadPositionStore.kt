package dev.bidzz.opencode.ui.chat

import android.content.Context

/**
 * Persists the per-session read watermark (`lastReadMessageCount`) across
 * app launches (Fondasi Opsi B #6d). Same SharedPreferences pattern as
 * [dev.bidzz.opencode.ui.sessions.PinnedSessionStore]: one prefs file, one
 * int per session id. A missing entry means the session was never read and
 * the caller falls back to the bottom.
 */
class ReadPositionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "read_positions"
        private const val KEY_PREFIX = "read_count_"
    }

    fun getReadCount(sessionId: String): Int? =
        if (prefs.contains(KEY_PREFIX + sessionId)) prefs.getInt(KEY_PREFIX + sessionId, 0) else null

    fun putReadCount(sessionId: String, count: Int) {
        prefs.edit().putInt(KEY_PREFIX + sessionId, count).apply()
    }
}
