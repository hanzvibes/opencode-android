package dev.bidzz.opencode.core.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A scheduled prompt (`Schedule.Info`).
 *
 * The engine fires it in its session: once after a delay, at a time,
 * every interval, or on a cron. Shapes verified against the engine fork
 * at pin fedc6f20 (`packages/protocol/src/groups/schedule.ts`,
 * `packages/schema/src/schedule.ts`).
 *
 * `when` and the instants stay raw JSON on purpose: `When` is a tagged
 * union (`{type: "in", in: "20 minutes", tz}` / `{type: "at", at,
 * "2026-10-27T09:00", tz}` / `{type: "every", ...}` / `{type: "cron",
 * ...}`) and the instants are `DateTimeUtcFromMillis` (a number in
 * JSON) — decoding them into typed fields would break on either
 * representation. [whenText] and the screen's instant formatter read
 * them leniently instead.
 */
@Serializable
data class ScheduleInfo(
    val id: String = "",
    val sessionID: String? = null,
    val name: String? = null,
    @SerialName("when") val whenValue: JsonObject = JsonObject(emptyMap()),
    val tz: String = "",
    /** "active" | "paused" | "done". */
    val status: String = "",
    val nextDueAt: JsonElement? = null,
    val endsAt: JsonElement? = null,
    val remainingRuns: Int? = null,
    val runCount: Int = 0,
    val lastFiredAt: JsonElement? = null,
    val lastOutcome: JsonElement? = null,
) {
    /** "in 20 minutes", "at 2026-10-27T09:00", "every 1 hour", "cron 0 9 * * 1-5". */
    fun whenText(): String {
        val type = (whenValue["type"] as? JsonPrimitive)?.contentOrNull ?: return ""
        val value = (whenValue[type] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return if (value.isBlank()) type else "$type $value"
    }

    /** True while the schedule can still fire. */
    fun isActive(): Boolean = status == "active"
}

/** The `when` of a create request: exactly one of `in` / `at` / `every` / `cron`. */
@Serializable
data class ScheduleWhenInput(
    val `in`: String? = null,
    val at: String? = null,
    val every: String? = null,
    val cron: String? = null,
    val tz: String? = null,
)

/** The action of a create request: only a prompt action exists. */
@Serializable
data class ScheduleAction(
    val type: String = "prompt",
    val prompt: String,
)

/** `POST /api/experimental/schedule` body (`Schedule.CreatePayload`). */
@Serializable
data class ScheduleCreateRequest(
    val sessionID: String,
    val name: String? = null,
    @SerialName("when") val whenValue: ScheduleWhenInput? = null,
    val action: ScheduleAction,
)
