package dev.bidzz.opencode.ui.instructions

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionsValidationTest {

    @Test
    fun `valid keys match the engine pattern`() {
        assertTrue(isValidInstructionKey("notes"))
        assertTrue(isValidInstructionKey("my-notes.v2_final"))
        assertTrue(isValidInstructionKey("a1"))
        assertTrue(isValidInstructionKey("9lives"))
    }

    @Test
    fun `invalid keys are rejected`() {
        assertFalse(isValidInstructionKey(""))
        assertFalse(isValidInstructionKey("Notes"))
        assertFalse(isValidInstructionKey("my notes"))
        assertFalse(isValidInstructionKey("-notes"))
        assertFalse(isValidInstructionKey(".notes"))
        assertFalse(isValidInstructionKey("notes/key"))
        assertFalse(isValidInstructionKey("notes!"))
    }

    @Test
    fun `string values read back as plain text`() {
        assertEquals("Prefer short answers", instructionValueText(JsonPrimitive("Prefer short answers")))
        assertEquals("", instructionValueText(null))
    }

    @Test
    fun `non-string values read back as compact JSON`() {
        val obj = buildJsonObject { put("tone", "formal") }
        assertEquals("""{"tone":"formal"}""", instructionValueText(obj))
        assertEquals("3", instructionValueText(JsonPrimitive(3)))
    }
}
