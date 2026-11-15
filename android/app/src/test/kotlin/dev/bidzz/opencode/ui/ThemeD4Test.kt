package dev.bidzz.opencode.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.bidzz.opencode.core.design.Palette
import dev.bidzz.opencode.core.design.ThemeMode
import dev.bidzz.opencode.core.design.isDarkTheme
import dev.bidzz.opencode.core.design.resolveLaunchWindow
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D4 (tema / dark / light): the launch-window pin, the lock
 * screen pin, the widget pin, the three contrast fixes, and the guards
 * that keep the batch's other fixes in place. The repo has no Compose UI
 * test harness, so — like the D2/D3 guards — the behaviour that lives in
 * composables is pinned by scanning the source, and everything pure (the
 * launch-window resolver, the contrast ratios, the snippet helpers'
 * siblings) is unit-tested directly. Visual confirmation (the flash
 * duration, the widget on a launcher, the lock screen) still needs a
 * tablet.
 */
class ThemeD4Test {

    private fun read(path: String): String = File(path).readText()

    /** Body of the function whose signature starts at [marker], brace-matched (D3 pattern). */
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

    // ---- Launch-window resolver (m-014/m-342/m-437/m-500) ----

    @Test
    fun `a Dark pin on a Light system launches dark with dark bars`() {
        val style = resolveLaunchWindow(ThemeMode.Dark, systemDark = false)
        assertEquals(0xFF000000.toInt(), style.backgroundArgb)
        assertFalse(style.lightBars)
    }

    @Test
    fun `a Light pin on a Dark system launches light with light bars`() {
        val style = resolveLaunchWindow(ThemeMode.Light, systemDark = true)
        assertEquals(0xFFF9F9FA.toInt(), style.backgroundArgb)
        assertTrue(style.lightBars)
    }

    @Test
    fun `System follows the system theme either way`() {
        assertEquals(0xFF000000.toInt(), resolveLaunchWindow(ThemeMode.System, systemDark = true).backgroundArgb)
        assertFalse(resolveLaunchWindow(ThemeMode.System, systemDark = true).lightBars)
        assertEquals(0xFFF9F9FA.toInt(), resolveLaunchWindow(ThemeMode.System, systemDark = false).backgroundArgb)
        assertTrue(resolveLaunchWindow(ThemeMode.System, systemDark = false).lightBars)
    }

    @Test
    fun `isDarkTheme resolves the pin the same way Compose does`() {
        assertTrue(isDarkTheme(ThemeMode.Dark, systemDark = false))
        assertFalse(isDarkTheme(ThemeMode.Light, systemDark = true))
        assertTrue(isDarkTheme(ThemeMode.System, systemDark = true))
        assertFalse(isDarkTheme(ThemeMode.System, systemDark = false))
    }

    @Test
    fun `MainActivity applies the pin before the first Compose frame`() {
        val main = read("src/main/kotlin/dev/bidzz/opencode/MainActivity.kt")
        val applyAt = main.indexOf("applyLaunchWindowTheme(this)")
        assertTrue("MainActivity never applies the launch-window theme", applyAt >= 0)
        val setContentAt = main.indexOf("setContent {", applyAt)
        assertTrue("applyLaunchWindowTheme must run before setContent", setContentAt > applyAt)
    }

    @Test
    fun `the lock screen reads the pin instead of the system theme`() {
        val lock = read("src/main/kotlin/dev/bidzz/opencode/ui/security/AppLock.kt")
        assertTrue(lock.contains("ThemeStore.get("))
        assertTrue(lock.contains("OpenCodeTheme(themeMode = themeMode)"))
        assertFalse("LockScreen must not use the bare System default", lock.contains("OpenCodeTheme {"))
    }

    @Test
    fun `the launch-window comments no longer claim the system theme is the app theme`() {
        val light = read("src/main/res/values/themes.xml")
        val night = read("src/main/res/values-night/themes.xml")
        assertFalse(light.contains("follows the light theme (Putih B)"))
        assertTrue(light.contains("SYSTEM"))
        assertTrue(night.contains("SYSTEM"))
    }

    // ---- Contrast (m-002 / m-015 / m-341) ----

    // WCAG relative luminance, straight from the spec (WhiteThemeTest pattern).
    private fun luminance(color: Color): Double {
        val argb = color.toArgb()
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        fun linear(c: Double) = if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = luminance(foreground)
        val l2 = luminance(background)
        val (lighter, darker) = if (l1 >= l2) l1 to l2 else l2 to l1
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** [foreground] painted at [alpha] over [background], as the tag tint does.
     * Channels round to nearest, matching Compose's own 8-bit conversion
     * (the research pinned tint 0xFF372121, which truncation misses by 1
     * on the green channel: 32.92 truncates to 32, rounds to 33). */
    private fun blend(foreground: Color, alpha: Float, background: Color): Color {
        fun channel(f: Int, b: Int) = (f * alpha + b * (1 - alpha)).roundToInt()
        val fg = foreground.toArgb()
        val bg = background.toArgb()
        return Color(
            channel((fg shr 16) and 0xFF, (bg shr 16) and 0xFF),
            channel((fg shr 8) and 0xFF, (bg shr 8) and 0xFF),
            channel(fg and 0xFF, bg and 0xFF),
        )
    }

    @Test
    fun `m-002 the danger tag text passes in dark once it uses the text variant`() {
        assertEquals(0xFFFF453A.toInt(), Palette.danger.toArgb())
        assertEquals(0xFFFF8A80.toInt(), Palette.darkToolErrorText.toArgb())
        val tint = blend(Palette.danger, 0.12f, Palette.darkSurfaceRaised)
        assertEquals("tint arithmetic changed — re-derive the ratios below", 0xFF372121.toInt(), tint.toArgb())
        assertTrue(
            "raw danger on its tint is ${contrastRatio(Palette.danger, tint)}, still below AA — the fix did not land",
            contrastRatio(Palette.danger, tint) < 4.5,
        )
        assertTrue(
            "toolErrorText on the danger tint is ${contrastRatio(Palette.darkToolErrorText, tint)}, below AA 4.5:1",
            contrastRatio(Palette.darkToolErrorText, tint) >= 4.5,
        )
    }

    @Test
    fun `m-002 the AppTag maps danger to its text variant`() {
        val cards = read("src/main/kotlin/dev/bidzz/opencode/ui/Cards.kt")
        val body = funBody(cards, "fun AppTag(")
        assertTrue(body.contains("colors.danger -> colors.toolErrorText"))
    }

    @Test
    fun `m-002 the danger tag still passes in light`() {
        val tint = blend(Palette.lightDangerText, 0.12f, Color.White)
        assertTrue(
            "light danger on its tint is ${contrastRatio(Palette.lightDangerText, tint)}, below AA 4.5:1",
            contrastRatio(Palette.lightDangerText, tint) >= 4.5,
        )
    }

    @Test
    fun `m-015 full faint line numbers pass on sunken in both themes`() {
        assertTrue(
            "light faint on sunken is ${contrastRatio(Palette.lightOnSurfaceFaint, Palette.lightSurfaceSunken)}, below AA 4.5:1",
            contrastRatio(Palette.lightOnSurfaceFaint, Palette.lightSurfaceSunken) >= 4.5,
        )
        assertTrue(
            "dark faint on sunken is ${contrastRatio(Palette.darkOnSurfaceFaint, Palette.darkSurfaceSunken)}, below AA 4.5:1",
            contrastRatio(Palette.darkOnSurfaceFaint, Palette.darkSurfaceSunken) >= 4.5,
        )
        // The defect, locked: the old alphas fail even the 3:1 graphic bar.
        assertTrue(contrastRatio(blend(Palette.lightOnSurfaceFaint, 0.45f, Palette.lightSurfaceSunken), Palette.lightSurfaceSunken) < 3.0)
        assertTrue(contrastRatio(blend(Palette.darkOnSurfaceFaint, 0.5f, Palette.darkSurfaceSunken), Palette.darkSurfaceSunken) < 3.0)
    }

    @Test
    fun `m-015 no line number paints faint at an alpha anymore`() {
        val markdown = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/MarkdownText.kt")
        val diagnostics = read("src/main/kotlin/dev/bidzz/opencode/ui/diagnostics/DiagnosticsScreen.kt")
        assertFalse(markdown.contains("onSurfaceFaint.copy(alpha"))
        assertFalse(diagnostics.contains("onSurfaceFaint.copy(alpha"))
    }

    @Test
    fun `m-341 running status text passes on the page background in both themes`() {
        assertEquals(0xFF005FCC.toInt(), Palette.lightAccentText.toArgb())
        assertTrue(
            "light accentText on the page is ${contrastRatio(Palette.lightAccentText, Palette.lightBackground)}, below AA 4.5:1",
            contrastRatio(Palette.lightAccentText, Palette.lightBackground) >= 4.5,
        )
        assertTrue(
            "dark accentText on the page is ${contrastRatio(Palette.darkAccent, Palette.darkBackground)}, below AA 4.5:1",
            contrastRatio(Palette.darkAccent, Palette.darkBackground) >= 4.5,
        )
        assertTrue(
            "light accent on the page is ${contrastRatio(Palette.lightAccent, Palette.lightBackground)}, expected below AA — the defect moved",
            contrastRatio(Palette.lightAccent, Palette.lightBackground) < 4.5,
        )
    }

    @Test
    fun `m-341 the Jobs status word uses the text-safe accent, the dot keeps accent`() {
        val jobs = read("src/main/kotlin/dev/bidzz/opencode/ui/jobs/JobsScreen.kt")
        val body = funBody(jobs, "private fun JobRow(")
        assertTrue(body.contains("colors.accentText"))
        assertTrue(body.contains("color = textColor"))
        // The dot itself keeps dotColor (one occurrence: the StatusDot call);
        // the status Text must not reuse it — a revert would add a second one.
        assertEquals(
            "the status Text must not reuse the dot colour",
            1,
            body.split("color = dotColor").size - 1,
        )
    }

    // ---- Language tables (m-068 / m-076) ----

    @Test
    fun `m-068 the title-bar label covers the tags the highlighter knows`() {
        val markdown = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/MarkdownText.kt")
        val body = funBody(markdown, "private fun formatLanguageName(")
        assertTrue(body.contains("\"kts\""))
        assertTrue(body.contains("\"mjs\""))
        assertTrue(body.contains("\"h\" -> \"C Header\""))
        assertTrue(body.contains("\"hpp\" -> \"C++ Header\""))
        assertTrue(body.contains("\"console\", \"terminal\""))
        assertTrue(body.contains("\"c++\""))
        assertTrue(body.contains("\"c#\""))
    }

    @Test
    fun `m-076 the docker dialect is highlighted as a Dockerfile`() {
        val highlight = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/SyntaxHighlight.kt")
        val body = funBody(highlight, "private fun dialectOf(")
        assertTrue(body.contains("\"docker\""))
    }

    @Test
    fun `m-076 console and terminal save as shell but stay Save-only`() {
        val save = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/CodeBlockSave.kt")
        val runnable = funBody(save, "fun isRunnableCodeBlock(")
        assertFalse("console/terminal must not gain Run", runnable.contains("console"))
        assertFalse("console/terminal must not gain Run", runnable.contains("terminal"))
    }

    // ---- Camera index (m-144), saved permissions (m-210), sidebar (m-329) ----

    @Test
    fun `m-144 the camera flow drops the cached quick-open index after a save`() {
        val chat = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt")
        val collector = funBody(chat, "LaunchedEffect(cameraPhotoPicked)")
        assertTrue(collector.contains("onInvalidateFileIndex()"))
        assertTrue(collector.indexOf("onDraftChange(insertMention") < collector.indexOf("onInvalidateFileIndex()"))
        val app = read("src/main/kotlin/dev/bidzz/opencode/ui/OpenCodeApp.kt")
        assertTrue(app.contains("onInvalidateFileIndex = explorerViewModel::invalidateQuickOpenIndex"))
    }

    @Test
    fun `m-210 a saved permission with no action or resource never shows its raw id`() {
        val settings = read("src/main/kotlin/dev/bidzz/opencode/ui/settings/SettingsScreen.kt")
        assertFalse(settings.contains(".ifBlank { saved.id }"))
        assertTrue(settings.contains("stringResource(R.string.settings_saved_permissions)"))
    }

    @Test
    fun `m-329 the sidebar reload effect re-runs when the entry load finishes`() {
        val app = read("src/main/kotlin/dev/bidzz/opencode/ui/OpenCodeApp.kt")
        assertTrue(app.contains("LaunchedEffect(chatState.sessionID, isExpanded, sessionsState.loading)"))
    }

    // ---- Widget (m-228), in-flight guards (m-392), stale comment (m-344) ----

    @Test
    fun `m-228 the widget resolves the pinned theme and colours itself`() {
        val widget = read("src/main/kotlin/dev/bidzz/opencode/ui/widget/SessionWidgetProvider.kt")
        assertTrue(widget.contains("ThemeStore.get("))
        assertTrue(widget.contains("setTextColor"))
        assertTrue(widget.contains("setBackgroundResource"))
        assertTrue(widget.contains("widget_background_dark"))
        assertTrue(File("src/main/res/drawable/widget_background_dark.xml").isFile)
    }

    @Test
    fun `m-392 the MCP load cancels the in-flight load and drops stale results`() {
        val mcp = read("src/main/kotlin/dev/bidzz/opencode/ui/mcp/McpServersViewModel.kt")
        val body = funBody(mcp, "private fun load(showLoading: Boolean)")
        assertTrue(body.contains("++loadGeneration"))
        assertTrue(body.contains("loadJob?.cancel()"))
        assertTrue(body.contains("if (generation != loadGeneration) return@launch"))
    }

    @Test
    fun `m-392 the Plugins refresh carries the same in-flight guard`() {
        val plugins = read("src/main/kotlin/dev/bidzz/opencode/ui/plugin/PluginsViewModel.kt")
        val body = funBody(plugins, "fun refresh()")
        assertTrue(body.contains("++loadGeneration"))
        assertTrue(body.contains("loadJob?.cancel()"))
        assertTrue(body.contains("if (generation != loadGeneration) return@launch"))
    }

    @Test
    fun `m-344 the PR 39 not-landed claim is gone from the theme and its test`() {
        val theme = read("../core/design/src/main/kotlin/dev/bidzz/opencode/core/design/Theme.kt")
        val scheme = read("src/test/kotlin/dev/bidzz/opencode/ui/MaterialSchemeTest.kt")
        assertFalse(theme.contains("PR #39"))
        assertFalse(scheme.contains("PR #39"))
    }

    // ---- Search snippet wiring (m-172) ----

    @Test
    fun `m-172 both session surfaces render the match snippet when one exists`() {
        val list = read("src/main/kotlin/dev/bidzz/opencode/ui/sessions/SessionListScreen.kt")
        assertTrue(list.contains("sessionMatchSnippet(query, detail)"))
        assertTrue(list.contains("highlightMatches(snippet, searchTokens(query)"))
        val chat = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt")
        val drawer = funBody(chat, "private fun SessionDrawerItem(")
        assertTrue(drawer.contains("sessionMatchSnippet(query, detail)"))
        assertTrue(drawer.contains("highlightMatches(snippet, searchTokens(query)"))
    }
}
