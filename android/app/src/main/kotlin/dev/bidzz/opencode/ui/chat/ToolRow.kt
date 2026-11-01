package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

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
                text = "${tools.size} ${if (tools.size == 1) "step" else "steps"}",
                style = OpenCodeTypography.label,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(start = spacing.sm),
            )
            if (failed > 0) {
                Text(
                    text = "· $failed failed",
                    style = OpenCodeTypography.label,
                    color = colors.danger,
                    modifier = Modifier.padding(start = spacing.sm),
                )
            }
            if (running.isNotEmpty()) {
                Text(
                    text = "· working",
                    style = OpenCodeTypography.label,
                    color = colors.warning,
                    modifier = Modifier.padding(start = spacing.sm),
                )
            }
        }

        if (open) {
            tools.forEach { ToolRow(it, Modifier.padding(top = spacing.xs)) }
        } else {
            (running + tools.filter { it.status == "error" })
                .forEach { ToolRow(it, Modifier.padding(top = spacing.xs)) }
        }
    }
}

/** One-line tool call: status dot, name, input gist. Tap toggles input and output. */
@Composable
fun ToolRow(call: ToolCall, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    var open by rememberSaveable(call.id) { mutableStateOf(false) }

    val dot = when (call.status) {
        "completed" -> colors.success
        "error" -> colors.danger
        else -> colors.warning
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceSunken, RoundedCornerShape(radius.sm))
            .clickable { open = !open }
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Text(
                text = call.name.ifEmpty { "tool" },
                style = OpenCodeTypography.label,
                color = colors.onSurface,
                modifier = Modifier.padding(start = spacing.sm),
            )
            Text(
                text = call.summary,
                style = OpenCodeTypography.codeInline,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = spacing.sm),
            )
        }
        if (open) {
            call.inputText?.let { Detail("INPUT", it) }
            call.output?.let { Detail("OUTPUT", it.take(MAX_OUTPUT)) }
            call.error?.let { Detail("ERROR", it, colors.danger) }
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
