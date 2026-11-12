package dev.bidzz.opencode.ui.chat

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Base64

/**
 * Key under which a composer typed before any session exists is parked
 * (95-F6): a fresh chat has no session id, so [ChatViewModel.parkComposer]
 * used to return early and the text was lost on the first session switch.
 * The unsent composer is a real parked entry under this fixed key, taken
 * back by [ChatViewModel.newSession] — so text typed in a fresh chat
 * follows the user into the new conversation instead of vanishing.
 */
internal const val UNSENT_SESSION_KEY = "__unsent__"

/**
 * A composer parked per session (audit-70 20-F4, Fase 1a): the draft, the
 * pending attachment chips, and — since 1a — the queued follow-ups (13-F6).
 * The queue is part of the parked state because a follow-up typed while the
 * agent worked belongs to the session it was queued in, exactly like the
 * draft; parking only the draft threw the queue away on every switch.
 */
data class ParkedComposer(
    val draft: String,
    val attachments: List<AttachedFile>,
    val queued: List<QueuedPrompt> = emptyList(),
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Persists parked composers across process death (57-F1, 95-F7).
 *
 * The parked map used to live only in memory: a force-stop or a
 * low-memory kill wiped every parked draft except the active session's
 * (which survives via the saved-state handle). This store puts one entry
 * per session id on disk, following the [ReadPositionStore] pattern — one
 * prefs file, one value per session — so coming back after the process
 * died finds the text still there.
 *
 * Values are Java-serialised ([ParkedComposer], [AttachedFile] and
 * [QueuedPrompt] are all `Serializable` with explicit `serialVersionUID`s)
 * and Base64'd into a string: the same mechanism the saved-state handle
 * already uses for the live queue, with no new dependency and no new
 * build plugin. A value that no longer decodes — corrupt bytes, or a
 * class that changed shape — reads back as "no parked composer" instead
 * of crashing; the text is already gone by then, and inventing a
 * replacement would be worse.
 *
 * Entries are removed when taken, when parked empty, and when their queue
 * drains (see [removeQueued]): a parked composer for a deleted session is
 * the only case that lingers, and it is one small string per session.
 */
interface ParkedComposerStore {
    fun get(sessionId: String): ParkedComposer?
    fun put(sessionId: String, composer: ParkedComposer)
    fun remove(sessionId: String)

    /**
     * Drops one queued entry from a parked composer (13-F1): the flush
     * sends the head entry and, once the engine accepted it, the parked
     * copy must go too — otherwise reopening the session would flush the
     * same follow-up a second time. A no-op when the entry is not there.
     * An entry whose draft, attachments and queue are all empty afterwards
     * is removed entirely.
     */
    fun removeQueued(sessionId: String, entry: QueuedPrompt) {
        val current = get(sessionId) ?: return
        val kept = current.queued.filter { it != entry }
        if (kept.size == current.queued.size) return
        if (current.draft.isBlank() && current.attachments.isEmpty() && kept.isEmpty()) {
            remove(sessionId)
        } else {
            put(sessionId, current.copy(queued = kept))
        }
    }
}

/** In-memory store: the default for unit tests, and what a view model constructed without a store uses. */
class InMemoryParkedComposerStore : ParkedComposerStore {
    private val map = mutableMapOf<String, ParkedComposer>()
    override fun get(sessionId: String): ParkedComposer? = map[sessionId]
    override fun put(sessionId: String, composer: ParkedComposer) {
        map[sessionId] = composer
    }
    override fun remove(sessionId: String) {
        map.remove(sessionId)
    }
}

/** SharedPreferences-backed store: the production implementation. */
class SharedPrefsParkedComposerStore(context: Context) : ParkedComposerStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "parked_composers"
        private const val KEY_PREFIX = "composer_"
    }

    override fun get(sessionId: String): ParkedComposer? {
        val encoded = prefs.getString(KEY_PREFIX + sessionId, null) ?: return null
        return decodeParkedComposer(encoded)
    }

    override fun put(sessionId: String, composer: ParkedComposer) {
        prefs.edit().putString(KEY_PREFIX + sessionId, encodeParkedComposer(composer)).apply()
    }

    override fun remove(sessionId: String) {
        prefs.edit().remove(KEY_PREFIX + sessionId).apply()
    }
}

/**
 * Encodes a parked composer for storage. Pure — `java.util.Base64`, not
 * the Android one, so the round-trip is unit-tested here on the JVM rather
 * than through a view model or SharedPreferences (minSdk 29, so the JDK
 * Base64 is available on device too).
 */
internal fun encodeParkedComposer(composer: ParkedComposer): String {
    val bytes = ByteArrayOutputStream().use { out ->
        ObjectOutputStream(out).use { it.writeObject(composer) }
        out.toByteArray()
    }
    return Base64.getEncoder().withoutPadding().encodeToString(bytes)
}

/** Decodes a stored composer, or null when the bytes are not one anymore. */
internal fun decodeParkedComposer(encoded: String): ParkedComposer? {
    return try {
        val bytes = Base64.getDecoder().decode(encoded)
        ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as? ParkedComposer }
    } catch (_: Exception) {
        null
    }
}
