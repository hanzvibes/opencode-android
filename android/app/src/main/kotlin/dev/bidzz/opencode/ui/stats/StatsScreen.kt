package dev.bidzz.opencode.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.SessionStatsInfo
import dev.bidzz.opencode.core.api.dto.StatsModelUsage
import dev.bidzz.opencode.core.api.dto.StatsToolUsage
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.formatCompactTokens
import dev.bidzz.opencode.ui.message
import dev.bidzz.opencode.ui.usage.formatResetDate

/**
 * Aggregate session statistics, for the range the engine reports.
 *
 * Read-only: the totals come from `GET /api/experimental/session/stats`.
 * The endpoint is experimental, so a failure shows the empty state
 * instead of an error — the shape may change without notice, and an
 * empty stats page is information, not a broken screen. The range row
 * shows the engine's reported `from`–`to`; when the engine reports no
 * range the row says so instead of pretending a period (77-F1).
 */
@Composable
fun StatsScreen(
    state: StatsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    LaunchedEffect(Unit) { onRefresh() }

    ScreenScaffold(
        title = stringResource(R.string.stats_title),
        onBack = onBack,
        contentMaxWidth = 720.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.stats_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(bottom = spacing.sm),
            )
            when {
                state.loading -> LoadingState(stringResource(R.string.common_loading), onRetry = onRefresh)
                state.failure != null -> EmptyState(state.failure.message(), isError = true, onRetry = onRefresh)
                state.stats == null -> EmptyState(stringResource(R.string.stats_empty))
                else -> StatsContent(stats = state.stats)
            }
        }
    }
}

@Composable
private fun StatsContent(stats: SessionStatsInfo) {
    val spacing = OpenCodeTheme.tokens.spacing

    // The range the totals cover (77-F1): without it the aggregates cannot
    // be checked against any other surface. Both dates known → the span;
    // otherwise an honest "default range" instead of a guessed period.
    StatRow(
        labelRes = R.string.stats_range_label,
        value = statsRangeText(stats.range),
    )
    StatRow(labelRes = R.string.stats_field_sessions, value = stats.sessions.toString())
    StatRow(labelRes = R.string.stats_field_prompts, value = stats.prompts.toString())
    StatRow(labelRes = R.string.stats_field_steps, value = stats.steps.toString())
    StatRow(labelRes = R.string.stats_field_subagents, value = stats.subagents.toString())
    StatRow(
        labelRes = R.string.stats_field_tokens,
        // Compact like every other token surface (77-F5); a dash when the
        // engine reported no usage at all, not a "0" that claims zero use.
        value = stats.tokens.totalTokens()?.let { formatCompactTokens(it) } ?: "—",
    )
    StatRow(labelRes = R.string.stats_field_cost, value = formatStatsCost(stats.cost))
    StatRow(labelRes = R.string.stats_field_active_days, value = stats.activeDays.toString())
    StatRow(labelRes = R.string.stats_field_streak, value = stats.streak.toString())

    if (stats.models.isNotEmpty()) {
        SectionHeader(textRes = R.string.stats_section_models)
        stats.models.forEachIndexed { index, model ->
            if (index > 0) RowDivider(inset = spacing.md)
            ModelRow(model = model)
        }
    }

    if (stats.tools.mode == "detail" && stats.tools.usage.isNotEmpty()) {
        SectionHeader(textRes = R.string.stats_section_tools)
        val ranked = stats.tools.usage.sortedByDescending { it.calls }
        ranked.take(10).forEachIndexed { index, tool ->
            if (index > 0) RowDivider(inset = spacing.md)
            ToolRow(tool = tool)
        }
        // The list is capped at 10 (77-F7): say how many more hide below
        // instead of silently dropping them.
        val extra = ranked.size - 10
        if (extra > 0) {
            Text(
                text = stringResource(R.string.stats_tools_more, extra),
                style = OpenCodeTypography.bodySmall,
                color = OpenCodeTheme.colors.onSurfaceFaint,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
    } else {
        stats.tools.totals?.let { totals ->
            SectionHeader(textRes = R.string.stats_section_tools)
            StatRow(
                labelRes = R.string.stats_field_tool_calls,
                value = totals.calls.toString(),
            )
            StatRow(
                labelRes = R.string.stats_field_tool_succeeded,
                value = totals.succeeded.toString(),
            )
            StatRow(
                labelRes = R.string.stats_field_tool_failed,
                value = totals.failed.toString(),
            )
            StatRow(
                labelRes = R.string.stats_field_tool_unfinished,
                value = totals.unfinished.toString(),
            )
        }
    }

    // Daily activity the engine already fetched (77-F7): the trend was
    // dropped on the floor while the screen claimed "tool reliability"
    // it could not show. One row per day, newest first as reported.
    if (stats.activity.isNotEmpty()) {
        SectionHeader(textRes = R.string.stats_section_activity)
        stats.activity.forEachIndexed { index, day ->
            if (index > 0) RowDivider(inset = spacing.md)
            StatRow(
                label = day.date,
                value = stringResource(R.string.stats_activity_steps, day.steps),
            )
        }
    }
}

/** The stats range as "2026-11-01 – 2026-11-13", or the honest default label. */
@Composable
private fun statsRangeText(range: dev.bidzz.opencode.core.api.dto.StatsRange?): String {
    val from = range?.from?.let { formatResetDate(it) }
    val to = range?.to?.let { formatResetDate(it) }
    return if (from != null && to != null) {
        "$from – $to"
    } else {
        stringResource(R.string.stats_range_default)
    }
}

/** One totals row: label on the left, value on the right. */
@Composable
private fun StatRow(labelRes: Int, value: String) {
    StatRow(label = stringResource(labelRes), value = value)
}

/** The same row with a dynamic label (dates, model names). */
@Composable
private fun StatRow(label: String, value: String) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.body,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = OpenCodeTypography.body,
            color = colors.onSurfaceMuted,
        )
    }
}

@Composable
private fun SectionHeader(textRes: Int) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Text(
        text = stringResource(textRes).uppercase(),
        style = OpenCodeTypography.caption.copy(letterSpacing = 0.6.sp),
        color = colors.onSurfaceFaint,
        modifier = Modifier.padding(top = spacing.xl, bottom = spacing.xs),
    )
}

@Composable
private fun ModelRow(model: StatsModelUsage) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    // A row the breakdown cannot label must not vanish while the header
    // total still counts it (77-F2): fall back to an honest "unknown"
    // label so the breakdown always reconciles to the total.
    val label = statsModelLabel(model.model) ?: stringResource(R.string.stats_model_unknown)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.body,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(
                R.string.stats_model_detail,
                model.steps,
                model.tokens.totalTokens()?.let { formatCompactTokens(it) } ?: "—",
                formatStatsCost(model.cost),
            ),
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = spacing.xxs),
        )
    }
}

@Composable
private fun ToolRow(tool: StatsToolUsage) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
    ) {
        Text(
            text = tool.name,
            style = OpenCodeTypography.body,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(
                R.string.stats_tool_detail,
                tool.calls,
                tool.succeeded,
                tool.failed,
                tool.unfinished,
            ),
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = spacing.xxs),
        )
    }
}
