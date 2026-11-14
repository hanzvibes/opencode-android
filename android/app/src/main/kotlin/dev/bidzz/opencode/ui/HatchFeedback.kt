package dev.bidzz.opencode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.AuraPalette
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Hatch list feedback — clean-room re-implementation of the Meta Muse
 * app's (`com.facebook.aura` 16.0.0.2.159) `HatchPaginationIndicator`,
 * `HatchRowSpinner` and `ErrorState`, from their dex class names in
 * `aura-unpack/ANALYSIS.md`. No Aura code, font, or asset is copied:
 * the spinner is our own [AuraPullRefreshIndicator] ring, the error
 * state is our own centred column with a Hatch danger dot, and the
 * pagination row is a 48dp text action in Aura blue.
 *
 * Behaviour is deliberately unchanged from what these replace:
 * - The session list's trailing "load more" item used to be a bare
 *   `TextButton` ("Load more sessions") or a plain "Loading…" text.
 *   [HatchPaginationIndicator] is that same item, with the Aura ring
 *   while the page loads.
 * - The chat's "load earlier" row used to be a plain "Loading…" text
 *   while the older window loads. It now shows [HatchRowSpinner].
 * - Every screen's full-screen error used to be [EmptyState] with
 *   `isError = true`: danger text + retry button. [EmptyState] now
 *   delegates its error branch to [HatchErrorState], which is that
 *   same text + retry with the danger dot above it.
 */

/** The three states of a paged list footer. Pure so it is unit-testable. */
enum class HatchPaginationState { HAS_MORE, LOADING, END }

/**
 * Which footer a paged list shows. Loading wins over has-more: a page
 * already in flight is not offered again. Pure so it is unit-testable.
 */
internal fun hatchPaginationState(hasMore: Boolean, loadingMore: Boolean): HatchPaginationState =
    when {
        loadingMore -> HatchPaginationState.LOADING
        hasMore -> HatchPaginationState.HAS_MORE
        else -> HatchPaginationState.END
    }

/**
 * The row spinner's ring size. Smaller than the 24dp full-screen ring
 * ([AuraPullRefreshIndicator]'s default): a row spinner sits next to a
 * label, not alone on a screen.
 */
internal val HatchRowSpinnerSize: Dp = 16.dp

/**
 * An inline loading row ([HatchRowSpinner] in Aura): the Aura ring at
 * [HatchRowSpinnerSize] next to an optional label, 48dp tall so the row
 * is a comfortable touch target where it is tappable.
 */
@Composable
fun HatchRowSpinner(
    label: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = HatchRowSpinnerSize,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuraPullRefreshIndicator(progress = null, size = size)
        if (label != null) {
            Text(
                text = label,
                style = OpenCodeTypography.body,
                color = colors.onSurfaceFaint,
                modifier = Modifier.padding(start = spacing.sm),
            )
        }
    }
}

/**
 * A paged list's trailing footer ([HatchPaginationIndicator] in Aura).
 * LOADING shows the row spinner; HAS_MORE shows the "load more" text
 * action in Aura blue (dark keeps `accentText` — the label is text);
 * END renders nothing, so callers keep the footer item only while a
 * next page exists.
 */
@Composable
fun HatchPaginationIndicator(
    hasMore: Boolean,
    loadingMore: Boolean,
    loadMoreLabel: String,
    loadingLabel: String,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val dims = OpenCodeTheme.tokens.dimensions
    when (hatchPaginationState(hasMore, loadingMore)) {
        HatchPaginationState.END -> Unit
        HatchPaginationState.LOADING -> HatchRowSpinner(
            label = loadingLabel,
            modifier = modifier,
        )
        HatchPaginationState.HAS_MORE -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(
                onClick = onLoadMore,
                modifier = Modifier.sizeIn(minHeight = dims.minTouchTarget),
            ) {
                Text(
                    text = loadMoreLabel,
                    style = OpenCodeTypography.body,
                    color = if (colors.isDark) colors.accentText else AuraPalette.auraBlueText,
                )
            }
        }
    }
}

/**
 * A full-screen error ([ErrorState] in Aura): the danger dot above the
 * message in danger text, with the retry button under it — the same
 * shape as [EmptyState]'s error branch, plus the dot. Capped at the
 * 720dp content width like [EmptyState].
 */
@Composable
fun HatchErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    // Audit-200 B1 (M-192): same opt-out as EmptyState/LoadingState — a
    // fillMaxSize child of a scrolling column measures 0dp.
    fillMaxSize: Boolean = true,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Box(
        modifier = if (fillMaxSize) modifier.fillMaxSize() else modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .padding(spacing.xxl),
        ) {
            // Danger dot: Aura danger as a fill, never as text.
            StatusDot(color = AuraPalette.auraDanger, size = 8.dp)
            Text(
                text = message,
                style = OpenCodeTypography.body,
                color = colors.danger,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                TonalButton(
                    label = stringResource(R.string.chat_retry),
                    onClick = onRetry,
                )
            }
        }
    }
}
