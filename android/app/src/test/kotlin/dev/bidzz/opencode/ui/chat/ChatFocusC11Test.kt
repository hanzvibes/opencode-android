package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.ModelInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C11: M-161 (a permission arriving mid-sentence steals
 * the composer's focus and swallows the next keystrokes) and M-162
 * (the Model/Agent picker search fields never receive focus, and the
 * menus have no keyboard navigation).
 *
 * The pure decisions are tested directly; the Compose wiring (focus
 * requesters, key handlers, the composer callback) is inspection +
 * compile, following the ChatViewModelTest pattern.
 */
class ChatFocusC11Test {

    // M-161: shouldFocusPermissionPrompt

    @Test
    fun `M-161 a permission may take focus when the composer is not focused`() {
        assertTrue(shouldFocusPermissionPrompt(composerFocused = false))
    }

    @Test
    fun `M-161 a permission must not steal focus while the composer is focused`() {
        assertFalse(shouldFocusPermissionPrompt(composerFocused = true))
    }

    // M-162: firstRowIndex / nextRowIndex

    private fun entries() = listOf(
        SelectorEntry.Header("RECENT"),
        SelectorEntry.ModelRow(ModelInfo(id = "m1", providerID = "anthropic")),
        SelectorEntry.ModelRow(ModelInfo(id = "m2", providerID = "anthropic")),
        SelectorEntry.Header("OPENAI"),
        SelectorEntry.ModelRow(ModelInfo(id = "m3", providerID = "openai")),
    )

    @Test
    fun `M-162 the first row skips the section header`() {
        assertEquals(1, firstRowIndex(entries()))
    }

    @Test
    fun `M-162 an empty menu has no first row`() {
        assertEquals(-1, firstRowIndex(emptyList()))
        assertEquals(-1, firstRowIndex(listOf(SelectorEntry.Header("X"))))
    }

    @Test
    fun `M-162 moving down skips headers and wraps to the first row`() {
        val e = entries()
        assertEquals(2, nextRowIndex(e, 1, down = true))
        assertEquals(4, nextRowIndex(e, 2, down = true))
        assertEquals(1, nextRowIndex(e, 4, down = true))
    }

    @Test
    fun `M-162 moving up skips headers and wraps to the last row`() {
        val e = entries()
        assertEquals(4, nextRowIndex(e, 1, down = false))
        assertEquals(2, nextRowIndex(e, 4, down = false))
        assertEquals(1, nextRowIndex(e, 2, down = false))
    }

    @Test
    fun `M-162 no highlight yet restarts at the first row going down and the last going up`() {
        val e = entries()
        assertEquals(1, nextRowIndex(e, -1, down = true))
        assertEquals(4, nextRowIndex(e, -1, down = false))
    }

    @Test
    fun `M-162 a stale highlight on a header restarts at an end`() {
        val e = entries()
        assertEquals(1, nextRowIndex(e, 0, down = true))
        assertEquals(4, nextRowIndex(e, 3, down = false))
    }

    @Test
    fun `M-162 an empty menu has nowhere to move`() {
        assertEquals(-1, nextRowIndex(emptyList(), -1, down = true))
        assertEquals(-1, nextRowIndex(emptyList(), -1, down = false))
    }
}
