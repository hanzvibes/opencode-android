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
class EventStream(private val client: EngineClient) {

    companion object {
        private const val TAG = "EventStream"

        /** Backoff bounds for reconnect attempts. */
        private const val INITIAL_BACKOFF_MS = 500L
        private const val MAX_BACKOFF_MS = 15_000L
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

        client.streaming().newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                // Surface auth failures instead of reconnecting forever: a 401 will
                // never fix itself, and the caller needs to know the password moved.
                throw IOException("event stream rejected with HTTP ${response.code}")
            }
            val source = response.body?.source() ?: throw IOException("event stream had no body")
            val parser = SseParser()
            while (true) {
                val line = source.readUtf8Line() ?: return
                val event = parser.accept(line) ?: continue
                onEvent(event)
            }
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
            // One malformed event must not kill the stream.
            Log.w("SseParser", "skipping undecodable event: ${payload.take(120)}")
        }.getOrNull()
    }
}
