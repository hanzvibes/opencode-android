package dev.bidzz.opencode.core.api

import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Streaming text blocks must stay separate, and the streaming indicator must
 * not stick when the resync after a run fails (item 25).
 *
 * Over a real socket: history is empty, the event stream is held open, and
 * the messages GET fails on every call after the first (the open resync).
 * Two text blocks are streamed for one assistant message — block 0 gets a
 * late delta after block 1 already ended — then the run ends and the
 * post-run resync fails. The published text must keep both blocks
 * (paragraph-separated, never overwritten by the other block's TEXT_ENDED),
 * and the streaming entry must be gone once the resync fails.
 */
class ChatRepositoryStreamingTest {

    @Test
    fun `multi-block streaming keeps blocks separate and a failed resync drops the indicator`() {
        val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val eventOut = LinkedBlockingQueue<OutputStream>()
        val messageCalls = AtomicInteger(0)
        val listener = Thread {
            while (true) {
                val connection = try { socket.accept() } catch (e: IOException) { break }
                Thread {
                    connection.use {
                        val requestLine = runCatching { it.getInputStream().bufferedReader().readLine() }
                            .getOrNull().orEmpty()
                        val out = it.getOutputStream()
                        when {
                            "/api/event" in requestLine -> {
                                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n".encodeToByteArray())
                                out.flush()
                                eventOut.put(out)
                                while (!socket.isClosed) Thread.sleep(100)
                            }
                            "/api/session/s1/message" in requestLine -> {
                                if (messageCalls.incrementAndGet() == 1) {
                                    val body = """{"data":[]}"""
                                    out.write(
                                        ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                            "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                                            .encodeToByteArray(),
                                    )
                                } else {
                                    val body = """{"error":"boom"}"""
                                    out.write(
                                        ("HTTP/1.1 500 Internal Server Error\r\nContent-Type: application/json\r\n" +
                                            "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                                            .encodeToByteArray(),
                                    )
                                }
                            }
                            else -> {
                                val body = """{"data":[]}"""
                                out.write(
                                    ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                        "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                                        .encodeToByteArray(),
                                )
                            }
                        }
                    }
                }.apply { isDaemon = true; start() }
            }
        }.apply { isDaemon = true; start() }

        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val client = EngineClient("http://127.0.0.1:${socket.localPort}", "Basic dXNlcjp4")
            val repository = ChatRepository(SessionApi(client), EventStream(client), scope)
            repository.open("s1")
            val stream = eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never connected", stream)
            fun sendEvent(json: String) {
                stream!!.write(("data: $json\n\n").encodeToByteArray())
                stream.flush()
            }

            sendEvent("""{"type":"session.text.started","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":0}}""")
            sendEvent("""{"type":"session.text.delta","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":0,"delta":"Hello "}}""")
            sendEvent("""{"type":"session.text.started","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":1}}""")
            sendEvent("""{"type":"session.text.delta","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":1,"delta":"World"}}""")
            sendEvent("""{"type":"session.text.ended","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":1,"text":"World"}}""")
            // A late delta for block 0, after block 1 already ended: under
            // the old id-only keying this would land in (and the TEXT_ENDED
            // above would have overwritten) the shared buffer.
            sendEvent("""{"type":"session.text.delta","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":0,"delta":"there"}}""")

            val text = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.streaming["m1"] != "Hello there\n\nWorld") delay(25)
                    repository.state.value.streaming["m1"]
                }
            }
            assertEquals("Hello there\n\nWorld", text)

            sendEvent("""{"type":"session.execution.succeeded","data":{"sessionID":"s1"}}""")
            val cleared = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.streaming.isNotEmpty()) delay(25)
                    true
                }
            }
            assertEquals(true, cleared)
            assertEquals(false, repository.state.value.busy)
        } finally {
            scope.cancel()
            socket.close()
            listener.join(1_000)
        }
    }
}
