package dev.bidzz.opencode.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Animation C shared-element pilot: the session-title flight between a
 * session row (SessionListScreen) and the chat header (ChatHeader).
 *
 * Both sides key their shared element through [sessionTitleSharedKey],
 * so the one thing that can silently break the flight — the keys
 * drifting apart — is locked here. The key is the session id, never the
 * title text: a rename must not break the match, and two sessions can
 * share a title.
 */
class SharedTitleTransitionTest {

    @Test
    fun `the key is the session id`() {
        assertEquals("ses_abc123", sessionTitleSharedKey("ses_abc123"))
    }

    @Test
    fun `different sessions get different keys`() {
        assertNotEquals(sessionTitleSharedKey("ses_a"), sessionTitleSharedKey("ses_b"))
    }

    @Test
    fun `the same session always gets the same key`() {
        assertEquals(sessionTitleSharedKey("ses_a"), sessionTitleSharedKey("ses_a"))
    }
}
