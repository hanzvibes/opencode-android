package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An undeliverable question answer is appended to the draft exactly once
 * (audit-100 Fase 6b, 10-F2): re-tapping an expired card after a reload
 * must not duplicate the line.
 */
class QuestionDraftTest {

    @Test
    fun `an empty draft takes the answer as-is`() {
        assertEquals("Yes", resolveDraftAppend("", "Yes"))
    }

    @Test
    fun `an answer appends after typed text`() {
        assertEquals("hello\nYes", resolveDraftAppend("hello", "Yes"))
    }

    @Test
    fun `an answer already on its own line is not appended again`() {
        assertEquals("hello\nYes", resolveDraftAppend("hello\nYes", "Yes"))
        assertEquals("Yes", resolveDraftAppend("Yes", "Yes"))
    }
}
