package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.PromptMention
import dev.bidzz.opencode.core.api.dto.PromptSkillAttachment
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C3: the permission queue (M-060/M-061) and the skill
 * attachment on send (M-067), over a real socket, following the
 * [ChatRepositoryC2Test] harness.
 */
class ChatRepositoryC3Test {

    /**
     * Minimal engine: answers `/api/session/<id>/message` with
     * `{"data":[]}`, serves GET `/api/session/s1/permission` from
     * [permissionLists] in call order (optionally delayed, for the
     * resync race), records POST bodies in [postedBodies], and holds
     * one event stream open per connection, handing each output stream
     * to [eventOut].
     */
    private class Stub(
        val permissionLists: List<String> = listOf("""{"data":[]}"""),
        val permissionDelayMs: Long = 0,
        val eventOut: LinkedBlockingQueue<OutputStream> = LinkedBlockingQueue(),
        val postedBodies: LinkedBlockingQueue<Pair<String, String>> = LinkedBlockingQueue(),
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val port: Int get() = socket.localPort
        private val permissionCalls = AtomicInteger(0)
        val permissionRequests = AtomicInteger(0)
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
                        // Read the headers; a POST body follows, sized by
                        // Content-Length.
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
                            "/api/session/s1/permission" in requestLine && !requestLine.startsWith("POST") -> {
                                permissionRequests.incrementAndGet()
                                if (permissionDelayMs > 0) Thread.sleep(permissionDelayMs)
                                val callIndex = permissionCalls.getAndIncrement()
                                answer("200 OK", permissionLists.getOrNull(callIndex) ?: """{"data":[]}""")
                            }
                            "/prompt" in requestLine -> {
                                postedBodies.put(requestLine to body)
                                answer("200 OK", """{"data":{"id":"m1","type":"user","text":"commit do the thing"}}""")
                            }
                            else -> {
                                if (body.isNotEmpty()) postedBodies.put(requestLine to body)
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

    private fun askEvent(id: String, action: String = "edit"): String =
        """{"type":"permission.asked","data":{"id":"$id","sessionID":"s1","action":"$action","resources":["/tmp/x"],"save":[],"message":"Allow $action?"}}"""

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
    fun `M-060 a second ask queues behind the first instead of overwriting it`() {
        val stub = Stub()
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val (repository, stream) = openAndStream(stub, scope)
            stream.sendEvent(askEvent("p1"))
            assertTrue(
                "the first ask never became the head",
                waitFor { repository.state.value.pendingPermission?.id == "p1" },
            )
            stream.sendEvent(askEvent("p2", "write"))
            assertTrue(
                "the second ask never queued behind the first",
                waitFor { repository.state.value.pendingPermissionQueue.map { it.id } == listOf("p2") },
            )
            assertEquals("p1", repository.state.value.pendingPermission?.id)
            // Answering the head promotes the queued ask: the run is
            // still blocked on it, and it must surface.
            runBlocking { repository.replyPermission("once") }
            assertTrue(
                "the queued ask never promoted after the reply",
                waitFor {
                    repository.state.value.pendingPermission?.id == "p2" &&
                        repository.state.value.pendingPermissionQueue.isEmpty()
                },
            )
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `M-061 resync clears a pending the engine no longer waits on`() {
        val stub = Stub()
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val (repository, stream) = openAndStream(stub, scope)
            stream.sendEvent(askEvent("p1"))
            assertTrue(
                "the ask never became the head",
                waitFor { repository.state.value.pendingPermission?.id == "p1" },
            )
            // The engine's list is empty: nothing is waited on anymore.
            runBlocking { repository.refresh() }
            assertTrue(
                "the dead head was never cleared by resync",
                waitFor { repository.state.value.pendingPermission == null },
            )
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `M-061 an ask arriving mid-resync survives the empty list`() {
        val stub = Stub(permissionDelayMs = 500)
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val (repository, stream) = openAndStream(stub, scope)
            // The open resync already made one permission-list call;
            // the refresh below makes the second, delayed one.
            val refreshJob = scope.launch { repository.refresh() }
            assertTrue(
                "the refresh never started its permission-list call",
                waitFor { stub.permissionRequests.get() >= 2 },
            )
            // The ask lands while the list call is in flight — after
            // the resync stamped its start, before the empty snapshot
            // returns. It must survive the reconcile.
            stream.sendEvent(askEvent("p-new"))
            assertTrue(
                "the mid-resync ask never became the head",
                waitFor { repository.state.value.pendingPermission?.id == "p-new" },
            )
            runBlocking { refreshJob.join() }
            assertEquals("p-new", repository.state.value.pendingPermission?.id)
            assertTrue(repository.state.value.pendingPermissionQueue.isEmpty())
        } finally {
            scope.cancel()
            stub.close()
        }
    }

    @Test
    fun `M-067 a send with a skill carries the structured attachment`() {
        val stub = Stub()
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val (repository, _) = openAndStream(stub, scope)
            val skills = listOf(
                PromptSkillAttachment(
                    id = "sk_1",
                    mention = PromptMention(start = 0, end = 6, text = "commit"),
                ),
            )
            val result = runBlocking { repository.send("commit do the thing", skills) }
            assertTrue("the send failed: $result", result is ApiResult.Success)
            val posted = stub.postedBodies.poll(20, TimeUnit.SECONDS)
            assertNotNull("no prompt POST was captured", posted)
            assertTrue("the prompt went to the wrong path: ${posted!!.first}", "/prompt" in posted.first)
            assertTrue(
                "the prompt body carries no skill attachment: ${posted.second}",
                """"skills":[{"id":"sk_1","mention":{"start":0,"end":6,"text":"commit"}}]""" in posted.second,
            )
            // A prompt without skills must omit the field entirely
            // (explicitNulls = false), not send `"skills":null`.
            runBlocking { repository.send("plain prompt", null) }
            val plain = stub.postedBodies.poll(20, TimeUnit.SECONDS)
            assertNotNull("no second prompt POST was captured", plain)
            assertTrue(
                "a skill-less prompt still carries a skills field: ${plain!!.second}",
                "\"skills\"" !in plain.second,
            )
        } finally {
            scope.cancel()
            stub.close()
        }
    }
}
