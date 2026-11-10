package dev.bidzz.opencode.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon

private data class HeadingStyle(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val fontWeight: FontWeight,
    val topPadding: Dp,
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
) {
    val blocks = remember(markdown) { Markdown.parse(markdown) }
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                blocks.forEach { block ->
                    when (block) {
                        is MdBlock.Paragraph -> Text(
                            text = block.spans.toAnnotated(),
                            style = OpenCodeTypography.body,
                            color = colors.onSurface,
                        )

                        is MdBlock.Heading -> {
                            val headingStyle = when (block.level) {
                                1 -> HeadingStyle(20.sp, 26.sp, FontWeight.Bold, spacing.sm)
                                2 -> HeadingStyle(17.sp, 22.sp, FontWeight.SemiBold, spacing.xs + spacing.xxs)
                                3 -> HeadingStyle(15.sp, 20.sp, FontWeight.SemiBold, spacing.xs)
                                else -> HeadingStyle(14.sp, 18.sp, FontWeight.SemiBold, spacing.xxs)
                            }
                            Text(
                                text = block.spans.toAnnotated(),
                                style = OpenCodeTypography.body.copy(
                                    fontSize = headingStyle.fontSize,
                                    lineHeight = headingStyle.lineHeight,
                                    fontWeight = headingStyle.fontWeight,
                                    letterSpacing = if (block.level <= 2) (-0.3).sp else (-0.15).sp,
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

                        is MdBlock.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            block.items.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = "•",
                                        style = OpenCodeTypography.body.copy(fontWeight = FontWeight.Bold),
                                        color = colors.onSurfaceMuted,
                                        modifier = Modifier.width(spacing.lg),
                                    )
                                    Text(
                                        text = item.toAnnotated(),
                                        style = OpenCodeTypography.body,
                                        color = colors.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        is MdBlock.Numbered -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            block.items.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        style = OpenCodeTypography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = colors.onSurfaceMuted,
                                        modifier = Modifier
                                            .widthIn(min = spacing.lg + spacing.xs)
                                            .padding(end = spacing.xs, top = 1.dp),
                                    )
                                    Text(
                                        text = item.toAnnotated(),
                                        style = OpenCodeTypography.body,
                                        color = colors.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        is MdBlock.Rule -> Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = spacing.xs)
                                .height(0.5.dp)
                                .background(colors.hairline),
                        )

                        is MdBlock.Code -> CodeBlock(language = block.language, code = block.code)

                        is MdBlock.Table -> TableBlock(block)
                    }
                }
            }
        }
    }
}

/**
 * A fenced code block: sunken surface, monospace, header bar with language badge and actions.
 *
 * Matching cross-platform-llm-client code blocks with 12dp radius and hairline border.
 */
@Composable
private fun CodeBlock(language: String?, code: String) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val context = LocalContext.current
    var copied by remember(code) { mutableStateOf(false) }
    var saved by remember(code) { mutableStateOf(false) }
    // The system "save as" screen picks the place; the app needs no storage permission.
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) saved = writeText(context, uri, code)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.md))
            .background(colors.surfaceSunken)
            .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.md)),
    ) {
        // Header bar with language badge and action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant.copy(alpha = 0.35f))
                .padding(start = spacing.sm, end = spacing.xs, top = spacing.xs, bottom = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Language badge pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(radius.xs))
                    .background(colors.fill)
                    .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.xs))
                    .padding(horizontal = spacing.xs + 2.dp, vertical = spacing.xxs),
            ) {
                Text(
                    text = (language ?: stringResource(R.string.code_label)).lowercase(),
                    style = OpenCodeTypography.codeInline.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.onSurfaceMuted,
                )
            }

            Spacer(Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BlockAction(
                    label = stringResource(if (saved) R.string.code_saved else R.string.code_download),
                    glyph = Glyph.ArrowDown,
                    done = saved,
                ) {
                    saveAs.launch(suggestedFileName(language))
                }
                BlockAction(
                    label = stringResource(if (copied) R.string.code_copied else R.string.code_copy),
                    glyph = Glyph.Copy,
                    done = copied,
                ) {
                    copyToClipboard(context, code)
                    copied = true
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

        // Monospace code with horizontal scroll and comfortable padding
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                text = highlightedCode(language, code),
                style = OpenCodeTypography.code,
                softWrap = false,
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm + 2.dp),
            )
        }
    }
}

/**
 * A code block as coloured text.
 *
 * Only the colour changes: the underlying characters are the source verbatim, so the
 * copy button and text selection still hand out exactly what the model emitted.
 */
@Composable
private fun highlightedCode(language: String?, code: String): AnnotatedString {
    val colors = OpenCodeTheme.colors
    val tokens = remember(code, language) { highlight(language, code) }
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
 * Columns share the width equally and wrap. Outer hairline border, clean header
 * row styling, alternating row tint, and full-width hairline dividers for clean tabular data.
 */
@Composable
private fun TableBlock(table: MdBlock.Table) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    val colCount = table.columns.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.md))
            .background(colors.surfaceSunken)
            .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.md)),
    ) {
        if (table.header.isNotEmpty() && table.header.any { it.isNotEmpty() }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                table.header.forEachIndexed { colIdx, cell ->
                    val weight = if (colIdx == 0 && colCount == 2) 0.38f else 1f / colCount
                    Text(
                        text = cell.toAnnotated(),
                        style = OpenCodeTypography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface,
                        modifier = Modifier
                            .weight(weight)
                            .padding(end = spacing.xs),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.hairline),
            )
        }
        table.rows.forEachIndexed { rowIdx, row ->
            if (rowIdx > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(colors.hairline),
                )
            }
            val rowBg = if (rowIdx % 2 == 1) colors.fill.copy(alpha = 0.45f) else Color.Transparent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEachIndexed { colIdx, cell ->
                    val isFirstCol = colIdx == 0
                    val weight = if (isFirstCol && colCount == 2) 0.38f else 1f / colCount
                    Text(
                        text = cell.toAnnotated(),
                        style = if (isFirstCol) {
                            OpenCodeTypography.bodySmall.copy(fontWeight = FontWeight.Medium)
                        } else {
                            OpenCodeTypography.bodySmall
                        },
                        color = colors.onSurface,
                        modifier = Modifier
                            .weight(weight)
                            .padding(end = spacing.xs),
                    )
                }
            }
        }
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
    val tint = if (done) colors.success else colors.onSurfaceMuted

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(radius.xs))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 32.dp)
            .padding(horizontal = spacing.xs + 2.dp, vertical = spacing.xxs),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            if (glyph != null) {
                GlyphIcon(glyph = glyph, tint = tint, size = 12.dp)
            }
            Text(
                text = label,
                style = OpenCodeTypography.label.copy(fontSize = 11.sp),
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

/** Converts inline spans to an [AnnotatedString]. */
@Composable
private fun List<MdSpan>.toAnnotated(): AnnotatedString {
    val colors = OpenCodeTheme.colors

    return buildAnnotatedString {
        this@toAnnotated.forEach { span ->
            when (span) {
                is MdSpan.Text -> append(span.text)
                is MdSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(span.text)
                }
                is MdSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(span.text)
                }
                is MdSpan.Code -> withStyle(
                    SpanStyle(
                        fontFamily = OpenCodeTypography.mono,
                        fontSize = OpenCodeTypography.codeInline.fontSize,
                        fontWeight = FontWeight.Medium,
                        background = colors.surfaceVariant,
                        color = colors.onSurface,
                        baselineShift = BaselineShift(0.04f),
                    ),
                ) {
                    append('\u00A0')
                    append(span.text)
                    append('\u00A0')
                }
                is MdSpan.Link -> withStyle(
                    SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline),
                ) {
                    append(span.text)
                }
            }
        }
    }
}
