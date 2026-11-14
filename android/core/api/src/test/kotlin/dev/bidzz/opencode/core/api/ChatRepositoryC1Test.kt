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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C1: resync — a stale buffer shadowing newer history
 * (M-043), a terminal resync reviving "Working" after an aborted run
 * (M-045), and a mid-run resync clearing busy while the run is still
 * streaming (M-046). Over a real socket, following the
 * [ChatRepositoryB3Test] harness.
 */
class ChatRepositoryC1Test {

    /**
     * Minimal engine: answers `/api/session/<id>/message` from [histories]
     * in call order (any session), `{"data":[]}` for everything else, and
     * holds one event stream open per connection, handing each output
     * stream to [eventOut].
     */
    private class Stub(
        val histories: List<String> = listOf("""{"data":[]}"""),
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
                        fun answer(status: String, body: String) {
                            out.write(
                                ("HTTP/1.1 $status\r\nContent-Type: application/json\r\n" +
                                    "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
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
                            "/message" in requestLine -> {
                                val callIndex = messageCalls.getAndIncrement()
                                answer("200 OK", histories.getOrNull(callIndex) ?: """{"data":[]}""")
                            }
                            else -> {
                                answer("200 OK", """{"data":[]}""")
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

    private fun repository(stub: Stub, scope: CoroutineScope): ChatRepository {
        val client = EngineClient("http://127.0.0.1:${stub.port}", "Basic dXNlcjp4")
        return ChatRepository(SessionApi(client), EventStream(client), scope)
    }

    private fun openAndStream(stub: Stub, scope: CoroutineScope): Pair<ChatRepository, OutputStream> {
        val repository = repository(stub, scope)
        repository.open("s1")
        val stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
        assertNotNull("event stream never connected", stream)
        return repository to stream!!
    }

    private fun OutputStream.sendEvent(json: String) {
        write(("data: $json\n\n").encodeToByteArray())
        flush()
    }

    private fun waitFor(
        timeoutMs: Long = 20_000,
        check: () -> Boolean,
    ): Boolean = runBlocking {
        withTimeoutOrNull(timeoutMs) {
            while (!check()) delay(25)
            true
        } ?: false
    }

    @Test
    fun `C1 M-043 a diverged buffer drops to history`() {
        // The buffer holds "AAAA" for m1; the resync's history holds a
        // DIFFERENT text of the same length ("BBBB") — the old
        // length-only rule kept the stale buffer, so the transcript
        // showed the wrong text until the next delta.
        val diverged = """{"data":[{"id":"m1","type":"assistant","content":[{"type":"text","text":"BBBB"}]}]}"""
        val stub = Stub(histories = listOf("""{"data":[]}""", diverged))
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val (repository, stream) = openAndStream(stub, scope)
            stream.sendEvent("""{"type":"session.execution.started","data":{"sessionID":"s1"}}""")
            stream.sendEvent("""{"type":"session.text.started","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":0}}""")
            stream.sendEvent("""{"type":"session.text.delta","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":0,"delta":"AAAA"}}""")
            assertTrue(
                "streaming text never landed",
                waitFor { repository.state.value.streaming["m1"] == "AAAA" },
            )
            runBlocking { repository.refresh() }
            assertFalse("stale buffer survived the resync", repository.state.value.streaming.containsKey("m1"))
            val shown = repository.state.value.messages.firstOrNull { it.id == "m1" }?.plainText
            assertEquals("BBBB", shown)
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `C1 M-045 a terminal resync after an aborted run does not revive busy`() {
        // The run is aborted mid-stream; the terminal resync's history
        // still shows the unfinished assistant message (no completed
        // time, no idle marker). The old busy read revived the
        // "Working" spinner for a run that is dead.
        val unfinished = """{"data":[{"id":"m1","type":"assistant","content":[{"type":"text","text":"partial"}]}]}"""
        val stub = Stub(histories = listOf("""{"data":[]}""", unfinished))
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val (repository, stream) = openAndStream(stub, scope)
            stream.sendEvent("""{"type":"session.execution.started","data":{"sessionID":"s1"}}""")
            assertTrue("busy never turned on", waitFor { repository.state.value.busy })
            stream.sendEvent("""{"type":"session.execution.interrupted","data":{"sessionID":"s1"}}""")
            assertTrue(
                "busy stayed on after the aborted run's resync",
                waitFor { !repository.state.value.busy },
            )
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `C1 M-046 a mid-run resync does not clear busy while streaming`() {
        // A reconnect resync mid-run fetches a history snapshot whose
        // newest row is from the previous turn (nothing live) — the old
        // busy read cleared busy, and the view model flushed a queued
        // follow-up into the middle of the run.
        val stub = Stub(histories = listOf("""{"data":[]}""", """{"data":[]}"""))
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val (repository, stream) = openAndStream(stub, scope)
            stream.sendEvent("""{"type":"session.execution.started","data":{"sessionID":"s1"}}""")
            stream.sendEvent("""{"type":"session.text.started","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":0}}""")
            stream.sendEvent("""{"type":"session.text.delta","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":0,"delta":"Hello "}}""")
            assertTrue(
                "streaming text never landed",
                waitFor { repository.state.value.streaming["m1"] == "Hello " },
            )
            runBlocking { repository.refresh() }
            assertTrue("busy cleared mid-run", repository.state.value.busy)
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `C1 M-043 isBufferAheadOfHistory`() {
        assertTrue(isBufferAheadOfHistory("Hello world", "Hello"))
        assertFalse(isBufferAheadOfHistory("Hello", "Hello"))
        assertFalse(isBufferAheadOfHistory("Hello", "Hello world"))
        assertFalse(isBufferAheadOfHistory("AAAA", "BBBB"))
        // Multi-block join: the buffer joins blocks with blank lines,
        // history with nothing — the same content is not ahead.
        assertFalse(isBufferAheadOfHistory("AB\n\nCD", "ABCD"))
        assertTrue(isBufferAheadOfHistory("Hello", ""))
    }
}
