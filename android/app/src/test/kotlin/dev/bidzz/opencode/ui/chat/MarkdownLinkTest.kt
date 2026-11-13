package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Links the model writes must never crash the app on tap (item 2): only
 * http/https/mailto/tel become links, everything else stays plain text.
 */
class MarkdownLinkTest {

    private fun spans(text: String): List<MdSpan> =
        (Markdown.parse(text).single() as MdBlock.Paragraph).spans

    @Test
    fun `an https link stays a link`() {
        val links = spans("[docs](https://example.com)").filterIsInstance<MdSpan.Link>()
        assertEquals(listOf(MdSpan.Link(listOf(MdSpan.Text("docs")), "https://example.com")), links)
    }

    @Test
    fun `an empty url is plain text, not a link`() {
        assertTrue(spans("[docs]()").none { it is MdSpan.Link })
    }

    @Test
    fun `javascript intent and file urls are plain text, not links`() {
        for (url in listOf("javascript:alert(1)", "intent://x", "file:///etc/passwd", "/relative/path")) {
            assertTrue("expected no link for $url", spans("[x]($url)").none { it is MdSpan.Link })
        }
    }

    @Test
    fun `mailto and tel links stay links`() {
        assertEquals(
            listOf(MdSpan.Link(listOf(MdSpan.Text("mail")), "mailto:a@b.com")),
            spans("[mail](mailto:a@b.com)").filterIsInstance<MdSpan.Link>(),
        )
        assertEquals(
            listOf(MdSpan.Link(listOf(MdSpan.Text("call")), "tel:+621234567")),
            spans("[call](tel:+621234567)").filterIsInstance<MdSpan.Link>(),
        )
    }
}
