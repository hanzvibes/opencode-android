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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase B3a: the repository-side chat races — permission restore
 * (M-547/M-081/M-550), terminal resyncs that outlive their session or run
 * (M-490/M-375), stale text after the run ends (M-558), and a shorter
 * streaming buffer shadowing longer history (M-044). Over a real socket,
 * following the [ChatRepositoryResyncStreamingTest] harness.
 */
class ChatRepositoryB3Test {

    /**
     * Minimal engine: answers `/api/session/<id>/message` from [histories]
     * in call order (any session), `{"data":[]}` for everything else, and
     * holds one event stream open per connection, handing each output
     * stream to [eventOut]. [permissionsJson] is served for the permission
     * list; a prompt POST is answered with a bare user message so [send]
     * succeeds.
     */
    private class Stub(
        val histories: List<String> = listOf("""{"data":[]}"""),
        val eventOut: LinkedBlockingQueue<OutputStream> = LinkedBlockingQueue(),
        /** Answer these 0-based `/message` calls with HTTP 500. */
        val errorOnMessageCalls: Set<Int> = emptySet(),
        /** Sleep this long before answering each `/message` call. */
        val messageDelayMs: Long = 0L,
        /** Body served for `GET /api/session/<id>/permission`. */
        val permissionsJson: String = """{"data":[]}""",
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
                                if (messageDelayMs > 0) Thread.sleep(messageDelayMs)
                                if (callIndex in errorOnMessageCalls) {
                                    answer("500 Internal Server Error", """{"error":{"code":500,"message":"boom"}}""")
                                    return@Thread
                                }
                                answer("200 OK", histories.getOrNull(callIndex) ?: """{"data":[]}""")
                            }
                            "/prompt" in requestLine -> {
                                answer("200 OK", """{"data":{"id":"m-new","type":"user","text":"hi"}}""")
                            }
                            "/permission/" in requestLine && "/reply" in requestLine -> {
                                answer("200 OK", """{"data":[]}""")
                            }
                            "/permission" in requestLine -> {
                                answer("200 OK", permissionsJson)
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

    private fun permissionJson(vararg ids: String): String {
        val entries = ids.joinToString(",") { id ->
            """{"id":"$id","sessionID":"s1","action":"edit","resources":["/tmp/x"],"save":[],"message":"Allow edit?"}"""
        }
        return """{"data":[$entries]}"""
    }

    private fun repository(stub: Stub, scope: CoroutineScope, gate: PermissionRestoreGate = PermissionRestoreGate()): ChatRepository {
        val client = EngineClient("http://127.0.0.1:${stub.port}", "Basic dXNlcjp4")
        return ChatRepository(SessionApi(client), EventStream(client), scope, gate)
    }

    @Test
    fun `B3 M-550 resync restores the first restorable permission, not only the first entry`() {
        // The engine lists the just-answered p1 first and a fresh p2
        // behind it: the old code only ever checked the first entry, so
        // the answered one hid the live one forever.
        val stub = Stub(permissionsJson = permissionJson("p1", "p2"))
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val repository = repository(stub, scope)
            repository.open("s1")
            val stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never connected", stream)

            val first = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.pendingPermission?.id != "p1") delay(25)
                    repository.state.value.pendingPermission?.id
                }
            }
            assertEquals("p1", first)

            runBlocking { repository.replyPermission("p1", "once") }
            val cleared = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.pendingPermission != null) delay(25)
                    true
                }
            }
            assertTrue("answered permission never cleared", cleared == true)

            stream!!.close()
            val newStream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never reconnected", newStream)
            val restored = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.pendingPermission?.id != "p2") delay(25)
                    repository.state.value.pendingPermission?.id
                }
            }
            assertEquals("p2", restored)
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `B3 M-547 the answered suppression survives a repository rebuild through the shared gate`() {
        // The repository is rebuilt on every attach, reload and new
        // session: an answer recorded in a dead instance must still
        // suppress the restore in the next one, or the dead prompt
        // comes back the moment the view model rebuilds.
        val stub = Stub(permissionsJson = permissionJson("p1"))
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        val gate = PermissionRestoreGate()
        try {
            val first = repository(stub, scope, gate)
            first.open("s1")
            val stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never connected", stream)
            val shown = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (first.state.value.pendingPermission?.id != "p1") delay(25)
                    first.state.value.pendingPermission?.id
                }
            }
            assertEquals("p1", shown)

            runBlocking { first.replyPermission("p1", "once") }
            val cleared = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (first.state.value.pendingPermission != null) delay(25)
                    true
                }
            }
            assertTrue("answered permission never cleared", cleared == true)
            first.close()

            val second = repository(stub, scope, gate)
            second.open("s1")
            val newStream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never reconnected", newStream)
            runBlocking { delay(1_500) }
            assertNull("dead permission resurrected after rebuild", second.state.value.pendingPermission)
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `B3 M-081 a dead run's ask is not restored until the session sends again`() {
        // Stop (or a finish) ends the run: the engine still lists its
        // ask, but no answer can land anymore. The resync must not bring
        // it back — until the session sends again, when a fresh run makes
        // the ask live again.
        val stub = Stub(permissionsJson = permissionJson("p1"))
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val repository = repository(stub, scope)
            repository.open("s1")
            var stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never connected", stream)
            fun sendEvent(json: String) {
                stream!!.write(("data: $json\n\n").encodeToByteArray())
                stream.flush()
            }

            val shown = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.pendingPermission?.id != "p1") delay(25)
                    repository.state.value.pendingPermission?.id
                }
            }
            assertEquals("p1", shown)

            sendEvent("""{"type":"session.execution.succeeded","data":{"sessionID":"s1"}}""")
            val dropped = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.pendingPermission != null) delay(25)
                    true
                }
            }
            assertTrue("terminal event never dropped the dead ask", dropped == true)

            stream!!.close()
            stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never reconnected", stream)
            runBlocking { delay(1_500) }
            assertNull("dead ask restored after reconnect", repository.state.value.pendingPermission)

            val sent = runBlocking { repository.send("hi") }
            assertTrue("send failed: $sent", sent is ApiResult.Success)

            stream!!.close()
            stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never reconnected after send", stream)
            val restored = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.pendingPermission?.id != "p1") delay(25)
                    repository.state.value.pendingPermission?.id
                }
            }
            assertEquals("p1", restored)
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `B3 M-558 late text events after the terminal event do not resurrect streaming`() {
        // The run ended and the resync already persisted m1 finished:
        // a late delta for it — and one for a message that never existed
        // — must not paint a streaming entry over the finished answer.
        val finished = """{"data":[{"id":"m1","type":"assistant","content":[{"type":"text","text":"Hello "}],"time":{"created":1,"completed":2}}]}"""
        val stub = Stub(histories = listOf("""{"data":[]}""", finished))
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val repository = repository(stub, scope)
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

            sendEvent("""{"type":"session.execution.succeeded","data":{"sessionID":"s1"}}""")
            val persisted = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.messages.none { it.id == "m1" && it.time?.completed != null }) delay(25)
                    repository.state.value.messages.first { it.id == "m1" }
                }
            }
            assertEquals("Hello ", persisted!!.plainText)

            sendEvent("""{"type":"session.text.delta","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":0,"delta":" STALE"}}""")
            sendEvent("""{"type":"session.text.delta","data":{"sessionID":"s1","assistantMessageID":"m9","ordinal":0,"delta":"ghost"}}""")
            runBlocking { delay(500) }
            assertNull("late delta resurrected the finished message", repository.state.value.streaming["m1"])
            assertNull("late delta created a ghost streaming entry", repository.state.value.streaming["m9"])
            assertEquals("Hello ", repository.state.value.messages.first { it.id == "m1" }.plainText)
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `B3 M-044 a longer history drops the shorter streaming buffer on resync`() {
        // Deltas were missed during a disconnect: the buffer holds
        // "Hello " while history already has the fuller text. The
        // overlay prefers streaming text, so without the drop the
        // transcript would show the shorter, older text until the next
        // delta — if one ever comes.
        val fuller = """{"data":[{"id":"m1","type":"assistant","content":[{"type":"text","text":"Hello there, full answer"}],"time":{"created":1}},{"id":"m0","type":"user","text":"hi"}]}"""
        val stub = Stub(histories = listOf("""{"data":[]}""", fuller))
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val repository = repository(stub, scope)
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

            stream!!.close()
            val newStream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never reconnected", newStream)
            val dropped = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.streaming["m1"] != null) delay(25)
                    repository.state.value.messages.firstOrNull { it.id == "m1" }?.plainText
                }
            }
            assertEquals("Hello there, full answer", dropped)
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `B3 M-490 a terminal resync that wakes after a switch does not fail the new session`() {
        // The terminal resync's first attempt is still in flight (slow
        // server) when the user opens s2: its failure belongs to s1 and
        // must never land on s2 as a failure banner.
        val stub = Stub(messageDelayMs = 1_500L, errorOnMessageCalls = setOf(1))
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val repository = repository(stub, scope)
            repository.open("s1")
            val stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never connected", stream)
            fun sendEvent(json: String) {
                stream!!.write(("data: $json\n\n").encodeToByteArray())
                stream.flush()
            }
            // Let the (delayed) open resync finish first.
            runBlocking { delay(2_000) }

            sendEvent("""{"type":"session.execution.succeeded","data":{"sessionID":"s1"}}""")
            runBlocking { delay(300) }
            repository.open("s2")
            val newStream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never reconnected", newStream)
            // The orphan loop: attempt 1 fails ~1.5s after the terminal
            // event, 1s backoff, attempt 2 must bail on the session
            // check. s2's own resync finished long before this.
            runBlocking { delay(4_500) }
            assertNull("old run's failure leaked onto the new session", repository.state.value.lastFailure)
            assertEquals(false, repository.state.value.resyncFailed)
            assertEquals("s2", repository.state.value.sessionID)
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `B3 M-375 a new run started during the terminal retry window is left alone`() {
        // The terminal resync fails and retries (1s, then 2s). A new run
        // starts in that window: the late retries must stop touching
        // state, or they wipe the new run's streaming text and stick the
        // old run's failure onto it.
        val stub = Stub(errorOnMessageCalls = setOf(1, 2, 3))
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val repository = repository(stub, scope)
            repository.open("s1")
            val stream = stub.eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never connected", stream)
            fun sendEvent(json: String) {
                stream!!.write(("data: $json\n\n").encodeToByteArray())
                stream.flush()
            }

            sendEvent("""{"type":"session.execution.succeeded","data":{"sessionID":"s1"}}""")
            // Attempt 1 has failed by now; the loop is in its 1s backoff.
            runBlocking { delay(300) }
            val sent = runBlocking { repository.send("new prompt") }
            assertTrue("send failed: $sent", sent is ApiResult.Success)
            sendEvent("""{"type":"session.text.delta","data":{"sessionID":"s1","assistantMessageID":"m2","ordinal":0,"delta":"new text"}}""")
            val streamed = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.streaming["m2"] != "new text") delay(25)
                    repository.state.value.streaming["m2"]
                }
            }
            assertEquals("new text", streamed)
            // Let the whole retry loop exhaust itself (1s + 2s backoffs).
            runBlocking { delay(4_000) }
            assertEquals("new text", repository.state.value.streaming["m2"])
            assertNull("old run's failure overwrote the new run", repository.state.value.lastFailure)
        } finally {
            scope.cancel()
            stub.close()
        }
    }
}
