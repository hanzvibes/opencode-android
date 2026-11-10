package dev.bidzz.opencode.ui.chat

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon

/**
 * The whole tool trace for one message, collapsed to a single summary line.
 *
 * A real coding run calls fifteen to thirty tools. Rendering every one as a
 * permanent row buried the answer under a wall of log, so the trace is folded and
 * the operator opens it on demand. Two things stay visible while it is folded:
 * the step currently running, so progress is still legible, and any failure, so a
 * red dot is never hidden behind a tap.
 *
 * It starts open while the message streams and closes itself when the run ends —
 * watch it work, then read what it produced.
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

    LaunchedEffect(streaming) { if (!streaming) open = false }

    val visible = if (open) tools else (running + tools.filter { it.status == "error" }).distinctBy { it.id }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceSunken)
            .border(0.5.dp, colors.hairline, shape),
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

            Text(
                text = stringResource(
                    if (tools.size == 1) R.string.tool_steps_one else R.string.tool_steps_other,
                    tools.size,
                ),
                style = OpenCodeTypography.label,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(start = spacing.xs),
            )

            Spacer(Modifier.weight(1f))

            if (failed > 0) {
                StatusChip(
                    text = stringResource(R.string.tool_failed, failed).removePrefix("· ").trim(),
                    color = colors.danger,
                )
            }
            if (running.isNotEmpty()) {
                if (failed > 0) Spacer(Modifier.width(spacing.xs))
                StatusChip(
                    text = stringResource(R.string.tool_working).removePrefix("· ").trim(),
                    color = colors.warning,
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
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape),
        )
        Text(
            text = text,
            style = OpenCodeTypography.label.copy(fontSize = 11.sp),
            color = color,
        )
    }
}

/**
 * One step on the timeline: status dot on the rail, verb and target beside it.
 * The step currently running reads in the accent colour; tap toggles input and output.
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
    val dot = when {
        call.status == "completed" -> colors.success
        isError -> colors.danger
        else -> colors.accent
    }

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
                .width(20.dp)
                .fillMaxHeight()
                .drawBehind {
                    val centerX = size.width / 2f
                    val dotCenterY = 12.dp.toPx()
                    val strokeWidth = 1.5.dp.toPx()
                    val railColor = colors.outline

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
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (active) {
                    Box(
                        modifier = Modifier
                            .size(15.dp)
                            .background(dot.copy(alpha = 0.2f), CircleShape),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(if (active) 9.dp else 7.dp)
                        .background(dot, CircleShape),
                )
            }
        }

        // Step content: header line and optional expanded details
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.xs, bottom = if (isLast) 0.dp else spacing.sm),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp)
                    .clip(RoundedCornerShape(radius.xs))
                    .clickable(enabled = hasDetails) { open = !open }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(
                        glyph = label.glyph,
                        tint = if (active) colors.accent else if (isError) colors.danger else colors.onSurfaceMuted,
                        size = 14.dp,
                    )

                    if (verbText.isNotEmpty()) {
                        Text(
                            text = verbText,
                            style = OpenCodeTypography.label.copy(fontSize = 12.sp),
                            color = if (active) colors.accent else if (isError) colors.danger else colors.onSurface,
                            modifier = Modifier.padding(start = spacing.xs),
                        )
                    }

                    if (label.target.isNotEmpty()) {
                        Text(
                            text = label.target,
                            style = if (verbText.isNotEmpty()) {
                                OpenCodeTypography.codeInline.copy(fontSize = 12.sp)
                            } else {
                                OpenCodeTypography.label.copy(fontSize = 12.sp)
                            },
                            color = if (verbText.isNotEmpty()) colors.onSurfaceMuted else if (active) colors.accent else colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(start = spacing.xs),
                        )
                    }
                }

                if (hasDetails) {
                    GlyphIcon(
                        glyph = if (open) Glyph.ChevronDown else Glyph.ChevronRight,
                        tint = colors.onSurfaceFaint,
                        size = 10.dp,
                        modifier = Modifier.padding(start = spacing.xs, end = spacing.xxs),
                    )
                }
            }

            if (open) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    call.inputText?.takeIf { it.isNotBlank() }?.let {
                        DetailBlock(
                            label = stringResource(R.string.tool_input),
                            body = it,
                        )
                    }
                    call.output?.takeIf { it.isNotBlank() }?.let {
                        val truncated = if (it.length > MAX_OUTPUT) it.take(MAX_OUTPUT) + "…" else it
                        DetailBlock(
                            label = stringResource(R.string.tool_output),
                            body = truncated,
                        )
                    }
                    call.error?.takeIf { it.isNotBlank() }?.let {
                        DetailBlock(
                            label = stringResource(R.string.tool_error),
                            body = it,
                            isError = true,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Monospace code/text block for tool details (input, output, error).
 */
@Composable
private fun DetailBlock(
    label: String,
    body: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val shape = RoundedCornerShape(radius.sm)

    val boxBg = if (isError) colors.danger.copy(alpha = 0.08f) else colors.background
    val boxBorder = if (isError) colors.danger.copy(alpha = 0.35f) else colors.hairline
    val textColor = if (isError) colors.danger else colors.onSurface

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            style = OpenCodeTypography.label.copy(fontSize = 10.sp),
            color = if (isError) colors.danger else colors.onSurfaceFaint,
            modifier = Modifier.padding(bottom = spacing.xxs),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(boxBg)
                .border(0.5.dp, boxBorder, shape)
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.md, vertical = spacing.sm),
        ) {
            SelectionContainer {
                Text(
                    text = body,
                    style = OpenCodeTypography.code.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = textColor,
                )
            }
        }
    }
}

private const val MAX_OUTPUT = 4000
