package dev.bidzz.opencode.ui.chat

import android.content.Context
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.ArrayDeque
import java.util.Base64

/**
 * A code block the user asked to save through the system picker (SAF).
 * Moved here from [dev.bidzz.opencode.OpenCodeApplication] so the queue
 * that owns it can be tested without the application: the request is
 * just a filename plus the code verbatim.
 */
data class CodeSaveRequest(val filename: String, val text: String) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Where the pending code-save queue lives on disk (05-F2). One entry:
 * the whole queue, Java-serialised and Base64'd — the same mechanism the
 * saved-state handle already uses for the live queue, and the parked
 * composers use for theirs (see [ParkedComposerStore]).
 */
interface CodeSaveQueueStore {
    fun load(): List<CodeSaveRequest>
    fun save(queue: List<CodeSaveRequest>)
}

/** In-memory store: the default for unit tests. */
class InMemoryCodeSaveQueueStore : CodeSaveQueueStore {
    private var held: List<CodeSaveRequest> = emptyList()
    override fun load(): List<CodeSaveRequest> = held
    override fun save(queue: List<CodeSaveRequest>) {
        held = queue.toList()
    }
}

/** SharedPreferences-backed store: the production implementation. */
class SharedPrefsCodeSaveQueueStore(context: Context) : CodeSaveQueueStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "pending_code_saves"
        private const val KEY_QUEUE = "queue"
    }

    override fun load(): List<CodeSaveRequest> {
        val encoded = prefs.getString(KEY_QUEUE, null) ?: return emptyList()
        return decodeCodeSaveQueue(encoded)
    }

    override fun save(queue: List<CodeSaveRequest>) {
        // Audit-200 A9-4 (DL-006): commit(), not apply() — the request
        // must be on disk before this returns. A process death in the
        // async flush window lost the queued save exactly when the user
        // had tapped Save and the picker was open; same rule as
        // lastSessionID (69-F10). One small string write.
        if (queue.isEmpty()) {
            prefs.edit().remove(KEY_QUEUE).commit()
        } else {
            prefs.edit().putString(KEY_QUEUE, encodeCodeSaveQueue(queue)).commit()
        }
    }
}

/**
 * The pending code-save requests, in the order the user tapped Save
 * (05-F2). The old shape was a single in-memory slot: a second Save
 * overwrote the first without feedback, and a process death while the
 * system picker was open meant nothing was ever written — the picker
 * came back to an activity whose pending request was already null. The
 * queue is persisted on every change, so the callback that arrives
 * after a recreation still finds the request it belongs to, and a
 * second Save waits its turn instead of destroying the first.
 */
class CodeSaveQueue(private val store: CodeSaveQueueStore = InMemoryCodeSaveQueueStore()) {
    private val queue: ArrayDeque<CodeSaveRequest> = ArrayDeque(store.load())

    /** Appends a request; the picker for it opens after the ones ahead. */
    fun enqueue(request: CodeSaveRequest) {
        queue.addLast(request)
        store.save(queue.toList())
    }

    /**
     * Prepends a request: a retry is the oldest work, so it goes first
     * (audit-200 A6, DL-025). Only safe when no picker is open — the head
     * is the request whose picker is currently open, and jumping it would
     * make that picker take the wrong request on its return.
     */
    fun enqueueFirst(request: CodeSaveRequest) {
        queue.addFirst(request)
        store.save(queue.toList())
    }

    /**
     * Inserts a request right behind the head (audit-200 A6, DL-024,
     * DL-025): the head is the request whose picker is currently open.
     * A failed save re-queues here — ahead of every request whose picker
     * has not opened yet, behind the one that has — so it survives the
     * snackbar and stays FIFO without breaking the open picker's pairing.
     */
    fun enqueueAfterHead(request: CodeSaveRequest) {
        val head = queue.pollFirst()
        queue.addFirst(request)
        if (head != null) queue.addFirst(head)
        store.save(queue.toList())
    }

    /** Takes the oldest request, or null when the queue is empty. */
    fun take(): CodeSaveRequest? {
        val head = queue.pollFirst() ?: return null
        store.save(queue.toList())
        return head
    }

    /** Peeks at the oldest request without consuming it (for the launch). */
    fun peek(): CodeSaveRequest? = queue.peekFirst()

    fun isEmpty(): Boolean = queue.isEmpty()
}

/**
 * Encodes the queue for storage: one Base64 blob per request, joined
 * with newlines (audit-200 A6, DL-026). Pure — `java.util.Base64`, not
 * the Android one, so the round-trip is unit-tested here on the JVM
 * (minSdk 29, so the JDK Base64 is available on device too).
 */
internal fun encodeCodeSaveQueue(queue: List<CodeSaveRequest>): String {
    return queue.joinToString("\n") { request ->
        val bytes = ByteArrayOutputStream().use { out ->
            ObjectOutputStream(out).use { it.writeObject(request) }
            out.toByteArray()
        }
        Base64.getEncoder().withoutPadding().encodeToString(bytes)
    }
}

/** Decodes one stored request, or null when its bytes are not one anymore. */
private fun decodeCodeSaveRequest(encoded: String): CodeSaveRequest? {
    return try {
        val bytes = Base64.getDecoder().decode(encoded)
        ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as? CodeSaveRequest }
    } catch (_: Exception) {
        null
    }
}

/**
 * Decodes a stored queue, or empty when the bytes are not one anymore.
 *
 * Audit-200 A6 (DL-026): one corrupt entry no longer wipes the whole
 * queue. Each part decodes on its own; a part that fails is dropped (and
 * logged) while the rest survive. A stored value with no newlines is
 * tried as one new-format entry first, then as the legacy whole-queue
 * blob, so queues written before this change still read back.
 */
internal fun decodeCodeSaveQueue(encoded: String): List<CodeSaveRequest> {
    if (encoded.isEmpty()) return emptyList()
    val parts = encoded.split('\n')
    val decoded = parts.mapNotNull { decodeCodeSaveRequest(it) }
    if (decoded.isNotEmpty()) {
        if (decoded.size != parts.size) {
            Log.w(
                "CodeSaveQueue",
                "Dropped ${parts.size - decoded.size} unreadable code-save request(s)",
            )
        }
        return decoded
    }
    return try {
        val bytes = Base64.getDecoder().decode(encoded)
        @Suppress("UNCHECKED_CAST")
        (ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() } as? ArrayList<CodeSaveRequest>)
            ?.toList().orEmpty()
    } catch (_: Exception) {
        emptyList()
    }
}
