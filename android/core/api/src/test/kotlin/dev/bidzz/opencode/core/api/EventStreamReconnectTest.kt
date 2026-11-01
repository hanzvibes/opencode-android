package dev.bidzz.opencode.core.api

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reconnecting a dropped event stream, over a real socket.
 *
 * `handoff.md` recorded that `onReconnect` had never been triggered on purpose. This
 * triggers it: a server that answers the request, delivers one event, and hangs up.
 * A fake could not reproduce the part under test, which is OkHttp reading a connection
 * the other end closed.
 */
class EventStreamReconnectTest {

    @Test
    fun `a stream the server closes is reopened, with the resync in between`() {
        val server = SseStub()
        val order = CopyOnWriteArrayList<String>()
        server.connects = { n -> order.add("connect-$n") }

        try {
            val client = EngineClient("http://${server.address}", "Basic dXNlcjp4")
            val received = runBlocking {
                withTimeoutOrNull(20_000) {
                    EventStream(client)
                        .events(onReconnect = { order.add("reconnect") })
                        .take(2)
                        .toList()
                }?.map { it.type }
            }

            assertEquals(listOf("event-1", "event-2"), received)
            // Order, not just counts: a resync that ran after the new connection had
            // already emitted would leave a hole in the chat state that still looks
            // like two received events.
            assertEquals(listOf("connect-1", "reconnect", "connect-2"), order)
        } finally {
            server.close()
        }
    }

    @Test
    fun `the first connection does not resync`() {
        // `ChatRepository.open()` already reads state over REST before following the
        // stream, and a resync clears in-flight buffers — running it on a connection
        // that dropped nothing would wipe text that has not been published yet.
        val server = SseStub()
        val order = CopyOnWriteArrayList<String>()
        server.connects = { n -> order.add("connect-$n") }

        try {
            val client = EngineClient("http://${server.address}", "Basic dXNlcjp4")
            val count = runBlocking {
                withTimeoutOrNull(20_000) {
                    EventStream(client)
                        .events(onReconnect = { order.add("reconnect") })
                        .take(1)
                        .toList()
                }?.size
            }
            assertEquals(1, count)
            assertEquals(listOf("connect-1"), order)
        } finally {
            server.close()
        }
    }

    /**
     * Minimal HTTP endpoint that streams one SSE event per connection and closes.
     *
     * `Connection: close` with no `Content-Length` means the body is read until EOF,
     * which is exactly the failure the stream loop has to survive. A real web server is
     * not used because AGP unit tests compile without `com.sun.net.httpserver`, and one
     * more test dependency to fake a socket would be the larger risk.
     */
    private class SseStub : AutoCloseable {
        private val socket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val address: String = "127.0.0.1:${socket.localPort}"
        private val count = AtomicInteger()
        private var stopped = false
        private var listener: Thread? = null

        /** Called from the accept loop as each request arrives, with its number. */
        @Volatile
        var connects: (Int) -> Unit = {}

        init {
            listener = Thread {
                while (!stopped) {
                    val connection = try {
                        socket.accept()
                    } catch (e: IOException) {
                        break
                    }
                    val n = count.incrementAndGet()
                    connects(n)
                    connection.use { socketConnection ->
                        runCatching { socketConnection.getInputStream().bufferedReader().readLine() }
                        val out = socketConnection.getOutputStream()
                        val headers = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/event-stream\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "Connection: close\r\n\r\n"
                        out.write(headers.encodeToByteArray())
                        out.write("data: {\"type\":\"event-$n\"}\n\n".encodeToByteArray())
                        out.flush()
                        // Give the reader a moment to take the event before the socket
                        // disappears; closing on top of a flush can truncate it.
                        runCatching { Thread.sleep(50) }
                    }
                }
            }.apply {
                isDaemon = true
                name = "sse-stub"
                start()
            }
        }

        override fun close() {
            stopped = true
            runCatching { socket.close() }
            listener?.interrupt()
        }
    }
}
