package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.PendingPermission
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.StatusDot

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
    val cardShape = RoundedCornerShape(radius.lg)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surface)
            .border(0.5.dp, colors.warning.copy(alpha = 0.35f), cardShape)
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        // Warning card header with badge or subtle status dot
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(colors.warning.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                StatusDot(
                    color = colors.warning,
                    size = 6.dp,
                )
            }
            Text(
                text = stringResource(R.string.permission_title),
                style = OpenCodeTypography.label,
                color = colors.warning,
            )
        }

        // Action description
        if (request.action.isNotBlank()) {
            Text(
                text = request.action,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
            )
        }

        // Detail code box styling (monospace, hairline border, rounded corners)
        val detail = remember(request.message, request.resources, request.action) {
            val items = mutableListOf<String>()
            request.message?.takeIf { it.isNotBlank() && it != request.action }?.let { items.add(it) }
            if (request.resources.isNotEmpty()) {
                items.addAll(request.resources.filter { it.isNotBlank() })
            }
            if (items.isEmpty() && !request.message.isNullOrBlank()) {
                items.add(request.message)
            }
            items.joinToString("\n").takeIf { it.isNotBlank() }
        }

        if (detail != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radius.sm))
                    .background(colors.surfaceSunken)
                    .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.sm))
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                ) {
                    Text(
                        text = detail,
                        style = OpenCodeTypography.code,
                        color = colors.onSurfaceMuted,
                        softWrap = false,
                    )
                }
            }
        }

        // Action buttons: "Allow once" (Primary accent Apple Blue), "Always" (Neutral tonal fill), "Reject" (Soft red destructive tint)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            PermissionAction(
                label = stringResource(R.string.permission_allow_once),
                backgroundColor = colors.accent,
                contentColor = colors.onAccent,
                onClick = { onReply("once") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            PermissionAction(
                label = stringResource(R.string.permission_always),
                backgroundColor = colors.fill,
                contentColor = colors.onSurface,
                borderColor = colors.hairline,
                onClick = { onReply("always") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            PermissionAction(
                label = stringResource(R.string.permission_reject),
                backgroundColor = colors.danger.copy(alpha = 0.14f),
                contentColor = colors.danger,
                borderColor = colors.danger.copy(alpha = 0.22f),
                onClick = { onReply("reject") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun PermissionAction(
    label: String,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
) {
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val shape = RoundedCornerShape(radius.sm)

    val borderModifier = if (borderColor != null) {
        Modifier.border(0.5.dp, borderColor, shape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .background(backgroundColor)
            .then(borderModifier)
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = spacing.xs, vertical = spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.label,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
