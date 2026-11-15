package dev.bidzz.opencode.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D3 (a11y): TalkBack roles/names, 48dp touch targets, labelled
 * text fields, a labelled sheet Close button. The repo has no Compose UI
 * test harness (no compose-ui-test / createComposeRule), so — like the D2
 * TextButton guard — these tests scan the source, so a future bare
 * clickable, unlabelled field or shrunken target fails the build instead
 * of waiting for the next audit. The exact announcement TalkBack makes
 * still needs a real tablet; the guards prove the semantics exist.
 */
class A11yD3Test {

    private fun read(path: String): String = File(path).readText()

    /** Body of the function whose signature starts at [marker], brace-matched. */
    private fun funBody(src: String, marker: String): String {
        val start = src.indexOf(marker)
        require(start >= 0) { "missing $marker" }
        var i = src.indexOf('{', start)
        require(i >= 0) { "no body after $marker" }
        var depth = 0
        val bodyStart = i
        while (i < src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(bodyStart, i + 1)
                }
            }
            i++
        }
        error("unbalanced braces after $marker")
    }

    /** Every `BasicTextField(` call in [src], paren-matched (D2 pattern). */
    private fun fieldCalls(src: String): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var from = 0
        while (true) {
            val start = src.indexOf("BasicTextField(", from)
            if (start < 0) break
            var depth = 0
            var i = start + "BasicTextField".length
            while (i < src.length) {
                when (src[i]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) break
                    }
                }
                i++
            }
            val line = src.substring(0, start).count { it == '\n' } + 1
            out += line to src.substring(start, i)
            from = i + 1
        }
        return out
    }

    // m-340 / m-556 / m-662 / m-612 — tappable rows and dropdown triggers
    // announce their role and purpose.
    @Test
    fun `tappable rows and dropdown triggers announce their role and purpose`() {
        val artifacts = read("src/main/kotlin/dev/bidzz/opencode/ui/artifacts/ArtifactsScreen.kt")
        val artifactRow = funBody(artifacts, "private fun ArtifactRow(")
        assertTrue(artifactRow.contains("role = Role.Button"))
        assertTrue(artifactRow.contains("R.string.artifact_open"))
        assertTrue(artifactRow.contains("R.string.artifact_delete_item"))

        val generate = read("src/main/kotlin/dev/bidzz/opencode/ui/generate/GenerateScreen.kt")
        val modelPicker = funBody(generate, "private fun ModelPicker(")
        assertTrue(modelPicker.contains("role = Role.DropdownList"))
        assertTrue(modelPicker.contains("stateDescription"))
        assertTrue(modelPicker.contains("R.string.generate_model_pick"))
        assertTrue(modelPicker.contains("R.string.generate_model_none"))
        assertTrue(modelPicker.contains("heightIn(min = 48.dp)"))

        val webSearch = read("src/main/kotlin/dev/bidzz/opencode/ui/websearch/WebSearchScreen.kt")
        val providerPicker = funBody(webSearch, "private fun ProviderPicker(")
        assertTrue(providerPicker.contains("role = Role.DropdownList"))
        assertTrue(providerPicker.contains("stateDescription"))
        assertTrue(providerPicker.contains("R.string.websearch_provider_pick"))
        assertTrue(providerPicker.contains("heightIn(min = 48.dp)"))
        val webSearchRow = funBody(webSearch, "private fun WebSearchRow(")
        assertTrue(webSearchRow.contains("role = Role.Button"))
        assertTrue(webSearchRow.contains("R.string.websearch_open_result"))
    }

    // m-339 / m-070 / m-859 / m-921 — every touched target meets 48dp.
    @Test
    fun `touch targets meet the 48dp standard`() {
        val diagnostics = read("src/main/kotlin/dev/bidzz/opencode/ui/diagnostics/DiagnosticsScreen.kt")
        // Exactly two 48dp floors in the file: the Follow pill and its
        // "Jump to latest" neighbour. Plain split-count, not a regex —
        // the parens in the pattern must match literally.
        assertEquals(2, diagnostics.split("defaultMinSize(minHeight = 48.dp)").size - 1)

        val markdown = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/MarkdownText.kt")
        assertFalse(markdown.contains("sizeIn(minHeight = 32.dp)"))
        assertTrue(funBody(markdown, "private fun BlockAction(").contains("dims.minTouchTarget"))

        val cards = read("src/main/kotlin/dev/bidzz/opencode/ui/Cards.kt")
        assertTrue(funBody(cards, "fun SelectorChip(").contains("defaultMinSize(minHeight = 48.dp)"))
        assertTrue(funBody(cards, "fun SearchField(").contains("sizeIn(minWidth = 48.dp, minHeight = 48.dp)"))

        val browser = read("src/main/kotlin/dev/bidzz/opencode/ui/browser/InAppBrowserScreen.kt")
        val browserAction = funBody(browser, "private fun BrowserAction(")
        assertTrue(browserAction.contains("sizeIn(minWidth = 48.dp, minHeight = 48.dp)"))
        assertTrue(browserAction.contains("size(48.dp)"))
        assertFalse(browserAction.contains("40.dp"))

        val chat = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt")
        val noticeRow = funBody(chat, "private fun AttachNoticeRow(")
        assertTrue(noticeRow.contains("dims.minTouchTarget"))
        assertFalse(noticeRow.contains("size(40.dp)"))
    }

    // m-031 — file previews have a TalkBack description.
    @Test
    fun `file previews have a TalkBack description`() {
        val preview = read("src/main/kotlin/dev/bidzz/opencode/ui/files/FilePreview.kt")
        assertTrue(preview.contains("R.string.preview_image_desc"))
        assertTrue(preview.contains("R.string.preview_pdf_page_desc"))
        assertFalse(preview.contains("contentDescription = null"))
    }

    // m-905 — the settings toggle row is a single Switch node.
    @Test
    fun `settings toggle row is a single Switch node`() {
        val settings = read("src/main/kotlin/dev/bidzz/opencode/ui/settings/SettingsScreen.kt")
        val row = funBody(settings, "private fun SettingsToggleRow(")
        assertTrue(row.contains("role = Role.Switch"))
        assertTrue(row.contains("toggleable("))
        assertTrue(row.contains("onCheckedChange = null"))
        assertFalse(row.contains("Role.Button"))
    }

    // m-911 — every BasicTextField carries a contentDescription.
    @Test
    fun `every BasicTextField has a contentDescription`() {
        val missing = mutableListOf<String>()
        File("src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val src = file.readText()
                if (!src.contains("BasicTextField(")) return@forEach
                for ((line, call) in fieldCalls(src)) {
                    if (!call.contains("contentDescription")) {
                        missing += "${file.path}:$line"
                    }
                }
            }
        assertTrue(
            "BasicTextField without contentDescription: $missing",
            missing.isEmpty(),
        )
    }

    // m-914 — the bottom sheet exposes a labelled Close button.
    @Test
    fun `bottom sheet exposes a labelled Close button`() {
        val sheet = read("src/main/kotlin/dev/bidzz/opencode/ui/DraggableBottomSheet.kt")
        assertTrue(sheet.contains("R.string.common_close"))
        assertTrue(sheet.contains("Glyph.Close"))
        assertTrue(sheet.contains("onClick = { dismiss() }"))
    }

    // m-1160 — the send button comment matches the 32dp visual.
    @Test
    fun `send button comment matches the 32dp visual`() {
        val tokens = read("../core/design/src/main/kotlin/dev/bidzz/opencode/core/design/Tokens.kt")
        assertTrue(tokens.contains("Visual stays 32dp"))
        assertFalse(tokens.contains("Visual stays 36dp"))
    }
}
