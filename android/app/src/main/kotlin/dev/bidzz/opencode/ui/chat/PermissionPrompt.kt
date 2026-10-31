package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.api.dto.PendingPermission
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Shown while the engine is blocked waiting for permission.
 *
 * The agent cannot continue until this is answered, so the card sits above the
 * composer rather than inside the scrolling transcript: scrolling away must not
 * be able to hide the only control that unblocks the conversation.
 *
 * Three answers mirror the engine's contract:
 * - `once` allows this request only.
 * - `always` writes a saved permission for the project, so the same request is
 *   not asked again.
 * - `reject` refuses and lets the agent continue without the tool.
 */
@Composable
fun PermissionPrompt(
    request: PendingPermission,
    onReply: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised)
            .border(0.5.dp, colors.outline, RoundedCornerShape(radius.lg))
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = "Permission required",
            style = OpenCodeTypography.label,
            color = colors.warning,
        )

        Text(
            text = request.action,
            style = OpenCodeTypography.body,
            color = colors.onSurface,
        )

        val detail = request.message
            ?: request.resources.takeIf { it.isNotEmpty() }?.joinToString("\n")
        if (detail != null) {
            Text(
                text = detail,
                style = OpenCodeTypography.code,
                color = colors.onSurfaceMuted,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            PermissionAction(
                label = "Allow once",
                color = colors.accent,
                onSurface = colors.onAccent,
                onClick = { onReply("once") },
                modifier = Modifier.weight(1f),
            )
            PermissionAction(
                label = "Always",
                color = colors.surfaceSunken,
                onSurface = colors.onSurface,
                onClick = { onReply("always") },
                modifier = Modifier.weight(1f),
            )
            PermissionAction(
                label = "Reject",
                color = colors.surfaceSunken,
                onSurface = colors.danger,
                onClick = { onReply("reject") },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PermissionAction(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onSurface: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.sm))
            .background(color)
            .clickable(onClick = onClick)
            .padding(vertical = spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.label,
            color = onSurface,
        )
    }
}
