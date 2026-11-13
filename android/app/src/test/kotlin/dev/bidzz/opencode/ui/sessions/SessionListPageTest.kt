package dev.bidzz.opencode.ui.sessions

import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.SessionMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 6 batch 6d: the session list's pure decisions — rename revert,
 * ghost-pin pruning, page merging and the preview walk — tested without
 * a live engine. The ViewModel runs these inside viewModelScope
 * coroutines the JVM unit tests cannot drive (no Main dispatcher on the
 * test classpath), so the pure helpers carry the coverage.
 */
class SessionListPageTest {

    private fun row(id: String, title: String, pinned: Boolean = false) =
        SessionRow(id = id, title = title, directory = null, age = null, cost = null, isPinned = pinned)

    private fun state(vararg rows: SessionRow) =
        SessionListUiState(rows = rows.toList())

    private fun user(id: String, text: String) =
        SessionMessage(id = id, type = "user", text = text)

    private fun assistant(id: String) =
        SessionMessage(id = id, type = "assistant")

    @Test
    fun `65-F2 a failed rename reverts the title and keeps the failure for retry`() {
        val before = state(row("s1", "Old"), row("s2", "Other"))

        val after = resolveRenameFailure(
            before, "s1", "Old", "New",
            ApiResult.Failure(code = 500, tag = null, message = "boom"),
        )

        assertEquals("Old", after.rows[0].title)
        assertEquals("Other", after.rows[1].title)
        assertEquals(500, after.renameFailure?.code)
        assertEquals("s1", after.renameFailedSessionID)
        assertEquals("New", after.renameFailedTitle)
        assertNull(after.renameFailureKey)
    }

    @Test
    fun `65-F3 an engine-dead rename reverts the title and names the missing engine`() {
        val before = state(row("s1", "Old"))

        val after = resolveRenameFailure(before, "s1", "Old", "New", null, failureKey = 123)

        assertEquals("Old", after.rows[0].title)
        assertNull(after.renameFailure)
        assertEquals("s1", after.renameFailedSessionID)
        assertEquals("New", after.renameFailedTitle)
        assertEquals(123, after.renameFailureKey)
    }

    @Test
    fun `80-F6 ghost pins are pruned only when the list is known complete`() {
        val pinned = setOf("gone", "kept", "other-project")
        val listed = setOf("kept")

        assertEquals(setOf("kept"), pruneGhostPins(pinned, listed, incomplete = false))
        // Capped at a full page: an unlisted pin may be a real session out of view.
        assertEquals(pinned, pruneGhostPins(pinned, listed, incomplete = true))
    }

    @Test
    fun `19-F3 appending a page never duplicates a session already shown`() {
        val existing = listOf(row("s1", "One"), row("s2", "Two"))
        val page = listOf(row("s2", "Two"), row("s3", "Three"))

        assertEquals(listOf("s1", "s2", "s3"), mergeSessionPage(existing, page).map { it.id })
    }

    @Test
    fun `19-F2 the walk keeps the oldest user message seen so far`() {
        // Newest page first: its oldest user message is the candidate until
        // an older page replaces it.
        val newest = updatePreviewCandidate(null, listOf(user("m9", "latest"), assistant("m8")))
        assertEquals("latest", newest)

        val older = updatePreviewCandidate(newest, listOf(user("m1", "first"), assistant("m0")))
        assertEquals("first", older)
    }

    @Test
    fun `19-F2 a page with no user message keeps the earlier candidate`() {
        val candidate = updatePreviewCandidate("earliest", listOf(assistant("m7"), assistant("m6")))

        assertEquals("earliest", candidate)
    }

    @Test
    fun `19-F2 blank user text is not a preview`() {
        val candidate = updatePreviewCandidate(null, listOf(user("m1", "   ")))

        assertNull(candidate)
    }

    @Test
    fun `19-F2 the candidate survives an empty oldest page`() {
        assertTrue(updatePreviewCandidate("kept", emptyList()) == "kept")
    }
}
