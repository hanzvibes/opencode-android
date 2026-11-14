package dev.bidzz.opencode.ui.sessions

import dev.bidzz.opencode.core.api.dto.SessionMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C5: the lazy session previews (M-089/M-099) and the
 * search across later user messages (M-097). Pure helpers only — the
 * ViewModel runs its preview job in viewModelScope, which the JVM
 * unit tests cannot drive (no Main dispatcher on the test classpath),
 * so the pure helpers carry the coverage, same as SessionListPageTest.
 */
class SessionListC5Test {

    private fun row(id: String, title: String) =
        SessionRow(id = id, title = title, directory = null, age = null, cost = null, isPinned = false)

    private fun user(id: String, text: String) =
        SessionMessage(id = id, type = "user", text = text)

    private fun assistant(id: String) =
        SessionMessage(id = id, type = "assistant")

    // M-097: accumulateSearchText.

    @Test
    fun `M-097 the walk accumulates user messages in visit order`() {
        val page = listOf(
            user("m1", "first ask"),
            assistant("m2"),
            user("m3", "second ask"),
        )
        assertEquals("first ask\nsecond ask", accumulateSearchText(null, page))
    }

    @Test
    fun `M-097 blank and assistant-only pages add nothing`() {
        val page = listOf(user("m1", "   "), assistant("m2"), user("m3", ""))
        assertNull(accumulateSearchText(null, page))
        assertEquals("kept", accumulateSearchText("kept", page))
    }

    @Test
    fun `M-097 the accumulated text is capped and never grows past the cap`() {
        val longText = "x".repeat(SEARCH_TEXT_MAX_CHARS + 100)
        val capped = accumulateSearchText(null, listOf(user("m1", longText)))
        assertEquals(SEARCH_TEXT_MAX_CHARS, capped?.length)
        // Once capped, later pages are ignored — the cap is a budget,
        // not a window that keeps sliding.
        assertEquals(capped, accumulateSearchText(capped, listOf(user("m2", "more"))))
    }

    // M-097: matchesSessionQuery on the accumulated text.

    @Test
    fun `M-097 a session is findable by a later user message, not just its preview`() {
        val detail = SessionDetail(preview = "first ask", searchText = "first ask\nlater ask")
        assertTrue(matchesSessionQuery("later ask", row("s1", "Untitled"), detail))
    }

    @Test
    fun `M-097 a query that matches nothing still finds nothing`() {
        val detail = SessionDetail(preview = "first ask", searchText = "first ask\nlater ask")
        assertFalse(matchesSessionQuery("never asked", row("s1", "Untitled"), detail))
    }

    @Test
    fun `M-097 a session with no preview yet is not searchable`() {
        assertFalse(matchesSessionQuery("anything", row("s1", "Untitled"), null))
        assertFalse(matchesSessionQuery("anything", row("s1", "Untitled"), SessionDetail()))
    }

    // M-089: previewKnown.

    @Test
    fun `M-089 a detail starts unknown until the walk completes`() {
        assertFalse(SessionDetail().previewKnown)
        assertNull(SessionDetail().preview)
        assertNull(SessionDetail().searchText)
    }
}
