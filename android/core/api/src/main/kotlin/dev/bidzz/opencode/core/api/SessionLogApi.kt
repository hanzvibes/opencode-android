package dev.bidzz.opencode.core.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.URLEncoder

/** One durable session event from the log. */
data class SessionLogEvent(
    /** e.g. "session.created", "session.message.content.updated". */
    val type: String,
    /** Aggregate sequence from the durable envelope; null when absent. */
    val seq: Long?,
    /** Wall-clock the event was created, epoch millis; null when absent. */
    val createdMs: Long?,
    /** The event's payload (`data` in the envelope). */
    val data: JsonObject,
)

/** A one-shot log read: the events, plus the watermark the read reached. */
data class SessionLogResult(
    val events: List<SessionLogEvent>,
    /** The `seq` of the `log.synced` marker, or null when the stream ended without one. */
    val syncedSeq: Long?,
)

/**
 * The durable session event log (`GET
 * `/api/experimental/session/{id}/log`, audit-200 C17, M-259).
 *
 * One-shot read (`follow=false`): the engine sends the backlog as SSE
 * `data:` lines, then one `log.synced` marker, then closes. The events
 * are the same durable session events the main stream carries, framed
 * flat — `{id, created, type, durable: {aggregateID, seq, version},
 * data: {...}}` — verified against the engine fork at pin fedc6f20
 * (`packages/protocol/src/groups/session.ts`,
 * `packages/schema/src/session-event.ts`,
 * `packages/schema/src/event-log.ts`).
 *
 * Capped at [MAX_EVENTS]: the log is a diagnostic view, and an old
 * session's full history would swamp the list. Leaving the screen
 * cancels the socket at once (the read loop blocks in `readUtf8Line()`,
 * like [EventStream]).
 */
class SessionLogApi(private val client: EngineClient) {

    suspend fun read(sessionID: String, after: Long? = null): ApiResult<SessionLogResult> {
        val query = buildString {
            append("?follow=false")
            if (after != null) append("&after=").append(after)
        }
        val request = client.request("/api/experimental/session/${encode(sessionID)}/log$query")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .get()
            .build()
        val call = client.streaming().newCall(request)
        val cancellation = currentCoroutineContext().job.invokeOnCompletion {
            runCatching { call.cancel() }
        }
        try {
            call.await().use { response ->
                if (!response.isSuccessful) {
                    return ApiResult.Failure(code = response.code)
                }
                val source = response.body?.source()
                    ?: return ApiResult.Failure(code = 0, message = "log stream had no body")
                val events = ArrayList<SessionLogEvent>()
                var syncedSeq: Long? = null
                val data = StringBuilder()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) {
                        if (data.isNotEmpty()) {
                            parseEvent(data.toString())?.let { (event, seq) ->
                                if (event.type == "log.synced") {
                                    syncedSeq = seq
                                } else if (events.size < MAX_EVENTS) {
                                    events += event
                                }
                            }
                            data.clear()
                        }
                        if (syncedSeq != null) break
                        continue
                    }
                    if (line.startsWith(":")) continue // comment / keep-alive
                    val colon = line.indexOf(':')
                    if (colon < 0) continue
                    if (line.substring(0, colon) == "data") {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.substring(colon + 1).removePrefix(" "))
                    }
                }
                return ApiResult.Success(SessionLogResult(events, syncedSeq))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return ApiResult.Failure(code = 0, message = e.message, cause = e)
        } catch (e: Exception) {
            return ApiResult.Failure(code = -1, message = e.message, cause = e)
        } finally {
            cancellation.dispose()
        }
    }

    /**
     * Parses one accumulated `data:` block into an event plus the
     * `log.synced` marker's seq. A malformed line is skipped, never
     * fatal — one bad event must not hide the rest of the log.
     */
    private fun parseEvent(raw: String): Pair<SessionLogEvent, Long?>? {
        val obj = runCatching {
            ApiJson.parseToJsonElement(raw) as? JsonObject
        }.getOrNull() ?: return null
        val type = (obj["type"] as? JsonPrimitive)?.contentOrNull ?: return null
        val durable = obj["durable"] as? JsonObject
        val seq = (durable?.get("seq") as? JsonPrimitive)?.longOrNull
            ?: (obj["seq"] as? JsonPrimitive)?.longOrNull
        if (type == "log.synced") {
            return SessionLogEvent(type, seq, null, JsonObject(emptyMap())) to seq
        }
        val created = (obj["created"] as? JsonPrimitive)?.longOrNull
        val data = obj["data"] as? JsonObject ?: JsonObject(emptyMap())
        return SessionLogEvent(type, seq, created, data) to null
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        /** Most events shown in one log read (M-259). */
        const val MAX_EVENTS: Int = 500
    }
}
