package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBlockquoteTest {

    @Test
    fun `parses single line blockquote`() {
        val blocks = Markdown.parse("> Note: this is a note")
        assertEquals(1, blocks.size)
        val quote = blocks.single() as MdBlock.Blockquote
        assertEquals(1, quote.spans.size)
        assertEquals("Note: this is a note", (quote.spans[0] as MdSpan.Text).text)
    }

    @Test
    fun `parses multiline blockquote with inline formatting`() {
        val blocks = Markdown.parse(
            """
            > Be careful with `rm -rf`
            > Always check **twice** before executing.
            """.trimIndent(),
        )
        assertEquals(1, blocks.size)
        val quote = blocks.single() as MdBlock.Blockquote
        assertTrue(quote.spans.any { it is MdSpan.Code && it.text == "rm -rf" })
        assertTrue(quote.spans.any { it is MdSpan.Bold && it.text == "twice" })
    }

    @Test
    fun `parses blockquote followed by prose`() {
        val blocks = Markdown.parse(
            """
            > Important message

            Regular paragraph follows.
            """.trimIndent(),
        )
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MdBlock.Blockquote)
        assertTrue(blocks[1] is MdBlock.Paragraph)
    }

    @Test
    fun `parses headings up to level 6`() {
        val md = """
            # H1
            ## H2
            ### H3
            #### H4
            ##### H5
            ###### H6
        """.trimIndent()
        val blocks = Markdown.parse(md)
        assertEquals(6, blocks.size)
        for (i in 1..6) {
            val heading = blocks[i - 1] as MdBlock.Heading
            assertEquals(i, heading.level)
            assertEquals("H$i", (heading.spans[0] as MdSpan.Text).text)
        }
    }
}
