package dev.bidzz.opencode.ui

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Copy feedback must only claim success when the copy really landed
 * (audit-100 65-F4): a "Copied" check that fires while the clipboard
 * service is missing or the set throws is a lie the user cannot detect.
 */
class ClipboardTest {

    private class FakeClipboardManager(
        private val throwOnSet: Boolean = false,
    ) : ClipboardManager {
        var lastText: AnnotatedString? = null

        override fun setText(annotatedString: AnnotatedString) {
            if (throwOnSet) throw IllegalStateException("no clipboard service")
            lastText = annotatedString
        }

        override fun getText(): AnnotatedString? = lastText

        override fun hasText(): Boolean = lastText != null
    }

    @Test
    fun `setClipboardText returns true and stores the text on success`() {
        val manager = FakeClipboardManager()
        assertTrue(setClipboardText(manager, AnnotatedString("hello")))
        assertEquals("hello", manager.lastText?.text)
    }

    @Test
    fun `setClipboardText returns false when the set throws`() {
        val manager = FakeClipboardManager(throwOnSet = true)
        assertFalse(setClipboardText(manager, AnnotatedString("hello")))
        assertEquals(null, manager.lastText)
    }
}
