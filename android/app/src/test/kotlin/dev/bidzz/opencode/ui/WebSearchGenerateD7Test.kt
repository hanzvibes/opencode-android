package dev.bidzz.opencode.ui

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.ui.generate.GenerateViewModel
import dev.bidzz.opencode.ui.websearch.WebSearchViewModel
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D7 (m-1204 regression): a failed WebSearch/Generate is a
 * persistent error with a Retry, not a snackbar that vanishes and leaves
 * the screen claiming it never searched. The engine-down path is driven
 * directly (no handle, no coroutine); the screen shape is pinned by a
 * scanner guard.
 */
class WebSearchGenerateD7Test {

    private fun read(path: String): String = File(path).readText()

    @Test
    fun `m-1204 a search with no engine reports a persistent failure, not silence`() {
        val vm = WebSearchViewModel(EngineApi { "/tmp" })
        vm.setQuery("opencode")
        vm.search()
        assertFalse(vm.state.value.searching)
        assertNull(vm.state.value.response)
        assertTrue(vm.state.value.failure != null)
    }

    @Test
    fun `m-1204 a generation with no engine reports a persistent failure, not silence`() {
        val vm = GenerateViewModel(EngineApi { "/tmp" })
        vm.setPrompt("a cat")
        vm.generate()
        assertFalse(vm.state.value.generating)
        assertNull(vm.state.value.textResult)
        assertTrue(vm.state.value.imageResults.isEmpty())
        assertTrue(vm.state.value.failure != null)
    }

    @Test
    fun `m-1204 the search screen never snackbars-and-clears a failure`() {
        val screen = read("src/main/kotlin/dev/bidzz/opencode/ui/websearch/WebSearchScreen.kt")
        assertFalse(screen.contains("showSnackbar"))
        assertFalse(screen.contains("onClearFailure"))
        assertFalse(screen.contains("clearFailure"))
        // The failure branch comes before the idle branch.
        val failureAt = screen.indexOf("state.failure != null -> EmptyState(")
        val idleAt = screen.indexOf("response == null -> EmptyState(")
        assertTrue(failureAt >= 0 && idleAt >= 0 && failureAt < idleAt)
    }

    @Test
    fun `m-1204 the generate screen never snackbars-and-clears a failure`() {
        val screen = read("src/main/kotlin/dev/bidzz/opencode/ui/generate/GenerateScreen.kt")
        assertFalse(screen.contains("showSnackbar"))
        assertFalse(screen.contains("onClearFailure"))
        assertFalse(screen.contains("clearFailure"))
        assertTrue(screen.contains("if (state.failure != null) {"))
    }
}
