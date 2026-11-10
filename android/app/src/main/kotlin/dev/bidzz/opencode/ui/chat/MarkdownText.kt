package dev.bidzz.opencode.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.core.design.Palette
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon

private data class HeadingStyle(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val fontWeight: FontWeight,
    val topPadding: Dp,
    val letterSpacing: TextUnit,
)

/**
 * Renders assistant output.
 *
 * Wrapped in [SelectionContainer] so any text can be selected and copied with the
 * platform gesture; the copy button on code blocks is an addition, not a
 * replacement for normal selection.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    /**
     * True while this is the last text block of a turn that is still
     * streaming. Shows a blinking cursor after the rendered blocks so the
     * reader can tell "still typing" from "finished" on a long answer.
     * The cursor sits outside the SelectionContainer, so it is never part
     * of a copy/selection and does not jump when the markdown re-parses.
     */
    streaming: Boolean = false,
) {
    val blocks = remember(markdown) { Markdown.parse(markdown) }
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                blocks.forEachIndexed { blockIndex, block ->
                    when (block) {
                        is MdBlock.Paragraph -> Text(
                            text = block.spans.toAnnotated(),
                            style = OpenCodeTypography.body,
                            color = colors.onSurface,
                        )

                        is MdBlock.Heading -> {
                            val baseTopPadding = if (blockIndex == 0) 0.dp else when (block.level) {
                                1 -> spacing.md
                                2 -> spacing.sm + spacing.xxs
                                3 -> spacing.sm
                                else -> spacing.xs
                            }
                            val headingStyle = when (block.level) {
                                1 -> HeadingStyle(20.sp, 26.sp, FontWeight.Bold, baseTopPadding, (-0.3).sp)
                                2 -> HeadingStyle(17.sp, 22.sp, FontWeight.Bold, baseTopPadding, (-0.25).sp)
                                3 -> HeadingStyle(15.sp, 20.sp, FontWeight.SemiBold, baseTopPadding, (-0.15).sp)
                                // H4–H6 used to be identical; each level now
                                // steps down so the hierarchy still reads.
                                4 -> HeadingStyle(14.sp, 18.sp, FontWeight.SemiBold, baseTopPadding, (-0.1).sp)
                                5 -> HeadingStyle(13.5.sp, 18.sp, FontWeight.SemiBold, baseTopPadding, (-0.1).sp)
                                else -> HeadingStyle(13.sp, 17.sp, FontWeight.Medium, baseTopPadding, (-0.1).sp)
                            }
                            Text(
                                text = block.spans.toAnnotated(),
                                style = OpenCodeTypography.body.copy(
                                    fontSize = headingStyle.fontSize,
                                    lineHeight = headingStyle.lineHeight,
                                    fontWeight = headingStyle.fontWeight,
                                    letterSpacing = headingStyle.letterSpacing,
                                ),
                                color = colors.onSurface,
                                modifier = Modifier.padding(top = headingStyle.topPadding, bottom = spacing.xxs),
                            )
                        }

                        is MdBlock.Blockquote -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topEnd = radius.sm, bottomEnd = radius.sm))
                                .background(colors.surfaceSunken)
                                .drawBehind {
                                    val strokeWidth = 3.dp.toPx()
                                    drawLine(
                                        color = colors.accent,
                                        start = Offset(strokeWidth / 2f, 0f),
                                        end = Offset(strokeWidth / 2f, size.height),
                                        strokeWidth = strokeWidth,
                                    )
                                }
                                .padding(
                                    start = spacing.md,
                                    end = spacing.md,
                                    top = spacing.sm,
                                    bottom = spacing.sm,
                                ),
                        ) {
                            Text(
                                text = block.spans.toAnnotated(),
                                style = OpenCodeTypography.body.copy(
                                    fontStyle = FontStyle.Italic,
                                    lineHeight = 22.sp,
                                ),
                                color = colors.onSurface,
                            )
                        }

                        is MdBlock.Bullet -> Column(
                            verticalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs),
                            modifier = Modifier.padding(start = spacing.xxs),
                        ) {
                            block.items.forEach { rawItem ->
                                val firstSpan = rawItem.firstOrNull()
                                val taskCheck = if (firstSpan is MdSpan.Text) {
                                    when {
                                        firstSpan.text.startsWith("[x] ", ignoreCase = true) -> true to firstSpan.text.substring(4)
                                        firstSpan.text.startsWith("[ ] ") -> false to firstSpan.text.substring(4)
                                        else -> null
                                    }
                                } else null

                                val (isTask, isChecked, item) = if (taskCheck != null) {
                                    val (checked, newFirst) = taskCheck
                                    val modifiedSpans = listOf(MdSpan.Text(newFirst)) + rawItem.drop(1)
                                    Triple(true, checked, modifiedSpans)
                                } else {
                                    Triple(false, false, rawItem)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    if (isTask) {
                                        Box(
                                            modifier = Modifier
                                                .width(20.dp)
                                                .padding(top = 2.dp),
                                            contentAlignment = Alignment.TopStart,
                                        ) {
                                            GlyphIcon(
                                                glyph = if (isChecked) Glyph.CheckSquare else Glyph.Square,
                                                tint = if (isChecked) colors.accent else colors.onSurfaceMuted,
                                                size = 15.dp,
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .width(16.dp)
                                                .padding(top = 8.dp),
                                            contentAlignment = Alignment.TopCenter,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.5.dp)
                                                    .background(colors.onSurfaceMuted.copy(alpha = 0.75f), CircleShape),
                                            )
                                        }
                                    }
                                    Text(
                                        text = item.toAnnotated(),
                                        style = OpenCodeTypography.body.copy(lineHeight = 22.sp),
                                        color = if (isTask && isChecked) colors.onSurfaceMuted else colors.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        is MdBlock.Numbered -> Column(
                            verticalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs),
                            modifier = Modifier.padding(start = spacing.xxs),
                        ) {
                            block.items.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = "${block.start + index}.",
                                        style = OpenCodeTypography.bodySmall.copy(
                                            fontFamily = OpenCodeTypography.mono,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp,
                                        ),
                                        color = colors.onSurfaceMuted,
                                        modifier = Modifier
                                            .widthIn(min = 20.dp)
                                            .padding(top = 1.dp, end = spacing.xs),
                                    )
                                    Text(
                                        text = item.toAnnotated(),
                                        style = OpenCodeTypography.body.copy(lineHeight = 22.sp),
                                        color = colors.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        is MdBlock.Rule -> DividerBlock()

                        is MdBlock.Code -> CodeBlock(
                            language = block.language,
                            code = block.code,
                            streaming = streaming && blockIndex == blocks.lastIndex,
                        )

                        is MdBlock.Table -> TableBlock(block)
                    }
                }
            }
        }
        // The lone cursor below the blocks is only shown when the last block
        // is not a code window: while streaming inside a code block the
        // cursor lives inside the window instead (see CodeBlock), so it
        // never reads as a stray mark under a finished block.
        if (streaming && blocks.lastOrNull() !is MdBlock.Code) {
            Row(
                modifier = Modifier.padding(top = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BlinkingCursor(
                    color = colors.accent,
                    height = 15.dp,
                )
            }
        }
    }
}

/**
 * A fenced code block: sunken surface, editor window title bar with file icon/language badge,
 * action pills, line numbers support, and soft-contrast modern syntax highlighting.
 *
 * Designed to look like Claude Code / Antigravity editor windows with 16dp rounded corners
 * and inset hairline borders.
 */
@Composable
private fun CodeBlock(language: String?, code: String, streaming: Boolean = false) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val context = LocalContext.current
    var copied by remember(code) { mutableStateOf(false) }

    // "Copied" is ephemeral feedback: it resets after 1.5s so the next
    // copy shows the feedback again instead of staying green forever.
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    val defaultCodeLabel = stringResource(R.string.code_label)
    val displayLanguage = remember(language, defaultCodeLabel) {
        if (language.isNullOrBlank()) {
            defaultCodeLabel.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else {
            formatLanguageName(language)
        }
    }

    val lines = remember(code) { code.lines() }
    val lineCount = lines.size
    val showLineNumbers = lineCount > 1
    // Locked to at least 3 digits so the gutter never shifts width when
    // the count crosses 9→10 or 99→100 mid-stream.
    val digits = remember(lineCount) { lineCount.toString().length.coerceAtLeast(3) }
    val lineNumbersText = remember(lineCount, digits) {
        (1..lineCount).joinToString("\n") { it.toString().padStart(digits, ' ') }
    }

    val codeStyle = OpenCodeTypography.code.copy(
        fontSize = 12.5.sp,
        lineHeight = 19.sp,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.lg))
            .background(colors.surfaceSunken)
            .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.lg)),
    ) {
        // Window title bar with file icon/language badge and action pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant.copy(alpha = 0.45f))
                .padding(horizontal = spacing.sm + 2.dp, vertical = spacing.xs + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // File icon / Language badge pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs + 1.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(radius.full))
                    .background(colors.fill)
                    .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.full))
                    .padding(horizontal = spacing.sm, vertical = spacing.xxs + 1.dp),
            ) {
                GlyphIcon(
                    glyph = if (isShell(language)) Glyph.Terminal else Glyph.File,
                    tint = colors.onSurfaceMuted,
                    size = 12.dp,
                )
                Text(
                    text = displayLanguage,
                    style = OpenCodeTypography.caption.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.15.sp,
                    ),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
            }

            // A block past the highlight cutoff renders plain on purpose;
            // say so, so it does not read as a broken highlighter.
            if (code.length > MAX_HIGHLIGHT_CHARS) {
                Text(
                    text = stringResource(R.string.code_highlight_off),
                    style = OpenCodeTypography.caption.copy(fontSize = 10.sp),
                    color = colors.onSurfaceFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = spacing.xs),
                )
            }

            Spacer(Modifier.weight(1f))

            // No Copy pill while streaming: the code is still arriving, so
            // copying here would hand out a partial snippet.
            if (!streaming) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BlockAction(
                        label = stringResource(if (copied) R.string.code_copied else R.string.code_copy),
                        glyph = if (copied) Glyph.Check else Glyph.Copy,
                        done = copied,
                    ) {
                        copyToClipboard(context, code)
                        copied = true
                    }
                }
            }
        }

        // Hairline divider separating header bar from code area
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.hairline),
        )

        // Monospace code with line numbers and horizontal scroll.
        // Long blocks are capped in height (360dp, 420dp on wide/tablet
        // screens) and scroll vertically instead of pushing the rest of the
        // answer off-screen.
        val isWide = LocalConfiguration.current.screenWidthDp >= 600
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (isWide) 420.dp else 360.dp)
                .verticalScroll(rememberScrollState()),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.xs),
        ) {
            if (showLineNumbers) {
                DisableSelection {
                    Text(
                        text = lineNumbersText,
                        style = codeStyle.copy(
                            color = colors.onSurfaceFaint.copy(alpha = 0.45f),
                        ),
                        softWrap = false,
                        modifier = Modifier.padding(
                            start = spacing.md,
                            end = spacing.sm,
                            top = spacing.sm,
                            bottom = spacing.sm,
                        ),
                    )
                }
                // Hairline between the gutter and the code, so the numbers
                // read as a column instead of text floating beside the code.
                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .fillMaxHeight()
                        .background(colors.hairline),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
            ) {
                Column {
                    Text(
                        text = highlightedCode(language, code, streaming),
                        style = codeStyle,
                        softWrap = false,
                        modifier = Modifier.padding(
                            start = if (showLineNumbers) spacing.xs else spacing.lg,
                            end = spacing.lg,
                            top = spacing.sm,
                            bottom = if (streaming) 0.dp else spacing.sm,
                        ),
                    )
                    // While streaming, the cursor sits inside the window on
                    // its own line below the last code line, bottom-aligned
                    // with the code area — like a terminal prompt waiting
                    // for the next line — instead of under the window.
                    if (streaming) {
                        Row(
                            modifier = Modifier.padding(
                                start = if (showLineNumbers) spacing.xs else spacing.lg,
                                bottom = spacing.sm,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BlinkingCursor(
                                color = colors.accent,
                                height = 12.dp,
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

private fun isShell(language: String?): Boolean = when (language?.trim()?.substringBefore(' ')?.substringBefore(':')?.lowercase()) {
    "sh", "bash", "zsh", "shell", "console", "terminal" -> true
    else -> false
}

private fun formatLanguageName(language: String?): String {
    // Only the first token is the language: fences like
    // ```kotlin:Main.kt or ```python /app/main.py carry a file/path
    // suffix that must never be shown as the language name.
    val raw = language?.trim()?.substringBefore(' ')?.substringBefore(':')?.lowercase()
        ?: return "Code"
    return when (raw) {
        "kt", "kotlin" -> "Kotlin"
        "py", "python" -> "Python"
        "js", "javascript" -> "JavaScript"
        "ts", "typescript" -> "TypeScript"
        "tsx" -> "TSX"
        "jsx" -> "JSX"
        "rs", "rust" -> "Rust"
        "go", "golang" -> "Go"
        "java" -> "Java"
        "c" -> "C"
        "cpp", "cc", "cxx" -> "C++"
        "cs", "csharp" -> "C#"
        "swift" -> "Swift"
        "ruby", "rb" -> "Ruby"
        "php" -> "PHP"
        "html", "htm" -> "HTML"
        "css" -> "CSS"
        "scss" -> "SCSS"
        "json", "jsonc" -> "JSON"
        "yaml", "yml" -> "YAML"
        "toml" -> "TOML"
        "xml" -> "XML"
        "sql" -> "SQL"
        "sh", "bash", "zsh", "shell" -> "Bash"
        "dockerfile", "docker" -> "Dockerfile"
        "md", "markdown" -> "Markdown"
        "diff" -> "Diff"
        "" -> "Code"
        else -> raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

/**
 * A code block as coloured text with modern dark theme syntax highlighting.
 *
 * Uses soft contrast palette designed for extended reading without eye strain.
 * Only the colour changes: the underlying characters are the source verbatim, so the
 * copy button and text selection still hand out exactly what the model emitted.
 */
@Composable
private fun highlightedCode(language: String?, code: String, streaming: Boolean = false): AnnotatedString {
    val colors = OpenCodeTheme.colors
    val isDark = colors.background == Palette.darkBackground || colors.surfaceSunken == Palette.darkSurfaceSunken
    // While streaming, long blocks stay plain until the stream finishes
    // (see highlight()); the full highlight runs once, on completion.
    val tokens = remember(code, language, streaming) { highlight(language, code, streaming) }

    val plainColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
    val keywordColor = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED)
    val stringColor = if (isDark) Color(0xFF86EFAC) else Color(0xFF15803D)
    val numberColor = if (isDark) Color(0xFFFDBA74) else Color(0xFFC2410C)
    val commentColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    return remember(tokens, plainColor, keywordColor, stringColor, numberColor, commentColor) {
        buildAnnotatedString {
            tokens.forEach { token ->
                withStyle(
                    SpanStyle(
                        color = when (token.kind) {
                            TokenKind.Keyword -> keywordColor
                            TokenKind.String -> stringColor
                            TokenKind.Number -> numberColor
                            TokenKind.Comment -> commentColor
                            TokenKind.Plain -> plainColor
                        },
                        fontWeight = when (token.kind) {
                            TokenKind.Keyword -> FontWeight.Medium
                            else -> FontWeight.Normal
                        },
                        fontStyle = if (token.kind == TokenKind.Comment) FontStyle.Italic else FontStyle.Normal,
                    ),
                ) {
                    append(token.text)
                }
            }
        }
    }
}

/**
 * A pipe table.
 *
 * Polished layout with clean outer border, header caps, alternating row background,
 * vertical & horizontal hairline dividers, bold keys, and responsive horizontal scrolling.
 */
@Composable
private fun TableBlock(table: MdBlock.Table) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions

    val colCount = table.columns.coerceAtLeast(1)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val availableWidth = if (maxWidth != Dp.Infinity && maxWidth > 0.dp) maxWidth else 360.dp

        val colWidths: List<Dp> = when (colCount) {
            1 -> listOf(availableWidth)
            2 -> {
                val minTableWidth = 280.dp
                val tableWidth = maxOf(availableWidth, minTableWidth)
                val col0Width = maxOf(110.dp, tableWidth * 0.36f)
                val col1Width = tableWidth - col0Width
                listOf(col0Width, col1Width)
            }
            else -> {
                val minColWidth = 120.dp
                val minTableWidth = minColWidth * colCount
                val tableWidth = maxOf(availableWidth, minTableWidth)
                val colWidth = tableWidth / colCount
                List(colCount) { colWidth }
            }
        }
        val totalTableWidth = colWidths.fold(0.dp) { acc, dp -> acc + dp }
        val horizontalScrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(radius.md))
                .background(colors.surfaceSunken)
                .border(0.5.dp, colors.hairline.copy(alpha = 0.5f), RoundedCornerShape(radius.md))
                .horizontalScroll(horizontalScrollState),
        ) {
            Column(modifier = Modifier.width(totalTableWidth)) {
                // Header row with header caps and distinct background
                if (table.header.isNotEmpty() && table.header.any { it.isNotEmpty() }) {
                    Row(
                        modifier = Modifier
                            .width(totalTableWidth)
                            .background(colors.surfaceVariant.copy(alpha = 0.55f)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        table.header.forEachIndexed { colIdx, cell ->
                            val cellWidth = colWidths.getOrElse(colIdx) { 120.dp }
                            Box(
                                modifier = Modifier
                                    .width(cellWidth)
                                    .padding(horizontal = spacing.md, vertical = 10.dp),
                            ) {
                                Text(
                                    text = cell.toUppercase().toAnnotated(),
                                    style = OpenCodeTypography.label.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.5.sp,
                                    ),
                                    color = colors.onSurfaceMuted,
                                )
                            }
                        }
                    }
                    Box(
                        Modifier
                            .width(totalTableWidth)
                            .height(0.5.dp)
                            .background(colors.hairline.copy(alpha = 0.6f)),
                    )
                }

                // Data rows with bold keys, alternating background, and clean hairline borders
                table.rows.forEachIndexed { rowIdx, row ->
                    if (rowIdx > 0) {
                        Box(
                            Modifier
                                .width(totalTableWidth)
                                .height(0.5.dp)
                                .background(colors.hairline.copy(alpha = 0.35f)),
                        )
                    }
                    val rowBg = if (rowIdx % 2 == 1) colors.fill.copy(alpha = 0.25f) else Color.Transparent
                    Row(
                        modifier = Modifier
                            .width(totalTableWidth)
                            .background(rowBg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.forEachIndexed { colIdx, cell ->
                            val isKeyCol = colIdx == 0
                            val cellWidth = colWidths.getOrElse(colIdx) { 120.dp }
                            Box(
                                modifier = Modifier
                                    .width(cellWidth)
                                    .padding(horizontal = spacing.md, vertical = 10.dp),
                            ) {
                                Text(
                                    text = cell.toAnnotated(),
                                    style = if (isKeyCol) {
                                        OpenCodeTypography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                    } else {
                                        OpenCodeTypography.bodySmall
                                    },
                                    color = colors.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DividerBlock() {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dims.hairline)
                .background(colors.hairline),
        )
    }
}

@Composable
private fun BlockAction(
    label: String,
    glyph: Glyph? = null,
    done: Boolean,
    onClick: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    val pillBg = if (done) colors.success.copy(alpha = 0.12f) else colors.fill.copy(alpha = 0.6f)
    val pillBorder = if (done) colors.success.copy(alpha = 0.35f) else colors.hairline
    val tint = if (done) colors.success else colors.onSurfaceMuted

    Box(
        modifier = Modifier
            .sizeIn(minHeight = 32.dp)
            .clip(RoundedCornerShape(radius.full))
            .background(pillBg)
            .border(0.5.dp, pillBorder, RoundedCornerShape(radius.full))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            if (glyph != null) {
                GlyphIcon(glyph = glyph, tint = tint, size = 11.dp)
            }
            Text(
                text = label,
                style = OpenCodeTypography.caption.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.1.sp,
                ),
                color = tint,
            )
        }
    }
}

/** Writes [text] to a document the user chose; false when it could not be written. */
private fun writeText(context: Context, uri: android.net.Uri, text: String): Boolean = try {
    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
} catch (_: java.io.IOException) {
    false
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("code", text))
}

private fun List<MdSpan>.toUppercase(): List<MdSpan> = map { span ->
    when (span) {
        is MdSpan.Text -> MdSpan.Text(span.text.uppercase())
        is MdSpan.Bold -> MdSpan.Bold(span.text.uppercase())
        is MdSpan.Italic -> MdSpan.Italic(span.text.uppercase())
        // Code stays verbatim: uppercasing it turns `config.yaml` into
        // `CONFIG.YAML`, which is a different file.
        is MdSpan.Code -> span
        is MdSpan.Link -> MdSpan.Link(span.text.uppercase(), span.url)
        is MdSpan.Highlight -> MdSpan.Highlight(span.text.uppercase())
    }
}

/** Converts inline spans to an [AnnotatedString]. */
@Composable
private fun List<MdSpan>.toAnnotated(): AnnotatedString {
    val colors = OpenCodeTheme.colors

    return buildAnnotatedString {
        this@toAnnotated.forEach { span ->
            when (span) {
                is MdSpan.Text -> append(span.text)
                is MdSpan.Bold -> withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    ),
                ) {
                    append(span.text)
                }
                is MdSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(span.text)
                }
                is MdSpan.Code -> withStyle(
                    SpanStyle(
                        fontFamily = OpenCodeTypography.mono,
                        // Inline code sits at 12.5sp like block code; the
                        // shared codeInline token is 13.5sp, larger than
                        // the block it is supposed to be smaller than.
                        // The global token is untouched — only prose here.
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        background = colors.surfaceVariant.copy(alpha = 0.8f),
                        color = colors.onSurface,
                        baselineShift = BaselineShift(0.02f),
                    ),
                ) {
                    append('\u00A0')
                    append(span.text)
                    append('\u00A0')
                }
                is MdSpan.Highlight -> withStyle(
                    SpanStyle(
                        background = colors.warning.copy(alpha = 0.22f),
                        color = colors.onSurface,
                        fontWeight = FontWeight.Medium,
                    ),
                ) {
                    append(span.text)
                }
                // withLink makes the span a real tappable link (opened via
                // the ambient UriHandler); styling alone used to render links
                // that did nothing when tapped.
                is MdSpan.Link -> withLink(LinkAnnotation.Url(span.url)) {
                    withStyle(
                        SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline),
                    ) {
                        append(span.text)
                    }
                }
            }
        }
    }
}
