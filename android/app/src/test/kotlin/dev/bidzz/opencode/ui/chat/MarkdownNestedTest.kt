package dev.bidzz.opencode.ui.chat

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 6 batch 6f: nested inline formatting (04-F2), link edge cases
 * (04-F1/F8), table cell splitting (04-F4), multi-backtick code (04-F5),
 * streaming fallbacks (04-F7) and locale-safe uppercasing (04-F11).
 */
class MarkdownNestedTest {

    private fun spans(text: String): List<MdSpan> =
        (Markdown.parse(text).single() as MdBlock.Paragraph).spans

    private fun streamingSpans(text: String): List<MdSpan> =
        (Markdown.parse(text, streaming = true).single() as MdBlock.Paragraph).spans

    @Test
    fun `04-F2 a link inside bold parses and stays clickable`() {
        val bold = spans("**see [docs](https://example.com) for details**")
            .single() as MdSpan.Bold
        assertEquals(
            listOf(
                MdSpan.Text("see "),
                MdSpan.Link(listOf(MdSpan.Text("docs")), "https://example.com"),
                MdSpan.Text(" for details"),
            ),
            bold.spans,
        )
    }

    @Test
    fun `04-F2 bold inside a link text parses`() {
        val link = spans("[**bold** text](https://example.com)").single() as MdSpan.Link
        assertEquals("https://example.com", link.url)
        assertEquals(
            listOf(MdSpan.Bold(listOf(MdSpan.Text("bold"))), MdSpan.Text(" text")),
            link.spans,
        )
    }

    @Test
    fun `04-F2 code inside bold stays monospace`() {
        val bold = spans("**run `rm -rf` first**").single() as MdSpan.Bold
        assertTrue(bold.spans.any { it is MdSpan.Code && it.text == "rm -rf" })
    }

    @Test
    fun `04-F1 a URL with balanced parens is not cut at the first paren`() {
        val link = spans("[wiki](https://en.wikipedia.org/wiki/Kotlin_(programming_language))")
            .single() as MdSpan.Link
        assertEquals(
            "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
            link.url,
        )
    }

    @Test
    fun `04-F8 a nested bracket inside the link text does not end it`() {
        val link = spans("[array[0]](https://example.com)").single() as MdSpan.Link
        assertEquals(listOf(MdSpan.Text("array[0]")), link.spans)
        assertEquals("https://example.com", link.url)
    }

    @Test
    fun `04-F8 a title after the destination is consumed, not shown`() {
        val link = spans("[x](https://example.com \"the title\")").single() as MdSpan.Link
        assertEquals("https://example.com", link.url)
        assertEquals(listOf(MdSpan.Text("x")), link.spans)
    }

    @Test
    fun `04-F8 an angle destination may contain spaces and parens`() {
        val link = spans("[x](<https://example.com/a b (c)>)").single() as MdSpan.Link
        assertEquals("https://example.com/a b (c)", link.url)
    }

    @Test
    fun `04-F4 a pipe inside inline code does not split the cell`() {
        assertEquals(
            listOf("`a|b`", "c"),
            Markdown.splitCells("| `a|b` | c |"),
        )
    }

    @Test
    fun `04-F4 an escaped pipe is part of the cell`() {
        assertEquals(
            listOf("a|b", "c"),
            Markdown.splitCells("| a\\|b | c |"),
        )
    }

    @Test
    fun `04-F5 double backticks render one code span with no stray backticks`() {
        val code = spans("run ``code`` now").filterIsInstance<MdSpan.Code>()
        assertEquals(listOf(MdSpan.Code("code")), code)
        assertTrue(spans("run ``code`` now").none { it is MdSpan.Text && it.text.contains('`') })
    }

    @Test
    fun `04-F5 a backtick inside double backticks stays literal`() {
        val code = spans("`` `x` ``").filterIsInstance<MdSpan.Code>()
        assertEquals(listOf(MdSpan.Code("`x`")), code)
    }

    @Test
    fun `04-F7 streaming unclosed bold shows its words bold, not raw asterisks`() {
        val bold = streamingSpans("**bold words").single() as MdSpan.Bold
        assertEquals(listOf(MdSpan.Text("bold words")), bold.spans)
        // The final parse is unchanged: unclosed markup stays literal.
        assertTrue(spans("**bold words").none { it is MdSpan.Bold })
    }

    @Test
    fun `04-F7 streaming unclosed fence is prose, not a swallowed code block`() {
        val streaming = Markdown.parse("```kotlin\nval x = 1\n", streaming = true)
        assertTrue(streaming.single() is MdBlock.Paragraph)
        // The final parse keeps the old code-block reading.
        val final = Markdown.parse("```kotlin\nval x = 1\n")
        assertTrue(final.single() is MdBlock.Code)
    }

    @Test
    fun `04-F7 streaming unclosed link shows its words, not raw brackets`() {
        assertEquals(listOf(MdSpan.Text("docs")), streamingSpans("[docs](https://exa"))
        // The final parse keeps the old behaviour: "[docs](..." is not a
        // link (no closing paren), though the bare URL still autolinks.
        val final = spans("[docs](https://exa")
        assertTrue(final.none { it is MdSpan.Link && it.spans == listOf(MdSpan.Text("docs")) })
        assertEquals(MdSpan.Text("[docs]("), final.first())
    }

    @Test
    fun `04-F7 streaming arithmetic stays literal, not italic`() {
        assertTrue(streamingSpans("2 * 3").none { it is MdSpan.Italic })
    }

    @Test
    fun `04-F11 uppercasing is locale-safe under Turkish locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals(
                listOf(MdSpan.Text("I")),
                listOf(MdSpan.Text("i")).toUppercase(),
            )
        } finally {
            Locale.setDefault(previous)
        }
    }
}
