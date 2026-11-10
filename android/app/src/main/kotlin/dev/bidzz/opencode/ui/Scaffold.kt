package dev.bidzz.opencode.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * The frame every secondary screen shares: a top bar with a back arrow, a title and optional
 * actions, a crisp hairline bottom separator, and a body that clears the system bars.
 *
 * One place for the things that were repeated nine times and drifted apart: the back target is
 * 48dp and announced as a button with tactile feedback, the status bar and the navigation bar
 * are both respected (edge-to-edge is on), and the keyboard pushes the body up instead of covering it.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    backLabel: String? = null,
    showSeparator: Boolean = true,
    separatorAlpha: Float = 1f,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .defaultMinSize(minHeight = dims.topBarHeight)
                .padding(horizontal = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                glyph = Glyph.ArrowLeft,
                description = backLabel ?: stringResource(R.string.common_back),
                onClick = onBack,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.xs, end = spacing.sm),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = OpenCodeTypography.label,
                        color = subtitleColor ?: colors.onSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }

        if (showSeparator) {
            HairlineSeparator(alpha = separatorAlpha)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
            content = content,
        )
    }
}

/**
 * A crisp 0.5dp hairline bottom separator that can disappear seamlessly at top scroll via [alpha].
 */
@Composable
fun HairlineSeparator(
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    color: Color = OpenCodeTheme.colors.hairline,
) {
    if (alpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(OpenCodeTheme.tokens.dimensions.hairline)
                .graphicsLayer { this.alpha = alpha }
                .background(color),
        )
    }
}

/** A round icon button with the minimum 48dp touch target, tactile press feedback, and clean ripple. */
@Composable
fun IconAction(
    glyph: Glyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    rotation: Float = 0f,
) {
    val colors = OpenCodeTheme.colors
    val dims = OpenCodeTheme.tokens.dimensions
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "icon_action_scale",
    )

    Box(
        modifier = modifier
            .size(dims.minTouchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            glyph = glyph,
            tint = if (enabled) colors.onSurface else colors.onSurfaceFaint,
            size = dims.iconMedium,
            modifier = if (rotation != 0f) {
                Modifier.graphicsLayer { rotationZ = rotation }
            } else {
                Modifier
            },
        )
    }
}

/** A text button for a top bar or a row of links: accent coloured, at least 48dp touch target with tactile press. */
@Composable
fun TextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "text_action_scale",
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = dims.minTouchTarget, minHeight = dims.minTouchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.md))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.label,
            color = if (enabled) colors.accent else colors.onSurfaceFaint,
        )
    }
}
