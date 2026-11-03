package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon

/**
 * Message input.
 *
 * One rounded container: the text on top, and underneath it a row with the session
 * controls on the left and send on the right. Mode and model are decided right before
 * typing, so they sit with the input instead of floating as a separate strip above it.
 *
 * The field grows with its content up to a cap, then scrolls, so a long prompt does not
 * push the conversation off screen. Send becomes stop while the agent is working, in the
 * same position, because stopping is what the user wants at that moment.
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
    controls: @Composable () -> Unit = {},
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions

    val canSend = enabled && value.isNotBlank() && !busy
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.sm)
            .background(colors.surface, shape)
            .border(0.5.dp, colors.outline, shape)
            .padding(start = spacing.lg, end = spacing.sm, top = spacing.md, bottom = spacing.sm),
    ) {
        // `decorationBox` rather than a sibling Text: the field itself must own the
        // placeholder and the layout, otherwise it collapses to zero height and the IME
        // connection never attaches to it.
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = OpenCodeTypography.input.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.accent),
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = dims.composerMaxHeight),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        // While the agent runs the button is Stop and send() refuses, so the
                        // field only accumulates a draft for after the run.
                        Text(
                            text = when {
                                !enabled -> "Engine is not ready"
                                busy -> "Agent is working…"
                                else -> "Ask anything about this project"
                            },
                            style = OpenCodeTypography.input,
                            color = colors.onSurfaceFaint,
                        )
                    }
                    inner()
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { controls() }
            ActionButton(
                busy = busy,
                canSend = canSend,
                onClick = { if (busy) onStop() else onSend() },
            )
        }
    }
}

@Composable
private fun ActionButton(busy: Boolean, canSend: Boolean, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val active = busy || canSend

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            // The strongest neutral of the theme: near-black on light, near-white on dark.
            .background(if (active) colors.onSurface else colors.surfaceSunken)
            .semantics { contentDescription = if (busy) "Stop" else "Send" }
            .clickable(enabled = active, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            // A filled square reads as "stop" without needing an icon.
            Box(
                Modifier
                    .size(11.dp)
                    .background(colors.background, RoundedCornerShape(2.dp)),
            )
        } else {
            GlyphIcon(
                glyph = Glyph.ArrowUp,
                tint = if (canSend) colors.background else colors.onSurfaceFaint,
                size = 20.dp,
            )
        }
    }
}
