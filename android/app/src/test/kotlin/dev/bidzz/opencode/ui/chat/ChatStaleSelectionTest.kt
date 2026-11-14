package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.AgentInfo
import dev.bidzz.opencode.core.api.dto.ModelInfo
import dev.bidzz.opencode.core.api.dto.ModelRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit-200 B4 (M-459): a model/agent the chip shows can vanish from the
 * fresh lists when its provider is deleted — the chip must clear to the
 * engine default and the screen must name what went stale, instead of
 * silently running the next prompt on the default.
 */
class ChatStaleSelectionTest {

    private fun model(id: String) = ModelInfo(id = id, providerID = "p")
    private fun agent(id: String) = AgentInfo(id = id)

    @Test
    fun `nothing is stale when the picks are still listed`() {
        assertNull(
            findStaleSelection(
                activeModel = ModelRef(id = "gpt-4", providerID = "p"),
                activeAgent = "build",
                models = listOf(model("gpt-4"), model("claude")),
                agents = listOf(agent("build"), agent("plan")),
            ),
        )
    }

    @Test
    fun `nothing is stale when nothing is picked`() {
        assertNull(
            findStaleSelection(
                activeModel = null,
                activeAgent = null,
                models = listOf(model("gpt-4")),
                agents = listOf(agent("build")),
            ),
        )
    }

    @Test
    fun `an empty list is not loaded yet, not a vanished model`() {
        // The lists start empty on first attach: clearing the chip then
        // would drop a valid pick before the engine even answered.
        assertNull(
            findStaleSelection(
                activeModel = ModelRef(id = "gpt-4", providerID = "p"),
                activeAgent = "build",
                models = emptyList(),
                agents = emptyList(),
            ),
        )
    }

    @Test
    fun `a deleted model is stale by id`() {
        val stale = findStaleSelection(
            activeModel = ModelRef(id = "gpt-4", providerID = "p"),
            activeAgent = null,
            models = listOf(model("claude")),
            agents = listOf(agent("build")),
        )

        assertEquals("gpt-4", stale?.modelId)
        assertNull(stale?.agentId)
    }

    @Test
    fun `a deleted agent is stale by id`() {
        val stale = findStaleSelection(
            activeModel = null,
            activeAgent = "build",
            models = listOf(model("gpt-4")),
            agents = listOf(agent("plan")),
        )

        assertNull(stale?.modelId)
        assertEquals("build", stale?.agentId)
    }

    @Test
    fun `both can go stale at once`() {
        val stale = findStaleSelection(
            activeModel = ModelRef(id = "gpt-4", providerID = "p"),
            activeAgent = "build",
            models = listOf(model("claude")),
            agents = listOf(agent("plan")),
        )

        assertEquals("gpt-4", stale?.modelId)
        assertEquals("build", stale?.agentId)
    }
}
