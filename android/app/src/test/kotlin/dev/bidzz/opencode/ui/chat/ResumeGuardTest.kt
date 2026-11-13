package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 6 batch 6c (58-F7): resuming the remembered session must not
 * clobber a session the user opened while the verification GET was in
 * flight.
 */
class ResumeGuardTest {

    @Test
    fun `58-F7 nothing opened while verifying opens the remembered session`() {
        assertTrue(
            shouldOpenAfterResume(
                attachedSessionID = null,
                targetSessionID = "s1",
                repositoryChanged = false,
            ),
        )
    }

    @Test
    fun `58-F7 the same session already open is a harmless re-open`() {
        assertTrue(
            shouldOpenAfterResume(
                attachedSessionID = "s1",
                targetSessionID = "s1",
                repositoryChanged = false,
            ),
        )
    }

    @Test
    fun `58-F7 a different session opened meanwhile wins`() {
        assertFalse(
            shouldOpenAfterResume(
                attachedSessionID = "s2",
                targetSessionID = "s1",
                repositoryChanged = false,
            ),
        )
    }

    @Test
    fun `58-F7 a replaced repository never opens on the stale one`() {
        assertFalse(
            shouldOpenAfterResume(
                attachedSessionID = null,
                targetSessionID = "s1",
                repositoryChanged = true,
            ),
        )
    }
}
