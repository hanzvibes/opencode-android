package dev.bidzz.opencode.ui.config

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
import dev.bidzz.opencode.core.api.dto.ConfigEntry
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.message

/**
 * The engine's configuration documents, lowest to highest priority.
 *
 * Read-only: the list comes from `GET /api/config`. Each document shows
 * its path and the key settings it sets; a searched directory shows its
 * path and nothing else — it holds no document. Editing config from the
 * app is not available yet.
 */
@Composable
fun ConfigScreen(
    state: ConfigUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    LaunchedEffect(Unit) { onRefresh() }

    ScreenScaffold(
        title = stringResource(R.string.config_title),
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
                text = stringResource(R.string.config_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(
                    horizontal = spacing.lg,
                    vertical = spacing.sm,
                ),
            )
            when {
                state.loading -> EmptyState(stringResource(R.string.common_loading))
                state.failure != null -> EmptyState(state.failure.message(), isError = true)
                state.entries.isEmpty() -> EmptyState(stringResource(R.string.config_empty))
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.lg),
                    ) {
                        itemsIndexed(
                            state.entries,
                            // Two documents can share a path display (a
                            // document without a path), and two directories
                            // cannot — the index keeps keys unique either way.
                            key = { index, entry -> "$index:${entry.type}:${entry.path.orEmpty()}" },
                        ) { index, entry ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            ConfigEntryCard(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigEntryCard(entry: ConfigEntry) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val path = entry.displayPath()
    val info = entry.info
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
    ) {
        if (path != null) {
            Text(
                text = path,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (entry.type == "directory") {
            Text(
                text = stringResource(R.string.config_directory),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        if (info != null) {
            ConfigField(
                label = stringResource(R.string.config_field_model),
                value = configModelLabel(info.model),
            )
            ConfigField(
                label = stringResource(R.string.config_field_agent),
                value = info.default_agent?.takeIf { it.isNotBlank() },
            )
            ConfigField(
                label = stringResource(R.string.config_field_shell),
                value = info.shell?.takeIf { it.isNotBlank() },
            )
            ConfigField(
                label = stringResource(R.string.config_field_timezone),
                value = info.timezone?.takeIf { it.isNotBlank() },
            )
            ConfigField(
                label = stringResource(R.string.config_field_username),
                value = info.username?.takeIf { it.isNotBlank() },
            )
        }
    }
}

/** One key setting of a config document; hides when the document does not set it. */
@Composable
private fun ConfigField(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.xs),
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.caption,
            color = colors.onSurfaceFaint,
        )
        Text(
            text = value,
            style = OpenCodeTypography.body,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
