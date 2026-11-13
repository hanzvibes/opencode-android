package dev.bidzz.opencode.core.api

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 6 batch 6d (19-F2/19-F3): the engine returns a page cursor with
 * both the session list and the message history, but the API dropped it —
 * sessions past the first 50 were unreachable and a paginating engine
 * silently broke the "first user message" preview. The page methods keep
 * the envelope; [SessionApi.list] and [SessionApi.messages] keep
 * returning just the rows, so existing callers are untouched.
 */
class SessionApiPageTest {

    /** Serves canned JSON per path and records every request line. */
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
                        val body = when {
                            requestLine.contains("/api/session?") -> """{"data":[{"id":"s1"},{"id":"s2"}],"cursor":{"next":"c2"}}"""
                            requestLine.contains("/api/session/s1/message") ->
                                if (requestLine.contains("cursor=m2")) """{"data":[{"id":"m0","type":"user","text":"first"}],"cursor":{}}"""
                                else """{"data":[{"id":"m9","type":"user","text":"latest"}],"cursor":{"next":"m2"}}"""
                            else -> """{"data":[]}"""
                        }.encodeToByteArray()
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
    fun `19-F3 listPage returns the rows and the next cursor`() {
        Stub().use { stub ->
            val result = runBlocking { api(stub).listPage(limit = 50) }

            assertTrue(result is ApiResult.Success)
            val page = (result as ApiResult.Success).value
            assertEquals(listOf("s1", "s2"), page.data.map { it.id })
            assertEquals("c2", page.cursor?.next)
            assertTrue(stub.requests.single().contains("GET /api/session?limit=50 "))
        }
    }

    @Test
    fun `19-F3 listPage sends the cursor back for the next page`() {
        Stub().use { stub ->
            runBlocking { api(stub).listPage(limit = 50, cursor = "c2") }

            assertTrue(stub.requests.single().contains("cursor=c2"))
        }
    }

    @Test
    fun `19-F3 list still returns just the rows`() {
        Stub().use { stub ->
            val result = runBlocking { api(stub).list() }

            assertTrue(result is ApiResult.Success)
            assertEquals(listOf("s1", "s2"), (result as ApiResult.Success).value.map { it.id })
        }
    }

    @Test
    fun `19-F2 messagesPage walks toward the oldest page with the cursor`() {
        Stub().use { stub ->
            val first = runBlocking { api(stub).messagesPage("s1", limit = 50) }
            assertTrue(first is ApiResult.Success)
            val firstPage = (first as ApiResult.Success).value
            assertEquals("latest", firstPage.data.single().text)
            assertEquals("m2", firstPage.cursor?.next)

            val second = runBlocking { api(stub).messagesPage("s1", limit = 50, cursor = "m2") }
            assertTrue(second is ApiResult.Success)
            val secondPage = (second as ApiResult.Success).value
            assertEquals("first", secondPage.data.single().text)
            assertNull(secondPage.cursor?.next)

            assertTrue(stub.requests.any { it.contains("GET /api/session/s1/message?limit=50 ") })
            assertTrue(stub.requests.any { it.contains("cursor=m2") })
        }
    }

    @Test
    fun `19-F2 messages still returns just the rows`() {
        Stub().use { stub ->
            val result = runBlocking { api(stub).messages("s1") }

            assertTrue(result is ApiResult.Success)
            assertEquals(listOf("latest"), (result as ApiResult.Success).value.map { it.text })
        }
    }
}
