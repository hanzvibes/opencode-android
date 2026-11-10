package dev.bidzz.opencode.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay

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
) {
    if (tools.isEmpty()) return

    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val shape = RoundedCornerShape(radius.md)

    var open by rememberSaveable { mutableStateOf(streaming) }

    val running = tools.filter { it.status == "running" || it.status == "streaming" }
    val failed = tools.count { it.status == "error" }

    // When a run finishes with a failure, open it so an error is never
    // hidden behind the collapsed summary. Never force a run closed: if
    // the user opened a clean run mid-stream to watch its output, it
    // stays open.
    LaunchedEffect(streaming, failed) { if (!streaming && failed > 0) open = true }

    val visible = if (open) tools else (running + tools.filter { it.status == "error" }).distinctBy { it.id }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceSunken)
            .border(
                width = 0.5.dp,
                color = if (running.isNotEmpty()) colors.accent.copy(alpha = 0.4f) else colors.hairline,
                shape = shape,
            ),
    ) {
        // Summary toggle header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = !open }
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
                    glyph = if (open) Glyph.ChevronDown else Glyph.ChevronRight,
                    tint = colors.onSurfaceFaint,
                    size = 12.dp,
                )
            }

            GlyphIcon(
                glyph = Glyph.Terminal,
                tint = if (running.isNotEmpty()) colors.accent else colors.onSurfaceMuted,
                size = 13.dp,
                modifier = Modifier.padding(start = spacing.xxs),
            )

            val summaryText = if (running.isNotEmpty()) {
                val currentTool = running.firstOrNull()
                val toolName = currentTool?.name?.takeIf { it.isNotBlank() }
                if (toolName != null) {
                    "Running $toolName…"
                } else {
                    stringResource(
                        if (tools.size == 1) R.string.tool_steps_one else R.string.tool_steps_other,
                        tools.size,
                    )
                }
            } else {
                "Ran " + stringResource(
                    if (tools.size == 1) R.string.tool_steps_one else R.string.tool_steps_other,
                    tools.size,
                )
            }

            Text(
                text = summaryText,
                style = OpenCodeTypography.label.copy(
                    fontFamily = OpenCodeTypography.mono,
                    fontSize = 12.sp,
                    fontWeight = if (running.isNotEmpty()) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (running.isNotEmpty()) colors.accent else colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(start = spacing.xs),
            )

            Spacer(Modifier.weight(1f))

            if (failed > 0) {
                StatusChip(
                    text = stringResource(R.string.tool_failed, failed).removePrefix("· ").trim(),
                    color = colors.toolError,
                )
            }
            if (running.isNotEmpty()) {
                if (failed > 0) Spacer(Modifier.width(spacing.xs))
                StatusChip(
                    text = stringResource(R.string.tool_working).removePrefix("· ").trim(),
                    color = colors.toolWarning,
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
            val completed = tools.count { it.status == "completed" }
            val lastCompleted = tools.lastOrNull { it.status == "completed" }
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

        // Vertical timeline of steps
        if (visible.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = spacing.sm, end = spacing.sm, bottom = spacing.sm, top = spacing.xxs),
            ) {
                visible.forEachIndexed { index, call ->
                    ToolStep(
                        call = call,
                        isFirst = index == 0,
                        isLast = index == visible.lastIndex,
                    )
                }
            }
        }
    }
}

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
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    var open by rememberSaveable(call.id) { mutableStateOf(false) }

    val active = call.status == "running" || call.status == "streaming"
    val isError = call.status == "error"
    val dotColor = when {
        call.status == "completed" -> colors.toolSuccess
        isError -> colors.toolError
        else -> colors.toolRunning
    }

    val toolKind = toolKindFor(call.name)
    val badgeColor = badgeColorForKind(toolKind, colors)
    val label = stepLabel(call.name, call.summary, call.status)
    val verbText = label.verbText()
    val hasDetails = !call.inputText.isNullOrBlank() || !call.output.isNullOrBlank() || !call.error.isNullOrBlank()

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
                    val strokeWidth = if (isError) 2.dp.toPx() else 1.dp.toPx()
                    val railColor = if (isError) colors.danger.copy(alpha = 0.6f) else colors.hairline

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
                    LivePulseIndicator(color = colors.accent, size = 6.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(if (isError) 8.dp else 6.dp)
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
                            active -> colors.accent.copy(alpha = 0.5f)
                            else -> colors.hairline
                        },
                        shape = RoundedCornerShape(radius.sm),
                    ),
            ) {
                // Main terminal execution row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = hasDetails) { open = !open }
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
                                .weight(1f, fill = false)
                                .padding(start = spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "$ ",
                                style = OpenCodeTypography.codeInline.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = colors.accent,
                            )
                            Text(
                                text = firstLine,
                                style = OpenCodeTypography.codeInline.copy(
                                    fontSize = 11.5.sp,
                                ),
                                color = if (active) colors.accent else colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (active) {
                                BlinkingCursor(
                                    color = colors.accent,
                                    modifier = Modifier.padding(start = spacing.xxs),
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(start = spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (verbText.isNotEmpty()) {
                                Text(
                                    text = verbText,
                                    style = OpenCodeTypography.label.copy(fontSize = 11.sp),
                                    color = if (active) colors.accent else if (isError) colors.danger else colors.onSurfaceMuted,
                                )
                            }
                            if (label.target.isNotEmpty()) {
                                Text(
                                    text = label.target,
                                    style = OpenCodeTypography.codeInline.copy(fontSize = 11.5.sp),
                                    color = if (active) colors.accent else colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = spacing.xs),
                                )
                            }
                            if (active) {
                                BlinkingCursor(
                                    color = colors.accent,
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
                                        .background(colors.accent.copy(alpha = 0.12f))
                                        .padding(horizontal = spacing.xs, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    LivePulseIndicator(color = colors.accent, size = 4.5.dp)
                                    Text(
                                        text = stringResource(R.string.tool_status_running),
                                        style = OpenCodeTypography.caption.copy(
                                            fontFamily = OpenCodeTypography.mono,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        color = colors.accent,
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
                                        color = colors.danger,
                                    )
                                }
                            }
                            call.status == "completed" -> {
                                val out = call.output
                                val summaryText = if (!out.isNullOrBlank()) {
                                    val lines = out.lines().size
                                    if (lines > 1) {
                                        stringResource(R.string.tool_lines, lines)
                                    } else {
                                        stringResource(R.string.tool_lines_one, 1)
                                    }
                                } else {
                                    stringResource(R.string.tool_status_success)
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

                        if (hasDetails) {
                            GlyphIcon(
                                glyph = if (open) Glyph.ChevronDown else Glyph.ChevronRight,
                                tint = colors.onSurfaceFaint,
                                size = 10.dp,
                            )
                        }
                    }
                }

                // Expanded Terminal Console Windows
                AnimatedVisibility(
                    visible = open,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
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

                        // 1. Input/Command terminal block
                        if (!inputBody.isNullOrBlank()) {
                            val inputLang = if (isShell) "bash" else if (inputBody.trim().startsWith("{")) "json" else null
                            val prompt = if (isShell) "$ " else null
                            TerminalConsoleBlock(
                                title = if (isShell) stringResource(R.string.tool_command) else stringResource(R.string.tool_input),
                                body = inputBody,
                                language = inputLang,
                                promptPrefix = prompt,
                                subtitle = if (isShell) "bash" else null,
                            )
                        }

                        // 2. Output STDOUT block
                        call.output?.takeIf { it.isNotBlank() }?.let { out ->
                            val truncated = if (out.length > MAX_OUTPUT) out.take(MAX_OUTPUT) + "\n… [truncated]" else out
                            val outLang = detectOutputLanguage(out, isShell)
                            val lineCount = out.lines().size
                            val lineSubtitle = if (lineCount > 1) {
                                stringResource(R.string.tool_lines, lineCount)
                            } else {
                                stringResource(R.string.tool_lines_one, 1)
                            }

                            TerminalConsoleBlock(
                                title = stringResource(R.string.tool_stdout),
                                body = truncated,
                                language = outLang,
                                subtitle = lineSubtitle,
                            )
                        }

                        // 3. Error STDERR block
                        call.error?.takeIf { it.isNotBlank() }?.let { err ->
                            TerminalConsoleBlock(
                                title = stringResource(R.string.tool_stderr),
                                body = err,
                                isError = true,
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
 * - 3-dot window decoration bar (red/yellow/green terminal dots)
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
) {
    val colors = OpenCodeTheme.colors
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

    val highlightedText = rememberSyntaxHighlightedText(
        language = language,
        text = body,
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
            // High-tech terminal window dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(if (isError) colors.danger else Color(0xFFFF5F56), CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(if (isError) colors.danger.copy(alpha = 0.5f) else Color(0xFFFFBD2E), CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(if (isError) colors.danger.copy(alpha = 0.25f) else Color(0xFF27C93F), CircleShape),
                )
            }

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
                    .clickable {
                        clipboardManager.setText(AnnotatedString(body))
                        copied = true
                    }
                    .padding(horizontal = spacing.xs, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                GlyphIcon(
                    glyph = if (copied) Glyph.Check else Glyph.Copy,
                    tint = if (copied) colors.success else colors.onSurfaceFaint,
                    size = 11.dp,
                )
                Text(
                    text = if (copied) stringResource(R.string.code_copied) else stringResource(R.string.code_copy),
                    style = OpenCodeTypography.caption.copy(fontSize = 10.sp),
                    color = if (copied) colors.success else colors.onSurfaceFaint,
                )
            }
        }

        // Terminal Content Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState())
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
                        color = colors.accent,
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
    ToolKind.SHELL -> colors.accent
    ToolKind.READ -> colors.success
    ToolKind.WRITE -> colors.warning
    ToolKind.EDIT -> colors.warning
    ToolKind.SEARCH -> colors.syntaxKeyword
    ToolKind.LIST -> colors.syntaxNumber
    ToolKind.FETCH -> colors.accent
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
