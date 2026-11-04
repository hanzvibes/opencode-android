package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
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
    var open by rememberSaveable { mutableStateOf(streaming) }

    val running = tools.filter { it.status == "running" || it.status == "streaming" }
    val failed = tools.count { it.status == "error" }

    LaunchedEffect(streaming) { if (!streaming) open = false }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (open) "▾" else "▸",
                style = OpenCodeTypography.label,
                color = colors.onSurfaceFaint,
            )
            Text(
                text = stringResource(
                    if (tools.size == 1) R.string.tool_steps_one else R.string.tool_steps_other,
                    tools.size,
                ),
                style = OpenCodeTypography.label,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(start = spacing.sm),
            )
            if (failed > 0) {
                Text(
                    text = stringResource(R.string.tool_failed, failed),
                    style = OpenCodeTypography.label,
                    color = colors.danger,
                    modifier = Modifier.padding(start = spacing.sm),
                )
            }
            if (running.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.tool_working),
                    style = OpenCodeTypography.label,
                    color = colors.warning,
                    modifier = Modifier.padding(start = spacing.sm),
                )
            }
        }

        // A vertical timeline: a dot per step on a rail, joined by a line to the next.
        val visible = if (open) tools else running + tools.filter { it.status == "error" }
        visible.forEachIndexed { index, call ->
            ToolStep(call, isLast = index == visible.lastIndex)
        }
    }
}

/**
 * One step on the timeline: status dot on the rail, name and input gist beside it.
 * The step currently running reads in the accent colour; tap toggles input and output.
 */
@Composable
fun ToolStep(call: ToolCall, isLast: Boolean, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    var open by rememberSaveable(call.id) { mutableStateOf(false) }

    val active = call.status == "running" || call.status == "streaming"
    val dot = when {
        call.status == "completed" -> colors.success
        call.status == "error" -> colors.danger
        else -> colors.accent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable { open = !open },
    ) {
        Column(
            modifier = Modifier.width(20.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .padding(top = spacing.sm)
                    .size(if (active) 10.dp else 8.dp)
                    .background(dot, CircleShape),
            )
            if (!isLast) {
                Box(
                    Modifier
                        .padding(top = spacing.xs)
                        .width(1.5.dp)
                        .weight(1f)
                        // `outline` is a hairline colour and vanishes at this width; the faint
                        // text colour keeps the rail readable in both themes.
                        .background(colors.onSurfaceFaint.copy(alpha = 0.45f)),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.sm, bottom = spacing.sm),
        ) {
            val label = stepLabel(call.name, call.summary, call.status)
            Row(
                modifier = Modifier.padding(top = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(label.glyph, tint = if (active) colors.accent else colors.onSurfaceMuted, size = 14.dp)
                Text(
                    text = label.text(),
                    style = OpenCodeTypography.bodySmall,
                    color = if (active) colors.accent else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = spacing.sm),
                )
            }
            if (open) {
                call.inputText?.let { Detail(stringResource(R.string.tool_input).uppercase(), it) }
                call.output?.let { Detail(stringResource(R.string.tool_output).uppercase(), it.take(MAX_OUTPUT)) }
                call.error?.let { Detail(stringResource(R.string.tool_error).uppercase(), it, colors.danger) }
            }
        }
    }
}

@Composable
private fun Detail(
    label: String,
    body: String,
    color: androidx.compose.ui.graphics.Color = OpenCodeTheme.colors.onSurface,
) {
    val spacing = OpenCodeTheme.tokens.spacing
    Text(
        text = label,
        style = OpenCodeTypography.label,
        color = OpenCodeTheme.colors.onSurfaceFaint,
        modifier = Modifier.padding(top = spacing.sm),
    )
    Text(text = body, style = OpenCodeTypography.code, color = color)
}

private const val MAX_OUTPUT = 4000
