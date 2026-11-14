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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C2 (M-058): a tool first seen mid-call — never announced
 * by `session.tool.input.started` — was dropped by `applyToolEvent`,
 * so the live trace had a hole and the progress header could claim the
 * run was done while a tool still ran. Over a real socket, following the
 * [ChatRepositoryC1Test] harness.
 */
class ChatRepositoryC2Test {

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
    fun `M-058 a tool called without an earlier start still appears live`() {
        val stub = Stub()
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val (repository, stream) = openAndStream(stub, scope)
            // No `session.tool.input.started` for call-9 — the engine
            // first mentions it with `session.tool.called`.
            stream.sendEvent("""{"type":"session.tool.called","data":{"sessionID":"s1","assistantMessageID":"m1","id":"call-9","input":{"command":"ls"}}}""")
            assertTrue(
                "the unseen tool never appeared in liveTools",
                waitFor { repository.state.value.liveTools["call-9"]?.call?.status == "running" },
            )
            val call = repository.state.value.liveTools["call-9"]?.call
            assertEquals("m1", repository.state.value.liveTools["call-9"]?.messageID)
            // The name is unknown until a resync supplies the history
            // row; the placeholder keeps the id and the input instead
            // of dropping the update.
            assertEquals("", call?.name)
            assertNotNull(call?.input)
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `M-058 a tool that succeeds without an earlier start still lands`() {
        val stub = Stub()
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val (repository, stream) = openAndStream(stub, scope)
            stream.sendEvent("""{"type":"session.tool.success","data":{"sessionID":"s1","assistantMessageID":"m1","id":"call-10","content":[{"type":"text","text":"hello"}]}}""")
            assertTrue(
                "the unseen success never appeared in liveTools",
                waitFor { repository.state.value.liveTools["call-10"]?.call?.status == "completed" },
            )
            val call = repository.state.value.liveTools["call-10"]?.call
            assertEquals("hello", call?.output)
            assertEquals("", call?.name)
        } finally {
            scope.cancel()
            stub.close()
        }
    }
}
