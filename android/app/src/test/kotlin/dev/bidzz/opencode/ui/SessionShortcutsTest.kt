package dev.bidzz.opencode.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionShortcutsTest {

    @Test
    fun `session title is the label when present`() {
        assertEquals("Fix scroll bug", lastSessionShortcutLabel("Fix scroll bug", "Continue last session"))
    }

    @Test
    fun `blank title falls back`() {
        assertEquals("Continue last session", lastSessionShortcutLabel("", "Continue last session"))
        assertEquals("Continue last session", lastSessionShortcutLabel(null, "Continue last session"))
    }
}
