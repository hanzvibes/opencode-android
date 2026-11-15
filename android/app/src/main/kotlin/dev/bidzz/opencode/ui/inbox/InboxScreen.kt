package dev.bidzz.opencode.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.InboxItem
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.TextAction
import dev.bidzz.opencode.ui.message

/**
 * Queued work for one session, in enqueue order.
 *
 * Each row shows the item kind, its summary (prompt text, note, or move
 * destination) and its delivery mode. A queued item can be cancelled,
 * and its delivery switched between queue and steer (audit-200 C17,
 * M-257) — a mistyped prompt no longer has to run just because it was
 * queued.
 */
@Composable
fun InboxScreen(
    sessionID: String,
    state: InboxUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCancel: (String) -> Unit = {},
    onSetDelivery: (String, String) -> Unit = { _, _ -> },
    onClearActionFailure: () -> Unit = {},
    inboxUpdatedAtMs: Long = 0L,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val context = LocalContext.current

    LaunchedEffect(sessionID) { onRefresh() }
    LaunchedEffect(inboxUpdatedAtMs) {
        if (inboxUpdatedAtMs > 0L) onRefresh()
    }

    // Action failures arrive as values; show once, then clear.
    // message() is @Composable, so resolve it here — not inside LaunchedEffect.
    val actionFailureMessage = state.actionFailure?.message()
    LaunchedEffect(state.actionFailure) {
        val message = actionFailureMessage ?: return@LaunchedEffect
        (context.applicationContext as OpenCodeApplication).showSnackbar(message)
        onClearActionFailure()
    }

    ScreenScaffold(
        title = stringResource(R.string.inbox_title),
        onBack = onBack,
        contentMaxWidth = 720.dp,
        actions = {
            // Audit-200 C17 (M-261): the queue used to refresh only on
            // entry — a prompt enqueued while the screen was open stayed
            // invisible until the screen was left and re-entered.
            TextAction(
                label = stringResource(R.string.common_refresh),
                onClick = onRefresh,
                enabled = !state.loading,
            )
        },
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
                state.loading -> LoadingState(stringResource(R.string.common_loading), onRetry = onRefresh)
                state.failure != null -> EmptyState(state.failure.message(), isError = true, onRetry = onRefresh)
                state.items.isEmpty() -> EmptyState(stringResource(R.string.inbox_empty))
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
                            InboxRow(
                                item = item,
                                busy = item.id in state.busyItems,
                                onCancel = { onCancel(item.id) },
                                onSetDelivery = { onSetDelivery(item.id, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxRow(
    item: InboxItem,
    busy: Boolean,
    onCancel: () -> Unit,
    onSetDelivery: (String) -> Unit,
) {
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
        // Audit-200 C17 (M-257): cancel, and switch the delivery mode —
        // a queued prompt goes to "Send now" (steer: into the running
        // execution at once), a steering one back to "Queue instead".
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            TextButton(onClick = onCancel, enabled = !busy) {
                Text(
                    text = stringResource(R.string.inbox_cancel),
                    style = OpenCodeTypography.label,
                    color = colors.danger,
                )
            }
            if (delivery != null) {
                TextButton(
                    onClick = {
                        onSetDelivery(if (delivery.lowercase() == "steer") "queue" else "steer")
                    },
                    enabled = !busy,
                ) {
                    Text(
                        text = stringResource(
                            if (delivery.lowercase() == "steer") {
                                R.string.inbox_switch_queue
                            } else {
                                R.string.inbox_switch_steer
                            },
                        ),
                        style = OpenCodeTypography.label,
                        color = colors.accent,
                    )
                }
            }
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
