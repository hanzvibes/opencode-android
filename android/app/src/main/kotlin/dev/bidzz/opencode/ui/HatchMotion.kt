package dev.bidzz.opencode.ui

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import dev.bidzz.opencode.core.design.AuraPalette
import dev.bidzz.opencode.core.design.OpenCodeTheme

/**
 * Hatch motion — clean-room re-implementation of the Meta Muse app's
 * (`com.facebook.aura` 16.0.0.2.159) `HatchInteractiveMeshGradient`
 * and `HatchTiltTracker`, from their dex class names in
 * `aura-unpack/ANALYSIS.md`. No Aura code, font, or asset is copied:
 * the gradient stops are our own [AuraPalette] values, and the tilt
 * is plain pointer math. The press glow (`HatchHighlightGlow`) was
 * deleted in audit-200 D5 (m-999): it had no call site, and its only
 * alpha token went with it.
 *
 * Pointer observation never consumes the event: the components only
 * watch moves/presses, so scrolling and tapping keep working exactly
 * as before.
 *
 * Wiring (Batch 3): only [AuraInteractiveMeshGradient] has a call site
 * — the session-list empty state's mesh band ([EmptyState] with
 * `meshGradient = true`), which used to be the static [AuraMeshGradient].
 * The publish sheet's band stays static: at 4dp tall there is nothing
 * for a finger to move across. [HatchTiltTracker] ships with tested
 * mappings and no call site yet — the same honest pattern as Batch 1's
 * TaskDelete/Publish sheets — ready for a pressable card that wants it.
 */

/**
 * A pointer position as a 0..1 fraction of the box. Null (no pointer
 * yet) and empty sizes fall back to the top-start corner, matching the
 * static mesh gradient's highlight. Pure so it is unit-testable.
 */
internal fun hatchPointerFraction(pointer: Offset?, size: IntSize): Offset {
    if (pointer == null || size.width <= 0 || size.height <= 0) return Offset(0f, 0f)
    return Offset(
        (pointer.x / size.width).coerceIn(0f, 1f),
        (pointer.y / size.height).coerceIn(0f, 1f),
    )
}

/** A 0..1 fraction back to pixels inside [size]. Pure so it is unit-testable. */
internal fun hatchGlowCenterPx(fraction: Offset, size: IntSize): Offset =
    Offset(fraction.x * size.width, fraction.y * size.height)

/**
 * The interactive mesh gradient ([HatchInteractiveMeshGradient] in
 * Aura): the same band as [AuraMeshGradient] (see [auraMeshStops] for
 * the per-theme stops), but the soft white highlight follows the finger
 * (or mouse) instead of sitting at the top-start corner. The highlight
 * eases to the pointer over 150ms so it trails smoothly rather than
 * snapping. The highlight radius follows the box size
 * ([hatchGlowRadius]) instead of a fixed 600f.
 */
@Composable
fun AuraInteractiveMeshGradient(
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    var pointerFraction by remember { mutableStateOf(Offset(0f, 0f)) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val animatedFraction by animateOffsetAsState(
        targetValue = pointerFraction,
        animationSpec = tween(durationMillis = 150),
        label = "hatchMeshHighlight",
    )
    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.firstOrNull()?.position ?: continue
                        if (event.type == PointerEventType.Move ||
                            event.type == PointerEventType.Press
                        ) {
                            pointerFraction = hatchPointerFraction(position, size)
                        }
                    }
                }
            }
            .background(
                Brush.linearGradient(
                    colors = auraMeshStops(colors.isDark),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = auraMeshHighlightAlpha(colors.isDark)),
                            Color.Transparent,
                        ),
                        center = hatchGlowCenterPx(animatedFraction, boxSize),
                        radius = hatchGlowRadius(boxSize),
                    ),
                ),
        )
    }
}

/** The glow's radius: three quarters of the box's long side. Pure so it is unit-testable. */
internal fun hatchGlowRadius(size: IntSize): Float =
    maxOf(size.width, size.height) * 0.75f

/** A 3D tilt in degrees: positive X tips the top away, positive Y tips the right away. */
data class HatchTiltAngles(val rotationX: Float, val rotationY: Float)

/** The default tilt range: 8 degrees either way. */
internal const val HatchTiltMaxDegrees: Float = 8f

/**
 * Tilt angles for a pointer inside a box: the centre is flat, the edges
 * tilt up to [maxDegrees]. Out-of-bounds pointers clamp to the edge
 * angles instead of overshooting. Pure so it is unit-testable.
 */
internal fun hatchTiltAngles(
    pointer: Offset,
    size: IntSize,
    maxDegrees: Float = HatchTiltMaxDegrees,
): HatchTiltAngles {
    if (size.width <= 0 || size.height <= 0) return HatchTiltAngles(0f, 0f)
    val nx = (pointer.x / size.width * 2f - 1f).coerceIn(-1f, 1f)
    val ny = (pointer.y / size.height * 2f - 1f).coerceIn(-1f, 1f)
    val rotationX = -ny * maxDegrees
    val rotationY = nx * maxDegrees
    // -0.0f must read as 0.0f: data-class equality tells them apart,
    // and the flat centre should be exactly (0, 0).
    return HatchTiltAngles(
        rotationX = if (rotationX == 0f) 0f else rotationX,
        rotationY = if (rotationY == 0f) 0f else rotationY,
    )
}

/**
 * Pointer tilt state ([HatchTiltTracker] in Aura). Feed it pointer
 * positions with [onPointer] and apply the angles with [Modifier.hatchTilt];
 * [reset] flattens the card again on release. No call site in Batch 3.
 */
class HatchTiltTracker(val maxDegrees: Float = HatchTiltMaxDegrees) {
    var angles by mutableStateOf(HatchTiltAngles(0f, 0f))
        private set

    fun onPointer(pointer: Offset, size: IntSize) {
        angles = hatchTiltAngles(pointer, size, maxDegrees)
    }

    fun reset() {
        angles = HatchTiltAngles(0f, 0f)
    }
}

@Composable
fun rememberHatchTiltTracker(
    maxDegrees: Float = HatchTiltMaxDegrees,
): HatchTiltTracker = remember(maxDegrees) { HatchTiltTracker(maxDegrees) }

/** Applies a [HatchTiltTracker]'s angles as a 3D tilt. */
fun Modifier.hatchTilt(tracker: HatchTiltTracker): Modifier = graphicsLayer {
    rotationX = tracker.angles.rotationX
    rotationY = tracker.angles.rotationY
    cameraDistance = 12f * density
}

/**
 * Wires pointer moves into a [HatchTiltTracker]: moves and presses tilt,
 * release flattens. Observes only — scrolling and tapping are untouched.
 */
fun Modifier.hatchTiltInput(tracker: HatchTiltTracker): Modifier = pointerInput(tracker) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val position = event.changes.firstOrNull()?.position ?: continue
            when (event.type) {
                PointerEventType.Move, PointerEventType.Press ->
                    tracker.onPointer(position, size)
                PointerEventType.Release -> tracker.reset()
                else -> Unit
            }
        }
    }
}
