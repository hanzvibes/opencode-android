package dev.bidzz.opencode.core.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * DTOs for Tier 3 items 6-10 (engine-to-UI, second batch).
 *
 * Every shape below was verified against the engine fork source at pin
 * 92b2ca0 (`packages/protocol/src/groups/session.ts`,
 * `packages/protocol/src/groups/usage.ts`,
 * `packages/schema/src/session-inbox.ts`,
 * `packages/schema/src/session-stats.ts`,
 * `packages/schema/src/instruction-entry.ts`,
 * `packages/schema/src/usage-limits.ts`), not just the API reference —
 * the reference lists property names but no request shapes or envelopes.
 * Where the engine answers a tagged union (`anyOf` in the reference),
 * the app decodes the common fields and keeps the variant payload as a
 * [JsonObject], so a new variant is shown generically instead of crashing
 * the decode.
 */

/**
 * One queued inbox item (`GET /api/session/{id}/inbox`).
 *
 * Verified against the fork source: the endpoint answers
 * `{data: Session.Inbox.Info[]}`, a tagged union on `type` with the four
 * variants `user | synthetic | compaction | move`. Every variant carries
 * `id`, `sessionID`, `time.created` (millis) and `delivery`
 * (`steer | queue`); the payload differs per variant, so it stays a
 * [JsonObject] and is read through `inboxSummary` in the UI layer:
 * - user: `Prompt` fields — `text`, optional `files`, `agents`, `skills`
 * - synthetic: `{ text, description?, metadata? }`
 * - compaction: `{}` — there is nothing to summarise
 * - move (public form): `{ projectID, subpath?, location: { directory } }`
 *   — the internal `location` (with `workspaceID`) is replaced by the
 *   public ref, so only `directory` is ever visible.
 *
 * The `experimental-media-uri` variant (`MediaUser`, files kept on the
 * server) is NOT opted into: the app never sends that capability, so the
 * plain `User` variant is what arrives.
 */
@Serializable
data class InboxItem(
    val id: String,
    val sessionID: String = "",
    val time: InboxTime? = null,
    val type: String = "",
    val delivery: String? = null,
    val payload: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class InboxTime(
    val created: Long? = null,
)

@Serializable
data class InboxResponse(
    val data: List<InboxItem> = emptyList(),
    val location: LocationRef? = null,
)

/**
 * Body of `POST /api/session/{id}/move`.
 *
 * Verified against the engine fork source at pin 92b2ca0
 * (`packages/protocol/src/groups/session.ts` — the `session.move`
 * endpoint — and `packages/server/src/handlers/session.ts`): the payload
 * is `Location.PublicRef` plus an optional `delivery`, i.e. just
 * `{ directory, delivery? }` — NOT `{ projectID, subpath?, location? }`
 * (that shape is the move *inbox item* payload, not the request). The
 * handler moves by directory and fails with `InvalidRequestError` when
 * the directory does not exist or is not a directory. The endpoint
 * answers **204 No Content**. `delivery` is left unset, so the engine
 * uses its default boundary.
 */
@Serializable
data class MoveSessionRequest(
    val directory: String,
    val delivery: String? = null,
)

/**
 * Aggregate session statistics (`GET /api/experimental/session/stats`).
 *
 * Verified against the engine fork source at pin 92b2ca0
 * (`packages/protocol/src/groups/session.ts` — the `session.stats`
 * endpoint — and `packages/schema/src/session-stats.ts`): the endpoint
 * answers `{data: SessionStats.Info}`. `tools` is a union on `mode`
 * (`none | summary { totals } | detail { totals, usage }`), so it is
 * decoded as one struct with optional fields. `range` is millis since
 * the epoch; `cost` is `Money.USD`, a plain number. The endpoint takes
 * optional `from`/`to`/`project`/`timezone`/`tools` query parameters;
 * the app calls it with none, so the engine reports its default range.
 * `model` in [StatsModelUsage] is a `Model.Ref` (`{ providerID,
 * modelID }`); it stays a [JsonElement] and is read through
 * `statsModelLabel`, because the app's own `ModelRef` shape differs.
 */
@Serializable
data class SessionStatsResponse(
    val data: SessionStatsInfo,
)

@Serializable
data class SessionStatsInfo(
    val range: StatsRange? = null,
    val sessions: Long = 0,
    val subagents: Long = 0,
    val prompts: Long = 0,
    val steps: Long = 0,
    val tokens: TokenUsage? = null,
    val cost: Double = 0.0,
    val tools: StatsTools = StatsTools(),
    val activeDays: Long = 0,
    val streak: Long = 0,
    val activity: List<StatsActivity> = emptyList(),
    val models: List<StatsModelUsage> = emptyList(),
)

@Serializable
data class StatsRange(
    val from: Long? = null,
    val to: Long? = null,
)

@Serializable
data class StatsTools(
    val mode: String = "none",
    val totals: StatsToolTotals? = null,
    val usage: List<StatsToolUsage> = emptyList(),
)

@Serializable
data class StatsToolTotals(
    val calls: Long = 0,
    val succeeded: Long = 0,
    val failed: Long = 0,
    val unfinished: Long = 0,
)

@Serializable
data class StatsToolUsage(
    val name: String = "",
    val calls: Long = 0,
    val succeeded: Long = 0,
    val failed: Long = 0,
    val unfinished: Long = 0,
    val durationP50: Double? = null,
)

@Serializable
data class StatsActivity(
    val date: String = "",
    val steps: Long = 0,
)

@Serializable
data class StatsModelUsage(
    val model: JsonElement? = null,
    val steps: Long = 0,
    val tokens: TokenUsage? = null,
    val cost: Double = 0.0,
)

/**
 * A complete projected session transcript
 * (`GET /api/experimental/session/{id}/export`).
 *
 * Verified against the engine fork source at pin 92b2ca0
 * (`packages/protocol/src/groups/session.ts` — the `session.export`
 * endpoint): the endpoint answers `{data: SessionTransfer.Data}`, i.e.
 * `{ info, messages }`, with an optional `?sanitize=true` query the app
 * does not send. `info` is the public session info and `messages` the
 * projected transcript; both stay [JsonObject]s — the export is shown
 * and copied as JSON, never re-interpreted as chat state — so an older
 * or newer transcript shape cannot break the decode.
 */
@Serializable
data class SessionTransferResponse(
    val data: SessionTransferData,
)

@Serializable
data class SessionTransferData(
    val info: JsonObject = JsonObject(emptyMap()),
    val messages: List<JsonObject> = emptyList(),
)
