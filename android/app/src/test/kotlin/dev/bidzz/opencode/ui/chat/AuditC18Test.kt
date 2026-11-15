package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ChatState
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Audit-200 Fase C18: parked attachments (M-447) and send-during-restore
 * (M-449). M-513 (173-F3) needs no code: its kill mechanism died with C10
 * (M-149) — see c18-research.md.
 *
 * The M-449 tests run a real stub engine, following ChatViewModelC16Test:
 * the session GET is held open so the test can observe the restore in
 * flight, tap Send in that window, and prove no new session is minted.
 */
class AuditC18Test {

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

    private fun viewModel(store: ParkedComposerStore = InMemoryParkedComposerStore()) =
        ChatViewModel(EngineApi { "/tmp" }, { "/tmp" }, parkedStore = store)

    private fun repoState(busy: Boolean = false, sessionID: String? = null) =
        ChatState(busy = busy, sessionID = sessionID)

    private fun file(id: String, name: String = "notes.txt") =
        AttachedFile(id = id, name = name, content = "isi $name")

    @Test
    fun `M-447 removing an attachment rewrites the parked copy at once`() {
        val store = InMemoryParkedComposerStore()
        val vm = viewModel(store)
        vm.onRepoState(repoState(sessionID = "s1"))
        vm.onDraftChange("tolong review file ini")
        vm.addAttachment(file("f1"))

        // The engine dies: the composer parks under s1 with the file.
        vm.onEngineStatus(EngineStatus(state = EngineState.FAILED))
        assertEquals(listOf("f1"), store.get("s1")?.attachments?.map { it.id })

        // The user deletes the file while the engine is down. The parked
        // copy must forget it now — not 750 ms later, when a process
        // death could already have frozen the stale snapshot.
        vm.removeAttachment("f1")

        assertTrue(vm.state.value.pendingAttachments.isEmpty())
        assertEquals(emptyList<String>(), store.get("s1")?.attachments?.map { it.id })
        assertEquals("tolong review file ini", store.get("s1")?.draft)
    }

    @Test
    fun `M-447 adding an attachment rewrites the parked copy at once`() {
        val store = InMemoryParkedComposerStore()
        val vm = viewModel(store)
        vm.onRepoState(repoState(sessionID = "s1"))
        vm.onDraftChange("tolong review file ini")

        vm.onEngineStatus(EngineStatus(state = EngineState.FAILED))
        assertEquals(emptyList<String>(), store.get("s1")?.attachments?.map { it.id })

        vm.addAttachment(file("f1"))

        assertEquals(listOf("f1"), store.get("s1")?.attachments?.map { it.id })
    }

    @Test
    fun `M-447 removing the last attachment from an empty composer drops the parked entry`() {
        val store = InMemoryParkedComposerStore()
        val vm = viewModel(store)
        vm.onRepoState(repoState(sessionID = "s1"))
        vm.addAttachment(file("f1"))

        vm.onEngineStatus(EngineStatus(state = EngineState.FAILED))
        assertNotNull(store.get("s1"))

        vm.removeAttachment("f1")

        // Nothing left to park: the entry goes instead of lingering as an
        // empty shell the next restore would resurrect.
        assertNull(store.get("s1"))
    }

    @Test
    fun `M-449 a send during the restore is held and mints no session`() {
        val stub = RestoreStub()
        try {
            val vm = viewModel()
            vm.onEngineStatus(
                EngineStatus(
                    state = EngineState.RUNNING,
                    credentials = EngineCredentials("http://127.0.0.1:${stub.port}", "x"),
                ),
            )
            vm.onDraftChange("tolong review")

            vm.onResume("s1")

            // The GET is still held open by the stub: the restore is in
            // flight, and the composer must say so.
            assertEquals("s1", vm.restoringSessionForTest())
            assertTrue(vm.state.value.restoringSession)

            // A Send tap (and a follow-up chip tap) in this window must
            // not mint a new session for the draft.
            vm.send()
            assertFalse(vm.sendFollowUp("lanjut"))
            assertEquals("tolong review", vm.state.value.draft)
            assertEquals(0, stub.createCallsSeen)

            // The restore lands: the flag clears and the session is s1.
            val restored = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (vm.state.value.sessionID != "s1") delay(25)
                    true
                }
            }
            assertEquals(true, restored)
            assertFalse(vm.state.value.restoringSession)
            assertNull(vm.restoringSessionForTest())

            // Sending now goes to s1 — still without creating a session.
            vm.send()
            val posted = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (stub.promptCallsSeen < 1) delay(25)
                    true
                }
            }
            assertEquals(true, posted)
            assertEquals(0, stub.createCallsSeen)
            assertEquals("tolong review", vm.state.value.lastSentText)
        } finally {
            stub.close()
        }
    }

    @Test
    fun `M-449 a fresh session during the restore lifts the hold at once`() {
        val stub = RestoreStub()
        try {
            val vm = viewModel()
            vm.onEngineStatus(
                EngineStatus(
                    state = EngineState.RUNNING,
                    credentials = EngineCredentials("http://127.0.0.1:${stub.port}", "x"),
                ),
            )
            vm.onDraftChange("tolong review")

            vm.onResume("s1")
            assertEquals("s1", vm.restoringSessionForTest())

            // The user explicitly chooses a fresh chat: the restore is
            // abandoned and sending is allowed again right away.
            vm.newSession()
            assertNull(vm.restoringSessionForTest())
            assertFalse(vm.state.value.restoringSession)

            // The draft was parked under s1 by the switch; retype and
            // send in the fresh chat — a new session is minted now, and
            // only now.
            vm.onDraftChange("halo baru")
            vm.send()
            val created = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (stub.createCallsSeen < 1) delay(25)
                    true
                }
            }
            assertEquals(true, created)
        } finally {
            stub.close()
        }
    }

    /**
     * A stub engine for the restore tests. The session GET is held 2 s on
     * its first call so the restore stays in flight while the test taps
     * Send; everything else answers at once. Session creates are counted
     * — the M-449 fix must never trigger one during a restore.
     */
    private class RestoreStub : AutoCloseable {
        val eventOut: LinkedBlockingQueue<OutputStream> = LinkedBlockingQueue()
        private val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val port: Int get() = socket.localPort
        private val sessionGetCalls = AtomicInteger(0)
        private val promptCalls = AtomicInteger(0)
        private val createCalls = AtomicInteger(0)
        val promptCallsSeen: Int get() = promptCalls.get()
        val createCallsSeen: Int get() = createCalls.get()
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
                            "/prompt" in requestLine -> {
                                promptCalls.getAndIncrement()
                                answer(
                                    "200 OK",
                                    """{"data":{"id":"u-p1","type":"user","content":[{"type":"text","text":"tolong review"}]}}""",
                                )
                            }
                            requestLine.startsWith("POST /api/session") -> {
                                createCalls.getAndIncrement()
                                answer("200 OK", """{"data":{"id":"s-new"}}""")
                            }
                            requestLine.contains("/api/session/s1 ") -> {
                                // The restore's verifying GET: hold it open
                                // once so the test sees the in-flight state.
                                if (sessionGetCalls.getAndIncrement() == 0) Thread.sleep(2_000)
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
}
