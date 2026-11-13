package dev.bidzz.opencode.core.api

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 6 batch 6c (59-F5): reading a file must not load the whole body
 * into RAM before the preview guard can cut it down. The body is streamed
 * with a cap on what is kept, while the true size and line count are still
 * measured over the whole body.
 */
class FileApiReadTextTest {

    /** Serves [body] for every `GET /api/fs/read/...` request. */
    private class Stub(val body: ByteArray) : AutoCloseable {
        private val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val port: Int get() = socket.localPort
        private val listener = Thread {
            while (true) {
                val connection = try { socket.accept() } catch (e: IOException) { break }
                Thread {
                    connection.use {
                        runCatching { it.getInputStream().bufferedReader().readLine() }
                        val out = it.getOutputStream()
                        out.write(
                            ("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n" +
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                                .encodeToByteArray(),
                        )
                        out.write(body)
                        out.flush()
                    }
                }.apply { isDaemon = true; start() }
            }
        }.apply { isDaemon = true; start() }

        override fun close() {
            runCatching { socket.close() }
            listener.interrupt()
        }
    }

    private fun read(body: ByteArray): ApiResult<FileTextResult> {
        Stub(body).use { stub ->
            val client = EngineClient("http://127.0.0.1:${stub.port}", "Basic dXNlcjp4")
            return runBlocking { FileApi(client).readText("/tmp", "log.txt") }
        }
    }

    @Test
    fun `59-F5 a small file reads whole with its exact size and line count`() {
        val body = "first\nsecond\n".encodeToByteArray()

        val result = read(body)

        assertTrue(result is ApiResult.Success)
        val file = (result as ApiResult.Success).value
        assertEquals("first\nsecond\n", file.text)
        assertEquals(13L, file.totalBytes)
        assertEquals(3, file.totalLines)
        assertFalse(file.truncated)
    }

    @Test
    fun `59-F5 a huge file keeps only the cap but counts the whole body`() {
        // 9 MB of short lines: over the 8 MB cap, so the kept text stops
        // at the cap while the measurements cover all of it.
        val line = "log line\n"
        val body = line.repeat(1_000_000).encodeToByteArray()

        val result = read(body)

        assertTrue(result is ApiResult.Success)
        val file = (result as ApiResult.Success).value
        assertEquals(FILE_READ_CAP_BYTES, file.text.encodeToByteArray().size)
        assertEquals(9_000_000L, file.totalBytes)
        assertEquals(1_000_001, file.totalLines)
        assertTrue(file.truncated)
    }

    @Test
    fun `59-F5 a cap cut through a multi-byte character still decodes`() {
        // "a" up to one byte short of the cap, then "é" (2 bytes): the cap
        // lands between the two bytes of the "é", which must be dropped,
        // not reported as malformed.
        val body = ByteArray(FILE_READ_CAP_BYTES + 3)
        body.fill('a'.code.toByte(), 0, FILE_READ_CAP_BYTES - 1)
        body[FILE_READ_CAP_BYTES - 1] = 0xC3.toByte()
        body[FILE_READ_CAP_BYTES] = 0xA9.toByte()
        body[FILE_READ_CAP_BYTES + 1] = 'b'.code.toByte()
        body[FILE_READ_CAP_BYTES + 2] = '\n'.code.toByte()

        val result = read(body)

        assertTrue(result is ApiResult.Success)
        val file = (result as ApiResult.Success).value
        assertEquals("a".repeat(FILE_READ_CAP_BYTES - 1), file.text)
        assertEquals(FILE_READ_CAP_BYTES + 3L, file.totalBytes)
        assertEquals(2, file.totalLines)
        assertTrue(file.truncated)
    }

    @Test
    fun `59-F5 a NUL byte in the kept prefix is still binary`() {
        val body = ByteArray(100).also { it[50] = 0 }

        val result = read(body)

        assertTrue(result is ApiResult.Failure)
        assertEquals("BinaryFile", (result as ApiResult.Failure).tag)
    }

    @Test
    fun `59-F5 genuinely malformed UTF-8 is still refused`() {
        val body = byteArrayOf('o'.code.toByte(), 'k'.code.toByte(), 0xFF.toByte())

        val result = read(body)

        assertTrue(result is ApiResult.Failure)
        assertEquals("NotUtf8", (result as ApiResult.Failure).tag)
    }

    @Test
    fun `countLines matches String lines for every separator shape`() {
        assertEquals(1, countLines(byteArrayOf()))
        assertEquals(1, countLines("a".encodeToByteArray()))
        assertEquals(2, countLines("a\nb".encodeToByteArray()))
        assertEquals(3, countLines("a\nb\n".encodeToByteArray()))
        assertEquals(2, countLines("a\r\nb".encodeToByteArray()))
        assertEquals(3, countLines("a\r\nb\r\n".encodeToByteArray()))
        assertEquals(2, countLines("a\rb".encodeToByteArray()))
        assertEquals(2, countLines("\n".encodeToByteArray()))
        assertEquals(3, countLines("\n\r".encodeToByteArray()))
        assertEquals("a\nb\n".lines().size, countLines("a\nb\n".encodeToByteArray()))
        assertEquals("a\r\nb\r".lines().size, countLines("a\r\nb\r".encodeToByteArray()))
    }
}
