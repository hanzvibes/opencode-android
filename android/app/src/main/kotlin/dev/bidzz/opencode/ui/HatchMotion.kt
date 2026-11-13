package dev.bidzz.opencode.ui

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import dev.bidzz.opencode.core.design.AuraPalette

/**
 * Hatch motion — clean-room re-implementation of the Meta Muse app's
 * (`com.facebook.aura` 16.0.0.2.159) `HatchInteractiveMeshGradient`,
 * `HatchHighlightGlow` and `HatchTiltTracker`, from their dex class
 * names in `aura-unpack/ANALYSIS.md`. No Aura code, font, or asset is
 * copied: the gradient stops are our own [AuraPalette] values, the
 * glow is our own radial brush, and the tilt is plain pointer math.
 *
 * Pointer observation never consumes the event: the components only
 * watch moves/presses, so scrolling and tapping keep working exactly
 * as before.
 *
 * Wiring (Batch 3): only [AuraInteractiveMeshGradient] has a call site
 * — the session-list empty state's mesh band ([EmptyState] with
 * `meshGradient = true`), which used to be the static [AuraMeshGradient].
 * The publish sheet's band stays static: at 4dp tall there is nothing
 * for a finger to move across. [HatchHighlightGlow] and [HatchTiltTracker]
 * ship with tested mappings and no call site yet — the same honest
 * pattern as Batch 1's TaskDelete/Publish sheets — ready for a
 * pressable card that wants them.
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
 * Aura): the same blue → purple → light-blue band as [AuraMeshGradient],
 * but the soft white highlight follows the finger (or mouse) instead of
 * sitting at the top-start corner. The highlight eases to the pointer
 * over 150ms so it trails smoothly rather than snapping.
 */
@Composable
fun AuraInteractiveMeshGradient(
    modifier: Modifier = Modifier,
) {
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
                        center = hatchGlowCenterPx(animatedFraction, boxSize),
                        radius = 600f,
                    ),
                ),
        )
    }
}

/** The glow's radius: three quarters of the box's long side. Pure so it is unit-testable. */
internal fun hatchGlowRadius(size: IntSize): Float =
    maxOf(size.width, size.height) * 0.75f

/**
 * A press glow ([HatchHighlightGlow] in Aura): a soft Aura-blue radial
 * highlight at the press point, drawn behind the content, cleared on
 * release. No call site in Batch 3 — wrap a pressable card in this
 * when one wants the glow.
 */
@Composable
fun HatchHighlightGlow(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var press by remember { mutableStateOf<Offset?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.firstOrNull()?.position ?: continue
                        when (event.type) {
                            PointerEventType.Press -> press = position
                            PointerEventType.Release,
                            PointerEventType.Exit -> press = null
                            else -> Unit
                        }
                    }
                }
            }
            .drawBehind {
                val position = press ?: return@drawBehind
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AuraPalette.auraBlueAlpha20,
                            Color.Transparent,
                        ),
                        center = hatchGlowCenterPx(
                            hatchPointerFraction(position, boxSize),
                            boxSize,
                        ),
                        radius = hatchGlowRadius(boxSize),
                    ),
                    radius = hatchGlowRadius(boxSize),
                    center = hatchGlowCenterPx(
                        hatchPointerFraction(position, boxSize),
                        boxSize,
                    ),
                )
            },
    ) {
        content()
    }
}

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
    return HatchTiltAngles(rotationX = -ny * maxDegrees, rotationY = nx * maxDegrees)
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
