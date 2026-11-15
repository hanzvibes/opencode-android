package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D4 (m-067): the `==...==` highlight used to fire on the
 * comparison operator — "x == 1 and y == 2" highlighted " 1 and y ".
 * A bare == only opens a highlight when it is not glued to a word and is
 * followed by a non-space, and the closer must be glued to the content on
 * its left, so a spaced comparison never opens or closes one.
 */
class MarkdownHighlightTest {

    private fun hasHighlight(text: String, streaming: Boolean = false): Boolean {
        val blocks = Markdown.parse(text, streaming = streaming)
        val para = blocks.singleOrNull() as? MdBlock.Paragraph ?: return false
        return para.spans.any { it is MdSpan.Highlight }
    }

    @Test
    fun `a comparison is not a highlight`() {
        assertFalse(hasHighlight("x == 1 and y == 2"))
        assertFalse(hasHighlight("a==b==c"))
        assertFalse(hasHighlight("if x == 1 and y == 2 then lanjut"))
        assertFalse(hasHighlight("a == b == c"))
    }

    @Test
    fun `a real highlight still highlights`() {
        assertTrue(hasHighlight("==teks=="))
        assertTrue(hasHighlight("This is ==highlighted== text"))
        assertTrue(hasHighlight("kata ==sorot== ini"))
        assertTrue(hasHighlight("==sorot==, lanjut"))
        assertTrue(hasHighlight("<mark>teks</mark>"))
    }

    @Test
    fun `a lone double-equals stays plain while streaming`() {
        assertFalse(hasHighlight("cek a ==b dulu", streaming = true))
        assertFalse(hasHighlight("x == 1 and y", streaming = true))
        assertFalse(hasHighlight("==teks", streaming = true))
    }
}
