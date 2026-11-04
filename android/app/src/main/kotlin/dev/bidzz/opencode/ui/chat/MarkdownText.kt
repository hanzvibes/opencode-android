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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

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

                        is MdBlock.Heading -> Text(
                            text = block.spans.toAnnotated(),
                            style = OpenCodeTypography.body.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = when (block.level) {
                                    1 -> OpenCodeTypography.body.fontSize * 1.35f
                                    2 -> OpenCodeTypography.body.fontSize * 1.2f
                                    else -> OpenCodeTypography.body.fontSize * 1.08f
                                },
                            ),
                            color = colors.onSurface,
                            modifier = Modifier.padding(top = if (block.level == 1) spacing.xs else spacing.xxs),
                        )

                        is MdBlock.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                            block.items.forEach { item ->
                                Row {
                                    Text(
                                        text = "•",
                                        style = OpenCodeTypography.body,
                                        color = colors.onSurfaceMuted,
                                        modifier = Modifier.width(spacing.md),
                                    )
                                    Text(
                                        text = item.toAnnotated(),
                                        style = OpenCodeTypography.body,
                                        color = colors.onSurface,
                                    )
                                }
                            }
                        }

                        is MdBlock.Numbered -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                            block.items.forEachIndexed { index, item ->
                                Row {
                                    Text(
                                        text = "${index + 1}.",
                                        style = OpenCodeTypography.body,
                                        color = colors.onSurfaceMuted,
                                        modifier = Modifier.width(spacing.lg),
                                    )
                                    Text(
                                        text = item.toAnnotated(),
                                        style = OpenCodeTypography.body,
                                        color = colors.onSurface,
                                    )
                                }
                            }
                        }

                        is MdBlock.Rule -> Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = spacing.xs)
                                .border(0.5.dp, colors.outline),
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
 * A fenced code block: sunken surface, monospace, copy button.
 *
 * The surface recedes (`surfaceSunken`) rather than raises: code is quoted material,
 * and a raised card would compete with the message it belongs to.
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
            .background(colors.surfaceSunken, RoundedCornerShape(radius.sm))
            .border(0.5.dp, colors.outline, RoundedCornerShape(radius.sm)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.md, end = spacing.sm, top = spacing.xs, bottom = spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language ?: "code",
                style = OpenCodeTypography.label,
                color = colors.onSurfaceFaint,
                modifier = Modifier.weight(1f),
            )
            BlockAction(if (saved) "Saved" else "Download", done = saved) {
                saveAs.launch(suggestedFileName(language))
            }
            BlockAction(if (copied) "Copied" else "Copy", done = copied) {
                copyToClipboard(context, code)
                copied = true
            }
        }
        // Horizontal scroll: long lines should be readable rather than wrapped into
        // an unreadable staircase. Vertical scroll is left to the message list.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                text = highlightedCode(language, code),
                style = OpenCodeTypography.code,
                softWrap = false,
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
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
 * Columns share the width equally and wrap. That is a deliberate limit: horizontal
 * scrolling would fight `fillMaxWidth` inside a scroll container and leave narrow
 * tables floating, and models almost never emit more than a handful of columns here.
 * A six-column table is therefore cramped rather than broken, which is the cheaper
 * failure.
 */
@Composable
private fun TableBlock(table: MdBlock.Table) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceSunken, RoundedCornerShape(radius.sm))
            .border(0.5.dp, colors.outline, RoundedCornerShape(radius.sm))
            .padding(horizontal = spacing.xs, vertical = spacing.xs),
    ) {
        Row(Modifier.fillMaxWidth()) {
            table.header.forEach { cell ->
                Text(
                    text = cell.toAnnotated(),
                    style = OpenCodeTypography.label,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.weight(1f).padding(spacing.xs),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xs)
                .border(0.5.dp, colors.outline),
        )
        table.rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { cell ->
                    Text(
                        text = cell.toAnnotated(),
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f).padding(spacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockAction(label: String, done: Boolean, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(radius.xs))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.label,
            color = if (done) colors.success else colors.onSurfaceMuted,
        )
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
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    return buildAnnotatedString {
        this@toAnnotated.forEach { span ->
            when (span) {
                is MdSpan.Text -> append(span.text)
                is MdSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(span.text)
                }
                is MdSpan.Italic -> withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(span.text)
                }
                is MdSpan.Code -> withStyle(
                    SpanStyle(
                        fontFamily = OpenCodeTypography.mono,
                        fontSize = OpenCodeTypography.codeInline.fontSize,
                        background = colors.surfaceSunken,
                        color = colors.onSurface,
                    ),
                ) {
                    append(" ${span.text} ")
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
