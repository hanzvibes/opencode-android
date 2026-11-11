package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Step durations: the finished-step format and the running-step elapsed
 * time. Both are pure arithmetic — the ticking itself lives in the
 * composable and is not tested here.
 */
class ToolStepDurationTest {

    @Test
    fun `under a second shows milliseconds`() {
        assertEquals("350ms", formatStepDuration(350))
        assertEquals("0ms", formatStepDuration(0))
        assertEquals("999ms", formatStepDuration(999))
    }

    @Test
    fun `a second or more shows one decimal of seconds`() {
        assertEquals("1.0s", formatStepDuration(1000))
        assertEquals("1.2s", formatStepDuration(1250))
        assertEquals("59.9s", formatStepDuration(59_999))
    }

    @Test
    fun `live elapsed is now minus start`() {
        assertEquals(1500L, liveElapsedMillis(startedAtMillis = 1000, nowMillis = 2500))
        assertEquals(0L, liveElapsedMillis(startedAtMillis = 2500, nowMillis = 2500))
    }

    @Test
    fun `live elapsed never goes negative when the clock jumps back`() {
        assertEquals(0L, liveElapsedMillis(startedAtMillis = 2000, nowMillis = 1000))
    }
}
