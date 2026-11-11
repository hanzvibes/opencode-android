package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Opsi A ("Tenang, tapi rapi"): the tool trace's default-open rule and
 * the turn clock. Both are pure — the composables only read them, so
 * the collapsed-by-default behaviour is pinned here, not in a preview.
 */
class ChatTurnMetaTest {

    @Test
    fun `finished clean run starts collapsed`() {
        assertFalse(toolTraceInitialOpen(streaming = false, hasError = false))
    }

    @Test
    fun `streaming run starts open so live progress stays visible`() {
        assertTrue(toolTraceInitialOpen(streaming = true, hasError = false))
    }

    @Test
    fun `failed run starts open so the error is never hidden`() {
        assertTrue(toolTraceInitialOpen(streaming = false, hasError = true))
        assertTrue(toolTraceInitialOpen(streaming = true, hasError = true))
    }

    @Test
    fun `clean finished run auto-collapses unless the reader expanded it`() {
        assertTrue(
            shouldAutoCollapseToolTrace(
                userExpanded = false,
                streaming = false,
                hasError = false,
            ),
        )
        assertFalse(
            shouldAutoCollapseToolTrace(
                userExpanded = true,
                streaming = false,
                hasError = false,
            ),
        )
    }

    @Test
    fun `streaming or failed run never auto-collapses`() {
        assertFalse(
            shouldAutoCollapseToolTrace(
                userExpanded = false,
                streaming = true,
                hasError = false,
            ),
        )
        assertFalse(
            shouldAutoCollapseToolTrace(
                userExpanded = false,
                streaming = false,
                hasError = true,
            ),
        )
    }

    @Test
    fun `turn clock is a short HH-mm time in the device timezone`() {
        val clock = formatClock(1_791_800_400_000L)
        assertTrue(clock.matches(Regex("\\d{2}:\\d{2}")))
        // Same instant, same clock — the format never depends on call order.
        assertEquals(clock, formatClock(1_791_800_400_000L))
    }

    @Test
    fun `turn clock moves with the instant`() {
        val earlier = formatClock(1_791_800_400_000L)
        val later = formatClock(1_791_800_400_000L + 3_600_000L)
        assertTrue(earlier != later)
    }
}
