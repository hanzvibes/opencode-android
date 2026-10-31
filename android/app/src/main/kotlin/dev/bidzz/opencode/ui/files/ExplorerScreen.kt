package dev.bidzz.opencode.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Project file browser.
 *
 * Files the agent changed are marked inline with their diff counts, so the
 * listing answers "what did it touch" without a second screen. Directories sort
 * first, which is the ordering every file manager uses.
 */
@Composable
fun ExplorerScreen(
    state: ExplorerUiState,
    onBack: () -> Unit,
    onOpenDirectory: (FileRow) -> Unit,
    onOpenFile: (FileRow) -> Unit,
    onBreadcrumb: (String) -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onDiff: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderAction(if (state.currentPath.isEmpty()) "Back" else "Up") { if (state.currentPath.isEmpty()) onBack() else onUp() }
            Text(
                text = "Files",
                style = OpenCodeTypography.title,
                color = colors.onSurface,
                modifier = Modifier.padding(start = spacing.lg),
            )
            Box(Modifier.weight(1f))
            if (state.changed.isNotEmpty()) {
                HeaderAction("Diff (${state.changed.size})") { onDiff() }
            }
            HeaderAction("Refresh", Modifier.padding(start = spacing.sm)) { onRefresh() }
        }

        if (state.breadcrumbs.isNotEmpty()) {
            Breadcrumbs(state.breadcrumbs, onBreadcrumb)
        }

        state.vcsNote?.let { note ->
            Text(
                text = note,
                style = OpenCodeTypography.label,
                color = colors.onSurfaceFaint,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
            )
        }

        when {
            state.loading -> Centered("Loading…")
            state.error != null -> Centered(state.error, isError = true)
            state.rows.isEmpty() -> Centered("This folder is empty.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = spacing.sm, vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                items(state.rows, key = { it.path }) { row ->
                    FileRowItem(
                        row = row,
                        onClick = { if (row.isDirectory) onOpenDirectory(row) else onOpenFile(row) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(crumbs: List<ExplorerUiState.Crumb>, onBreadcrumb: (String) -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderAction("root") { onBreadcrumb("") }
        crumbs.forEach { crumb ->
            Text("/", style = OpenCodeTypography.label, color = colors.onSurfaceFaint)
            HeaderAction(crumb.label) { onBreadcrumb(crumb.path) }
        }
    }
}

@Composable
private fun FileRowItem(row: FileRow, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A leading glyph rather than an icon font: no asset to ship, and the
        // distinction that matters (folder vs file) is unambiguous.
        Text(
            text = if (row.isDirectory) "/" else "·",
            style = OpenCodeTypography.code,
            color = colors.onSurfaceFaint,
            modifier = Modifier.width(spacing.lg),
        )
        Text(
            text = row.name,
            style = OpenCodeTypography.body,
            color = if (row.isDirectory) colors.onSurface else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (row.gitStatus != null) {
            Text(
                text = row.gitStatus.first().uppercase(),
                style = OpenCodeTypography.label,
                color = when (row.gitStatus) {
                    "added" -> colors.success
                    "deleted" -> colors.danger
                    else -> colors.warning
                },
            )
            if (row.additions > 0 || row.deletions > 0) {
                Text(
                    text = " +${row.additions} −${row.deletions}",
                    style = OpenCodeTypography.label,
                    color = colors.onSurfaceFaint,
                )
            }
        }
    }
}

@Composable
private fun HeaderAction(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
    ) {
        Text(text, style = OpenCodeTypography.label, color = colors.accent)
    }
}

@Composable
private fun Centered(text: String, isError: Boolean = false) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = OpenCodeTypography.body,
            color = if (isError) colors.danger else colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(spacing.xxl),
        )
    }
}
