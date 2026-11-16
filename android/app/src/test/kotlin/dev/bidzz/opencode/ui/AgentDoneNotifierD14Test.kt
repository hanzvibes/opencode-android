package dev.bidzz.opencode.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D14 (notifikasi): pure-unit tests for the channel-id
 * derivation (m-370) and the legacy-id cancel guard (m-790). The Android
 * halves — posting, pruning, cancelling — are pinned by AuditD14Test.
 */
class AgentDoneNotifierD14Test {

    @Test
    fun `m-370 the channel id carries the display language`() {
        assertEquals("agent_done_en", notificationChannelId("agent_done", "en"))
        assertEquals("agent_done_id", notificationChannelId("agent_done", "id"))
        assertEquals("engine_en", notificationChannelId("engine", "en"))
        assertEquals("engine_failure_id", notificationChannelId("engine_failure", "id"))
    }

    @Test
    fun `m-370 the phone old code for Indonesian maps to id`() {
        assertEquals("agent_done_id", notificationChannelId("agent_done", "in"))
    }

    @Test
    fun `m-790 a free legacy id may be cancelled`() {
        assertTrue(shouldCancelLegacyAgentDoneId(12345, 10000, setOf(10000, 10001)))
    }

    @Test
    fun `m-790 the sessions own stable id is never cancelled`() {
        assertFalse(shouldCancelLegacyAgentDoneId(10000, 10000, setOf(10000)))
    }

    @Test
    fun `m-790 another sessions stable id is never cancelled`() {
        assertFalse(shouldCancelLegacyAgentDoneId(10001, 10000, setOf(10000, 10001)))
    }
}
