package dev.bidzz.opencode.ui.jobs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.JobInfo
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.SelectorChip
import dev.bidzz.opencode.ui.StatusDot
import dev.bidzz.opencode.ui.message
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Background jobs on the engine.
 *
 * The list comes from `GET /api/experimental/job`, filtered server-side
 * by the kind and status chips. A row is a shell command, a subagent or
 * an image generation running behind a session — the detail line is
 * its command, description or output path.
 */
@Composable
fun JobsScreen(
    state: JobsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onKindFilter: (JobKindFilter) -> Unit,
    onStatusFilter: (JobStatusFilter) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    LaunchedEffect(Unit) { onRefresh() }

    ScreenScaffold(
        title = stringResource(R.string.job_title),
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
                text = stringResource(R.string.job_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(
                    horizontal = spacing.lg,
                    vertical = spacing.sm,
                ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = spacing.lg, vertical = spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                for (filter in JobKindFilter.entries) {
                    SelectorChip(
                        label = stringResource(jobKindLabel(filter)),
                        selected = state.kindFilter == filter,
                        onClick = { onKindFilter(filter) },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = spacing.lg, vertical = spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                for (filter in JobStatusFilter.entries) {
                    SelectorChip(
                        label = stringResource(jobStatusLabel(filter)),
                        selected = state.statusFilter == filter,
                        onClick = { onStatusFilter(filter) },
                    )
                }
            }
            when {
                state.loading -> LoadingState(stringResource(R.string.common_loading), onRetry = onRefresh)
                state.failure != null -> EmptyState(state.failure.message(), isError = true)
                state.jobs.isEmpty() -> EmptyState(stringResource(R.string.job_empty))
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.lg),
                    ) {
                        itemsIndexed(state.jobs, key = { _, job -> job.id }) { index, job ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            JobRow(job = job)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobRow(job: JobInfo) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dotColor = when (job.status.lowercase()) {
        "running" -> colors.accent
        "completed" -> colors.success
        "error" -> colors.danger
        else -> colors.onSurfaceMuted
    }

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
                text = job.detailLine(),
                style = OpenCodeTypography.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.job_started, formatTime(job.time.started)),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = spacing.xs),
        ) {
            StatusDot(color = dotColor, size = 7.dp, modifier = Modifier.padding(end = spacing.xs))
            Text(
                text = stringResource(jobStatusLabel(job.status)),
                style = OpenCodeTypography.label,
                color = dotColor,
            )
        }
    }
}

private fun jobKindLabel(filter: JobKindFilter): Int = when (filter) {
    JobKindFilter.ALL -> R.string.job_kind_all
    JobKindFilter.SHELL -> R.string.job_kind_shell
    JobKindFilter.SUBAGENT -> R.string.job_kind_subagent
    JobKindFilter.IMAGE -> R.string.job_kind_image
}

private fun jobStatusLabel(filter: JobStatusFilter): Int = when (filter) {
    JobStatusFilter.ALL -> R.string.job_status_all
    JobStatusFilter.RUNNING -> R.string.job_status_running
    JobStatusFilter.COMPLETED -> R.string.job_status_completed
    JobStatusFilter.ERROR -> R.string.job_status_error
    JobStatusFilter.CANCELLED -> R.string.job_status_cancelled
}

/** Status word for a raw engine status string; unknown values read as-is, never crash. */
private fun jobStatusLabel(status: String): Int = when (status.lowercase()) {
    "running" -> R.string.job_status_running
    "completed" -> R.string.job_status_completed
    "error" -> R.string.job_status_error
    "cancelled" -> R.string.job_status_cancelled
    else -> R.string.job_status_unknown
}

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault()))
