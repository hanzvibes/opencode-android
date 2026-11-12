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

    @Test
    fun `53-F4 mailto and plain tel numbers may go to the system`() {
        assertTrue(isForwardableUrl("mailto:rehan@example.com"))
        assertTrue(isForwardableUrl("MAILTO:rehan@example.com"))
        assertTrue(isForwardableUrl("tel:+621234567"))
        assertTrue(isForwardableUrl("tel:021 555 1234"))
        assertTrue(isForwardableUrl("tel:(021) 555-1234"))
    }

    @Test
    fun `53-F4 MMI USSD and other schemes are never forwarded`() {
        assertFalse(isForwardableUrl("tel:*#06#"))
        assertFalse(isForwardableUrl("tel:*#*#4636#*#*"))
        assertFalse(isForwardableUrl("tel:123;123"))
        assertFalse(isForwardableUrl("tel:123,456"))
        assertFalse(isForwardableUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(isForwardableUrl("file:///storage/emulated/0/notes.txt"))
        assertFalse(isForwardableUrl("javascript:alert(1)"))
        assertFalse(isForwardableUrl("https://example.com"))
        assertFalse(isForwardableUrl(""))
        assertFalse(isForwardableUrl("mailto:"))
    }
}
