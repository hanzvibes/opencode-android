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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A chat whose credentials went stale (the engine restarted) must say so.
 *
 * Over a real socket: the history read succeeds, the event stream answers 401. The
 * scope uses a plain [Job] on purpose, so an exception that escaped the repository
 * would cancel it and fail the `isActive` check.
 */
class ChatRepositoryAuthTest {

    @Test
    fun `a rejected event stream surfaces an error and leaves the scope alive`() {
        val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val listener = Thread {
            while (true) {
                val connection = try { socket.accept() } catch (e: IOException) { break }
                connection.use {
                    val requestLine = runCatching { it.getInputStream().bufferedReader().readLine() }
                        .getOrNull().orEmpty()
                    val response = if ("/api/event" in requestLine) {
                        "HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    } else {
                        val body = """{"data":[]}"""
                        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                            "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
                    }
                    it.getOutputStream().write(response.encodeToByteArray())
                }
            }
        }.apply { isDaemon = true; start() }

        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val client = EngineClient("http://127.0.0.1:${socket.localPort}", "Basic dXNlcjp4")
            val repository = ChatRepository(SessionApi(client), EventStream(client), scope)
            repository.open("s1")

            val error = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.lastError == null) delay(25)
                    repository.state.value.lastError
                }
            }

            assertNotNull(error)
            assertTrue(error!!, "401" in error)
            assertEquals(true, scope.coroutineContext[Job]?.isActive)
        } finally {
            scope.cancel()
            runCatching { socket.close() }
            listener.interrupt()
        }
    }
}
