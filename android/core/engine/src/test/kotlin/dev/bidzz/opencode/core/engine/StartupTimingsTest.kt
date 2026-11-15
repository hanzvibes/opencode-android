package dev.bidzz.opencode.core.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Audit-200 D5 (m-875): the cold-start timings object. The clock is a
 * scripted sequence, so the test does not depend on wall time.
 */
class StartupTimingsTest {

    @Before
    fun setUp() {
        StartupTimings.reset()
    }

    @After
    fun tearDown() {
        StartupTimings.clock = { android.os.SystemClock.elapsedRealtime() }
        StartupTimings.reset()
    }

    @Test
    fun `marks measure milliseconds since the first mark`() {
        val times = ArrayDeque(listOf(1_000L, 1_012L, 1_340L))
        StartupTimings.clock = { times.removeFirst() }
        StartupTimings.mark("appOnCreate")
        StartupTimings.mark("engineStartRequested")
        StartupTimings.mark("engineHealthy")
        assertEquals(0L, StartupTimings.elapsed("appOnCreate"))
        assertEquals(12L, StartupTimings.elapsed("engineStartRequested"))
        assertEquals(340L, StartupTimings.elapsed("engineHealthy"))
        assertNull(StartupTimings.elapsed("neverMarked"))
    }

    @Test
    fun `snapshot keeps mark order and report joins them`() {
        val times = ArrayDeque(listOf(500L, 520L))
        StartupTimings.clock = { times.removeFirst() }
        StartupTimings.mark("a")
        StartupTimings.mark("b")
        assertEquals(mapOf("a" to 0L, "b" to 20L), StartupTimings.snapshot())
        assertEquals("a=0ms, b=20ms", StartupTimings.report())
    }

    @Test
    fun `a retried stage keeps its latest value and reset clears everything`() {
        val times = ArrayDeque(listOf(100L, 200L, 300L))
        StartupTimings.clock = { times.removeFirst() }
        StartupTimings.mark("engineStartAttempt")
        StartupTimings.mark("engineStartAttempt")
        // Second mark at 200ms, base 100ms — the stored value is 100ms.
        assertEquals(100L, StartupTimings.elapsed("engineStartAttempt"))
        StartupTimings.reset()
        assertTrue(StartupTimings.snapshot().isEmpty())
        assertEquals("no marks yet", StartupTimings.report())
        assertNull(StartupTimings.elapsed("engineStartAttempt"))
    }
}
