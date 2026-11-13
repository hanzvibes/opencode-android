package dev.bidzz.opencode.ui

import dev.bidzz.opencode.core.api.FailureReason
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared engine-down failure (audit-100 64-F12): code 0 is the value
 * the API layer already uses for "no network at all", so the reason is
 * UNREACHABLE and the screen words it as "Cannot reach the engine".
 */
class EngineDownFailureTest {

    @Test
    fun `engine-down failure uses code 0`() {
        assertEquals(0, engineDownFailure().code)
    }

    @Test
    fun `engine-down failure reasons as unreachable`() {
        assertEquals(FailureReason.UNREACHABLE, engineDownFailure().reason)
    }
}
