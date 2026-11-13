package dev.bidzz.opencode.ui.chat

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.ModelRef
import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.TurnActionPill
import dev.bidzz.opencode.ui.formatCompactTokens
import dev.bidzz.opencode.ui.announceForAccessibility
import dev.bidzz.opencode.ui.browser.shareText
import dev.bidzz.opencode.ui.copyPlainText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Result of parsing potential thinking/reasoning blocks out of an assistant message.
 */
internal data class ParsedThinking(
    val reasoning: String?,
    val answer: String,
    val isActivelyStreamingThoughts: Boolean,
)

/**
 * Extracts explicit or inline thinking blocks (<think>...</think>) from message content.
 */
internal fun parseThinkingContent(
    markdown: String,
    explicitReasoning: String?,
): ParsedThinking {
    val trimmed = markdown.trim()
    if (trimmed.startsWith("<think>")) {
        if (trimmed.contains("</think>")) {
            val thought = trimmed.substringAfter("<think>").substringBefore("</think>").trim()
            val remaining = trimmed.substringAfter("</think>").trim()
            val combined = listOfNotNull(explicitReasoning?.trim(), thought)
                .filter { it.isNotEmpty() }
                .joinToString("\n\n")
            return ParsedThinking(
                reasoning = combined.takeIf { it.isNotEmpty() },
                answer = remaining,
                isActivelyStreamingThoughts = false,
            )
        } else {
            val thought = trimmed.substringAfter("<think>").trim()
            val combined = listOfNotNull(explicitReasoning?.trim(), thought)
                .filter { it.isNotEmpty() }
                .joinToString("\n\n")
            return ParsedThinking(
                reasoning = combined.takeIf { it.isNotEmpty() },
                answer = "",
                isActivelyStreamingThoughts = true,
            )
        }
    }

    return ParsedThinking(
        reasoning = explicitReasoning?.trim()?.takeIf { it.isNotEmpty() },
        answer = markdown,
        isActivelyStreamingThoughts = false,
    )
}

/**
 * One chat message styled after modern LLM interfaces (ChatGPT / Claude):
 * - User message: compact neutral bubble on the right with asymmetric rounded corners.
 * - Assistant message: clean full-width markdown with soft glowing "Thinking" disclosure widget,
 *   smooth transition to streaming answer, branded provenance tag badge, and tool traces.
 * - Error message: tinted warning surface with danger accent and readability hierarchy.
 */
@Composable
fun MessageBubble(
    role: Role,
    markdown: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    tools: List<ToolCall> = emptyList(),
    /** Mode and model that produced this reply, when known. Empty when absent. */
    provenance: String? = null,
    showProvenance: Boolean = true,
    model: ModelRef? = null,
    reasoning: String? = null,
    parts: List<ChatTurnPart> = emptyList(),
    onAnswerQuestion: suspend (QuestionAnswer) -> Boolean = { false },
    /**
     * Seconds since this run started, owned by the screen — not by this
     * bubble. A bubble-local timer reset to 0 every time the streaming
     * flag toggled, so "Thought for 12s" could jump back to "Thinking…"
     * mid-run; the screen's timer runs from busy false→true and survives
     * the placeholder→turn transition. Since Phase 2 the screen passes each
     * turn's own stored [thinkingSeconds] (see [ChatMessage]) and only
     * falls back to the live timer while that turn streams — null keeps
     * the old local timer.
     */
    thinkingElapsedSeconds: Int? = null,
    /** Local delivery state (C-B): true once an assistant turn follows
     * this user message. A local read receipt, not a server status. */
    delivered: Boolean = false,
    /** Engine `time.created` of this turn, for the tool-trace start time (C-C). */
    toolsStartedAtMillis: Long? = null,
    /** Quote this turn into the composer (C-E). Receives the "> quote" text. */
    onQuote: (String) -> Unit = {},
    /** Turn footer (Phase 2, finding 6): usage/cost/duration/stop-reason for
     * this assistant turn. These are the exact nullable fields the
     * integration agent adds to [ChatMessage] and fills from the engine's
     * `tokens`/`cost`/`finish` — null until then, and the footer hides
     * entirely while all four are null, so nothing breaks before that lands. */
    usageTokens: Long? = null,
    usageCostUsd: Double? = null,
    turnDurationMs: Long? = null,
    stopReason: String? = null,
    /** Edit/resend affordances (Phase 2, finding 3): the callbacks the
     * ViewModel wires to the composer's editing mode (edit — the edited text
     * is re-sent as a new prompt, the transcript is never rewritten) and to
     * re-sending the message's text (resend). Null hides the pill — the
     * behaviour itself lives in the ViewModel, not here. */
    onEdit: (() -> Unit)? = null,
    onResend: (() -> Unit)? = null,
    /** Per-tool durations in milliseconds, keyed by tool id (Phase 2,
     * finding 20): client-side stamps from the ViewModel. Empty until a
     * tool is seen live; a finished step without one shows no duration. */
    toolDurations: Map<String, Long> = emptyMap(),
    /** Workspace path tool file paths resolve against (Fase 1, item 3). */
    workspacePath: String? = null,
    /** Opens a workspace-relative file from a tool step (Fase 1, item 3). */
    onOpenToolFile: (String) -> Unit = {},
    /** Asks the agent to revert a rejected edit (Fase 1, item 2). */
    onRequestDiffRevert: (String, String?) -> Unit = { _, _ -> },
    /** #6f: called before a ToolTrace height toggle (expand/collapse,
     * show-full output/diff), so the transcript can anchor the scroll
     * position first and the list does not jump. */
    onBeforeToolTraceHeightChange: () -> Unit = {},
    /** Saves a code block through the system picker (Fase 1, item 4). */
    onSaveCode: (String, String) -> Unit = { _, _ -> },
    /** Per-tool start stamps in epoch millis, keyed by tool id (Fase 2,
     * item 5): the same client-side stamps the durations come from, so a
     * still-running step can show a live elapsed time. */
    toolStartedAtMillis: Map<String, Long> = emptyMap(),
    /** Engine `time.created` of this turn, epoch millis (Opsi A): shown
     * as a short clock time beside the turn's actions — the user pill row
     * and the assistant header row — so each turn carries its own time.
     * Null hides the clock; the date divider already covers the day. */
    createdAtMillis: Long? = null,
    /**
     * The engine's error for a turn that still produced partial content
     * (01-F4): rendered under the turn so a run that died mid-turn does
     * not look complete. Null when the turn has no error; a turn with no
     * content at all renders as a [Role.ERROR] bubble instead.
     */
    errorText: String? = null,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions
    val context = LocalContext.current

    when (role) {
        Role.USER -> BoxWithConstraints(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            // Pill row scroll (PR #9 follow-up): the row under the bubble
            // (checkmark + Copy + Edit + Resend) is wider than a narrow
            // screen once the pills size to their labels, and a plain Row
            // clips the rightmost pill off the edge. The row scrolls
            // horizontally instead, and starts scrolled to the end so the
            // Resend pill — the one that was clipped — is visible first.
            // The effect re-runs when the scrollable range changes (pills
            // appearing), which also re-anchors the end after rotation.
            val userPillScrollState = rememberScrollState()
            LaunchedEffect(userPillScrollState.maxValue) {
                userPillScrollState.scrollTo(userPillScrollState.maxValue)
            }
            val maxBubbleWidth = (maxWidth * dims.userBubbleMaxWidthFraction).coerceAtMost(600.dp)
            val userBubbleShape = RoundedCornerShape(
                topStart = radius.bubble,
                topEnd = radius.bubble,
                bottomStart = radius.bubble,
                bottomEnd = radius.xs,
            )
            Column(horizontalAlignment = Alignment.End) {
                // White B: the bubble stays neutral (userBubble is the
                // raised surface in both themes, Phase 1 §7 — never blue),
                // so on a #F9F9FA page a white bubble reads as a bubble
                // by edge + shadow, not colour: the whiteCard treatment
                // (card shadow + 1dp cardBorder, ui/Cards.kt), applied
                // with this bubble's own asymmetric shape so the tail
                // corner survives — whiteCard() itself would force a
                // symmetric 16dp radius. Dark keeps the flat hairline
                // edge with no shadow, exactly like whiteCard().
                // Text stays onUserBubble (onSurface) on the white.
                val cardShadow = OpenCodeTheme.tokens.shadow.card
                val userBubbleEdge: Modifier = if (colors.isDark) {
                    Modifier
                        .clip(userBubbleShape)
                        .background(colors.userBubble)
                        .border(
                            OpenCodeTheme.tokens.dimensions.hairline,
                            colors.hairline,
                            userBubbleShape,
                        )
                } else {
                    Modifier
                        .shadow(
                            elevation = cardShadow.elevation,
                            shape = userBubbleShape,
                            ambientColor = cardShadow.ambientColor,
                            spotColor = cardShadow.spotColor,
                        )
                        .clip(userBubbleShape)
                        .background(colors.userBubble)
                        .border(
                            OpenCodeTheme.tokens.shadow.cardBorderWidth,
                            colors.cardBorder,
                            userBubbleShape,
                        )
                }
                Box(
                    modifier = Modifier
                        .padding(start = dims.minTouchTarget)
                        .widthIn(max = maxBubbleWidth)
                        .then(userBubbleEdge)
                        .padding(horizontal = spacing.lg, vertical = spacing.md - 2.dp),
                ) {
                    // The bubble is neutral now (surfaceRaised, Phase 1 §7),
                    // so selection is accent-tinted with accent handles in
                    // both themes — the old white-on-blue selection is
                    // unreadable on a neutral bubble. Assistant text keeps
                    // the default.
                    // The message renders as markdown (bold, code, links)
                    // in the bubble-aware variant: prose stays onUserBubble
                    // on the neutral bubble, links stay tappable, and the
                    // bubble colour itself is untouched.
                    CompositionLocalProvider(
                        LocalTextSelectionColors provides TextSelectionColors(
                            handleColor = colors.accent,
                            backgroundColor = colors.accent.copy(alpha = 0.22f),
                        ),
                    ) {
                        MarkdownText(
                            markdown = markdown,
                            textColor = colors.onUserBubble,
                            onSaveCode = onSaveCode,
                        )
                    }
                }
                // Same copy affordance as assistant replies, right-aligned
                // under the user's own bubble, next to the delivery mark:
                // one check while unanswered, two once an assistant turn
                // follows (C-B). Local only — the engine reports no status.
                // Edit/Resend (Phase 2, finding 3, UI half) sit in the same
                // row, in the same quiet pill style, and only appear once
                // the integration agent wires their callbacks — the pills
                // call back out, they do not implement editing or re-running.
                if (markdown.isNotBlank()) {
                    // Right-aligned under the bubble: the scrollable Row
                    // used to size to its content and sit at the START of
                    // the column, so on a wide tablet "20:28 ✓✓ Copy Edit
                    // Resend" floated at the left edge, detached from the
                    // right-aligned bubble. fillMaxWidth + End arrangement
                    // pins the group flush with the bubble's right edge
                    // when it fits; when it overflows, the scroll-to-end
                    // effect above still shows the Resend pill first.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.xs)
                            .horizontalScroll(userPillScrollState),
                    ) {
                        // Opsi A: the turn's own clock time, mono, ahead of
                        // the delivery mark — same row as Copy/Edit/Resend.
                        if (createdAtMillis != null) {
                            Text(
                                text = formatClock(createdAtMillis),
                                style = OpenCodeTypography.caption.copy(
                                    fontFamily = OpenCodeTypography.mono,
                                    fontSize = 11.sp,
                                ),
                                color = colors.onSurfaceFaint,
                                maxLines = 1,
                            )
                        }
                        DeliveryCheckmark(delivered = delivered)
                        CopyButton(text = markdown)
                        if (onEdit != null) {
                            TurnActionPill(
                                label = stringResource(R.string.message_edit),
                                glyph = Glyph.Pencil,
                                onClick = onEdit,
                            )
                        }
                        if (onResend != null) {
                            TurnActionPill(
                                label = stringResource(R.string.message_resend),
                                glyph = Glyph.Refresh,
                                onClick = onResend,
                            )
                        }
                    }
                }
            }
        }

        Role.ASSISTANT -> Row(modifier = modifier.fillMaxWidth()) {
            // Redesign C (Klasik + Avatar): every assistant turn gets the
            // small logomark avatar on the left and its content in a grey
            // bubble — the classic chat shape. The avatar is the same
            // logomark the provenance badge already uses (no new asset);
            // the bubble is the assistantBubble token (grey #EFEFF1 in
            // light, raised surface in dark). The tool timeline, diffs
            // and thinking disclosure stay INSIDE the bubble: they are
            // part of the turn, not floating elements. Copy/Reply and
            // the turn footer stay outside, under the bubble, like the
            // actions under a message in Muse/Claude.
            AssistantAvatar(
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.width(spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                // Tail at the top-start corner, next to the avatar — the
                // mirror of the user bubble's tail at the bottom-end.
                val assistantBubbleShape = RoundedCornerShape(
                    topStart = radius.xs,
                    topEnd = radius.bubble,
                    bottomStart = radius.bubble,
                    bottomEnd = radius.bubble,
                )
                val effectiveParts = remember(parts, markdown, tools, reasoning, streaming) {
                    val built = if (parts.isNotEmpty()) parts
                    else buildTurnParts(
                        content = emptyList(),
                        streamingText = markdown,
                        isStreaming = streaming,
                        liveTools = tools,
                        explicitReasoning = reasoning,
                        fallbackText = markdown,
                    )
                    // One trace card per turn (Phase 2, finding 20): a text
                    // block between two tool calls splits the turn into several
                    // ToolGroups, and each one used to render its own ToolTrace.
                    // The groups collapse here — prose order is untouched.
                    mergeToolGroups(built)
                }

                val hasReasoning = effectiveParts.any { it is ChatTurnPart.Reasoning }
                val isThinkingActive = effectiveParts.any { it is ChatTurnPart.Reasoning && it.isActivelyStreaming }

                // Same scroll guard as the user row (PR #9 follow-up): Copy +
                // Reply cannot clip on a narrow turn. Left-aligned, so no
                // scroll-to-end — the row starts at Copy as before.
                val assistantPillScrollState = rememberScrollState()

                var elapsedSeconds by remember(streaming) { mutableIntStateOf(0) }
                var recordedDuration by remember { mutableIntStateOf(0) }

                LaunchedEffect(isThinkingActive) {
                    if (isThinkingActive) {
                        val start = System.currentTimeMillis()
                        while (isActive) {
                            // Rounded to the nearest second (66-F8), like the
                            // recorded thinking time: truncation understated
                            // every live timer by up to a second.
                            val current = ((System.currentTimeMillis() - start + 500) / 1000).toInt()
                            elapsedSeconds = maxOf(1, current)
                            delay(500)
                        }
                    } else if (elapsedSeconds > 0) {
                        recordedDuration = elapsedSeconds
                    }
                }

                // The screen-owned timer wins when provided; the local one is
                // only a fallback for callers that do not pass it.
                val finalDuration = thinkingElapsedSeconds
                    ?: if (recordedDuration > 0) recordedDuration else elapsedSeconds
                val hasTextContent = effectiveParts.any { it is ChatTurnPart.Text && it.markdown.isNotBlank() }
                val showStreamingSpinner = streaming && hasTextContent

                // The bubble is flat — no shadow, just a 0.5dp hairline
                // edge — so it reads as a message bubble, not a floating
                // card; the white user bubble keeps the card shadow.
                // Bottom padding comes from the parts themselves: every
                // part carries bottom sm, so the bubble needs none.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(assistantBubbleShape)
                        .background(colors.assistantBubble)
                        .border(0.5.dp, colors.hairline, assistantBubbleShape)
                        .padding(start = spacing.md, end = spacing.md, top = spacing.sm),
                ) {
                    Column {
                        // 1. Author Identity Header: Branded provenance badge
                        // While streaming with no identity yet, the badge stays hidden
                        // instead of showing "OpenCode" and then jumping to
                        // "Build · model" once the identity arrives — one appearance,
                        // not a label swap. A finished turn with no identity still
                        // falls back to "OpenCode".
                        val identityKnown = !provenance.isNullOrBlank()
                        // Opsi A turn header: identity badge on the left, the turn's
                        // own clock time on the right (mono, faint). A consecutive
                        // assistant turn has no badge — it is the same turn continuing,
                        // so its time already showed on the first part — and the
                        // streaming row carries the clock instead.
                        if (showProvenance && (identityKnown || !streaming)) {
                            val labelText = if (identityKnown) provenance else "OpenCode"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = spacing.sm),
                            ) {
                                ProvenanceBadge(
                                    provenance = labelText,
                                    modelRef = model,
                                    streaming = showStreamingSpinner,
                                )
                                if (createdAtMillis != null) {
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = formatClock(createdAtMillis),
                                        style = OpenCodeTypography.caption.copy(
                                            fontFamily = OpenCodeTypography.mono,
                                            fontSize = 11.sp,
                                        ),
                                        color = colors.onSurfaceFaint,
                                        maxLines = 1,
                                    )
                                }
                            }
                        } else if (showStreamingSpinner) {
                            // No provenance badge on a consecutive assistant turn, so the
                            // streaming state needs its own marker. The old 8dp spinner
                            // here was practically invisible; an 11dp pulse plus a
                            // "Typing…" label reads at a glance.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = spacing.xs),
                            ) {
                                StreamingTypingRow()
                                if (createdAtMillis != null) {
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = formatClock(createdAtMillis),
                                        style = OpenCodeTypography.caption.copy(
                                            fontFamily = OpenCodeTypography.mono,
                                            fontSize = 11.sp,
                                        ),
                                        color = colors.onSurfaceFaint,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }

                        // The last text block of a streaming turn gets the blinking cursor.
                        val lastTextIndex = effectiveParts.indexOfLast { it is ChatTurnPart.Text }

                        // 2. Chronological turn content: Interleaved Reasoning, Tools, Questions, and Markdown Text
                        effectiveParts.forEachIndexed { partIndex, part ->
                            when (part) {
                                is ChatTurnPart.Reasoning -> {
                                    ThinkingDisclosure(
                                        isThinkingActive = isThinkingActive,
                                        reasoningText = part.text,
                                        elapsedSeconds = finalDuration,
                                        modifier = Modifier.padding(bottom = spacing.sm),
                                    )
                                }

                                is ChatTurnPart.ToolGroup -> {
                                    if (part.tools.isNotEmpty()) {
                                        ToolTrace(
                                            tools = part.tools,
                                            streaming = part.streaming,
                                            startedAtMillis = toolsStartedAtMillis,
                                            toolDurations = toolDurations,
                                            toolStartedAtMillis = toolStartedAtMillis,
                                            workspacePath = workspacePath,
                                            onOpenToolFile = onOpenToolFile,
                                            onRequestDiffRevert = onRequestDiffRevert,
                                            onBeforeHeightChange = onBeforeToolTraceHeightChange,
                                            modifier = Modifier.padding(bottom = spacing.sm),
                                        )
                                    }
                                }

                                is ChatTurnPart.Question -> {
                                    InlineQuestionCard(
                                        call = part.call,
                                        questions = part.questions,
                                        onAnswer = onAnswerQuestion,
                                        modifier = Modifier.padding(bottom = spacing.sm),
                                    )
                                }

                                is ChatTurnPart.Text -> {
                                    if (part.markdown.isNotBlank()) {
                                        AnimatedVisibility(
                                            visible = true,
                                            enter = fadeIn(OpenCodeTheme.tokens.motion.tweenStandard()) +
                                                expandVertically(OpenCodeTheme.tokens.motion.tweenStandard()),
                                            modifier = Modifier.padding(bottom = spacing.sm),
                                        ) {
                                            MarkdownText(
                                                markdown = part.markdown,
                                                streaming = streaming && partIndex == lastTextIndex,
                                                onSaveCode = onSaveCode,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
                // 3. Response Actions: Quiet copy button when complete, plus
                // Reply, which quotes a line of this turn into the composer
                // (C-E). Long-press quoting is deliberately not added: the
                // text is already selectable, and a long-press gesture would
                // fight text selection.
                val allProse = effectiveParts
                    .filterIsInstance<ChatTurnPart.Text>()
                    .map { it.markdown }
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                    .ifEmpty { markdown }

                // #6c: Copy/Reply appear as soon as the turn has prose to
                // copy — not only when it ends. A turn that is all tools so
                // far has no prose, so the pills stay hidden until the first
                // text part lands. Share sits in this row now, not below
                // the footer: as a standalone pill it wrapped onto its own
                // line with a gap (screenshot: Copy/Reply on one row,
                // Share alone on the next).
                if (allProse.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        modifier = Modifier
                            .padding(top = spacing.xs)
                            .horizontalScroll(assistantPillScrollState),
                    ) {
                        CopyButton(text = allProse)
                        TurnActionPill(
                            label = stringResource(R.string.message_reply),
                            glyph = Glyph.ArrowLeft,
                            onClick = {
                                val flat = allProse.replace("\n", " ").trim()
                                val snippet = flat.take(120)
                                    .let { if (flat.length > 120) "$it…" else it }
                                onQuote("> $snippet")
                            },
                        )
                        TurnActionPill(
                            label = stringResource(R.string.message_share),
                            glyph = Glyph.Share,
                            onClick = {
                                // 65-F6: a failed chooser used to do nothing at all.
                                if (!shareText(context, allProse)) {
                                    (context.applicationContext as OpenCodeApplication).showSnackbar(
                                        context.getString(R.string.browser_share_failed),
                                    )
                                }
                            },
                        )
                    }
                }

                // 4. Turn footer (Phase 2, finding 6, UI half): mono usage line
                // under the turn — "12,4k tokens · 8,2s · $0,02" plus the stop
                // reason. Hidden entirely while every field is null, so a turn
                // renders exactly as before until the integration agent fills
                // the fields from the engine.
                // #6c: the usage line still waits for the turn to end — its
                // values only exist then. Share joins Copy/Reply above: it
                // needs prose, not a finished turn.
                if (!streaming) {
                    TurnFooter(
                        usageTokens = usageTokens,
                        usageCostUsd = usageCostUsd,
                        turnDurationMs = turnDurationMs,
                        stopReason = stopReason,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }

                // A run that died after partial content (01-F4): the turn
                // keeps its answer, and the error is named under it
                // instead of silently dropped — without this the partial
                // answer looks complete.
                if (!errorText.isNullOrBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(top = spacing.xs),
                    ) {
                        GlyphIcon(
                            glyph = Glyph.Info,
                            tint = colors.danger,
                            size = dims.iconSmall,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                        Text(
                            text = errorText,
                            style = OpenCodeTypography.bodySmall,
                            color = colors.danger,
                        )
                    }
                }
            }
        }

        Role.ERROR -> BoxWithConstraints(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            val maxBubbleWidth = (maxWidth * dims.userBubbleMaxWidthFraction).coerceAtMost(600.dp)
            val errorShape = RoundedCornerShape(radius.md)
            Box(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .clip(errorShape)
                    .background(colors.surfaceSunken)
                    .background(colors.danger.copy(alpha = 0.08f))
                    .border(0.5.dp, colors.danger.copy(alpha = 0.35f), errorShape)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    GlyphIcon(
                        glyph = Glyph.Info,
                        tint = colors.danger,
                        size = dims.iconSmall,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        SelectionContainer {
                            Text(
                                // A failed run with no message and no error
                                // type (54-F7): the view model leaves the
                                // text blank rather than hardcoding English,
                                // and the bubble shows the sentence in the
                                // app language instead of an empty red box.
                                text = markdown.ifBlank {
                                    stringResource(R.string.chat_agent_failed)
                                },
                                style = OpenCodeTypography.bodySmall,
                                color = colors.danger,
                            )
                        }
                        // Rate limit (Phase 3): the free-tier 429 surfaces
                        // here as an assistant error bubble, never as a
                        // banner failure, so the banner's typed retry never
                        // sees it. The bubble shows the same switch-model /
                        // wait guidance the banner shows for RATE_LIMITED
                        // (chat_rate_limited_guidance). There is no retry
                        // button yet — the bubble has no path to the model
                        // picker or the view model (see isRateLimitError),
                        // so the guidance is the fix and the affordance is
                        // documented as remaining work.
                        if (isRateLimitError(markdown)) {
                            Text(
                                text = stringResource(R.string.chat_rate_limited_guidance),
                                style = OpenCodeTypography.bodySmall,
                                color = colors.onSurfaceMuted,
                                modifier = Modifier.padding(top = spacing.xs),
                            )
                        }
                        if (!provenance.isNullOrBlank()) {
                            ProvenanceBadge(
                                provenance = provenance,
                                modifier = Modifier.padding(top = spacing.xs),
                            )
                        }
                        // Turn footer (66-F3): a failed turn still spent
                        // tokens — the bubble shows them instead of hiding
                        // the cost of the failure. TurnFooter hides itself
                        // while every field is null, so an error with no
                        // usage renders exactly as before.
                        TurnFooter(
                            usageTokens = usageTokens,
                            usageCostUsd = usageCostUsd,
                            turnDurationMs = turnDurationMs,
                            stopReason = stopReason,
                            modifier = Modifier.padding(top = spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated ChatGPT / Claude style collapsible thinking disclosure widget:
 * - Soft glowing / pulsing pill with spinner and elapsed time & step count while thinking.
 * - Smooth transition from active pulsating state to quiet "Thought for Xs" collapsed pill.
 * - Tap to expand/collapse full reasoning transcript with accent rail guide and sunken surface.
 */
@Composable
fun ThinkingDisclosure(
    isThinkingActive: Boolean,
    reasoningText: String?,
    elapsedSeconds: Int,
    /**
     * #6b: a label that replaces the "Thinking…" pill while the run is
     * blocked on something else (a permission answer). Used as-is, with
     * no elapsed-time suffix — the wait is not thinking time.
     */
    activeLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val colors = OpenCodeTheme.colors
    val motion = OpenCodeTheme.tokens.motion
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val hasContent = !reasoningText.isNullOrBlank()

    // Auto-expand while thinking, collapse when done (Phase 2, finding 7):
    // the reasoning is the live activity while the model thinks, and a
    // quiet "Thought for Xs" pill once it has answered. The effect only
    // fires on the transition, so tapping to collapse mid-thought still
    // works until the next transition.
    LaunchedEffect(isThinkingActive) {
        isExpanded = isThinkingActive
    }

    // Breathing soft glow animation while actively thinking, on the one
    // live pulse period (motion.pulsePeriodMs). The infinite transition
    // only exists while thinking: a finished "Thought for Xs" pill must
    // not keep an animation running behind a static border.
    val pulseAlpha = if (isThinkingActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "thinking_pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(motion.pulsePeriodMs, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse_alpha",
        )
        alpha
    } else {
        0.95f
    }

    // One glyph rotating 0° (right, collapsed) ↔ 90° (down, expanded)
    // instead of two glyphs swapping on every tap.
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = motion.tweenQuick(),
        label = "thinking_chevron_rotation",
    )

    val pillShape = RoundedCornerShape(radius.full)

    val borderColor = if (isThinkingActive) {
        colors.accent.copy(alpha = pulseAlpha)
    } else {
        colors.hairline
    }
    val backgroundColor = if (isThinkingActive) {
        colors.surfaceVariant.copy(alpha = 0.85f)
    } else {
        colors.surfaceVariant.copy(alpha = 0.45f)
    }

    Column(modifier = modifier.animateContentSize(animationSpec = motion.springSmooth())) {
        Box(
            modifier = Modifier
                .clip(pillShape)
                .background(backgroundColor)
                .border(
                    width = if (isThinkingActive) 1.dp else 0.5.dp,
                    color = borderColor,
                    shape = pillShape,
                )
                .then(
                    if (hasContent) {
                        Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            isExpanded = !isExpanded
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = spacing.md, vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs),
            ) {
                if (isThinkingActive) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = colors.accent,
                    )
                } else {
                    ThoughtSparkleIcon(
                        tint = colors.onSurfaceFaint,
                        size = 12.dp,
                    )
                }

                val timeSuffix = if (elapsedSeconds > 0) " (${elapsedSeconds}s)" else "…"

                val labelText = if (isThinkingActive) {
                    activeLabel
                        ?: stringResource(R.string.bubble_thinking).removeSuffix("…").trim() + timeSuffix
                } else {
                    val duration = maxOf(1, elapsedSeconds)
                    stringResource(R.string.bubble_thought_for, duration)
                }

                Text(
                    text = labelText,
                    style = OpenCodeTypography.labelSmall.copy(
                        fontWeight = if (isThinkingActive) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (isThinkingActive) colors.onSurface else colors.onSurfaceMuted,
                )

                if (hasContent) {
                    GlyphIcon(
                        glyph = Glyph.ChevronRight,
                        tint = colors.onSurfaceFaint,
                        size = 11.dp,
                        modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            }
        }

        // Collapsible reasoning text drawer
        AnimatedVisibility(
            visible = isExpanded && hasContent,
            enter = fadeIn(motion.tweenStandard()) + expandVertically(motion.tweenStandard()),
            exit = fadeOut(motion.tweenQuick()) + shrinkVertically(motion.tweenStandard()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.xs)
                    .clip(RoundedCornerShape(radius.sm))
                    .background(colors.surfaceSunken)
                    .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.sm))
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(colors.accent.copy(alpha = 0.45f), RoundedCornerShape(radius.full)),
                    )
                    SelectionContainer {
                        Text(
                            text = reasoningText.orEmpty(),
                            style = OpenCodeTypography.bodySmall,
                            color = colors.onSurfaceMuted,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Thought sparkle icon for the finished thinking state.
 */
@Composable
private fun ThoughtSparkleIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val path = Path().apply {
            moveTo(12f * u, 3f * u)
            quadraticBezierTo(12f * u, 12f * u, 21f * u, 12f * u)
            quadraticBezierTo(12f * u, 12f * u, 12f * u, 21f * u)
            quadraticBezierTo(12f * u, 12f * u, 3f * u, 12f * u)
            quadraticBezierTo(12f * u, 12f * u, 12f * u, 3f * u)
            close()
        }
        drawPath(path, tint)
    }
}

/**
 * Redesign C avatar: the logomark the provenance badge already uses,
 * in a 28dp circle (tokens.dimensions.avatarSize) beside the assistant
 * bubble. The logomark PNG is an opaque black rounded square, so the
 * circle clip crops it into a black disc with the white mark — it reads
 * in both themes, and the hairline border defines its edge on the dark
 * page. Decorative: the badge inside the bubble still carries the
 * provider identity, so TalkBack gets nothing here.
 *
 * Internal (not private): ChatScreen's working placeholder wears the
 * same avatar so the placeholder→turn transition does not pop.
 */
@Composable
internal fun AssistantAvatar(
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val dims = OpenCodeTheme.tokens.dimensions
    Box(
        modifier = modifier
            .size(dims.avatarSize)
            .clip(CircleShape)
            .border(0.5.dp, colors.hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logomark),
            contentDescription = null,
            modifier = Modifier.size(dims.avatarSize),
        )
    }
}

/**
 * Delivery mark under a user bubble (C-B): one faint check while the
 * message is unanswered, two green checks once an assistant turn follows
 * it. Local only — the engine reports no delivery status.
 */
@Composable
private fun DeliveryCheckmark(
    delivered: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val motion = OpenCodeTheme.tokens.motion
    val label = stringResource(
        if (delivered) R.string.message_delivered else R.string.message_sent,
    )
    Box(
        modifier = modifier.semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = delivered,
            transitionSpec = {
                (fadeIn(animationSpec = motion.tweenQuick()) +
                    scaleIn(initialScale = 0.7f, animationSpec = motion.tweenQuick())) togetherWith
                    (fadeOut(animationSpec = motion.tweenQuick()) +
                        scaleOut(targetScale = 0.7f, animationSpec = motion.tweenQuick()))
            },
            label = "delivery_checkmark",
        ) { isDelivered ->
            if (isDelivered) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-4).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(glyph = Glyph.Check, tint = colors.success, size = 12.dp)
                    GlyphIcon(glyph = Glyph.Check, tint = colors.success, size = 12.dp)
                }
            } else {
                GlyphIcon(glyph = Glyph.Check, tint = colors.onSurfaceFaint, size = 12.dp)
            }
        }
    }
}

/**
 * Subtle tonal pill with tap ripple, icon, and ephemeral "Copied" feedback.
 * The shell is the shared [TurnActionPill] (animated pill colours passed
 * in); the label crossfade is gone — the label swaps instantly — the
 * animated colours and the TalkBack announcement survive.
 */
@Composable
private fun CopyButton(
    text: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = OpenCodeTheme.colors
    val motion = OpenCodeTheme.tokens.motion
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    val pillBg by animateColorAsState(
        targetValue = if (copied) colors.success.copy(alpha = 0.12f) else colors.surfaceVariant,
        animationSpec = motion.tweenQuick(),
        label = "copy_pill_bg",
    )
    val pillBorder by animateColorAsState(
        targetValue = if (copied) colors.success.copy(alpha = 0.4f) else colors.hairline,
        animationSpec = motion.tweenQuick(),
        label = "copy_pill_border",
    )
    val contentColor by animateColorAsState(
        targetValue = if (copied) colors.success else colors.onSurfaceMuted,
        animationSpec = motion.tweenQuick(),
        label = "copy_pill_content",
    )
    val copiedLabel = stringResource(R.string.code_copied)
    val copyFailedLabel = stringResource(R.string.copy_failed)

    // Copying announces "Copied" once, on the tap — never on recomposition.
    // The check only flips on a real copy (65-F4): a missing clipboard
    // service or a failed set used to show "Copied" anyway.
    TurnActionPill(
        label = stringResource(if (copied) R.string.code_copied else R.string.code_copy),
        glyph = Glyph.Copy,
        onClick = {
            if (copyPlainText(context, "OpenCode", text)) {
                copied = true
                announceForAccessibility(context, copiedLabel)
            } else {
                announceForAccessibility(context, copyFailedLabel)
            }
        },
        modifier = modifier,
        background = pillBg,
        border = pillBorder,
        contentColor = contentColor,
    )
}

enum class Role { USER, ASSISTANT, ERROR }

/**
 * Mono usage footer under a finished assistant turn (Phase 2, finding 6):
 * "12.4k tokens · 8.2s · $0.02 · stop: length". One segment per known
 * field, joined with " · "; a field that is null contributes nothing, and
 * when every field is null the footer is not rendered at all.
 */
@Composable
private fun TurnFooter(
    usageTokens: Long?,
    usageCostUsd: Double?,
    turnDurationMs: Long?,
    stopReason: String?,
    modifier: Modifier = Modifier,
) {
    val segments = buildList {
        if (usageTokens != null) {
            add(stringResource(R.string.turn_footer_tokens, formatCompactTokens(usageTokens)))
        }
        if (turnDurationMs != null) add(formatStepDuration(turnDurationMs))
        if (usageCostUsd != null) add(formatTurnCost(usageCostUsd))
        // A plain "stop" means the turn ended normally and says nothing —
        // showing it reads as the redundant "stop: stop". Only a real
        // reason (length, error, ...) earns a segment.
        if (!stopReason.isNullOrBlank() && !stopReason.equals("stop", ignoreCase = true)) {
            add(stringResource(R.string.turn_footer_stop_reason, stopReason))
        }
    }
    if (segments.isEmpty()) return

    Text(
        text = segments.joinToString(" · "),
        style = OpenCodeTypography.caption.copy(
            fontFamily = OpenCodeTypography.mono,
            fontSize = 11.sp,
        ),
        color = OpenCodeTheme.colors.onSurfaceFaint,
        modifier = modifier,
    )
}

/** "$0.02" — two decimals, US formatting so the "$" amount never renders with a comma decimal. Under a cent reads "<$0.01", never "$0.00": a sub-cent turn is cheap, not free (66-F4), matching the session list. */
internal fun formatTurnCost(costUsd: Double): String =
    if (costUsd < 0.01) "<$0.01" else "$" + String.format(java.util.Locale.US, "%.2f", costUsd)

/**
 * Whether an assistant error bubble reports the free-tier rate limit.
 *
 * The engine reports it as error type `provider.free-limit` (HTTP 429);
 * the bubble is rendered from `error.message ?: error.type`, so the type
 * usually IS the text, and a 429 message carries the code or the words
 * "rate limit". Matched case-insensitively on the rendered text because
 * the bubble only ever receives that text — the error type itself is
 * dropped before it reaches this composable (ChatViewModel's toUiMessages
 * keeps only the text on ChatMessage). That is also why a typed retry
 * cannot live here yet: it would need the error type preserved on
 * ChatMessage, a bubble-originated retry kind in the view model, and a
 * model-picker opener plumbed from the composer through ChatScreen.
 */
internal fun isRateLimitError(text: String): Boolean {
    val lower = text.lowercase()
    return lower.contains("free-limit") || lower.contains("free limit") ||
        lower.contains("rate limit") || lower.contains("rate_limit") ||
        lower.contains("429")
}
