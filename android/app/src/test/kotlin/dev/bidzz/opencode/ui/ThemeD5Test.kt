package dev.bidzz.opencode.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D5 (tema / dark / light): the guards that keep this batch's
 * behaviour fixes in place. The repo has no Compose UI test harness, so
 * — like the D2/D3/D4 guards — the behaviour that lives in composables
 * is pinned by scanning the source, and everything pure is unit-tested
 * directly (see [AuraThemeTest] for the mesh / ring / dot / token
 * guards). Visual confirmation (the widget after a pin change, the
 * starting-window flash, the dark scrim) still needs a tablet.
 */
class ThemeD5Test {

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

    // ---- Fork / revert in-flight pills (m-597) ----

    @Test
    fun `TurnActionPill takes an enabled flag and dims when disabled`() {
        val cards = read("src/main/kotlin/dev/bidzz/opencode/ui/Cards.kt")
        // The flag is a signature parameter, so it lives outside the
        // brace-matched body — check it against the whole file.
        assertTrue(cards.contains("enabled: Boolean = true"))
        val pill = funBody(cards, "fun TurnActionPill(")
        assertTrue(pill.contains(".clickable(role = Role.Button, enabled = enabled)"))
        assertTrue(pill.contains(".alpha(if (enabled) 1f else 0.4f)"))
    }

    @Test
    fun `the fork and revert pills and the dialog Fork disable while in flight`() {
        val bubble = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/MessageBubble.kt")
        val forkPill = funBody(bubble, "if (onFork != null)")
        assertTrue(forkPill.contains("enabled = forkEnabled"))
        val revertPill = funBody(bubble, "if (onRevert != null)")
        assertTrue(revertPill.contains("enabled = revertEnabled"))
        val screen = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt")
        assertTrue(screen.contains("forkEnabled = !state.forking"))
        assertTrue(screen.contains("revertEnabled = !state.revertBusy"))
        assertTrue(screen.contains("forking = state.forking"))
    }

    // ---- Widget refresh on pin change (m-1176) ----

    @Test
    fun `changing the theme pin re-pushes the home-screen widget`() {
        val app = read("src/main/kotlin/dev/bidzz/opencode/ui/OpenCodeApp.kt")
        val change = app.indexOf("onThemeModeChange = { chosen ->")
        assertTrue("onThemeModeChange block is gone", change >= 0)
        // Audit-200 D7 (m-860): the window must reach past the
        // documenting comments to the re-push call itself.
        val window = app.substring(change, minOf(change + 1400, app.length))
        // Audit-200 D7 (m-860): the re-push uses the localized context
        // now, so the widget shows strings in the chosen language —
        // the re-push itself is what this guard pins.
        assertTrue(
            "onThemeModeChange must re-push the widget or the widget keeps the old theme",
            window.contains("updateSessionWidget(localized, id, title)"),
        )
    }

    // ---- Scrim is intentionally a no-op in dark (m-1002) ----

    @Test
    fun `the bottom-sheet scrim stays 45 percent black and the decision is documented`() {
        val sheet = read("src/main/kotlin/dev/bidzz/opencode/ui/DraggableBottomSheet.kt")
        assertTrue(sheet.contains("Color.Black.copy(alpha = 0.45f)"))
        assertTrue(sheet.contains("(m-1002)"))
        assertTrue(sheet.contains("Do not \"fix\" this by raising"))
    }
}
