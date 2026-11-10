package dev.bidzz.opencode.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon

/** Visual representation details for a file type. */
data class FileTypeInfo(
    val language: String,
    val glyph: Glyph,
    val color: Color,
    val tag: String? = null,
)

object FileTypes {
    private val KotlinColor = Color(0xFF7F52FF)
    private val JavaColor = Color(0xFFE76F00)
    private val TypeScriptColor = Color(0xFF3178C6)
    private val JavaScriptColor = Color(0xFFE5A00D)
    private val PythonColor = Color(0xFF3776AB)
    private val RustColor = Color(0xFFDEA584)
    private val GoColor = Color(0xFF00ADD8)
    private val MarkdownColor = Color(0xFF0885FF)
    private val JsonColor = Color(0xFFCB9C00)
    private val YamlColor = Color(0xFFCB171E)
    private val ShellColor = Color(0xFF4EAA25)
    private val HtmlColor = Color(0xFFE44D26)
    private val CssColor = Color(0xFF264DE4)
    private val GitColor = Color(0xFFF05032)
    private val ImageColor = Color(0xFFB358F6)
    private val ConfigColor = Color(0xFF7A828E)

    fun resolve(path: String, isDirectory: Boolean = false): FileTypeInfo {
        if (isDirectory) {
            return FileTypeInfo(
                language = "Directory",
                glyph = Glyph.Folder,
                color = Color(0xFF4A90E2),
            )
        }

        val filename = path.substringAfterLast('/').lowercase()
        val extension = filename.substringAfterLast('.', "")

        return when {
            filename == ".gitignore" || filename == ".gitmodules" || filename == ".gitattributes" ->
                FileTypeInfo("Git", Glyph.Code, GitColor, "GIT")

            filename == "dockerfile" ->
                FileTypeInfo("Docker", Glyph.Terminal, Color(0xFF2496ED), "DOC")

            extension in listOf("kt", "kts") ->
                FileTypeInfo("Kotlin", Glyph.Code, KotlinColor, "KT")

            extension == "java" ->
                FileTypeInfo("Java", Glyph.Code, JavaColor, "JAVA")

            extension in listOf("ts", "tsx") ->
                FileTypeInfo("TypeScript", Glyph.Code, TypeScriptColor, "TS")

            extension in listOf("js", "jsx", "mjs", "cjs") ->
                FileTypeInfo("JavaScript", Glyph.Code, JavaScriptColor, "JS")

            extension in listOf("py", "pyw") ->
                FileTypeInfo("Python", Glyph.Code, PythonColor, "PY")

            extension == "rs" ->
                FileTypeInfo("Rust", Glyph.Code, RustColor, "RS")

            extension == "go" ->
                FileTypeInfo("Go", Glyph.Code, GoColor, "GO")

            extension in listOf("c", "h") ->
                FileTypeInfo("C", Glyph.Code, Color(0xFF555555), "C")

            extension in listOf("cpp", "cc", "cxx", "hpp") ->
                FileTypeInfo("C++", Glyph.Code, Color(0xFF00599C), "C++")

            extension in listOf("cs", "csharp") ->
                FileTypeInfo("C#", Glyph.Code, Color(0xFF178600), "C#")

            extension == "swift" ->
                FileTypeInfo("Swift", Glyph.Code, Color(0xFFF05138), "SW")

            extension in listOf("md", "markdown", "rst") ->
                FileTypeInfo("Markdown", Glyph.File, MarkdownColor, "MD")

            extension in listOf("json", "jsonc") ->
                FileTypeInfo("JSON", Glyph.Sliders, JsonColor, "{ }")

            extension in listOf("yaml", "yml") ->
                FileTypeInfo("YAML", Glyph.Sliders, YamlColor, "YML")

            extension == "toml" ->
                FileTypeInfo("TOML", Glyph.Sliders, YamlColor, "TOML")

            extension in listOf("xml", "svg") ->
                FileTypeInfo("XML", Glyph.Code, Color(0xFFE36209), "XML")

            extension in listOf("html", "htm") ->
                FileTypeInfo("HTML", Glyph.Code, HtmlColor, "HTML")

            extension in listOf("css", "scss", "sass", "less") ->
                FileTypeInfo("CSS", Glyph.Code, CssColor, "CSS")

            extension in listOf("sh", "bash", "zsh", "fish") ->
                FileTypeInfo("Shell", Glyph.Terminal, ShellColor, "SH")

            extension in listOf("png", "jpg", "jpeg", "webp", "gif", "ico") ->
                FileTypeInfo("Image", Glyph.Camera, ImageColor, "IMG")

            extension in listOf("gradle", "properties", "env", "conf", "ini") ->
                FileTypeInfo("Config", Glyph.Sliders, ConfigColor, "CFG")

            extension in listOf("txt", "log") ->
                FileTypeInfo("Text", Glyph.File, ConfigColor, "TXT")

            else ->
                FileTypeInfo("File", Glyph.File, ConfigColor)
        }
    }

    /** Maps a file path or extension to the syntax highlighter dialect name. */
    fun detectLanguage(path: String): String? {
        val ext = path.substringAfterLast('.', "").lowercase()
        return if (ext.isNotEmpty()) ext else null
    }
}

/**
 * A file type badge showing either an icon or a short language tag (e.g. "KT", "TS", "PY").
 */
@Composable
fun FileTypeBadge(
    info: FileTypeInfo,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val radius = OpenCodeTheme.tokens.radius.sm
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(info.color.copy(alpha = 0.14f))
            .border(0.5.dp, info.color.copy(alpha = 0.28f), RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) {
        if (info.tag != null && info.tag.length <= 4) {
            Text(
                text = info.tag,
                style = OpenCodeTypography.caption.copy(
                    fontFamily = OpenCodeTypography.mono,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = if (info.tag.length > 2) 9.sp else 10.sp,
                ),
                color = info.color,
                maxLines = 1,
            )
        } else {
            GlyphIcon(info.glyph, tint = info.color, size = size * 0.58f)
        }
    }
}
