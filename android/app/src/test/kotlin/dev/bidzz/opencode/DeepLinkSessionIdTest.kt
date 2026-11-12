package dev.bidzz.opencode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 5 (53-F6): a deep-link session id must be shaped like a real
 * session id before it reaches the engine.
 */
class DeepLinkSessionIdTest {

    @Test
    fun `53-F6 real session ids pass`() {
        assertTrue(isValidSessionId("550e8400-e29b-41d4-a716-446655440000"))
        assertTrue(isValidSessionId("ses_01J8X7Q2K9"))
        assertTrue(isValidSessionId("abc123"))
        assertTrue(isValidSessionId("a.b-c_d"))
    }

    @Test
    fun `53-F6 traversal and encoded separators fail`() {
        assertFalse(isValidSessionId(".."))
        assertFalse(isValidSessionId("../config"))
        assertFalse(isValidSessionId("a/b"))
        assertFalse(isValidSessionId("a%2Fb"))
        assertFalse(isValidSessionId("a?x=1"))
        assertFalse(isValidSessionId("a#b"))
        assertFalse(isValidSessionId("a b"))
        assertFalse(isValidSessionId(".abc"))
        assertFalse(isValidSessionId("-abc"))
        assertFalse(isValidSessionId(""))
    }
}
