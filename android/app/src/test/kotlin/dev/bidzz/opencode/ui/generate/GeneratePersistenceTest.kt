package dev.bidzz.opencode.ui.generate

import dev.bidzz.opencode.core.api.dto.GenerateImageOutput
import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Audit-200 Fase A9-3 (DL-011): the last generation — prompt, settings
 * and result — lives on disk, so a prompt (and a paid image result) is
 * not lost to process death.
 *
 * The save/load pair is tested directly; the view model calls it at the
 * start of a generation and on its outcome, and restores it in init —
 * those wirings are covered by compile + fast-verify, since driving a
 * generation needs a live engine.
 */
class GeneratePersistenceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun imageState(): GenerateUiState {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x01, 0x02)
        return GenerateUiState(
            prompt = "a red cat",
            mode = GenerateMode.IMAGE,
            selectedModelId = "img-model",
            aspect = "16:9",
            resolution = "1024",
            imageResults = listOf(
                GenerateImageOutput(
                    type = "image",
                    data = Base64.getEncoder().encodeToString(bytes),
                    mime = "image/png",
                    name = "cat.png",
                ),
            ),
            resultCost = 0.04,
            resultInputTokens = 12,
            resultOutputTokens = 340,
        )
    }

    @Test
    fun `an image result round-trips through disk`() {
        val dir = lastGenerationDir(tmp.root)
        val state = imageState()

        saveLastGeneration(state, dir)
        val restored = loadLastGeneration(dir)

        assertEquals("a red cat", restored?.prompt)
        assertEquals(GenerateMode.IMAGE, restored?.mode)
        assertEquals("img-model", restored?.selectedModelId)
        assertEquals("16:9", restored?.aspect)
        assertEquals("1024", restored?.resolution)
        assertEquals(1, restored?.imageResults?.size)
        assertEquals("image/png", restored?.imageResults?.firstOrNull()?.mime)
        assertEquals("cat.png", restored?.imageResults?.firstOrNull()?.name)
        assertEquals(
            state.imageResults.first().data,
            restored?.imageResults?.firstOrNull()?.data,
        )
        assertEquals(0.04, restored?.resultCost)
        assertEquals(12L, restored?.resultInputTokens)
        assertEquals(340L, restored?.resultOutputTokens)
        assertFalse(restored!!.interrupted)
    }

    @Test
    fun `a text result round-trips through disk`() {
        val dir = lastGenerationDir(tmp.root)
        val state = GenerateUiState(prompt = "write a haiku", mode = GenerateMode.TEXT, textResult = "old pond\n")

        saveLastGeneration(state, dir)
        val restored = loadLastGeneration(dir)

        assertEquals("write a haiku", restored?.prompt)
        assertEquals(GenerateMode.TEXT, restored?.mode)
        assertEquals("old pond\n", restored?.textResult)
        assertTrue(restored?.imageResults.isNullOrEmpty())
        assertFalse(restored!!.interrupted)
    }

    @Test
    fun `a record that was still generating comes back interrupted, not spinning`() {
        val dir = lastGenerationDir(tmp.root)

        saveLastGeneration(imageState().copy(generating = true), dir)
        val restored = loadLastGeneration(dir)

        assertTrue(restored!!.interrupted)
        assertFalse(restored.generating)
        assertEquals("a red cat", restored.prompt)
        // The previous result is still there — only the run is gone.
        assertEquals(1, restored.imageResults.size)
    }

    @Test
    fun `a missing record loads as nothing`() {
        assertNull(loadLastGeneration(lastGenerationDir(tmp.root)))
    }

    @Test
    fun `a corrupt record loads as nothing`() {
        val dir = lastGenerationDir(tmp.root).apply { mkdirs() }
        File(dir, "last.json").writeText("{not json")

        assertNull(loadLastGeneration(dir))
    }

    @Test
    fun `an image whose file is gone is skipped, the rest survive`() {
        val dir = lastGenerationDir(tmp.root)
        saveLastGeneration(imageState(), dir)
        File(dir, "image-0.png").delete()

        val restored = loadLastGeneration(dir)

        assertEquals("a red cat", restored?.prompt)
        assertTrue(restored?.imageResults.isNullOrEmpty())
    }

    @Test
    fun `a new save replaces the previous result`() {
        val dir = lastGenerationDir(tmp.root)
        saveLastGeneration(imageState(), dir)
        saveLastGeneration(GenerateUiState(prompt = "second", mode = GenerateMode.TEXT, textResult = "two"), dir)

        val restored = loadLastGeneration(dir)

        assertEquals("second", restored?.prompt)
        assertEquals("two", restored?.textResult)
        assertTrue(restored?.imageResults.isNullOrEmpty())
    }
}
