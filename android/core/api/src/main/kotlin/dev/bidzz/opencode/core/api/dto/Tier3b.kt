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

/**
 * Usage limits (`GET /api/experimental/usage/limits`).
 *
 * Verified against the engine fork source at pin 92b2ca0
 * (`packages/protocol/src/groups/usage.ts` and
 * `packages/schema/src/usage-limits.ts`): the endpoint answers
 * `Location.response(NullOr(UsageLimits.Info))`, i.e.
 * `{location: {directory}, data: UsageLimits.Info | null}` — `data` is
 * **null when the location is not signed in to an OpenCode Console
 * workspace**, and that null is a normal answer, not an error. The
 * `location` query parameter (`LocationQuery`, all fields optional) is
 * not sent, so the engine reports its default location. `go` is
 * optional (a workspace may subscribe to no Go allowance); `percent`
 * is 0–100; `resetsAt` is millis since the epoch. The endpoint is
 * experimental, so every nested field is optional-or-defaulted: a
 * shape change must degrade to an empty row, never a failed decode.
 */
@Serializable
data class UsageLimitsResponse(
    val data: UsageLimitsInfo? = null,
    val location: LocationRef? = null,
)

@Serializable
data class UsageLimitsInfo(
    val workspace: UsageWorkspace? = null,
    val go: UsageGo? = null,
    val budgets: List<UsageBudget> = emptyList(),
)

@Serializable
data class UsageWorkspace(
    val id: String = "",
    val name: String? = null,
    val usageURL: String = "",
)

@Serializable
data class UsageGo(
    val rolling: UsageMeter? = null,
    val weekly: UsageMeter? = null,
    val monthly: UsageMeter? = null,
)

@Serializable
data class UsageMeter(
    val percent: Double = 0.0,
    val limited: Boolean = false,
    val resetsAt: Long? = null,
)

@Serializable
data class UsageBudget(
    val id: String = "",
    val limit: Double = 0.0,
    val spent: Double = 0.0,
    val scope: String = "",
    val per: String = "",
    val coverage: String = "",
    val models: List<UsageBudgetModel> = emptyList(),
    val vendors: List<String> = emptyList(),
    val inputPriceAtLeast: Double? = null,
    val picked: Boolean = false,
    val shared: Boolean = false,
    val resetsAt: Long? = null,
)

@Serializable
data class UsageBudgetModel(
    val providerID: String = "",
    val modelID: String = "",
    val name: String = "",
    val spent: Double? = null,
)

/**
 * Per-session instruction entries
 * (`/api/experimental/session/{id}/instructions/entries`).
 *
 * Verified against the engine fork source at pin 92b2ca0
 * (`packages/protocol/src/groups/session.ts` — the
 * `session.instructions.entry.*` endpoints — and
 * `packages/schema/src/instruction-entry.ts`): list answers
 * `{data: InstructionEntry.Info[]}`; put takes `{ value: Json }` at
 * `.../entries/{key}` and answers 204; delete answers 204. A key
 * matches `^[a-z0-9][a-z0-9._-]*$` (the server derives the namespaced
 * key `api/<key>`). `value` is arbitrary JSON — the app sends the
 * user's text as a JSON string and reads a string value back as text,
 * anything else as compact JSON. Put fails with 413
 * (`InstructionEntryValueTooLargeError`, max 256 KB) when the value is
 * too large; the UI words that as a friendly message instead of
 * showing the engine's error body.
 */
@Serializable
data class InstructionEntriesResponse(
    val data: List<InstructionEntry> = emptyList(),
)

@Serializable
data class InstructionEntry(
    val key: String = "",
    val value: JsonElement? = null,
)

@Serializable
data class PutInstructionRequest(
    val value: JsonElement,
)
