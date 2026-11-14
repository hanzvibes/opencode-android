package dev.bidzz.opencode.ui.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppBrowserAuthTest {

    @Test
    fun `M-228 a challenge from the entry host is answered`() {
        assertTrue(shouldAnswerBasicAuth("127.0.0.1", "127.0.0.1"))
    }

    @Test
    fun `M-228 a challenge from any other host is refused`() {
        assertFalse(shouldAnswerBasicAuth("evil.example.com", "127.0.0.1"))
        assertFalse(shouldAnswerBasicAuth("example.com", "127.0.0.1"))
    }

    @Test
    fun `M-228 the host match is case-insensitive and nulls refuse`() {
        assertTrue(shouldAnswerBasicAuth("EXAMPLE.COM", "example.com"))
        assertFalse(shouldAnswerBasicAuth(null, "example.com"))
        assertFalse(shouldAnswerBasicAuth("example.com", null))
    }
}
