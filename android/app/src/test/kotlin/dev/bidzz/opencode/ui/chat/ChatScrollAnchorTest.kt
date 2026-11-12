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
}
