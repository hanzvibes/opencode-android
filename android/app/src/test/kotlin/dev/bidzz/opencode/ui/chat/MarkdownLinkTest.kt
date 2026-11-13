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

    @Test
    fun `tel links with MMI or USSD characters are plain text, not links (53-F2)`() {
        for (url in listOf("tel:*#06#", "tel:*100#", "tel:+62,123", "tel:+62;123")) {
            assertTrue("expected no link for $url", spans("[x]($url)").none { it is MdSpan.Link })
        }
    }

    @Test
    fun `tel numbers with parens and dashes stay links (53-F2)`() {
        for (url in listOf("tel:(021)123456", "tel:0812.345.678")) {
            assertEquals("expected a link for $url", 1, spans("[x]($url)").filterIsInstance<MdSpan.Link>().size)
        }
    }

    @Test
    fun `a spaced tel number passes the openable gate (53-F2)`() {
        // A raw space cannot appear in a `(...)` link destination — the
        // parser rejects `[x](tel:+62 812-3456)` before the gate is ever
        // consulted (pre-existing CommonMark behavior, not this fix).
        // What 53-F2 owns is the gate itself: a spaced number is a real
        // number per the Fase 5 regex, MMI/USSD is not.
        assertTrue(Markdown.isOpenableLink("tel:+62 812-3456"))
        assertTrue(Markdown.isOpenableLink("tel:(021) 123456"))
        assertTrue(!Markdown.isOpenableLink("tel:*#06#"))
    }
}
