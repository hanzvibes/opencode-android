package dev.bidzz.opencode.ui

import dev.bidzz.opencode.core.api.dto.EventTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 5 (55-F6): the service-side agent-done watcher and the UI
 * trigger agree on which engine events end a run.
 */
class AgentDoneNotifierTest {

    @Test
    fun `55-F6 succeeded and failed end a run`() {
        assertTrue(isAgentRunEndEvent(EventTypes.EXECUTION_SUCCEEDED))
        assertTrue(isAgentRunEndEvent(EventTypes.EXECUTION_FAILED))
    }

    @Test
    fun `55-F6 started and interrupted do not end a run`() {
        assertFalse(isAgentRunEndEvent(EventTypes.EXECUTION_STARTED))
        assertFalse(isAgentRunEndEvent(EventTypes.EXECUTION_INTERRUPTED))
        assertFalse(isAgentRunEndEvent(EventTypes.TEXT_DELTA))
        assertFalse(isAgentRunEndEvent(EventTypes.SESSION_CREATED))
        assertFalse(isAgentRunEndEvent("session.unknown"))
    }

    @Test
    fun `D13 m-788 the agent-done notification is secret while App Lock is on`() {
        assertEquals(
            android.app.Notification.VISIBILITY_SECRET,
            agentDoneVisibility(true),
        )
    }

    @Test
    fun `D13 m-788 the agent-done notification stays private without App Lock`() {
        assertEquals(
            android.app.Notification.VISIBILITY_PRIVATE,
            agentDoneVisibility(false),
        )
    }
}
