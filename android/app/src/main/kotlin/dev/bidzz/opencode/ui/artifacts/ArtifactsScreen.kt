package dev.bidzz.opencode.ui.artifacts

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.ArtifactInfo
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.LocalizedAlertDialog
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.message
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Published artifacts on the engine.
 *
 * The list comes from `GET /api/experimental/artifacts` (newest
 * first). Tapping a row opens its view URL in the in-app browser
 * (with the engine credentials — the view endpoint needs Basic auth
 * like every engine call). Delete removes the artifact and its stored
 * files after a confirm.
 *
 * When artifacts are disabled on the engine (the flag defaults off in
 * releases) the list answers 403: the screen explains how to enable
 * them instead of showing a bare error, because the app cannot flip
 * the flag itself.
 */
@Composable
fun ArtifactsScreen(
    state: ArtifactsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (ArtifactInfo) -> Unit,
    onDelete: (String) -> Unit,
    onClearActionFailure: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<ArtifactInfo?>(null) }

    LaunchedEffect(Unit) { onRefresh() }

    // message() is @Composable, so resolve it here — not inside LaunchedEffect.
    val actionFailureMessage = state.actionFailure?.message()
    LaunchedEffect(state.actionFailure) {
        val message = actionFailureMessage ?: return@LaunchedEffect
        (context.applicationContext as OpenCodeApplication).showSnackbar(message)
        onClearActionFailure()
    }

    ScreenScaffold(
        title = stringResource(R.string.artifact_title),
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
                text = stringResource(R.string.artifact_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(
                    horizontal = spacing.lg,
                    vertical = spacing.sm,
                ),
            )
            if (state.providers.isNotEmpty()) {
                // stringResource is @Composable: resolve it here, not inside joinToString.
                val defaultLabel = stringResource(R.string.artifact_provider_default)
                Text(
                    text = stringResource(
                        R.string.artifact_providers,
                        state.providers.joinToString(", ") { provider ->
                            if (provider.default) "${provider.name} ($defaultLabel)" else provider.name
                        },
                    ),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(
                        horizontal = spacing.lg,
                        vertical = spacing.xxs,
                    ),
                )
            }
            when {
                state.loading -> LoadingState(stringResource(R.string.common_loading), onRetry = onRefresh)
                state.failure != null && state.failure.isArtifactsDisabled() ->
                    EmptyState(stringResource(R.string.artifact_disabled))
                state.failure != null -> EmptyState(state.failure.message(), isError = true)
                state.artifacts.isEmpty() -> EmptyState(stringResource(R.string.artifact_empty))
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.lg),
                    ) {
                        itemsIndexed(state.artifacts, key = { _, artifact -> artifact.id }) { index, artifact ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            ArtifactRow(
                                artifact = artifact,
                                deleting = artifact.id in state.deletingIds,
                                onOpen = { onOpen(artifact) },
                                onDelete = { pendingDelete = artifact },
                            )
                        }
                    }
                }
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        LocalizedAlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(toDelete.id)
                }) {
                    Text(
                        text = stringResource(R.string.artifact_remove_confirm),
                        color = colors.danger,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
            title = { Text(text = stringResource(R.string.artifact_remove_title)) },
            text = { Text(text = stringResource(R.string.artifact_remove_body)) },
        )
    }
}

@Composable
private fun ArtifactRow(
    artifact: ArtifactInfo,
    deleting: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onOpen)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = spacing.xs),
        ) {
            Text(
                text = artifact.title,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.artifact_row_meta,
                    artifact.provider,
                    formatTime(artifact.time.updated),
                ),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artifact.entry,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = onDelete,
            enabled = !deleting,
        ) {
            Text(
                text = stringResource(R.string.artifact_delete),
                color = colors.danger,
            )
        }
    }
}

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault()))
