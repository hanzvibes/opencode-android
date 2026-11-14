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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C5: M-091 — a send that returns after the user has
 * switched sessions must not write its message, busy flag, or failure
 * into the new session. The view-model half (the sendNowInner guards)
 * already existed; the repository half is the fix under test.
 *
 * Socket-Stub harness copied from [ChatRepositoryC3Test], with a
 * delayed / failing prompt endpoint so the send can be held in flight
 * across the switch.
 */
class ChatRepositoryC5Test {

    private class Stub(
        val promptDelayMs: Long = 0,
        val promptFailure: Boolean = false,
        val eventOut: LinkedBlockingQueue<OutputStream> = LinkedBlockingQueue(),
        val postedBodies: LinkedBlockingQueue<Pair<String, String>> = LinkedBlockingQueue(),
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val port: Int get() = socket.localPort
        val promptRequests = AtomicInteger(0)
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
                        var body = ""
                        if (contentLength > 0) {
                            val chars = CharArray(contentLength)
                            var read = 0
                            while (read < contentLength) {
                                val n = reader.read(chars, read, contentLength - read)
                                if (n < 0) break
                                read += n
                            }
                            body = String(chars, 0, read)
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
                            "/prompt" in requestLine -> {
                                promptRequests.incrementAndGet()
                                postedBodies.put(requestLine to body)
                                if (promptDelayMs > 0) Thread.sleep(promptDelayMs)
                                if (promptFailure) {
                                    answer("500 Internal Server Error", """{"error":"engine blew up"}""")
                                } else {
                                    answer(
                                        "200 OK",
                                        """{"data":{"id":"m1","type":"user","text":"hello from s1"}}""",
                                    )
                                }
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

    private fun openAndStream(stub: Stub, scope: CoroutineScope): Pair<ChatRepository, OutputStream> {
        val repository = repository(stub, scope)
        repository.open("s1")
        val stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
        assertNotNull("event stream never connected", stream)
        return repository to stream!!
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
    fun `M-091 a send that returns after a switch writes nothing into the new session`() {
        val stub = Stub(promptDelayMs = 500)
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val (repository, _) = openAndStream(stub, scope)
            // Start a send in s1; the stub holds the prompt response.
            val sendJob = scope.launch { repository.send("hello from s1") }
            assertTrue(
                "the prompt never reached the stub",
                waitFor { stub.promptRequests.get() >= 1 },
            )
            // Switch while the admit is in flight. open() installs s2
            // synchronously; the resync runs on the repo scope.
            repository.open("s2")
            assertTrue(
                "the switch never installed s2",
                waitFor { repository.state.value.sessionID == "s2" },
            )
            runBlocking { sendJob.join() }
            // The prompt did land in s1 — and the stub answered 200 —
            // but s2's transcript must not gain s1's message, busy, or
            // failure.
            val posted = stub.postedBodies.poll(20, TimeUnit.SECONDS)
            assertNotNull("no prompt POST was captured", posted)
            assertTrue("the prompt went to the wrong session: ${posted!!.first}", "/api/session/s1/prompt" in posted.first)
            val state = repository.state.value
            assertEquals("s2", state.sessionID)
            assertTrue(
                "s1's message leaked into s2",
                state.messages.none { it.text == "hello from s1" },
            )
            assertFalse("s1's send set busy on s2", state.busy)
            assertNull("s1's send left a failure on s2", state.lastFailure)
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `M-091 a failed send that returns after a switch leaves no failure on the new session`() {
        val stub = Stub(promptDelayMs = 500, promptFailure = true)
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val (repository, _) = openAndStream(stub, scope)
            val sendJob = scope.launch { repository.send("hello from s1") }
            assertTrue(
                "the prompt never reached the stub",
                waitFor { stub.promptRequests.get() >= 1 },
            )
            repository.open("s2")
            assertTrue(
                "the switch never installed s2",
                waitFor { repository.state.value.sessionID == "s2" },
            )
            runBlocking { sendJob.join() }
            val state = repository.state.value
            assertEquals("s2", state.sessionID)
            assertNull("s1's failure leaked into s2", state.lastFailure)
            assertFalse("s1's failed send set busy on s2", state.busy)
        } finally {
            scope.cancel()
            stub.close()
        }
    }
}
