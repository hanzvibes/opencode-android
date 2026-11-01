package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.ModelInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The label the model picker shows.
 *
 * A custom provider whose config entry has no `name` is exactly what makes the chip
 * fall through to a raw id, so both the precedence and the blank-name case belong
 * here rather than in the composable.
 */
class ModelLabelTest {

    private fun model(name: String?, modelID: String?, id: String) =
        ModelInfo(id = id, modelID = modelID, providerID = "probe", name = name)

    @Test
    fun `a human name wins`() {
        assertEquals("Agnes 2.5 Flash", model("Agnes 2.5 Flash", "agnes/agnes-2.5-flash", "rec1").label())
    }

    @Test
    fun `a name that is only blanks does not win`() {
        // The engine can answer `""` for a model with no display name, and an empty
        // chip reads as a bug in the picker rather than as a missing name.
        assertEquals("agnes/agnes-2.5-flash", model("   ", "agnes/agnes-2.5-flash", "rec1").label())
        assertEquals("agnes/agnes-2.5-flash", model("", "agnes/agnes-2.5-flash", "rec1").label())
    }

    @Test
    fun `falls back to the model id and then the record id`() {
        assertEquals("rec1", model(null, null, "rec1").label())
        assertEquals("m/one", model(null, "m/one", "rec1").label())
    }
}
