package dev.bidzz.opencode

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D6 (m-593): the snackbar queue is the one piece of this
 * batch that is pure enough to unit-test directly — a bounded buffer
 * (16) in front of a flow. The 17th offer in a burst is refused
 * instead of suspending the caller, and what is offered drains in
 * arrival order.
 */
class SnackbarQueueTest {

    @Test
    fun `the first 16 offers succeed and the 17th is refused`() {
        val queue = SnackbarQueue()
        repeat(SnackbarQueue.BUFFER_CAPACITY) {
            assertTrue(queue.tryOffer(SnackbarQueue.Request(message = "m$it")))
        }
        assertFalse(queue.tryOffer(SnackbarQueue.Request(message = "overflow")))
    }

    @Test
    fun `offered requests drain in arrival order`() = runBlocking {
        val queue = SnackbarQueue()
        queue.tryOffer(SnackbarQueue.Request(message = "first"))
        queue.tryOffer(SnackbarQueue.Request(message = "second"))
        val drained = withTimeout(5_000) { queue.requests.take(2).toList() }
        assertEquals(listOf("first", "second"), drained.map { it.message })
    }

    @Test
    fun `a drained slot accepts a new offer`() {
        val queue = SnackbarQueue()
        repeat(SnackbarQueue.BUFFER_CAPACITY) {
            assertTrue(queue.tryOffer(SnackbarQueue.Request(message = "m$it")))
        }
        assertFalse(queue.tryOffer(SnackbarQueue.Request(message = "overflow")))
        runBlocking {
            withTimeout(5_000) { queue.requests.take(1).toList() }
        }
        assertTrue(queue.tryOffer(SnackbarQueue.Request(message = "after drain")))
    }
}
