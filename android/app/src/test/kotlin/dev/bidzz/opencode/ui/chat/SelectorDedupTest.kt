package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.AgentInfo
import dev.bidzz.opencode.core.api.dto.ModelInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The selector menus key their LazyColumn rows by engine identifiers
 * (model: provider/id, agent: id), so a list the engine advertises twice
 * crashes the menu on open with a duplicate-key IllegalArgumentException.
 * The dedup helpers keep the first copy of each key.
 */
class SelectorDedupTest {

    @Test
    fun `duplicate models collapse to the first copy`() {
        val models = listOf(
            ModelInfo(id = "a", providerID = "anthropic", modelID = "claude-sonnet-4", name = "First"),
            ModelInfo(id = "b", providerID = "anthropic", modelID = "claude-sonnet-4", name = "Second"),
        )
        val deduped = dedupeModels(models)
        assertEquals(listOf("First"), deduped.map { it.name })
    }

    @Test
    fun `same model id under different providers is kept`() {
        val models = listOf(
            ModelInfo(id = "m", providerID = "anthropic", modelID = "model-x"),
            ModelInfo(id = "m", providerID = "openai", modelID = "model-x"),
        )
        assertEquals(2, dedupeModels(models).size)
    }

    @Test
    fun `a model without modelID dedupes against its bare id`() {
        val models = listOf(
            ModelInfo(id = "model-x", providerID = "anthropic", modelID = null),
            ModelInfo(id = "other", providerID = "anthropic", modelID = "model-x"),
        )
        assertEquals(1, dedupeModels(models).size)
    }

    @Test
    fun `distinct models are untouched`() {
        val models = listOf(
            ModelInfo(id = "1", providerID = "anthropic", modelID = "claude-sonnet-4"),
            ModelInfo(id = "2", providerID = "openai", modelID = "gpt-4o"),
        )
        assertEquals(models, dedupeModels(models))
    }

    @Test
    fun `duplicate agents collapse to the first copy`() {
        val agents = listOf(
            AgentInfo(id = "build", name = "Build"),
            AgentInfo(id = "build", name = "Build again"),
            AgentInfo(id = "plan", name = "Plan"),
        )
        val deduped = dedupeAgents(agents)
        assertEquals(listOf("build", "plan"), deduped.map { it.id })
        assertEquals("Build", deduped.first().name)
    }

    @Test
    fun `empty lists stay empty`() {
        assertEquals(emptyList<ModelInfo>(), dedupeModels(emptyList()))
        assertEquals(emptyList<AgentInfo>(), dedupeAgents(emptyList()))
    }
}
