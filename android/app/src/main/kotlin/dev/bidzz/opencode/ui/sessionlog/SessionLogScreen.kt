package dev.bidzz.opencode.ui.sessionlog

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.SessionLogEvent
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.TextAction
import dev.bidzz.opencode.ui.message

/**
 * The durable session event log, read-only (audit-200 C17, M-259).
 *
 * One-shot read from the engine (`follow=false`): the backlog arrives,
 * then a `log.synced` marker, then the stream closes — this is a
 * diagnostic snapshot, not a live feed. Each row shows the event type,
 * its wall-clock, and a one-line summary; the footer names the
 * watermark the read reached (or that the read never synced).
 */
@Composable
fun SessionLogScreen(
    sessionID: String,
    state: SessionLogUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    LaunchedEffect(sessionID) { onRefresh() }

    ScreenScaffold(
        title = stringResource(R.string.session_log_title),
        onBack = onBack,
        contentMaxWidth = 720.dp,
        actions = {
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
                text = stringResource(R.string.session_log_body),
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
                state.events.isEmpty() -> EmptyState(stringResource(R.string.session_log_empty))
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.lg),
                    ) {
                        itemsIndexed(
                            state.events,
                            key = { index, event -> "${event.seq ?: index}:${event.type}" },
                        ) { index, event ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            SessionLogRow(event = event)
                        }
                        item {
                            val synced = state.syncedSeq
                            Text(
                                text = if (synced != null) {
                                    stringResource(R.string.session_log_synced, synced)
                                } else {
                                    stringResource(R.string.session_log_unsynced)
                                },
                                style = OpenCodeTypography.bodySmall,
                                color = colors.onSurfaceFaint,
                                modifier = Modifier.padding(vertical = spacing.md),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionLogRow(event: SessionLogEvent) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val summary = sessionLogSummary(event)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
    ) {
        Text(
            text = buildString {
                formatLogTime(event.createdMs)?.let { append("$it  ") }
                append(event.type)
                event.seq?.let { append("  #$it") }
            },
            style = OpenCodeTypography.label,
            color = colors.onSurfaceMuted,
        )
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
    }
}
