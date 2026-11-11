package dev.bidzz.opencode.core.api

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * A permission the engine still waits on must survive a reconnect/reopen (item 22).
 *
 * The stream only announces *new* asks, so the repository restores the pending
 * request from `GET /api/session/{id}/permission` during resync. Over a real
 * socket: history is empty, the permission list carries one request (with the
 * `source` field the stream shape has, which the client must ignore), and the
 * event stream is held open without events.
 */
class ChatRepositoryPermissionTest {

    @Test
    fun `resync restores a pending permission the engine still waits on`() {
        val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
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
                                while (!socket.isClosed) Thread.sleep(100)
                            }
                            "/api/session/s1/permission" in requestLine -> {
                                val body = """{"data":[{"id":"p1","sessionID":"s1","action":"edit","resources":["/tmp/x"],"save":[],"message":"Allow edit?","source":{"type":"tool"}}]}"""
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

            val pending = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.pendingPermission == null) delay(25)
                    repository.state.value.pendingPermission
                }
            }
            assertNotNull(pending)
            assertEquals("p1", pending!!.id)
            assertEquals("edit", pending.action)
            assertEquals(setOf("/tmp/x"), pending.resources)
        } finally {
            scope.cancel()
            socket.close()
            listener.join(1_000)
        }
    }
}
