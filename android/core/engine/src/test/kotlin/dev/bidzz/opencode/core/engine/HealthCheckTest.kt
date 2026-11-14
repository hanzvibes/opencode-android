package dev.bidzz.opencode.core.engine

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The health probe tells a busy engine from a dead one (audit-200 C10,
 * M-149): while an agent run streams, the engine stops answering
 * /api/info for tens of seconds, and the old Boolean probe counted every
 * silent probe as a failure until the monitor killed a healthy engine
 * mid-run. Only an unreachable server counts towards degradation.
 *
 * The live tests run a real loopback socket: `isReturnDefaultValues`
 * keeps `android.util.Log` from throwing under JVM unit tests, and
 * `basicAuthHeader` degrades to "Basic null" there, which the fake
 * servers below do not check.
 */
class HealthCheckTest {

    private fun creds(port: Int) =
        EngineCredentials(baseUrl = "http://127.0.0.1:$port", password = "secret")

    @Test
    fun `M-149 a socket timeout is unresponsive, not unreachable`() {
        assertEquals(HealthResult.UNRESPONSIVE, classifyHealthFailure(SocketTimeoutException()))
        assertEquals(HealthResult.UNRESPONSIVE, classifyHealthFailure(null))
    }

    @Test
    fun `M-149 a refused connection is unreachable`() {
        assertEquals(HealthResult.UNREACHABLE, classifyHealthFailure(ConnectException()))
        assertEquals(HealthResult.UNREACHABLE, classifyHealthFailure(IllegalStateException()))
    }

    @Test
    fun `M-149 only an unreachable probe counts towards degradation`() {
        assertTrue(shouldCountHealthFailure(HealthResult.UNREACHABLE))
        assertFalse(shouldCountHealthFailure(HealthResult.UNRESPONSIVE))
        assertFalse(shouldCountHealthFailure(HealthResult.HEALTHY))
    }

    @Test
    fun `M-149 HTTP 200 is healthy`() = runTest {
        serveOnce(statusLine = "HTTP/1.1 200 OK") { port ->
            assertEquals(HealthResult.HEALTHY, HttpHealthCheck(timeoutMs = 2_000).checkHealth(creds(port)))
        }
    }

    @Test
    fun `M-149 a non-200 status is unreachable`() = runTest {
        serveOnce(statusLine = "HTTP/1.1 401 Unauthorized") { port ->
            assertEquals(HealthResult.UNREACHABLE, HttpHealthCheck(timeoutMs = 2_000).checkHealth(creds(port)))
        }
    }

    @Test
    fun `M-149 a connection refused is unreachable`() = runTest {
        val port = ServerSocket(0).use { it.localPort }
        assertEquals(HealthResult.UNREACHABLE, HttpHealthCheck(timeoutMs = 2_000).checkHealth(creds(port)))
    }

    @Test
    fun `M-149 a server that accepts but never answers is unresponsive`() = runTest {
        ServerSocket(0).use { server ->
            val accepted = Thread {
                runCatching {
                    val socket = server.accept()
                    // Hold the connection open without answering, like a
                    // streaming engine: the probe must time out, not fail.
                    Thread.sleep(5_000)
                    socket.close()
                }
            }.also { it.start() }
            try {
                assertEquals(
                    HealthResult.UNRESPONSIVE,
                    HttpHealthCheck(timeoutMs = 500).checkHealth(creds(server.localPort)),
                )
            } finally {
                accepted.interrupt()
            }
        }
    }

    /**
     * Accepts one connection, answers with [statusLine] and an empty body,
     * then runs [block] with the port. The server thread is joined so a
     * slow test cannot leak a listening socket into the next test.
     */
    private suspend fun serveOnce(statusLine: String, block: suspend (Int) -> Unit) {
        ServerSocket(0).use { server ->
            val thread = Thread {
                runCatching {
                    server.accept().use { socket ->
                        BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                        socket.getOutputStream().write(
                            "$statusLine\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.US_ASCII),
                        )
                        socket.getOutputStream().flush()
                    }
                }
            }.also { it.start() }
            try {
                block(server.localPort)
            } finally {
                thread.join(5_000)
            }
        }
    }
}
