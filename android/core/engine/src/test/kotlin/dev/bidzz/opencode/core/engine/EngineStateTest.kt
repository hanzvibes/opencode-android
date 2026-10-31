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

    @Test
    fun `stop is meaningful from every state that is not stopped`() {
        EngineState.entries.filter { it != EngineState.STOPPED }.forEach {
            assertTrue("$it should accept a stop request", it.canStop)
        }
        assertFalse(EngineState.STOPPED.canStop)
    }

    @Test
    fun `stop from FAILED reaches STOPPED`() {
        // Regression: `restart()` used to throw here, because stop() always asked
        // for STOPPING and FAILED cannot go there. That made the recovery path
        // after a crash unusable, and made the notification's Stop action crash.
        assertTrue(EngineState.FAILED.canTransitionTo(EngineState.STOPPED))
    }

    @Test
    fun `stop can abort an in-flight start`() {
        assertTrue(EngineState.INITIALIZING.canTransitionTo(EngineState.STOPPING))
        assertTrue(EngineState.STARTING.canTransitionTo(EngineState.STOPPING))
    }

    @Test
    fun `restart walks FAILED through INITIALIZING`() {
        // stop() from FAILED lands on STOPPED, and start() then needs STOPPED.
        assertTrue(EngineState.FAILED.canTransitionTo(EngineState.STOPPED))
        assertTrue(EngineState.STOPPED.canTransitionTo(EngineState.INITIALIZING))
        // start() straight from FAILED is also legal, without the stop() leg.
        assertTrue(EngineState.FAILED.canTransitionTo(EngineState.INITIALIZING))
    }

    @Test
    fun `stopped is the only state that refuses a stop request`() {
        assertFalse(EngineState.STOPPED.canStop)
        assertTrue(EngineState.FAILED.canStop)
        assertTrue(EngineState.INITIALIZING.canStop)
    }
}
