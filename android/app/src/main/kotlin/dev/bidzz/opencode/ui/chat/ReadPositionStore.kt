package dev.bidzz.opencode.ui.chat

import android.content.Context

/**
 * Persists the per-session read watermark across app launches (Fondasi
 * Opsi B #6d, reworked audit-100 Fase 6b 01-F2). The watermark used to be
 * a message COUNT: loading older messages prepended above it, so the
 * count pointed at a message the reader never saw and the badge lied.
 * It is now the id of the last message read: ids survive prepends, so
 * the watermark points at the same message after "load earlier" and
 * after a relaunch. Same SharedPreferences pattern as
 * [dev.bidzz.opencode.ui.sessions.PinnedSessionStore]: one prefs file,
 * one string per session id. A missing entry means the session was never
 * read and the caller falls back to the bottom. Old count entries
 * (`read_count_*`) are left orphaned: a one-time watermark loss on
 * upgrade is acceptable — the count watermark was itself the bug.
 */
class ReadPositionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "read_positions"
        private const val KEY_PREFIX = "read_id_"
    }

    fun getReadMessageId(sessionId: String): String? =
        prefs.getString(KEY_PREFIX + sessionId, null)

    fun putReadMessageId(sessionId: String, messageId: String) {
        prefs.edit().putString(KEY_PREFIX + sessionId, messageId).apply()
    }
}

/**
 * Resolves a persisted read-message id to the in-memory watermark count
 * (01-F2): the index after the stored message. A null id was never
 * stored — everything is read, the watermark is the bottom. An empty id
 * is the "nothing read yet" marker — the watermark is 0. An id that is
 * no longer in the window was read before the window starts, so
 * everything in the window is newer than it — also 0. An id that is
 * still present resolves to its index + 1, wherever the prepends moved it.
 */
internal fun resolveReadCount(storedId: String?, messages: List<ChatMessage>): Int {
    if (storedId == null) return messages.size
    if (storedId.isEmpty()) return 0
    val index = messages.indexOfFirst { it.id == storedId }
    return if (index < 0) 0 else index + 1
}
