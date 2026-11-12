package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window head gets a date divider only when it really is a day
 * boundary (audit-100 Fase 6b, 01-F7): with older messages still to load,
 * its date is just where the window happened to start.
 */
class DateDividerTest {

    // Two instants exactly one day apart (2025-11-13 02:13 UTC and one
    // day later): the exact date does not matter, only that they fall on
    // different days — and exactly 24h apart means different days in any
    // zone, DST included. Tests run in the VM's UTC zone; isSameDay uses
    // the system default zone.
    private val dayOne = 1_763_000_000_000L
    private val dayTwo = dayOne + 86_400_000L // one day later

    @Test
    fun `a new day after a previous message shows the divider`() {
        assertTrue(shouldShowDateDivider(dayTwo, dayOne, false, false))
    }

    @Test
    fun `the same day as the previous message shows no divider`() {
        assertFalse(shouldShowDateDivider(dayOne + 60_000L, dayOne, false, false))
    }

    @Test
    fun `the window head with older messages to load shows no divider`() {
        assertFalse(shouldShowDateDivider(dayTwo, null, true, true))
    }

    @Test
    fun `the window head with nothing older shows the divider`() {
        assertTrue(shouldShowDateDivider(dayOne, null, true, false))
    }

    @Test
    fun `a message without a timestamp never starts a divider`() {
        assertFalse(shouldShowDateDivider(null, dayOne, false, false))
    }
}
