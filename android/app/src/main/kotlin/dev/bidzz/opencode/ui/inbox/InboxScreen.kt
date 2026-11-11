package dev.bidzz.opencode.ui.inbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.InboxItem
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.message

/**
 * Queued work for one session, in enqueue order.
 *
 * Read-only: the list comes from `GET /api/session/{id}/inbox`. Each row
 * shows the item kind, its summary (prompt text, note, or move
 * destination) and its delivery mode. Cancelling an item and changing its
 * delivery are not available from the app yet.
 */
@Composable
fun InboxScreen(
    sessionID: String,
    state: InboxUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    LaunchedEffect(sessionID) { onRefresh() }

    ScreenScaffold(
        title = stringResource(R.string.inbox_title),
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
                text = stringResource(R.string.inbox_body),
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
                state.items.isEmpty() -> CenteredMessage(stringResource(R.string.inbox_empty))
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.lg),
                    ) {
                        itemsIndexed(
                            state.items,
                            key = { index, item -> "$index:${item.id}" },
                        ) { index, item ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            InboxRow(item = item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxRow(item: InboxItem) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val summary = item.summaryText()
    val fileCount = item.fileCount()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
    ) {
        Text(
            text = inboxTypeWord(item.type),
            style = OpenCodeTypography.label,
            color = colors.onSurfaceMuted,
        )
        if (summary != null) {
            Text(
                text = summary,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        val delivery = item.delivery?.takeIf { it.isNotBlank() }
        val metaText = buildString {
            if (delivery != null) append(inboxDeliveryWord(delivery))
            if (fileCount > 0) {
                if (isNotEmpty()) append(" · ")
                append(fileCountText(fileCount))
            }
        }
        if (metaText.isNotEmpty()) {
            Text(
                text = metaText,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceFaint,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
    }
}

@Composable
private fun inboxTypeWord(type: String): String = stringResource(
    when (type.lowercase()) {
        "user" -> R.string.inbox_type_user
        "synthetic" -> R.string.inbox_type_synthetic
        "compaction" -> R.string.inbox_type_compaction
        "move" -> R.string.inbox_type_move
        else -> R.string.inbox_type_unknown
    },
)

@Composable
private fun inboxDeliveryWord(delivery: String): String = when (delivery.lowercase()) {
    "steer" -> stringResource(R.string.inbox_delivery_steer)
    "queue" -> stringResource(R.string.inbox_delivery_queue)
    // An unknown delivery mode is shown as-is rather than hidden — a new
    // mode is information, not an error.
    else -> delivery
}

@Composable
private fun fileCountText(count: Int): String =
    stringResource(R.string.inbox_files, count)

@Composable
private fun CenteredMessage(text: String, isError: Boolean = false) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = OpenCodeTypography.body,
            color = if (isError) colors.danger else colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
    }
}
