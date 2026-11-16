package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The read watermark is a message id, not a count (audit-100 Fase 6b,
 * 01-F2): prepends must not move it, and a windowed-out id must resolve
 * to "everything in the window is unread".
 */
class ReadPositionTest {

    private fun messages(vararg ids: String) =
        ids.map { ChatMessage(id = it, role = Role.ASSISTANT, text = "t") }

    @Test
    fun `a stored id resolves to the index after it`() {
        assertEquals(2, resolveReadCount("m2", messages("m1", "m2", "m3")))
    }

    @Test
    fun `a stored id still resolves after older messages are prepended`() {
        assertEquals(4, resolveReadCount("m2", messages("m0a", "m0b", "m1", "m2", "m3")))
    }

    @Test
    fun `a never-stored id means everything is read`() {
        assertEquals(3, resolveReadCount(null, messages("m1", "m2", "m3")))
    }

    @Test
    fun `an empty id and a windowed-out id both mean nothing is read`() {
        val window = messages("m4", "m5", "m6")
        assertEquals(0, resolveReadCount("", window))
        assertEquals(0, resolveReadCount("m1", window))
    }

    // DL-037: a legacy count watermark migrates to an id --------------

    @Test
    fun `DL-037 a legacy count resolves to the id of the last read message`() {
        assertEquals("m2", resolveMigratedReadId(2, messages("m1", "m2", "m3")))
    }

    @Test
    fun `DL-037 a legacy count of zero means nothing read`() {
        assertEquals("", resolveMigratedReadId(0, messages("m1", "m2", "m3")))
        assertEquals("", resolveMigratedReadId(-1, messages("m1", "m2", "m3")))
    }

    @Test
    fun `DL-037 a legacy count past the end clamps to the last message`() {
        assertEquals("m3", resolveMigratedReadId(9, messages("m1", "m2", "m3")))
    }

    @Test
    fun `DL-037 a legacy count with an empty window cannot be resolved`() {
        assertNull(resolveMigratedReadId(2, emptyList()))
    }

    // Audit-200 D16 (m-049): the watermark advances only when at least
    // half of the first unread item is on screen -----------------------

    @Test
    fun `m-049 a one-pixel sliver at the viewport edge does not count as read`() {
        // Item 200px tall, only its last pixel inside a 1000px viewport.
        assertEquals(0.005f, visibleFraction(itemOffset = 999, itemSize = 200, viewportHeight = 1000), 0.001f)
    }

    @Test
    fun `m-049 exactly half visible counts as read`() {
        assertEquals(0.5f, visibleFraction(itemOffset = 900, itemSize = 200, viewportHeight = 1000), 0.001f)
    }

    @Test
    fun `m-049 more than half visible counts as read`() {
        assertEquals(0.75f, visibleFraction(itemOffset = 850, itemSize = 200, viewportHeight = 1000), 0.001f)
    }

    @Test
    fun `m-049 an item fully above or below the viewport is not visible`() {
        assertEquals(0f, visibleFraction(itemOffset = -200, itemSize = 200, viewportHeight = 1000), 0.001f)
        assertEquals(0f, visibleFraction(itemOffset = 1000, itemSize = 200, viewportHeight = 1000), 0.001f)
        assertEquals(0f, visibleFraction(itemOffset = -50, itemSize = 0, viewportHeight = 1000), 0.001f)
    }

    @Test
    fun `m-049 an item scrolled partly past the top still counts its visible part`() {
        // Top 50px scrolled past: 150 of 200px visible.
        assertEquals(0.75f, visibleFraction(itemOffset = -50, itemSize = 200, viewportHeight = 1000), 0.001f)
    }
}
