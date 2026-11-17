package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatScrollAnchorTest {

    @Test
    fun `prepended messages shift the anchor by the new count`() {
        assertEquals(
            14,
            computeRestoredIndex(
                anchorIndex = 4,
                oldMessageCount = 10,
                newMessageCount = 20,
                oldHeadOffset = 1,
                newHeadOffset = 1,
            ),
        )
    }

    @Test
    fun `head item disappearing shifts the anchor back by one`() {
        assertEquals(
            13,
            computeRestoredIndex(
                anchorIndex = 4,
                oldMessageCount = 10,
                newMessageCount = 20,
                oldHeadOffset = 1,
                newHeadOffset = 0,
            ),
        )
    }

    @Test
    fun `no growth keeps the anchor`() {
        assertEquals(
            4,
            computeRestoredIndex(
                anchorIndex = 4,
                oldMessageCount = 10,
                newMessageCount = 10,
                oldHeadOffset = 1,
                newHeadOffset = 1,
            ),
        )
    }

    @Test
    fun `restored index never goes below zero`() {
        assertEquals(
            0,
            computeRestoredIndex(
                anchorIndex = 0,
                oldMessageCount = 10,
                newMessageCount = 10,
                oldHeadOffset = 1,
                newHeadOffset = 0,
            ),
        )
    }

    @Test
    fun `head anchor targets the first old message while the head stays`() {
        // Audit-200 C14 (M-199): the "load earlier" tap captures the head
        // item itself (index 0). Ten messages prepended, head still shown:
        // the first old message is now at 10 + 1, not at 10 (the last
        // prepended message the old formula landed on).
        assertEquals(
            ScrollAnchor(index = 11, offset = 0),
            restoreScrollAnchor(
                anchor = ScrollAnchor(index = 0, offset = 0),
                oldMessageCount = 10,
                newMessageCount = 20,
                oldHeadOffset = 1,
                newHeadOffset = 1,
            ),
        )
    }

    @Test
    fun `head anchor targets the first old message once the head is gone`() {
        assertEquals(
            ScrollAnchor(index = 10, offset = 0),
            restoreScrollAnchor(
                anchor = ScrollAnchor(index = 0, offset = 0),
                oldMessageCount = 10,
                newMessageCount = 20,
                oldHeadOffset = 1,
                newHeadOffset = 0,
            ),
        )
    }

    @Test
    fun `head anchor drops its pixel offset`() {
        // The head's offset is meaningless for a message: the first old
        // message lands flush at the viewport top.
        assertEquals(
            ScrollAnchor(index = 11, offset = 0),
            restoreScrollAnchor(
                anchor = ScrollAnchor(index = 0, offset = 37),
                oldMessageCount = 10,
                newMessageCount = 20,
                oldHeadOffset = 1,
                newHeadOffset = 1,
            ),
        )
    }

    @Test
    fun `message anchor keeps its pixel offset`() {
        assertEquals(
            ScrollAnchor(index = 14, offset = 12),
            restoreScrollAnchor(
                anchor = ScrollAnchor(index = 4, offset = 12),
                oldMessageCount = 10,
                newMessageCount = 20,
                oldHeadOffset = 1,
                newHeadOffset = 1,
            ),
        )
    }

    // Audit-200 D16 (m-038/m-048): restore by message id ---------------

    @Test
    fun `m-038 the anchor id is the message under the anchor`() {
        val ids = listOf("m1", "m2", "m3")
        assertEquals("m2", anchorMessageId(ScrollAnchor(index = 2, offset = 0), ids, headOffset = 1))
        assertEquals("m1", anchorMessageId(ScrollAnchor(index = 1, offset = 0), ids, headOffset = 1))
        // The head item itself points at no message.
        assertEquals(null, anchorMessageId(ScrollAnchor(index = 0, offset = 0), ids, headOffset = 1))
    }

    @Test
    fun `m-038 a streaming ghost appended mid-load does not shift the anchor`() {
        // Ten older messages prepended, one streaming ghost appended at
        // the tail while loading: the count formula would land one item
        // too far down (15 instead of 14); the id restore stays on m4.
        val newIds = (0 until 10).map { "old$it" } + listOf("m1", "m2", "m3", "m4", "ghost")
        assertEquals(
            ScrollAnchor(index = 14, offset = 12),
            restoreScrollAnchorById(
                anchorMessageId = "m4",
                anchor = ScrollAnchor(index = 4, offset = 12),
                messageIds = newIds,
                oldMessageCount = 4,
                newMessageCount = 15,
                oldHeadOffset = 1,
                newHeadOffset = 1,
            ),
        )
    }

    @Test
    fun `m-038 an optimistic bubble staged mid-load does not shift the anchor`() {
        // Same shape as the ghost case: the optimistic "local:…" bubble
        // appended at the tail must not move the anchor either.
        val newIds = (0 until 10).map { "old$it" } + listOf("m1", "m2", "m3", "m4", "local:abc")
        assertEquals(
            ScrollAnchor(index = 14, offset = 0),
            restoreScrollAnchorById(
                anchorMessageId = "m4",
                anchor = ScrollAnchor(index = 4, offset = 0),
                messageIds = newIds,
                oldMessageCount = 4,
                newMessageCount = 15,
                oldHeadOffset = 1,
                newHeadOffset = 1,
            ),
        )
    }

    @Test
    fun `m-038 a merged-away id falls back to the count formula`() {
        // The anchor message is gone from the new list (merged away by a
        // rebuild): the count formula is the only thing left.
        assertEquals(
            ScrollAnchor(index = 14, offset = 12),
            restoreScrollAnchorById(
                anchorMessageId = "gone",
                anchor = ScrollAnchor(index = 4, offset = 12),
                messageIds = (0 until 10).map { "old$it" } + listOf("m1", "m2", "m3", "m4"),
                oldMessageCount = 4,
                newMessageCount = 14,
                oldHeadOffset = 1,
                newHeadOffset = 1,
            ),
        )
    }

    @Test
    fun `m-048 a head anchor with no id falls back to the head formula`() {
        // No message id (the tap captured the head item): C14's head
        // rule still applies — the first old message, not the last
        // prepended one.
        assertEquals(
            ScrollAnchor(index = 11, offset = 0),
            restoreScrollAnchorById(
                anchorMessageId = null,
                anchor = ScrollAnchor(index = 0, offset = 0),
                messageIds = (0 until 10).map { "old$it" } + listOf("m1", "m2"),
                oldMessageCount = 2,
                newMessageCount = 12,
                oldHeadOffset = 1,
                newHeadOffset = 1,
            ),
        )
    }
}
