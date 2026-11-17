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

/**
 * Saver for [ScrollAnchor] (audit-200 D17, m-426): the load/expand
 * anchors are saveable state now, so a recreate or process death
 * mid-load does not throw the reading position away. Saved as a plain
 * two-int list, which the Bundle handles.
 */
val ScrollAnchorSaver: androidx.compose.runtime.saveable.Saver<ScrollAnchor?, List<Int>> =
    androidx.compose.runtime.saveable.Saver(
        save = { anchor -> anchor?.let { listOf(it.index, it.offset) } },
        restore = { list ->
            if (list != null && list.size == 2) ScrollAnchor(list[0], list[1]) else null
        },
    )

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

/**
 * The id of the message [anchor] sits on, or null when the anchor is the
 * "load earlier" head item itself (its index is below [headOffset], so it
 * points at no message). [messageIds] is the message list in list order.
 */
fun anchorMessageId(anchor: ScrollAnchor, messageIds: List<String>, headOffset: Int): String? {
    val messageIndex = anchor.index - headOffset
    return messageIds.getOrNull(messageIndex)
}

/**
 * Where [anchor] lands when the transcript changed while older messages
 * were loading — by MESSAGE ID, not by count (audit-200 D16, m-038/m-048).
 *
 * The count formula in [restoreScrollAnchor] assumes the size delta is
 * purely prepended messages, but two polluters grow the tail mid-load: a
 * streaming ghost appended by the overlay and an optimistic bubble staged
 * while loading. Either one inflates the delta by one, so the count
 * formula lands one item too low. The anchor's own id does not move when
 * the tail grows: if it is still in the new list, it targets that message
 * directly (plus the head shift); only when the id is gone — merged away
 * by a rebuild — does it fall back to the count formula.
 */
fun restoreScrollAnchorById(
    anchorMessageId: String?,
    anchor: ScrollAnchor,
    messageIds: List<String>,
    oldMessageCount: Int,
    newMessageCount: Int,
    oldHeadOffset: Int,
    newHeadOffset: Int,
): ScrollAnchor {
    val found = anchorMessageId?.let { messageIds.indexOf(it) } ?: -1
    if (found >= 0) {
        return ScrollAnchor(index = found + newHeadOffset, offset = anchor.offset)
    }
    return restoreScrollAnchor(
        anchor = anchor,
        oldMessageCount = oldMessageCount,
        newMessageCount = newMessageCount,
        oldHeadOffset = oldHeadOffset,
        newHeadOffset = newHeadOffset,
    )
}

/**
 * Where [anchor] lands after `newMessageCount - oldMessageCount` messages
 * were prepended at the front and the head-item offset moved from
 * [oldHeadOffset] to [newHeadOffset] — index AND offset, because the two
 * cases restore differently (audit-200 C14, M-199).
 *
 * A message anchor shifts by the prepended count plus the head shift,
 * keeping its pixel offset (see [computeRestoredIndex]). A HEAD anchor —
 * the "load earlier" item itself, index 0 while it is shown — points at
 * no message: it means "the top of the old content", i.e. the first old
 * message, which sat right below the head at index [oldHeadOffset]. The
 * plain formula would land one item short, on the last prepended message
 * instead of the first message the reader was actually reading, so a
 * head anchor targets that first old message
 * (`(new - old) + newHeadOffset`) with offset 0. This is the anchor a
 * "load earlier" tap practically always captures: tapping the button
 * requires scrolling to the top, so the head item is the first visible
 * item.
 */
fun restoreScrollAnchor(
    anchor: ScrollAnchor,
    oldMessageCount: Int,
    newMessageCount: Int,
    oldHeadOffset: Int,
    newHeadOffset: Int,
): ScrollAnchor {
    if (anchor.index < oldHeadOffset) {
        return ScrollAnchor(
            index = ((newMessageCount - oldMessageCount) + newHeadOffset).coerceAtLeast(0),
            offset = 0,
        )
    }
    return ScrollAnchor(
        index = computeRestoredIndex(
            anchorIndex = anchor.index,
            oldMessageCount = oldMessageCount,
            newMessageCount = newMessageCount,
            oldHeadOffset = oldHeadOffset,
            newHeadOffset = newHeadOffset,
        ),
        offset = anchor.offset,
    )
}
