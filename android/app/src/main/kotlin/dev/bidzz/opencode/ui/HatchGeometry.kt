package dev.bidzz.opencode.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.OpenCodeTheme

/**
 * Batch 4 (Aura full, Opsi B): floating-composer + window geometry,
 * clean-room re-type of Aura's `HatchComposerGeometry` /
 * `HatchFloatingChromeGeometry` / `HatchWindowGeometry`.
 *
 * Aura's composer floats as a card above the message list and rides the
 * keyboard: its bottom inset is the keyboard height when the keyboard
 * is open, the navigation bar otherwise, and the chrome itself is a
 * rounded card whose offset follows a drag. The geometry is kept as
 * plain data + pure functions so the numbers are testable without a
 * window: the [Composer] card is the one live call site (its padding
 * and radius now come from [HatchComposerGeometry]), and the floating
 * chrome + window helpers model the same numbers for the next floating
 * surface that needs them.
 */

/**
 * The floating composer's card geometry. Defaults equal the values
 * [Composer] already used — [spacing.md][dev.bidzz.opencode.core.design.Spacing.md]
 * horizontal, [spacing.sm][dev.bidzz.opencode.core.design.Spacing.sm]
 * vertical, [radius.lg][dev.bidzz.opencode.core.design.Radius.lg] — so
 * adopting them changes no pixel; it just gives the numbers one owner.
 * Prefer [rememberHatchComposerGeometry], which reads them from the
 * live tokens instead of the defaults.
 */
@Immutable
data class HatchComposerGeometry(
    val horizontalPadding: Dp = 12.dp,
    val verticalPadding: Dp = 8.dp,
    val cornerRadius: Dp = 16.dp,
)

/** [HatchComposerGeometry] from the live design tokens. */
@Composable
fun rememberHatchComposerGeometry(): HatchComposerGeometry {
    val tokens = OpenCodeTheme.tokens
    return remember(tokens) {
        HatchComposerGeometry(
            horizontalPadding = tokens.spacing.md,
            verticalPadding = tokens.spacing.sm,
            cornerRadius = tokens.radius.lg,
        )
    }
}

/**
 * Pure: the composer card's bottom inset in pixels. The app stacks the
 * two insets — `.imePadding()` on the chat column plus
 * `.navigationBarsPadding()` on the composer itself — so the inset is
 * the SUM, not the max: keyboard open means keyboard + nav bar, closed
 * means nav bar alone. Negative inputs (a misbehaving inset) read as 0.
 */
fun hatchComposerBottomInset(imeBottomPx: Int, navigationBarBottomPx: Int): Int =
    imeBottomPx.coerceAtLeast(0) + navigationBarBottomPx.coerceAtLeast(0)

/**
 * Geometry of a floating chrome — a card that hovers above content
 * and can be dragged (the composer card, a floating toolbar). The
 * offset is the finger's drag in pixels; the elevation is the
 * caller's, because the chrome's idle/focused/dragged elevations
 * belong to the surface that owns it.
 */
@Immutable
data class HatchFloatingChromeGeometry(
    val cornerRadius: Dp = 16.dp,
    val elevation: Dp = 0.dp,
    val offsetYPx: Float = 0f,
)

/**
 * Pure: the chrome's vertical offset for a drag, clamped to
 * ±[maxOffsetPx] so a wild fling cannot throw the chrome off-screen.
 * A non-positive max means the chrome does not move.
 */
fun hatchFloatingChromeOffsetY(dragOffsetPx: Float, maxOffsetPx: Float): Float {
    val max = maxOffsetPx.coerceAtLeast(0f)
    return dragOffsetPx.coerceIn(-max, max)
}

/**
 * Pure: which elevation a floating chrome shows. The numbers are
 * parameters, not constants — the owning surface picks them from its
 * own tokens; what is pinned here is the priority: dragging wins over
 * focused, focused over idle.
 */
fun hatchFloatingChromeElevation(
    isDragging: Boolean,
    isFocused: Boolean,
    draggingElevation: Dp,
    focusedElevation: Dp,
    idleElevation: Dp,
): Dp = when {
    isDragging -> draggingElevation
    isFocused -> focusedElevation
    else -> idleElevation
}

/**
 * The window the composer lives in: its size and what the keyboard
 * covers. [isKeyboardVisible] is exactly "the IME reports a height",
 * which is also how the app decides the composer rides the keyboard.
 */
@Immutable
data class HatchWindowGeometry(
    val widthDp: Dp,
    val heightDp: Dp,
    val imeHeightDp: Dp,
    val isKeyboardVisible: Boolean,
)

/** Pure: [HatchWindowGeometry] from raw window + IME pixels. */
fun hatchWindowGeometry(
    windowWidthPx: Int,
    windowHeightPx: Int,
    imeBottomPx: Int,
    density: Density,
): HatchWindowGeometry {
    val ime = imeBottomPx.coerceAtLeast(0)
    return with(density) {
        HatchWindowGeometry(
            widthDp = windowWidthPx.coerceAtLeast(0).toDp(),
            heightDp = windowHeightPx.coerceAtLeast(0).toDp(),
            imeHeightDp = ime.toDp(),
            isKeyboardVisible = ime > 0,
        )
    }
}
