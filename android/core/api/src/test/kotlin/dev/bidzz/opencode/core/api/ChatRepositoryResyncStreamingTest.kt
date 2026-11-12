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
 * Fase 2: a resync mid-run must not lose the streaming text (03-F1), and the
 * resync's merge of contiguous assistant chunks must sum tokens and time
 * (01-F3). Over a real socket, following the [ChatRepositoryStreamingTest]
 * harness: history is served per messages call, the event stream is held
 * open and closed on purpose to force a reconnect.
 */
class ChatRepositoryResyncStreamingTest {

    /**
     * Minimal engine: answers `/api/session/s1/message` from [histories] in
     * call order, `{"data":[]}` for everything else, and holds one event
     * stream open per connection, handing each output stream to [eventOut].
     */
    private class Stub(
        val histories: List<String>,
        val eventOut: LinkedBlockingQueue<OutputStream> = LinkedBlockingQueue(),
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val port: Int get() = socket.localPort
        private val messageCalls = AtomicInteger(0)
        private val listener = Thread {
            while (true) {
                val connection = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
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
                                val body = histories.getOrNull(messageCalls.getAndIncrement()) ?: """{"data":[]}"""
                                out.write(
                                    ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                        "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                                        .encodeToByteArray(),
                                )
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

        override fun close() {
            runCatching { socket.close() }
            listener.interrupt()
        }
    }

    @Test
    fun `03-F1 mid-run resync keeps streaming text and later deltas append`() {
        // Open resync: empty history. Reconnect resync: the in-flight
        // assistant message, partial text, no completed time.
        val stub = Stub(
            histories = listOf(
                """{"data":[]}""",
                """{"data":[{"id":"m1","type":"assistant","content":[{"type":"text","text":"Hello "}],"time":{"created":1}}]}""",
            ),
        )
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val client = EngineClient("http://127.0.0.1:${stub.port}", "Basic dXNlcjp4")
            val repository = ChatRepository(SessionApi(client), EventStream(client), scope)
            repository.open("s1")
            val stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never connected", stream)
            fun sendEvent(json: String) {
                stream!!.write(("data: $json\n\n").encodeToByteArray())
                stream.flush()
            }

            sendEvent("""{"type":"session.text.started","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":0}}""")
            sendEvent("""{"type":"session.text.delta","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":0,"delta":"Hello "}}""")
            val streamed = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.streaming["m1"] != "Hello ") delay(25)
                    repository.state.value.streaming["m1"]
                }
            }
            assertEquals("Hello ", streamed)

            // The last delta arrives without its closing blank line and the
            // server hangs up right after: 03-F4 flushes it, then the
            // reconnect resync must keep the buffer (03-F1), not wipe it.
            stream!!.write(
                """data: {"type":"session.text.delta","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":0,"delta":"there"}}"""
                    .encodeToByteArray(),
            )
            stream.flush()
            stream.close()

            // The reconnect (after the 500ms backoff) resyncs, then opens a
            // new stream: its arrival proves the resync already ran.
            val newStream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never reconnected", newStream)
            val kept = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.streaming["m1"] != "Hello there") delay(25)
                    repository.state.value.streaming["m1"]
                }
            }
            assertEquals("Hello there", kept)

            // A delta on the new connection continues the same buffer:
            // the text is appended once, never duplicated from history.
            newStream!!.write(
                ("data: {\"type\":\"session.text.delta\",\"data\":{\"sessionID\":\"s1\",\"assistantMessageID\":\"m1\",\"ordinal\":0,\"delta\":\"!\"}}\n\n")
                    .encodeToByteArray(),
            )
            newStream.flush()
            val continued = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.streaming["m1"] != "Hello there!") delay(25)
                    repository.state.value.streaming["m1"]
                }
            }
            assertEquals("Hello there!", continued)
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `01-F3 resync merges contiguous assistant chunks with summed tokens and time`() {
        // Newest first, as the engine returns it: chunk2, chunk1, user.
        val history = """{"data":[
            {"id":"a2","type":"assistant","content":[{"type":"text","text":"World"}],"cost":0.25,"tokens":{"input":3,"output":7,"reasoning":4,"cache":{"read":1}},"time":{"created":120,"streamed":130,"completed":200}},
            {"id":"a1","type":"assistant","content":[{"type":"text","text":"Hello "}],"cost":0.5,"tokens":{"input":10,"output":5,"cache":{"read":2,"write":1}},"time":{"created":100,"streamed":110}},
            {"id":"u1","type":"user","text":"hi"}
        ]}"""
        val stub = Stub(histories = listOf(history))
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val client = EngineClient("http://127.0.0.1:${stub.port}", "Basic dXNlcjp4")
            val repository = ChatRepository(SessionApi(client), EventStream(client), scope)
            repository.open("s1")
            val merged = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.messages.size < 2) delay(25)
                    repository.state.value.messages.last()
                }
            }
            assertNotNull(merged)
            assertEquals("Hello World", merged!!.plainText)
            assertEquals(0.75, merged.cost!!, 0.0001)
            assertEquals(13L, merged.tokens?.input)
            assertEquals(12L, merged.tokens?.output)
            assertEquals(4L, merged.tokens?.reasoning)
            assertEquals(3L, merged.tokens?.cache?.read)
            assertEquals(1L, merged.tokens?.cache?.write)
            assertEquals(100L, merged.time?.created)
            assertEquals(130L, merged.time?.streamed)
            assertEquals(200L, merged.time?.completed)
        } finally {
            scope.cancel()
            stub.close()
        }
    }
}
