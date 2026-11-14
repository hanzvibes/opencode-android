package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.AgentInfo
import dev.bidzz.opencode.core.api.dto.ModelInfo
import dev.bidzz.opencode.core.api.dto.ModelRef
import dev.bidzz.opencode.core.api.dto.PermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C7 (M-114/M-115/M-121): the provider-aware model
 * match, the Antigravity naming, and the permission-derived agent
 * badge. The presentation wiring itself (which badgeRes the row
 * renders) is inspection + compile — the decisions live here.
 */
class ChatControlsC7Test {

    private val models = listOf(
        // Two providers advertising the same model id (M-114's case).
        ModelInfo(id = "rec-anthropic", providerID = "anthropic", modelID = "claude-sonnet-4", name = "Sonnet"),
        ModelInfo(id = "rec-openai", providerID = "openai", modelID = "claude-sonnet-4", name = "Sonnet (OpenAI)"),
        ModelInfo(id = "gpt-5", providerID = "openai", modelID = "gpt-5", name = "GPT-5"),
    )

    // M-114: modelRefMatches.

    @Test
    fun `M-114 a strict ref match still matches`() {
        val model = ModelInfo(id = "gpt-5", providerID = "openai", modelID = "gpt-5")
        assertTrue(modelRefMatches(model, ModelRef(id = "gpt-5", providerID = "openai")))
    }

    @Test
    fun `M-114 the engine record id matches the catalog entry`() {
        // The engine's session model id is its record id while the
        // catalog's modelID is the bare id.
        val model = ModelInfo(id = "rec-anthropic", providerID = "anthropic", modelID = "claude-sonnet-4")
        assertTrue(modelRefMatches(model, ModelRef(id = "rec-anthropic", providerID = "anthropic")))
    }

    @Test
    fun `M-114 the bare model id matches within the same provider`() {
        val model = ModelInfo(id = "rec-anthropic", providerID = "anthropic", modelID = "claude-sonnet-4")
        assertTrue(modelRefMatches(model, ModelRef(id = "claude-sonnet-4", providerID = "anthropic")))
    }

    @Test
    fun `M-114 the same model id under another provider does not match`() {
        val model = ModelInfo(id = "rec-openai", providerID = "openai", modelID = "claude-sonnet-4")
        assertFalse(modelRefMatches(model, ModelRef(id = "claude-sonnet-4", providerID = "anthropic")))
        assertFalse(modelRefMatches(model, ModelRef(id = "rec-openai", providerID = "anthropic")))
    }

    @Test
    fun `M-114 no active model matches nothing`() {
        val model = ModelInfo(id = "gpt-5", providerID = "openai", modelID = "gpt-5")
        assertFalse(modelRefMatches(model, null))
    }

    // M-114: resolveCurrentModel with a shared model id.

    @Test
    fun `M-114 two providers share a model id, the active provider's row wins`() {
        val resolved = resolveCurrentModel(models, ModelRef(id = "claude-sonnet-4", providerID = "openai"))
        assertEquals("rec-openai", resolved?.id)
        assertEquals("Sonnet (OpenAI)", resolved?.name)
    }

    @Test
    fun `M-114 the first row does not win when its provider differs`() {
        val resolved = resolveCurrentModel(models, ModelRef(id = "rec-anthropic", providerID = "anthropic"))
        assertEquals("rec-anthropic", resolved?.id)
    }

    // M-115: cleanModelName names Antigravity.

    @Test
    fun `M-115 Antigravity Gemini 3-8 Flash names its provider`() {
        assertEquals(
            "Gemini 3.8 Flash (Antigravity)",
            cleanModelName("", "antigravity-gemini-3.8-flash"),
        )
    }

    @Test
    fun `M-115 Antigravity Gemini 3-8 Flash High keeps its tier and names its provider`() {
        assertEquals(
            "Gemini 3.8 Flash (High, Antigravity)",
            cleanModelName("", "antigravity-gemini-3.8-flash-high"),
        )
    }

    @Test
    fun `M-115 Antigravity Gemini 3-1 Pro names its provider`() {
        assertEquals(
            "Gemini 3.1 Pro (Antigravity)",
            cleanModelName("", "antigravity-gemini-3.1-pro"),
        )
    }

    @Test
    fun `M-115 Antigravity Claude keeps its existing suffix`() {
        assertEquals(
            "Claude (Antigravity)",
            cleanModelName("", "antigravity-claude-opus-4-6"),
        )
    }

    // M-121: editAccessOf.

    @Test
    fun `M-121 no permissions field means Unknown, not a name guess`() {
        assertEquals(EditAccess.Unknown, editAccessOf(null))
    }

    @Test
    fun `M-121 no rules at all inherits the documented base allow`() {
        assertEquals(EditAccess.FullAccess, editAccessOf(emptyList()))
    }

    @Test
    fun `M-121 an allow catch-all is Full Access`() {
        val rules = listOf(PermissionRule(action = "edit", resource = "*", effect = "allow"))
        assertEquals(EditAccess.FullAccess, editAccessOf(rules))
    }

    @Test
    fun `M-121 a deny catch-all is Read Only`() {
        // The plan agent's shape: deny edits everywhere, allow them
        // only under the plan directory.
        val rules = listOf(
            PermissionRule(action = "edit", resource = "*", effect = "deny"),
            PermissionRule(action = "edit", resource = "~/.opencode/plan/*", effect = "allow"),
        )
        assertEquals(EditAccess.ReadOnly, editAccessOf(rules))
    }

    @Test
    fun `M-121 an ask catch-all Asks First`() {
        val rules = listOf(PermissionRule(action = "edit", resource = "*", effect = "ask"))
        assertEquals(EditAccess.AsksFirst, editAccessOf(rules))
    }

    @Test
    fun `M-121 a deny-all action denies edits too`() {
        // The title/summary agents' shape: deny everything.
        val rules = listOf(PermissionRule(action = "*", resource = "*", effect = "deny"))
        assertEquals(EditAccess.ReadOnly, editAccessOf(rules))
    }

    @Test
    fun `M-121 the last catch-all rule wins`() {
        val rules = listOf(
            PermissionRule(action = "edit", resource = "*", effect = "deny"),
            PermissionRule(action = "edit", resource = "*", effect = "allow"),
        )
        assertEquals(EditAccess.FullAccess, editAccessOf(rules))
    }

    @Test
    fun `M-121 a narrow allow does not restrict the rest`() {
        val rules = listOf(
            PermissionRule(action = "edit", resource = "plans/*.md", effect = "allow"),
        )
        assertEquals(EditAccess.FullAccess, editAccessOf(rules))
    }

    @Test
    fun `M-121 a rule about another action says nothing about edits`() {
        // The build agent's shape: its own rules allow questions; edits
        // are untouched, so the base allow applies.
        val rules = listOf(
            PermissionRule(action = "question", resource = "*", effect = "allow"),
        )
        assertEquals(EditAccess.FullAccess, editAccessOf(rules))
    }
}
