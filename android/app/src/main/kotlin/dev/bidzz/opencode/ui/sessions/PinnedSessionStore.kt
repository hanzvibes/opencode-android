package dev.bidzz.opencode.ui.sessions

import android.content.Context

/**
 * Persists pinned session IDs across app launches.
 */
class PinnedSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "pinned_sessions"
        private const val KEY_PINNED = "pinned_ids"
    }

    fun getPinnedIds(): Set<String> =
        prefs.getStringSet(KEY_PINNED, emptySet()) ?: emptySet()

    fun togglePin(sessionId: String): Boolean {
        val current = getPinnedIds().toMutableSet()
        val isPinned = if (current.contains(sessionId)) {
            current.remove(sessionId)
            false
        } else {
            current.add(sessionId)
            true
        }
        prefs.edit().putStringSet(KEY_PINNED, current).apply()
        return isPinned
    }

    fun isPinned(sessionId: String): Boolean = getPinnedIds().contains(sessionId)

    fun remove(sessionId: String) {
        val current = getPinnedIds().toMutableSet()
        if (current.remove(sessionId)) {
            prefs.edit().putStringSet(KEY_PINNED, current).apply()
        }
    }
}
