package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.ModelRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rejected model/agent picks revert to the engine default (audit-100
 * Fase 6b, 43-F2): the chip must show what the session actually runs on,
 * not the pick the engine refused.
 */
class SelectionRevertTest {

    private val modelA = ModelRef(id = "a", providerID = "opencode")
    private val modelB = ModelRef(id = "b", providerID = "opencode")

    @Test
    fun `a rejected model reverts to default, a kept agent stays`() {
        val reverted = resolveSelectionAfterSetFailure(
            activeAgent = "build",
            activeModel = modelB,
            agentSetFailed = false,
            modelSetFailed = true,
        )
        assertEquals("build", reverted.agent)
        assertNull(reverted.model)
    }

    @Test
    fun `a rejected agent reverts to default, a kept model stays`() {
        val reverted = resolveSelectionAfterSetFailure(
            activeAgent = "plan",
            activeModel = modelA,
            agentSetFailed = true,
            modelSetFailed = false,
        )
        assertNull(reverted.agent)
        assertEquals(modelA, reverted.model)
    }

    @Test
    fun `both rejected revert both, neither rejected keeps both`() {
        val both = resolveSelectionAfterSetFailure(
            activeAgent = "plan",
            activeModel = modelA,
            agentSetFailed = true,
            modelSetFailed = true,
        )
        assertNull(both.agent)
        assertNull(both.model)

        val neither = resolveSelectionAfterSetFailure(
            activeAgent = "plan",
            activeModel = modelA,
            agentSetFailed = false,
            modelSetFailed = false,
        )
        assertEquals("plan", neither.agent)
        assertEquals(modelA, neither.model)
    }
}
