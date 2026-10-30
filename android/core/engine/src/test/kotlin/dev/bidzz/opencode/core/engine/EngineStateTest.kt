package dev.bidzz.opencode.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The state machine is the part of the supervisor that must never be wrong: a
 * wrong transition means the UI shows "running" while nothing is listening.
 */
class EngineStateTest {
    @Test
    fun `happy path is walkable`() {
        val path = listOf(
            EngineState.STOPPED,
            EngineState.INITIALIZING,
            EngineState.STARTING,
            EngineState.RUNNING,
            EngineState.STOPPING,
            EngineState.STOPPED,
        )
        path.zipWithNext().forEach { (from, to) ->
            assertTrue("$from -> $to should be legal", from.canTransitionTo(to))
        }
    }

    @Test
    fun `cannot jump from STOPPED to RUNNING`() {
        assertFalse(EngineState.STOPPED.canTransitionTo(EngineState.RUNNING))
    }

    @Test
    fun `cannot start twice`() {
        assertFalse(EngineState.RUNNING.canTransitionTo(EngineState.INITIALIZING))
        assertFalse(EngineState.RUNNING.canStart)
    }

    @Test
    fun `failure is reachable from every active state`() {
        listOf(EngineState.INITIALIZING, EngineState.STARTING, EngineState.RUNNING, EngineState.DEGRADED)
            .forEach { assertTrue("$it -> FAILED", it.canTransitionTo(EngineState.FAILED)) }
    }

    @Test
    fun `degraded can recover to running`() {
        assertTrue(EngineState.DEGRADED.canTransitionTo(EngineState.RUNNING))
    }

    @Test
    fun `stopping always terminates`() {
        assertTrue(EngineState.STOPPING.canTransitionTo(EngineState.STOPPED))
        assertFalse(EngineState.STOPPING.canTransitionTo(EngineState.RUNNING))
    }

    @Test
    fun `only stopped and failed can start`() {
        val startable = EngineState.entries.filter { it.canStart }
        assertTrue(startable.containsAll(listOf(EngineState.STOPPED, EngineState.FAILED)))
        assertFalse(startable.contains(EngineState.RUNNING))
    }

    @Test
    fun `isAlive covers the states where a process exists`() {
        assertTrue(EngineState.STARTING.isAlive)
        assertTrue(EngineState.RUNNING.isAlive)
        assertTrue(EngineState.DEGRADED.isAlive)
        assertFalse(EngineState.STOPPED.isAlive)
        assertFalse(EngineState.FAILED.isAlive)
        assertFalse(EngineState.STOPPING.isAlive)
    }
}
