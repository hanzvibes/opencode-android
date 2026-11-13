package dev.bidzz.opencode.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.AuraPalette
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Hatch inline banner — clean-room re-implementation of the Meta Muse
 * app's (`com.facebook.aura` 16.0.0.2.159) `HatchInlineBanner`, from its
 * dex class name in `aura-unpack/ANALYSIS.md`. No Aura code, font, or
 * asset is copied: the banner is our own [auraCard] with a Hatch kind
 * dot, Aura text colours and 48dp action targets.
 *
 * It replaces the coloured [NoticeRow] (kind colour at 12% background +
 * 0.5dp border): the message, retry and dismiss behaviour are identical
 * — only the treatment changes. [NoticeRow] now delegates here, so the
 * chat, Providers and session-list banners all get the Hatch look from
 * the one call site.
 */

/** The four banner kinds. The dot is a fill, never text. Pure so it is unit-testable. */
enum class HatchBannerKind { ERROR, WARNING, INFO, SUCCESS }

/**
 * The dot colour for a kind. Aura has no warning token, so WARNING takes
 * the theme warning from the call site; the other three are Aura
 * constants regardless of what is passed in.
 */
internal fun HatchBannerKind.dotColor(warning: Color): Color = when (this) {
    HatchBannerKind.ERROR -> AuraPalette.auraDanger
    HatchBannerKind.WARNING -> warning
    HatchBannerKind.INFO -> AuraPalette.auraBlueFill
    HatchBannerKind.SUCCESS -> AuraPalette.auraSuccess
}

/** The leading glyph for a kind. Pure so it is unit-testable. */
internal fun HatchBannerKind.glyph(): Glyph = when (this) {
    HatchBannerKind.ERROR, HatchBannerKind.WARNING -> Glyph.Warning
    HatchBannerKind.INFO -> Glyph.Info
    HatchBannerKind.SUCCESS -> Glyph.Check
}

/**
 * A Hatch banner: kind dot + message + optional retry text action +
 * optional dismiss X, on a floating white card. Retry and dismiss keep
 * the 48dp hit areas the old banner had; the retry label is text, so in
 * light it is [AuraPalette.auraBlueText] (dark keeps `accentText`).
 */
@Composable
fun HatchInlineBanner(
    message: String,
    kind: HatchBannerKind,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions
    val retryLabel = stringResource(R.string.chat_retry)
    val closeDescription = stringResource(R.string.chat_status_dialog_close)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .auraCard()
            .padding(start = spacing.md, top = spacing.xs, bottom = spacing.xs, end = spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(
            color = kind.dotColor(colors.warning),
            size = 8.dp,
        )
        Text(
            text = message,
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.sm),
        )
        if (onRetry != null) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier
                    .sizeIn(minHeight = dims.minTouchTarget)
                    .semantics {
                        role = Role.Button
                        contentDescription = retryLabel
                    },
            ) {
                Text(
                    text = retryLabel,
                    style = OpenCodeTypography.label,
                    // The retry label is text — bright Aura blue is
                    // 3.07:1 on white, so light uses the dark text blue.
                    color = if (colors.isDark) colors.accentText else AuraPalette.auraBlueText,
                )
            }
            Spacer(Modifier.width(spacing.xs))
        }
        if (onDismiss != null) {
            // Dismiss: a full 48dp hit area around the 12dp cross.
            Box(
                modifier = Modifier
                    .size(dims.minTouchTarget)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = closeDescription },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = Glyph.Close,
                    tint = colors.onSurfaceMuted,
                    size = 12.dp,
                )
            }
        }
    }
}
