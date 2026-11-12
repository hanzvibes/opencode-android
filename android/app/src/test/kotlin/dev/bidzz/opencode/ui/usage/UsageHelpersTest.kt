package dev.bidzz.opencode.ui.usage

import dev.bidzz.opencode.core.api.dto.UsageBudget
import dev.bidzz.opencode.core.api.dto.UsageMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageHelpersTest {

    @Test
    fun `meter fraction clamps to zero to one`() {
        assertEquals(0.425f, UsageMeter(percent = 42.5).fraction(), 0.001f)
        assertEquals(0f, UsageMeter(percent = -5.0).fraction(), 0.001f)
        assertEquals(1f, UsageMeter(percent = 150.0).fraction(), 0.001f)
    }

    @Test
    fun `budget spent fraction divides spent by limit`() {
        assertEquals(0.25f, UsageBudget(limit = 50.0, spent = 12.5).spentFraction(), 0.001f)
        assertEquals(1f, UsageBudget(limit = 10.0, spent = 12.0).spentFraction(), 0.001f)
        assertEquals(0f, UsageBudget(limit = 0.0, spent = 5.0).spentFraction(), 0.001f)
    }

    @Test
    fun `reset date formats as a local calendar date`() {
        // 1794052727000 ms is 2026-12-01 in UTC; the helper uses the
        // device zone, so only the shape is asserted, not the day.
        val date = formatResetDate(1794052727000L)
        assertEquals(10, date?.length)
        assertEquals('-', date?.get(4))
        assertEquals('-', date?.get(7))
        assertNull(formatResetDate(null))
    }
}
