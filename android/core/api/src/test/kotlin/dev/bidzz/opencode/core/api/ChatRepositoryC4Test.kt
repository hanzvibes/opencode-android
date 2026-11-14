package dev.bidzz.opencode.core.api

import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C4: `interrupt` takes the session id (M-082), over a
 * real socket, following the [ChatRepositoryC3Test] harness.
 */
class ChatRepositoryC4Test {

    /**
     * Minimal engine: answers `/api/session/<id>/message` with
     * `{"data":[]}`, holds one event stream open per connection, and
     * records every POST request line in [postedRequests].
     */
    private class Stub(
        val eventOut: LinkedBlockingQueue<OutputStream> = LinkedBlockingQueue(),
        val postedRequests: LinkedBlockingQueue<String> = LinkedBlockingQueue(),
    ) : AutoCloseable {
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
                        when {
                            "/api/event" in requestLine -> {
                                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n".encodeToByteArray())
                                out.flush()
                                eventOut.put(out)
                                while (!socket.isClosed) Thread.sleep(100)
                            }
                            requestLine.startsWith("POST") -> {
                                postedRequests.put(requestLine)
                                answer("200 OK", """{"data":[]}""")
                            }
                            else -> answer("200 OK", """{"data":[]}""")
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

    private fun repository(stub: Stub, scope: CoroutineScope): ChatRepository {
        val client = EngineClient("http://127.0.0.1:${stub.port}", "Basic dXNlcjp4")
        return ChatRepository(SessionApi(client), EventStream(client), scope)
    }

    @Test
    fun `M-082 interrupt posts to the given session, not the current one`() {
        val stub = Stub()
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            val repository = repository(stub, scope)
            runBlocking { repository.open("s1") }
            val stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never opened", stream)

            // The repository is on s1, but the interrupt names s2: the
            // old code read the mutable session id at call time, so a
            // switch between the Stop tap and the POST sent the interrupt
            // to the session the user had just opened instead.
            val result = runBlocking { repository.interrupt("s2") }
            assertTrue(result is ApiResult.Success)
            val requestLine = stub.postedRequests.poll(20, TimeUnit.SECONDS)
            assertEquals("POST /api/session/s2/interrupt HTTP/1.1", requestLine)
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `M-082 interrupt for the open session still posts to it`() {
        val stub = Stub()
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            val repository = repository(stub, scope)
            runBlocking { repository.open("s1") }
            val stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never opened", stream)

            val result = runBlocking { repository.interrupt("s1") }
            assertTrue(result is ApiResult.Success)
            val requestLine = stub.postedRequests.poll(20, TimeUnit.SECONDS)
            assertEquals("POST /api/session/s1/interrupt HTTP/1.1", requestLine)
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    // M-084 (017-F5, resync reviving busy) is NOT re-tested here: C1's
    // `M-045 a terminal resync after an aborted run does not revive
    // busy` (ChatRepositoryC1Test) already locks exactly that path —
    // the terminal event clears busy and the resync after it keeps it
    // cleared against an unfinished newest history row. Repeating it
    // here would pin nothing new.
}
