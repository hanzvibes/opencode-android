package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Audit-200 B3 (M-242): what [ChatViewModel.returnQueueToDraft] does with
 * the queued follow-ups. The entry the flush is sending stays queued —
 * its POST may still land — while everything else goes back to the
 * composer. The split is pure ([resolveReturnedQueue]) so it is testable
 * without a live engine.
 */
class ChatReturnedQueueTest {

    private fun entry(text: String) = QueuedPrompt(fullText = text, typedText = text)

    @Test
    fun `with no in-flight entry everything returns`() {
        val queued = listOf(entry("one"), entry("two"))
        val (toReturn, toKeep) = resolveReturnedQueue(queued, inFlightId = null)
        assertEquals(queued, toReturn)
        assertEquals(emptyList<QueuedPrompt>(), toKeep)
    }

    @Test
    fun `the in-flight head entry stays queued while the rest returns`() {
        val head = entry("sending")
        val queued = listOf(head, entry("later"), entry("last"))
        val (toReturn, toKeep) = resolveReturnedQueue(queued, inFlightId = head.id)
        assertEquals(listOf(queued[1], queued[2]), toReturn)
        assertEquals(listOf(head), toKeep)
    }

    @Test
    fun `the in-flight entry is matched by id, not by text`() {
        // Two follow-ups with the same words are two different
        // follow-ups (audit-200 A5, DL-001): only the one being sent
        // stays, its twin goes back to the composer.
        val first = entry("same words")
        val twin = entry("same words")
        val queued = listOf(first, twin)
        val (toReturn, toKeep) = resolveReturnedQueue(queued, inFlightId = first.id)
        assertEquals(listOf(twin), toReturn)
        assertEquals(listOf(first), toKeep)
    }

    @Test
    fun `an unknown in-flight id returns everything`() {
        val queued = listOf(entry("one"), entry("two"))
        val (toReturn, toKeep) = resolveReturnedQueue(queued, inFlightId = "no-such-entry")
        assertEquals(queued, toReturn)
        assertEquals(emptyList<QueuedPrompt>(), toKeep)
    }
}
