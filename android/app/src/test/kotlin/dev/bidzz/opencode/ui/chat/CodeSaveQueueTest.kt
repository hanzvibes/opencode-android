package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 1c (05-F2): the pending code-save requests are a persisted FIFO
 * queue, not a single in-memory slot. The queue and its encoding are
 * pure JVM, so they are tested here rather than through the application
 * (which needs a Context) or the system picker.
 */
class CodeSaveQueueTest {

    private fun request(name: String = "a.kt", text: String = "code") =
        CodeSaveRequest(name, text)

    @Test
    fun `enqueue then take returns requests in order`() {
        val queue = CodeSaveQueue()
        queue.enqueue(request("a.kt", "aaa"))
        queue.enqueue(request("b.kt", "bbb"))

        assertEquals(request("a.kt", "aaa"), queue.take())
        assertEquals(request("b.kt", "bbb"), queue.take())
        assertNull(queue.take())
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `peek does not consume`() {
        val queue = CodeSaveQueue()
        queue.enqueue(request())

        assertEquals(request(), queue.peek())
        assertEquals(request(), queue.peek())
        assertEquals(request(), queue.take())
    }

    @Test
    fun `take on an empty queue returns null`() {
        assertNull(CodeSaveQueue().take())
        assertNull(CodeSaveQueue().peek())
    }

    @Test
    fun `a queue restored from the same store keeps its requests`() {
        val store = InMemoryCodeSaveQueueStore()
        val first = CodeSaveQueue(store)
        first.enqueue(request("a.kt", "aaa"))
        first.enqueue(request("b.kt", "bbb"))

        // A new queue over the same store — what the application sees
        // after a process death — reads the requests back in order.
        val restored = CodeSaveQueue(store)

        assertEquals(request("a.kt", "aaa"), restored.take())
        assertEquals(request("b.kt", "bbb"), restored.take())
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `encode then decode returns the same queue`() {
        val queue = listOf(request("a.kt", "aaa"), request("b.kt", "bbb"))

        assertEquals(queue, decodeCodeSaveQueue(encodeCodeSaveQueue(queue)))
        assertEquals(emptyList<CodeSaveRequest>(), decodeCodeSaveQueue(encodeCodeSaveQueue(emptyList())))
    }

    @Test
    fun `decode of garbage returns an empty queue instead of crashing`() {
        assertEquals(emptyList<CodeSaveRequest>(), decodeCodeSaveQueue("bukan-base64!!!"))
        assertEquals(emptyList<CodeSaveRequest>(), decodeCodeSaveQueue("aGVsbG8"))
    }

    @Test
    fun `DL-025 enqueueFirst puts a retry ahead of newer requests`() {
        val queue = CodeSaveQueue()
        queue.enqueue(request("b.kt", "bbb"))
        queue.enqueue(request("c.kt", "ccc"))

        queue.enqueueFirst(request("a.kt", "aaa"))

        assertEquals(request("a.kt", "aaa"), queue.take())
        assertEquals(request("b.kt", "bbb"), queue.take())
        assertEquals(request("c.kt", "ccc"), queue.take())
    }

    @Test
    fun `DL-024 enqueueAfterHead keeps the head and jumps the rest`() {
        val queue = CodeSaveQueue()
        queue.enqueue(request("b.kt", "bbb"))
        queue.enqueue(request("c.kt", "ccc"))

        queue.enqueueAfterHead(request("a.kt", "aaa"))

        assertEquals(request("b.kt", "bbb"), queue.take())
        assertEquals(request("a.kt", "aaa"), queue.take())
        assertEquals(request("c.kt", "ccc"), queue.take())
    }

    @Test
    fun `DL-024 enqueueAfterHead on an empty queue just adds the request`() {
        val queue = CodeSaveQueue()

        queue.enqueueAfterHead(request("a.kt", "aaa"))

        assertEquals(request("a.kt", "aaa"), queue.take())
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `DL-026 one corrupt entry is dropped while the rest survive`() {
        val queue = listOf(request("a.kt", "aaa"), request("b.kt", "bbb"), request("c.kt", "ccc"))
        val encoded = encodeCodeSaveQueue(queue)
        val parts = encoded.split("\n")
        val corrupted = listOf(parts[0], "!!!not-base64!!!", parts[2]).joinToString("\n")

        val decoded = decodeCodeSaveQueue(corrupted)

        assertEquals(listOf(request("a.kt", "aaa"), request("c.kt", "ccc")), decoded)
    }

    @Test
    fun `DL-026 a queue written by the old whole-blob format still reads back`() {
        val queue = listOf(request("a.kt", "aaa"), request("b.kt", "bbb"))
        val legacy = java.io.ByteArrayOutputStream().use { out ->
            java.io.ObjectOutputStream(out).use { it.writeObject(java.util.ArrayList(queue)) }
            out.toByteArray()
        }.let { java.util.Base64.getEncoder().withoutPadding().encodeToString(it) }

        assertEquals(queue, decodeCodeSaveQueue(legacy))
    }

    @Test
    fun `DL-026 a one-entry queue round-trips`() {
        val queue = listOf(request("a.kt", "aaa"))

        assertEquals(queue, decodeCodeSaveQueue(encodeCodeSaveQueue(queue)))
    }
}
