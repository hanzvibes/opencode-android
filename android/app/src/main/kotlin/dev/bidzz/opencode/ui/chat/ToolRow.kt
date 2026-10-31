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
