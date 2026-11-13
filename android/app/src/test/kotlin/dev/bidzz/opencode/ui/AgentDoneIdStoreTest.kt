package dev.bidzz.opencode.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Agent-done notification ids must be unique per session and stable
 * (audit-100 55-F8/56-F5): the old `sessionID.hashCode()` could collide
 * between two sessions, silently replacing one notification with the
 * other's — and tapping it opened the wrong session.
 */
class AgentDoneIdStoreTest {

    @Test
    fun `a new session takes the next free id`() {
        val result = assignAgentDoneId("ses-a", emptyMap(), 10_000)
        assertEquals(10_000, result.id)
        assertEquals(10_001, result.nextFree)
        assertTrue(result.isNew)
    }

    @Test
    fun `a known session keeps its id`() {
        val assigned = mapOf("ses-a" to 10_000, "ses-b" to 10_001)
        val result = assignAgentDoneId("ses-a", assigned, 10_002)
        assertEquals(10_000, result.id)
        assertEquals(10_002, result.nextFree)
        assertFalse(result.isNew)
    }

    @Test
    fun `two sessions never share an id`() {
        val first = assignAgentDoneId("ses-a", emptyMap(), 10_000)
        val assigned = mapOf("ses-a" to first.id)
        val second = assignAgentDoneId("ses-b", assigned, first.nextFree)
        assertEquals(10_001, second.id)
        assertTrue(second.isNew)
    }
}
