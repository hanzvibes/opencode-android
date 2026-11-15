package dev.bidzz.opencode.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.AuraPalette
import dev.bidzz.opencode.core.design.OpenCodeTokens
import dev.bidzz.opencode.core.design.Palette
import dev.bidzz.opencode.ui.chat.blockquoteBarColor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aura palette + two-layer shadow tokens (Opsi B).
 *
 * The exact hex values extracted from the Meta Muse app
 * (`com.facebook.aura` 16.0.0.2.159, `resources.arsc`), and the WCAG
 * contrast audit of every Aura text role on the surfaces it may sit on
 * (#FFFFFF card / #F6F7F8 Aura page). Pure JVM: [Color.toArgb] plus
 * local luminance math, no Android framework, no Robolectric — the same
 * pattern as [WhiteThemeTest].
 *
 * The guard is the point: Aura blue #5890FF and faint #9197A3 look like
 * text colours but fail AA outright, so the tests lock them to
 * dots/fills/decoration. [AuraPalette.auraBlueFill] and [AuraPalette.auraDanger]
 * pass on white and fail on the Aura page, so the tests lock the
 * white-only rule instead of letting a call site assume AA everywhere.
 */
class AuraThemeTest {

    // ---- Extracted hex (resources.arsc) ----

    @Test
    fun `aura blue scale is the extracted Facebook blue`() {
        assertEquals(0xFF5890FF.toInt(), AuraPalette.auraBlue.toArgb())
        assertEquals(0xFF4673CC.toInt(), AuraPalette.auraBlueFill.toArgb())
        assertEquals(0xFF355089.toInt(), AuraPalette.auraBlueText.toArgb())
        assertEquals(0xFF9BBCFF.toInt(), AuraPalette.auraBlueLight.toArgb())
        // Audit-200 D5 (m-673/m-999): the alpha steps had no production
        // call site (auraBlueAlpha20 only lived in the dead
        // HatchHighlightGlow) and were deleted with it.
    }

    @Test
    fun `aura purple is the extracted M3 baseline`() {
        assertEquals(0xFF6750A4.toInt(), AuraPalette.auraPurple.toArgb())
        assertEquals(0xFFEADDFF.toInt(), AuraPalette.auraPurpleContainer.toArgb())
        // Audit-200 D5 (m-673/m-999): auraPurpleLight had no production
        // call site and was deleted.
    }

    @Test
    fun `aura neutrals and navy are the extracted values`() {
        assertEquals(0xFFE9EAED.toInt(), AuraPalette.auraBorder.toArgb())
        assertEquals(0xFF6A7180.toInt(), AuraPalette.auraMuted.toArgb())
        assertEquals(0xFF9197A3.toInt(), AuraPalette.auraFaint.toArgb())
        assertEquals(0xFF141823.toInt(), AuraPalette.auraNavy.toArgb())
        assertEquals(0xFF080A10.toInt(), AuraPalette.auraNavyDeep.toArgb())
        assertEquals(0xFF232937.toInt(), AuraPalette.auraSlate.toArgb())
        // Audit-200 D5 (m-673/m-999): auraSurface, auraOnSurface,
        // auraSlateLight and auraSlateBorder had no production call site
        // and were deleted. auraNavy / auraNavyDeep / auraSlate survive
        // as the dark mesh gradient stops (m-674).
    }

    @Test
    fun `aura semantics are the extracted green and red`() {
        assertEquals(0xFF1D873B.toInt(), AuraPalette.auraSuccess.toArgb())
        assertEquals(0xFFD93025.toInt(), AuraPalette.auraDanger.toArgb())
        // Audit-200 D5 (m-673/m-999): auraSuccessText had no production
        // call site and was deleted.
    }

    // ---- WCAG AA text roles (4.5:1) ----

    @Test
    fun `aura blue text muted and purple are AA on white and on the app page`() {
        // Audit-200 D5 (m-673/m-999): the Aura page #F6F7F8 is gone — no
        // production call site used it — so the page is the real app page
        // #F9F9FA now. auraSuccessText and auraOnSurface were deleted in
        // the same batch.
        val white = Color(0xFFFFFFFF)
        val page = Color(0xFFF9F9FA)
        assertTrue(contrastRatio(AuraPalette.auraBlueText, white) >= 4.5)
        assertTrue(contrastRatio(AuraPalette.auraBlueText, page) >= 4.5)
        assertTrue(contrastRatio(AuraPalette.auraMuted, white) >= 4.5)
        assertTrue(contrastRatio(AuraPalette.auraMuted, page) >= 4.5)
        assertTrue(contrastRatio(AuraPalette.auraPurple, white) >= 4.5)
    }

    @Test
    fun `aura blue fill is AA on white only, danger is AA on white and the app page`() {
        val white = Color(0xFFFFFFFF)
        val page = Color(0xFFF9F9FA)
        // White text on the fill, and the colour as text on white: both AA.
        assertTrue(contrastRatio(white, AuraPalette.auraBlueFill) >= 4.5)
        assertTrue(contrastRatio(AuraPalette.auraDanger, white) >= 4.5)
        // …but the fill falls under AA on the app page, so a call site must
        // not use it as text there (use auraBlueText).
        assertTrue(contrastRatio(AuraPalette.auraBlueFill, page) < 4.5)
        // Audit-200 D5 (m-673/m-999): danger was "white only" against the
        // dead #F6F7F8 page (4.45:1); on the real page #F9F9FA it is 4.54:1
        // — barely AA — so the old negative assertion is wrong now.
        assertTrue(contrastRatio(AuraPalette.auraDanger, page) >= 4.5)
    }

    @Test
    fun `aura blue and faint are never text`() {
        val white = Color(0xFFFFFFFF)
        assertTrue(contrastRatio(AuraPalette.auraBlue, white) < 4.5)
        assertTrue(contrastRatio(AuraPalette.auraFaint, white) < 4.5)
    }

    @Test
    fun `the quote bar keeps the aura fill in light and the app accent in dark`() {
        // Audit-200 D1 (m-062): the bar used the light-only Aura fill in both
        // themes, contradicting the file's own docstring.
        val accent = Color(0xFF007AFF)
        assertEquals(AuraPalette.auraBlueFill, blockquoteBarColor(isDark = false, accent))
        assertEquals(accent, blockquoteBarColor(isDark = true, accent))
    }

    @Test
    fun `aura purple container pair and navy pair are AA`() {
        assertTrue(
            contrastRatio(AuraPalette.auraPurple, AuraPalette.auraPurpleContainer) >= 4.5,
        )
        assertTrue(contrastRatio(Color.White, AuraPalette.auraNavy) >= 4.5)
        assertTrue(contrastRatio(AuraPalette.auraBlueLight, AuraPalette.auraNavy) >= 4.5)
    }

    // ---- Two-layer shadow tokens ----

    @Test
    fun `aura card shadow is two layers, ambient 2dp 6pct plus key 8dp 5pct`() {
        val card = OpenCodeTokens().auraShadow.card
        assertEquals(2.dp, card.ambient.elevation)
        assertEquals(0x0F000000.toInt(), card.ambient.ambientColor.toArgb())
        assertEquals(0x0F000000.toInt(), card.ambient.spotColor.toArgb())
        assertEquals(8.dp, card.key.elevation)
        assertEquals(0x0D000000.toInt(), card.key.ambientColor.toArgb())
        assertEquals(0x0D000000.toInt(), card.key.spotColor.toArgb())
    }

    @Test
    fun `aura raised shadow is one step above the card`() {
        val raised = OpenCodeTokens().auraShadow.raised
        assertEquals(4.dp, raised.ambient.elevation)
        assertEquals(16.dp, raised.key.elevation)
        assertTrue(raised.key.elevation > OpenCodeTokens().auraShadow.card.key.elevation)
    }

    // ---- Audit-200 D5: mesh gradient, ring, dark dots (m-674/m-675/m-998/m-1001) ----

    @Test
    fun `mesh stops are bright in light and navy in dark`() {
        assertEquals(
            listOf(AuraPalette.auraBlue, AuraPalette.auraPurple, AuraPalette.auraBlueLight),
            auraMeshStops(isDark = false),
        )
        assertEquals(
            listOf(AuraPalette.auraNavy, AuraPalette.auraSlate, AuraPalette.auraNavyDeep),
            auraMeshStops(isDark = true),
        )
        assertEquals(0.22f, auraMeshHighlightAlpha(isDark = false))
        assertEquals(0.08f, auraMeshHighlightAlpha(isDark = true))
    }

    @Test
    fun `the refresh ring clears the 3 to 1 graphics bar on every real surface`() {
        // Audit-200 D5 (m-1001): the plain Aura blue was 2.91:1 on the real
        // light page — under the bar — so the ring uses auraBlueFill.
        val white = Color(0xFFFFFFFF)
        val lightPage = Color(0xFFF9F9FA)
        val darkRaised = Color(0xFF1C1C1E)
        val black = Color(0xFF000000)
        assertTrue(contrastRatio(AuraPalette.auraBlueFill, white) >= 3.0)
        assertTrue(contrastRatio(AuraPalette.auraBlueFill, lightPage) >= 3.0)
        assertTrue(contrastRatio(AuraPalette.auraBlueFill, darkRaised) >= 3.0)
        assertTrue(contrastRatio(AuraPalette.auraBlueFill, black) >= 3.0)
        assertTrue(contrastRatio(AuraPalette.auraBlue, lightPage) < 3.0)
    }

    @Test
    fun `the publish dots clear 3 to 1 on the dark sheet and keep their light fills`() {
        // Audit-200 D5 (m-675): the light fills were 3.47–3.71:1 on the dark
        // sheet and the muted/faint order inverted there.
        val darkSheet = Color(0xFF1C1C1E)
        for (state in HatchPublishState.entries) {
            val dot = state.dotColor(isDark = true)
            assertTrue(
                "$state dark dot is ${contrastRatio(dot, darkSheet)}, below 3:1 on the dark sheet",
                contrastRatio(dot, darkSheet) >= 3.0,
            )
        }
        assertEquals(AuraPalette.auraBlueFill, HatchPublishState.PUBLISH.dotColor(isDark = false))
        assertEquals(AuraPalette.auraSuccess, HatchPublishState.SUCCESS.dotColor(isDark = false))
        assertEquals(AuraPalette.auraDanger, HatchPublishState.FAILED.dotColor(isDark = false))
        assertEquals(AuraPalette.auraMuted, HatchPublishState.UP_TO_DATE.dotColor(isDark = false))
        assertEquals(AuraPalette.auraFaint, HatchPublishState.UNPUBLISHED.dotColor(isDark = false))
        // The muted dot stays visibly brighter than the faint dot in dark.
        assertTrue(
            contrastRatio(HatchPublishState.UP_TO_DATE.dotColor(isDark = true), darkSheet) >
                contrastRatio(HatchPublishState.UNPUBLISHED.dotColor(isDark = true), darkSheet),
        )
    }

    @Test
    fun `aura text tokens fail AA on the dark surface, so they must never be dark text`() {
        // Audit-200 D5 (m-998): the negative lock — auraBlueText (2.16:1)
        // and auraPurple (2.64:1) on dark raised are dark-on-dark. The
        // selected tab label and the file-action button branched on isDark
        // in the same batch because they used auraBlueText unguarded.
        val darkRaised = Color(0xFF1C1C1E)
        assertTrue(contrastRatio(AuraPalette.auraBlueText, darkRaised) < 4.5)
        assertTrue(contrastRatio(AuraPalette.auraPurple, darkRaised) < 4.5)
        assertEquals(Palette.darkAccent, hatchTabLabelColor(selected = true, isDark = true))
        assertEquals(AuraPalette.auraBlueText, hatchTabLabelColor(selected = true, isDark = false))
    }

    @Test
    fun `the navy link stays AA on the light assistant bubble`() {
        // Audit-200 D5 (m-698): the trade-off is kept on purpose (navy is
        // 6.87:1 on the bubble but only 2.49:1 against body text, so the
        // underline carries the distinction) — this pins the AA half.
        assertTrue(
            contrastRatio(AuraPalette.auraBlueText, Palette.lightAssistantBubble) >= 4.5,
        )
    }

    @Test
    fun `no aura text token is used as a colour outside an isDark branch`() {
        // Audit-200 D5 (m-998): every production use of auraBlueText /
        // auraPurple as a colour must sit in a function that also branches
        // on isDark. Two call sites were unguarded before this batch (the
        // file-action button, the tab label) and were fixed with it.
        val files = listOf(
            "src/main/kotlin/dev/bidzz/opencode/ui/HatchPublishSheet.kt",
            "src/main/kotlin/dev/bidzz/opencode/ui/HatchFeedback.kt",
            "src/main/kotlin/dev/bidzz/opencode/ui/HatchFileActions.kt",
            "src/main/kotlin/dev/bidzz/opencode/ui/HatchInlineBanner.kt",
            "src/main/kotlin/dev/bidzz/opencode/ui/HatchTabs.kt",
            "src/main/kotlin/dev/bidzz/opencode/ui/chat/MarkdownText.kt",
        )
        for (path in files) {
            val src = File(path).readText()
            for (body in topLevelBodies(src)) {
                if (body.contains("AuraPalette.auraBlueText") || body.contains("AuraPalette.auraPurple")) {
                    assertTrue(
                        "$path has an aura text token in a function with no isDark branch",
                        body.contains("isDark"),
                    )
                }
            }
        }
    }

    @Test
    fun `no deleted aura token is referenced from production code`() {
        // Audit-200 D5 (m-673/m-999): the 11 deleted tokens must not
        // survive anywhere outside Palette.kt itself.
        val deleted = listOf(
            "auraBlueAlpha10", "auraBlueAlpha20", "auraBlueAlpha40",
            "auraBlueAlpha60", "auraBlueAlpha80", "auraPurpleLight",
            "auraSurface", "auraOnSurface", "auraSlateLight",
            "auraSlateBorder", "auraSuccessText",
        )
        val main = File("src/main/kotlin")
        val offenders = main.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { !it.path.endsWith("core/design/Palette.kt") }
            .filter { file ->
                val text = file.readText()
                deleted.any { text.contains(it) }
            }
            .map { it.path }
            .toList()
        assertTrue("deleted aura tokens still referenced in: $offenders", offenders.isEmpty())
        assertTrue(
            "HatchHighlightGlow must be gone with auraBlueAlpha20",
            !File("src/main/kotlin/dev/bidzz/opencode/ui/HatchMotion.kt").readText()
                .contains("fun HatchHighlightGlow"),
        )
    }

    /** Top-level function bodies of [src], brace-matched (D3/D4 pattern). */
    private fun topLevelBodies(src: String): List<String> {
        val bodies = mutableListOf<String>()
        val starts = Regex("(?m)^(private\\s+|internal\\s+)?fun\\s").findAll(src).map { it.range.first }.toList()
        for (start in starts) {
            var i = src.indexOf('{', start)
            if (i < 0) continue
            var depth = 0
            val bodyStart = i
            while (i < src.length) {
                when (src[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            bodies.add(src.substring(bodyStart, i + 1))
                            break
                        }
                    }
                }
                i++
            }
        }
        return bodies
    }

    // ---- WCAG relative luminance, straight from the spec ----

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
}
