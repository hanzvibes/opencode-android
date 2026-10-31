package dev.bidzz.opencode.ui.sessions

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Relative age formatting for the session list.
 *
 * Worth testing because the boundaries are easy to get wrong in a way that looks
 * plausible: "60m ago" instead of "1h ago", or a future timestamp rendering as a
 * huge negative age.
 */
class SessionAgeTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `sub-minute is just now`() {
        assertEquals("just now", formatAge(now - 30_000, now))
    }

    @Test
    fun `minutes`() {
        assertEquals("1m ago", formatAge(now - 60_000, now))
        assertEquals("59m ago", formatAge(now - 59 * 60_000, now))
    }

    @Test
    fun `hours`() {
        assertEquals("1h ago", formatAge(now - 60 * 60_000, now))
        assertEquals("23h ago", formatAge(now - 23 * 3_600_000, now))
    }

    @Test
    fun `days`() {
        assertEquals("1d ago", formatAge(now - 24 * 3_600_000, now))
        assertEquals("29d ago", formatAge(now - 29 * 86_400_000L, now))
    }

    @Test
    fun `months beyond thirty days`() {
        assertEquals("1mo ago", formatAge(now - 30 * 86_400_000L, now))
        assertEquals("2mo ago", formatAge(now - 75 * 86_400_000L, now))
    }

    @Test
    fun `missing or zero timestamp renders empty`() {
        // A session the engine has not stamped yet must not show "20658d ago".
        assertEquals("", formatAge(null, now))
        assertEquals("", formatAge(0, now))
    }

    @Test
    fun `a future timestamp does not go negative`() {
        // Clock skew between engine and app is possible; "just now" is the honest
        // answer, not "-2m ago".
        assertEquals("just now", formatAge(now + 120_000, now))
    }
}
