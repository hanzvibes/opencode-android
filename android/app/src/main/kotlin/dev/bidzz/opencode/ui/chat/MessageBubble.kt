package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * One chat message.
 *
 * Layout is asymmetric on purpose, following how reading actually works here:
 *
 * - The user's own message is a compact bubble on the right. It is short, already
 *   known, and does not need width.
 * - The assistant's message is full-width with no bubble. Model output contains
 *   code and tables, and a bubble would steal horizontal space from exactly the
 *   content that needs it.
 *
 * That asymmetry is the single biggest reason a chat reads as a coding tool rather
 * than a messaging app.
 */
@Composable
fun MessageBubble(
    role: Role,
    markdown: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    tools: List<dev.bidzz.opencode.core.api.dto.ToolCall> = emptyList(),
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
                    .background(colors.userBubble, RoundedCornerShape(radius.lg))
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                // User text is plain: markdown the user typed is not rendered as
                // formatting, so what they see is what they sent.
                Text(
                    text = markdown,
                    style = OpenCodeTypography.body,
                    color = colors.onSurface,
                )
            }
        }

        Role.ASSISTANT -> Column(modifier = modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "OPENCODE",
                    style = OpenCodeTypography.label,
                    color = colors.onSurfaceFaint,
                )
                if (streaming) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = spacing.sm)
                            .size(dims.statusDotSize),
                        strokeWidth = 1.5.dp,
                        color = colors.accent,
                    )
                }
            }
            Box(Modifier.padding(top = spacing.xs)) {
                if (markdown.isEmpty() && streaming) {
                    // Nothing yet: say so instead of showing an empty gap that looks
                    // like a broken message.
                    Text(
                        text = "Thinking…",
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
        }

        Role.ERROR -> Row(modifier = modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(dims.userBubbleMaxWidthFraction)
                    .background(colors.surfaceSunken, RoundedCornerShape(radius.sm))
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                Text(
                    text = markdown,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.danger,
                )
            }
        }
    }
}

enum class Role { USER, ASSISTANT, ERROR }

