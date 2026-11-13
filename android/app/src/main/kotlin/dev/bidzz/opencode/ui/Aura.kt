package dev.bidzz.opencode.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.AuraPalette
import dev.bidzz.opencode.core.design.AuraShadow
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Aura components — clean-room re-implementation of the Meta Muse app's
 * (`com.facebook.aura` 16.0.0.2.159) `Hatch*` UI, from its dex class
 * names (`HatchSurface`, `HatchSection`, `HatchMeshGradient`,
 * `HatchPullToRefresh`) and the extracted token values in [AuraPalette].
 * No Aura code, font, or asset is copied: the shapes are drawn from our
 * own tokens, in Inter / JetBrains Mono.
 *
 * The global theme stays Muse B (see Palette.kt): these components are
 * the Aura *variant* — a card / section / gradient a screen opts into —
 * not a second theme. The one-blue rule survives: Aura blue is used for
 * dots, fills, gradient stops and the refresh ring only, never as body
 * text ([AuraPalette.auraBlue] is 3.07:1 on white).
 */

/**
 * Draws an [AuraShadow] as two stacked shadows, one per layer.
 * The ambient layer first (small blur), then the key layer — the same
 * order as the CSS the values came from.
 */
fun Modifier.auraShadow(shadow: AuraShadow, shape: Shape): Modifier =
    this
        .shadow(
            elevation = shadow.ambient.elevation,
            shape = shape,
            ambientColor = shadow.ambient.ambientColor,
            spotColor = shadow.ambient.spotColor,
        )
        .shadow(
            elevation = shadow.key.elevation,
            shape = shape,
            ambientColor = shadow.key.ambientColor,
            spotColor = shadow.key.spotColor,
        )

/**
 * The Aura card treatment ([HatchSurface] in Aura).
 *
 * Light: 16dp radius + 1dp [AuraPalette.auraBorder] edge + the two-layer
 * [AuraShadowTokens.card] shadow on a pure-white card, so it lifts off
 * an Aura-grey page. Dark: the flat grouped treatment — raised surface,
 * hairline edge, no shadow — so the same call site renders correctly in
 * both themes without branching on `colors.isDark` itself.
 * Compare [whiteCard]: same shape, Muse B border (#EBEBEB) and the
 * collapsed single-elevation shadow.
 */
@Composable
fun Modifier.auraCard(): Modifier {
    val colors = OpenCodeTheme.colors
    val tokens = OpenCodeTheme.tokens
    val shape = RoundedCornerShape(tokens.radius.lg)
    return if (colors.isDark) {
        clip(shape)
            .background(colors.surface)
            .border(tokens.dimensions.hairline, colors.hairline, shape)
    } else {
        auraShadow(tokens.auraShadow.card, shape)
            .clip(shape)
            .background(colors.surface)
            .border(tokens.shadow.cardBorderWidth, AuraPalette.auraBorder, shape)
    }
}

/** A floating Aura card: [auraCard] + 16dp inner padding. */
@Composable
fun HatchSurface(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .auraCard()
            .padding(contentPadding),
    ) {
        content()
    }
}

/**
 * An Aura section ([HatchSection] in Aura): a sentence-case label in
 * faint text above a [HatchSurface]. The label is the section's name,
 * never a heading — sections are labelled, not shouted.
 */
@Composable
fun HatchSection(
    title: String,
    modifier: Modifier = Modifier,
    /**
     * Inner padding of the card. Settings rows carry their own 12dp
     * horizontal padding, so Settings passes 0.dp here; a section of
     * free text keeps the default 16.dp.
     */
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = OpenCodeTypography.labelSmall,
            color = colors.onSurfaceFaint,
            modifier = Modifier.padding(
                start = spacing.sm,
                bottom = spacing.sm,
            ),
        )
        HatchSurface(contentPadding = contentPadding) {
            Column {
                content()
            }
        }
    }
}

/**
 * The Aura mesh gradient ([HatchMeshGradient] in Aura): blue → purple →
 * light blue, drawn as a linear gradient with a soft white radial
 * highlight at the top-start corner. Decorative only — never behind
 * text smaller than a section title.
 */
@Composable
fun AuraMeshGradient(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        AuraPalette.auraBlue,
                        AuraPalette.auraPurple,
                        AuraPalette.auraBlueLight,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        center = Offset(0f, 0f),
                        radius = 600f,
                    ),
                ),
        )
    }
}

/**
 * Aura's pull-to-refresh / loading ring ([HatchPullToRefresh] in Aura):
 * a 24dp ring in Aura blue, 2.5dp stroke, drawn on canvas — not the M3
 * spinner. [progress] is 0..1 while pulling (a partial arc); null means
 * refreshing, and the arc rotates once per [Motion.pulsePeriodMs]
 * (1000ms, the app's one pulse period) until it stops.
 */
@Composable
fun AuraPullRefreshIndicator(
    progress: Float?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val refreshing = progress == null
    // The transition is created unconditionally: creating it only while
    // refreshing would change the composable call order when `progress`
    // flips between null and a value. When not refreshing the angle is
    // simply ignored (rotation 0).
    val transition = rememberInfiniteTransition(label = "auraRefreshRotation")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = OpenCodeTheme.tokens.motion.pulsePeriodMs,
                easing = LinearEasing,
            ),
        ),
        label = "auraRefreshAngle",
    )
    val rotation = if (refreshing) angle else 0f
    val sweep = if (refreshing) 270f else 30f + (progress ?: 0f).coerceIn(0f, 1f) * 240f
    Box(
        modifier = modifier
            .size(size)
            .rotate(rotation),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawArc(
                color = AuraPalette.auraBlue,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}
