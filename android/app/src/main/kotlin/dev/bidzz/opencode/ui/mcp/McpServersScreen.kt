package dev.bidzz.opencode.ui.mcp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.McpServer
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.StatusDot
import dev.bidzz.opencode.ui.message

/**
 * MCP servers configured on the engine, with their connection status.
 *
 * Read-only: the list comes from `GET /api/mcp`. A failed server shows
 * the engine's error text under its name; connecting, disconnecting,
 * adding and removing are not available from the app yet.
 */
@Composable
fun McpServersScreen(
    state: McpServersUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    LaunchedEffect(Unit) { onRefresh() }

    ScreenScaffold(
        title = stringResource(R.string.mcp_title),
        onBack = onBack,
        contentMaxWidth = 720.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(R.string.mcp_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(
                    horizontal = spacing.lg,
                    vertical = spacing.sm,
                ),
            )
            when {
                state.loading -> CenteredMessage(stringResource(R.string.common_loading))
                state.failure != null -> CenteredMessage(state.failure.message(), isError = true)
                state.servers.isEmpty() -> CenteredMessage(stringResource(R.string.mcp_empty))
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.lg),
                    ) {
                        itemsIndexed(state.servers, key = { _, server -> server.name }) { index, server ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            McpServerRow(server = server)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun McpServerRow(server: McpServer) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val kind = server.statusKind()
    val statusText = mcpStatusWord(kind)
    val dotColor = when (kind) {
        McpStatusKind.CONNECTED -> colors.success
        McpStatusKind.PENDING, McpStatusKind.NEEDS_AUTH -> colors.warning
        McpStatusKind.FAILED -> colors.danger
        McpStatusKind.DISABLED, McpStatusKind.UNKNOWN -> colors.onSurfaceMuted
    }
    // The title is the human name when the engine has one; the server name
    // is the key the config uses, so it stays visible underneath.
    val title = server.title?.takeIf { it.isNotBlank() } ?: server.name
    val error = server.status.error?.takeIf { it.isNotBlank() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = spacing.xs),
        ) {
            Text(
                text = title,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (title != server.name) {
                Text(
                    text = server.name,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (error != null && (kind == McpStatusKind.FAILED || kind == McpStatusKind.NEEDS_AUTH)) {
                Text(
                    text = error,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.danger,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = spacing.xxs),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = spacing.xs),
        ) {
            StatusDot(color = dotColor, size = 7.dp, modifier = Modifier.padding(end = spacing.xs))
            Text(
                text = statusText,
                style = OpenCodeTypography.label,
                color = dotColor,
            )
        }
    }
}

/** Lower-case status word for use inside a sentence ("Server is connected"). */
@Composable
private fun mcpStatusWord(kind: McpStatusKind): String = stringResource(
    when (kind) {
        McpStatusKind.CONNECTED -> R.string.mcp_status_connected
        McpStatusKind.DISABLED -> R.string.mcp_status_disabled
        McpStatusKind.FAILED -> R.string.mcp_status_failed
        McpStatusKind.NEEDS_AUTH -> R.string.mcp_status_needs_auth
        McpStatusKind.PENDING -> R.string.mcp_status_pending
        McpStatusKind.UNKNOWN -> R.string.mcp_status_unknown
    },
)

@Composable
private fun CenteredMessage(text: String, isError: Boolean = false) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = OpenCodeTypography.body,
            color = if (isError) colors.danger else colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .padding(spacing.xxl),
        )
    }
}
