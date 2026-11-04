package dev.bidzz.opencode.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The icons the app uses, drawn on a 24-unit grid.
 *
 * Drawn here rather than imported from an icon library: the set is ten shapes, a
 * library would add a dependency for them, and font glyphs (`☰`, `✎`) render
 * differently on every device. One stroke weight keeps them reading as a family.
 */
enum class Glyph {
    Menu, NewChat, ArrowUp, ArrowDown, ArrowLeft, ChevronDown, ChevronRight,
    File, Folder, Pencil, Terminal, Search, Globe, Sliders,
    Send, Camera, Code, Mail, External, Info,
}

@Composable
fun GlyphIcon(glyph: Glyph, tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val width = 1.8f * u
        val stroke = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), strokeWidth = width, cap = StrokeCap.Round)

        fun shape(vararg points: Pair<Float, Float>, close: Boolean = false) {
            val path = Path()
            points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * u, y * u) else path.lineTo(x * u, y * u) }
            if (close) path.close()
            drawPath(path, tint, style = stroke)
        }

        when (glyph) {
            // Two lines of different length, so it does not read as a stock hamburger.
            Glyph.Menu -> {
                line(4f, 8f, 20f, 8f)
                line(4f, 16f, 14f, 16f)
            }
            Glyph.NewChat -> {
                shape(12f to 5f, 6f to 5f, 5f to 6f, 5f to 18f, 6f to 19f, 18f to 19f, 19f to 18f, 19f to 12f)
                shape(10f to 14f, 10.5f to 11f, 18f to 3.5f, 20.5f to 6f, 13f to 13.5f, 10f to 14f, close = true)
            }
            Glyph.ArrowUp -> {
                line(12f, 19f, 12f, 6f)
                shape(6f to 12f, 12f to 6f, 18f to 12f)
            }
            Glyph.ArrowDown -> {
                line(12f, 5f, 12f, 18f)
                shape(6f to 12f, 12f to 18f, 18f to 12f)
            }
            Glyph.ChevronRight -> shape(9f to 6f, 15f to 12f, 9f to 18f)
            Glyph.ArrowLeft -> {
                line(19f, 12f, 5f, 12f)
                shape(11f to 6f, 5f to 12f, 11f to 18f)
            }
            Glyph.ChevronDown -> shape(7f to 10f, 12f to 15f, 17f to 10f)
            Glyph.File -> {
                shape(7f to 3f, 14f to 3f, 19f to 8f, 19f to 21f, 7f to 21f, close = true)
                shape(14f to 3f, 14f to 8f, 19f to 8f)
            }
            Glyph.Folder -> shape(3f to 7f, 10f to 7f, 12f to 9f, 21f to 9f, 21f to 19f, 3f to 19f, close = true)
            Glyph.Pencil -> {
                shape(15f to 5f, 19f to 9f, 9f to 19f, 5f to 19f, 5f to 15f, close = true)
                line(13f, 7f, 17f, 11f)
            }
            Glyph.Terminal -> {
                shape(5f to 8f, 10f to 12f, 5f to 16f)
                line(12f, 17f, 19f, 17f)
            }
            Glyph.Search -> {
                drawCircle(tint, radius = 6f * u, center = Offset(11f * u, 11f * u), style = stroke)
                line(15.5f, 15.5f, 20f, 20f)
            }
            Glyph.Globe -> {
                drawCircle(tint, radius = 9f * u, center = Offset(12f * u, 12f * u), style = stroke)
                drawOval(tint, Offset(8f * u, 3f * u), Size(8f * u, 18f * u), style = stroke)
                line(3f, 12f, 21f, 12f)
            }
            Glyph.Sliders -> {
                line(4f, 7f, 20f, 7f)
                line(4f, 12f, 20f, 12f)
                line(4f, 17f, 20f, 17f)
                drawCircle(tint, radius = 2.2f * u, center = Offset(9f * u, 7f * u))
                drawCircle(tint, radius = 2.2f * u, center = Offset(15f * u, 12f * u))
                drawCircle(tint, radius = 2.2f * u, center = Offset(8f * u, 17f * u))
            }
            // Paper plane: Telegram.
            Glyph.Send -> {
                shape(21f to 3f, 3f to 10.5f, 10f to 13.5f, close = true)
                shape(21f to 3f, 10f to 13.5f, 13f to 21f, close = true)
            }
            Glyph.Camera -> {
                drawRoundRect(
                    tint, Offset(3.5f * u, 3.5f * u), Size(17f * u, 17f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f * u), style = stroke,
                )
                drawCircle(tint, radius = 4f * u, center = Offset(12f * u, 12f * u), style = stroke)
                drawCircle(tint, radius = 0.9f * u, center = Offset(17f * u, 7f * u))
            }
            Glyph.Code -> {
                shape(8f to 7f, 3f to 12f, 8f to 17f)
                shape(16f to 7f, 21f to 12f, 16f to 17f)
                line(13.5f, 5f, 10.5f, 19f)
            }
            Glyph.Mail -> {
                drawRoundRect(
                    tint, Offset(3f * u, 5f * u), Size(18f * u, 14f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * u), style = stroke,
                )
                shape(3.5f to 7f, 12f to 13f, 20.5f to 7f)
            }
            // Arrow leaving a box: "opens outside the app".
            Glyph.External -> {
                line(8f, 16f, 17f, 7f)
                shape(9f to 7f, 17f to 7f, 17f to 15f)
            }
            Glyph.Info -> {
                drawCircle(tint, radius = 9f * u, center = Offset(12f * u, 12f * u), style = stroke)
                line(12f, 11f, 12f, 16.5f)
                drawCircle(tint, radius = 0.9f * u, center = Offset(12f * u, 7.8f * u))
            }
        }
    }
}
