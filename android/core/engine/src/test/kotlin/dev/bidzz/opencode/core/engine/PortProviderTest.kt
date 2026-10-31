package dev.bidzz.opencode.core.engine

import java.net.InetAddress
import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port allocation is small but load-bearing: if it hands out a port that is
 * already bound, the engine fails to start and the failure surfaces 60 seconds
 * later as "did not become healthy", which points nowhere useful.
 */
class PortProviderTest {
    @Test
    fun `ephemeral provider returns a port that can be bound`() {
        val port = EphemeralPortProvider().allocate()
        assertTrue("port should be in the dynamic range, was $port", port in 1024..65535)

        // The provider closes its socket before returning, so the port must be
        // free for the engine to claim it.
        ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).use { socket ->
            assertEquals(port, socket.localPort)
        }
    }

    @Test
    fun `ephemeral provider does not reuse the port it just released`() {
        val provider = EphemeralPortProvider()
        val first = provider.allocate()
        // Two consecutive calls may legitimately collide, but never with a port
        // that is currently held. Bind the first and confirm the second differs.
        ServerSocket(first, 1, InetAddress.getByName("127.0.0.1")).use {
            val second = provider.allocate()
            assertNotEquals("must not hand out a bound port", first, second)
        }
    }

    @Test
    fun `fixed provider honours its port`() {
        assertEquals(4096, FixedPortProvider(4096).allocate())
        assertEquals(ProcessManager.DEFAULT_PORT, FixedPortProvider(ProcessManager.DEFAULT_PORT).allocate())
    }

    @Test
    fun `port conflict is detected from engine output`() {
        // Verbatim from a second engine instance on an occupied port. This is the
        // exact wording the retry logic has to recognise.
        val observed = """
            ServeError:
                at N (../../node_modules/.bun/@effect+platform-node@4.0.6/dist/NodeHttpServer.js:123:30)
              [cause]: Error: Failed to start server. Is port 41234 in use?
        """.trimIndent()
        assertTrue(isPortConflict(observed))

        // errno forms, in case Bun's wording changes.
        assertTrue(isPortConflict("EADDRINUSE: address already in use 127.0.0.1:4096"))
        assertTrue(isPortConflict("EADDRINUSE"))
    }

    @Test
    fun `unrelated failures are not mistaken for port conflicts`() {
        assertFalse(isPortConflict("EROFS: mkdir '/.local'"))
        assertFalse(isPortConflict("engine did not become healthy"))
        assertFalse(isPortConflict("Error: EACCES: permission denied, mkdir '/data/local/tmp/opencode'"))
        assertFalse(isPortConflict(""))
    }
}
