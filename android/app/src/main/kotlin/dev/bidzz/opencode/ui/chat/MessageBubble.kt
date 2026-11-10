package dev.bidzz.opencode.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.ModelRef
import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
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
 * - User message: compact system blue bubble on the right with asymmetric rounded corners.
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
    onAnswerQuestion: (String) -> Unit = {},
    /**
     * Seconds since this run started, owned by the screen — not by this
     * bubble. A bubble-local timer reset to 0 every time the streaming
     * flag toggled, so "Thought for 12s" could jump back to "Thinking…"
     * mid-run; the screen's timer runs from busy false→true and survives
     * the placeholder→turn transition. Null keeps the old local timer.
     */
    thinkingElapsedSeconds: Int? = null,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions

    when (role) {
        Role.USER -> BoxWithConstraints(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            val maxBubbleWidth = (maxWidth * dims.userBubbleMaxWidthFraction).coerceAtMost(600.dp)
            val userBubbleShape = RoundedCornerShape(
                topStart = radius.bubble,
                topEnd = radius.bubble,
                bottomStart = radius.bubble,
                bottomEnd = radius.xs,
            )
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .padding(start = dims.minTouchTarget)
                        .widthIn(max = maxBubbleWidth)
                        .clip(userBubbleShape)
                        .background(colors.userBubble)
                        .padding(horizontal = spacing.lg, vertical = spacing.md - 2.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = markdown,
                            style = OpenCodeTypography.body,
                            color = colors.onUserBubble,
                        )
                    }
                }
                // Same copy affordance as assistant replies, right-aligned
                // under the user's own bubble.
                if (markdown.isNotBlank()) {
                    CopyButton(
                        text = markdown,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }
            }
        }

        Role.ASSISTANT -> Column(modifier = modifier.fillMaxWidth()) {
            val effectiveParts = remember(parts, markdown, tools, reasoning, streaming) {
                if (parts.isNotEmpty()) parts
                else buildTurnParts(
                    content = emptyList(),
                    streamingText = markdown,
                    isStreaming = streaming,
                    liveTools = tools,
                    explicitReasoning = reasoning,
                    fallbackText = markdown,
                )
            }

            val hasReasoning = effectiveParts.any { it is ChatTurnPart.Reasoning }
            val isThinkingActive = effectiveParts.any { it is ChatTurnPart.Reasoning && it.isActivelyStreaming }

            var elapsedSeconds by remember(streaming) { mutableIntStateOf(0) }
            var recordedDuration by remember { mutableIntStateOf(0) }

            LaunchedEffect(isThinkingActive) {
                if (isThinkingActive) {
                    val start = System.currentTimeMillis()
                    while (isActive) {
                        val current = ((System.currentTimeMillis() - start) / 1000).toInt()
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

            // 1. Author Identity Header: Branded provenance badge
            // While streaming with no identity yet, the badge stays hidden
            // instead of showing "OpenCode" and then jumping to
            // "Build · model" once the identity arrives — one appearance,
            // not a label swap. A finished turn with no identity still
            // falls back to "OpenCode".
            val identityKnown = !provenance.isNullOrBlank()
            if (showProvenance && (identityKnown || !streaming)) {
                val labelText = if (identityKnown) provenance else "OpenCode"
                ProvenanceBadge(
                    provenance = labelText,
                    modelRef = model,
                    streaming = showStreamingSpinner,
                    modifier = Modifier.padding(bottom = spacing.sm),
                )
            } else if (showStreamingSpinner) {
                // No provenance badge on a consecutive assistant turn, so the
                // streaming state needs its own marker. The old 8dp spinner
                // here was practically invisible; an 11dp pulse plus a
                // "Typing…" label reads at a glance.
                StreamingTypingRow(
                    modifier = Modifier.padding(bottom = spacing.xs),
                )
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
                                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                                modifier = Modifier.padding(bottom = spacing.sm),
                            ) {
                                MarkdownText(
                                    markdown = part.markdown,
                                    streaming = streaming && partIndex == lastTextIndex,
                                )
                            }
                        }
                    }
                }
            }

            // 3. Response Actions: Quiet copy button when complete
            val allProse = effectiveParts
                .filterIsInstance<ChatTurnPart.Text>()
                .map { it.markdown }
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
                .ifEmpty { markdown }

            if (!streaming && allProse.isNotBlank()) {
                CopyButton(
                    text = allProse,
                    modifier = Modifier.padding(top = spacing.xs),
                )
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
                                text = markdown,
                                style = OpenCodeTypography.bodySmall,
                                color = colors.danger,
                            )
                        }
                        if (!provenance.isNullOrBlank()) {
                            ProvenanceBadge(
                                provenance = provenance,
                                modifier = Modifier.padding(top = spacing.xs),
                            )
                        }
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
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val hasContent = !reasoningText.isNullOrBlank()

    // Breathing soft glow animation while actively thinking
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
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

    Column(modifier = modifier.animateContentSize()) {
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
                    stringResource(R.string.bubble_thinking).removeSuffix("…").trim() + timeSuffix
                } else {
                    val duration = maxOf(1, elapsedSeconds)
                    "Thought for ${duration}s"
                }

                Text(
                    text = labelText,
                    style = OpenCodeTypography.label.copy(
                        fontSize = 11.5.sp,
                        fontWeight = if (isThinkingActive) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (isThinkingActive) colors.onSurface else colors.onSurfaceMuted,
                )

                if (hasContent) {
                    GlyphIcon(
                        glyph = if (isExpanded) Glyph.ChevronDown else Glyph.ChevronRight,
                        tint = colors.onSurfaceFaint,
                        size = 11.dp,
                    )
                }
            }
        }

        // Collapsible reasoning text drawer
        AnimatedVisibility(
            visible = isExpanded && hasContent,
            enter = fadeIn(tween(180)) + expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(180)),
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
                            style = OpenCodeTypography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                            ),
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
 * Subtle tonal pill with tap ripple, icon, and ephemeral "Copied" feedback.
 */
@Composable
private fun CopyButton(
    text: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    val pillShape = RoundedCornerShape(radius.full)
    val contentColor = if (copied) colors.success else colors.onSurfaceMuted

    Box(
        modifier = modifier
            .clip(pillShape)
            .background(if (copied) colors.success.copy(alpha = 0.12f) else colors.surfaceVariant)
            .border(
                0.5.dp,
                if (copied) colors.success.copy(alpha = 0.4f) else colors.hairline,
                pillShape,
            )
            .clickable {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("OpenCode", text))
                copied = true
            }
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            GlyphIcon(
                glyph = Glyph.Copy,
                tint = contentColor,
                size = 12.dp,
            )
            Text(
                text = stringResource(if (copied) R.string.code_copied else R.string.code_copy),
                style = OpenCodeTypography.label.copy(fontSize = 11.sp),
                color = contentColor,
            )
        }
    }
}

enum class Role { USER, ASSISTANT, ERROR }
