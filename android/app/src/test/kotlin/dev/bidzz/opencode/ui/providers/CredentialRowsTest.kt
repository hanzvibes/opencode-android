package dev.bidzz.opencode.ui.providers

import dev.bidzz.opencode.core.api.dto.CredentialEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mapping from the engine's stored credentials to the rows the manage screen shows.
 *
 * Two things here are worth pinning down because both fail silently: the match on
 * `integrationID`, and the label fallback for keys stored without one.
 */
class CredentialRowsTest {

    private fun entry(
        id: String,
        integrationID: String,
        label: String = "",
        active: Boolean = false,
    ) = CredentialEntry(id = id, integrationID = integrationID, label = label, active = active)

    @Test
    fun `only the provider's own credentials come back`() {
        val entries = listOf(
            entry("cred_1", "302ai"),
            entry("cred_2", "openrouter"),
            entry("cred_3", "302ai"),
        )
        val ids = rowsFor(entries, "302ai").map { it.id }
        assertEquals(listOf("cred_1", "cred_3"), ids)
    }

    @Test
    fun `matching is exact, not by prefix`() {
        // The control for the test above: an integration whose id starts with the one
        // being filtered must not match, otherwise a `contains`/`startsWith` slip in
        // the filter would still leave that test green.
        val entries = listOf(
            entry("cred_1", "302ai"),
            entry("cred_2", "302ai-plus"),
            entry("cred_3", "ai"),
        )
        val rows = rowsFor(entries, "302ai")
        assertEquals(listOf("cred_1"), rows.map { it.id })
        assertEquals(1, rows.size)
    }

    @Test
    fun `the active key is listed first`() {
        val entries = listOf(
            entry("cred_inactive", "302ai", label = "a-inactive", active = false),
            entry("cred_active", "302ai", label = "z-active", active = true),
        )
        val rows = rowsFor(entries, "302ai")
        assertTrue(rows.first().inUse)
        assertEquals("cred_active", rows.first().id)
    }

    @Test
    fun `an empty label still renders a readable title`() {
        // Every key this app stores is created without a label, so the engine returns
        // "" and the row would otherwise be blank.
        val rows = rowsFor(listOf(entry("cred_1", "302ai")), "302ai")
        assertEquals("API key", rows.single().title)
    }

    @Test
    fun `a label from the engine is shown as given`() {
        val rows = rowsFor(listOf(entry("cred_1", "302ai", label = "work key")), "302ai")
        assertEquals("work key", rows.single().title)
    }

    @Test
    fun `long ids are shortened with both ends visible`() {
        val shortened = shortId("cred_17e4b2f0a9c3d8e5b1f2a903")
        assertEquals("cred_1…a903", shortened)
        assertTrue(shortened.startsWith("cred_1"))
        assertTrue(shortened.endsWith("a903"))
        assertFalse(shortened.contains("c3d8e5"))
    }

    @Test
    fun `an id no longer than both ends is shown whole`() {
        // The degenerate case: slicing without this guard prints an ellipsis and a
        // suffix that repeats characters already in the prefix.
        assertEquals("cred_abc12", shortId("cred_abc12"))
        assertEquals("ab", shortId("ab"))
    }

    @Test
    fun `losing the last key disconnects the provider`() {
        val notice = afterDeleteNotice(0, "302.AI")
        assertTrue(notice, notice.contains("no key left"))
        assertTrue(notice, notice.contains("until you add one"))
    }

    @Test
    fun `losing one of several keys keeps the provider usable`() {
        val notice = afterDeleteNotice(2, "302.AI")
        assertTrue(notice, notice.contains("2 left"))
        assertFalse(notice, notice.contains("no key left"))
    }

    @Test
    fun `rows are counted from what the engine returned, not decremented`() {
        // Guards the assumption `deleteCredential` relies on: after removing cred_1 the
        // remaining count comes from the re-read list.
        val entries = listOf(entry("cred_1", "302ai"), entry("cred_2", "302ai"))
        val after = entries.filter { it.id != "cred_1" }
        assertEquals(1, rowsFor(after, "302ai").size)
        assertEquals(0, rowsFor(emptyList(), "302ai").size)
    }
}
