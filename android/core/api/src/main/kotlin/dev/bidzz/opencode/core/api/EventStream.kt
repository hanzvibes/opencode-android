package dev.bidzz.opencode.core.api

import android.util.Log
import dev.bidzz.opencode.core.api.dto.EngineEvent
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import okhttp3.Request

/**
 * Reads the engine's event stream and exposes it as a [Flow].
 *
 * ## Why this is hand-written
 *
 * The stream is *almost* standard SSE, and the differences matter:
 *
 * - There is no `event:` field. The event kind lives inside the JSON payload
 *   (`{"type":"session.text.delta",...}`), so a client that reads SSE's event name
 *   gets nothing.
 * - Keep-alives arrive as `: heartbeat`, an SSE comment. A parser that treats every
 *   line as data chokes on it.
 *
 * The server also states its own contract, which rules out replay:
 *
 * > *Volatile by contract: a slow consumer overflows and fails the stream, and
 * > events during disconnection are missed.*
 *
 * So `Last-Event-ID` replay is not available. The only correct recovery is to
 * re-read state over REST after reconnecting, which is what [onReconnect] is for.
 * A library that silently reconnects would hide exactly the gap the server warns
 * about.
 */
/** The engine rejected this stream's credentials; retrying cannot help. */
class EventStreamAuthException(val code: Int) : Exception("event stream rejected with HTTP $code")

class EventStream(private val client: EngineClient) {

    companion object {
        private const val TAG = "EventStream"

        /** Backoff bounds for reconnect attempts. */
        private const val INITIAL_BACKOFF_MS = 500L
        private const val MAX_BACKOFF_MS = 15_000L
    }

    /**
     * The SSE call currently blocked in [streamOnce], if any.
     *
     * Audit-200 C16 (M-245): the read loop blocks in `readUtf8Line()`,
     * which coroutine cancellation cannot interrupt — switching
     * sessions used to leave the old socket open until the 90s read
     * timeout, stacking dead connections. [close] cancels this call,
     * which unblocks the read with an IOException at once. Written on
     * the stream's IO dispatcher and read from [close]; the field is
     * volatile because the two run on different threads.
     */
    @Volatile
    private var activeCall: okhttp3.Call? = null

    /**
     * Closes the in-flight SSE connection now, if one is open.
     *
     * Called by [ChatRepository.close] before it cancels the stream
     * job (a session switch), so the old socket does not linger.
     * Cancelling a call that already finished is a no-op.
     */
    fun close() {
        activeCall?.cancel()
    }

    /**
     * Connects and emits events until cancelled.
     *
     * Reconnects with exponential backoff on IO failure. [onReconnect] is invoked
     * before the first emission of each new connection, so a collector can resync
     * from REST and avoid a hole in its state.
     *
     * Emission never throws for a malformed event: a single bad line must not take
     * down a chat screen. Decode failures are logged and skipped.
     */
    fun events(onReconnect: suspend () -> Unit = {}): Flow<EngineEvent> = flow {
        var backoff = INITIAL_BACKOFF_MS
        var firstConnection = true
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                if (!firstConnection) {
                    onReconnect()
                }
                firstConnection = false
                var received = 0
                streamOnce { event ->
                    received++
                    // A connection that delivers events proves health; reset backoff.
                    backoff = INITIAL_BACKOFF_MS
                    emit(event)
                }
                Log.i(TAG, "stream ended after $received events")
            } catch (e: CancellationException) {
                throw e
            } catch (e: EventStreamAuthException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "stream disconnected: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "stream failed", e)
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }.flowOn(Dispatchers.IO)

    /** One connection's lifetime; returns when the server closes the stream. */
    private suspend fun streamOnce(onEvent: suspend (EngineEvent) -> Unit) {
        val request = client.request("/api/event")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .get()
            .build()

        // Audit-200 C16 (M-245): `await()` cancels the call only while
        // it is still connecting — once the response arrives, the loop
        // below blocks in `readUtf8Line()` and coroutine cancellation
        // cannot interrupt it. The completion hook below closes the
        // socket the moment the collector job is cancelled (or [close] is
        // called), so the blocked read fails at once instead of waiting
        // out the 90s read timeout. The hook is disposed when the
        // connection ends normally, so reconnects do not accumulate
        // handlers on the long-lived stream job.
        val call = client.streaming().newCall(request)
        activeCall = call
        val cancellation = currentCoroutineContext().job.invokeOnCompletion {
            runCatching { call.cancel() }
        }
        try {
            call.await().use { response ->
                if (response.code == 401 || response.code == 403) {
                    // A 401 will never fix itself: credentials are fixed per client and change
                    // on every engine restart, so this stream is stale for good.
                    throw EventStreamAuthException(response.code)
                }
                if (!response.isSuccessful) {
                    throw IOException("event stream rejected with HTTP ${response.code}")
                }
                val source = response.body?.source() ?: throw IOException("event stream had no body")
                val parser = SseParser()
                while (true) {
                    val line = source.readUtf8Line() ?: run {
                        // The server closed the stream mid-block: a trailing
                        // event without its closing blank line still counts
                        // (Fase 2, 03-F4). The parser exists for exactly this
                        // case and previously had no production caller.
                        parser.flush()?.let { onEvent(it) }
                        return
                    }
                    val event = parser.accept(line) ?: continue
                    onEvent(event)
                }
            }
        } finally {
            cancellation.dispose()
            if (activeCall === call) activeCall = null
        }
    }
}

/**
 * Incremental SSE parser.
 *
 * Kept separate from the socket so it can be tested without a server. It accepts
 * one line at a time and returns an event when a blank line closes a block.
 *
 * Handled forms:
 * - `data: <json>` — accumulated; multiple `data:` lines are joined with `\n`.
 * - `: comment` — ignored (the engine sends `: heartbeat`).
 * - blank line — dispatches the accumulated data.
 * - `event:` / `id:` / `retry:` — ignored; this server puts everything in `data`.
 */
internal class SseParser {
    private val data = StringBuilder()

    /** Feeds one line. Returns an event when the block is complete, else null. */
    fun accept(line: String): EngineEvent? {
        if (line.isEmpty()) {
            return dispatch()
        }
        if (line.startsWith(":")) {
            return null // comment / keep-alive
        }
        val colon = line.indexOf(':')
        if (colon < 0) return null // field with no value; nothing to accumulate
        val field = line.substring(0, colon)
        val value = line.substring(colon + 1).removePrefix(" ")
        if (field != "data") return null
        if (data.isNotEmpty()) data.append('\n')
        data.append(value)
        return null
    }

    /** Flushes a trailing block if the stream ended without a blank line. */
    fun flush(): EngineEvent? = dispatch()

    private fun dispatch(): EngineEvent? {
        if (data.isEmpty()) return null
        val payload = data.toString()
        data.setLength(0)
        return runCatching {
            ApiJson.decodeFromString<EngineEvent>(payload)
        }.onFailure {
            // One malformed event must not kill the stream. Log the failure,
            // never the payload: the first bytes of an undecodable event are
            // conversation content (user text, agent output, file contents).
            // Audit-200 C13 (M-007).
            Log.w(
                "SseParser",
                "skipping undecodable event (${payload.length} bytes): ${it::class.java.simpleName}",
            )
        }.getOrNull()
    }
}
