package dev.bidzz.opencode.core.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * DTOs for session fork / compact / revert (engine-to-UI, mockup group 2).
 *
 * Every shape below was verified against the engine fork source at pin
 * 92b2ca0dd727942e00aba3523ed141d53e78ea28 — not just the API reference,
 * which lists property names but no request shapes or envelopes:
 * - `packages/server/src/handlers/session.ts` — the `session.fork`,
 *   `session.compact`, `session.revert.stage/clear/commit` handlers
 * - `packages/core/src/session.ts` — `ForkInput`, `CompactInput`,
 *   `session.compact` returning `SessionInbox.Compaction`
 * - `packages/core/src/session/revert.ts` — `stage({ session,
 *   messageID, files?: boolean })`
 * - `packages/schema/src/session-fork.ts` — `Boundary` union
 * - `packages/schema/src/session-revert.ts` — `Revert`
 * - `packages/schema/src/session-inbox.ts` — `Delivery` literals
 *   (`steer | queue`), `CompactionPayload` (`{}`)
 * - `packages/schema/src/session-message.ts` — message IDs are
 *   `"msg_" + Identifier.ascending()` (see [newMessageId])
 * - `packages/schema/src/session.ts` — `Session.Info.fork` is
 *   `{ sessionID, boundary: ForkBoundary }`, `revert` is `Revert`
 *
 * Fork semantics (from `packages/core/src/session.ts`): `before` is the
 * message the fork stops *before* — the forked session contains
 * everything up to (excluding) that message; without `before` it forks
 * the whole session through the latest message. Forking an empty session
 * fails with `ForkEmptyError`, surfaced as `InvalidRequestError` with
 * kind `empty_session`. The fork inherits the parent's instructions and
 * instruction entries, and answers `{ data: SessionInfo }` for the new
 * session.
 *
 * Compact semantics: the client supplies the inbox-item `id` (a fresh
 * message ID) and a `delivery` (`queue` waits behind the current run,
 * `steer` interrupts it). The call answers
 * `{ data: SessionInbox.Compaction }` — an [InboxItem] of type
 * `compaction` — whose message shows the outcome; the transcript itself
 * is replaced by the summary afterwards, so callers must reload the
 * messages. A conflicting `id` (already a durable record) fails with
 * 409 `CompactionConflictError`.
 *
 * Revert semantics: `stage` snapshots the workspace, plans the revert
 * to just before [RevertStageRequest.messageID] and answers
 * `{ data: Session.Revert }`; the staged revert is visible on the
 * session (`SessionInfo.revert`) until `commit` applies it or `clear`
 * drops it. `files = true` includes the file changes in the plan;
 * staging while the session is busy fails with `Session.BusyError`.
 */

/** Body of `POST /api/session/{id}/fork`. Null `before` is omitted. */
@Serializable
data class ForkRequest(
    val before: String? = null,
)

/** Body of `POST /api/session/{id}/compact`. Both fields are sent. */
@Serializable
data class CompactRequest(
    val id: String,
    val delivery: String,
)

/** Body of `POST /api/session/{id}/revert/stage`. */
@Serializable
data class RevertStageRequest(
    val messageID: String,
    val files: Boolean,
)

/**
 * A staged session revert (`Session.Revert`).
 *
 * `files` is the list of file diffs the revert would apply; it stays a
 * list of raw objects — the file-diff shape is read leniently through
 * [revertFilePaths] — so a newer diff field cannot break the decode.
 */
@Serializable
data class SessionRevert(
    val messageID: String = "",
    val partID: String? = null,
    val snapshot: String? = null,
    val files: List<JsonObject> = emptyList(),
)

/**
 * The path one revert file entry names, if any.
 *
 * Lenient on purpose (audit-200 A9-6, DL-013): the staged-revert
 * entries were verified with a `"path"` key (see the DTO comment), but
 * the engine's own diff shape (`FileDiff.Info`, used by the session
 * diff and the VCS diff) names the same file under `"file"` — and
 * either key may hold the path directly or inside a nested object. An
 * entry that names no path at all yields null instead of breaking the
 * decode.
 */
internal fun revertFilePath(entry: JsonObject): String? {
    for (key in listOf("path", "file")) {
        when (val value = entry[key]) {
            is JsonPrimitive -> if (value.isString) return value.content
            is JsonObject -> {
                val nested = revertFilePath(value)
                if (nested != null) return nested
            }
            else -> Unit
        }
    }
    return null
}

/** Workspace-relative paths a staged revert would touch, if any. */
fun SessionRevert.revertFilePaths(): List<String> =
    files.mapNotNull { revertFilePath(it) }

/**
 * What the fork banner can honestly say about the files a staged revert
 * would touch (audit-200 A9-6, DL-013). [KnownFiles] carries the count
 * through [SessionRevert.revertFilePaths]; [UnknownFiles] is the case
 * the old banner got wrong — [SessionRevert.files] is non-empty but no
 * path could be read from any entry, so the banner must NOT claim "No
 * files change"; [MessagesOnly] is a genuinely file-free revert.
 */
enum class RevertFileImpact { KnownFiles, UnknownFiles, MessagesOnly }

/** The banner's file impact for this staged revert. */
fun SessionRevert.fileImpact(): RevertFileImpact = when {
    revertFilePaths().isNotEmpty() -> RevertFileImpact.KnownFiles
    files.isNotEmpty() -> RevertFileImpact.UnknownFiles
    else -> RevertFileImpact.MessagesOnly
}

/** The fork boundary of a forked session (`Session.ForkBoundary`). */
@Serializable
data class ForkBoundary(
    val type: String = "",
    val messageID: String = "",
)

/** Where a session was forked from (`Session.Info.fork`). */
@Serializable
data class ForkInfo(
    val sessionID: String = "",
    val boundary: ForkBoundary = ForkBoundary(),
)
