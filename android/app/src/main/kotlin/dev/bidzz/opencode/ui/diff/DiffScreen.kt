package dev.bidzz.opencode.ui.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeColors
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.files.DiffUiState
import dev.bidzz.opencode.ui.message

enum class DiffViewMode { Unified, Split }

/**
 * Code diff viewer with Side-by-side or Unified layout.
 *
 * Line highlights match Git and Claude Code styling:
 * - Green tinted background and solid left border for added lines (+).
 * - Red tinted background and solid left border for removed lines (−).
 * - Monospace line gutters that remain aligned during horizontal code scrolling.
 */
@Composable
fun DiffScreen(
    state: DiffUiState,
    onBack: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    var mode by rememberSaveable { mutableStateOf(DiffViewMode.Unified) }

    ScreenScaffold(
        title = state.file.substringAfterLast('/'),
        onBack = onBack,
        subtitle = if (state.hunks.isNotEmpty()) "+${state.additions} −${state.deletions}" else null,
        subtitleColor = if (state.hunks.isNotEmpty()) colors.accent else null,
        actions = {
            if (state.hunks.isNotEmpty()) {
                DiffModeSelector(
                    current = mode,
                    onSelect = { mode = it },
                )
            }
        },
    ) {
        when {
            state.loading -> Centered(stringResource(R.string.diff_loading))
            state.gitUnavailable -> Centered(stringResource(R.string.diff_git_unavailable))
            state.failure != null -> Centered(state.failure.message())
            state.failureKey != null -> Centered(stringResource(state.failureKey))
            state.hunks.isEmpty() -> Centered(stringResource(R.string.diff_none))
            mode == DiffViewMode.Unified -> UnifiedDiffView(state = state)
            mode == DiffViewMode.Split -> SplitDiffView(state = state)
        }
    }
}

@Composable
private fun DiffModeSelector(
    current: DiffViewMode,
    onSelect: (DiffViewMode) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius.full

    Row(
        modifier = Modifier
            .padding(end = spacing.xs)
            .clip(RoundedCornerShape(radius))
            .background(colors.fill)
            .border(0.5.dp, colors.hairline, RoundedCornerShape(radius))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeOptionChip(
            label = stringResource(R.string.diff_mode_unified),
            selected = current == DiffViewMode.Unified,
            onClick = { onSelect(DiffViewMode.Unified) },
        )
        ModeOptionChip(
            label = stringResource(R.string.diff_mode_split),
            selected = current == DiffViewMode.Split,
            onClick = { onSelect(DiffViewMode.Split) },
        )
    }
}

@Composable
private fun ModeOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius.full

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(radius))
            .background(if (selected) colors.surfaceRaised else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.sm + 2.dp, vertical = spacing.xxs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.caption.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) colors.accent else colors.onSurfaceMuted,
        )
    }
}

/** Unified diff view: old/new gutters with single-column code flow. */
@Composable
private fun UnifiedDiffView(state: DiffUiState) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.xxl),
    ) {
        state.hunks.forEachIndexed { index, hunk ->
            item(key = "hunk-$index-${hunk.header}") {
                HunkHeaderRow(hunk.header)
            }
            items(hunk.lines) { line ->
                UnifiedDiffLineRow(line, colors)
            }
        }
        if (state.truncated) {
            item(key = "truncated") {
                Text(
                    text = stringResource(R.string.diff_truncated),
                    style = OpenCodeTypography.label,
                    color = colors.onSurfaceFaint,
                    modifier = Modifier.padding(spacing.md),
                )
            }
        }
    }
}

@Composable
private fun HunkHeaderRow(header: String) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceSunken)
            .border(width = 0.5.dp, color = colors.hairline)
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = header,
            style = OpenCodeTypography.code.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun UnifiedDiffLineRow(line: DiffLine, colors: OpenCodeColors) {
    val spacing = OpenCodeTheme.tokens.spacing

    val (bg, stripe, markerColor, marker) = when (line.kind) {
        DiffLine.Kind.ADDED -> Quad(
            colors.diffAddedBg,
            colors.diffAdded,
            colors.diffAdded,
            "+",
        )
        DiffLine.Kind.REMOVED -> Quad(
            colors.diffRemovedBg,
            colors.diffRemoved,
            colors.diffRemoved,
            "−",
        )
        DiffLine.Kind.META -> Quad(
            colors.surfaceSunken.copy(alpha = 0.5f),
            Color.Transparent,
            colors.onSurfaceFaint,
            "\\",
        )
        else -> Quad(
            Color.Transparent,
            Color.Transparent,
            colors.onSurfaceFaint,
            " ",
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Claude Code style solid edge bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .background(stripe),
        )

        // Old line number gutter
        Text(
            text = line.oldLine?.toString() ?: "",
            style = OpenCodeTypography.code.copy(fontSize = 11.sp),
            color = if (line.kind == DiffLine.Kind.REMOVED) colors.diffRemoved.copy(alpha = 0.9f) else colors.onSurfaceFaint,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .width(36.dp)
                .padding(end = 4.dp),
        )

        // New line number gutter
        Text(
            text = line.newLine?.toString() ?: "",
            style = OpenCodeTypography.code.copy(fontSize = 11.sp),
            color = if (line.kind == DiffLine.Kind.ADDED) colors.success.copy(alpha = 0.9f) else colors.onSurfaceFaint,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .width(36.dp)
                .padding(end = 4.dp),
        )

        // Change marker (+ / −)
        Text(
            text = marker,
            style = OpenCodeTypography.code.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
            color = markerColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(16.dp),
        )

        // Code content with independent horizontal scroll
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                text = line.text,
                style = OpenCodeTypography.code.copy(fontSize = 12.sp),
                color = colors.onSurface,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.padding(end = spacing.md),
            )
        }
    }
}

/** Side-by-side (Split) diff view: Old code on Left, New code on Right. */
@Composable
private fun SplitDiffView(state: DiffUiState) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val horizontalScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(horizontalScroll),
    ) {
        // Split Column Headers
        Row(
            modifier = Modifier
                .width(720.dp)
                .background(colors.surfaceSunken)
                .border(0.5.dp, colors.hairline)
                .padding(horizontal = spacing.md, vertical = spacing.xs),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.diff_original),
                    style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.danger,
                )
                Text(
                    text = "−${state.deletions}",
                    style = OpenCodeTypography.caption,
                    color = colors.danger,
                )
            }
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(colors.hairline)
                    .padding(horizontal = spacing.xs),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.diff_modified),
                    style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.success,
                )
                Text(
                    text = "+${state.additions}",
                    style = OpenCodeTypography.caption,
                    color = colors.success,
                )
            }
        }

        // Lazy hunks list for split view
        LazyColumn(
            modifier = Modifier
                .width(720.dp)
                .weight(1f),
            contentPadding = PaddingValues(bottom = spacing.xxl),
        ) {
            state.hunks.forEachIndexed { index, hunk ->
                item(key = "hunk-header-$index-${hunk.header}") {
                    HunkHeaderRow(hunk.header)
                }
                val pairedRows = DiffParser.toSideBySide(hunk)
                items(pairedRows) { pair ->
                    SplitDiffRow(pair, colors)
                }
            }
        }
    }
}

@Composable
private fun SplitDiffRow(pair: DiffSideBySideRow, colors: OpenCodeColors) {
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .width(720.dp)
            .height(IntrinsicSize.Min)
            .border(width = 0.25.dp, color = colors.hairline.copy(alpha = 0.5f)),
    ) {
        // Left column (Original / Removed)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    when (pair.left?.kind) {
                        DiffLine.Kind.REMOVED -> colors.diffRemovedBg
                        null -> colors.surfaceSunken.copy(alpha = 0.3f)
                        else -> Color.Transparent
                    },
                ),
        ) {
            if (pair.left != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Accent stripe
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(if (pair.left.kind == DiffLine.Kind.REMOVED) colors.diffRemoved else Color.Transparent),
                    )
                    Text(
                        text = pair.left.oldLine?.toString() ?: "",
                        style = OpenCodeTypography.code.copy(fontSize = 11.sp),
                        color = if (pair.left.kind == DiffLine.Kind.REMOVED) colors.diffRemoved else colors.onSurfaceFaint,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .width(34.dp)
                            .padding(end = 4.dp),
                    )
                    Text(
                        text = if (pair.left.kind == DiffLine.Kind.REMOVED) "−" else " ",
                        style = OpenCodeTypography.code.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        color = if (pair.left.kind == DiffLine.Kind.REMOVED) colors.diffRemoved else colors.onSurfaceFaint,
                        modifier = Modifier.width(14.dp),
                    )
                    Text(
                        text = pair.left.text,
                        style = OpenCodeTypography.code.copy(fontSize = 12.sp),
                        color = colors.onSurface,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.padding(end = spacing.xs),
                    )
                }
            }
        }

        // Column Divider
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(colors.hairline),
        )

        // Right column (Modified / Added)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    when (pair.right?.kind) {
                        DiffLine.Kind.ADDED -> colors.diffAddedBg
                        null -> colors.surfaceSunken.copy(alpha = 0.3f)
                        else -> Color.Transparent
                    },
                ),
        ) {
            if (pair.right != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Accent stripe
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(if (pair.right.kind == DiffLine.Kind.ADDED) colors.diffAdded else Color.Transparent),
                    )
                    Text(
                        text = pair.right.newLine?.toString() ?: "",
                        style = OpenCodeTypography.code.copy(fontSize = 11.sp),
                        color = if (pair.right.kind == DiffLine.Kind.ADDED) colors.diffAdded else colors.onSurfaceFaint,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .width(34.dp)
                            .padding(end = 4.dp),
                    )
                    Text(
                        text = if (pair.right.kind == DiffLine.Kind.ADDED) "+" else " ",
                        style = OpenCodeTypography.code.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        color = if (pair.right.kind == DiffLine.Kind.ADDED) colors.diffAdded else colors.onSurfaceFaint,
                        modifier = Modifier.width(14.dp),
                    )
                    Text(
                        text = pair.right.text,
                        style = OpenCodeTypography.code.copy(fontSize = 12.sp),
                        color = colors.onSurface,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.padding(end = spacing.xs),
                    )
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun Centered(text: String) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = OpenCodeTypography.body,
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(spacing.xxl),
        )
    }
}
