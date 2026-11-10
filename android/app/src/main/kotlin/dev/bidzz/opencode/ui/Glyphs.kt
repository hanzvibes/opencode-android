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
    Send, Camera, Code, Mail, External, Info, Copy, Check, Close, Mic,
    Eye, EyeOff, Key, Sparkle, Refresh,
    GitBranch, Sparkles, CheckCircle, Warning, Play, Stop,
    FileCode, Lightning, Pin, Trash, Dots, Pulse, User,
    Question, Square, CheckSquare, History,
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
            Glyph.Copy -> {
                shape(8f to 4f, 19f to 4f, 19f to 15f)
                drawRoundRect(
                    tint, Offset(5f * u, 8f * u), Size(11f * u, 12f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * u), style = stroke,
                )
            }
            Glyph.Check -> shape(5f to 12.5f, 9.5f to 17f, 19f to 7.5f)
            Glyph.Close -> {
                line(6f, 6f, 18f, 18f)
                line(18f, 6f, 6f, 18f)
            }
            Glyph.Mic -> {
                drawRoundRect(
                    tint, Offset(9f * u, 3.5f * u), Size(6f * u, 10f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * u), style = stroke,
                )
                shape(6f to 10f, 6f to 13.5f, 7.5f to 16.5f, 12f to 17.5f, 16.5f to 16.5f, 18f to 13.5f, 18f to 10f)
                line(12f, 17.5f, 12f, 21f)
                line(9f, 21f, 15f, 21f)
            }
            Glyph.Eye -> {
                val path = Path()
                path.moveTo(3f * u, 12f * u)
                path.quadraticBezierTo(12f * u, 5f * u, 21f * u, 12f * u)
                path.quadraticBezierTo(12f * u, 19f * u, 3f * u, 12f * u)
                drawPath(path, tint, style = stroke)
                drawCircle(tint, radius = 3f * u, center = Offset(12f * u, 12f * u), style = stroke)
            }
            Glyph.EyeOff -> {
                val path = Path()
                path.moveTo(3f * u, 12f * u)
                path.quadraticBezierTo(12f * u, 5f * u, 21f * u, 12f * u)
                path.quadraticBezierTo(12f * u, 19f * u, 3f * u, 12f * u)
                drawPath(path, tint, style = stroke)
                drawCircle(tint, radius = 3f * u, center = Offset(12f * u, 12f * u), style = stroke)
                line(4f, 4f, 20f, 20f)
            }
            Glyph.Key -> {
                drawCircle(tint, radius = 3.5f * u, center = Offset(8.5f * u, 8.5f * u), style = stroke)
                line(11f, 11f, 20f, 20f)
                line(16f, 16f, 18.5f, 13.5f)
                line(18f, 18f, 20.5f, 15.5f)
            }
            Glyph.Sparkle -> {
                val path = Path()
                path.moveTo(12f * u, 3f * u)
                path.quadraticBezierTo(12f * u, 12f * u, 21f * u, 12f * u)
                path.quadraticBezierTo(12f * u, 12f * u, 12f * u, 21f * u)
                path.quadraticBezierTo(12f * u, 12f * u, 3f * u, 12f * u)
                path.quadraticBezierTo(12f * u, 12f * u, 12f * u, 3f * u)
                path.close()
                drawPath(path, tint)
            }
            Glyph.Refresh -> {
                drawArc(
                    color = tint,
                    startAngle = 45f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(4.5f * u, 4.5f * u),
                    size = Size(15f * u, 15f * u),
                    style = stroke,
                )
                shape(15f to 2f, 19.5f to 5.5f, 15.5f to 9f)
            }
            Glyph.GitBranch -> {
                drawCircle(tint, radius = 2.2f * u, center = Offset(7f * u, 6f * u), style = stroke)
                drawCircle(tint, radius = 2.2f * u, center = Offset(7f * u, 18f * u), style = stroke)
                drawCircle(tint, radius = 2.2f * u, center = Offset(17f * u, 7f * u), style = stroke)
                line(7f, 8.5f, 7f, 15.5f)
                val branch = Path().apply {
                    moveTo(7f * u, 14f * u)
                    cubicTo(7f * u, 10.5f * u, 17f * u, 12f * u, 17f * u, 9.5f * u)
                }
                drawPath(branch, tint, style = stroke)
            }
            Glyph.Sparkles -> {
                val spark1 = Path().apply {
                    moveTo(9f * u, 2.5f * u)
                    quadraticBezierTo(9f * u, 9.5f * u, 16f * u, 9.5f * u)
                    quadraticBezierTo(9f * u, 9.5f * u, 9f * u, 16.5f * u)
                    quadraticBezierTo(9f * u, 9.5f * u, 2f * u, 9.5f * u)
                    quadraticBezierTo(9f * u, 9.5f * u, 9f * u, 2.5f * u)
                    close()
                }
                drawPath(spark1, tint, style = stroke)
                val spark2 = Path().apply {
                    moveTo(17.5f * u, 2f * u)
                    quadraticBezierTo(17.5f * u, 5.5f * u, 21f * u, 5.5f * u)
                    quadraticBezierTo(17.5f * u, 5.5f * u, 17.5f * u, 9f * u)
                    quadraticBezierTo(17.5f * u, 5.5f * u, 14f * u, 5.5f * u)
                    quadraticBezierTo(17.5f * u, 5.5f * u, 17.5f * u, 2f * u)
                    close()
                }
                drawPath(spark2, tint, style = stroke)
                val spark3 = Path().apply {
                    moveTo(18f * u, 14f * u)
                    quadraticBezierTo(18f * u, 16.5f * u, 20.5f * u, 16.5f * u)
                    quadraticBezierTo(18f * u, 16.5f * u, 18f * u, 19f * u)
                    quadraticBezierTo(18f * u, 16.5f * u, 15.5f * u, 16.5f * u)
                    quadraticBezierTo(18f * u, 16.5f * u, 18f * u, 14f * u)
                    close()
                }
                drawPath(spark3, tint, style = stroke)
            }
            Glyph.CheckCircle -> {
                drawCircle(tint, radius = 9f * u, center = Offset(12f * u, 12f * u), style = stroke)
                shape(7.5f to 12f, 10.5f to 15f, 16.5f to 9f)
            }
            Glyph.Warning -> {
                shape(12f to 3.5f, 21.5f to 19.5f, 2.5f to 19.5f, close = true)
                line(12f, 9f, 12f, 13.5f)
                drawCircle(tint, radius = 0.9f * u, center = Offset(12f * u, 16.8f * u))
            }
            Glyph.Play -> {
                shape(8f to 5f, 19f to 12f, 8f to 19f, close = true)
            }
            Glyph.Stop -> {
                drawRoundRect(
                    tint, Offset(6f * u, 6f * u), Size(12f * u, 12f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * u), style = stroke,
                )
            }
            Glyph.FileCode -> {
                shape(7f to 3f, 14f to 3f, 19f to 8f, 19f to 21f, 7f to 21f, close = true)
                shape(14f to 3f, 14f to 8f, 19f to 8f)
                shape(11f to 12.5f, 9.5f to 14.5f, 11f to 16.5f)
                shape(15f to 12.5f, 16.5f to 14.5f, 15f to 16.5f)
            }
            Glyph.Lightning -> {
                shape(13f to 2.5f, 6.5f to 12.5f, 12f to 12.5f, 11f to 21.5f, 17.5f to 10.5f, 12f to 10.5f, close = true)
            }
            Glyph.Pin -> {
                line(8f, 4f, 16f, 4f)
                shape(9f to 4f, 9f to 10f, 6f to 13.5f, 18f to 13.5f, 15f to 10f, 15f to 4f, close = true)
                line(12f, 13.5f, 12f, 21f)
            }
            Glyph.Trash -> {
                line(4f, 6.5f, 20f, 6.5f)
                shape(9f to 6.5f, 9f to 3.5f, 15f to 3.5f, 15f to 6.5f)
                shape(6.5f to 6.5f, 7.5f to 20f, 16.5f to 20f, 17.5f to 6.5f)
                line(10f, 9.5f, 10.5f, 17f)
                line(14f, 9.5f, 13.5f, 17f)
            }
            Glyph.Dots -> {
                drawCircle(tint, radius = 1.6f * u, center = Offset(12f * u, 5.5f * u))
                drawCircle(tint, radius = 1.6f * u, center = Offset(12f * u, 12f * u))
                drawCircle(tint, radius = 1.6f * u, center = Offset(12f * u, 18.5f * u))
            }
            Glyph.Pulse -> {
                shape(2.5f to 12f, 7f to 12f, 9.5f to 6f, 13f to 18f, 15.5f to 10f, 17.5f to 13.5f, 19f to 12f, 21.5f to 12f)
            }
            Glyph.User -> {
                drawCircle(tint, radius = 3.8f * u, center = Offset(12f * u, 7.5f * u), style = stroke)
                shape(4.5f to 20.5f, 5.5f to 16f, 8.5f to 14.5f, 15.5f to 14.5f, 18.5f to 16f, 19.5f to 20.5f)
            }
            Glyph.Question -> {
                drawCircle(tint, radius = 9f * u, center = Offset(12f * u, 12f * u), style = stroke)
                shape(9.5f to 8.5f, 10.5f to 7f, 12f to 6.2f, 13.5f to 7f, 14.5f to 8.5f, 13.5f to 10.5f, 12f to 12f, 12f to 13.8f)
                drawCircle(tint, radius = 0.9f * u, center = Offset(12f * u, 16.5f * u))
            }
            Glyph.Square -> {
                drawRoundRect(
                    tint, Offset(5f * u, 5f * u), Size(14f * u, 14f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * u), style = stroke,
                )
            }
            Glyph.CheckSquare -> {
                drawRoundRect(
                    tint, Offset(5f * u, 5f * u), Size(14f * u, 14f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * u), style = stroke,
                )
                shape(8.5f to 12f, 11f to 15f, 15.5f to 9f)
            }
            // A list with dots: the sessions history, not a search.
            Glyph.History -> {
                drawCircle(tint, radius = 1.1f * u, center = Offset(5f * u, 7f * u))
                drawCircle(tint, radius = 1.1f * u, center = Offset(5f * u, 12f * u))
                drawCircle(tint, radius = 1.1f * u, center = Offset(5f * u, 17f * u))
                line(10f, 7f, 20f, 7f)
                line(10f, 12f, 20f, 12f)
                line(10f, 17f, 20f, 17f)
            }
        }
    }
}
