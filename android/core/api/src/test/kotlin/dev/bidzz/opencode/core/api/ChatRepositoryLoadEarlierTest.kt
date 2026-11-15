package dev.bidzz.opencode.core.api

import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Load earlier" walks the message cursor (audit-200 C12, M-169): one tap
 * fetches one older page and prepends it, instead of growing the window
 * and re-reading the whole transcript — 100 → 200 → 300 → … messages
 * fetched and re-parsed on every tap, quadratic in total.
 *
 * Over a real socket: the stub serves 200 messages in two 100-message
 * pages, the first with cursor "c1", the second without one.
 */
class ChatRepositoryLoadEarlierTest {

    private class Stub(
        newest: List<String> = (101..200).map { messageJson(it) },
        older: List<String> = (1..100).map { messageJson(it) },
        private val noCursor: Boolean = false,
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val port: Int get() = socket.localPort
        val messageRequests = ConcurrentLinkedQueue<String>()
        private val eventOut = LinkedBlockingQueue<OutputStream>()
        private val page1Body: String
        private val page2Body: String

        init {
            // The engine answers newest-first; the lists above are chronological.
            page1Body = """{"data":[${newest.reversed().joinToString(",")}]""" +
                (if (noCursor) "" else ""","cursor":{"next":"c1"}""") + "}"
            page2Body = """{"data":[${older.reversed().joinToString(",")}]}"""
        }

        private val listener = Thread {
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
                                messageRequests += requestLine
                                val body = if ("cursor=c1" in requestLine) page2Body else page1Body
                                val bytes = body.encodeToByteArray()
                                out.write(
                                    ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                        "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
                                        .encodeToByteArray(),
                                )
                                out.write(bytes)
                                out.flush()
                            }
                            else -> {
                                val body = """{"data":[]}""".encodeToByteArray()
                                out.write(
                                    ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                        "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                                        .encodeToByteArray(),
                                )
                                out.write(body)
                                out.flush()
                            }
                        }
                    }
                }.apply { isDaemon = true; start() }
            }
        }.apply { isDaemon = true; start() }

        fun awaitEventStream() = eventOut.poll(20, TimeUnit.SECONDS)

        override fun close() {
            runCatching { socket.close() }
            listener.join(1_000)
        }

        companion object {
            fun messageJson(n: Int): String =
                if (n % 2 == 1) """{"id":"a$n","type":"assistant","content":[{"type":"text","text":"a$n"}]}"""
                else """{"id":"u$n","type":"user","text":"u$n"}"""
        }
    }

    private fun repository(stub: Stub, scope: CoroutineScope): ChatRepository {
        val client = EngineClient("http://127.0.0.1:${stub.port}", "Basic dXNlcjp4")
        return ChatRepository(SessionApi(client), EventStream(client), scope)
    }

    private fun awaitMessages(repository: ChatRepository, size: Int): Boolean =
        runBlocking {
            withTimeoutOrNull(20_000) {
                while (repository.state.value.messages.size != size) delay(25)
                true
            }
        } == true

    @Test
    fun `load earlier fetches one older page by cursor and prepends it`() {
        Stub().use { stub ->
            val scope = CoroutineScope(Job() + Dispatchers.IO)
            try {
                val repository = repository(stub, scope)
                repository.open("s1")
                assertNotNull("event stream never connected", stub.awaitEventStream())
                assertTrue(awaitMessages(repository, 100))
                assertTrue(repository.state.value.hasMoreOlder)

                repository.loadEarlier()
                assertTrue(awaitMessages(repository, 200))

                // The whole transcript, oldest first, in order.
                val expectedIds = (1..200).map { n -> if (n % 2 == 1) "a$n" else "u$n" }
                assertEquals(expectedIds, repository.state.value.messages.map { it.id })
                assertFalse(repository.state.value.hasMoreOlder)
                // The tap walked the cursor with one page — it never grew
                // the window to 200 and re-read it.
                assertTrue(stub.messageRequests.any { "cursor=c1" in it && "limit=100" in it })
                assertTrue(stub.messageRequests.none { "limit=200" in it })
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun `a turn split across the page boundary still merges into one`() {
        // Newest window: assistant a2, then user u3. Older page: assistant a1.
        Stub(
            newest = listOf(
                """{"id":"a2","type":"assistant","content":[{"type":"text","text":"a2"}]}""",
                """{"id":"u3","type":"user","text":"u3"}""",
            ),
            older = listOf(
                """{"id":"a1","type":"assistant","content":[{"type":"text","text":"a1"}]}""",
            ),
        ).use { stub ->
            val scope = CoroutineScope(Job() + Dispatchers.IO)
            try {
                val repository = repository(stub, scope)
                repository.open("s1")
                assertNotNull("event stream never connected", stub.awaitEventStream())
                assertTrue(awaitMessages(repository, 2))
                assertTrue(repository.state.value.hasMoreOlder)

                repository.loadEarlier()
                val loaded = runBlocking {
                    withTimeoutOrNull(20_000) {
                        while (repository.state.value.loadingEarlier) delay(25)
                        true
                    }
                }
                assertEquals(true, loaded)

                // a1 + a2 folded into one turn; the user message untouched.
                val messages = repository.state.value.messages
                assertEquals(2, messages.size)
                assertEquals("a2", messages[0].id)
                assertEquals("a1a2", messages[0].plainText)
                assertEquals("u3", messages[1].id)
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun `without a cursor load earlier falls back to growing the window`() {
        Stub(noCursor = true).use { stub ->
            val scope = CoroutineScope(Job() + Dispatchers.IO)
            try {
                val repository = repository(stub, scope)
                repository.open("s1")
                assertNotNull("event stream never connected", stub.awaitEventStream())
                assertTrue(awaitMessages(repository, 100))
                assertTrue(repository.state.value.hasMoreOlder)

                repository.loadEarlier()
                val done = runBlocking {
                    withTimeoutOrNull(20_000) {
                        while (repository.state.value.hasMoreOlder) delay(25)
                        true
                    }
                }
                assertEquals(true, done)

                // The old path: a bigger window, no cursor anywhere.
                assertTrue(stub.messageRequests.any { "limit=200" in it })
                assertTrue(stub.messageRequests.none { "cursor=" in it })
                assertEquals(100, repository.state.value.messages.size)
            } finally {
                scope.cancel()
            }
        }
    }
}
