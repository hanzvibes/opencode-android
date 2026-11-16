package dev.bidzz.opencode.ui.chat

import android.content.Context

/**
 * Where deep-link / restore ids waiting for attach live (audit-200
 * D12, m-379): a process death in the seconds before attach used to
 * lose them — the pending open and the pending restore were plain
 * in-memory fields, so the user had to tap the link again (or the
 * remembered session never came back). The store is the persistence
 * seam: the app passes the SharedPreferences-backed one, unit tests
 * get the in-memory one.
 *
 * Ids are joined with '\n' — [dev.bidzz.opencode.isValidSessionId]
 * only allows letters, digits, '_' , '-' and '.', so a session id can
 * never contain the separator.
 */
interface PendingOpenStore {
    /** Deep-link ids waiting for attach, oldest first. */
    fun loadPendingOpens(): List<String>
    fun savePendingOpens(ids: List<String>)
    /** The restore id [ChatViewModel.onResume] parked, if any. */
    fun loadPendingRestore(): String?
    fun savePendingRestore(id: String?)
}

/** In-memory store: what unit tests get (nothing survives the test). */
class InMemoryPendingOpenStore : PendingOpenStore {
    private var opens: List<String> = emptyList()
    private var restore: String? = null

    override fun loadPendingOpens(): List<String> = opens
    override fun savePendingOpens(ids: List<String>) {
        opens = ids.toList()
    }
    override fun loadPendingRestore(): String? = restore
    override fun savePendingRestore(id: String?) {
        restore = id
    }
}

/**
 * SharedPreferences-backed store. All writes are commit(), not apply()
 * — the id must be on disk before the park returns, or a force-stop in
 * the next instant loses it (same rule as lastSessionID and the parked
 * composers).
 */
class SharedPrefsPendingOpenStore(context: Context) : PendingOpenStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "pending_opens"
        private const val KEY_PENDING_OPENS = "pending_open_ids"
        private const val KEY_PENDING_RESTORE = "pending_restore_id"
    }

    override fun loadPendingOpens(): List<String> =
        prefs.getString(KEY_PENDING_OPENS, null)
            ?.split('\n')
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    override fun savePendingOpens(ids: List<String>) {
        prefs.edit().putString(KEY_PENDING_OPENS, ids.joinToString("\n")).commit()
    }

    override fun loadPendingRestore(): String? =
        prefs.getString(KEY_PENDING_RESTORE, null)

    override fun savePendingRestore(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_PENDING_RESTORE) else putString(KEY_PENDING_RESTORE, id)
        }.commit()
    }
}
