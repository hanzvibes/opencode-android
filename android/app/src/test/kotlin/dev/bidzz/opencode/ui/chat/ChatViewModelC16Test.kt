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
import java.util.concurrent.atomic.AtomicInteger
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
 * Audit-200 Fase C16 (M-246): the queue flush must not be purely
 * edge-triggered.
 *
 * A real stub engine backs the view model. The run ends while the direct
 * send's POST is still in flight, so the busy→idle edge returns NONE and
 * the queued follow-up sits in an idle session with no run coming. A
 * later idle emission — here the reconnect resync after the stream drops —
 * must flush it anyway. Without the level check the queue would sit there
 * forever.
 */
class ChatViewModelC16Test {

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
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val port: Int get() = socket.localPort
        private val messageCalls = AtomicInteger(0)
        private val promptCalls = AtomicInteger(0)
        val promptCallsSeen: Int get() = promptCalls.get()
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
                                val call = messageCalls.getAndIncrement()
                                // Open + terminal resyncs: empty. The
                                // reconnect resync lands the finished
                                // answer, so its state write differs and
                                // the emission is not swallowed as equal.
                                val body = if (call < 2) {
                                    """{"data":[]}"""
                                } else {
                                    """{"data":[{"id":"m1","type":"assistant","content":[{"type":"text","text":"Done"}],"time":{"created":1,"completed":2}}]}"""
                                }
                                answer("200 OK", body)
                            }
                            "/api/session/s1/prompt" in requestLine -> {
                                val call = promptCalls.getAndIncrement()
                                // The first prompt is held 2 s so the run
                                // can end while its POST is still in
                                // flight; later prompts answer at once.
                                if (call == 0) Thread.sleep(2_000)
                                val text = if (call == 0) "first" else "cek juga X"
                                answer(
                                    "200 OK",
                                    """{"data":{"id":"u-p$call","type":"user","content":[{"type":"text","text":"$text"}]}}""",
                                )
                            }
                            requestLine.contains("/api/session/s1 ") -> {
                                answer("200 OK", """{"data":{"id":"s1"}}""")
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

    @Test
    fun `M-246 an idle emission flushes a queue the run-end edge could not`() {
        val stub = Stub()
        try {
            val api = EngineApi { "/tmp" }
            val vm = ChatViewModel(api, { "/tmp" })
            vm.onEngineStatus(
                EngineStatus(
                    state = EngineState.RUNNING,
                    credentials = EngineCredentials("http://127.0.0.1:${stub.port}", "x"),
                ),
            )
            vm.openSession("s1")
            val stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never connected", stream)
            val opened = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (vm.state.value.sessionID != "s1") delay(25)
                    true
                }
            }
            assertEquals(true, opened)
            fun sendEvent(json: String) {
                stream!!.write(("data: $json\n\n").encodeToByteArray())
                stream.flush()
            }

            // A direct send whose POST is held 2 s by the stub.
            val t0 = System.currentTimeMillis()
            vm.onDraftChange("first")
            vm.send()
            val posted = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (stub.promptCallsSeen < 1) delay(25)
                    true
                }
            }
            assertEquals(true, posted)
            assertEquals("first", vm.state.value.lastSentText)

            // The run starts; a follow-up queues behind it.
            sendEvent("""{"type":"session.execution.started","data":{"sessionID":"s1"}}""")
            val busy = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (!vm.state.value.busy) delay(25)
                    true
                }
            }
            assertEquals(true, busy)
            vm.onDraftChange("cek juga X")
            vm.send()
            assertEquals(1, vm.state.value.queuedPrompts.size)

            // The run ends while the first POST is still in flight: the
            // edge returns NONE and the queue stays put.
            sendEvent("""{"type":"session.execution.succeeded","data":{"sessionID":"s1"}}""")
            val idle = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (vm.state.value.busy) delay(25)
                    true
                }
            }
            assertEquals(true, idle)
            assertEquals(1, vm.state.value.queuedPrompts.size)
            assertEquals("first", vm.state.value.lastSentText)

            // Let the first POST settle, then drop the stream: the
            // reconnect resync is an idle emission with a non-empty
            // queue, and only the level check can flush it now.
            val waited = System.currentTimeMillis() - t0
            if (waited < 3_000) Thread.sleep(3_000 - waited)
            stream!!.close()
            val reconnected = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never reconnected", reconnected)
            val flushed = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (vm.state.value.lastSentText != "cek juga X") delay(25)
                    true
                }
            }
            assertEquals(true, flushed)
            val drained = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (vm.state.value.queuedPrompts.isNotEmpty()) delay(25)
                    true
                }
            }
            assertEquals(true, drained)
        } finally {
            stub.close()
        }
    }
}
