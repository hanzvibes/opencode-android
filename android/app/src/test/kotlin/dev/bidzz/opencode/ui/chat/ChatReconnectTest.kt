package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.engine.EngineCredentials
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deciding whether the attached handle still points at the running engine.
 *
 * The engine picks a fresh ephemeral port per start and prints a new password, and
 * the port is allocated by binding a probe socket that is then closed — so the OS is
 * free to hand the same number to the next engine. Comparing only the URL would let
 * that case pass with a stale password, and every request would answer 401 forever.
 */
class ChatReconnectTest {

    private fun creds(baseUrl: String, password: String) = EngineCredentials(baseUrl, password)

    @Test
    fun `identical credentials are not stale`() {
        val attached = creds("http://127.0.0.1:41000", "aaa")
        assertFalse(isStale(attached, creds("http://127.0.0.1:41000", "aaa")))
    }

    @Test
    fun `a new port is stale`() {
        val attached = creds("http://127.0.0.1:41000", "aaa")
        assertTrue(isStale(attached, creds("http://127.0.0.1:41001", "aaa")))
    }

    @Test
    fun `a reused port with a new password is still stale`() {
        // The case the URL-only comparison got wrong.
        val attached = creds("http://127.0.0.1:41000", "aaa")
        assertTrue(isStale(attached, creds("http://127.0.0.1:41000", "bbb")))
    }

    @Test
    fun `nothing attached yet is stale`() {
        assertTrue(isStale(null, creds("http://127.0.0.1:41000", "aaa")))
    }
}
