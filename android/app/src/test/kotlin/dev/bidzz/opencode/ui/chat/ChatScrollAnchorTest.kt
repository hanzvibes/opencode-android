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
}
