package dev.bidzz.opencode.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Batch 4 (Aura full, Opsi B): drag-to-reorder, clean-room re-type of
 * Aura's `ReorderableColumn` / `ReorderableList` / `ReorderableRow`.
 *
 * Aura lifts the dragged item while it moves (elevation + a finger-following
 * translation) and drops it at the index under its centre. The pieces here
 * are the same: a long-press starts the drag, the item follows the finger,
 * and [HatchReorderState.endDrag] settles it at the centre index.
 *
 * No call site yet: no list in the app is user-reorderable today (the
 * session list is sorted by the server, the explorer by name), so the
 * three containers ship as tested components, ready for the first list
 * that wants reordering — the same honest pattern as Batch 1's
 * [HatchTaskDeleteConfirmationDialog]. What is pinned by tests is the
 * logic a call site cannot see: the list move, the centre-index mapping
 * for uniform and variable item sizes, and the drag elevation.
 */

/** Elevation of the item while it is being dragged (Aura's drag lift). */
val HatchReorderDragElevation: Dp = 8.dp

/**
 * Pure: the list after moving the item at [fromIndex] to [toIndex]
 * (the index it occupies in the result). Out-of-range indices, or a
 * move onto itself, return the list unchanged.
 */
fun <T> hatchReorderedList(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in items.indices || toIndex !in items.indices) return items
    if (fromIndex == toIndex) return items
    val mutable = items.toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(toIndex, item)
    return mutable
}

/**
 * Pure: which index a dragged item lands on, for uniform [itemSizePx]
 * items. [dragOffsetPx] is the dragged item's top edge in list
 * coordinates; the landing index is the one under the item's centre.
 * Returns -1 when there is nothing to land on (no items, or no size).
 */
fun hatchReorderTargetIndex(dragOffsetPx: Float, itemSizePx: Float, count: Int): Int {
    if (count <= 0 || itemSizePx <= 0f) return -1
    val centre = dragOffsetPx + itemSizePx / 2f
    return (centre / itemSizePx).toInt().coerceIn(0, count - 1)
}

/**
 * Pure: the same centre rule for variable-height items, where [sizesPx]
 * holds each item's size in order. The centre belongs to the first item
 * whose span it falls inside; past the end it clamps to the last item.
 * Returns -1 for an empty list.
 */
fun hatchReorderTargetIndexFromSizes(centrePx: Float, sizesPx: List<Float>): Int {
    if (sizesPx.isEmpty()) return -1
    var accumulated = 0f
    for (index in sizesPx.indices) {
        accumulated += sizesPx[index].coerceAtLeast(0f)
        if (centrePx < accumulated) return index
    }
    return sizesPx.lastIndex
}

/** Pure: the drag elevation — lifted while dragging, flat otherwise. */
fun hatchReorderElevation(isDragging: Boolean): Dp =
    if (isDragging) HatchReorderDragElevation else 0.dp

/**
 * Index-based drag state, shared by the three containers. It knows
 * nothing about the items themselves — only positions — so the same
 * state drives a [Column], a [LazyColumn], or a [Row].
 *
 * Item sizes are recorded by the containers via [recordSize] as items
 * are measured; [endDrag] needs the item count because a list can
 * hold items that have never been measured (off-screen in a lazy list).
 */
class HatchReorderState(
    val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    var draggingIndex: Int by mutableIntStateOf(-1)
        private set
    var dragOffsetPx: Float by mutableFloatStateOf(0f)
        private set

    private val sizesPx = mutableMapOf<Int, Float>()

    val isDragging: Boolean get() = draggingIndex >= 0

    fun recordSize(index: Int, sizePx: Float) {
        if (sizePx > 0f) sizesPx[index] = sizePx
    }

    fun startDrag(index: Int) {
        draggingIndex = index
        dragOffsetPx = 0f
    }

    fun dragBy(deltaPx: Float) {
        if (isDragging) dragOffsetPx += deltaPx
    }

    fun endDrag(itemCount: Int) {
        val from = draggingIndex
        if (from in 0 until itemCount) {
            val size = sizesPx[from] ?: 0f
            val start = (0 until from).sumOf { (sizesPx[it] ?: 0f).toDouble() }.toFloat()
            val centre = start + dragOffsetPx + size / 2f
            val orderedSizes = (0 until itemCount).map { sizesPx[it] ?: 0f }
            val target = hatchReorderTargetIndexFromSizes(centre, orderedSizes)
            if (target in 0 until itemCount && target != from) onMove(from, target)
        }
        draggingIndex = -1
        dragOffsetPx = 0f
    }

    fun cancelDrag() {
        draggingIndex = -1
        dragOffsetPx = 0f
    }
}

@Composable
fun rememberHatchReorderState(
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
): HatchReorderState = remember(onMove) { HatchReorderState(onMove) }

/**
 * The drag handling + lift for one item. Long-press starts the drag
 * (a plain scroll still scrolls: the press has to settle first), the
 * item follows the finger, and release settles it via [HatchReorderState].
 */
private fun Modifier.hatchReorderDrag(
    state: HatchReorderState,
    index: Int,
    itemCount: Int,
    horizontal: Boolean,
): Modifier = this
    .onSizeChanged { size ->
        state.recordSize(
            index,
            if (horizontal) size.width.toFloat() else size.height.toFloat(),
        )
    }
    .pointerInput(state, index, horizontal) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.startDrag(index) },
            onDragEnd = { state.endDrag(itemCount) },
            onDragCancel = { state.cancelDrag() },
            onDrag = { change, dragAmount ->
                state.dragBy(if (horizontal) dragAmount.x else dragAmount.y)
                change.consume()
            },
        )
    }

/** One item inside a reorderable container: lift + translation while dragged. */
@Composable
private fun ReorderableItem(
    state: HatchReorderState,
    index: Int,
    itemCount: Int,
    horizontal: Boolean,
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    val isDragging = state.draggingIndex == index
    val elevation by animateDpAsState(
        targetValue = hatchReorderElevation(isDragging),
        label = "reorder_elevation",
    )
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .hatchReorderDrag(state, index, itemCount, horizontal)
            .graphicsLayer {
                if (isDragging) {
                    if (horizontal) translationX = state.dragOffsetPx
                    else translationY = state.dragOffsetPx
                    shadowElevation = elevation.toPx()
                }
            }
            .zIndex(if (isDragging) 1f else 0f),
    ) {
        content(isDragging)
    }
}

/**
 * A [Column] whose items reorder by long-press drag. [key] must be
 * stable per item (the item itself by default); [onMove] receives the
 * source and landing indices — the caller owns the list and applies
 * [hatchReorderedList].
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    key: (T) -> Any = { it as Any },
    itemContent: @Composable (item: T, isDragging: Boolean) -> Unit,
) {
    val state = rememberHatchReorderState(onMove)
    Column(modifier = modifier) {
        items.forEachIndexed { index, item ->
            key(key(item)) {
                ReorderableItem(state, index, items.size, horizontal = false) { isDragging ->
                    itemContent(item, isDragging)
                }
            }
        }
    }
}

/**
 * A [LazyColumn] whose items reorder by long-press drag — the same
 * behaviour as [ReorderableColumn] for lists too long to lay out
 * eagerly. Off-screen items have no measured size yet, so a drag
 * landing past the visible window settles at the nearest measured
 * edge; the caller can re-measure by scrolling.
 */
@Composable
fun <T> ReorderableList(
    items: List<T>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    key: (T) -> Any = { it as Any },
    itemContent: @Composable (item: T, isDragging: Boolean) -> Unit,
) {
    val state = rememberHatchReorderState(onMove)
    LazyColumn(modifier = modifier) {
        items(count = items.size, key = { index -> key(items[index]) }) { index ->
            ReorderableItem(state, index, items.size, horizontal = false) { isDragging ->
                itemContent(items[index], isDragging)
            }
        }
    }
}

/**
 * A [Row] whose items reorder by long-press drag — the horizontal
 * twin of [ReorderableColumn]; the drag follows the finger on X.
 */
@Composable
fun <T> ReorderableRow(
    items: List<T>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    key: (T) -> Any = { it as Any },
    itemContent: @Composable (item: T, isDragging: Boolean) -> Unit,
) {
    val state = rememberHatchReorderState(onMove)
    Row(modifier = modifier) {
        items.forEachIndexed { index, item ->
            key(key(item)) {
                ReorderableItem(state, index, items.size, horizontal = true) { isDragging ->
                    itemContent(item, isDragging)
                }
            }
        }
    }
}
