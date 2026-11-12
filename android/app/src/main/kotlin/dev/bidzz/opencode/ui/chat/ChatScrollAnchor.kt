package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.lazy.LazyListState

/**
 * A saved scroll position: the first visible list item plus its pixel offset
 * from the viewport top. Captured before a change that shifts list indices
 * (loading earlier messages, expanding a trace), restored after, so the
 * reader's viewport does not jump (Fondasi Opsi B #6d — the anchor helper
 * is shared with #6f's expand/collapse compensation).
 */
data class ScrollAnchor(val index: Int, val offset: Int)

fun LazyListState.captureAnchor(): ScrollAnchor =
    ScrollAnchor(firstVisibleItemIndex, firstVisibleItemScrollOffset)

/**
 * Where [anchor] lands after `newMessageCount - oldMessageCount` messages
 * were prepended at the front and the head-item offset moved from
 * [oldHeadOffset] to [newHeadOffset] (1 while the "load earlier" head item
 * is shown, 0 once it is gone). List indices of pre-existing items shift
 * by the prepended count plus the head shift; the result never goes below 0.
 */
fun computeRestoredIndex(
    anchorIndex: Int,
    oldMessageCount: Int,
    newMessageCount: Int,
    oldHeadOffset: Int,
    newHeadOffset: Int,
): Int = (anchorIndex + (newMessageCount - oldMessageCount) + (newHeadOffset - oldHeadOffset))
    .coerceAtLeast(0)
