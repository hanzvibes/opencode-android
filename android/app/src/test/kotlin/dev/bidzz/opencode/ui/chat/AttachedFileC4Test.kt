package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C4: the text decode (M-074) and the attachment-block
 * split (M-079). Both helpers are pure — exact bytes in, exact text out.
 */
class AttachedFileC4Test {

    private fun file(name: String, content: String) =
        AttachedFile(id = "id-$name", name = name, content = content)

    @Test
    fun `M-074 valid UTF-8 still decodes as before`() {
        assertEquals("hello café", decodeTextFile("hello café".encodeToByteArray()))
    }

    @Test
    fun `M-074 UTF-16LE with BOM decodes`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "hello".toByteArray(Charsets.UTF_16LE)
        assertEquals("hello", decodeTextFile(bytes))
    }

    @Test
    fun `M-074 UTF-16BE with BOM decodes`() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            "hello".toByteArray(Charsets.UTF_16BE)
        assertEquals("hello", decodeTextFile(bytes))
    }

    @Test
    fun `M-074 Latin-1 bytes fall back instead of refusing`() {
        // café in ISO-8859-1: the 0xE9 byte is not valid UTF-8.
        val bytes = byteArrayOf('c'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0xE9.toByte())
        assertEquals("café", decodeTextFile(bytes))
    }

    @Test
    fun `M-074 Windows-1252 smart quotes fall back`() {
        // 0x93/0x94 are the curly quotes Windows-1252 puts where Latin-1
        // has control characters — the decode must take the 1252 glyphs.
        val bytes = byteArrayOf(0x93.toByte(), 'x'.code.toByte(), 0x94.toByte())
        assertEquals("\u201Cx\u201D", decodeTextFile(bytes))
    }

    @Test
    fun `M-074 a UTF-16LE file without BOM still decodes as UTF-8 with NULs for the caller to refuse`() {
        // No BOM means no UTF-16 detection: the bytes are valid UTF-8
        // (NUL is a valid code point), so the decode succeeds and the
        // caller's NUL check — not the decoder — refuses the file.
        val bytes = "h\u0000i\u0000".encodeToByteArray()
        assertEquals("h\u0000i\u0000", decodeTextFile(bytes))
    }

    @Test
    fun `M-079 plain text without fences splits to itself`() {
        val (typed, attachments) = splitAttachmentBlocks("just some words")
        assertEquals("just some words", typed)
        assertTrue(attachments.isEmpty())
    }

    @Test
    fun `M-079 typed text plus one block splits cleanly`() {
        val (typed, attachments) = splitAttachmentBlocks(
            "review this\n\n```notes.md\nFILE\n```",
        )
        assertEquals("review this", typed)
        assertEquals(listOf("notes.md" to "FILE"), attachments.map { it.name to it.content })
    }

    @Test
    fun `M-079 two blocks come back as two attachments`() {
        val full = buildMessageWithAttachments(
            "check both",
            listOf(file("a.txt", "A"), file("b.txt", "B")),
        )
        val (typed, attachments) = splitAttachmentBlocks(full)
        assertEquals("check both", typed)
        assertEquals(listOf("a.txt" to "A", "b.txt" to "B"), attachments.map { it.name to it.content })
    }

    @Test
    fun `M-079 attachments-only text leaves no typed text`() {
        val (typed, attachments) = splitAttachmentBlocks("```a.txt\nA\n```")
        assertEquals("", typed)
        assertEquals(listOf("a.txt" to "A"), attachments.map { it.name to it.content })
    }

    @Test
    fun `M-079 a user code fence stays inline`() {
        val text = "explain this\n\n```kotlin\nval x = 1\n```"
        val (typed, attachments) = splitAttachmentBlocks(text)
        assertEquals(text, typed)
        assertTrue(attachments.isEmpty())
    }

    @Test
    fun `M-079 an unclosed fence stays inline`() {
        val text = "half a fence\n\n```notes.md\nnever closed"
        val (typed, attachments) = splitAttachmentBlocks(text)
        assertEquals(text, typed)
        assertTrue(attachments.isEmpty())
    }

    @Test
    fun `M-079 split then rebuild reproduces the sent text`() {
        // The round-trip is the safety net: even a user fence that looks
        // like a filename (config.json) re-emits identically, so a
        // misread block costs a chip while editing, never content.
        val full = buildMessageWithAttachments(
            "review this",
            listOf(file("notes.md", "a\nb"), file("code.md", "```\ninner\n```")),
        )
        val (typed, attachments) = splitAttachmentBlocks(full)
        assertEquals(full, buildMessageWithAttachments(typed, attachments))

        val userFence = "look\n\n```config.json\n{\"a\": 1}\n```"
        val (typed2, parsed2) = splitAttachmentBlocks(userFence)
        assertEquals(userFence, buildMessageWithAttachments(typed2, parsed2))
    }
}
