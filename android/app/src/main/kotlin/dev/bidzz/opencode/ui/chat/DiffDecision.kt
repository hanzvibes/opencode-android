package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.ui.diff.DiffHunk
import dev.bidzz.opencode.ui.diff.DiffLine

/**
 * Accept / reject for one completed edit, shown inline under its diff
 * (Fase 1, item 2).
 *
 * What the buttons honestly do, and what they do not:
 *
 * - The engine applies an edit the moment the tool runs, and there is no
 *   endpoint that un-applies one finished edit. The doc lists
 *   `POST /api/session/{id}/revert/stage` + `/revert/commit`
 *   (docs/API-REFERENCE.md), but it gives no request-body schema for
 *   either — `Session.Revert` only lists `messageID, partID, snapshot,
 *   files` — and the client has no revert method, so staging a revert
 *   from here would be a guessed request against the live session.
 *   The real gate is the permission prompt, which answers `reject`
 *   *before* the tool runs (`POST
 *   /api/session/{id}/permission/{requestID}/reply`, already wired in
 *   [ChatViewModel.replyPermission]); a finished edit is past it.
 * - `ACCEPTED` is therefore a local acknowledgement only: the change
 *   stays, and the step records that the operator saw and kept it.
 * - `REJECTED` asks the agent to undo it: the view model sends
 *   [revertPromptFor] as an ordinary user message, through the same
 *   send/queue path as a question answer, so the agent reverts the file
 *   in a new turn. It is a request to the agent, not an engine revert —
 *   the status text says "Revert requested", never "Reverted".
 *
 * The decision is per tool call (keyed by call id) and owned by the view
 * model ([ChatViewModel.diffDecisions], audit-200 C2, M-053), so it
 * survives a transcript rebuild; it never touches the file on disk itself.
 */
enum class DiffDecision {
    NONE,
    ACCEPTED,
    REJECTED,
}

/** True when [call] is a finished edit/write whose diff can be judged. */
fun isReviewableEdit(call: ToolCall): Boolean =
    call.status == "completed" && when (call.name.lowercase()) {
        "write", "edit", "patch", "apply_patch", "multiedit" -> true
        else -> false
    }

/**
 * Whether a tool step starts expanded (audit-200 B6, M-198): a running step
 * shows its input while it runs (the engine sends no stdout until the tool
 * finishes, so there is no live output to show — audit-200 C2, M-059), and
 * a finished edit shows its diff plus the Accept/Reject review — a completed
 * edit that arrives already finished (restore, load-earlier) must not hide
 * its review behind a tap, even though the trace shows the step. Any other
 * finished step starts collapsed.
 */
fun toolStepInitialOpen(call: ToolCall): Boolean =
    call.status == "running" || call.status == "streaming" || isReviewableEdit(call)

/**
 * The prompt sent when the operator rejects an edit: asks the agent to
 * undo the change described below, in [relativePath]. The workspace-
 * relative path is what the agent resolves, so the absolute tool path is
 * never sent.
 *
 * The wording is deliberately not relative to time (audit-200 C2,
 * M-055): the old "before your last edit" pointed at whatever edit was
 * newest when the agent finally ran the prompt — a queued reject could
 * undo a NEWER edit that landed after the rejection. This one names the
 * change itself and tells the agent to keep any newer edits.
 *
 * [change] names the edit itself — the hunk header plus the lines it
 * removed and added — so two edits to the same file produce different
 * prompts and the agent can tell them apart (audit-70 07-F2). Without
 * it, two rejects of the same file are word-identical: the agent cannot
 * know which edit is meant, and the queue dedup drops the second one
 * while the UI claims "Revert requested" for both.
 */
fun revertPromptFor(relativePath: String, change: String? = null): String {
    val base = "Please revert $relativePath to undo the change described below. " +
        "If newer edits exist on this file, keep them and only undo this one."
    if (change.isNullOrBlank()) return base
    return "$base The change to revert is:\n$change"
}

/**
 * A compact fingerprint of one edit for the revert prompt: the first hunk
 * header (its line numbers already separate most edits to one file) plus
 * up to three removed and three added lines. Null when the hunks carry no
 * changed lines — then the path alone is all there is to send.
 */
fun revertChangeDescription(hunks: List<DiffHunk>): String? {
    val header = hunks.firstOrNull()?.header?.takeIf { it.isNotBlank() } ?: return null
    val lines = hunks.flatMap { it.lines }
    val removed = lines.filter { it.kind == DiffLine.Kind.REMOVED }.map { it.text }.take(3)
    val added = lines.filter { it.kind == DiffLine.Kind.ADDED }.map { it.text }.take(3)
    if (removed.isEmpty() && added.isEmpty()) return null
    val excerpt = buildList {
        add(header)
        removed.forEach { add("- ${it.take(80)}") }
        added.forEach { add("+ ${it.take(80)}") }
    }.joinToString("\n")
    return excerpt.take(500)
}
