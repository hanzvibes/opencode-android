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

    @Test
    fun `04-F3 a 3-backtick line does not close a 4-backtick block`() {
        val md = "````markdown\n```kotlin\nval x = 1\n```\n````"
        val code = Markdown.parse(md).single() as MdBlock.Code
        assertEquals("markdown", code.language)
        assertEquals("```kotlin\nval x = 1\n```", code.code)
    }

    @Test
    fun `04-F3 a fence with an info string does not close a block`() {
        val code = Markdown.parse("```\n```kotlin\ncode\n```").single() as MdBlock.Code
        assertEquals(null, code.language)
        assertEquals("```kotlin\ncode", code.code)
    }

    @Test
    fun `04-F3 a longer fence of the same char does close`() {
        val code = Markdown.parse("````\ncode\n`````").single() as MdBlock.Code
        assertEquals("code", code.code)
    }

    @Test
    fun `04-F3 a backtick line does not close a tilde block`() {
        val code = Markdown.parse("~~~\n```\n~~~").single() as MdBlock.Code
        assertEquals("```", code.code)
    }

    @Test
    fun `04-F3 an indented closing fence still closes`() {
        val code = Markdown.parse("```\ncode\n  ```").single() as MdBlock.Code
        assertEquals("code", code.code)
    }
}
