package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownCodeTest {
    @Test
    fun `blank lines around a fenced block are dropped, inner ones kept`() {
        val md = "```html\n\n  \n<p>a</p>\n\n<p>b</p>\n\n```"
        val code = Markdown.parse(md).single() as MdBlock.Code
        assertEquals("html", code.language)
        assertEquals("<p>a</p>\n\n<p>b</p>", code.code)
    }

    @Test
    fun `indentation of the first real line is kept`() {
        val code = Markdown.parse("```\n\n    indented\n```").single() as MdBlock.Code
        assertEquals("    indented", code.code)
    }
}
