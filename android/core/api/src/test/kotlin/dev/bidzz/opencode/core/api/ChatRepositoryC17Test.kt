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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit-200 Fase C17: malformed SSE events must not kill the collector
 * (M-265), and a reconnect resync fetches permissions only when they
 * may be stale (M-254).
 *
 * The stub follows the [ChatRepositoryC16Test] harness: a real loopback
 * socket, `/api/session/s1/message` answered from [histories] in call
 * order, `/api/session/s1/permission` counted, `{"data":[]}` for
 * everything else, one event stream held open per connection.
 */
class ChatRepositoryC17Test {

    private class Stub(
        val histories: List<String> = listOf("""{"data":[]}"""),
        val eventOut: LinkedBlockingQueue<OutputStream> = LinkedBlockingQueue(),
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val port: Int get() = socket.localPort
        val messageCalls = AtomicInteger(0)
        val permissionCalls = AtomicInteger(0)
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
                                val body = histories.getOrNull(callIndex) ?: histories.lastOrNull() ?: """{"data":[]}"""
                                out.write(
                                    ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                        "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                                        .encodeToByteArray(),
                                )
                            }
                            "/api/session/s1/permission" in requestLine -> {
                                permissionCalls.incrementAndGet()
                                val body = """{"data":[]}"""
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

    private fun waitFor(
        timeoutMs: Long = 20_000,
        condition: () -> Boolean,
    ): Boolean = runBlocking {
        withTimeoutOrNull(timeoutMs) {
            while (!condition()) delay(25)
            true
        } ?: false
    }

    @Test
    fun `M-265 malformed event data does not kill the collector or force a reconnect`() {
        val stub = Stub()
        val (repo, scope) = repository(stub)
        try {
            repo.open("s1")
            val stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never connected", stream)
            fun sendEvent(json: String) {
                stream!!.write(("data: $json\n\n").encodeToByteArray())
                stream.flush()
            }
            // `data` is a string, then an array — neither is the object
            // the handlers read fields from.
            sendEvent("""{"type":"session.updated","data":"not-an-object"}""")
            sendEvent("""{"type":"session.updated","data":["a","b"]}""")
            // PERMISSION_ASKED with `resources` as an object holding a
            // nested object — the old code threw reading it as a set.
            sendEvent(
                """{"type":"permission.asked","data":{"id":"p1","action":"read","resources":{"weird":{"nested":true}}}}""",
            )
            // A permission ask with no id at all.
            sendEvent("""{"type":"permission.asked","data":{"action":"read"}}""")
            Thread.sleep(500)
            // No reconnect resync was forced: only the open resync ran.
            assertEquals(1, stub.messageCalls.get())
            // The collector is still alive: a well-formed tool event
            // after the malformed ones still goes live.
            sendEvent("""{"type":"session.tool.input.started","data":{"sessionID":"s1","assistantMessageID":"m1","id":"call-1","name":"read"}}""")
            val live = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repo.state.value.liveTools["call-1"]?.call?.status != "streaming") delay(25)
                    repo.state.value.liveTools["call-1"]
                }
            }
            assertNotNull("collector died on the malformed events", live)
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `M-254 the open resync fetches permissions once`() {
        val stub = Stub()
        val (repo, scope) = repository(stub)
        try {
            repo.open("s1")
            assertEquals(
                true,
                waitFor { stub.permissionCalls.get() == 1 },
            )
            assertEquals(1, stub.permissionCalls.get())
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `M-254 a quick reconnect skips the permission round trip`() {
        val stub = Stub()
        val (repo, scope) = repository(stub)
        try {
            repo.open("s1")
            val stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never connected", stream)
            assertEquals(true, waitFor { stub.permissionCalls.get() == 1 })
            // A fresh event stamps the stream as alive just now, so the
            // reconnect a beat later sees a clean flag and a short gap.
            stream!!.write(
                ("data: {\"type\":\"session.updated\",\"data\":{\"sessionID\":\"s1\"}}\n\n").encodeToByteArray(),
            )
            stream.flush()
            Thread.sleep(100)
            stream.close()
            val newStream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never reconnected", newStream)
            assertEquals(true, waitFor { stub.messageCalls.get() >= 2 })
            Thread.sleep(500)
            // The reconnect resync ran (a new stream is up, messages
            // re-fetched) but permissions were not re-fetched.
            assertEquals(1, stub.permissionCalls.get())
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `M-254 refresh re-marks permissions dirty and re-fetches them`() {
        val stub = Stub()
        val (repo, scope) = repository(stub)
        try {
            repo.open("s1")
            assertEquals(true, waitFor { stub.permissionCalls.get() == 1 })
            runBlocking { repo.refresh() }
            assertEquals(true, waitFor { stub.permissionCalls.get() == 2 })
            assertEquals(2, stub.permissionCalls.get())
        } finally {
            scope.cancel()
            stub.close()
        }
    }
}
