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
 *
 * Audit-200 D13 (m-1004): a pending deep link expires. A link the user
 * abandoned — tap while locked, back out of the lock screen, open from
 * the launcher later — used to open the old session over the restored
 * one, however much later. Each id carries the wall-clock it was
 * parked at; [loadPendingOpens] only returns ids younger than
 * [PENDING_OPEN_TTL_MS]. The TTL is about *staleness*, not restarts: a
 * link parked at a cold start still survives a view-model restart
 * (D12 m-379) as long as it is fresh. The pending *restore* id has no
 * TTL — the last session is still the right one hours later.
 */
const val PENDING_OPEN_TTL_MS = 10 * 60 * 1000L

interface PendingOpenStore {
    /** Deep-link ids waiting for attach, oldest first, fresh only. */
    fun loadPendingOpens(): List<String>
    fun savePendingOpens(ids: List<String>)
    /** The restore id [ChatViewModel.onResume] parked, if any. */
    fun loadPendingRestore(): String?
    fun savePendingRestore(id: String?)

    /**
     * Append one id, keeping the queue order and every surviving id's
     * original stamp (audit-200 D13, m-525/m-948): the deliver point
     * persists the link before emitting it, so a process death in the
     * in-memory window no longer loses it.
     */
    fun addPendingOpen(id: String) {
        val ids = loadPendingOpens().toMutableList()
        if (!ids.contains(id)) {
            ids.add(id)
            savePendingOpens(ids)
        }
    }
}

/**
 * Timestamped pending-open entries, shared by both stores (audit-200
 * D13, m-1004). The stamp is the wall-clock the id was parked at;
 * [set] keeps each surviving id's original stamp, so re-saving the
 * queue cannot renew a stale link.
 */
internal class TimestampedOpens(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val entries = LinkedHashMap<String, Long>()

    /** Fresh ids, oldest first. */
    fun freshIds(): List<String> {
        pruneExpired()
        return entries.keys.toList()
    }

    /** Replace the queue, keeping each surviving id's original stamp. */
    fun set(ids: List<String>) {
        val now = nowMillis()
        val kept = LinkedHashMap<String, Long>()
        for (id in ids) kept[id] = entries[id] ?: now
        entries.clear()
        entries.putAll(kept)
    }

    /** Drop ids older than the TTL; true when anything was dropped. */
    fun pruneExpired(): Boolean {
        val now = nowMillis()
        val before = entries.size
        entries.entries.removeIf { now - it.value > PENDING_OPEN_TTL_MS }
        return entries.size != before
    }

    /** One line per id: "<epochMillis>|<id>". */
    fun serialize(): String = entries.entries.joinToString("\n") { "${it.value}|${it.key}" }

    fun deserialize(text: String?) {
        entries.clear()
        text?.split('\n')?.forEach { line ->
            if (line.isEmpty()) return@forEach
            val sep = line.indexOf('|')
            if (sep < 0) {
                // Pre-D13 rows stored bare ids — treat them as fresh, so
                // an id parked before the upgrade gets one full TTL.
                entries[line] = nowMillis()
            } else {
                entries[line.substring(sep + 1)] =
                    line.substring(0, sep).toLongOrNull() ?: nowMillis()
            }
        }
    }
}

/** In-memory store: what unit tests get (nothing survives the test). */
class InMemoryPendingOpenStore(
    nowMillis: () -> Long = System::currentTimeMillis,
) : PendingOpenStore {
    private val opens = TimestampedOpens(nowMillis)
    private var restore: String? = null

    override fun loadPendingOpens(): List<String> = opens.freshIds()
    override fun savePendingOpens(ids: List<String>) {
        opens.set(ids)
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
 * composers). Each operation re-reads the prefs, so two instances over
 * the same file (the application at deliver time, the view model at
 * attach time) see each other's writes.
 */
class SharedPrefsPendingOpenStore(
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PendingOpenStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "pending_opens"
        private const val KEY_PENDING_OPENS = "pending_open_ids"
        private const val KEY_PENDING_RESTORE = "pending_restore_id"
    }

    private fun readOpens(): TimestampedOpens =
        TimestampedOpens(nowMillis).also {
            it.deserialize(prefs.getString(KEY_PENDING_OPENS, null))
        }

    private fun writeOpens(opens: TimestampedOpens) {
        prefs.edit().putString(KEY_PENDING_OPENS, opens.serialize()).commit()
    }

    override fun loadPendingOpens(): List<String> {
        val opens = readOpens()
        if (opens.pruneExpired()) writeOpens(opens)
        return opens.freshIds()
    }

    override fun savePendingOpens(ids: List<String>) {
        val opens = readOpens()
        opens.set(ids)
        writeOpens(opens)
    }

    override fun loadPendingRestore(): String? =
        prefs.getString(KEY_PENDING_RESTORE, null)

    override fun savePendingRestore(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_PENDING_RESTORE) else putString(KEY_PENDING_RESTORE, id)
        }.commit()
    }
}
