package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.AgentInfo
import dev.bidzz.opencode.core.api.dto.ModelInfo
import dev.bidzz.opencode.core.api.dto.ModelRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit-200 Fase C6 (M-110/M-111): the header/composer chips must not
 * claim a model or agent is active when none is. The old fallback showed
 * the first catalog entry with full color — and the agent menu even
 * checkmarked it — so the user could send a prompt believing they were
 * on model X / in mode Y while the engine used its own default.
 */
class ChatControlsC6Test {

    private val models = listOf(
        ModelInfo(id = "a", providerID = "anthropic", modelID = "claude-sonnet-4", name = "Sonnet"),
        ModelInfo(id = "b", providerID = "openai", modelID = "gpt-5", name = "GPT-5"),
    )
    private val agents = listOf(
        AgentInfo(id = "build", name = "Build"),
        AgentInfo(id = "plan", name = "Plan"),
    )

    @Test
    fun `M-110 no active model shows no model, not the first catalog entry`() {
        assertNull(resolveCurrentModel(models, null))
    }

    @Test
    fun `M-110 the active model resolves from the catalog`() {
        val resolved = resolveCurrentModel(models, ModelRef(id = "gpt-5", providerID = "openai"))
        assertEquals("GPT-5", resolved?.name)
    }

    @Test
    fun `M-110 an active model missing from the catalog still shows as itself`() {
        val resolved = resolveCurrentModel(models, ModelRef(id = "gone", providerID = "openai"))
        assertEquals("gone", resolved?.id)
        assertEquals("openai", resolved?.providerID)
    }

    @Test
    fun `M-110 a model matched by modelID, not bare id, resolves`() {
        val resolved = resolveCurrentModel(models, ModelRef(id = "claude-sonnet-4", providerID = "anthropic"))
        assertEquals("Sonnet", resolved?.name)
    }

    @Test
    fun `M-111 no active agent shows no agent, not the first catalog entry`() {
        assertNull(resolveCurrentAgent(agents, null))
    }

    @Test
    fun `M-111 the active agent resolves from the catalog`() {
        val resolved = resolveCurrentAgent(agents, "plan")
        assertEquals("Plan", resolved?.name)
    }

    @Test
    fun `M-111 an active agent matched by name, case-insensitively, resolves`() {
        val resolved = resolveCurrentAgent(agents, "BUILD")
        assertEquals("build", resolved?.id)
    }

    @Test
    fun `M-111 an active agent missing from the catalog still shows as itself`() {
        val resolved = resolveCurrentAgent(agents, "custom-mode")
        assertEquals("custom-mode", resolved?.id)
        assertEquals("custom-mode", resolved?.name)
    }
}
