package dev.bidzz.opencode.ui.generate

import dev.bidzz.opencode.core.api.dto.GenerateImageOutput
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D8 (m-990): switching modes stashes the result of the mode
 * being left and restores the result of the mode being entered — an
 * accidental tap no longer discards work. The switch is a pure function
 * ([GenerateUiState.withMode]), so it is tested directly; the failure
 * branches that must clear the stash are pinned by a scanner guard
 * below, since they live inside the view model's generate().
 */
class GenerateModeD8Test {

    private fun image(name: String) =
        GenerateImageOutput(type = "file", data = "eA==", mime = "image/png", name = name)

    @Test
    fun `m-990 toggling text to image and back restores the text`() {
        val text = GenerateUiState(mode = GenerateMode.TEXT, textResult = "hello")

        val image = text.withMode(GenerateMode.IMAGE)
        assertEquals(GenerateMode.IMAGE, image.mode)
        assertNull(image.textResult)
        assertEquals("hello", image.savedTextResult)

        val back = image.withMode(GenerateMode.TEXT)
        assertEquals(GenerateMode.TEXT, back.mode)
        assertEquals("hello", back.textResult)
        assertEquals("hello", back.savedTextResult)
    }

    @Test
    fun `m-990 toggling image to text and back restores the images`() {
        val images = listOf(image("a.png"), image("b.png"))
        val imageState = GenerateUiState(mode = GenerateMode.IMAGE, imageResults = images)

        val text = imageState.withMode(GenerateMode.TEXT)
        assertEquals(GenerateMode.TEXT, text.mode)
        assertTrue(text.imageResults.isEmpty())
        assertEquals(images, text.savedImageResults)

        val back = text.withMode(GenerateMode.IMAGE)
        assertEquals(GenerateMode.IMAGE, back.mode)
        assertEquals(images, back.imageResults)
        assertEquals(images, back.savedImageResults)
    }

    @Test
    fun `m-990 switching to the same mode changes nothing`() {
        val state = GenerateUiState(mode = GenerateMode.TEXT, textResult = "hello")
        assertEquals(state, state.withMode(GenerateMode.TEXT))
    }

    @Test
    fun `m-990 a switch clears the failure and the cost, not the stash`() {
        val state = GenerateUiState(
            mode = GenerateMode.TEXT,
            textResult = "hello",
            resultCost = 0.02,
            resultInputTokens = 10L,
            resultOutputTokens = 20L,
            failure = dev.bidzz.opencode.core.api.ApiResult.Failure(code = 0, tag = "Boom"),
            interrupted = true,
        )

        val switched = state.withMode(GenerateMode.IMAGE)
        assertNull(switched.failure)
        assertFalse(switched.interrupted)
        assertNull(switched.resultCost)
        assertNull(switched.resultInputTokens)
        assertNull(switched.resultOutputTokens)
        assertEquals("hello", switched.savedTextResult)
    }

    @Test
    fun `m-990 a failed generation clears the stash, so a toggle cannot resurrect it`() {
        // The failure copies live in generate(); pin them by source.
        val src = File(
            "src/main/kotlin/dev/bidzz/opencode/ui/generate/GenerateViewModel.kt",
        ).readText()
        assertTrue(src.contains("savedTextResult = null"))
        assertTrue(src.contains("savedImageResults = emptyList()"))
    }
}
