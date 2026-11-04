package dev.bidzz.opencode.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.IconBadge
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.TextAction

/**
 * Project file browser.
 *
 * Files the agent changed are marked inline with their diff counts, so the
 * listing answers "what did it touch" without a second screen. Directories sort
 * first, which is the ordering every file manager uses.
 *
 * The path is a row of chips that scrolls to the folder you are in; the listing is one
 * rounded card so it reads as a single list rather than loose rows.
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
        title = stringResource(R.string.drawer_files),
        // Inside a folder the arrow goes up one level; only at the root does it leave the screen.
        onBack = { if (atRoot) onBack() else onUp() },
        backLabel = if (atRoot) null else stringResource(R.string.files_up),
        actions = {
            if (state.changed.isNotEmpty()) {
                TextAction(stringResource(R.string.files_diff, state.changed.size), onDiff)
            }
            TextAction(stringResource(R.string.common_refresh), onRefresh)
        },
    ) {
        if (state.breadcrumbs.isNotEmpty()) {
            Breadcrumbs(state.breadcrumbs, onBreadcrumb)
        }

        state.vcsNote?.let { note ->
            Text(
                text = stringResource(
                    when (note) {
                        VcsNote.NOT_A_REPOSITORY -> R.string.files_note_not_repo
                        VcsNote.NO_CHANGES -> R.string.files_note_clean
                    },
                ),
                style = OpenCodeTypography.label,
                color = colors.onSurfaceMuted,
                // At the project root there are no chips above, so the note needs room of its own
                // under the header; below the chips they already provide it.
                modifier = Modifier.padding(
                    start = spacing.lg,
                    end = spacing.lg,
                    top = if (state.breadcrumbs.isEmpty()) spacing.lg else spacing.xs,
                    bottom = spacing.sm,
                ),
            )
        }

        when {
            state.loading -> Centered(stringResource(R.string.common_loading))
            state.error != null -> Centered(state.error, isError = true)
            state.rows.isEmpty() -> EmptyFolder()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
            ) {
                itemsIndexed(state.rows, key = { _, row -> row.path }) { index, row ->
                    FileRowItem(
                        row = row,
                        isFirst = index == 0,
                        isLast = index == state.rows.lastIndex,
                        onClick = { if (row.isDirectory) onOpenDirectory(row) else onOpenFile(row) },
                    )
                }
            }
        }
    }
}

/** root / folder / subfolder as chips; the last one, where you are, is highlighted. */
@Composable
private fun Breadcrumbs(crumbs: List<ExplorerUiState.Crumb>, onBreadcrumb: (String) -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val scroll = rememberScrollState()

    // A deep path is wider than the screen; keep the folder you are in visible. One frame
    // first, because the scroll range is only known after the chips have been measured.
    LaunchedEffect(crumbs) {
        withFrameNanos { }
        scroll.scrollTo(scroll.maxValue)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crumb(label = stringResource(R.string.files_root), current = false, glyph = Glyph.Folder) { onBreadcrumb("") }
        crumbs.forEachIndexed { index, crumb ->
            GlyphIcon(Glyph.ChevronRight, tint = colors.onSurfaceFaint, size = 16.dp)
            Crumb(label = crumb.label, current = index == crumbs.lastIndex) { onBreadcrumb(crumb.path) }
        }
    }
}

@Composable
private fun Crumb(label: String, current: Boolean, glyph: Glyph? = null, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    // The tap area is 44dp tall; the chip drawn inside it is smaller.
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.full))
                .background(if (current) colors.accent.copy(alpha = 0.16f) else colors.surfaceSunken)
                .padding(horizontal = spacing.md, vertical = spacing.xs + spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) {
                GlyphIcon(glyph, tint = if (current) colors.accent else colors.onSurfaceMuted, size = 14.dp)
            }
            Text(
                text = label,
                style = OpenCodeTypography.label,
                color = if (current) colors.accent else colors.onSurfaceMuted,
                maxLines = 1,
                modifier = Modifier.padding(start = if (glyph != null) spacing.xs else 0.dp),
            )
        }
    }
}

/** One row of the listing. First and last round the card's corners; the rest are square. */
@Composable
private fun FileRowItem(row: FileRow, isFirst: Boolean, isLast: Boolean, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val r = OpenCodeTheme.tokens.radius.lg
    val shape = RoundedCornerShape(
        topStart = if (isFirst) r else 0.dp,
        topEnd = if (isFirst) r else 0.dp,
        bottomStart = if (isLast) r else 0.dp,
        bottomEnd = if (isLast) r else 0.dp,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface),
    ) {
        if (!isFirst) RowDivider(inset = spacing.md + 32.dp + spacing.md)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Folder vs file is the one distinction that matters when scanning, so it is the mark.
            IconBadge(
                glyph = if (row.isDirectory) Glyph.Folder else Glyph.File,
                tint = if (row.isDirectory) colors.accent else colors.onSurfaceMuted,
                size = 32.dp,
            )
            Text(
                text = row.name,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.md),
            )
            if (row.gitStatus != null) {
                if (row.additions > 0 || row.deletions > 0) {
                    Text(
                        text = "+${row.additions} −${row.deletions}",
                        style = OpenCodeTypography.label,
                        color = colors.onSurfaceFaint,
                        modifier = Modifier.padding(start = spacing.sm),
                    )
                }
                GitBadge(row.gitStatus, Modifier.padding(start = spacing.sm))
            }
            if (row.isDirectory) {
                GlyphIcon(
                    Glyph.ChevronRight,
                    tint = colors.onSurfaceFaint,
                    size = 18.dp,
                    modifier = Modifier.padding(start = spacing.sm),
                )
            }
        }
    }
}

/**
 * A, M or D on a tinted square. The letter carries the meaning and the colour backs it up,
 * so it still reads for someone who cannot tell the colours apart.
 */
@Composable
private fun GitBadge(status: String, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val tint: Color = when (status) {
        "added" -> colors.success
        "deleted" -> colors.danger
        else -> colors.warning
    }
    val spoken = stringResource(
        when (status) {
            "added" -> R.string.files_status_added
            "deleted" -> R.string.files_status_deleted
            else -> R.string.files_status_modified
        },
    )
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.xs))
            .background(tint.copy(alpha = 0.16f))
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = status.first().uppercase(),
            style = OpenCodeTypography.label,
            color = tint,
        )
    }
}

@Composable
private fun EmptyFolder() {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        GlyphIcon(Glyph.Folder, tint = colors.onSurfaceFaint, size = 40.dp)
        Text(
            text = stringResource(R.string.files_empty),
            style = OpenCodeTypography.body,
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.md),
        )
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
