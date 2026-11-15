package dev.bidzz.opencode.ui.instructions

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.dto.InstructionEntry
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D1 (m-468): editing a non-string instruction value and saving
 * it used to silently retype it to a string. Those entries are read-only
 * now — the editor flags them and Save is a no-op for them.
 */
class InstructionsNonStringTest {

    private fun viewModel() = InstructionsViewModel(EngineApi { "/tmp" })

    @Test
    fun `editing an object entry flags it non-string and save does nothing`() {
        val vm = viewModel()
        vm.load("s1") // no engine running; only sets the session id here
        vm.edit(InstructionEntry("prefs", buildJsonObject { put("tone", "formal") }))

        assertTrue(vm.state.value.editingNonString)
        assertEquals("""{"tone":"formal"}""", vm.state.value.value)

        vm.save()

        // No engine is running, so a normal save would fail with an engine
        // error — a non-string save must not even get there.
        assertFalse(vm.state.value.saving)
        assertNull(vm.state.value.failure)
        assertTrue(vm.state.value.editingNonString)
    }

    @Test
    fun `editing a string entry stays editable`() {
        val vm = viewModel()
        vm.edit(InstructionEntry("notes", JsonPrimitive("Prefer short answers")))

        assertFalse(vm.state.value.editingNonString)
        assertEquals("Prefer short answers", vm.state.value.value)
    }

    @Test
    fun `clearing the editor clears the non-string flag`() {
        val vm = viewModel()
        vm.edit(InstructionEntry("count", JsonPrimitive(3)))
        assertTrue(vm.state.value.editingNonString)

        vm.clearEditor()

        assertFalse(vm.state.value.editingNonString)
    }
}
