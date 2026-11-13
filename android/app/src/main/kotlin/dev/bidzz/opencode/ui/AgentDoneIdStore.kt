package dev.bidzz.opencode.ui

import android.content.Context

/**
 * Stable notification ids for agent-done notifications (audit-100
 * 55-F8/56-F5).
 *
 * The old ids were `sessionID.hashCode()`: a 32-bit hash, so two sessions
 * could collide and the second notification silently replaced the first —
 * and tapping it opened the wrong session. A notification id must be
 * unique per session and stable across restarts: the service watcher and
 * the UI trigger post through the same function, and the cancel path must
 * find the same id later.
 *
 * Same SharedPreferences pattern as [dev.bidzz.opencode.ui.chat.ReadPositionStore]:
 * one prefs file, one int per session id, assigned monotonically. The map
 * is append-only and tiny (one int per session the app ever notified
 * about); no pruning — even a thousand sessions is a few KB. Ids start at
 * 10000, clear of the engine service's fixed ids (1001/1002).
 *
 * One upgrade note: notifications posted before 6h used the hash id, so a
 * pre-upgrade entry can linger in the drawer until swiped — the new id
 * cannot cancel an id the store never assigned.
 */
class AgentDoneIdStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The stable id for [sessionID], assigning a fresh one on first sight. */
    fun idFor(sessionID: String): Int = synchronized(LOCK) {
        val assigned = assignedIds()
        val result = assignAgentDoneId(sessionID, assigned, nextFree())
        if (result.isNew) {
            prefs.edit()
                .putInt(KEY_PREFIX + sessionID, result.id)
                .putInt(KEY_NEXT, result.nextFree)
                .apply()
        }
        result.id
    }

    /** The id assigned earlier, or null when this session never got one. */
    fun idIfPresent(sessionID: String): Int? = synchronized(LOCK) {
        assignedIds()[sessionID]
    }

    private fun assignedIds(): Map<String, Int> =
        prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(KEY_PREFIX) && value is Int) {
                key.removePrefix(KEY_PREFIX) to value
            } else {
                null
            }
        }.toMap()

    private fun nextFree(): Int = prefs.getInt(KEY_NEXT, FIRST_ID)

    companion object {
        private const val PREFS_NAME = "agent_done_notification_ids"
        private const val KEY_PREFIX = "id_"
        private const val KEY_NEXT = "next_id"
        internal const val FIRST_ID = 10_000
        private val LOCK = Any()
    }
}

/** Result of one id assignment: the id, the next free id, and whether it is new. */
internal data class AgentDoneIdResult(val id: Int, val nextFree: Int, val isNew: Boolean)

/**
 * The pure assignment step (55-F8/56-F5), testable without Android: an
 * already-known session keeps its id, a new one takes the next free id.
 */
internal fun assignAgentDoneId(
    sessionID: String,
    assigned: Map<String, Int>,
    nextFree: Int,
): AgentDoneIdResult {
    val existing = assigned[sessionID]
    return if (existing != null) {
        AgentDoneIdResult(existing, nextFree, isNew = false)
    } else {
        AgentDoneIdResult(nextFree, nextFree + 1, isNew = true)
    }
}
