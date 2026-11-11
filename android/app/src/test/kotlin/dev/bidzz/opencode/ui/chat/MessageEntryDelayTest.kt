package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageEntryDelayTest {

    @Test
    fun `newest message enters immediately`() {
        assertEquals(0, messageEntryDelayMillis(9, 9))
        assertEquals(0, messageEntryDelayMillis(0, 0))
    }

    @Test
    fun `older messages stagger one step apart`() {
        assertEquals(30, messageEntryDelayMillis(8, 9))
        assertEquals(60, messageEntryDelayMillis(7, 9))
        assertEquals(90, messageEntryDelayMillis(6, 9))
    }

    @Test
    fun `stagger is capped so a long history never delays entry`() {
        assertEquals(150, messageEntryDelayMillis(0, 100))
        assertEquals(150, messageEntryDelayMillis(0, 9))
    }

    @Test
    fun `out of range indices never go negative`() {
        assertEquals(0, messageEntryDelayMillis(10, 9))
    }
}
