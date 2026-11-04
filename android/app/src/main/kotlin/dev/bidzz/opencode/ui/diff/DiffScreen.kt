package dev.bidzz.opencode.ui.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.message
import dev.bidzz.opencode.ui.files.DiffUiState

/**
 * Unified diff viewer.
 *
 * Line numbers sit in a fixed gutter so they stay aligned while the code scrolls
 * horizontally, which is the only way a diff of long lines stays readable on a
 * phone. Added and removed lines are distinguished by background, not by colour
 * alone, so the diff survives a colour-vision difference.
 */
@Composable
fun DiffScreen(
    state: DiffUiState,
    onBack: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    ScreenScaffold(
        title = state.file.substringAfterLast('/'),
        onBack = onBack,
        subtitle = if (state.hunks.isNotEmpty()) "+${state.additions} −${state.deletions}" else null,
    ) {
        when {
            state.loading -> Centered(stringResource(R.string.diff_loading))
            state.gitUnavailable -> Centered(stringResource(R.string.diff_git_unavailable))
            state.failure != null -> Centered(state.failure.message())
            state.failureKey != null -> Centered(stringResource(state.failureKey))
            state.hunks.isEmpty() -> Centered(stringResource(R.string.diff_none))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = spacing.sm),
            ) {
                state.hunks.forEach { hunk ->
                    item(key = "hunk-${hunk.header}") {
                        Text(
                            text = hunk.header,
                            style = OpenCodeTypography.code,
                            color = colors.onSurfaceMuted,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surfaceSunken)
                                .padding(horizontal = spacing.md, vertical = spacing.xs),
                        )
                    }
                    items(hunk.lines) { line ->
                        DiffLineRow(line)
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
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    val background = when (line.kind) {
        DiffLine.Kind.ADDED -> colors.success.copy(alpha = 0.12f)
        DiffLine.Kind.REMOVED -> colors.danger.copy(alpha = 0.12f)
        DiffLine.Kind.HUNK_HEADER -> colors.surfaceSunken
        else -> colors.background
    }
    // A prefix glyph means the change is legible without relying on colour.
    val marker = when (line.kind) {
        DiffLine.Kind.ADDED -> "+"
        DiffLine.Kind.REMOVED -> "−"
        else -> " "
    }
    val markerColor = when (line.kind) {
        DiffLine.Kind.ADDED -> colors.success
        DiffLine.Kind.REMOVED -> colors.danger
        else -> colors.onSurfaceFaint
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background),
    ) {
        // Fixed gutters: numbers must not move when the code scrolls.
        Text(
            text = line.oldLine?.toString() ?: "",
            style = OpenCodeTypography.code,
            color = colors.onSurfaceFaint,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .width(spacing.xxxl)
                .padding(end = spacing.xs),
        )
        Text(
            text = line.newLine?.toString() ?: "",
            style = OpenCodeTypography.code,
            color = colors.onSurfaceFaint,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .width(spacing.xxxl)
                .padding(end = spacing.xs),
        )
        Text(
            text = marker,
            style = OpenCodeTypography.code,
            color = markerColor,
            modifier = Modifier.width(spacing.sm),
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Text(
                text = line.text,
                style = OpenCodeTypography.code,
                color = colors.onSurface,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.padding(end = spacing.md),
            )
        }
    }
}

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
