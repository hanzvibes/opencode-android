package dev.bidzz.opencode.core.api

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Query encoding for the filesystem endpoints.
 *
 * Every path here is user-visible data — a project folder or file name with a space is
 * ordinary — and the engine reads query values as percent-encoded, so a form-encoded
 * `+` arrives as a literal plus and the file simply does not exist.
 */
class FsQueryEncodingTest {

    private val api = FileApi(EngineClient("http://127.0.0.1:1", "Basic dGVzdA=="))

    @Test
    fun `a path with a space reaches the engine as percent-encoding`() {
        val url = api.writeUrl("/data/user/0/dev.bidzz.opencode/files/workspace", "with space.md")
        assertTrue(url, url.contains("path=with%20space.md"))
        assertFalse(url, url.contains("+"))
    }

    @Test
    fun `a directory with a space is encoded the same way`() {
        val param = api.locationParam("/storage/emulated/0/Projects/my app")
        assertTrue(param, param.contains("Projects%2Fmy%20app"))
        assertFalse(param, param.contains("+"))
    }

    @Test
    fun `the location parameter keeps its bracketed name`() {
        // `style: deepObject` in the engine's schema: the parameter is literally
        // `location[directory]`, and the brackets must arrive encoded.
        assertTrue(api.locationParam("/data/x").startsWith("location%5Bdirectory%5D="))
    }

    @Test
    fun `an ordinary path is unchanged apart from the encoded slashes`() {
        // Control for the two tests above: if nothing were encoded at all, the space
        // assertions would pass by accident of a broken encoder.
        assertEquals(
            "/api/experimental/fs/write?location%5Bdirectory%5D=%2Fdata%2Fx&path=main.py",
            api.writeUrl("/data/x", "main.py"),
        )
    }

    /** Serves a canned branch list and records every request line. */
    private class Stub(private val body: String = """{"data":["fix login","other"]}""") : AutoCloseable {
        private val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val port: Int get() = socket.localPort
        val requests = ConcurrentLinkedQueue<String>()
        private val listener = Thread {
            while (true) {
                val connection = try { socket.accept() } catch (e: IOException) { break }
                Thread {
                    connection.use {
                        val requestLine = runCatching { it.getInputStream().bufferedReader().readLine() }.getOrNull().orEmpty()
                        requests += requestLine
                        val bodyBytes = body.encodeToByteArray()
                        val out = it.getOutputStream()
                        out.write(
                            ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                "Content-Length: ${bodyBytes.size}\r\nConnection: close\r\n\r\n")
                                .encodeToByteArray(),
                        )
                        out.write(bodyBytes)
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

    @Test
    fun `a branch search with a space reaches the engine as percent-encoding`() {
        // Audit-200 C12 (M-175): the search used to go out with the space
        // as `+`, which the engine reads as a literal plus — searching
        // "fix login" always answered an empty list.
        Stub().use { stub ->
            val vcs = VcsApi(EngineClient("http://127.0.0.1:${stub.port}", "Basic dXNlcjp4"))
            val result = runBlocking { vcs.branches("/data/x", search = "fix login") }

            assertTrue(result is ApiResult.Success)
            assertEquals(listOf("fix login", "other"), (result as ApiResult.Success).value)
            val request = stub.requests.single()
            assertTrue(request, request.contains("search=fix%20login"))
            assertFalse(request, request.contains("fix+login"))
        }
    }

    @Test
    fun `a worktree list for a project with a space reaches the engine as percent-encoding`() {
        // Audit-200 C16 (M-024): the project id went out form-encoded, so
        // a project in "my app" asked for a project literally called
        // "my+app" and always answered an empty list.
        Stub(body = """[{"directory":"/tmp/wt1"}]""").use { stub ->
            val worktrees = WorktreeApi(EngineClient("http://127.0.0.1:${stub.port}", "Basic dXNlcjp4"))
            val result = runBlocking { worktrees.list("/storage/emulated/0/Projects/my app") }

            assertTrue(result is ApiResult.Success)
            val request = stub.requests.single()
            assertTrue(request, request.contains("projectID=%2Fstorage%2Femulated%2F0%2FProjects%2Fmy%20app"))
            assertFalse(request, request.contains("my+app"))
        }
    }
}
