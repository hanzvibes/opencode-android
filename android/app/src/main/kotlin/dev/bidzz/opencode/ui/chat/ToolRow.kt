package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.ui.setClipboardText

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.core.design.OpenCodeColors
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.PrimaryButton
import dev.bidzz.opencode.ui.SelectorChip
import dev.bidzz.opencode.ui.pressScale
import dev.bidzz.opencode.ui.diff.DiffHunk
import dev.bidzz.opencode.ui.diff.DiffLine
import dev.bidzz.opencode.ui.diff.DiffParser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The tool trace for one message, presented as a high-tech terminal console execution.
 *
 * Designed to evoke terminal-grade CLI environments like Claude Code / Antigravity:
 * - Compact console pill/card with dark console styling
 * - Live pulse indicator when a command is running
 * - Clean status badges with execution summaries and line counts
 * - Formatted terminal console output with `$ ` prompt prefix and syntax coloring
 */
@Composable
fun ToolTrace(
    tools: List<ToolCall>,
    streaming: Boolean,
    modifier: Modifier = Modifier,
    /** Engine `time.created` of the turn this trace belongs to (C-C).
     * The engine records no per-tool timestamps, so the trace shows when
     * its turn started — a real time, never a faked per-step one. */
    startedAtMillis: Long? = null,
    /** Per-tool durations in milliseconds, keyed by tool id, when the
     * caller knows them. Empty until the integration agent fills it from
     * per-tool startedAt/completedAt timestamps (the engine sends none
     * today, so no duration is invented here). */
    toolDurations: Map<String, Long> = emptyMap(),
    /** Workspace path the tool file paths are resolved against (Fase 1,
     * item 3): a tap opens the file only when its path resolves inside
     * it; without one, absolute paths stay copy-only. */
    workspacePath: String? = null,
    /** Opens a workspace-relative file in the file viewer (Fase 1,
     * item 3). */
    onOpenToolFile: (String) -> Unit = {},
    /** Asks the agent to revert a rejected edit (Fase 1, item 2): the
     * view model sends the revert request as an ordinary user message.
     * Receives the call id, so the view model can record the decision
     * against the step that asked (audit-200 C2, M-052/M-053). */
    onRequestDiffRevert: (String, String, String?) -> Unit = { _, _, _ -> },
    /** Accept / Reject decisions per tool call id, owned by the view
     * model (audit-200 C2, M-053) — the step reads its own entry here
     * instead of keeping it in the composition, so a transcript rebuild
     * cannot resurrect the buttons. */
    diffDecisions: Map<String, DiffDecision> = emptyMap(),
    /** Records an Accept / Reject decision for one tool call id. */
    onDiffDecision: (String, DiffDecision) -> Unit = { _, _ -> },
    /** Per-tool start stamps in epoch millis, keyed by tool id (Fase 2,
     * item 5): the same client-side stamps the durations come from, so a
     * still-running step can show a live elapsed time instead of no
     * duration at all. A tool the client never saw live has no entry. */
    toolStartedAtMillis: Map<String, Long> = emptyMap(),
    /** #6f: called before any height-changing toggle in this trace
     * (header expand/collapse, step expand, show-full output/diff),
     * so the caller can anchor the scroll position first. */
    onBeforeHeightChange: () -> Unit = {},
) {
    if (tools.isEmpty()) return

    val colors = OpenCodeTheme.colors
    val motion = OpenCodeTheme.tokens.motion
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val shape = RoundedCornerShape(radius.md)

    val running = tools.filter { it.status == "running" || it.status == "streaming" }
    val failed = tools.count { it.status == "error" }
    val completed = tools.count { it.status == "completed" }
    val lastCompleted = tools.lastOrNull { it.status == "completed" }

    // Opsi A: the trace defaults to its one-line summary. Open while the
    // run streams (live progress) or when it failed (an error is never
    // hidden behind the summary); a finished clean run starts collapsed.
    var open by rememberSaveable { mutableStateOf(toolTraceInitialOpen(streaming, failed > 0)) }
    // True once the reader taps the header open by hand: auto-collapse
    // below must never close a trace the reader opened on purpose.
    var userExpanded by rememberSaveable { mutableStateOf(false) }

    // Animation C: shared press feedback on the summary toggle header.
    val headerPressSource = remember { MutableInteractionSource() }

    // One glyph rotating 0° (right, collapsed) ↔ 90° (down, open)
    // instead of two glyphs swapping on every tap.
    val headerChevronRotation by animateFloatAsState(
        targetValue = if (open) 90f else 0f,
        animationSpec = motion.tweenQuick(),
        label = "tool_trace_chevron_rotation",
    )

    // Timeline filter, kept in rememberSaveable so it survives collapse,
    // rotation and recomposition: collapsing the trace never resets it.
    // Only these four kinds exist — no more are added here.
    // Audit-200 A2: the enum itself is stored by NAME, not by value —
    // rememberSaveable has no built-in enum saver, and saving the enum
    // directly crashes on onSaveInstanceState (same class as C-006).
    // See diffDecisionName below for the same pattern.
    var filterName by rememberSaveable { mutableStateOf(ToolFilter.ALL.name) }
    val filter = toolFilterForName(filterName)

    // When a run finishes with a failure, open it so an error is never
    // hidden behind the collapsed summary. When a clean run finishes,
    // collapse it back to the one-line summary — unless the reader
    // expanded it by hand mid-stream, in which case it stays open.
    // #6f: anchor first, but only when the value actually flips.
    LaunchedEffect(streaming, failed) {
        if (!streaming && failed > 0) {
            if (!open) {
                onBeforeHeightChange()
                open = true
            }
        } else if (shouldAutoCollapseToolTrace(userExpanded, streaming, failed > 0)) {
            if (open) {
                onBeforeHeightChange()
                open = false
            }
        }
    }

    // Collapsed while streaming still shows the most recently finished tool,
    // so the timeline reads as progress (done → running) instead of only
    // showing what is still running or has failed.
    val visible = if (open) tools else (
        running + tools.filter { it.status == "error" } + listOfNotNull(lastCompleted)
        ).distinctBy { it.id }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceSunken)
            .border(
                width = 0.5.dp,
                color = if (running.isNotEmpty()) colors.activity.copy(alpha = 0.4f) else colors.hairline,
                shape = shape,
            ),
    ) {
        // Summary toggle header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(motion, headerPressSource)
                .clickable(
                    interactionSource = headerPressSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                ) {
                    // #6f: anchor the scroll position before the
                    // height toggle, so the list does not jump.
                    onBeforeHeightChange()
                    open = !open
                    if (open) userExpanded = true
                }
                .padding(
                    start = spacing.sm,
                    end = spacing.sm,
                    top = spacing.sm,
                    bottom = if (visible.isNotEmpty()) spacing.xs else spacing.sm,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = Glyph.ChevronRight,
                    tint = colors.onSurfaceFaint,
                    size = 12.dp,
                    modifier = Modifier.graphicsLayer { rotationZ = headerChevronRotation },
                )
            }

            GlyphIcon(
                glyph = Glyph.Terminal,
                tint = if (running.isNotEmpty()) colors.activity else colors.onSurfaceMuted,
                size = 13.dp,
                modifier = Modifier.padding(start = spacing.xxs),
            )

            val summaryText = if (running.isNotEmpty()) {
                val currentTool = running.firstOrNull()
                val toolName = currentTool?.name?.takeIf { it.isNotBlank() }
                // Progress as finished/total, so a long run reads as
                // "Running Read… · 3/5" instead of an open-ended "Running…".
                if (toolName != null) {
                    stringResource(
                        R.string.tool_running_named,
                        toolName,
                        completed,
                        tools.size,
                    )
                } else {
                    stringResource(
                        if (tools.size == 1) R.string.tool_steps_one else R.string.tool_steps_other,
                        tools.size,
                    ) + " · $completed/${tools.size}"
                }
            } else {
                stringResource(
                    R.string.tool_ran,
                    stringResource(
                        if (tools.size == 1) R.string.tool_steps_one else R.string.tool_steps_other,
                        tools.size,
                    ),
                )
            }

            Text(
                text = summaryText,
                style = OpenCodeTypography.label.copy(
                    fontFamily = OpenCodeTypography.mono,
                    fontSize = 12.sp,
                    fontWeight = if (running.isNotEmpty()) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (running.isNotEmpty()) colors.toolRunning else colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.xs),
            )

            // Turn start time (C-C): "Ran 3 steps · 14:32". Shown once
            // here, not per step — per-tool timestamps do not exist in
            // the model, and inventing them would be a lie.
            if (startedAtMillis != null) {
                Text(
                    text = "· ${formatClock(startedAtMillis)}",
                    style = OpenCodeTypography.caption.copy(
                        fontFamily = OpenCodeTypography.mono,
                        fontSize = 11.sp,
                    ),
                    color = colors.onSurfaceFaint,
                    maxLines = 1,
                    modifier = Modifier.padding(start = spacing.xs),
                )
            }

            Spacer(Modifier.weight(1f))

            if (failed > 0) {
                StatusChip(
                    text = stringResource(R.string.tool_failed, failed).removePrefix("· ").trim(),
                    color = colors.toolError,
                )
            }
            if (running.isNotEmpty()) {
                if (failed > 0) Spacer(Modifier.width(spacing.xs))
                // Running reads the single live colour (activity = accent)
                // everywhere: the step badge already is, but this header
                // chip used to be orange (toolWarning), the system's
                // warning/error colour.
                StatusChip(
                    text = stringResource(R.string.tool_working).removePrefix("· ").trim(),
                    color = colors.toolRunning,
                    isPulsing = true,
                )
            } else if (failed == 0 && tools.isNotEmpty() && !streaming) {
                StatusChip(
                    text = stringResource(R.string.tool_status_success),
                    color = colors.toolSuccess,
                )
            }
        }

        // Collapsed summary: the last finished tool plus succeeded/failed counts,
        // so a finished run still reads at a glance without expanding it.
        // Kept to one ellipsised line so it cannot overflow on narrow screens.
        if (!open && !streaming && tools.isNotEmpty()) {
            // `completed` / `lastCompleted` are computed once above and
            // reused here.
            // Composable calls (stringResource / StepLabel.text) must stay in
            // composable scope, not inside buildList / let lambdas, so each
            // one is resolved here with a plain if-expression first.
            val lastStepLabel = lastCompleted?.let { stepLabel(it.name, it.summary, it.status) }
            val lastLabel = if (lastStepLabel != null) lastStepLabel.text() else null
            val succeededText = if (completed > 0) {
                stringResource(R.string.tool_summary_succeeded, completed)
            } else {
                null
            }
            val failedText = if (failed > 0) {
                stringResource(R.string.tool_failed, failed).removePrefix("· ").trim()
            } else {
                null
            }
            // Failed count first: on a one-line summary it is the most
            // important signal, so it must never be the part that gets
            // ellipsised away behind a long last-tool label.
            val summaryParts = listOfNotNull(
                failedText,
                succeededText,
                lastLabel?.takeIf { it.isNotBlank() }?.let { "✓ $it" },
            )
            val collapsedSummary = summaryParts.joinToString(" • ")
            if (collapsedSummary.isNotBlank()) {
                Text(
                    text = collapsedSummary,
                    style = OpenCodeTypography.caption.copy(
                        fontFamily = OpenCodeTypography.mono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = if (failed > 0) colors.danger else colors.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = spacing.sm,
                            end = spacing.sm,
                            top = spacing.xxs,
                            bottom = spacing.sm,
                        ),
                )
            }
        }

        // Filter chips, shown under the header while the trace is open.
        // The choice lives in rememberSaveable above, so it is still there
        // when the trace is collapsed and reopened.
        if (open) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = spacing.sm, end = spacing.sm, bottom = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                ToolFilter.entries.forEach { option ->
                    val selected = option == filter
                    SelectorChip(
                        label = stringResource(option.labelRes),
                        selected = selected,
                        onClick = { filterName = option.name },
                    )
                }
            }
        }

        // Vertical timeline of steps, narrowed by the active filter. The
        // collapsed summary above is unfiltered — it is the at-a-glance
        // state of the run, not the filtered view. A finished clean run
        // shows no steps at all while collapsed (Opsi A): header +
        // one-line summary only. While streaming the collapsed trace
        // still shows the live steps, so progress stays visible.
        val filteredVisible = if (open || streaming) visible.filter { filter.matches(it) } else emptyList()
        if (filteredVisible.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = spacing.sm, end = spacing.sm, bottom = spacing.sm, top = spacing.xxs),
            ) {
                filteredVisible.forEachIndexed { index, call ->
                    ToolStep(
                        call = call,
                        isFirst = index == 0,
                        isLast = index == filteredVisible.lastIndex,
                        durationMillis = toolDurations[call.id],
                        startedAtMillis = toolStartedAtMillis[call.id],
                        workspacePath = workspacePath,
                        onOpenToolFile = onOpenToolFile,
                        onRequestDiffRevert = onRequestDiffRevert,
                        diffDecisions = diffDecisions,
                        onDiffDecision = onDiffDecision,
                        onBeforeHeightChange = onBeforeHeightChange,
                    )
                }
            }
        }
    }
}

/** Short clock time ("14:32") in the device timezone, for the trace header and turn timestamps. */
internal fun formatClock(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))

/** The only four timeline filters. Edit covers file writes and edits. */
// Audit-200 A2: internal (was private) so the name-mapping helper
// below — and its unit test — can see the enum.
internal enum class ToolFilter(val labelRes: Int) {
    ALL(R.string.tool_filter_all),
    FAILED(R.string.tool_filter_failed),
    EDIT(R.string.tool_filter_edit),
    READ(R.string.tool_filter_read),
    ;

    fun matches(call: ToolCall): Boolean = when (this) {
        ALL -> true
        FAILED -> call.status == "error"
        EDIT -> toolKindFor(call.name) == ToolKind.EDIT ||
            toolKindFor(call.name) == ToolKind.WRITE
        READ -> toolKindFor(call.name) == ToolKind.READ
    }
}

/**
 * Audit-200 A2: the [ToolFilter] for a saved name. Unknown names
 * (an old build's value, a corrupt restore) fall back to [ToolFilter.ALL]
 * instead of throwing — the filter is a view preference, never data.
 */
internal fun toolFilterForName(name: String): ToolFilter =
    runCatching { ToolFilter.valueOf(name) }.getOrDefault(ToolFilter.ALL)

/**
 * Compact status chip in the summary row.
 */
@Composable
private fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    isPulsing: Boolean = false,
) {
    val radius = OpenCodeTheme.tokens.radius
    val spacing = OpenCodeTheme.tokens.spacing
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(radius.full))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = spacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        if (isPulsing) {
            LivePulseIndicator(color = color, size = 4.5.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape),
            )
        }
        Text(
            text = text,
            style = OpenCodeTypography.caption.copy(
                fontFamily = OpenCodeTypography.mono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = color,
        )
    }
}

/**
 * One step on the timeline: compact terminal pill card with dark console styling.
 * Supports live pulsing execution indicator, clean status badges, and expandable terminal output.
 */
@Composable
fun ToolStep(
    call: ToolCall,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    isFirst: Boolean = false,
    /** How long this step took, in milliseconds, when known. Null hides
     * the chip — a duration is only shown from real per-tool timestamps,
     * never estimated from the trace start time. */
    durationMillis: Long? = null,
    /** Workspace path the file path is resolved against (Fase 1, item 3). */
    workspacePath: String? = null,
    /** Opens a workspace-relative file in the file viewer (Fase 1, item 3). */
    onOpenToolFile: (String) -> Unit = {},
    /** Asks the agent to revert a rejected edit (Fase 1, item 2). Receives
     * the call id, so the view model can record the decision against the
     * step that asked (audit-200 C2, M-052/M-053). */
    onRequestDiffRevert: (String, String, String?) -> Unit = { _, _, _ -> },
    /** Accept / Reject decisions per tool call id, owned by the view model
     * (audit-200 C2, M-053). */
    diffDecisions: Map<String, DiffDecision> = emptyMap(),
    /** Records an Accept / Reject decision for one tool call id. */
    onDiffDecision: (String, DiffDecision) -> Unit = { _, _ -> },
    /** Client-side start stamp for this step, epoch millis (Fase 2,
     * item 5). Only read while the step is still running, to show a live
     * elapsed time; a finished step uses [durationMillis] instead. */
    startedAtMillis: Long? = null,
    /** #6f: called before any height-changing toggle in this step
     * (header expand, auto-open on start, show-full output/diff),
     * so the caller can anchor the scroll position first. */
    onBeforeHeightChange: () -> Unit = {},
) {
    val colors = OpenCodeTheme.colors
    val motion = OpenCodeTheme.tokens.motion
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val active = call.status == "running" || call.status == "streaming"
    // A step that is running right now starts expanded, so its input is
    // visible without a tap; a finished step still starts collapsed —
    // except a finished edit (audit-200 B6, M-198): its Accept/Reject
    // review buttons live inside the expanded body, so a completed edit
    // that arrives already finished (restore, load-earlier) must start
    // expanded, or the review is hidden behind a tap even though the
    // trace shows the step. See toolStepInitialOpen. (The engine sends
    // no stdout until the tool finishes — audit-200 C2, M-059 — so the
    // expanded running step shows its input, not live output.)
    var open by rememberSaveable(call.id) { mutableStateOf(toolStepInitialOpen(call)) }

    // Animation C: shared press feedback on the step's main row.
    val stepPressSource = remember { MutableInteractionSource() }

    // Live elapsed clock for a running step (Fase 2, item 5): ticks once a
    // second while the step runs, and stops with it — the infinite loop
    // only exists while active, so nothing ticks on a finished turn.
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active, startedAtMillis) {
        if (active && startedAtMillis != null) {
            while (isActive) {
                nowMillis = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    // A step that starts running after it was composed (pending → running)
    // opens too. Never force one closed: a user-opened step stays open.
    // #6f: anchor first, but only when the value actually flips.
    LaunchedEffect(active) {
        if (active && !open) {
            onBeforeHeightChange()
            open = true
        }
    }

    // One glyph rotating 0° (right, collapsed) ↔ 90° (down, open).
    val stepChevronRotation by animateFloatAsState(
        targetValue = if (open) 90f else 0f,
        animationSpec = motion.tweenQuick(),
        label = "tool_step_chevron_rotation",
    )
    val isError = call.status == "error"
    val dotColor = when {
        call.status == "completed" -> colors.toolSuccess
        isError -> colors.toolError
        // Audit-200 C2 (M-050): a tool cut off by Stop / interrupt is
        // terminal, not running — a muted dot, not the live colour.
        call.status == "interrupted" -> colors.onSurfaceMuted
        else -> colors.toolRunning
    }

    val toolKind = toolKindFor(call.name)
    val badgeColor = badgeColorForKind(toolKind, colors)
    val label = stepLabel(call.name, call.summary, call.status)
    val verbText = label.verbText()
    val hasDetails = !call.inputText.isNullOrBlank() || !call.output.isNullOrBlank() || !call.error.isNullOrBlank()

    // Edit/write steps show a concise unified diff instead of raw input
    // JSON (audit finding 4). Parsed once per input change; null when the
    // tool is not an edit/write, or its old/new text cannot be read (a
    // partial stream, an unknown field name) — then the raw input block
    // below is shown as before, so nothing is lost.
    //
    // `patch` / `apply_patch` / `multiedit` never went through
    // extractEditDiff — their change is a unified-diff string or an
    // `edits` array, not one old/new pair (audit-200 C2, M-051) — so
    // their hunks come from extractPatchHunks instead, and the review
    // row below is reached for them too.
    val patchHunks = remember(call.inputText, call.name) {
        if (toolKind == ToolKind.EDIT) extractPatchHunks(call) else null
    }
    val editDiff = remember(call.inputText, toolKind) {
        if (toolKind == ToolKind.EDIT || toolKind == ToolKind.WRITE) extractEditDiff(call) else null
    }
    val diffHunks = remember(editDiff, patchHunks) {
        patchHunks ?: editDiff?.let { computeDiffHunks(it.oldText, it.newText) }
    }
    val diffCounts = remember(diffHunks) { diffHunks?.let { DiffParser.counts(it) } }

    // Path-copy feedback (C-D): tapping a file step's target copies the
    // full path and flashes a check for 1.5s. No editor exists in the app,
    // so copy-to-clipboard is the safe jump-to-file.
    val clipboardManager = LocalClipboardManager.current
    var pathCopied by remember(call.id) { mutableStateOf(false) }
    LaunchedEffect(pathCopied) {
        if (pathCopied) {
            delay(1500)
            pathCopied = false
        }
    }

    // Tap-to-open (Fase 1, item 3): the file this step acted on, resolved
    // to a workspace-relative path. Null when the tool is not a file tool
    // or the path cannot be verified inside the workspace — then the
    // target keeps its old copy-on-tap behaviour instead of opening
    // something it should not.
    val filePath = remember(call) { toolFilePath(call) }
    val openablePath = remember(filePath, workspacePath) {
        filePath?.let { relativizeToWorkspace(it, workspacePath) }
    }

    // Reject needs a verified file path: the input must name the file,
    // and it must resolve inside the workspace. A summary is free text,
    // never a revert target (audit-70 70-F03) — when there is no verified
    // path the Reject button is not offered at all, instead of asking the
    // agent to revert a guess.
    val revertPath = remember(call, workspacePath) {
        toolInputFilePath(call)?.let { relativizeToWorkspace(it, workspacePath) }
    }
    // The change itself, so the revert prompt names this edit and not just
    // the file: two edits to one file must not produce identical prompts
    // (audit-70 07-F2).
    val revertChange = remember(diffHunks) { diffHunks?.let { revertChangeDescription(it) } }

    // Diff decision (Fase 1, item 2), per tool call: owned by the view
    // model (audit-200 C2, M-053), read here — a transcript rebuild used
    // to reset this to NONE because it lived in the composition, and a
    // Reject could then fire twice.
    val diffDecision = diffDecisions[call.id] ?: DiffDecision.NONE

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // Vertical rail column with dot and connecting lines
        Box(
            modifier = Modifier
                .width(22.dp)
                .fillMaxHeight()
                .drawBehind {
                    val centerX = size.width / 2f
                    val dotCenterY = 16.dp.toPx()
                    // One continuous rail: the same 1dp hairline on every
                    // step. The rail used to change width (2dp) and colour
                    // (danger) on error steps, so at every error boundary
                    // the line jumped in width and colour and read as
                    // chopped, not smooth. Status is already carried by
                    // the dot (green / red × / pulse), the card border and
                    // the Failed pill, so the rail carries none of it.
                    val strokeWidth = 1.dp.toPx()
                    val railColor = colors.hairline

                    if (!isFirst) {
                        drawLine(
                            color = railColor,
                            start = Offset(centerX, 0f),
                            end = Offset(centerX, dotCenterY),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                    if (!isLast) {
                        drawLine(
                            color = railColor,
                            start = Offset(centerX, dotCenterY),
                            end = Offset(centerX, size.height),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (active) {
                    LivePulseIndicator(color = colors.activity, size = 6.dp)
                } else if (isError) {
                    // A failed step is more than a slightly bigger dot:
                    // a white × inside the red dot reads as "failed" even
                    // without colour, unlike the 8dp-vs-6dp size difference.
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(dotColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(
                            glyph = Glyph.Close,
                            tint = Color.White,
                            size = 7.dp,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(dotColor, CircleShape),
                    )
                }
            }
        }

        // Terminal card column: header pill & optional expanded console
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.xs, bottom = if (isLast) 0.dp else spacing.sm),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radius.sm))
                    .background(
                        if (active) colors.surfaceVariant.copy(alpha = 0.35f)
                        else colors.surfaceVariant.copy(alpha = 0.2f),
                    )
                    .border(
                        width = if (isError) 1.dp else 0.5.dp,
                        color = when {
                            isError -> colors.danger.copy(alpha = 0.5f)
                            active -> colors.activity.copy(alpha = 0.5f)
                            else -> colors.hairline
                        },
                        shape = RoundedCornerShape(radius.sm),
                    ),
            ) {
                // Hoisted: stringResource() is @Composable and cannot be
                // called inside a .semantics {} block.
                val stepStateDescription = stringResource(
                    if (open) R.string.tool_step_expanded else R.string.tool_step_collapsed,
                )
                // Main terminal execution row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScale(motion, stepPressSource)
                        // A11y (#8b/#8c): an expandable step row is a button
                        // whose expanded state is announced.
                        .semantics { stateDescription = stepStateDescription }
                        .clickable(
                            interactionSource = stepPressSource,
                            indication = LocalIndication.current,
                            enabled = hasDetails,
                            role = Role.Button,
                        ) {
                            // #6f: anchor the scroll position before the
                            // height toggle, so the list does not jump.
                            onBeforeHeightChange()
                            open = !open
                        }
                        .padding(horizontal = spacing.sm, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Tool Badge Chip
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(radius.xs))
                            .background(badgeColor.copy(alpha = 0.14f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        GlyphIcon(glyph = label.glyph, tint = badgeColor, size = 10.dp)
                        Text(
                            text = toolKind.label,
                            style = OpenCodeTypography.caption.copy(
                                fontFamily = OpenCodeTypography.mono,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = badgeColor,
                        )
                    }

                    // Command snippet or Target text
                    if (toolKind == ToolKind.SHELL) {
                        val command = extractCommand(call) ?: call.summary
                        val firstLine = command.lineSequence().firstOrNull().orEmpty()
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "$ ",
                                style = OpenCodeTypography.codeInline.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = colors.accentText,
                            )
                            Text(
                                text = firstLine,
                                style = OpenCodeTypography.codeInline.copy(
                                    fontSize = 11.5.sp,
                                ),
                                color = if (active) colors.toolRunning else colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (active) {
                                BlinkingCursor(
                                    color = colors.toolRunning,
                                    modifier = Modifier.padding(start = spacing.xxs),
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (verbText.isNotEmpty()) {
                                Text(
                                    text = verbText,
                                    style = OpenCodeTypography.label.copy(fontSize = 11.sp),
                                    color = if (active) colors.toolRunning else if (isError) colors.danger else colors.onSurfaceMuted,
                                )
                            }
                            if (label.target.isNotEmpty()) {
                                // The label shows only the last two path
                                // segments, so the clipboard gets the full
                                // input path instead (C-D). Tapping the
                                // target opens the file when it resolves
                                // inside the workspace (Fase 1, item 3) —
                                // underlined in accent, like a link — and
                                // copies the path otherwise, as before.
                                // The step itself still expands on tap
                                // elsewhere.
                                val isFileTool = toolKind == ToolKind.EDIT ||
                                    toolKind == ToolKind.WRITE || toolKind == ToolKind.READ
                                val fullPath = call.summary
                                val canCopyPath = isFileTool && fullPath.isNotBlank() && openablePath == null
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(start = spacing.xs)
                                        .then(
                                            when {
                                                openablePath != null -> Modifier.clickable(role = Role.Button) {
                                                    onOpenToolFile(openablePath)
                                                }
                                                canCopyPath -> Modifier.clickable(role = Role.Button) {
                                                    // 65-F4: the check only flips on a real copy.
                                                    if (setClipboardText(clipboardManager, AnnotatedString(fullPath))) pathCopied = true
                                                }
                                                else -> Modifier
                                            }
                                        ),
                                ) {
                                    Text(
                                        text = label.target,
                                        style = OpenCodeTypography.codeInline.copy(fontSize = 11.5.sp),
                                        color = if (pathCopied) colors.success
                                        else if (openablePath != null) colors.accentText
                                        else if (active) colors.toolRunning else colors.onSurface,
                                        textDecoration = if (openablePath != null) TextDecoration.Underline else null,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (pathCopied) {
                                        GlyphIcon(
                                            glyph = Glyph.Check,
                                            tint = colors.success,
                                            size = 10.dp,
                                            modifier = Modifier.padding(start = 3.dp),
                                        )
                                    }
                                }
                            }
                            if (active) {
                                BlinkingCursor(
                                    color = colors.activity,
                                    modifier = Modifier.padding(start = spacing.xxs),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Status Badge (Success / Failed / Running)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        modifier = Modifier.padding(start = spacing.xs),
                    ) {
                        when {
                            active -> {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(radius.full))
                                        .background(colors.activity.copy(alpha = 0.12f))
                                        .padding(horizontal = spacing.xs, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    LivePulseIndicator(color = colors.activity, size = 4.5.dp)
                                    Text(
                                        text = stringResource(R.string.tool_status_running),
                                        style = OpenCodeTypography.caption.copy(
                                            fontFamily = OpenCodeTypography.mono,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        // 47-F4: the pill text sits on a 12%
                                        // tint of the same colour — the plain
                                        // token is only 4.09:1 in dark.
                                        color = colors.toolRunningText,
                                    )
                                }
                            }
                            isError -> {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(radius.full))
                                        .background(colors.danger.copy(alpha = 0.12f))
                                        .padding(horizontal = spacing.xs, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.tool_status_failed),
                                        style = OpenCodeTypography.caption.copy(
                                            fontFamily = OpenCodeTypography.mono,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        // 47-F4: same tint problem as the
                                        // running pill — plain danger is
                                        // only 4.40:1 in dark.
                                        color = colors.toolErrorText,
                                    )
                                }
                            }
                            // Audit-200 C2 (M-050): a tool the run left
                            // behind reads "Interrupted", not "Running" —
                            // and not "Failed" either, since nothing
                            // failed; the run was stopped.
                            call.status == "interrupted" -> {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(radius.full))
                                        .background(colors.onSurface.copy(alpha = 0.08f))
                                        .padding(horizontal = spacing.xs, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.tool_status_interrupted),
                                        style = OpenCodeTypography.caption.copy(
                                            fontFamily = OpenCodeTypography.mono,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        color = colors.onSurfaceMuted,
                                    )
                                }
                            }
                            call.status == "completed" -> {
                                // An edit/write with a readable diff reads
                                // "+n −m" — the change itself, not the output
                                // line count. Anything else keeps the old
                                // "N lines" / "Success" pill.
                                val summaryText = diffCounts?.let { (added, removed) ->
                                    "+$added −$removed"
                                } ?: run {
                                    val out = call.output
                                    if (!out.isNullOrBlank()) {
                                        val lines = out.lines().size
                                        if (lines > 1) {
                                            stringResource(R.string.tool_lines, lines)
                                        } else {
                                            stringResource(R.string.tool_lines_one, 1)
                                        }
                                    } else {
                                        stringResource(R.string.tool_status_success)
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(radius.full))
                                        .background(colors.success.copy(alpha = 0.10f))
                                        .padding(horizontal = spacing.xs, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    GlyphIcon(Glyph.Check, tint = colors.success, size = 9.dp)
                                    Text(
                                        text = summaryText,
                                        style = OpenCodeTypography.caption.copy(
                                            fontFamily = OpenCodeTypography.mono,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Normal,
                                        ),
                                        color = colors.success,
                                    )
                                }
                            }
                        }

                        // Duration chip (finding 20): only when the caller
                        // knows the real per-step duration. A finished step
                        // without one shows no chip rather than a guess.
                        // A running step shows a live elapsed time instead
                        // (Fase 2, item 5): same chip, activity colour, so
                        // "how long has this been running" is answered
                        // while it runs, not only after it finishes.
                        if (active && startedAtMillis != null) {
                            Text(
                                text = formatStepDuration(liveElapsedMillis(startedAtMillis, nowMillis)),
                                style = OpenCodeTypography.caption.copy(
                                    fontFamily = OpenCodeTypography.mono,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Normal,
                                ),
                                color = colors.toolRunning,
                                maxLines = 1,
                            )
                        } else if (durationMillis != null && !active) {
                            Text(
                                text = formatStepDuration(durationMillis),
                                style = OpenCodeTypography.caption.copy(
                                    fontFamily = OpenCodeTypography.mono,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Normal,
                                ),
                                color = colors.onSurfaceFaint,
                                maxLines = 1,
                            )
                        }

                        if (hasDetails) {
                            GlyphIcon(
                                glyph = Glyph.ChevronRight,
                                tint = colors.onSurfaceFaint,
                                size = 10.dp,
                                modifier = Modifier.graphicsLayer { rotationZ = stepChevronRotation },
                            )
                        }
                    }
                }

                // Expanded Terminal Console Windows
                AnimatedVisibility(
                    visible = open,
                    enter = fadeIn(animationSpec = tween(motion.quick)) +
                        expandVertically(animationSpec = tween(motion.standard)),
                    exit = fadeOut(animationSpec = tween(motion.quick)) +
                        shrinkVertically(animationSpec = tween(motion.standard)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = spacing.sm,
                                end = spacing.sm,
                                bottom = spacing.sm,
                                top = spacing.xxs,
                            ),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        val isShell = toolKind == ToolKind.SHELL
                        val commandText = if (isShell) extractCommand(call) else null
                        val inputBody = commandText ?: call.inputText?.takeIf { it.isNotBlank() }
                        // A `todowrite` step renders as a checklist, not raw
                        // input JSON (Tier 3, item 1). Null when the input
                        // is not a usable todos array — the raw block below
                        // is then shown instead, so nothing is hidden.
                        val todoList = remember(call.inputText) {
                            if (call.name.equals("todowrite", ignoreCase = true)) {
                                parseTodoList(call.inputText)
                            } else {
                                null
                            }
                        }

                        // 1. Edit/write change, as a concise unified diff
                        // (audit finding 4) — not the raw input JSON. When
                        // the diff cannot be parsed, the input block below
                        // is shown instead, so the raw arguments are never
                        // hidden behind a failed parse.
                        if (diffHunks != null && diffHunks.isNotEmpty() && diffCounts != null) {
                            InlineDiffBlock(
                                hunks = diffHunks,
                                additions = diffCounts.first,
                                deletions = diffCounts.second,
                                onBeforeHeightChange = onBeforeHeightChange,
                            )
                            // Accept / reject for a finished edit (Fase 1,
                            // item 2). Only a completed edit/write shows
                            // it: a running step has no final diff, and a
                            // failed one applied nothing. See DiffDecision
                            // for what each button honestly does — Accept
                            // records the decision locally, Reject asks the
                            // agent to revert the file in a new turn.
                            if (isReviewableEdit(call)) {
                                DiffDecisionRow(
                                    decision = diffDecision,
                                    onAccept = { onDiffDecision(call.id, DiffDecision.ACCEPTED) },
                                    showReject = revertPath != null,
                                    onReject = {
                                        // Audit-200 C2 (M-052): the view
                                        // model records REJECTED only when
                                        // the prompt was actually accepted
                                        // for sending/queueing — a dropped
                                        // duplicate keeps the buttons up
                                        // instead of claiming a request
                                        // that never went out.
                                        val path = revertPath
                                        if (path != null) onRequestDiffRevert(call.id, path, revertChange)
                                    },
                                )
                            }
                        } else if (todoList != null) {
                            TodoChecklist(todos = todoList)
                        } else if (!inputBody.isNullOrBlank()) {
                            val inputLang = if (isShell) "bash" else if (inputBody.trim().startsWith("{")) "json" else null
                            val prompt = if (isShell) "$ " else null
                            TerminalConsoleBlock(
                                title = if (isShell) stringResource(R.string.tool_command) else stringResource(R.string.tool_input),
                                body = inputBody,
                                language = inputLang,
                                promptPrefix = prompt,
                                subtitle = if (isShell) "bash" else null,
                                onBeforeHeightChange = onBeforeHeightChange,
                            )
                        }

                        // Audit-200 C2 (M-059): the engine sends no stdout
                        // until the tool finishes — a running step shows
                        // its input, not live output. Say so, instead of
                        // leaving a blank space that reads as a frozen
                        // stream.
                        if (active && call.output.isNullOrBlank() && call.error.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.tool_output_on_finish),
                                style = OpenCodeTypography.caption.copy(fontSize = 11.sp),
                                color = colors.onSurfaceFaint,
                            )
                        }

                        // 2. Output STDOUT block
                        call.output?.takeIf { it.isNotBlank() }?.let { out ->
                            val outLang = detectOutputLanguage(out, isShell)
                            val lineCount = out.lines().size
                            val lineSubtitle = if (lineCount > 1) {
                                stringResource(R.string.tool_lines, lineCount)
                            } else {
                                stringResource(R.string.tool_lines_one, 1)
                            }

                            TerminalConsoleBlock(
                                title = stringResource(R.string.tool_stdout),
                                body = truncateOutput(out),
                                // "Show full output" renders the whole
                                // output (audit-200 C2, M-056): the body
                                // above is only the first MAX_OUTPUT
                                // characters while collapsed.
                                fullBody = out,
                                // Copy takes the full output: the body
                                // shown is truncated at MAX_OUTPUT, but
                                // copying that would silently lose the rest.
                                copyText = out,
                                language = outLang,
                                subtitle = lineSubtitle,
                                onBeforeHeightChange = onBeforeHeightChange,
                            )
                        }

                        // 3. Error STDERR block
                        call.error?.takeIf { it.isNotBlank() }?.let { err ->
                            TerminalConsoleBlock(
                                title = stringResource(R.string.tool_stderr),
                                body = err,
                                isError = true,
                                onBeforeHeightChange = onBeforeHeightChange,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Terminal window console block:
 * - Unified header with the Markdown code-block header: mono title plus one
 *   status-coloured 8dp dot (no macOS traffic-light dots).
 * - Monospace console typography
 * - Live syntax highlighting for commands, output, JSON, and diffs
 * - Built-in Copy action button
 */
@Composable
private fun TerminalConsoleBlock(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    language: String? = null,
    promptPrefix: String? = null,
    isError: Boolean = false,
    subtitle: String? = null,
    /** Text the Copy button writes; defaults to [body] (the displayed text). */
    copyText: String = body,
    /** The full text, when [body] is only its collapsed slice (audit-200
     * C2, M-056): "Show full output" renders this instead of [body].
     * Null when the body is already complete (input, stderr). */
    fullBody: String? = null,
    /** #6f: called before the show-full toggle changes this block's
     * height, so the caller can anchor the scroll position first. */
    onBeforeHeightChange: () -> Unit = {},
) {
    val colors = OpenCodeTheme.colors
    val motion = OpenCodeTheme.tokens.motion
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    val boxBg = if (isError) colors.danger.copy(alpha = 0.08f) else colors.background
    val boxBorder = if (isError) colors.danger.copy(alpha = 0.35f) else colors.hairline
    val headerBorder = if (isError) colors.danger.copy(alpha = 0.2f) else colors.hairline
    val shape = RoundedCornerShape(radius.sm)

    // Long output is capped at 320dp, but the rest is one tap away instead
    // of cut off with no way to see it (the old 220dp cap + 8000-char
    // truncation had no escape hatch at all). "Show full output" renders
    // the whole text (audit-200 C2, M-056) — the old toggle only lifted
    // the height cap while the text stayed truncated.
    var showFullOutput by remember { mutableStateOf(false) }

    // The text on screen: the full text while expanded, the collapsed
    // slice otherwise. Without a fullBody the toggle only lifts the
    // height, as before.
    val shownBody = if (showFullOutput) fullBody ?: body else body

    // Audit-200 C2 (M-057): output used to grow below the viewport while
    // the box sat at the top, so a long run looked frozen and the result
    // — usually at the tail — stayed under the fold. The box now follows
    // its tail while the user has not scrolled up; any manual scroll up
    // pauses the follow until they scroll back to the bottom.
    val scrollState = rememberScrollState()
    var followTail by remember { mutableStateOf(true) }
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value to scrollState.maxValue }
            .collect { (value, max) -> followTail = value >= max - 8 }
    }
    LaunchedEffect(body) {
        if (followTail) {
            // Wait until the layout knows the content height; scrolling
            // before that is a no-op (maxValue is still 0).
            snapshotFlow { scrollState.maxValue }.first { it > 0 }
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    // One glyph rotating 0° (right) ↔ 90° (down) for show full / less.
    val showFullRotation by animateFloatAsState(
        targetValue = if (showFullOutput) 90f else 0f,
        animationSpec = motion.tweenQuick(),
        label = "tool_output_expand_rotation",
    )

    val highlightedText = rememberSyntaxHighlightedText(
        language = language,
        text = shownBody,
        promptPrefix = promptPrefix,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(boxBg)
            .border(0.5.dp, boxBorder, shape),
    ) {
        // Terminal Window Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant.copy(alpha = if (isError) 0.15f else 0.4f))
                .border(
                    width = 0.5.dp,
                    color = headerBorder,
                    shape = RoundedCornerShape(topStart = radius.sm, topEnd = radius.sm),
                )
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // One status-coloured 8dp dot, like the Markdown code-block
            // header: error reads danger, anything else the live colour.
            // The old macOS traffic-light dots (hardcoded red/yellow/
            // green) are gone.
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (isError) colors.dangerBright else colors.activity,
                        CircleShape,
                    ),
            )

            Spacer(Modifier.width(spacing.sm))

            // Console Title / Tab Label
            Text(
                text = title,
                style = OpenCodeTypography.caption.copy(
                    fontFamily = OpenCodeTypography.mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isError) colors.danger else colors.onSurfaceMuted,
            )

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = "· $subtitle",
                    style = OpenCodeTypography.caption.copy(
                        fontFamily = OpenCodeTypography.mono,
                        fontSize = 10.sp,
                    ),
                    color = colors.onSurfaceFaint,
                    modifier = Modifier.padding(start = spacing.xs),
                )
            }

            Spacer(Modifier.weight(1f))

            // Copy Action Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(radius.xs))
                    .clickable(role = Role.Button) {
                        // 65-F4: the check only flips on a real copy.
                        if (setClipboardText(clipboardManager, AnnotatedString(copyText))) copied = true
                    }
                    // 47-F7: a 2.dp vertical pad is only ~18dp tall — the
                    // minimum touch target is 48dp.
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = spacing.xs, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                GlyphIcon(
                    glyph = if (copied) Glyph.Check else Glyph.Copy,
                    tint = if (copied) colors.success else colors.onSurfaceMuted,
                    size = 11.dp,
                )
                Text(
                    text = if (copied) stringResource(R.string.code_copied) else stringResource(R.string.code_copy),
                    style = OpenCodeTypography.caption.copy(fontSize = 10.sp),
                    color = if (copied) colors.success else colors.onSurfaceMuted,
                )
            }
        }

        // Terminal Content Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (showFullOutput) Dp.Infinity else 320.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
        ) {
            SelectionContainer {
                Text(
                    text = highlightedText,
                    style = OpenCodeTypography.code.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 16.5.sp,
                    ),
                    color = if (isError) colors.danger else colors.onSurface,
                )
            }
        }

        // "Show full output" lifts the height cap instead of leaving the
        // rest of a long output unreachable behind a nested scroll.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // #6f: anchor the scroll position before the height
                // toggle, so the list does not jump.
                .clickable(role = Role.Button) { onBeforeHeightChange(); showFullOutput = !showFullOutput }
                .padding(horizontal = spacing.md, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            GlyphIcon(
                glyph = Glyph.ChevronRight,
                tint = colors.onSurfaceMuted,
                size = 10.dp,
                modifier = Modifier.graphicsLayer { rotationZ = showFullRotation },
            )
            Text(
                text = stringResource(
                    if (showFullOutput) R.string.tool_show_less else R.string.tool_show_full,
                ),
                style = OpenCodeTypography.caption.copy(fontSize = 10.sp),
                color = colors.onSurfaceMuted,
            )
        }
    }
}

/**
 * Syntax highlights [text] using [highlight] with optional prompt prefix.
 */
@Composable
private fun rememberSyntaxHighlightedText(
    language: String?,
    text: String,
    promptPrefix: String? = null,
): AnnotatedString {
    val colors = OpenCodeTheme.colors
    val tokens = remember(text, language) { highlight(language, text) }
    return remember(tokens, colors, promptPrefix) {
        buildAnnotatedString {
            if (promptPrefix != null) {
                withStyle(
                    SpanStyle(
                        color = colors.accentText,
                        fontWeight = FontWeight.Bold,
                    ),
                ) {
                    append(promptPrefix)
                }
            }
            tokens.forEach { token ->
                withStyle(
                    SpanStyle(
                        color = when (token.kind) {
                            TokenKind.Keyword -> colors.syntaxKeyword
                            TokenKind.String -> colors.syntaxString
                            TokenKind.Number -> colors.syntaxNumber
                            TokenKind.Comment -> colors.syntaxComment
                            TokenKind.Function -> colors.syntaxFunction
                            TokenKind.Type -> colors.syntaxType
                            TokenKind.Operator -> colors.onSurfaceMuted
                            TokenKind.Plain -> colors.onSurface
                        },
                        fontWeight = when (token.kind) {
                            TokenKind.Keyword -> FontWeight.Medium
                            else -> FontWeight.Normal
                        },
                        fontStyle = if (token.kind == TokenKind.Comment) FontStyle.Italic else FontStyle.Normal,
                    ),
                ) {
                    append(token.text)
                }
            }
        }
    }
}

/** Known tool kinds for high-tech terminal badges. */
private enum class ToolKind(val label: String) {
    SHELL("BASH"),
    READ("READ"),
    WRITE("WRITE"),
    EDIT("EDIT"),
    SEARCH("SEARCH"),
    LIST("LIST"),
    FETCH("FETCH"),
    OTHER("TOOL"),
}

private fun toolKindFor(name: String): ToolKind = when (name.lowercase()) {
    "bash", "shell", "command" -> ToolKind.SHELL
    "read" -> ToolKind.READ
    "write" -> ToolKind.WRITE
    "edit", "patch", "apply_patch", "multiedit" -> ToolKind.EDIT
    "grep", "search" -> ToolKind.SEARCH
    "glob", "list", "ls" -> ToolKind.LIST
    "webfetch", "fetch", "websearch" -> ToolKind.FETCH
    else -> ToolKind.OTHER
}

private fun badgeColorForKind(kind: ToolKind, colors: OpenCodeColors): Color = when (kind) {
    // 47-F3: the badge label is 9sp bold mono on a 14% tint of itself
    // over the assistant bubble, so the blue kinds use the dedicated
    // badge text token — accentText is only 4.27:1 (light) / 3.95:1
    // (dark) there, badgeAccentText is 6.14:1 / 6.77:1.
    ToolKind.SHELL -> colors.badgeAccentText
    ToolKind.READ -> colors.success
    ToolKind.WRITE -> colors.warning
    ToolKind.EDIT -> colors.warning
    ToolKind.SEARCH -> colors.badgeAccentText
    ToolKind.LIST -> colors.syntaxNumber
    ToolKind.FETCH -> colors.badgeAccentText
    ToolKind.OTHER -> colors.onSurfaceMuted
}

/** Extracts shell command from tool call input text. */
private fun extractCommand(call: ToolCall): String? {
    val input = call.inputText ?: return null
    val match = Regex(""""command"\s*:\s*"((?:\\.|[^"\\])*)"""").find(input)
    if (match != null) {
        return unescapeJson(match.groupValues[1])
    }
    if (!input.trim().startsWith("{")) {
        return input.trim()
    }
    return call.summary.ifEmpty { null }
}

/** Simple JSON string unescaper without external dependencies. */
private fun unescapeJson(raw: String): String {
    val sb = StringBuilder(raw.length)
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c == '\\' && i + 1 < raw.length) {
            when (val next = raw[i + 1]) {
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                'b' -> sb.append('\b')
                'f' -> sb.append('\u000c')
                '"' -> sb.append('"')
                '\\' -> sb.append('\\')
                '/' -> sb.append('/')
                'u' -> {
                    if (i + 5 < raw.length) {
                        val hex = raw.substring(i + 2, i + 6)
                        hex.toIntOrNull(16)?.let { sb.append(it.toChar()) } ?: sb.append("\\u$hex")
                        i += 6
                        continue
                    } else {
                        sb.append(c)
                    }
                }
                else -> sb.append(next)
            }
            i += 2
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

/** Detects syntax language for output formatting. */
private fun detectOutputLanguage(output: String, isShell: Boolean): String? {
    val trimmed = output.trim()
    if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
        (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
        return "json"
    }
    if (isShell) {
        return "bash"
    }
    return null
}

private const val MAX_OUTPUT = 8000

/**
 * The output text as shown while collapsed (audit-200 C2, M-056): the
 * first [MAX_OUTPUT] characters plus a marker, or the whole text when it
 * fits. "Show full output" renders the full text instead — see
 * [TerminalConsoleBlock].
 */
internal fun truncateOutput(out: String): String =
    if (out.length > MAX_OUTPUT) out.take(MAX_OUTPUT) + "\n… [truncated]" else out

/** Old and new text of an edit/write tool call, read from its input JSON. */
private data class EditDiff(
    val oldText: String,
    val newText: String,
)

/**
 * Reads the old/new text out of an edit/write tool call's input.
 *
 * The engine names the fields `oldString`/`newString` (edit) and `content`
 * (write, whole file, no old text); `oldText`/`newText` and snake_case
 * variants are accepted too, since tool schemas drift. Returns null for a
 * `multiedit` (its `edits` array is read by [extractPatchHunks], not here),
 * for a partial stream that is not JSON yet, and for anything without both
 * sides of the change — the caller then falls back to the raw input block.
 */
private fun extractEditDiff(call: ToolCall): EditDiff? {
    val input = call.inputText ?: return null
    if (!input.trim().startsWith("{")) return null
    val name = call.name.lowercase()
    if (name == "multiedit") return null
    val old = extractJsonStringField(input, listOf("oldString", "oldText", "old_string", "old"))
    val new = extractJsonStringField(input, listOf("newString", "newText", "new_string", "new"))
    if (old != null && new != null) return EditDiff(oldText = old, newText = new)
    if (name == "write") {
        val content = extractJsonStringField(input, listOf("content"))
        if (content != null) return EditDiff(oldText = "", newText = content)
    }
    return null
}

/** First matching JSON string field, unescaped; null when absent or malformed. */
private fun extractJsonStringField(input: String, keys: List<String>): String? {
    for (key in keys) {
        val match = Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""").find(input)
        if (match != null) return unescapeJson(match.groupValues[1])
    }
    return null
}

/** Every value of the first matching JSON string field, in document order. */
private fun extractJsonStringFields(input: String, keys: List<String>): List<String> {
    for (key in keys) {
        val matches = Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""").findAll(input).toList()
        if (matches.isNotEmpty()) return matches.map { unescapeJson(it.groupValues[1]) }
    }
    return emptyList()
}

/**
 * Hunks for `patch` / `apply_patch` / `multiedit` (audit-200 C2, M-051):
 * these never went through [extractEditDiff] — their change is a
 * unified-diff string or an `edits` array, not one old/new pair — so
 * they never got a diff, or Accept/Reject buttons, even though
 * [isReviewableEdit] calls them reviewable. Null when the change cannot
 * be read (a partial stream, an unknown field name, mismatched edit
 * pairs) — the caller then falls back to the raw input block.
 *
 * - `patch` / `apply_patch`: the `patch` field is parsed as a unified
 *   diff by [DiffParser], the same parser the full-screen diff uses.
 * - `multiedit`: every edit in the `edits` array becomes its own
 *   line-diff; the edits are paired in document order, so an edit
 *   without both sides fails the whole parse rather than silently
 *   pairing the wrong texts.
 */
internal fun extractPatchHunks(call: ToolCall): List<DiffHunk>? {
    val input = call.inputText ?: return null
    if (!input.trim().startsWith("{")) return null
    return when (call.name.lowercase()) {
        "patch", "apply_patch" -> {
            val patch = extractJsonStringField(input, listOf("patch"))
                ?.takeIf { it.isNotBlank() } ?: return null
            DiffParser.parse(patch).takeIf { it.isNotEmpty() }
        }
        "multiedit" -> {
            val olds = extractJsonStringFields(input, listOf("oldString", "oldText", "old_string", "old"))
            val news = extractJsonStringFields(input, listOf("newString", "newText", "new_string", "new"))
            if (olds.isEmpty() || olds.size != news.size) return null
            olds.zip(news)
                .flatMap { (old, new) -> computeDiffHunks(old, new) }
                .takeIf { it.isNotEmpty() }
        }
        else -> null
    }
}

/** Lines of context kept around each change in a computed diff. */
private const val DIFF_CONTEXT_LINES = 3

/** Lines shown before a diff collapses behind "Show full diff". */
private const val DIFF_COLLAPSED_LINES = 10

/**
 * Line diff of [oldText] vs [newText] as [DiffHunk]s, the same types the
 * full-screen [DiffScreen] renders — so a step diff and a file diff can
 * never disagree about what "+" and "−" mean.
 *
 * A plain LCS diff: exact for the short texts an edit tool carries, and
 * capped — past [DIFF_LCS_LINE_LIMIT] lines the whole old text is one
 * removal and the whole new text one addition, which keeps the counts
 * exact instead of silently truncating them. Empty lines at the end of
 * either side (a trailing newline) are not changes.
 */
/** Shared with the file conflict dialog (audit-200 A6, DL-022). */
internal const val DIFF_LCS_LINE_LIMIT = 500

fun computeDiffHunks(oldText: String, newText: String): List<DiffHunk> {
    val oldLines = oldText.split('\n').dropLastWhile { it.isEmpty() }
    val newLines = newText.split('\n').dropLastWhile { it.isEmpty() }
    if (oldLines == newLines) return emptyList()

    // (kind, oldIndex, newIndex) in order, from the LCS backtrack — or a
    // wholesale replace when the texts are too long to diff exactly.
    val ops: List<Triple<DiffLine.Kind, Int?, Int?>>
    if (oldLines.size + newLines.size > DIFF_LCS_LINE_LIMIT) {
        ops = oldLines.indices.map { Triple(DiffLine.Kind.REMOVED, it, null) } +
            newLines.indices.map { Triple(DiffLine.Kind.ADDED, null, it) }
    } else {
        val m = oldLines.size
        val n = newLines.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in m - 1 downTo 0) {
            for (j in n - 1 downTo 0) {
                dp[i][j] = if (oldLines[i] == newLines[j]) dp[i + 1][j + 1] + 1
                else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val walked = mutableListOf<Triple<DiffLine.Kind, Int?, Int?>>()
        var i = 0
        var j = 0
        while (i < m && j < n) {
            when {
                oldLines[i] == newLines[j] -> {
                    walked += Triple(DiffLine.Kind.CONTEXT, i, j)
                    i++
                    j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> {
                    walked += Triple(DiffLine.Kind.REMOVED, i, null)
                    i++
                }
                else -> {
                    walked += Triple(DiffLine.Kind.ADDED, null, j)
                    j++
                }
            }
        }
        while (i < m) {
            walked += Triple(DiffLine.Kind.REMOVED, i, null)
            i++
        }
        while (j < n) {
            walked += Triple(DiffLine.Kind.ADDED, null, j)
            j++
        }
        ops = walked
    }

    // Split into hunks: a run of context longer than twice the context
    // window breaks the hunk, keeping DIFF_CONTEXT_LINES lines on each side.
    val hunks = mutableListOf<DiffHunk>()
    var cursor = 0
    while (cursor < ops.size) {
        // Skip leading context up to the first change.
        var start = cursor
        while (start < ops.size && ops[start].first == DiffLine.Kind.CONTEXT) {
            start++
        }
        if (start >= ops.size) break
        val hunkStart = maxOf(cursor, start - DIFF_CONTEXT_LINES)
        // Extend to the last change, then DIFF_CONTEXT_LINES of trailing context.
        var end = start
        var lastChange = start
        while (end < ops.size) {
            if (ops[end].first != DiffLine.Kind.CONTEXT) lastChange = end
            // A context run longer than the window ends the hunk.
            if (end - lastChange > DIFF_CONTEXT_LINES * 2) break
            end++
        }
        val hunkEnd = minOf(lastChange + 1 + DIFF_CONTEXT_LINES, ops.size)
        val lines = ops.subList(hunkStart, hunkEnd).map { (kind, oldIdx, newIdx) ->
            val text = when (kind) {
                DiffLine.Kind.REMOVED -> oldLines[oldIdx!!]
                DiffLine.Kind.ADDED -> newLines[newIdx!!]
                else -> oldLines[oldIdx!!]
            }
            DiffLine(
                kind = kind,
                text = text,
                oldLine = oldIdx?.plus(1),
                newLine = newIdx?.plus(1),
            )
        }
        val oldStart = lines.firstNotNullOfOrNull { it.oldLine } ?: 1
        val newStart = lines.firstNotNullOfOrNull { it.newLine } ?: 1
        val oldCount = lines.count { it.oldLine != null }
        val newCount = lines.count { it.newLine != null }
        hunks += DiffHunk(
            header = "@@ -$oldStart,$oldCount +$newStart,$newCount @@",
            lines = lines,
        )
        cursor = hunkEnd
    }
    return hunks
}

/**
 * "350ms" under a second, "1.3s" above it, "1m 30s" past a minute — the
 * same shape as the turn footer (audit-100 Fase 6a, 66-F6). Seconds round
 * to one decimal instead of truncating (1999ms → "2.0s", not "1.9s"), and
 * a value that rounds up to 60 seconds promotes to minutes ("59.9s" can
 * never become the confusing "60.0s").
 */
internal fun formatStepDuration(millis: Long): String {
    if (millis < 1000) return "${millis}ms"
    val seconds = millis / 1000.0
    if (seconds < 59.95) {
        return "${String.format(java.util.Locale.US, "%.1f", seconds)}s"
    }
    val totalSeconds = (millis + 500) / 1000
    return "${totalSeconds / 60}m ${totalSeconds % 60}s"
}

/** Elapsed time of a running step, never negative — a clock that jumped backwards must not show "-3ms". */
internal fun liveElapsedMillis(startedAtMillis: Long, nowMillis: Long): Long =
    maxOf(0L, nowMillis - startedAtMillis)

/**
 * Concise unified diff for one edit/write step: the same added/removed
 * row styling as the full-screen diff viewer, collapsed to the first
 * [DIFF_COLLAPSED_LINES] lines with a "Show full diff" toggle for long
 * changes, and a Copy button that copies the whole patch.
 */
@Composable
private fun InlineDiffBlock(
    hunks: List<DiffHunk>,
    additions: Int,
    deletions: Int,
    modifier: Modifier = Modifier,
    /** #6f: called before the show-full toggle changes this block's
     * height, so the caller can anchor the scroll position first. */
    onBeforeHeightChange: () -> Unit = {},
) {
    val colors = OpenCodeTheme.colors
    val motion = OpenCodeTheme.tokens.motion
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var showFull by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    val showFullRotation by animateFloatAsState(
        targetValue = if (showFull) 90f else 0f,
        animationSpec = motion.tweenQuick(),
        label = "tool_diff_expand_rotation",
    )

    val allLines = remember(hunks) { hunks.flatMap { it.lines } }
    val visibleLines = if (showFull) allLines else allLines.take(DIFF_COLLAPSED_LINES)
    val patchText = remember(hunks) {
        buildString {
            hunks.forEach { hunk ->
                appendLine(hunk.header)
                hunk.lines.forEach { line ->
                    val marker = when (line.kind) {
                        DiffLine.Kind.ADDED -> "+"
                        DiffLine.Kind.REMOVED -> "-"
                        else -> " "
                    }
                    appendLine(marker + line.text)
                }
            }
        }.trimEnd()
    }
    val shape = RoundedCornerShape(radius.sm)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.background)
            .border(0.5.dp, colors.hairline, shape),
    ) {
        // Header: one status dot, "Changes", "+n −m", Copy.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant.copy(alpha = 0.4f))
                .border(
                    width = 0.5.dp,
                    color = colors.hairline,
                    shape = RoundedCornerShape(topStart = radius.sm, topEnd = radius.sm),
                )
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(colors.activity, CircleShape),
            )
            Spacer(Modifier.width(spacing.sm))
            Text(
                text = stringResource(R.string.tool_diff_changes),
                style = OpenCodeTypography.caption.copy(
                    fontFamily = OpenCodeTypography.mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.onSurfaceMuted,
            )
            Text(
                text = "· +$additions −$deletions",
                style = OpenCodeTypography.caption.copy(
                    fontFamily = OpenCodeTypography.mono,
                    fontSize = 10.sp,
                ),
                color = colors.onSurfaceFaint,
                modifier = Modifier.padding(start = spacing.xs),
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(radius.xs))
                    .clickable(role = Role.Button) {
                        // 65-F4: the check only flips on a real copy.
                        if (setClipboardText(clipboardManager, AnnotatedString(patchText))) copied = true
                    }
                    // 47-F7: same 48dp minimum touch target as the output
                    // copy button above.
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = spacing.xs, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                GlyphIcon(
                    glyph = if (copied) Glyph.Check else Glyph.Copy,
                    tint = if (copied) colors.success else colors.onSurfaceMuted,
                    size = 11.dp,
                )
                Text(
                    text = if (copied) stringResource(R.string.code_copied) else stringResource(R.string.code_copy),
                    style = OpenCodeTypography.caption.copy(fontSize = 10.sp),
                    color = if (copied) colors.success else colors.onSurfaceMuted,
                )
            }
        }

        // Diff rows: marker + tinted background, same colours as DiffScreen.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (showFull) 320.dp else Dp.Infinity)
                .verticalScroll(rememberScrollState())
                .padding(vertical = spacing.xxs),
        ) {
            visibleLines.forEach { line ->
                val (bg, markerColor, marker) = when (line.kind) {
                    DiffLine.Kind.ADDED -> Triple(colors.diffAddedBg, colors.diffAdded, "+")
                    DiffLine.Kind.REMOVED -> Triple(colors.diffRemovedBg, colors.diffRemoved, "−")
                    else -> Triple(Color.Transparent, colors.onSurfaceFaint, " ")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = marker,
                        style = OpenCodeTypography.code.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                        color = markerColor,
                        modifier = Modifier
                            .width(16.dp)
                            .padding(start = spacing.xs),
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = line.text.ifEmpty { " " },
                            style = OpenCodeTypography.code.copy(fontSize = 11.5.sp, lineHeight = 16.5.sp),
                            color = colors.onSurface,
                            softWrap = false,
                            maxLines = 1,
                            modifier = Modifier.padding(end = spacing.md),
                        )
                    }
                }
            }
        }

        // Long diffs collapse behind one toggle instead of pushing the
        // rest of the turn off-screen.
        if (allLines.size > DIFF_COLLAPSED_LINES) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // #6f: anchor the scroll position before the height
                    // toggle, so the list does not jump.
                    .clickable(role = Role.Button) { onBeforeHeightChange(); showFull = !showFull }
                    .padding(horizontal = spacing.md, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                GlyphIcon(
                    glyph = Glyph.ChevronRight,
                    tint = colors.onSurfaceMuted,
                    size = 10.dp,
                    modifier = Modifier.graphicsLayer { rotationZ = showFullRotation },
                )
                Text(
                    text = stringResource(
                        if (showFull) R.string.tool_diff_show_less else R.string.tool_diff_show_full,
                    ),
                    style = OpenCodeTypography.caption.copy(fontSize = 10.sp),
                    color = colors.onSurfaceMuted,
                )
            }
        }
    }
}

/**
 * Accept / reject row under one finished edit's diff (Fase 1, item 2).
 *
 * Two 48dp buttons in the permission prompt's language — Accept in the
 * one accent, Reject in the soft danger tint — plus one caption line
 * saying what each does, because neither verb is self-explanatory once
 * the edit is already on disk. Once a decision is tapped the row is
 * spent (like the permission prompt): a double-tap must not send the
 * revert request twice, and the decided state stays visible as the
 * step's verdict instead of flipping back to two live buttons.
 */
@Composable
private fun DiffDecisionRow(
    decision: DiffDecision,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    /** No verified file path, so there is nothing honest to ask a revert of (audit-70 70-F03). */
    showReject: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val decided = decision != DiffDecision.NONE

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.tool_diff_decision_note),
            style = OpenCodeTypography.caption.copy(fontSize = 11.sp),
            color = colors.onSurfaceMuted,
        )
        if (decided) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radius.sm))
                    .background(
                        if (decision == DiffDecision.ACCEPTED) colors.toolSuccess.copy(alpha = 0.08f)
                        else colors.danger.copy(alpha = 0.08f),
                    )
                    .border(
                        0.5.dp,
                        if (decision == DiffDecision.ACCEPTED) colors.toolSuccess.copy(alpha = 0.25f)
                        else colors.danger.copy(alpha = 0.25f),
                        RoundedCornerShape(radius.sm),
                    )
                    .padding(horizontal = spacing.sm, vertical = spacing.xs + 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                GlyphIcon(
                    glyph = if (decision == DiffDecision.ACCEPTED) Glyph.Check else Glyph.Close,
                    tint = if (decision == DiffDecision.ACCEPTED) colors.toolSuccess else colors.danger,
                    size = 12.dp,
                )
                Text(
                    text = stringResource(
                        if (decision == DiffDecision.ACCEPTED) R.string.tool_diff_accepted
                        else R.string.tool_diff_rejected,
                    ),
                    style = OpenCodeTypography.caption.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = if (decision == DiffDecision.ACCEPTED) colors.toolSuccess else colors.danger,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                PrimaryButton(
                    label = stringResource(R.string.tool_diff_accept),
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                )
                if (showReject) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(radius.sm))
                            .background(colors.danger.copy(alpha = 0.14f))
                            .border(0.5.dp, colors.danger.copy(alpha = 0.22f), RoundedCornerShape(radius.sm))
                            .clickable(role = Role.Button, onClick = onReject)
                            .padding(horizontal = spacing.xs, vertical = spacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.tool_diff_reject),
                            style = OpenCodeTypography.label,
                            color = colors.danger,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
