package dev.bidzz.opencode.ui.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.UsageBudget
import dev.bidzz.opencode.core.api.dto.UsageLimitsInfo
import dev.bidzz.opencode.core.api.dto.UsageMeter
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.stats.formatStatsCost
import kotlin.math.roundToInt

/**
 * Usage limits for the Console workspace the engine is signed in to.
 *
 * Read-only: the Go allowance meters and the Console budgets come from
 * `GET /api/experimental/usage/limits`. A workspace that is not signed
 * in has no limits — the endpoint answers `data: null` — so the screen
 * shows the empty state, exactly as it does when the call fails: the
 * endpoint is experimental, and neither case is an error the user can
 * act on.
 */
@Composable
fun UsageLimitsScreen(
    state: UsageLimitsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    LaunchedEffect(Unit) { onRefresh() }

    ScreenScaffold(
        title = stringResource(R.string.usage_title),
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
                text = stringResource(R.string.usage_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(bottom = spacing.sm),
            )
            when {
                state.loading -> LoadingState(stringResource(R.string.common_loading), onRetry = onRefresh)
                state.info == null -> EmptyState(stringResource(R.string.usage_empty))
                else -> UsageContent(info = state.info)
            }
        }
    }
}

@Composable
private fun UsageContent(info: UsageLimitsInfo) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val workspace = info.workspace

    if (workspace != null) {
        Text(
            text = workspace.name?.takeIf { it.isNotBlank() } ?: workspace.id,
            style = OpenCodeTypography.body,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = spacing.sm),
        )
    }

    val go = info.go
    if (go != null) {
        SectionHeader(textRes = R.string.usage_section_go)
        go.rolling?.let { MeterRow(labelRes = R.string.usage_meter_rolling, meter = it) }
        go.weekly?.let { MeterRow(labelRes = R.string.usage_meter_weekly, meter = it) }
        go.monthly?.let { MeterRow(labelRes = R.string.usage_meter_monthly, meter = it) }
    }

    if (info.budgets.isNotEmpty()) {
        SectionHeader(textRes = R.string.usage_section_budgets)
        info.budgets.forEachIndexed { index, budget ->
            if (index > 0) RowDivider(inset = spacing.md)
            BudgetRow(budget = budget)
        }
    }
}

/** One Go allowance window: label, percent bar, limited mark, reset date. */
@Composable
private fun MeterRow(labelRes: Int, meter: UsageMeter) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(labelRes),
                style = OpenCodeTypography.body,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (meter.limited) {
                Text(
                    text = stringResource(R.string.usage_limited),
                    style = OpenCodeTypography.label,
                    color = colors.danger,
                    modifier = Modifier.padding(end = spacing.sm),
                )
            }
            Text(
                // Rounded, like the bar below it (77-F6): truncation
                // showed a 99.9% meter as "99%".
                text = "${meter.percent.roundToInt()}%",
                style = OpenCodeTypography.body,
                color = if (meter.limited) colors.danger else colors.onSurfaceMuted,
            )
        }
        MeterBar(
            fraction = meter.fraction(),
            danger = meter.limited,
            modifier = Modifier.padding(top = spacing.xs),
        )
        formatResetDate(meter.resetsAt)?.let { date ->
            Text(
                text = stringResource(R.string.usage_resets, date),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceFaint,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
    }
}

/** One Console budget: spent against its limit, scope words, reset date. */
@Composable
private fun BudgetRow(budget: UsageBudget) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
    ) {
        Text(
            text = budget.id,
            style = OpenCodeTypography.body,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(
                R.string.usage_budget_spent,
                formatStatsCost(budget.spent),
                formatStatsCost(budget.limit),
            ),
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = spacing.xxs),
        )
        MeterBar(
            fraction = budget.spentFraction(),
            danger = budget.spent >= budget.limit && budget.limit > 0,
            modifier = Modifier.padding(top = spacing.xs),
        )
        // Per-model spend inside one budget (77-F6): the engine sends it
        // (`budget.models`) and the screen never rendered it, so a combined
        // budget never said which model ate it.
        budget.models.forEach { model ->
            val label = model.name.takeIf { it.isNotBlank() } ?: model.modelID
            if (label.isBlank()) return@forEach
            val amount = model.spent?.let { formatStatsCost(it) }
            Text(
                text = if (amount != null) "$label · $amount" else label,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        val words = buildList {
            add(usageScopeWord(budget.scope))
            add(usagePerWord(budget.per))
            add(usageCoverageWord(budget.coverage))
            if (budget.shared) add(stringResource(R.string.usage_budget_shared))
        }.filter { it.isNotBlank() }
        if (words.isNotEmpty()) {
            Text(
                text = words.joinToString(" · "),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceFaint,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        formatResetDate(budget.resetsAt)?.let { date ->
            Text(
                text = stringResource(R.string.usage_resets, date),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceFaint,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
    }
}

@Composable
private fun usageScopeWord(scope: String): String = when (scope.lowercase()) {
    "each" -> stringResource(R.string.usage_scope_each)
    "combined" -> stringResource(R.string.usage_scope_combined)
    else -> scope
}

@Composable
private fun usagePerWord(per: String): String = when (per.lowercase()) {
    "set" -> stringResource(R.string.usage_per_set)
    "model" -> stringResource(R.string.usage_per_model)
    else -> per
}

@Composable
private fun usageCoverageWord(coverage: String): String = when (coverage.lowercase()) {
    "all" -> stringResource(R.string.usage_coverage_all)
    "models" -> stringResource(R.string.usage_coverage_models)
    else -> coverage
}

/** A thin percent bar: neutral track, accent fill, danger when limited/over. */
@Composable
private fun MeterBar(fraction: Float, danger: Boolean, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val radius = OpenCodeTheme.tokens.radius
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(radius.sm))
            .background(colors.fill),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(radius.sm))
                .background(if (danger) colors.danger else colors.accent),
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
