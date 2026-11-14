package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fase 2 (13-F2/17-F1): what the busy→idle edge does with the queued
 * follow-ups. The decision is pure ([resolveRunEndAction]) so the table is
 * testable without a live engine; [ChatViewModel.onRepoState] applies it
 * and [ChatViewModel.stop] sets the `stopRequested` input (audit-200 B3,
 * M-080/M-376: the intent, not the `stopping` button flag — the flag
 * clears when the interrupt POST times out, the intent must not).
 */
class ChatStopFlushTest {

    private fun action(
        wasBusy: Boolean = true,
        busy: Boolean = false,
        stopRequested: Boolean = false,
        hasQueued: Boolean = true,
        sendInFlight: Boolean = false,
        flushing: Boolean = false,
        hasRepository: Boolean = true,
    ) = resolveRunEndAction(wasBusy, busy, stopRequested, hasQueued, sendInFlight, flushing, hasRepository)

    @Test
    fun `a run that ended on its own flushes the queue`() {
        assertEquals(RunEndAction.FLUSH, action())
    }

    @Test
    fun `13-F2 a stopped run returns the queue to the draft instead of flushing`() {
        assertEquals(RunEndAction.RETURN_TO_DRAFT, action(stopRequested = true))
    }

    @Test
    fun `no busy edge means nothing happens`() {
        assertEquals(RunEndAction.NONE, action(wasBusy = false))
        assertEquals(RunEndAction.NONE, action(busy = true))
    }

    @Test
    fun `17-F1 the stopping decision holds even while a send is in flight`() {
        // The flush could not start now, but the decision must not flip to
        // FLUSH later: the queue stays queued and the next edge decides.
        assertEquals(RunEndAction.NONE, action(stopRequested = true, sendInFlight = true))
        assertEquals(RunEndAction.NONE, action(sendInFlight = true))
        assertEquals(RunEndAction.NONE, action(flushing = true))
    }

    @Test
    fun `an empty queue or a missing repository leaves nothing to decide`() {
        assertEquals(RunEndAction.NONE, action(hasQueued = false))
        assertEquals(RunEndAction.NONE, action(hasRepository = false))
        assertEquals(RunEndAction.NONE, action(stopRequested = true, hasQueued = false))
    }
}
