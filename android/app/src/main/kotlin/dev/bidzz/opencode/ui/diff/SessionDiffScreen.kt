package dev.bidzz.opencode.ui.diff

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.message

/**
 * Latest turn's file diffs, reusing the working-tree list and file views.
 *
 * The list and the file view are the same screens the Files flow uses — the
 * rows are the same `VcsFileStatus` rows, the patch view is the same
 * `DiffScreen` — so a turn diff reads exactly like a working-tree diff.
 * Only the source differs: this list comes from the session, not the tree,
 * so the empty text says so instead of repeating the working-tree wording.
 */
@Composable
fun SessionDiffScreen(
    state: SessionDiffUiState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCloseFile: () -> Unit,
) {
    val selected = state.selected
    if (selected != null) {
        DiffScreen(state = selected, onBack = onCloseFile)
        return
    }
    when {
        state.loading -> CenteredText(stringResource(R.string.diff_loading))
        state.failure != null -> CenteredText(state.failure.message())
        state.failureKey != null -> CenteredText(stringResource(state.failureKey))
        state.files.isEmpty() -> CenteredText(stringResource(R.string.sessiondiff_empty))
        else -> DiffListScreen(
            changed = state.changed,
            onBack = onBack,
            onOpen = onOpen,
        )
    }
}

@Composable
private fun CenteredText(text: String) {
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
