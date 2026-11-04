package dev.bidzz.opencode.ui.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Relative age calculation for the session list.
 *
 * Worth testing because the boundaries are easy to get wrong in a way that looks
 * plausible: 60 minutes instead of 1 hour, or a future timestamp rendering as a
 * huge negative age. The wording itself is a string resource per language.
 */
class SessionAgeTest {

    private val now = 1_800_000_000_000L

    private fun age(unit: AgeUnit, value: Int = 0) = SessionAge(unit, value)

    @Test
    fun `sub-minute is now`() {
        assertEquals(age(AgeUnit.NOW), formatAge(now - 30_000, now))
    }

    @Test
    fun `minutes`() {
        assertEquals(age(AgeUnit.MINUTES, 1), formatAge(now - 60_000, now))
        assertEquals(age(AgeUnit.MINUTES, 59), formatAge(now - 59 * 60_000, now))
    }

    @Test
    fun `hours`() {
        assertEquals(age(AgeUnit.HOURS, 1), formatAge(now - 60 * 60_000, now))
        assertEquals(age(AgeUnit.HOURS, 23), formatAge(now - 23 * 3_600_000, now))
    }

    @Test
    fun `days`() {
        assertEquals(age(AgeUnit.DAYS, 1), formatAge(now - 24 * 3_600_000, now))
        assertEquals(age(AgeUnit.DAYS, 29), formatAge(now - 29 * 86_400_000L, now))
    }

    @Test
    fun `months beyond thirty days`() {
        assertEquals(age(AgeUnit.MONTHS, 1), formatAge(now - 30 * 86_400_000L, now))
        assertEquals(age(AgeUnit.MONTHS, 2), formatAge(now - 75 * 86_400_000L, now))
    }

    @Test
    fun `missing or zero timestamp has no age`() {
        // A session the engine has not stamped yet must not show "20658d ago".
        assertNull(formatAge(null, now))
        assertNull(formatAge(0, now))
    }

    @Test
    fun `a future timestamp does not go negative`() {
        // Clock skew between engine and app is possible; "now" is the honest answer, not "-2m".
        assertEquals(age(AgeUnit.NOW), formatAge(now + 120_000, now))
    }
}
