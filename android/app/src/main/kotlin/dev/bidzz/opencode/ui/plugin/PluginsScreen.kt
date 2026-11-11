package dev.bidzz.opencode.ui.plugin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.PluginInfo
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.StatusDot
import dev.bidzz.opencode.ui.message

/**
 * Server plugins enabled on the engine, with their status.
 *
 * Read-only: the list comes from `GET /api/plugin`. A failed plugin shows
 * the engine's error text under its name; checking for updates and
 * updating are not available from the app yet.
 */
@Composable
fun PluginsScreen(
    state: PluginsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    LaunchedEffect(Unit) { onRefresh() }

    ScreenScaffold(
        title = stringResource(R.string.plugin_title),
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
                text = stringResource(R.string.plugin_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(
                    horizontal = spacing.lg,
                    vertical = spacing.sm,
                ),
            )
            when {
                state.loading -> CenteredMessage(stringResource(R.string.common_loading))
                state.failure != null -> CenteredMessage(state.failure.message(), isError = true)
                state.plugins.isEmpty() -> CenteredMessage(stringResource(R.string.plugin_empty))
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.lg),
                    ) {
                        itemsIndexed(
                            state.plugins,
                            // The display name is not a key: two built-in
                            // plugins can both read "Built-in" (no id, same
                            // source), and duplicate LazyColumn keys crash.
                            key = { index, plugin -> "$index:${plugin.displayName()}" },
                        ) { index, plugin ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            PluginRow(plugin = plugin)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginRow(plugin: PluginInfo) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val kind = plugin.stateKind()
    val statusText = pluginStatusWord(kind)
    val dotColor = when (kind) {
        PluginStateKind.ACTIVE -> colors.success
        PluginStateKind.FAILED -> colors.danger
        PluginStateKind.UNKNOWN -> colors.onSurfaceMuted
    }
    // The id is the key the config uses; the source says where the plugin
    // came from, so it stays visible underneath.
    val title = plugin.displayName()
    val sourceLabel = pluginSourceLabel(plugin.source)
    val features = plugin.featureWords().joinToString(" · ")
    val error = plugin.state.error?.takeIf { it.isNotBlank() }

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
                text = title,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (features.isEmpty()) sourceLabel else "$sourceLabel · $features",
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (error != null && kind == PluginStateKind.FAILED) {
                Text(
                    text = error,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.danger,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = spacing.xxs),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = spacing.xs),
        ) {
            StatusDot(color = dotColor, size = 7.dp, modifier = Modifier.padding(end = spacing.xs))
            Text(
                text = statusText,
                style = OpenCodeTypography.label,
                color = dotColor,
            )
        }
    }
}

@Composable
private fun pluginStatusWord(kind: PluginStateKind): String = stringResource(
    when (kind) {
        PluginStateKind.ACTIVE -> R.string.plugin_status_active
        PluginStateKind.FAILED -> R.string.plugin_status_failed
        PluginStateKind.UNKNOWN -> R.string.plugin_status_unknown
    },
)

@Composable
private fun CenteredMessage(text: String, isError: Boolean = false) {
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
            color = if (isError) colors.danger else colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
    }
}
