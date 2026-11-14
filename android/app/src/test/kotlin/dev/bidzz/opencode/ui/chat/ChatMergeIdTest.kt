package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Audit-200 B5 (M-036): contiguous assistant chunks merge under the
 * LATER chunk's id, not the first chunk's — same rule as the repository
 * merge ([mergeAssistantMessages]). A turn grows at its head when "load
 * earlier" pulls in older chunks; the first chunk's id changed on every
 * prepend, so a read watermark pointing at the turn vanished from the
 * window and the unread badge came back as a ghost.
 */
class ChatMergeIdTest {

    private fun assistant(id: String, text: String) = ChatMessage(
        id = id,
        role = Role.ASSISTANT,
        text = text,
    )

    @Test
    fun `a merged turn keeps the later chunk's id`() {
        val merged = mergeContiguousAssistant(
            listOf(assistant("a1", "first "), assistant("a2", "second")),
        )

        assertEquals(1, merged.size)
        assertEquals("a2", merged[0].id)
        assertEquals("first \n\nsecond", merged[0].text)
    }

    @Test
    fun `prepending an older chunk leaves the merged id alone`() {
        val merged = mergeContiguousAssistant(
            listOf(
                assistant("a1", "first "),
                assistant("a2", "middle "),
                assistant("a3", "last"),
            ),
        )

        assertEquals(1, merged.size)
        assertEquals("a3", merged[0].id)
        assertEquals("first \n\nmiddle \n\nlast", merged[0].text)
    }

    @Test
    fun `a user message still breaks the merge`() {
        val merged = mergeContiguousAssistant(
            listOf(
                assistant("a1", "one"),
                ChatMessage(id = "u1", role = Role.USER, text = "hi"),
                assistant("a2", "two"),
            ),
        )

        assertEquals(3, merged.size)
        assertEquals("a1", merged[0].id)
        assertEquals("a2", merged[2].id)
    }
}
