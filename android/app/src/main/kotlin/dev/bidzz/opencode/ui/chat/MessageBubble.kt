package dev.bidzz.opencode.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon

/**
 * One chat message styled after cross-platform-llm-client (PrivateLM):
 * - User message: compact system blue bubble on the right with smooth rounded corners.
 * - Assistant message: clean full-width markdown with quiet copy button and tool traces.
 */
@Composable
fun MessageBubble(
    role: Role,
    markdown: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    tools: List<dev.bidzz.opencode.core.api.dto.ToolCall> = emptyList(),
    /** Mode and model that produced this reply, when known. Empty when absent. */
    provenance: String? = null,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions

    when (role) {
        Role.USER -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(dims.userBubbleMaxWidthFraction)
                    .background(
                        colors.userBubble,
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
            if (!provenance.isNullOrBlank() || streaming) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!provenance.isNullOrBlank()) {
                        Text(
                            text = provenance,
                            style = OpenCodeTypography.label,
                            color = colors.onSurfaceFaint,
                        )
                    }
                    if (streaming) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(start = if (provenance.isNullOrBlank()) 0.dp else spacing.sm)
                                .size(dims.statusDotSize),
                            strokeWidth = 1.5.dp,
                            color = colors.accent,
                        )
                    }
                }
            }
            Box(Modifier.padding(top = spacing.xs)) {
                if (markdown.isEmpty() && streaming) {
                    Text(
                        text = stringResource(R.string.bubble_thinking),
                        style = OpenCodeTypography.body,
                        color = colors.onSurfaceMuted,
                    )
                } else {
                    MarkdownText(markdown = markdown)
                }
            }
            ToolTrace(
                tools = tools,
                streaming = streaming,
                modifier = Modifier.padding(top = spacing.xs),
            )
            if (!streaming && markdown.isNotEmpty()) {
                val context = LocalContext.current
                Row(
                    modifier = Modifier.padding(top = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(radius.sm))
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("OpenCode", markdown))
                            }
                            .padding(horizontal = spacing.xs, vertical = spacing.xxs),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            GlyphIcon(Glyph.Copy, tint = colors.onSurfaceFaint, size = 13.dp)
                            Text(
                                text = stringResource(R.string.code_copy),
                                style = OpenCodeTypography.label.copy(fontSize = 11.sp),
                                color = colors.onSurfaceFaint,
                            )
                        }
                    }
                }
            }
        }

        Role.ERROR -> Row(modifier = modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(dims.userBubbleMaxWidthFraction)
                    .background(colors.surfaceSunken, RoundedCornerShape(radius.md))
                    .border(0.5.dp, colors.danger.copy(alpha = 0.3f), RoundedCornerShape(radius.md))
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                Column {
                    Text(
                        text = markdown,
                        style = OpenCodeTypography.bodySmall,
                        color = colors.danger,
                    )
                    provenance?.let {
                        Text(
                            text = it,
                            style = OpenCodeTypography.label,
                            color = colors.onSurfaceFaint,
                            modifier = Modifier.padding(top = spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

enum class Role { USER, ASSISTANT, ERROR }

