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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A resync that was already in flight when the session changed must not
 * overwrite the new session's transcript (item 26).
 *
 * Over a real socket: the post-run resync for s1 is held up server-side
 * (1.5 s) while s2 opens immediately and loads an empty history. The stale
 * resync's response — one assistant message — arrives long after s2 is
 * current, and must be dropped. Without the session guard it lands in s2's
 * transcript.
 */
class ChatRepositoryResyncTest {

    @Test
    fun `stale resync does not overwrite the new session transcript`() {
        val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val eventOut = LinkedBlockingQueue<OutputStream>()
        val s1MessageCalls = AtomicInteger(0)
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
                            "/api/session/s1/message" in requestLine -> {
                                if (s1MessageCalls.incrementAndGet() == 2) {
                                    // The post-run resync: held up, then answers
                                    // with a message the new session must never see.
                                    Thread.sleep(1_500)
                                    val body = """{"data":[{"id":"m-old","type":"assistant","content":[{"type":"text","text":"OLD"}]}]}"""
                                    out.write(
                                        ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                            "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                                            .encodeToByteArray(),
                                    )
                                } else {
                                    val body = """{"data":[]}"""
                                    out.write(
                                        ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                            "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                                            .encodeToByteArray(),
                                    )
                                }
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
            stream!!.write(
                ("data: {\"type\":\"session.execution.succeeded\",\"data\":{\"sessionID\":\"s1\"}}\n\n")
                    .encodeToByteArray(),
            )
            stream.flush()

            // Wait until the stale resync is in flight (its request reached
            // the server and is being held), then switch sessions.
            val inFlight = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (s1MessageCalls.get() < 2) delay(25)
                    true
                }
            }
            assertEquals(true, inFlight)
            repository.open("s2")

            // The stale response lands 1.5 s after the switch; s2's own
            // resync answered immediately with an empty history.
            Thread.sleep(2_500)
            assertEquals("s2", repository.state.value.sessionID)
            assertTrue(repository.state.value.messages.isEmpty())
        } finally {
            scope.cancel()
            socket.close()
            listener.join(1_000)
        }
    }
}
