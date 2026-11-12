package dev.bidzz.opencode.ui.files

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The language tag text and glyph in [FileTypeBadge] — and the language
 * name in the file view — must pass WCAG AA (4.5:1) in both themes. The
 * brand colour itself does not: as text on its own 14% tint it is
 * 1.7-3.6:1 in light (worst Rust 1.72:1), which is why [FileTypeInfo]
 * carries explicit [FileTypeInfo.textColor] / [FileTypeInfo.darkTextColor]
 * variants. This test enumerates every [FileTypes.resolve] branch and
 * asserts the variants against the backgrounds the text actually sits
 * on: the badge tint composited over white and over the #EFEFF1 bubble
 * (light), and over #1C1C1E and on #1C1C1E itself (dark, for the file
 * view name which sits on the bare surface). Pure JVM, no Compose.
 */
class FileTypeBadgeTest {

    private val samplePaths = listOf(
        "main.kt", "Main.java", "app.ts", "app.js", "script.py", "lib.rs",
        "main.go", "main.c", "main.cpp", "app.cs", "app.swift", "README.md",
        "config.json", "config.yaml", "config.toml", "layout.xml", "index.html",
        "style.css", "run.sh", "photo.png", "build.gradle", "notes.txt",
        ".gitignore", "Dockerfile", "mystery.xyz",
    )

    @Test
    fun `every language tag text passes AA in light`() {
        val white = Color(0xFFFFFFFF)
        val bubble = Color(0xFFEFEFF1)
        for (path in samplePaths) {
            val info = FileTypes.resolve(path)
            val tintOverWhite = composite(info.color, white, 0.14f)
            val tintOverBubble = composite(info.color, bubble, 0.14f)
            assertTrue(
                "${info.language} light text ${info.textColor} is " +
                    "${contrastRatio(info.textColor, tintOverWhite)} on its tint over white",
                contrastRatio(info.textColor, tintOverWhite) >= 4.5,
            )
            assertTrue(
                "${info.language} light text ${info.textColor} is " +
                    "${contrastRatio(info.textColor, tintOverBubble)} on its tint over #EFEFF1",
                contrastRatio(info.textColor, tintOverBubble) >= 4.5,
            )
        }
        val dir = FileTypes.resolve("src", isDirectory = true)
        assertTrue(
            "Directory light text is ${contrastRatio(dir.textColor, white)} on white",
            contrastRatio(dir.textColor, white) >= 4.5,
        )
    }

    @Test
    fun `every language tag text passes AA in dark`() {
        val darkSurface = Color(0xFF1C1C1E)
        for (path in samplePaths) {
            val info = FileTypes.resolve(path)
            val tintOverDark = composite(info.color, darkSurface, 0.14f)
            assertTrue(
                "${info.language} dark text ${info.darkTextColor} is " +
                    "${contrastRatio(info.darkTextColor, tintOverDark)} on its tint over #1C1C1E",
                contrastRatio(info.darkTextColor, tintOverDark) >= 4.5,
            )
            assertTrue(
                "${info.language} dark text ${info.darkTextColor} is " +
                    "${contrastRatio(info.darkTextColor, darkSurface)} on #1C1C1E",
                contrastRatio(info.darkTextColor, darkSurface) >= 4.5,
            )
        }
    }

    @Test
    fun `the brand colour itself fails AA as tag text in light`() {
        // Guards the reason the variants exist: if the brand ever passed,
        // the table would be dead weight and the test above would be the
        // only thing keeping the variants honest.
        val white = Color(0xFFFFFFFF)
        var failing = 0
        for (path in samplePaths) {
            val info = FileTypes.resolve(path)
            if (contrastRatio(info.color, composite(info.color, white, 0.14f)) < 4.5) {
                failing++
            }
        }
        assertTrue("expected most brands to fail as light tag text, got $failing", failing >= 15)
    }

    // ---- WCAG relative luminance, straight from the spec (mirrors WhiteThemeTest) ----

    private fun composite(foreground: Color, background: Color, alpha: Float): Color {
        val fg = foreground.toArgb()
        val bg = background.toArgb()
        val r = (((fg shr 16) and 0xFF) * alpha + ((bg shr 16) and 0xFF) * (1 - alpha)).toInt()
        val g = (((fg shr 8) and 0xFF) * alpha + ((bg shr 8) and 0xFF) * (1 - alpha)).toInt()
        val b = ((fg and 0xFF) * alpha + (bg and 0xFF) * (1 - alpha)).toInt()
        return Color(0xFF000000.toInt() or (r shl 16) or (g shl 8) or b)
    }

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
