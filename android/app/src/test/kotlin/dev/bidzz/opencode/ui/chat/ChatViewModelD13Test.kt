package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.R
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.engine.EngineCredentials
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.core.engine.EngineStatus
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Audit-200 Fase D13 (m-977): a deep-link verification GET must not hang
 * silently — it gets its own 10s timeout, an "opening…" indicator while
 * it runs, and a Retry after a defer. Same stub-engine harness as
 * [ChatViewModelD12Test].
 */
class ChatViewModelD13Test {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Stub(
        val eventOut: LinkedBlockingQueue<OutputStream> = LinkedBlockingQueue(),
        private val s1GetDelayMs: Long = 0,
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
                            "/api/session/s1/message" in requestLine -> {
                                answer("200 OK", """{"data":[]}""")
                            }
                            requestLine.contains("/api/session/s1 ") -> {
                                // The client cancels at 10s; answering a
                                // dead socket afterwards only logs.
                                runCatching {
                                    if (s1GetDelayMs > 0) Thread.sleep(s1GetDelayMs)
                                    answer("200 OK", """{"data":{"id":"s1"}}""")
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

    private fun attach(vm: ChatViewModel, stub: Stub) {
        vm.onEngineStatus(
            EngineStatus(
                state = EngineState.RUNNING,
                credentials = EngineCredentials("http://127.0.0.1:${stub.port}", "x"),
            ),
        )
    }

    private fun waitForOpening(vm: ChatViewModel, expected: Boolean, timeoutMs: Long = 15_000): Boolean =
        runBlocking {
            withTimeoutOrNull(timeoutMs) {
                while (vm.state.value.openingSession != expected) delay(25)
                true
            }
        } == true

    private fun waitForSession(vm: ChatViewModel, id: String): Boolean = runBlocking {
        withTimeoutOrNull(20_000) {
            while (vm.state.value.sessionID != id) delay(25)
            true
        }
    } == true

    @Test
    fun `D13 m-977 a verification GET that hangs past 10s defers with Retry`() {
        // The stub holds the GET for 11s — past the 10s verification
        // timeout, far short of the 120s client timeout. The user must
        // see "opening…" while it runs, then the defer banner with
        // Retry, not a silent 120s stare at the old session.
        val stub = Stub(s1GetDelayMs = 11_000)
        try {
            val vm = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })
            vm.openSession("s1")
            attach(vm, stub)
            assertTrue("opening indicator never went up", waitForOpening(vm, true))
            assertTrue("opening indicator never came down", waitForOpening(vm, false))
            assertEquals(R.string.session_link_verify_failed, vm.state.value.failureKey)
            assertEquals(RetryKind.RETRY_OPEN, vm.state.value.retryKind)
            assertEquals("s1", vm.pendingOpenForTest())
            assertEquals(null, vm.state.value.sessionID)
        } finally {
            stub.close()
        }
    }

    @Test
    fun `D13 m-977 a fast verification opens and clears the indicator`() {
        val stub = Stub()
        try {
            val vm = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })
            vm.openSession("s1")
            attach(vm, stub)
            // The "went up" half is proven by the slow test above — a
            // localhost GET can resolve before the first poll here.
            assertTrue(waitForSession(vm, "s1"))
            assertFalse(vm.state.value.openingSession)
            assertEquals(null, vm.pendingOpenForTest())
        } finally {
            stub.close()
        }
    }

    @Test
    fun `D13 m-977 retrying a deferred open re-attempts the verification`() {
        val vm = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })
        vm.openSession("s9")
        assertEquals("s9", vm.pendingOpenForTest())
        vm.deferSessionOpen()
        assertEquals(R.string.session_link_verify_failed, vm.state.value.failureKey)
        assertEquals(RetryKind.RETRY_OPEN, vm.state.value.retryKind)
        // No engine is attached, so the re-attempt fails at once with
        // the engine-down banner — the point is that it re-attempted
        // (the key changed) instead of no-op'ing, and the id is still
        // queued for the next attach.
        vm.retryPendingOpen()
        assertEquals(R.string.error_engine_not_running, vm.state.value.failureKey)
        assertEquals("s9", vm.pendingOpenForTest())
    }
}
