package dev.bidzz.opencode.service

import dev.bidzz.opencode.core.engine.EngineState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The resurrection rule for a crashed engine.
 *
 * Wrong in either direction is bad: too strict and the app sits dead until the user
 * force-quits it; too keen and a stop the user asked for gets undone, or a binary that
 * cannot start turns into a restart loop.
 */
class ShouldResurrectTest {

    private val limit = 3

    private fun decide(
        state: EngineState,
        stopping: Boolean = false,
        inFlight: Boolean = false,
        attempts: Int = 0,
    ) = shouldResurrect(state, stopping, inFlight, attempts, limit)

    @Test
    fun `a crash with no attempts yet is resurrected`() {
        assertTrue(decide(EngineState.FAILED))
    }

    @Test
    fun `every other state leaves the supervisor alone`() {
        // DEGRADED in particular: its process is alive and still holds working
        // credentials, so restarting it would discard a server that may recover on the
        // next health check.
        for (state in EngineState.entries) {
            if (state == EngineState.FAILED) continue
            assertFalse(state.name, decide(state))
        }
    }

    @Test
    fun `a stop in progress is never resurrected`() {
        assertFalse(decide(EngineState.FAILED, stopping = true))
    }

    @Test
    fun `an attempt already in flight is not stacked`() {
        assertFalse(decide(EngineState.FAILED, inFlight = true))
    }

    @Test
    fun `the limit stops the attempts`() {
        assertTrue(decide(EngineState.FAILED, attempts = limit - 1))
        assertFalse(decide(EngineState.FAILED, attempts = limit))
        assertFalse(decide(EngineState.FAILED, attempts = limit + 5))
    }
}
