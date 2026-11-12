package dev.bidzz.opencode.core.api

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 2 (58-F4, 59-F4): cancelling a request must cancel it, not report it
 * as a failed one. Both [EngineClient.execute] and [FileApi.readText] used
 * to catch a bare `Exception` and return `ApiResult.Failure`, so a
 * cancelled job kept running in the caller's eyes — and a cancelled load
 * could still write its (stale) result afterwards.
 *
 * Over a real socket: the server accepts and never answers, so the only way
 * the call ends is the cancellation.
 */
class EngineClientCancellationTest {

    /** Accepts connections and never responds; the client hangs until cancelled. */
    private class SilentStub : AutoCloseable {
        private val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val port: Int get() = socket.localPort
        private val listener = Thread {
            while (true) {
                val connection = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
                Thread {
                    connection.use { while (!socket.isClosed) Thread.sleep(100) }
                }.apply { isDaemon = true; start() }
            }
        }.apply { isDaemon = true; start() }

        override fun close() {
            runCatching { socket.close() }
            listener.interrupt()
        }
    }

    @Test
    fun `58-F4 execute rethrows CancellationException`() {
        val stub = SilentStub()
        try {
            val client = EngineClient("http://127.0.0.1:${stub.port}", "Basic dXNlcjp4")
            val request = client.request("/api/session/s1/message").get().build()
            val thrown = runBlocking {
                val deferred = async(Dispatchers.IO) { client.execute(request) { it } }
                delay(500)
                deferred.cancel()
                runCatching { deferred.await() }.exceptionOrNull()
            }
            assertTrue("expected CancellationException, got $thrown", thrown is CancellationException)
        } finally {
            stub.close()
        }
    }

    @Test
    fun `59-F4 readText rethrows CancellationException`() {
        val stub = SilentStub()
        try {
            val client = EngineClient("http://127.0.0.1:${stub.port}", "Basic dXNlcjp4")
            val thrown = runBlocking {
                val deferred = async(Dispatchers.IO) { FileApi(client).readText("/tmp", "a.txt") }
                delay(500)
                deferred.cancel()
                runCatching { deferred.await() }.exceptionOrNull()
            }
            assertTrue("expected CancellationException, got $thrown", thrown is CancellationException)
        } finally {
            stub.close()
        }
    }
}
