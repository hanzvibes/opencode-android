package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.ScheduleAction
import dev.bidzz.opencode.core.api.dto.ScheduleCreateRequest
import dev.bidzz.opencode.core.api.dto.ScheduleInfo
import dev.bidzz.opencode.core.api.dto.ScheduleWhenInput
import kotlinx.serialization.encodeToString
import java.net.URLEncoder

/**
 * Scheduled prompts (`/api/experimental/schedule`).
 *
 * A schedule wakes its session with a prompt: once after a delay, at a
 * time, every interval, or on a cron. The list is per session — the
 * endpoint requires a `sessionID` query — so the screen opens from a
 * session, not from Settings. Shapes verified against the engine fork
 * at pin fedc6f20 (`packages/protocol/src/groups/schedule.ts`,
 * `packages/schema/src/schedule.ts`):
 *
 * - Create answers `{data: Schedule.Info}`; pause/resume answer the
 *   same. The app ignores the returned info and re-lists instead, so
 *   all three are [ApiResult.Unit].
 * - Delete answers 204 No Content.
 * - `when` is optional on create (defaults to every 30 seconds with an
 *   `until`); the app always sends one — a schedule with no `when` and
 *   no `until` would fire on a default the user never picked.
 */
class ScheduleApi(private val client: EngineClient) {

    /** Lists one session's schedules, oldest first. */
    suspend fun list(sessionID: String): ApiResult<List<ScheduleInfo>> {
        val request = client.request("/api/experimental/schedule?sessionID=${encode(sessionID)}")
            .get()
            .build()
        return client.execute(request) { raw ->
            client.decodeDataList<ScheduleInfo>(raw)
        }
    }

    /** Creates a schedule that wakes [sessionID] with [prompt]. */
    suspend fun create(
        sessionID: String,
        name: String?,
        whenValue: ScheduleWhenInput,
        prompt: String,
    ): ApiResult<Unit> {
        val body = ApiJson.encodeToString(
            ScheduleCreateRequest(
                sessionID = sessionID,
                name = name?.takeIf { it.isNotBlank() },
                whenValue = whenValue,
                action = ScheduleAction(prompt = prompt),
            ),
        )
        val request = client.request("/api/experimental/schedule")
            .post(client.jsonBody(body))
            .build()
        return client.executeUnit(request)
    }

    /** Stops a schedule from firing until it is resumed. */
    suspend fun pause(scheduleID: String): ApiResult<Unit> {
        val request = client.request("/api/experimental/schedule/${encode(scheduleID)}/pause")
            .post(client.emptyBody())
            .build()
        return client.executeUnit(request)
    }

    /**
     * Resumes a paused schedule. A one-shot that fell due while paused
     * fires once; a repeating schedule resumes at its next slot.
     */
    suspend fun resume(scheduleID: String): ApiResult<Unit> {
        val request = client.request("/api/experimental/schedule/${encode(scheduleID)}/resume")
            .post(client.emptyBody())
            .build()
        return client.executeUnit(request)
    }

    /** Deletes a schedule. */
    suspend fun delete(scheduleID: String): ApiResult<Unit> {
        val request = client.request("/api/experimental/schedule/${encode(scheduleID)}")
            .delete()
            .build()
        return client.executeUnit(request)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
