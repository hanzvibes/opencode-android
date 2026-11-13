package dev.bidzz.opencode.core.api

import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 6 batch 6c (58-F5): a resync must not restore a permission request
 * that is being answered right now, or was answered moments ago — the
 * engine can keep listing it until it processes the reply, and bringing
 * it back shows a dead prompt that fails again when answered.
 */
class ChatRepositoryPermissionRestoreTest {

    @Test
    fun `58-F5 a request with a reply in flight is not restored`() {
        assertFalse(
            shouldRestorePermission(
                requestId = "p1",
                replyingPermissionId = "p1",
                lastAnsweredPermissionId = null,
                lastAnsweredPermissionAtMs = 0L,
                nowMs = 1_000L,
            ),
        )
    }

    @Test
    fun `58-F5 a request answered moments ago is not restored`() {
        assertFalse(
            shouldRestorePermission(
                requestId = "p1",
                replyingPermissionId = null,
                lastAnsweredPermissionId = "p1",
                lastAnsweredPermissionAtMs = 1_000L,
                nowMs = 1_000L + ANSWERED_PERMISSION_SUPPRESS_MS - 1,
            ),
        )
    }

    @Test
    fun `58-F5 a request answered past the window is restored again`() {
        assertTrue(
            shouldRestorePermission(
                requestId = "p1",
                replyingPermissionId = null,
                lastAnsweredPermissionId = "p1",
                lastAnsweredPermissionAtMs = 1_000L,
                nowMs = 1_000L + ANSWERED_PERMISSION_SUPPRESS_MS,
            ),
        )
    }

    @Test
    fun `58-F5 a different request is restored while another is being answered`() {
        assertTrue(
            shouldRestorePermission(
                requestId = "p2",
                replyingPermissionId = "p1",
                lastAnsweredPermissionId = "p1",
                lastAnsweredPermissionAtMs = 1_000L,
                nowMs = 1_001L,
            ),
        )
    }

    @Test
    fun `58-F5 a request with nothing outstanding is restored`() {
        assertTrue(
            shouldRestorePermission(
                requestId = "p1",
                replyingPermissionId = null,
                lastAnsweredPermissionId = null,
                lastAnsweredPermissionAtMs = 0L,
                nowMs = 1_000L,
            ),
        )
    }

    /**
     * End to end (58-F5): the engine keeps listing the request after it
     * was answered, so the reconnect resync that follows must not bring
     * the dead prompt back.
     */
    @Test
    fun `58-F5 answering a permission then reconnecting does not restore it`() {
        val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val eventOut = LinkedBlockingQueue<OutputStream>()
        val listener = Thread {
            while (true) {
                val connection = try { socket.accept() } catch (e: IOException) { break }
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
                            // The engine keeps listing p1 even after the
                            // reply: that stale listing is the bug's trigger.
                            "/api/session/s1/permission" in requestLine -> {
                                val body = """{"data":[{"id":"p1","sessionID":"s1","action":"edit","resources":["/tmp/x"],"save":[],"message":"Allow edit?"}]}"""
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

        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val client = EngineClient("http://127.0.0.1:${socket.localPort}", "Basic dXNlcjp4")
            val repository = ChatRepository(SessionApi(client), EventStream(client), scope)
            repository.open("s1")
            val stream = eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never connected", stream)

            val pending = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.pendingPermission == null) delay(25)
                    repository.state.value.pendingPermission
                }
            }
            assertEquals("p1", pending!!.id)

            val reply = runBlocking { repository.replyPermission("once") }
            assertTrue(reply is ApiResult.Success)
            assertNull(repository.state.value.pendingPermission)

            // Drop the stream: the reconnect resync lists p1 again, and
            // must not resurrect it.
            stream!!.close()
            val newStream = eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never reconnected", newStream)
            runBlocking { delay(1_000) }
            assertNull(
                "answered permission came back after reconnect resync",
                repository.state.value.pendingPermission,
            )
        } finally {
            scope.cancel()
            runCatching { socket.close() }
            listener.interrupt()
        }
    }
}
