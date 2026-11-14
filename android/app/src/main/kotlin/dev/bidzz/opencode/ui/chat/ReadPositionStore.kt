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
 * read and the caller falls back to the bottom. A count watermark left
 * over from before the switch (`read_count_*`) is migrated on the first
 * open that has messages to resolve it against
 * ([getOrMigrateReadMessageId], audit-200 A9-9, DL-037): a count N means
 * the first N messages were read, so the watermark becomes the id of the
 * message at index N - 1 instead of being dropped.
 */
class ReadPositionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "read_positions"
        private const val KEY_PREFIX = "read_id_"
        /** Legacy count watermark (pre-01-F2): `read_count_<sessionId>`. */
        private const val LEGACY_COUNT_PREFIX = "read_count_"
    }

    fun getReadMessageId(sessionId: String): String? =
        prefs.getString(KEY_PREFIX + sessionId, null)

    fun putReadMessageId(sessionId: String, messageId: String) {
        prefs.edit().putString(KEY_PREFIX + sessionId, messageId).apply()
    }

    /**
     * The read id for [sessionId], migrating a legacy count watermark
     * (audit-200 A9-9, DL-037) when no id was stored yet. An id that is
     * already stored wins — the migration never overwrites one. The
     * legacy entry is consumed in the same edit that writes the id, so
     * the migration runs once; when the window is empty the id cannot
     * be resolved yet, so the legacy entry is left alone and the
     * migration retries on the next open. The edit uses apply(), like
     * [putReadMessageId]: a lost flush loses both halves together, so
     * the migration simply retries instead of half-applying.
     */
    fun getOrMigrateReadMessageId(sessionId: String, messages: List<ChatMessage>): String? {
        getReadMessageId(sessionId)?.let { return it }
        val count = legacyReadCount(sessionId) ?: return null
        val id = resolveMigratedReadId(count, messages) ?: return null
        prefs.edit()
            .remove(LEGACY_COUNT_PREFIX + sessionId)
            .putString(KEY_PREFIX + sessionId, id)
            .apply()
        return id
    }

    /**
     * The stored legacy count, if any. Read defensively: the old
     * writer's value type is not recorded anywhere, so a number or a
     * numeric string both count, and anything else is not a watermark.
     */
    private fun legacyReadCount(sessionId: String): Int? {
        return when (val raw = prefs.all[LEGACY_COUNT_PREFIX + sessionId]) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
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

/**
 * Resolves a legacy count watermark to the message id it means
 * (audit-200 A9-9, DL-037): a count N means the first N messages were
 * read, so the last read message is at index N - 1. A count of 0 (or
 * less) means nothing was read — the empty id, the same marker
 * [resolveReadCount] treats as "nothing read yet". A count past the end
 * clamps to the last message (messages may have been deleted since the
 * count was stored). An empty window cannot be resolved — null, so the
 * caller leaves the legacy entry alone and retries on the next open.
 */
internal fun resolveMigratedReadId(count: Int, messages: List<ChatMessage>): String? {
    if (messages.isEmpty()) return null
    if (count <= 0) return ""
    return messages.getOrNull(count - 1)?.id ?: messages.last().id
}
