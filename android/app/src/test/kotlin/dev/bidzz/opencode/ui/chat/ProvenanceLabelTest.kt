package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.AgentInfo
import dev.bidzz.opencode.core.api.dto.ModelInfo
import dev.bidzz.opencode.core.api.dto.ModelRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The per-message "who answered this" label.
 *
 * The interesting cases are all about what happens when the label's inputs do not
 * line up with the current catalog: models get removed, providers get renamed, and a
 * transcript has to keep saying what produced each reply rather than going blank.
 */
class ProvenanceLabelTest {

    // Mirrors `GET /api/model`: the record id is namespaced, the bare model id is in
    // `modelID`, and the session's message carries the bare one.
    private val pickle = ModelInfo(
        id = "opencode/big-pickle",
        modelID = "big-pickle",
        providerID = "opencode",
        name = "Big Pickle",
    )
    private val agnes = ModelInfo(
        id = "ninerouter/agnes/agnes-2.5-flash",
        modelID = "agnes/agnes-2.5-flash",
        providerID = "ninerouter",
        name = "Agnes 2.5 Flash",
    )
    private val agents = listOf(AgentInfo(id = "build", name = "Build"), AgentInfo(id = "plan", name = "Plan"))
    private val models = listOf(pickle, agnes)

    private fun label(agent: String?, model: ModelRef?) =
        provenanceLabel(agent, model, agents, models)

    @Test
    fun `mode and model both show up`() {
        assertEquals(
            "Build · Big Pickle",
            label("build", ModelRef(id = "big-pickle", providerID = "opencode")),
        )
    }

    @Test
    fun `matching uses the model id the message carries, not the catalog record id`() {
        // The control for the test above: `Big Pickle` only appears if the lookup went
        // through ModelInfo.ref(). Matching on ModelInfo.id would find nothing here and
        // would print the raw id instead.
        assertEquals("Build · Agnes 2.5 Flash", label("build", ModelRef(id = "agnes/agnes-2.5-flash", providerID = "ninerouter")))
        assertEquals(
            "Build · big-pickle",
            provenanceLabel("build", ModelRef(id = "big-pickle", providerID = "opencode"), agents, emptyList()),
        )
    }

    @Test
    fun `a model that is no longer in the catalog is still named`() {
        // Providers get removed. The transcript should say which model answered, not
        // drop the label because the catalog moved on.
        assertEquals("Build · gpt-3.5-turbo", label("build", ModelRef(id = "gpt-3.5-turbo", providerID = "openai")))
    }

    @Test
    fun `an unknown mode falls back to its id`() {
        assertEquals("reviewer · Big Pickle", label("reviewer", ModelRef(id = "big-pickle", providerID = "opencode")))
    }

    @Test
    fun `either half alone is still a label`() {
        assertEquals("Build", label("build", null))
        assertEquals("Big Pickle", label(null, ModelRef(id = "big-pickle", providerID = "opencode")))
    }

    @Test
    fun `nothing known means no label`() {
        // A streaming message has neither yet; an empty chip would read as a bug.
        assertNull(label(null, null))
        assertNull(label("   ", ModelRef(id = "   ", providerID = "opencode")))
    }

    @Test
    fun `a catalog entry without a name is not turned into a blank`() {
        val nameless = ModelInfo(id = "x/y", modelID = "y", providerID = "x", name = "  ")
        assertEquals(
            "Build · y",
            provenanceLabel("build", ModelRef(id = "y", providerID = "x"), agents, listOf(nameless)),
        )
    }

    @Test
    fun `two messages in one session keep their own models`() {
        // The reason this feature exists: switching the model mid-conversation must not
        // relabel the replies that were already produced.
        val first = provenanceLabel("build", ModelRef(id = "big-pickle", providerID = "opencode"), agents, models)
        val second = provenanceLabel("plan", ModelRef(id = "agnes/agnes-2.5-flash", providerID = "ninerouter"), agents, models)
        assertEquals("Build · Big Pickle", first)
        assertEquals("Plan · Agnes 2.5 Flash", second)
    }

    @Test
    fun `a mode listed without a name is not turned into a blank`() {
        // `AgentInfo.name` defaults to "" in the DTO, so a catalog entry can arrive with
        // no label at all; the id is the only thing left that still means something.
        assertEquals(
            "build · Big Pickle",
            provenanceLabel(
                "build",
                ModelRef(id = "big-pickle", providerID = "opencode"),
                listOf(AgentInfo(id = "build", name = "  ")),
                models,
            ),
        )
    }

    @Test
    fun `a mode whose name matches the model name is printed once`() {
        val odd = listOf(AgentInfo(id = "big-pickle", name = "Big Pickle"))
        assertEquals(
            "Big Pickle",
            provenanceLabel("big-pickle", ModelRef(id = "big-pickle", providerID = "opencode"), odd, models),
        )
    }
}
