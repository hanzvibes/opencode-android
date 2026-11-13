package dev.bidzz.opencode.ui.chat

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.delay
import java.util.Locale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeColors
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.copyPlainText

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
    /**
     * Text colour for prose and inline spans. Null renders on a plain
     * surface ([colors.onSurface]); a caller that renders markdown inside
     * the neutral user bubble passes its own on-bubble colour, so
     * bold/code/links stay readable on the bubble instead of defaulting
     * to dark-on-bubble.
     */
    textColor: Color? = null,
    /**
     * Saves a code block through the system picker (Fase 1, item 4):
     * the suggested filename, then the code verbatim. The launcher lives
     * in MainActivity (a Compose launcher here crashed); the default
     * no-op keeps previews working without a host.
     */
    onSaveCode: (String, String) -> Unit = { _, _ -> },
) {
    val blocks = remember(markdown, streaming) { Markdown.parse(markdown, streaming) }
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val proseColor = textColor ?: colors.onSurface
    val onBubble = textColor != null

    // A tap on a link goes through the ambient UriHandler; the default one
    // throws ActivityNotFoundException when no app can open the URI, and
    // the model can write any URL (item 2). Guard it once here, so no link
    // in assistant output can kill the app.
    val ambientUriHandler = LocalUriHandler.current
    val safeUriHandler = remember(ambientUriHandler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                runCatching { ambientUriHandler.openUri(uri) }
            }
        }
    }

    CompositionLocalProvider(LocalUriHandler provides safeUriHandler) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                blocks.forEachIndexed { blockIndex, block ->
                    when (block) {
                        is MdBlock.Paragraph -> Text(
                            text = block.spans.toAnnotated(proseColor, onBubble),
                            style = OpenCodeTypography.body,
                            color = proseColor,
                        )

                        is MdBlock.Heading -> {
                            val baseTopPadding = if (blockIndex == 0) 0.dp else when (block.level) {
                                1 -> spacing.md
                                2 -> spacing.sm + spacing.xxs
                                3 -> spacing.sm
                                else -> spacing.xs
                            }
                            val headingStyle = when (block.level) {
                                1 -> OpenCodeTypography.heading1
                                2 -> OpenCodeTypography.heading2
                                3 -> OpenCodeTypography.heading3
                                4 -> OpenCodeTypography.heading4
                                5 -> OpenCodeTypography.heading5
                                else -> OpenCodeTypography.heading6
                            }
                            Text(
                                text = block.spans.toAnnotated(proseColor, onBubble),
                                style = headingStyle,
                                color = proseColor,
                                modifier = Modifier.padding(top = baseTopPadding, bottom = spacing.xxs),
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
                                text = block.spans.toAnnotated(proseColor, onBubble),
                                style = OpenCodeTypography.body.copy(
                                    fontStyle = FontStyle.Italic,
                                    lineHeight = 22.sp,
                                ),
                                color = proseColor,
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
                                        text = item.toAnnotated(proseColor, onBubble),
                                        style = OpenCodeTypography.body.copy(lineHeight = 22.sp),
                                        color = if (isTask && isChecked) colors.onSurfaceMuted else proseColor,
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
                                        text = item.toAnnotated(proseColor, onBubble),
                                        style = OpenCodeTypography.body.copy(lineHeight = 22.sp),
                                        color = proseColor,
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
                            onSaveCode = onSaveCode,
                        )

                        is MdBlock.Table -> TableBlock(block, proseColor, onBubble)
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
}

/** Code blocks longer than this many lines start collapsed (B-1). */
private const val CODE_COLLAPSE_THRESHOLD = 30

/**
 * A fenced code block: sunken surface, editor window title bar with file icon/language badge,
 * action pills, line numbers support, and soft-contrast modern syntax highlighting.
 *
 * Designed to look like Claude Code / Antigravity editor windows with 16dp rounded corners
 * and inset hairline borders.
 */
@Composable
private fun CodeBlock(
    language: String?,
    code: String,
    streaming: Boolean = false,
    /** Saves the block through the system picker (Fase 1, item 4). */
    onSaveCode: (String, String) -> Unit = { _, _ -> },
) {
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
    // Long blocks (>30 lines) start collapsed and can be expanded. The
    // choice is plain remember — deliberately not keyed on `code`, so a
    // block that grows past 30 lines while streaming collapses on its own
    // and the user's expand/collapse tap is not reset by the next token.
    // The 360dp height cap (A-8) still applies on top of this.
    val isLongBlock = lineCount > CODE_COLLAPSE_THRESHOLD
    var collapsedOverride by remember { mutableStateOf<Boolean?>(null) }
    val collapsed = collapsedOverride ?: isLongBlock
    // The expand arrow is one glyph rotating 0°↔180° (down = expand,
    // up = collapse) instead of two glyphs swapping on every tap.
    val expandRotation by animateFloatAsState(
        targetValue = if (collapsed) 0f else 180f,
        animationSpec = OpenCodeTheme.tokens.motion.tweenQuick(),
        label = "code_expand_rotation",
    )
    val visibleCode = if (collapsed) {
        lines.take(CODE_COLLAPSE_THRESHOLD).joinToString("\n")
    } else {
        code
    }
    val visibleLineCount = if (collapsed) CODE_COLLAPSE_THRESHOLD else lineCount
    val showLineNumbers = visibleLineCount > 1
    // Locked to at least 3 digits so the gutter never shifts width when
    // the count crosses 9→10 or 99→100 mid-stream.
    val digits = remember(visibleLineCount) { visibleLineCount.toString().length.coerceAtLeast(3) }
    val lineNumbersText = remember(visibleLineCount, digits) {
        (1..visibleLineCount).joinToString("\n") { it.toString().padStart(digits, ' ') }
    }

    // Opsi A: a touch roomier for reading — 13sp on a 20sp line (was
    // 12.5sp / 19sp), so dense Kotlin like the mockup's ChatScreen.kt
    // diff breathes without the block growing a line count.
    val codeStyle = OpenCodeTypography.code.copy(
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = OpenCodeTheme.tokens.motion.springSmooth())
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

            // The expand/collapse pill is always available for long blocks,
            // even while streaming: collapsing a still-growing block shows
            // its first 30 lines and keeps new lines out of the way.
            // No Copy pill while streaming: the code is still arriving, so
            // copying here would hand out a partial snippet. Copy always
            // hands out the full block, collapsed or not.
            if (!streaming || isLongBlock) {
                val expandLabel = if (collapsed) {
                    stringResource(if (lineCount == 1) R.string.code_show_more_one else R.string.code_show_more, lineCount)
                } else {
                    stringResource(R.string.tool_show_less)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isLongBlock) {
                        BlockAction(
                            label = expandLabel,
                            glyph = Glyph.ArrowDown,
                            glyphRotation = expandRotation,
                            done = false,
                            onClick = { collapsedOverride = !collapsed },
                        )
                    }
                    if (!streaming) {
                        BlockAction(
                            label = stringResource(if (copied) R.string.code_copied else R.string.code_copy),
                            glyph = if (copied) Glyph.Check else Glyph.Copy,
                            done = copied,
                            onClick = {
                                // 65-F4: the check only flips on a real copy.
                                if (copyToClipboard(context, code)) copied = true
                            },
                        )
                        // Save (Fase 1, item 4): the whole block, verbatim,
                        // through the system picker — the suggested name
                        // comes from the fence language. No Run button:
                        // there is no verified, gated execution path for
                        // arbitrary model-written code (see CodeBlockSave).
                        BlockAction(
                            label = stringResource(R.string.code_save),
                            glyph = null,
                            done = false,
                            onClick = { onSaveCode(codeBlockFileName(language), code) },
                        )
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
                        text = highlightedCode(language, visibleCode, streaming),
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
    // While streaming, long blocks stay plain until the stream finishes
    // (see highlight()); the full highlight runs once, on completion.
    val tokens = remember(code, language, streaming) { highlight(language, code, streaming) }

    // Colours come from the design tokens, never a local palette: the old
    // slate/violet copy drifted from Theme.kt and needed its own theme
    // check (comparing colour values). There is no theme branch left here,
    // so no `colors.isDark` check is needed either.
    return remember(tokens, colors) {
        buildAnnotatedString {
            tokens.forEach { token ->
                withStyle(
                    SpanStyle(
                        color = when (token.kind) {
                            TokenKind.Keyword -> colors.syntaxKeyword
                            TokenKind.String -> colors.syntaxString
                            TokenKind.Number -> colors.syntaxNumber
                            TokenKind.Comment -> colors.syntaxComment
                            TokenKind.Function -> colors.syntaxFunction
                            TokenKind.Type -> colors.syntaxType
                            TokenKind.Operator -> colors.onSurfaceMuted
                            TokenKind.Plain -> colors.onSurface
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
 * vertical & horizontal hairline dividers, and responsive horizontal scrolling.
 * The first column is bold only for two-column (key–value) tables; with more
 * columns the first column is data, not a key. Column alignment from the
 * separator row (`:---:` centre, `---:` right) is applied to header and body.
 */
@Composable
private fun TableBlock(
    table: MdBlock.Table,
    proseColor: Color = OpenCodeTheme.colors.onSurface,
    onBubble: Boolean = false,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions

    val colCount = table.columns.coerceAtLeast(1)
    val isKeyValueTable = colCount == 2
    fun columnAlign(colIdx: Int): TextAlign = when (
        table.alignments.getOrNull(colIdx) ?: TableAlign.LEFT
    ) {
        TableAlign.CENTER -> TextAlign.Center
        TableAlign.RIGHT -> TextAlign.Right
        TableAlign.LEFT -> TextAlign.Start
    }

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
                                    text = cell.toUppercase().toAnnotated(proseColor, onBubble),
                                    style = OpenCodeTypography.label.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.5.sp,
                                    ),
                                    color = if (onBubble) {
                                        proseColor.copy(alpha = 0.75f)
                                    } else {
                                        colors.onSurfaceMuted
                                    },
                                    textAlign = columnAlign(colIdx),
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
                            // Bold first column only for key–value tables.
                            val isKeyCol = isKeyValueTable && colIdx == 0
                            val cellWidth = colWidths.getOrElse(colIdx) { 120.dp }
                            Box(
                                modifier = Modifier
                                    .width(cellWidth)
                                    .padding(horizontal = spacing.md, vertical = 10.dp),
                            ) {
                                Text(
                                    text = cell.toAnnotated(proseColor, onBubble),
                                    style = if (isKeyCol) {
                                        OpenCodeTypography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                    } else {
                                        OpenCodeTypography.bodySmall
                                    },
                                    color = proseColor,
                                    textAlign = columnAlign(colIdx),
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
    /** Rotation applied to [glyph] (0°↔180° for the expand arrow). */
    glyphRotation: Float = 0f,
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
                GlyphIcon(
                    glyph = glyph,
                    tint = tint,
                    size = 11.dp,
                    modifier = Modifier.graphicsLayer { rotationZ = glyphRotation },
                )
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

private fun copyToClipboard(context: Context, text: String): Boolean =
    copyPlainText(context, "code", text)

/**
 * Uppercases prose spans for table headers. Internal (not private) so the
 * locale rule is unit-testable (04-F11: `uppercase` without a locale turns
 * "i" into "İ" under tr-TR).
 */
internal fun List<MdSpan>.toUppercase(): List<MdSpan> = map { span ->
    when (span) {
        is MdSpan.Text -> MdSpan.Text(span.text.uppercase(Locale.ROOT))
        is MdSpan.Bold -> MdSpan.Bold(span.spans.toUppercase())
        is MdSpan.Italic -> MdSpan.Italic(span.spans.toUppercase())
        // Code stays verbatim: uppercasing it turns `config.yaml` into
        // `CONFIG.YAML`, which is a different file.
        is MdSpan.Code -> span
        is MdSpan.Link -> MdSpan.Link(span.spans.toUppercase(), span.url)
        is MdSpan.Highlight -> MdSpan.Highlight(span.spans.toUppercase())
        is MdSpan.Strikethrough -> MdSpan.Strikethrough(span.spans.toUppercase())
    }
}

/**
 * Converts inline spans to an [AnnotatedString].
 *
 * [textColor] is the prose colour (surface text by default, the on-bubble
 * colour inside the neutral user bubble). On a bubble the code/highlight
 * backgrounds are surface tones, not translucent white — the bubble is
 * neutral now, so white-on-blue affordances would wash out — and links
 * keep the prose colour + underline.
 */
@Composable
private fun List<MdSpan>.toAnnotated(textColor: Color, onBubble: Boolean = false): AnnotatedString {
    val colors = OpenCodeTheme.colors

    return buildAnnotatedString {
        appendSpans(this@toAnnotated, textColor, onBubble, colors)
    }
}

/**
 * Appends [spans] with their styles. Formatting spans recurse into their
 * children (04-F2), so a link inside bold stays a link and code inside
 * bold stays monospace — the outer style and the inner span compose.
 */
private fun AnnotatedString.Builder.appendSpans(
    spans: List<MdSpan>,
    textColor: Color,
    onBubble: Boolean,
    colors: OpenCodeColors,
) {
    spans.forEach { span ->
        when (span) {
            is MdSpan.Text -> append(span.text)
            is MdSpan.Bold -> withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                ),
            ) {
                appendSpans(span.spans, textColor, onBubble, colors)
            }
            is MdSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendSpans(span.spans, textColor, onBubble, colors)
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
                    // Surface tones on the neutral bubble too: the old
                    // translucent white (0.18) was a white-on-blue
                    // leftover and is near-invisible on surfaceRaised.
                    background = colors.surfaceVariant.copy(alpha = 0.8f),
                    color = textColor,
                    baselineShift = BaselineShift(0.02f),
                ),
            ) {
                // 04-F6: no NBSP padding around the code text any more —
                // the U+00A0s used to pad the chip visually, but they
                // landed in the AnnotatedString and were copied with every
                // selection (" config.yaml " pasted as a wrong filename).
                append(span.text)
            }
            is MdSpan.Highlight -> withStyle(
                SpanStyle(
                    // Same white-on-blue leftover as inline code: a
                    // translucent white wash is invisible on the
                    // neutral bubble, so both themes use the warning tint.
                    background = colors.warning.copy(alpha = 0.22f),
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                ),
            ) {
                appendSpans(span.spans, textColor, onBubble, colors)
            }
            is MdSpan.Strikethrough -> withStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough),
            ) {
                appendSpans(span.spans, textColor, onBubble, colors)
            }
            // withLink makes the span a real tappable link (opened via
            // the ambient UriHandler); styling alone used to render links
            // that did nothing when tapped.
            is MdSpan.Link -> withLink(LinkAnnotation.Url(span.url)) {
                withStyle(
                    SpanStyle(
                        color = if (onBubble) textColor else colors.accentText,
                        textDecoration = TextDecoration.Underline,
                    ),
                ) {
                    appendSpans(span.spans, textColor, onBubble, colors)
                }
            }
        }
    }
}
