package dev.bidzz.opencode.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import kotlinx.coroutines.delay

/**
 * One chat message styled after cross-platform-llm-client (PrivateLM):
 * - User message: compact system blue bubble on the right with asymmetric rounded corners.
 * - Assistant message: clean full-width markdown with quiet copy button and tool traces.
 * - Error message: tinted warning surface with danger accent and readability hierarchy.
 */
@Composable
fun MessageBubble(
    role: Role,
    markdown: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    tools: List<ToolCall> = emptyList(),
    /** Mode and model that produced this reply, when known. Empty when absent. */
    provenance: String? = null,
    showProvenance: Boolean = true,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions

    when (role) {
        Role.USER -> BoxWithConstraints(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            val maxBubbleWidth = maxWidth * dims.userBubbleMaxWidthFraction
            val userBubbleShape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = radius.xs,
            )
            Box(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .clip(userBubbleShape)
                    .background(colors.userBubble)
                    .padding(horizontal = spacing.lg, vertical = 10.dp),
            ) {
                // User text is plain: markdown the user typed is not rendered as
                // formatting, so what they see is what they sent.
                Text(
                    text = markdown,
                    style = OpenCodeTypography.body,
                    color = colors.onUserBubble,
                )
            }
        }

        Role.ASSISTANT -> Column(modifier = modifier.fillMaxWidth()) {
            // Which mode and model answered, small and quiet: it is reference, not content.
            // Nothing at all when there is nothing to say, instead of a label on every reply.
            val hasProvenanceHeader = showProvenance && !provenance.isNullOrBlank()
            val showStreamingSpinner = streaming && markdown.isNotEmpty()
            if (hasProvenanceHeader || showStreamingSpinner) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = spacing.sm),
                ) {
                    if (hasProvenanceHeader) {
                        Text(
                            text = provenance.orEmpty(),
                            style = OpenCodeTypography.label.copy(fontSize = 12.sp),
                            color = colors.onSurfaceFaint,
                        )
                    }
                    if (showStreamingSpinner) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(start = if (hasProvenanceHeader) spacing.sm else 0.dp)
                                .size(dims.statusDotSize),
                            strokeWidth = 1.5.dp,
                            color = colors.accent,
                        )
                    }
                }
            }
            if (tools.isNotEmpty()) {
                ToolTrace(
                    tools = tools,
                    streaming = streaming,
                    modifier = Modifier.padding(bottom = if (markdown.isNotEmpty()) spacing.sm else 0.dp),
                )
            }
            if (markdown.isNotEmpty() || (streaming && tools.isEmpty())) {
                Box {
                    if (markdown.isEmpty() && streaming) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            modifier = Modifier.padding(vertical = spacing.xxs),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(dims.statusDotSize),
                                strokeWidth = 1.5.dp,
                                color = colors.accent,
                            )
                            Text(
                                text = stringResource(R.string.bubble_thinking),
                                style = OpenCodeTypography.body,
                                color = colors.onSurfaceMuted,
                            )
                        }
                    } else {
                        MarkdownText(markdown = markdown)
                    }
                }
            }
            if (!streaming && markdown.isNotEmpty()) {
                CopyButton(
                    text = markdown,
                    modifier = Modifier.padding(top = spacing.sm),
                )
            }
        }

        Role.ERROR -> BoxWithConstraints(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            val maxBubbleWidth = maxWidth * dims.userBubbleMaxWidthFraction
            val errorShape = RoundedCornerShape(radius.md)
            Box(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .clip(errorShape)
                    .background(colors.surfaceSunken)
                    .background(colors.danger.copy(alpha = 0.08f))
                    .border(0.5.dp, colors.danger.copy(alpha = 0.35f), errorShape)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    GlyphIcon(
                        glyph = Glyph.Info,
                        tint = colors.danger,
                        size = dims.iconSmall,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = markdown,
                            style = OpenCodeTypography.bodySmall,
                            color = colors.danger,
                        )
                        if (!provenance.isNullOrBlank()) {
                            Text(
                                text = provenance,
                                style = OpenCodeTypography.label.copy(fontSize = 11.sp),
                                color = colors.onSurfaceFaint,
                                modifier = Modifier.padding(top = spacing.xs),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Subtle tonal pill with tap ripple, icon, and ephemeral "Copied" feedback.
 */
@Composable
private fun CopyButton(
    text: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    val pillShape = RoundedCornerShape(radius.full)
    val contentColor = if (copied) colors.success else colors.onSurfaceMuted

    Box(
        modifier = modifier
            .clip(pillShape)
            .background(if (copied) colors.success.copy(alpha = 0.12f) else colors.surfaceVariant)
            .border(
                0.5.dp,
                if (copied) colors.success.copy(alpha = 0.4f) else colors.hairline,
                pillShape,
            )
            .clickable {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("OpenCode", text))
                copied = true
            }
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            GlyphIcon(
                glyph = Glyph.Copy,
                tint = contentColor,
                size = 12.dp,
            )
            Text(
                text = stringResource(if (copied) R.string.code_copied else R.string.code_copy),
                style = OpenCodeTypography.label.copy(fontSize = 11.sp),
                color = contentColor,
            )
        }
    }
}

enum class Role { USER, ASSISTANT, ERROR }
