package dev.bidzz.opencode.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Shared streaming indicators, moved out of ToolRow.kt so the chat transcript
 * can reuse them: the tool timeline, the streaming text cursor, and the
 * "typing" row all read as one live state instead of three different ones.
 *
 * Nothing here animates the text itself — the streaming text is the feedback.
 * These are the small, legible markers around it.
 */

/**
 * Radar / beacon animated live pulse indicator for running commands.
 */
@Composable
fun LivePulseIndicator(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 6.dp,
    /** False renders a static dot — the infinite transition is not created. */
    active: Boolean = true,
) {
    val motion = OpenCodeTheme.tokens.motion
    // The one live pulse period (1000ms); the transition only exists while active.
    val scale: Float
    val alpha: Float
    if (active) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        scale = infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 2.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = motion.pulsePeriodMs, easing = motion.easingStandard),
                repeatMode = RepeatMode.Restart,
            ),
            label = "pulseScale",
        ).value
        alpha = infiniteTransition.animateFloat(
            initialValue = 0.65f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = motion.pulsePeriodMs, easing = motion.easingStandard),
                repeatMode = RepeatMode.Restart,
            ),
            label = "pulseAlpha",
        ).value
    } else {
        scale = 1f
        alpha = 0.65f
    }

    Box(
        modifier = modifier.size(size * 2),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .background(color, CircleShape),
        )
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape),
        )
    }
}

/**
 * Monospace blinking terminal block cursor.
 *
 * [height] defaults to the 12dp used on tool rows; pass 15dp when it sits
 * after body text so it lines up with the 15sp / 22sp prose line height.
 */
@Composable
fun BlinkingCursor(
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    /** False renders a solid cursor — the infinite transition is not created. */
    active: Boolean = true,
) {
    val motion = OpenCodeTheme.tokens.motion
    // Sole exception to the live pulse period: the terminal cursor blink.
    val alpha: Float
    if (active) {
        val infiniteTransition = rememberInfiniteTransition(label = "cursor")
        alpha = infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = motion.cursorBlinkPeriodMs, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "cursorAlpha",
        ).value
    } else {
        alpha = 1f
    }
    Box(
        modifier = modifier
            .width(5.dp)
            .height(height)
            .graphicsLayer { this.alpha = alpha }
            .background(color, RoundedCornerShape(1.dp)),
    )
}

/**
 * The visible "assistant is typing" row, used when there is no provenance
 * badge to carry the streaming state (a consecutive assistant turn).
 *
 * Deliberately larger than the old 8dp spinner it replaces: an 11dp pulse
 * plus a short label, so "still typing" reads at a glance instead of
 * looking like a stray dot.
 */
@Composable
fun StreamingTypingRow(
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        LivePulseIndicator(
            color = colors.accent,
            size = dims.streamingIndicatorSize,
        )
        Text(
            text = stringResource(R.string.streaming_typing),
            style = OpenCodeTypography.caption,
            color = colors.accentText,
            modifier = Modifier.padding(start = spacing.xxs),
        )
    }
}
