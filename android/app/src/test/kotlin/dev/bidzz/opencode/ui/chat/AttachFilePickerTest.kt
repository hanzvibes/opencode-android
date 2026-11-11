package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.ui.files.FileRow
import dev.bidzz.opencode.ui.files.rankQuickOpen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the attach / @-mention picker logic: mention token
 * detection, plain-text insertion, and the shared quick-open ranking the
 * picker reuses (no second file-listing implementation).
 */
class AttachFilePickerTest {

    @Test
    fun `extractMentionQuery finds a trailing at-token`() {
        assertEquals("", extractMentionQuery("@"))
        assertEquals("rea", extractMentionQuery("@rea"))
        assertEquals("src/main", extractMentionQuery("look at @src/main"))
        assertEquals("main.kt", extractMentionQuery("fix @main.kt"))
    }

    @Test
    fun `extractMentionQuery rejects non-tokens and finished mentions`() {
        // No @ at all.
        assertNull(extractMentionQuery("regular text"))
        assertNull(extractMentionQuery(""))
        // @ mid-word is not a mention (user@host).
        assertNull(extractMentionQuery("user@host"))
        assertNull(extractMentionQuery("fix @a/b@c"))
        // A space after the @ ends the token: a finished mention, or "@ ".
        assertNull(extractMentionQuery("@README.md "))
        assertNull(extractMentionQuery("see @README.md and fix it"))
        assertNull(extractMentionQuery("@ "))
    }

    @Test
    fun `insertMention appends to an empty or plain draft`() {
        assertEquals("@README.md ", insertMention("", "README.md"))
        assertEquals("fix @README.md ", insertMention("fix", "README.md"))
        assertEquals("fix @README.md ", insertMention("fix ", "README.md"))
        assertEquals("a\n@README.md ", insertMention("a\n", "README.md"))
    }

    @Test
    fun `insertMention replaces a trailing token being typed`() {
        assertEquals("see @README.md ", insertMention("see @READ", "README.md"))
        assertEquals("@src/main.kt ", insertMention("@src/ma", "src/main.kt"))
    }

    @Test
    fun `insertMention appends after a finished mention`() {
        assertEquals(
            "see @README.md @other.kt ",
            insertMention("see @README.md ", "other.kt"),
        )
    }

    @Test
    fun `removeMentionToken drops only a trailing token`() {
        assertEquals("see ", removeMentionToken("see @READ"))
        assertEquals("", removeMentionToken("@READ"))
        assertEquals("plain text", removeMentionToken("plain text"))
        assertEquals("see @README.md ", removeMentionToken("see @README.md "))
    }

    @Test
    fun `picker ranking is the shared quick-open ranking`() {
        val files = listOf(
            FileRow(path = "src/main.kt", name = "main.kt", isDirectory = false),
            FileRow(path = "docs/README.md", name = "README.md", isDirectory = false),
            FileRow(path = "README.md", name = "README.md", isDirectory = false),
            FileRow(path = "src/readme_helper.kt", name = "readme_helper.kt", isDirectory = false),
        )

        // Exact filename matches, in index order within the bucket; the
        // prefix/substring/path buckets follow (none match here).
        val ranked = rankQuickOpen(files, "README.md")
        assertEquals(2, ranked.size)
        assertEquals("docs/README.md", ranked[0].path)
        assertEquals("README.md", ranked[1].path)

        // Empty query returns the index order, capped at 50 by default.
        assertEquals(4, rankQuickOpen(files, "").size)
        assertTrue(rankQuickOpen(files, "zzz").isEmpty())
    }
}
