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
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Live tool stdout (audit-200 C14, M-195), over a real socket.
 *
 * The engine sends no partial-stdout event of its own: a running bash
 * tool reports its shell id in a `session.tool.progress` event, and the
 * client pages the command's output from `GET /api/shell/{id}/output`
 * while the tool runs. The test server answers the output endpoint with
 * one page ("line1\n") at cursor 0 and an empty page once caught up,
 * like the real endpoint. The tool then succeeds with only the
 * "moved to the background" notice — the live log must survive that
 * success, with the notice appended, instead of being replaced by it.
 */
class ChatRepositoryLiveShellTest {

    @Test
    fun `progress event starts a live shell poll and success keeps the log`() {
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
                        fun respond(status: Int, body: String) {
                            out.write(
                                ("HTTP/1.1 $status OK\r\nContent-Type: application/json\r\n" +
                                    "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                                    .encodeToByteArray(),
                            )
                            out.flush()
                        }
                        when {
                            "/api/event" in requestLine -> {
                                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n".encodeToByteArray())
                                out.flush()
                                eventOut.put(out)
                                while (!socket.isClosed) Thread.sleep(100)
                            }
                            "/api/shell/sh_1/output" in requestLine -> {
                                // One page at cursor 0, empty once caught up.
                                val body = if ("cursor=0" in requestLine) {
                                    """{"data":{"output":"line1\n","cursor":6,"size":6,"truncated":false}}"""
                                } else {
                                    """{"data":{"output":"","cursor":6,"size":6,"truncated":false}}"""
                                }
                                respond(200, body)
                            }
                            else -> respond(200, """{"data":[]}""")
                        }
                    }
                }.apply { isDaemon = true; start() }
            }
        }.apply { isDaemon = true; start() }

        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val client = EngineClient("http://127.0.0.1:${socket.localPort}", "Basic dXNlcjp4")
            val repository = ChatRepository(
                SessionApi(client),
                EventStream(client),
                scope,
                shells = ShellApi(client),
            )
            repository.open("s1")
            val stream = eventOut.poll(20, TimeUnit.SECONDS)
            assertNotNull("event stream never connected", stream)
            fun sendEvent(json: String) {
                stream!!.write(("data: $json\n\n").encodeToByteArray())
                stream.flush()
            }

            sendEvent("""{"type":"session.tool.called","data":{"sessionID":"s1","assistantMessageID":"m1","id":"t1","name":"bash","input":{"command":"./gradlew test"},"executed":true}}""")
            sendEvent("""{"type":"session.tool.progress","data":{"sessionID":"s1","assistantMessageID":"m1","id":"t1","metadata":{"shellID":"sh_1"}}}""")

            val liveOutput = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.liveTools["t1"]?.call?.output != "line1\n") delay(25)
                    repository.state.value.liveTools["t1"]?.call?.output
                }
            }
            assertEquals("line1\n", liveOutput)
            assertEquals("sh_1", repository.state.value.liveTools["t1"]?.call?.shellID)

            sendEvent("""{"type":"session.tool.success","data":{"sessionID":"s1","assistantMessageID":"m1","id":"t1","content":[{"type":"text","text":"Command moved to the background (shell ID: sh_1).\nOutput is streaming to: /tmp/sh_1.out"}],"executed":true}}""")

            val final = runBlocking {
                withTimeoutOrNull(20_000) {
                    while (repository.state.value.liveTools["t1"]?.call?.status != "completed") delay(25)
                    repository.state.value.liveTools["t1"]?.call
                }
            }
            assertNotNull(final)
            assertEquals(
                "line1\n\nCommand moved to the background (shell ID: sh_1).\nOutput is streaming to: /tmp/sh_1.out",
                final!!.output,
            )
        } finally {
            scope.cancel()
            socket.close()
            listener.join(1_000)
        }
    }
}
