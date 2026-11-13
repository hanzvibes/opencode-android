package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown table parsing.
 *
 * Tables are the one block models emit constantly and the parser used to refuse, so
 * these lock the boundary: what counts as a table, and — more important — what must
 * stay literal text. A half-recognised table renders as broken markup, which is worse
 * than showing the pipes.
 */
class MarkdownTableTest {

    @Test
    fun `parses a header, separator and body rows`() {
        val blocks = Markdown.parse(
            """
            | File | Status |
            | --- | --- |
            | main.py | modified |
            | README.md | clean |
            """.trimIndent(),
        )

        val table = blocks.single() as MdBlock.Table
        assertEquals(2, table.columns)
        assertEquals(listOf("File", "Status"), table.header.plain())
        assertEquals(2, table.rows.size)
        assertEquals(listOf("main.py", "modified"), table.rows[0].plain())
    }

    @Test
    fun `pads a short row and trims a long one to the header width`() {
        val blocks = Markdown.parse(
            """
            | A | B | C |
            | --- | --- | --- |
            | 1 |
            | 1 | 2 | 3 | 4 |
            """.trimIndent(),
        )

        val table = blocks.single() as MdBlock.Table
        assertEquals(3, table.columns)
        assertEquals(listOf("1", "", ""), table.rows[0].plain())
        assertEquals(listOf("1", "2", "3"), table.rows[1].plain())
    }

    @Test
    fun `accepts alignment markers`() {
        val table = Markdown.parse(
            """
            | Left | Centre | Right |
            | :--- | :---: | ---: |
            | a | b | c |
            """.trimIndent(),
        ).single() as MdBlock.Table

        assertEquals(1, table.rows.size)
        assertEquals(listOf("a", "b", "c"), table.rows[0].plain())
    }

    @Test
    fun `keeps inline formatting inside cells`() {
        val table = Markdown.parse(
            """
            | Tool | Result |
            | --- | --- |
            | `read` | **ok** |
            """.trimIndent(),
        ).single() as MdBlock.Table

        val cell = table.rows[0][0]
        assertTrue("expected a code span, got $cell", cell.any { it is MdSpan.Code && it.text == "read" })
        val bold = table.rows[0][1]
        assertTrue("expected bold, got $bold", bold.any { it is MdSpan.Bold && it.spans == listOf(MdSpan.Text("ok")) })
    }

    @Test
    fun `a bare dash line stays a horizontal rule`() {
        // Regression guard: the separator pattern also matches `---`, which would
        // otherwise turn every rule into the start of a table.
        val blocks = Markdown.parse("---")
        assertEquals(listOf(MdBlock.Rule), blocks)
    }

    @Test
    fun `a piped line without a separator is still prose`() {
        val blocks = Markdown.parse("run `a | b` then stop")
        assertTrue(blocks.single() is MdBlock.Paragraph)
    }

    @Test
    fun `a table ends at the first blank line`() {
        val blocks = Markdown.parse(
            """
            | A | B |
            | --- | --- |
            | 1 | 2 |

            after the table
            """.trimIndent(),
        )

        assertEquals(2, blocks.size)
        assertEquals(1, (blocks[0] as MdBlock.Table).rows.size)
        assertEquals("after the table", (blocks[1] as MdBlock.Paragraph).spans.text())
    }

    @Test
    fun `parses hr tag as a horizontal rule`() {
        val blocks = Markdown.parse("<hr>")
        assertEquals(listOf(MdBlock.Rule), blocks)
        val blocksSelfClosing = Markdown.parse("<hr/>")
        assertEquals(listOf(MdBlock.Rule), blocksSelfClosing)
    }

    @Test
    fun `parses highlighted text with double equals and mark tags`() {
        val blocks = Markdown.parse("This is ==highlighted== and <mark>marked</mark> text")
        val para = blocks.single() as MdBlock.Paragraph
        assertTrue(para.spans.any { it is MdSpan.Highlight && it.spans == listOf(MdSpan.Text("highlighted")) })
        assertTrue(para.spans.any { it is MdSpan.Highlight && it.spans == listOf(MdSpan.Text("marked")) })
    }

    @Test
    fun `parses bold with double underscore`() {
        val blocks = Markdown.parse("This is __bold__ text")
        val para = blocks.single() as MdBlock.Paragraph
        assertTrue(para.spans.any { it is MdSpan.Bold && it.spans == listOf(MdSpan.Text("bold")) })
    }

    @Test
    fun `parses list items with continuation lines`() {
        val md = """
            - First line
              continuation of first
            - Second item
        """.trimIndent()
        val blocks = Markdown.parse(md)
        val bullet = blocks.single() as MdBlock.Bullet
        assertEquals(2, bullet.items.size)
        assertEquals("First line continuation of first", bullet.items[0].text())
        assertEquals("Second item", bullet.items[1].text())
    }

    /** Flattens a cell row to plain text, ignoring formatting. */
    private fun List<List<MdSpan>>.plain(): List<String> = map { cell -> cell.text() }

    private fun List<MdSpan>.text(): String = joinToString("") { span ->
        when (span) {
            is MdSpan.Text -> span.text
            is MdSpan.Bold -> span.spans.text()
            is MdSpan.Italic -> span.spans.text()
            is MdSpan.Code -> span.text
            is MdSpan.Link -> span.spans.text()
            is MdSpan.Highlight -> span.spans.text()
            is MdSpan.Strikethrough -> span.spans.text()
        }
    }
}
