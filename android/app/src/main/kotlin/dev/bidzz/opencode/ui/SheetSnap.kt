package dev.bidzz.opencode.ui

import kotlin.math.abs

/**
 * Opsi C — custom draggable bottom sheet: pure snap rules, no Compose.
 *
 * The sheet has three stops — [SheetStop.PEEK], [SheetStop.HALF],
 * [SheetStop.FULL] — as fractions of the available height. The composable
 * in DraggableBottomSheet.kt only animates a height and asks here where a
 * drag release should land, so the fling/dismiss behaviour is pinned in a
 * JVM test instead of a preview.
 *
 * A drag never confirms anything: the only way out of the sheet besides
 * the caller's own buttons is [SheetSnap.Dismiss], which the composable
 * turns into `onDismiss` — the same as tapping the scrim or pressing back.
 */
enum class SheetStop {
    PEEK,
    HALF,
    FULL,
}

/** Where a drag release lands: a stop, or off the sheet (dismiss). */
sealed interface SheetSnap {
    data class Stop(val index: Int) : SheetSnap
    object Dismiss : SheetSnap
}

/** Stop heights for a container of [maxHeightPx], in pixels. */
fun sheetStopHeights(maxHeightPx: Float): List<Float> =
    listOf(0.32f, 0.58f, 0.94f).map { it * maxHeightPx }

/** Index of the stop nearest to [currentHeightPx] (ties go to the lower stop). */
fun nearestSheetStopIndex(currentHeightPx: Float, stopHeightsPx: List<Float>): Int =
    stopHeightsPx.indices.minByOrNull { abs(stopHeightsPx[it] - currentHeightPx) }
        ?: SheetStop.HALF.ordinal

/**
 * Where a drag release at [currentHeightPx] with [velocityPxPerSec]
 * (positive = dragging down) should land.
 *
 * - A fast upward fling moves one stop up (stays at Full at the top).
 * - A fast downward fling moves one stop down; from Peek it dismisses.
 * - A slow release snaps to the nearest stop — unless the sheet was
 *   dragged below [dismissBelowFractionOfPeek] of the Peek height, in
 *   which case it dismisses. A slow release exactly at the threshold
 *   still snaps back to Peek: dragging must clearly mean "close".
 */
fun resolveSheetSnap(
    currentHeightPx: Float,
    velocityPxPerSec: Float,
    stopHeightsPx: List<Float>,
    flingVelocityPxPerSec: Float = 400f,
    dismissBelowFractionOfPeek: Float = 0.6f,
): SheetSnap {
    val nearest = nearestSheetStopIndex(currentHeightPx, stopHeightsPx)
    return when {
        velocityPxPerSec < -flingVelocityPxPerSec ->
            SheetSnap.Stop((nearest + 1).coerceAtMost(stopHeightsPx.lastIndex))
        velocityPxPerSec > flingVelocityPxPerSec ->
            if (nearest == 0) SheetSnap.Dismiss else SheetSnap.Stop(nearest - 1)
        currentHeightPx < stopHeightsPx.first() * dismissBelowFractionOfPeek ->
            SheetSnap.Dismiss
        else -> SheetSnap.Stop(nearest)
    }
}
