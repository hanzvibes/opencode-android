package dev.bidzz.opencode.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D14 (m-531/m-789/m-979): the agent-done watcher posts unless
 * the user is watching that exact session's chat.
 */
class EngineServiceGateTest {

    @Test
    fun `m-531 a run ending while the app is backgrounded posts`() {
        assertTrue(shouldPostAgentDone(false, false, "a", "a"))
        assertTrue(shouldPostAgentDone(false, false, null, "a"))
    }

    @Test
    fun `m-531 a run ending behind the lock posts`() {
        assertTrue(shouldPostAgentDone(true, true, "a", "a"))
        assertTrue(shouldPostAgentDone(true, true, null, "a"))
    }

    @Test
    fun `m-789 a run ending on another screen posts`() {
        assertTrue(shouldPostAgentDone(true, false, null, "a"))
    }

    @Test
    fun `m-979 a run ending while another session is open posts`() {
        assertTrue(shouldPostAgentDone(true, false, "b", "a"))
    }

    @Test
    fun `m-979 a run ending while its own chat is visible is suppressed`() {
        assertFalse(shouldPostAgentDone(true, false, "a", "a"))
    }

    @Test
    fun `m-531 a stale visible id from before backgrounding does not suppress`() {
        assertTrue(shouldPostAgentDone(false, false, "a", "a"))
    }
}
