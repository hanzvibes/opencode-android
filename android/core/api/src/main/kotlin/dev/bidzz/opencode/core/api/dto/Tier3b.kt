package dev.bidzz.opencode.core.api.dto

import kotlinx.serialization.Serializable
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
