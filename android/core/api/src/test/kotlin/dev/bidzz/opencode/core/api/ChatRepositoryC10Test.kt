package dev.bidzz.opencode.core.api

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C10: M-153 — the retry path fetches the transcript
 * before re-sending, so a prompt whose POST failed ambiguously is not
 * sent twice. This is the repository half: the passthrough that serves
 * the check.
 */
class ChatRepositoryC10Test {

    private class Stub : AutoCloseable {
        private val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val port: Int get() = socket.localPort
        private val listener = Thread {
            while (true) {
                val connection = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
                Thread {
                    connection.use {
                        val reader = it.getInputStream().bufferedReader()
                        val requestLine = runCatching { reader.readLine() }
                            .getOrNull().orEmpty()
                        var contentLength = 0
                        while (true) {
                            val header = runCatching { reader.readLine() }.getOrNull() ?: break
                            if (header.isBlank()) break
                            val length = Regex("(?i)^Content-Length:\\s*(\\d+)").find(header)
                            if (length != null) contentLength = length.groupValues[1].toInt()
                        }
                        if (contentLength > 0) {
                            val chars = CharArray(contentLength)
                            var read = 0
                            while (read < contentLength) {
                                val n = reader.read(chars, read, contentLength - read)
                                if (n < 0) break
                                read += n
                            }
                        }
                        val out = it.getOutputStream()
                        fun answer(status: String, responseBody: String) {
                            out.write(
                                ("HTTP/1.1 $status\r\nContent-Type: application/json\r\n" +
                                    "Content-Length: ${responseBody.length}\r\nConnection: close\r\n\r\n$responseBody")
                                    .encodeToByteArray(),
                            )
                        }
                        // Newest first, as the engine returns them.
                        if ("/message" in requestLine) {
                            answer(
                                "200 OK",
                                """{"data":[{"id":"m-landed","type":"user","text":"hello again"},{"id":"m-old","type":"user","text":"hello"}]}""",
                            )
                        } else {
                            answer("200 OK", """{"data":[]}""")
                        }
                    }
                }.apply { isDaemon = true; start() }
            }
        }.apply { isDaemon = true; start() }

        override fun close() {
            runCatching { socket.close() }
            listener.interrupt()
        }
    }

    @Test
    fun `M-153 fetchMessages returns the server transcript`() {
        val stub = Stub()
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val client = EngineClient("http://127.0.0.1:${stub.port}", "Basic dXNlcjp4")
            val repo = ChatRepository(SessionApi(client), EventStream(client), scope)
            val result = runBlocking { repo.fetchMessages("s1") }
            val messages = (result as ApiResult.Success).value
            assertEquals(listOf("m-landed", "m-old"), messages.map { it.id })
            assertTrue(messages.all { it.isUser })
            assertEquals("hello again", messages.first().text)
        } finally {
            scope.cancel()
            stub.close()
        }
    }
}
