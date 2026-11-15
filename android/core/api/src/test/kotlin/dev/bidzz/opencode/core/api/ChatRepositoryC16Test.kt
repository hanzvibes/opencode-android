package dev.bidzz.opencode.core.api

import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C16: concurrent resyncs (M-241), the resync's live-tool
 * and streaming preservation (M-023), and closing a stuck event stream
 * (M-245).
 *
 * The stub follows the [ChatRepositoryResyncStreamingTest] harness: a real
 * loopback socket, `/api/session/s1/message` answered from [histories] in
 * call order, `{"data":[]}` for everything else, one event stream held open
 * per connection.
 */
class ChatRepositoryC16Test {

    private class Stub(
        val histories: List<String> = listOf("""{"data":[]}"""),
        /** Answer these 0-based `/message` calls with HTTP 500. */
        val errorOnMessageCalls: Set<Int> = emptySet(),
        /** Hold this 0-based `/message` call back this many ms before answering. */
        val slowMessageCalls: Map<Int, Long> = emptyMap(),
        val eventOut: LinkedBlockingQueue<OutputStream> = LinkedBlockingQueue(),
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val port: Int get() = socket.localPort
        val messageCalls = AtomicInteger(0)
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
                                val callIndex = messageCalls.getAndIncrement()
                                slowMessageCalls[callIndex]?.let { Thread.sleep(it) }
                                if (callIndex in errorOnMessageCalls) {
                                    val body = """{"error":{"code":500,"message":"boom"}}"""
                                    out.write(
                                        ("HTTP/1.1 500 Internal Server Error\r\nContent-Type: application/json\r\n" +
                                            "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                                            .encodeToByteArray(),
                                    )
                                    return@Thread
                                }
                                val body = histories.getOrNull(callIndex) ?: histories.lastOrNull() ?: """{"data":[]}"""
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

    private fun repository(stub: Stub): Pair<ChatRepository, CoroutineScope> {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        val client = EngineClient("http://127.0.0.1:${stub.port}", "Basic dXNlcjp4")
        return ChatRepository(SessionApi(client), EventStream(client), scope) to scope
    }

    @Test
    fun `M-241 a stale resync must not overwrite the newer resync in the same session`() {
        // The open resync (call 0) is held up server-side for 1.5 s and
        // answers with the OLD transcript; a refresh started while it is
        // in flight (call 1) answers at once with the NEW transcript.
        // Last-wins would let the old answer clobber the new one when it
        // finally lands; the generation guard drops it instead.
        val old = """{"data":[{"id":"m-a","type":"assistant","content":[{"type":"text","text":"OLD"}],"time":{"created":1,"completed":2}}]}"""
        val new = """{"data":[{"id":"m-b","type":"assistant","content":[{"type":"text","text":"NEW"}],"time":{"created":1,"completed":2}}]}"""
        val stub = Stub(histories = listOf(old, new), slowMessageCalls = mapOf(0 to 1_500))
        val (repo, scope) = repository(stub)
        try {
            repo.open("s1")
            val inFlight = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (stub.messageCalls.get() < 1) delay(25)
                    true
                }
            }
            assertEquals(true, inFlight)
            runBlocking { repo.refresh() }
            val landed = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repo.state.value.messages.none { it.id == "m-b" }) delay(25)
                    true
                }
            }
            assertEquals(true, landed)
            // The stale answer lands 1.5 s after the open; wait past it.
            Thread.sleep(1_500)
            val ids = repo.state.value.messages.map { it.id }
            assertEquals(listOf("m-b"), ids)
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `M-023 a resync keeps a live tool the history does not mention yet`() {
        // Open resync: empty history. The reconnect resync answers the
        // same empty history — the tool started live on the stream is not
        // in it yet, because the engine has not persisted it. The old
        // code cleared liveTools on every resync, so the tool's card
        // vanished mid-run; it must stay until history proves it ended.
        val stub = Stub(histories = listOf("""{"data":[]}""", """{"data":[]}"""))
        val (repo, scope) = repository(stub)
        try {
            repo.open("s1")
            val stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never connected", stream)
            fun sendEvent(json: String) {
                stream!!.write(("data: $json\n\n").encodeToByteArray())
                stream.flush()
            }
            sendEvent("""{"type":"session.tool.input.started","data":{"sessionID":"s1","assistantMessageID":"m1","id":"call-1","name":"read"}}""")
            val live = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repo.state.value.liveTools["call-1"]?.call?.status != "streaming") delay(25)
                    repo.state.value.liveTools["call-1"]
                }
            }
            assertNotNull("tool never went live", live)
            stream!!.close()
            val newStream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never reconnected", newStream)
            // Wait until the reconnect resync actually ran (its messages
            // fetch is call 1), then give the state write a beat.
            val resynced = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (stub.messageCalls.get() < 2) delay(25)
                    true
                }
            }
            assertEquals(true, resynced)
            Thread.sleep(300)
            // The reconnect resync ran (a new stream is up) and the tool
            // is still live: nothing in history says it ended.
            assertNotNull(
                "live tool was dropped by a resync that never saw it end",
                repo.state.value.liveTools["call-1"],
            )
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `M-023 a resync drops a live tool the history shows as completed`() {
        // The reconnect resync's history carries the tool block with
        // status completed — the run is over for that call, so the live
        // card must go, not linger at "Running" forever.
        val finished = """{"data":[{"id":"m1","type":"assistant","content":[{"type":"tool","id":"call-1","name":"read","state":{"status":"completed"}}],"time":{"created":1,"completed":2}}]}"""
        val stub = Stub(histories = listOf("""{"data":[]}""", finished))
        val (repo, scope) = repository(stub)
        try {
            repo.open("s1")
            val stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never connected", stream)
            fun sendEvent(json: String) {
                stream!!.write(("data: $json\n\n").encodeToByteArray())
                stream.flush()
            }
            sendEvent("""{"type":"session.tool.input.started","data":{"sessionID":"s1","assistantMessageID":"m1","id":"call-1","name":"read"}}""")
            val live = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repo.state.value.liveTools["call-1"]?.call?.status != "streaming") delay(25)
                    repo.state.value.liveTools["call-1"]
                }
            }
            assertNotNull("tool never went live", live)
            stream!!.close()
            val newStream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never reconnected", newStream)
            val dropped = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repo.state.value.liveTools.containsKey("call-1")) delay(25)
                    true
                }
            }
            assertEquals(true, dropped)
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `M-023 a failed terminal resync materializes the finished answer into the transcript`() {
        // Open resync: empty history. Every terminal-resync attempt (calls
        // 1-3) fails with HTTP 500. The old code cleared the streaming
        // buffer on failure, so the finished answer vanished and only the
        // RELOAD_SESSION banner remained; keeping the streaming entry
        // instead would leave its spinner and hide its Copy/footer
        // forever (item 25). The text is materialized as a completed
        // transcript row: the answer stays visible with its footer, and
        // the streaming indicator still drops.
        val finished = """{"data":[{"id":"m1","type":"assistant","content":[{"type":"text","text":"Done"}],"time":{"created":1,"completed":2}}]}"""
        val stub = Stub(
            histories = listOf("""{"data":[]}""", "", "", "", finished),
            errorOnMessageCalls = setOf(1, 2, 3),
        )
        val (repo, scope) = repository(stub)
        try {
            repo.open("s1")
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
                    while (repo.state.value.streaming["m1"] != "Hello ") delay(25)
                    repo.state.value.streaming["m1"]
                }
            }
            assertEquals("Hello ", streamed)
            sendEvent("""{"type":"session.execution.succeeded","data":{"sessionID":"s1"}}""")
            val failed = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repo.state.value.lastFailure == null) delay(25)
                    true
                }
            }
            assertEquals(true, failed)
            // Item 25: the indicator drops. M-023: the text does not go
            // with it — it is a completed transcript row now.
            assertTrue(repo.state.value.streaming.isEmpty())
            assertEquals(false, repo.state.value.busy)
            val kept = repo.state.value.messages.singleOrNull { it.id == "m1" }
            assertNotNull("finished answer was lost by the failed terminal resync", kept)
            assertEquals("Hello", kept!!.plainText)
            assertNotNull(kept.time?.completed)

            // The next successful resync replaces the materialized row
            // with the persisted one — same id, no duplicate.
            stream!!.close()
            val newStream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never reconnected", newStream)
            val replaced = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repo.state.value.messages.none { it.plainText == "Done" }) delay(25)
                    true
                }
            }
            assertEquals(true, replaced)
            assertEquals(listOf("m1"), repo.state.value.messages.map { it.id })
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `M-245 closing the event stream drops the stuck socket at once`() {
        // The server holds the SSE connection open forever and never sends
        // a byte — the read loop blocks until the socket dies. close()
        // must cancel the in-flight call so the server sees EOF promptly,
        // instead of the socket lingering until the server's 90 s timeout.
        val closedAt = AtomicLong(0)
        val connections = AtomicInteger(0)
        val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val listener = Thread {
            while (true) {
                val connection = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
                Thread {
                    connection.use {
                        connections.incrementAndGet()
                        val input = it.getInputStream().bufferedReader()
                        // Drain the request line + headers first: without
                        // this, the blocking read below returns the first
                        // request byte ('G') instead of -1 on close.
                        input.readLine()
                        while (true) {
                            val header = input.readLine() ?: break
                            if (header.isEmpty()) break
                        }
                        val out = it.getOutputStream()
                        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n".encodeToByteArray())
                        out.flush()
                        // Block reading until the client closes the socket:
                        // a clean FIN reads -1, an abrupt reset throws —
                        // either way the socket is dead.
                        try {
                            if (input.read() == -1) closedAt.set(System.currentTimeMillis())
                        } catch (_: IOException) {
                            closedAt.set(System.currentTimeMillis())
                        }
                    }
                }.apply { isDaemon = true; start() }
            }
        }.apply { isDaemon = true; start() }
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val client = EngineClient("http://127.0.0.1:${socket.localPort}", "Basic dXNlcjp4")
            val stream = EventStream(client)
            val job = scope.launch { stream.events().collect {} }
            val connected = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (connections.get() < 1) delay(25)
                    true
                }
            }
            assertEquals(true, connected)
            stream.close()
            val dropped = runBlocking {
                withTimeoutOrNull(5_000) {
                    while (closedAt.get() == 0L) delay(25)
                    true
                }
            }
            assertEquals(true, dropped)
            job.cancel()
        } finally {
            scope.cancel()
            runCatching { socket.close() }
            listener.interrupt()
        }
    }
}
