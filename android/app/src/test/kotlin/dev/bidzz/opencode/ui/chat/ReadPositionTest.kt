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
}
