package dev.bidzz.opencode.ui.chat

import android.content.Context
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
        if (queue.isEmpty()) {
            prefs.edit().remove(KEY_QUEUE).apply()
        } else {
            prefs.edit().putString(KEY_QUEUE, encodeCodeSaveQueue(queue)).apply()
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
 * Encodes the queue for storage. Pure — `java.util.Base64`, not the
 * Android one, so the round-trip is unit-tested here on the JVM (minSdk
 * 29, so the JDK Base64 is available on device too).
 */
internal fun encodeCodeSaveQueue(queue: List<CodeSaveRequest>): String {
    val bytes = ByteArrayOutputStream().use { out ->
        ObjectOutputStream(out).use { it.writeObject(ArrayList(queue)) }
        out.toByteArray()
    }
    return Base64.getEncoder().withoutPadding().encodeToString(bytes)
}

/** Decodes a stored queue, or empty when the bytes are not one anymore. */
internal fun decodeCodeSaveQueue(encoded: String): List<CodeSaveRequest> {
    return try {
        val bytes = Base64.getDecoder().decode(encoded)
        @Suppress("UNCHECKED_CAST")
        (ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() } as? ArrayList<CodeSaveRequest>)
            ?.toList().orEmpty()
    } catch (_: Exception) {
        emptyList()
    }
}
