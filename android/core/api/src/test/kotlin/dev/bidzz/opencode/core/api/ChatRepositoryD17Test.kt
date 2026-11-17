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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D17: the transcript window survives a repository rebuild
 * (m-426) and a fully-absorbed prepend page stays unfolded (m-451).
 *
 * Same socket stub as [ChatRepositoryLoadEarlierTest]: the first
 * `/message` call serves the newest page (with cursor "c1"), the
 * cursor call serves the older page.
 */
class ChatRepositoryD17Test {

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

    private fun repository(
        stub: Stub,
        scope: CoroutineScope,
        windowMemory: TranscriptWindowMemory = TranscriptWindowMemory(),
    ): ChatRepository {
        val client = EngineClient("http://127.0.0.1:${stub.port}", "Basic dXNlcjp4")
        return ChatRepository(
            SessionApi(client),
            EventStream(client),
            scope,
            windowMemory = windowMemory,
        )
    }

    private fun awaitMessages(repository: ChatRepository, size: Int): Boolean =
        runBlocking {
            withTimeoutOrNull(20_000) {
                while (repository.state.value.messages.size != size) delay(25)
                true
            }
        } == true

    private fun awaitNotLoading(repository: ChatRepository): Boolean =
        runBlocking {
            withTimeoutOrNull(20_000) {
                while (repository.state.value.loadingEarlier) delay(25)
                true
            }
        } == true

    // ---- m-426: the widened window survives a rebuild -------------------

    @Test
    fun `m-426 a cursor-widened window re-opens wide after a rebuild`() {
        Stub().use { stub ->
            val scope = CoroutineScope(Job() + Dispatchers.IO)
            try {
                val memory = TranscriptWindowMemory()
                val first = repository(stub, scope, memory)
                first.open("s1")
                assertNotNull("event stream never connected", stub.awaitEventStream())
                assertTrue(awaitMessages(first, 100))
                first.loadEarlier()
                assertTrue(awaitMessages(first, 200))
                scope.cancel()

                // A fresh repository (as the view model builds on every
                // attach) sharing the same memory re-opens wide: the
                // resync asks for 200, not the default 100.
                val scope2 = CoroutineScope(Job() + Dispatchers.IO)
                try {
                    val second = repository(stub, scope2, memory)
                    second.open("s1")
                    assertNotNull("event stream never connected", stub.awaitEventStream())
                    assertTrue(awaitMessages(second, 100))
                    assertTrue(
                        "re-open did not ask for the remembered width",
                        stub.messageRequests.any { "limit=200" in it },
                    )
                } finally {
                    scope2.cancel()
                }
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun `m-426 a grow-widened window re-opens wide after a rebuild`() {
        Stub(noCursor = true).use { stub ->
            val scope = CoroutineScope(Job() + Dispatchers.IO)
            try {
                val memory = TranscriptWindowMemory()
                val first = repository(stub, scope, memory)
                first.open("s1")
                assertNotNull("event stream never connected", stub.awaitEventStream())
                assertTrue(awaitMessages(first, 100))
                first.loadEarlier()
                assertTrue(awaitNotLoading(first))
                assertTrue(
                    "load earlier did not grow the window",
                    stub.messageRequests.any { "limit=200" in it },
                )
                scope.cancel()

                val scope2 = CoroutineScope(Job() + Dispatchers.IO)
                try {
                    val second = repository(stub, scope2, memory)
                    second.open("s1")
                    assertNotNull("event stream never connected", stub.awaitEventStream())
                    assertTrue(awaitMessages(second, 100))
                    // Two limit=200 requests: the grow tap and the re-open.
                    assertEquals(
                        2,
                        stub.messageRequests.count { "limit=200" in it },
                    )
                } finally {
                    scope2.cancel()
                }
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun `m-426 a never-widened session still opens at the default width`() {
        Stub().use { stub ->
            val scope = CoroutineScope(Job() + Dispatchers.IO)
            try {
                val first = repository(stub, scope)
                first.open("s1")
                assertNotNull("event stream never connected", stub.awaitEventStream())
                assertTrue(awaitMessages(first, 100))
                assertTrue(stub.messageRequests.any { "limit=100" in it })
                assertTrue(stub.messageRequests.none { "limit=200" in it })
            } finally {
                scope.cancel()
            }
        }
    }

    // ---- m-451: a fully-absorbed page stays unfolded ---------------------

    @Test
    fun `m-451 a page that would be fully absorbed stays unfolded`() {
        // Newest window: assistant a2, then user u3. Older page: a single
        // assistant message a1 continuing a2. Folding it in would leave
        // the list the same size while the reading point sinks to the
        // middle of the merged item — so it stays a message of its own.
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
                assertTrue(awaitMessages(repository, 3))

                val messages = repository.state.value.messages
                assertEquals(listOf("a1", "a2", "u3"), messages.map { it.id })
                assertEquals("a1", messages[0].plainText)
                assertEquals("a2", messages[1].plainText)
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun `m-451 a page that contributes standalone messages still folds its boundary`() {
        // Older page: user x0, then assistant a1 continuing a2. The page
        // contributes x0 on its own, so the boundary fold is safe — the
        // list grows and the anchor holds.
        Stub(
            newest = listOf(
                """{"id":"a2","type":"assistant","content":[{"type":"text","text":"a2"}]}""",
                """{"id":"u3","type":"user","text":"u3"}""",
            ),
            older = listOf(
                """{"id":"x0","type":"user","text":"x0"}""",
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
                assertTrue(awaitMessages(repository, 3))

                val messages = repository.state.value.messages
                assertEquals(listOf("x0", "a2", "u3"), messages.map { it.id })
                assertEquals("a1a2", messages[1].plainText)
            } finally {
                scope.cancel()
            }
        }
    }
}
