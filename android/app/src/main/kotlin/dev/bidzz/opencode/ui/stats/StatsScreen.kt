package dev.bidzz.opencode.ui.stats

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.SessionStatsInfo
import dev.bidzz.opencode.core.api.dto.StatsModelUsage
import dev.bidzz.opencode.core.api.dto.StatsToolUsage
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.message

/**
 * Aggregate session statistics, for the engine's default range.
 *
 * Read-only: the totals come from `GET /api/experimental/session/stats`.
 * The endpoint is experimental, so a failure shows the empty state
 * instead of an error — the shape may change without notice, and an
 * empty stats page is information, not a broken screen.
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
                state.loading -> CenteredMessage(stringResource(R.string.common_loading))
                state.failure != null || state.stats == null ->
                    CenteredMessage(stringResource(R.string.stats_empty))
                else -> StatsContent(stats = state.stats)
            }
        }
    }
}

@Composable
private fun StatsContent(stats: SessionStatsInfo) {
    val spacing = OpenCodeTheme.tokens.spacing

    StatRow(labelRes = R.string.stats_field_sessions, value = stats.sessions.toString())
    StatRow(labelRes = R.string.stats_field_prompts, value = stats.prompts.toString())
    StatRow(labelRes = R.string.stats_field_steps, value = stats.steps.toString())
    StatRow(labelRes = R.string.stats_field_subagents, value = stats.subagents.toString())
    StatRow(
        labelRes = R.string.stats_field_tokens,
        value = stats.tokens?.totalTokens()?.toString() ?: "0",
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
        stats.tools.usage
            .sortedByDescending { it.calls }
            .take(10)
            .forEachIndexed { index, tool ->
                if (index > 0) RowDivider(inset = spacing.md)
                ToolRow(tool = tool)
            }
    } else {
        stats.tools.totals?.let { totals ->
            SectionHeader(textRes = R.string.stats_section_tools)
            StatRow(
                labelRes = R.string.stats_field_tool_calls,
                value = totals.calls.toString(),
            )
            StatRow(
                labelRes = R.string.stats_field_tool_failed,
                value = totals.failed.toString(),
            )
        }
    }
}

/** One totals row: label on the left, value on the right. */
@Composable
private fun StatRow(labelRes: Int, value: String) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
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
    val label = statsModelLabel(model.model) ?: return
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
                model.tokens?.totalTokens() ?: 0L,
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
                tool.failed,
            ),
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = spacing.xxs),
        )
    }
}

@Composable
private fun CenteredMessage(text: String) {
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
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
    }
}
