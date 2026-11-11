package dev.bidzz.opencode.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The engine password must never be stored verbatim in a string the UI can
 * show or copy (item 18): Diagnostics shows recentOutput and copies it to
 * the clipboard, and lastError is built from it.
 */
class PasswordRedactionTest {

    @Test
    fun `the password line keeps its prefix but loses the secret`() {
        assertEquals(
            "server password [redacted]",
            redactEnginePassword("server password hunter2"),
        )
    }

    @Test
    fun `ordinary lines pass through untouched`() {
        val line = "listening on http://127.0.0.1:4096"
        assertEquals(line, redactEnginePassword(line))
    }

    @Test
    fun `the secret never survives, even mid-line`() {
        val redacted = redactEnginePassword("error: server password hunter2 leaked?")
        assertFalse(redacted.contains("hunter2"))
    }
}
