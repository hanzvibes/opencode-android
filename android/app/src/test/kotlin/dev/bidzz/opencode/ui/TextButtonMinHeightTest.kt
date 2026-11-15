package dev.bidzz.opencode.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D2 (m-925): a plain Material 3 `TextButton` renders 40dp
 * tall — under the 48dp touch-target guideline. Every `TextButton` on
 * the screens this batch touched carries `sizeIn(minHeight = 48.dp)`,
 * the same one-line fix `HatchDialogs` already uses. This guard scans
 * the source so a future bare `TextButton(` fails the build instead of
 * waiting for the next audit.
 */
class TextButtonMinHeightTest {

    private val files = listOf(
        "src/main/kotlin/dev/bidzz/opencode/ui/artifacts/ArtifactsScreen.kt",
        "src/main/kotlin/dev/bidzz/opencode/ui/mcp/McpServersScreen.kt",
        "src/main/kotlin/dev/bidzz/opencode/ui/plugin/PluginsScreen.kt",
        "src/main/kotlin/dev/bidzz/opencode/ui/providers/ProvidersScreen.kt",
        "src/main/kotlin/dev/bidzz/opencode/ui/export/ExportScreen.kt",
    )

    @Test
    fun `every TextButton on the touched screens has a 48dp minimum height`() {
        val missing = mutableListOf<String>()
        for (path in files) {
            val src = File(path).readText()
            var from = 0
            while (true) {
                val start = src.indexOf("TextButton(", from)
                if (start < 0) break
                var depth = 0
                var i = start + "TextButton".length
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
                val head = src.substring(start, i)
                if (!head.contains("sizeIn(minHeight = 48.dp)")) {
                    val line = src.substring(0, start).count { it == '\n' } + 1
                    missing += "$path:$line"
                }
                from = i + 1
            }
        }
        assertTrue(
            "TextButton without sizeIn(minHeight = 48.dp): $missing",
            missing.isEmpty(),
        )
    }
}
