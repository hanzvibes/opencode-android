package dev.bidzz.opencode.core.api

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One malformed MCP server entry must not hide the whole list (audit-200
 * C12, M-177): the list used to decode all-or-nothing, so a single entry
 * without its `status` object turned every healthy server into an error
 * screen.
 */
class McpApiTest {

    /** Serves a canned MCP list and records every request line. */
    private class Stub(private val body: String) : AutoCloseable {
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

    private fun api(stub: Stub) = McpApi(EngineClient("http://127.0.0.1:${stub.port}", "Basic dXNlcjp4"))

    @Test
    fun `one malformed entry is skipped and the healthy servers still list`() {
        Stub("""{"data":[{"name":"good","status":{"status":"connected"}},{"name":"bad"}]}""").use { stub ->
            val result = runBlocking { api(stub).list() }

            assertTrue(result is ApiResult.Success)
            val servers = (result as ApiResult.Success).value
            assertEquals(listOf("good"), servers.map { it.name })
            assertEquals("connected", servers.single().status.status)
        }
    }

    @Test
    fun `every entry malformed is a failure, not a false empty list`() {
        Stub("""{"data":[{"name":"bad1"},{"name":"bad2"}]}""").use { stub ->
            val result = runBlocking { api(stub).list() }

            assertTrue(result is ApiResult.Failure)
        }
    }

    @Test
    fun `a genuinely empty list still decodes as empty`() {
        Stub("""{"data":[]}""").use { stub ->
            val result = runBlocking { api(stub).list() }

            assertTrue(result is ApiResult.Success)
            assertTrue((result as ApiResult.Success).value.isEmpty())
        }
    }
}
