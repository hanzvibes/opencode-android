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

/**
 * Visual representation details for a file type.
 *
 * [color] is the brand colour: badge tint/background and border only.
 * Tag text and the glyph never use it — the brand is 1.7-3.6:1 as text
 * on its own 14% tint (worst Rust 1.72:1 light) — they use [textColor]
 * in light and [darkTextColor] in dark, both >= 4.6:1 on the badge tint
 * (see FileTypeBadgeTest). The badge picks by theme; [color] stays the
 * single source for the tint so the brand identity is unchanged.
 */
data class FileTypeInfo(
    val language: String,
    val glyph: Glyph,
    val color: Color,
    val tag: String? = null,
    val textColor: Color,
    val darkTextColor: Color,
)

object FileTypes {
    private val DirectoryColor = Color(0xFF4A90E2)
    private val DirectoryTextColor = Color(0xFF33639C)
    private val DirectoryDarkTextColor = Color(0xFF5597E4)
    private val KotlinColor = Color(0xFF7F52FF)
    private val KotlinTextColor = Color(0xFF6843D1)
    private val KotlinDarkTextColor = Color(0xFF9B78FF)
    private val JavaColor = Color(0xFFE76F00)
    private val JavaTextColor = Color(0xFF9B4A00)
    private val JavaDarkTextColor = Color(0xFFE87308)
    private val TypeScriptColor = Color(0xFF3178C6)
    private val TypeScriptTextColor = Color(0xFF2861A0)
    private val TypeScriptDarkTextColor = Color(0xFF5C94D2)
    private val JavaScriptColor = Color(0xFFE5A00D)
    private val JavaScriptTextColor = Color(0xFF855D08)
    private val JavaScriptDarkTextColor = JavaScriptColor
    private val PythonColor = Color(0xFF3776AB)
    private val PythonTextColor = Color(0xFF2E6390)
    private val PythonDarkTextColor = Color(0xFF6596BE)
    private val RustColor = Color(0xFFDEA584)
    private val RustTextColor = Color(0xFF7F5E4B)
    private val RustDarkTextColor = RustColor
    private val GoColor = Color(0xFF00ADD8)
    private val GoTextColor = Color(0xFF006B86)
    private val GoDarkTextColor = GoColor
    private val MarkdownColor = Color(0xFF0885FF)
    private val MarkdownTextColor = Color(0xFF0660B8)
    private val MarkdownDarkTextColor = Color(0xFF2694FF)
    private val JsonColor = Color(0xFFCB9C00)
    private val JsonTextColor = Color(0xFF7C5F00)
    private val JsonDarkTextColor = JsonColor
    private val YamlColor = Color(0xFFCB171E)
    private val YamlTextColor = Color(0xFFB7151B)
    private val YamlDarkTextColor = Color(0xFFDD666B)
    private val ShellColor = Color(0xFF4EAA25)
    private val ShellTextColor = Color(0xFF337018)
    private val ShellDarkTextColor = ShellColor
    private val HtmlColor = Color(0xFFE44D26)
    private val HtmlTextColor = Color(0xFFAB3A1C)
    private val HtmlDarkTextColor = Color(0xFFE86949)
    private val CssColor = Color(0xFF264DE4)
    private val CssTextColor = Color(0xFF264CE2)
    private val CssDarkTextColor = Color(0xFF6B86ED)
    private val GitColor = Color(0xFFF05032)
    private val GitTextColor = Color(0xFFAA3923)
    private val GitDarkTextColor = Color(0xFFF26349)
    private val ImageColor = Color(0xFFB358F6)
    private val ImageTextColor = Color(0xFF8340B4)
    private val ImageDarkTextColor = Color(0xFFBD6EF7)
    private val ConfigColor = Color(0xFF7A828E)
    private val ConfigTextColor = Color(0xFF5C626A)
    private val ConfigDarkTextColor = Color(0xFF8D939E)
    private val DockerColor = Color(0xFF2496ED)
    private val DockerTextColor = Color(0xFF1866A1)
    private val DockerDarkTextColor = Color(0xFF2B99EE)
    private val CColor = Color(0xFF555555)
    private val CTextColor = CColor
    private val CDarkTextColor = Color(0xFF8D8D8D)
    private val CppColor = Color(0xFF00599C)
    private val CppTextColor = CppColor
    private val CppDarkTextColor = Color(0xFF5791BE)
    private val CSharpColor = Color(0xFF178600)
    private val CSharpTextColor = Color(0xFF136F00)
    private val CSharpDarkTextColor = Color(0xFF4CA23B)
    private val SwiftColor = Color(0xFFF05138)
    private val SwiftTextColor = Color(0xFFAA3A28)
    private val SwiftDarkTextColor = Color(0xFFF2644E)
    private val XmlColor = Color(0xFFE36209)
    private val XmlTextColor = Color(0xFFA14606)
    private val XmlDarkTextColor = Color(0xFFE67222)

    fun resolve(path: String, isDirectory: Boolean = false): FileTypeInfo {
        if (isDirectory) {
            return FileTypeInfo(
                language = "Directory",
                glyph = Glyph.Folder,
                color = DirectoryColor,
                textColor = DirectoryTextColor,
                darkTextColor = DirectoryDarkTextColor,
            )
        }

        val filename = path.substringAfterLast('/').lowercase()
        val extension = filename.substringAfterLast('.', "")

        return when {
            filename == ".gitignore" || filename == ".gitmodules" || filename == ".gitattributes" ->
                FileTypeInfo("Git", Glyph.Code, GitColor, "GIT", textColor = GitTextColor, darkTextColor = GitDarkTextColor)

            filename == "dockerfile" ->
                FileTypeInfo("Docker", Glyph.Terminal, DockerColor, "DOC", textColor = DockerTextColor, darkTextColor = DockerDarkTextColor)

            extension in listOf("kt", "kts") ->
                FileTypeInfo("Kotlin", Glyph.Code, KotlinColor, "KT", textColor = KotlinTextColor, darkTextColor = KotlinDarkTextColor)

            extension == "java" ->
                FileTypeInfo("Java", Glyph.Code, JavaColor, "JAVA", textColor = JavaTextColor, darkTextColor = JavaDarkTextColor)

            extension in listOf("ts", "tsx") ->
                FileTypeInfo("TypeScript", Glyph.Code, TypeScriptColor, "TS", textColor = TypeScriptTextColor, darkTextColor = TypeScriptDarkTextColor)

            extension in listOf("js", "jsx", "mjs", "cjs") ->
                FileTypeInfo("JavaScript", Glyph.Code, JavaScriptColor, "JS", textColor = JavaScriptTextColor, darkTextColor = JavaScriptDarkTextColor)

            extension in listOf("py", "pyw") ->
                FileTypeInfo("Python", Glyph.Code, PythonColor, "PY", textColor = PythonTextColor, darkTextColor = PythonDarkTextColor)

            extension == "rs" ->
                FileTypeInfo("Rust", Glyph.Code, RustColor, "RS", textColor = RustTextColor, darkTextColor = RustDarkTextColor)

            extension == "go" ->
                FileTypeInfo("Go", Glyph.Code, GoColor, "GO", textColor = GoTextColor, darkTextColor = GoDarkTextColor)

            extension in listOf("c", "h") ->
                FileTypeInfo("C", Glyph.Code, CColor, "C", textColor = CTextColor, darkTextColor = CDarkTextColor)

            extension in listOf("cpp", "cc", "cxx", "hpp") ->
                FileTypeInfo("C++", Glyph.Code, CppColor, "C++", textColor = CppTextColor, darkTextColor = CppDarkTextColor)

            extension in listOf("cs", "csharp") ->
                FileTypeInfo("C#", Glyph.Code, CSharpColor, "C#", textColor = CSharpTextColor, darkTextColor = CSharpDarkTextColor)

            extension == "swift" ->
                FileTypeInfo("Swift", Glyph.Code, SwiftColor, "SW", textColor = SwiftTextColor, darkTextColor = SwiftDarkTextColor)

            extension in listOf("md", "markdown", "rst") ->
                FileTypeInfo("Markdown", Glyph.File, MarkdownColor, "MD", textColor = MarkdownTextColor, darkTextColor = MarkdownDarkTextColor)

            extension in listOf("json", "jsonc") ->
                FileTypeInfo("JSON", Glyph.Sliders, JsonColor, "{ }", textColor = JsonTextColor, darkTextColor = JsonDarkTextColor)

            extension in listOf("yaml", "yml") ->
                FileTypeInfo("YAML", Glyph.Sliders, YamlColor, "YML", textColor = YamlTextColor, darkTextColor = YamlDarkTextColor)

            extension == "toml" ->
                FileTypeInfo("TOML", Glyph.Sliders, YamlColor, "TOML", textColor = YamlTextColor, darkTextColor = YamlDarkTextColor)

            extension in listOf("xml", "svg") ->
                FileTypeInfo("XML", Glyph.Code, XmlColor, "XML", textColor = XmlTextColor, darkTextColor = XmlDarkTextColor)

            extension in listOf("html", "htm") ->
                FileTypeInfo("HTML", Glyph.Code, HtmlColor, "HTML", textColor = HtmlTextColor, darkTextColor = HtmlDarkTextColor)

            extension in listOf("css", "scss", "sass", "less") ->
                FileTypeInfo("CSS", Glyph.Code, CssColor, "CSS", textColor = CssTextColor, darkTextColor = CssDarkTextColor)

            extension in listOf("sh", "bash", "zsh", "fish") ->
                FileTypeInfo("Shell", Glyph.Terminal, ShellColor, "SH", textColor = ShellTextColor, darkTextColor = ShellDarkTextColor)

            extension in listOf("png", "jpg", "jpeg", "webp", "gif", "ico") ->
                FileTypeInfo("Image", Glyph.Camera, ImageColor, "IMG", textColor = ImageTextColor, darkTextColor = ImageDarkTextColor)

            extension in listOf("gradle", "properties", "env", "conf", "ini") ->
                FileTypeInfo("Config", Glyph.Sliders, ConfigColor, "CFG", textColor = ConfigTextColor, darkTextColor = ConfigDarkTextColor)

            extension in listOf("txt", "log") ->
                FileTypeInfo("Text", Glyph.File, ConfigColor, "TXT", textColor = ConfigTextColor, darkTextColor = ConfigDarkTextColor)

            else ->
                FileTypeInfo("File", Glyph.File, ConfigColor, textColor = ConfigTextColor, darkTextColor = ConfigDarkTextColor)
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
    // Tag text and glyph use the AA-safe text variant for the current
    // theme, never the brand colour (see FileTypeInfo).
    val textColor =
        if (OpenCodeTheme.colors.isDark) info.darkTextColor else info.textColor
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
                    // 11sp is the labelSmall floor: the old 9/10sp tag was
                    // unreadable at badge sizes below the 48dp touch target.
                    fontSize = 11.sp,
                ),
                color = textColor,
                maxLines = 1,
            )
        } else {
            GlyphIcon(info.glyph, tint = textColor, size = size * 0.58f)
        }
    }
}
