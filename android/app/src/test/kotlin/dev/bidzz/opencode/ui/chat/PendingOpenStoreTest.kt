package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D13 (m-1004): a pending deep link expires. The TTL is about
 * *staleness*, not restarts — a fresh link still survives a view-model
 * restart (D12 m-379), but a link abandoned at the lock screen must not
 * open the old session over the restored one hours later.
 */
class PendingOpenStoreTest {

    @Test
    fun `m-1004 a fresh pending open loads back`() {
        var now = 1_000L
        val store = InMemoryPendingOpenStore(nowMillis = { now })
        store.savePendingOpens(listOf("ses-first", "ses-second"))
        assertEquals(listOf("ses-first", "ses-second"), store.loadPendingOpens())
    }

    @Test
    fun `m-1004 a pending open older than the TTL is dropped on load`() {
        var now = 1_000L
        val store = InMemoryPendingOpenStore(nowMillis = { now })
        store.savePendingOpens(listOf("ses-old"))
        now += PENDING_OPEN_TTL_MS + 1
        assertEquals(emptyList<String>(), store.loadPendingOpens())
    }

    @Test
    fun `m-1004 a pending open just inside the TTL still loads`() {
        var now = 1_000L
        val store = InMemoryPendingOpenStore(nowMillis = { now })
        store.savePendingOpens(listOf("ses-fresh"))
        now += PENDING_OPEN_TTL_MS - 1
        assertEquals(listOf("ses-fresh"), store.loadPendingOpens())
    }

    @Test
    fun `m-1004 re-saving the queue does not renew a stale id`() {
        var now = 1_000L
        val store = InMemoryPendingOpenStore(nowMillis = { now })
        store.savePendingOpens(listOf("ses-first"))
        // Nine minutes later a second link arrives; the queue is
        // re-saved with both — the first id keeps its original stamp.
        now += 9 * 60 * 1000L
        store.savePendingOpens(listOf("ses-first", "ses-second"))
        // Two minutes after that the first id is eleven minutes old.
        now += 2 * 60 * 1000L
        assertEquals(listOf("ses-second"), store.loadPendingOpens())
    }

    @Test
    fun `m-1004 addPendingOpen appends without duplicating`() {
        val store = InMemoryPendingOpenStore()
        store.addPendingOpen("ses-a")
        store.addPendingOpen("ses-b")
        store.addPendingOpen("ses-a")
        assertEquals(listOf("ses-a", "ses-b"), store.loadPendingOpens())
    }

    @Test
    fun `m-1004 a pre-D13 bare id deserializes as fresh`() {
        val entries = TimestampedOpens(nowMillis = { 5_000L })
        entries.deserialize("ses-legacy")
        assertEquals(listOf("ses-legacy"), entries.freshIds())
    }

    @Test
    fun `m-1004 timestamped rows round-trip through serialize`() {
        var now = 7_000L
        val first = TimestampedOpens(nowMillis = { now })
        first.set(listOf("ses-a", "ses-b"))
        now += 60_000L
        first.set(listOf("ses-a", "ses-b", "ses-c"))
        val second = TimestampedOpens(nowMillis = { now })
        second.deserialize(first.serialize())
        assertEquals(listOf("ses-a", "ses-b", "ses-c"), second.freshIds())
        // The first two ids keep their original stamp: eleven minutes
        // after the first save they expire together.
        now += 10 * 60 * 1000L + 1
        assertTrue(second.freshIds().isEmpty())
    }

    @Test
    fun `m-1004 the pending restore id has no TTL`() {
        var now = 1_000L
        val store = InMemoryPendingOpenStore(nowMillis = { now })
        store.savePendingRestore("ses-last")
        now += 24 * 60 * 60 * 1000L
        assertEquals("ses-last", store.loadPendingRestore())
    }
}
