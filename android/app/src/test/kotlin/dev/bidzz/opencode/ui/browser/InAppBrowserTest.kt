package dev.bidzz.opencode.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppBrowserTest {

    @Test
    fun `http and https with a host are browsable`() {
        assertTrue(isBrowsableUrl("https://example.com"))
        assertTrue(isBrowsableUrl("http://example.com/path?q=1"))
        assertTrue(isBrowsableUrl("HTTPS://EXAMPLE.COM"))
    }

    @Test
    fun `non-web schemes are not browsable`() {
        assertFalse(isBrowsableUrl("mailto:rehan@example.com"))
        assertFalse(isBrowsableUrl("tel:+621234567"))
        assertFalse(isBrowsableUrl("javascript:alert(1)"))
        assertFalse(isBrowsableUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(isBrowsableUrl("file:///storage/emulated/0/notes.txt"))
        assertFalse(isBrowsableUrl("opencode://session/abc"))
    }

    @Test
    fun `relative and empty urls are not browsable`() {
        assertFalse(isBrowsableUrl("/relative/path"))
        assertFalse(isBrowsableUrl("example.com"))
        assertFalse(isBrowsableUrl(""))
        assertFalse(isBrowsableUrl("https://"))
    }

    @Test
    fun `display host falls back to the raw url`() {
        assertEquals("example.com", browserDisplayHost("https://example.com/docs"))
        assertEquals("not a url", browserDisplayHost("not a url"))
    }
}
