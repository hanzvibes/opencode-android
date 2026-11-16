package dev.bidzz.opencode.ui.chat

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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Audit-200 Fase D12 (m-378/m-381): deep-link verification requests
 * must be ordered — a late first GET must never clobber the second
 * link, and a restore resolving while a deep-link GET is in flight
 * must yield to it.
 *
 * A real stub engine backs the view model, mirroring
 * [ChatViewModelC16Test]: the session-meta GET for one id is held
 * while the other answers at once, so the two results resolve in the
 * opposite order to the requests.
 */
class ChatViewModelD12Test {

    // `viewModelScope` runs on Dispatchers.Main, which has no Android
    // Looper on the JVM: install an Unconfined Main so the view model's
    // launches run on real threads, and reset it afterwards so no other
    // test class in the worker inherits it.
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
        private val s2GetDelayMs: Long = 0,
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
                            "/api/session/s2/message" in requestLine -> {
                                answer("200 OK", """{"data":[]}""")
                            }
                            requestLine.contains("/api/session/s1 ") -> {
                                if (s1GetDelayMs > 0) Thread.sleep(s1GetDelayMs)
                                answer("200 OK", """{"data":{"id":"s1"}}""")
                            }
                            requestLine.contains("/api/session/s2 ") -> {
                                if (s2GetDelayMs > 0) Thread.sleep(s2GetDelayMs)
                                answer("200 OK", """{"data":{"id":"s2"}}""")
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

    private fun waitForSession(vm: ChatViewModel, id: String): Boolean = runBlocking {
        withTimeoutOrNull(20_000) {
            while (vm.state.value.sessionID != id) delay(25)
            true
        }
    } == true

    @Test
    fun `D12 m-380 confirming the head drains the next queued link end to end`() {
        // Both links park before attach; the first opens on attach, and
        // the repository confirming it must drain the second link at
        // once — without the drain the session would stay on s1 until
        // another attach that may never come.
        val stub = Stub()
        try {
            val vm = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })
            vm.openSession("s1")
            vm.openSession("s2")
            assertEquals(listOf("s1", "s2"), vm.pendingOpensForTest())
            attach(vm, stub)
            assertNotNull("event stream never connected", stub.eventOut.poll(20, TimeUnit.SECONDS))
            assertEquals(true, waitForSession(vm, "s2"))
            Thread.sleep(1_000)
            assertEquals("s2", vm.state.value.sessionID)
            assertEquals(emptyList<String>(), vm.pendingOpensForTest())
        } finally {
            stub.close()
        }
    }

    @Test
    fun `D12 m-381 a late first deep-link GET must not clobber the second link`() {
        // The first link's verification is held 1.2 s; the second
        // answers at once, so the second session opens first. When the
        // held first result finally arrives it must be dropped — without
        // the ordering fix the session flips back to s1.
        val stub = Stub(s1GetDelayMs = 1_200)
        try {
            val vm = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })
            attach(vm, stub)
            vm.openSession("s1")
            vm.openSession("s2")
            assertNotNull("event stream never connected", stub.eventOut.poll(20, TimeUnit.SECONDS))
            assertEquals(true, waitForSession(vm, "s2"))
            // Past the held first GET's release: the session must stay.
            Thread.sleep(2_000)
            assertEquals("s2", vm.state.value.sessionID)
        } finally {
            stub.close()
        }
    }

    @Test
    fun `D12 m-378 a restore resolving while a deep-link GET is in flight yields to the link`() {
        // The restore's verification answers at once; the deep link's is
        // held 1.2 s, so the restore resolves first — while the link's
        // verification is still outstanding. The restore must yield
        // instead of opening the remembered session over the link.
        // Without the yield the session opens s1 and the link is lost.
        val stub = Stub(s2GetDelayMs = 1_200)
        try {
            val vm = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })
            attach(vm, stub)
            vm.onResume("s1")
            vm.openSession("s2")
            assertNotNull("event stream never connected", stub.eventOut.poll(20, TimeUnit.SECONDS))
            assertEquals(true, waitForSession(vm, "s2"))
            Thread.sleep(2_000)
            assertEquals("s2", vm.state.value.sessionID)
        } finally {
            stub.close()
        }
    }
}
