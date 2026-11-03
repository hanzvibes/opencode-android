package dev.bidzz.opencode.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.TextAction

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

    val atRoot = state.currentPath.isEmpty()
    ScreenScaffold(
        title = "Files",
        // Inside a folder the arrow goes up one level; only at the root does it leave the screen.
        onBack = { if (atRoot) onBack() else onUp() },
        backLabel = if (atRoot) "Back" else "Up one folder",
        actions = {
            if (state.changed.isNotEmpty()) TextAction("Diff (${state.changed.size})", onDiff)
            TextAction("Refresh", onRefresh)
        },
    ) {
        if (state.breadcrumbs.isNotEmpty()) {
            Breadcrumbs(state.breadcrumbs, onBreadcrumb)
        }

        state.vcsNote?.let { note ->
            Text(
                text = note,
                style = OpenCodeTypography.label,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
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
        TextAction("root", { onBreadcrumb("") })
        crumbs.forEach { crumb ->
            Text("/", style = OpenCodeTypography.label, color = colors.onSurfaceFaint)
            TextAction(crumb.label, { onBreadcrumb(crumb.path) })
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
        // Folder vs file is the one distinction that matters when scanning, so it is an icon.
        GlyphIcon(
            glyph = if (row.isDirectory) Glyph.Folder else Glyph.File,
            tint = if (row.isDirectory) colors.accent else colors.onSurfaceMuted,
            size = 20.dp,
            modifier = Modifier.padding(end = spacing.md),
        )
        Text(
            text = row.name,
            style = OpenCodeTypography.body,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (row.isDirectory) {
            GlyphIcon(Glyph.ChevronRight, tint = colors.onSurfaceFaint, size = 18.dp)
        }
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
