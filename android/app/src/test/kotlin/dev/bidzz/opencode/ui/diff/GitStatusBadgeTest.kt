package dev.bidzz.opencode.ui.diff

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The diff badge never claims a file was Modified when its status is
 * unknown (audit-100 Fase 6b, 24-F6): only the three engine-documented
 * statuses get a letter.
 */
class GitStatusBadgeTest {

    @Test
    fun `documented statuses map to their letters`() {
        assertEquals("A", gitStatusLabel("added"))
        assertEquals("D", gitStatusLabel("deleted"))
        assertEquals("M", gitStatusLabel("modified"))
    }

    @Test
    fun `an unknown status reads question mark, not Modified`() {
        assertEquals("?", gitStatusLabel("renamed"))
        assertEquals("?", gitStatusLabel("untracked"))
        assertEquals("?", gitStatusLabel(""))
    }
}
