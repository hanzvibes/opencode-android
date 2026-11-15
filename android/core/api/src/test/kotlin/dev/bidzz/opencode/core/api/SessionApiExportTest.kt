package dev.bidzz.opencode.core.api

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session export asks the engine to sanitize (audit-200 C13, M-189):
 * the export used to go out without `sanitize=true`, so the downloaded
 * bundle carried the session's secrets verbatim.
 */
class SessionApiExportTest {

    /** Serves a canned export and records every request line. */
    private class Stub : AutoCloseable {
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
                        val body = """{"data":{"messages":[]}}""".encodeToByteArray()
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
    fun `export requests sanitize`() {
        Stub().use { stub ->
            val result = runBlocking { api(stub).export("ses-1") }

            assertTrue(result is ApiResult.Success)
            val request = stub.requests.single()
            assertTrue(request, request.contains("export?sanitize=true"))
        }
    }

    @Test
    fun `export still encodes the session id`() {
        Stub().use { stub ->
            val result = runBlocking { api(stub).export("a/b") }

            assertTrue(result is ApiResult.Success)
            val request = stub.requests.single()
            assertTrue(request, request.contains("/api/experimental/session/a%2Fb/export?sanitize=true"))
            assertFalse(request, request.contains("a/b/export"))
        }
    }
}
