package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 6 batch 6c (13-F4): clearing the draft after a send must compare
 * identity, not value — a user who retypes the identical text while the
 * send is in flight loses the retype when only the value is compared.
 */
class ClearSentDraftTest {

    @Test
    fun `13-F4 an untouched draft equal to the sent text is cleared`() {
        assertTrue(shouldClearSentDraft("cek log", "cek log", editsSinceSend = 0))
    }

    @Test
    fun `13-F4 an identical retype during the send is kept`() {
        assertFalse(shouldClearSentDraft("cek log", "cek log", editsSinceSend = 1))
    }

    @Test
    fun `13-F4 a different draft is kept even when untouched`() {
        assertFalse(shouldClearSentDraft("cek log baru", "cek log", editsSinceSend = 0))
    }

    @Test
    fun `13-F4 surrounding whitespace does not matter for the value match`() {
        assertTrue(shouldClearSentDraft("  cek log  ", "cek log", editsSinceSend = 0))
    }
}
