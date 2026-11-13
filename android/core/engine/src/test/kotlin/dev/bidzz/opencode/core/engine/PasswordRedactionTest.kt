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

    @Test
    fun `a known password value is masked without the prefix (53-F1)`() {
        val redacted = redactEnginePassword("auth failed for user opencode: hunter2", "hunter2")
        assertFalse(redacted.contains("hunter2"))
    }

    @Test
    fun `a known password is masked inside its Basic header form (53-F1)`() {
        val header = "Basic " + java.util.Base64.getEncoder()
            .encodeToString("opencode:hunter2".toByteArray(Charsets.UTF_8))
        val redacted = redactEnginePassword("Authorization: $header", "hunter2")
        assertFalse(redacted.contains(header))
        assertFalse(redacted.contains("hunter2"))
    }

    @Test
    fun `ordinary lines still pass through with a known password`() {
        val line = "listening on http://127.0.0.1:4096"
        assertEquals(line, redactEnginePassword(line, "hunter2"))
    }

    @Test
    fun `EngineCredentials toString never prints the password (53-F1)`() {
        val text = EngineCredentials("http://127.0.0.1:4096", "hunter2").toString()
        assertFalse(text.contains("hunter2"))
    }

    @Test
    fun `EngineStatus toString never prints the password (53-F1)`() {
        val status = EngineStatus(
            credentials = EngineCredentials("http://127.0.0.1:4096", "hunter2"),
        )
        assertFalse(status.toString().contains("hunter2"))
    }
}
