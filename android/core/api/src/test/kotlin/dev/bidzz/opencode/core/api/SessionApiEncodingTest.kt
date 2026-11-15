package dev.bidzz.opencode.core.api

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session ids are interpolated into API paths (audit-100 56-F3, defense in
 * depth): a `/`, `?` or `#` inside an id must not reshape the request.
 * Valid ids pass through unchanged, so legitimate traffic is byte-identical.
 */
class SessionApiEncodingTest {

    /** Serves a canned session and records every request line. */
    private class Stub(private val body: String = """{"data":{"id":"a/b"}}""") : AutoCloseable {
        private val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val port: Int get() = socket.localPort
        val requests = ConcurrentLinkedQueue<String>()
        private val listener = Thread {
            while (true) {
                val connection = try { socket.accept() } catch (e: IOException) { break }
                Thread {
                    connection.use {
                        val requestLine = runCatching { it.getInputStream().bufferedReader().readLine() }.getOrNull().orEmpty()
                        requests += requestLine
                        val body = this.body.encodeToByteArray()
                        val out = it.getOutputStream()
                        out.write(
                            ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                                .encodeToByteArray(),
                        )
                        out.write(body)
                        out.flush()
                    }
                }.apply { isDaemon = true; start() }
            }
        }.apply { isDaemon = true; start() }

        override fun close() {
            runCatching { socket.close() }
            listener.interrupt()
        }
    }

    private fun api(stub: Stub) = SessionApi(EngineClient("http://127.0.0.1:${stub.port}", "Basic dXNlcjp4"))

    @Test
    fun `a valid session id is not encoded`() {
        assertEquals("ses-abc_123.x", encodePathSegment("ses-abc_123.x"))
    }

    @Test
    fun `a slash question mark and hash are encoded`() {
        assertEquals("a%2Fb", encodePathSegment("a/b"))
        assertEquals("a%3Fb", encodePathSegment("a?b"))
        assertEquals("a%23b", encodePathSegment("a#b"))
    }

    @Test
    fun `a space becomes percent-20, not plus`() {
        assertEquals("a%20b", encodePathSegment("a b"))
    }

    @Test
    fun `get with a slash in the id requests the encoded path`() {
        Stub().use { stub ->
            val result = runBlocking { api(stub).get("a/b") }

            assertTrue(result is ApiResult.Success)
            assertTrue(stub.requests.single().contains("GET /api/session/a%2Fb "))
        }
    }

    @Test
    fun `listPage with a directory containing a space requests percent-encoding`() {
        // Audit-200 C12 (M-173): the directory used to go out with the space
        // as `+`, which the engine reads as a literal plus — a project in
        // "my app" looked like it had no sessions at all.
        Stub(body = """{"data":[]}""").use { stub ->
            val result = runBlocking { api(stub).listPage(directory = "/tmp/my app") }

            assertTrue(result is ApiResult.Success)
            val request = stub.requests.single()
            assertTrue(request, request.contains("directory=%2Ftmp%2Fmy%20app"))
            assertFalse(request, request.contains("my+app"))
        }
    }
}
