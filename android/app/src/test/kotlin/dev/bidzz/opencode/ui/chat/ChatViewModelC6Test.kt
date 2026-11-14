package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Audit-200 Fase C6: the pure guards behind the VCS refresh (M-104/
 * M-105/M-107) and the delete-time interrupt (M-108). The loads and the
 * interrupt themselves need a live engine, so only the decisions are
 * pinned here.
 */
class ChatViewModelC6Test {

    @Test
    fun `M-107 a workspace switch clears the stored VCS values`() {
        assertEquals(true, shouldClearStaleVcs("/repo", "/repo", "/other"))
        assertEquals(true, shouldClearStaleVcs("/repo", null, "/other"))
        assertEquals(true, shouldClearStaleVcs(null, "/repo", "/other"))
    }

    @Test
    fun `M-107 the same workspace keeps its VCS values`() {
        assertEquals(false, shouldClearStaleVcs("/repo", "/repo", "/repo"))
    }

    @Test
    fun `M-107 a half-loaded workspace still clears on switch`() {
        // Branches loaded for A, details never landed: the stored list
        // is still A's, so switching to B must clear it.
        assertEquals(true, shouldClearStaleVcs("/repo", null, "/other"))
    }

    @Test
    fun `M-108 deleting the busy open session interrupts it`() {
        assertEquals(true, shouldInterruptSession(busy = true, currentSessionID = "s1", targetSessionID = "s1"))
    }

    @Test
    fun `M-108 deleting another session interrupts nothing`() {
        assertEquals(false, shouldInterruptSession(busy = true, currentSessionID = "s1", targetSessionID = "s2"))
    }

    @Test
    fun `M-108 deleting the open session while idle interrupts nothing`() {
        assertEquals(false, shouldInterruptSession(busy = false, currentSessionID = "s1", targetSessionID = "s1"))
    }

    @Test
    fun `M-108 deleting with no session open interrupts nothing`() {
        assertEquals(false, shouldInterruptSession(busy = true, currentSessionID = null, targetSessionID = "s1"))
    }
}
