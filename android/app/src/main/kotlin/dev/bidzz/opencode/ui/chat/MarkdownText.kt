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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
            CopyButton(copied = copied) {
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
                text = code,
                style = OpenCodeTypography.code,
                color = colors.onSurface,
                softWrap = false,
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            )
        }
    }
}

@Composable
private fun CopyButton(copied: Boolean, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(radius.xs))
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = spacing.xxs),
    ) {
        Text(
            text = if (copied) "Copied" else "Copy",
            style = OpenCodeTypography.label,
            color = if (copied) colors.success else colors.onSurfaceMuted,
        )
    }
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
