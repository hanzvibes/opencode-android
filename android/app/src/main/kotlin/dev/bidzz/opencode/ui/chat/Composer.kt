package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Message input.
 *
 * Grows with content up to a cap, then scrolls, so a long prompt does not push the
 * conversation off screen. The send button becomes a stop button while the agent
 * is working — the same affordance, one position, because stopping is what the user
 * wants at that moment.
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    busy: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions

    val canSend = enabled && value.isNotBlank() && !busy

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(colors.surfaceSunken, RoundedCornerShape(radius.lg))
                .border(0.5.dp, colors.outline, RoundedCornerShape(radius.lg))
                .padding(horizontal = spacing.md, vertical = spacing.sm)
                .heightIn(min = dims.minTouchTarget - spacing.sm, max = dims.composerMaxHeight),
        ) {
            // `decorationBox` rather than a sibling Text: the field itself must own
            // the placeholder and the layout, otherwise it collapses to zero height
            // and the IME connection never attaches to it.
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = OpenCodeTypography.input.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.accent),
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        // The placeholder must not swallow taps: it sits behind the
                        // field's own text area, and `inner()` is drawn on top.
                        // While the agent runs, the button is Stop and send() refuses,
                        // so the field only accumulates a draft for after the run.
                        Text(
                            text = when {
                                !enabled -> "Engine is not ready"
                                busy -> "Agent is working…"
                                else -> "Ask anything about this project"
                            },
                            style = OpenCodeTypography.input,
                            color = colors.onSurfaceFaint,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    inner()
                },
            )
        }

        ActionButton(
            busy = busy,
            canSend = canSend,
            onClick = { if (busy) onStop() else onSend() },
        )
    }
}

@Composable
private fun ActionButton(busy: Boolean, canSend: Boolean, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val dims = OpenCodeTheme.tokens.dimensions
    val active = busy || canSend

    Box(
        modifier = Modifier
            .size(dims.minTouchTarget)
            .clip(CircleShape)
            .background(if (active) colors.accent else colors.surfaceSunken)
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            // A filled square reads as "stop" without needing an icon font.
            Box(
                Modifier
                    .size(12.dp)
                    .background(colors.onAccent, RoundedCornerShape(2.dp)),
            )
        } else {
            Text(
                text = "↑",
                style = OpenCodeTypography.title.copy(fontSize = 20.sp),
                color = if (canSend) colors.onAccent else colors.onSurfaceFaint,
                textAlign = TextAlign.Center,
            )
        }
    }
}
