package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachedFileTest {

    private fun file(name: String, content: String) =
        AttachedFile(id = "id-$name", name = name, content = content)

    @Test
    fun `fenced block is headed by the filename`() {
        assertEquals(
            "```notes.txt\nhello\n```",
            file("notes.txt", "hello").toFencedBlock(),
        )
    }

    @Test
    fun `fence grows past backticks inside the content`() {
        // Content containing ``` must not close the block early: the fence
        // is one backtick longer than the longest run inside it.
        assertEquals(
            "````code.md\n```\ninner\n```\n````",
            file("code.md", "```\ninner\n```").toFencedBlock(),
        )
    }

    @Test
    fun `filename is sanitised for the fence line only`() {
        val block = file("we`ird\nname.txt", "x").toFencedBlock()
        assertTrue(block.startsWith("```we'ird name.txt\n"))
        assertTrue(block.endsWith("\n```"))
    }

    @Test
    fun `typed text comes first, then one block per attachment`() {
        val sent = buildMessageWithAttachments(
            typedText = "review this",
            attachments = listOf(file("a.txt", "A"), file("b.txt", "B")),
        )
        assertEquals(
            "review this\n\n```a.txt\nA\n```\n\n```b.txt\nB\n```",
            sent,
        )
    }

    @Test
    fun `attachments only send just the blocks`() {
        assertEquals(
            "```a.txt\nA\n```",
            buildMessageWithAttachments("", listOf(file("a.txt", "A"))),
        )
    }

    @Test
    fun `nothing typed and nothing attached sends nothing`() {
        assertEquals("", buildMessageWithAttachments("", emptyList()))
        assertTrue(buildMessageWithAttachments("   ", emptyList()).isBlank())
    }

    @Test
    fun `size cap is 64 KiB`() {
        assertEquals(65_536, MAX_INLINE_FILE_BYTES)
    }

    @Test
    fun `same name and content is a duplicate pick`() {
        val existing = listOf(file("notes.txt", "hello"))
        assertTrue(isDuplicateAttachment(existing, file("notes.txt", "hello")))
    }

    @Test
    fun `same name with different content is kept`() {
        // Two folders' notes can share a filename.
        val existing = listOf(file("notes.txt", "hello"))
        assertTrue(!isDuplicateAttachment(existing, file("notes.txt", "different")))
    }

    @Test
    fun `same content under a different name is kept`() {
        val existing = listOf(file("a.txt", "hello"))
        assertTrue(!isDuplicateAttachment(existing, file("b.txt", "hello")))
    }

    @Test
    fun `nothing pending means no duplicate`() {
        assertTrue(!isDuplicateAttachment(emptyList(), file("notes.txt", "hello")))
    }

    @Test
    fun `removing one attachment by id keeps the others`() {
        val pending = listOf(file("a.txt", "A"), file("b.txt", "B"))
        val remaining = withoutAttachment(pending, "id-a.txt")
        assertEquals(listOf(file("b.txt", "B")), remaining)
    }

    @Test
    fun `removing an unknown id changes nothing`() {
        val pending = listOf(file("a.txt", "A"))
        assertEquals(pending, withoutAttachment(pending, "id-nope"))
    }

    @Test
    fun `a removed attachment is never sent`() {
        val pending = listOf(file("a.txt", "A"), file("b.txt", "B"))
        val sent = buildMessageWithAttachments(
            typedText = "",
            attachments = withoutAttachment(pending, "id-a.txt"),
        )
        assertEquals("```b.txt\nB\n```", sent)
    }

    @Test
    fun `attachment size is the UTF-8 byte count of its text`() {
        assertEquals(5, attachmentByteSize(file("a.txt", "hello")))
        assertEquals(0, attachmentByteSize(file("empty.txt", "")))
    }

    @Test
    fun `attachment size formats as bytes under one KiB`() {
        assertEquals("0 B", formatAttachmentSize(0))
        assertEquals("512 B", formatAttachmentSize(512))
        assertEquals("1023 B", formatAttachmentSize(1023))
    }

    @Test
    fun `attachment size formats as kilobytes with one decimal`() {
        assertEquals("1 KB", formatAttachmentSize(1024))
        assertEquals("1.5 KB", formatAttachmentSize(1536))
        assertEquals("64 KB", formatAttachmentSize(65_536))
    }
}
