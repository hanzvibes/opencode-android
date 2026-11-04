package dev.bidzz.opencode.ui.about

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.CardGroup
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.IconBadge
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold

// Taken from https://bidzzofc.my.id/ . The site shows its email only through Cloudflare's
// obfuscation; this is the address that obfuscation decodes to.
private const val NAME = "Muhammad Abid"
private const val HANDLE = "Bidzz"
private const val PORTFOLIO_URL = "https://bidzzofc.my.id/"
private const val TELEGRAM_URL = "https://t.me/bidzz7"
private const val INSTAGRAM_URL = "https://instagram.com/muhammad.abid857"
private const val GITHUB_URL = "https://github.com/BIDZZ-RAWR"
private const val EMAIL = "support@bidzzofc.my.id"

private data class Link(val glyph: Glyph, val titleRes: Int, val shown: String, val url: String)

private val LINKS = listOf(
    Link(Glyph.Globe, R.string.about_portfolio, "bidzzofc.my.id", PORTFOLIO_URL),
    Link(Glyph.Send, R.string.about_telegram, "@bidzz7", TELEGRAM_URL),
    Link(Glyph.Camera, R.string.about_instagram, "muhammad.abid857", INSTAGRAM_URL),
    Link(Glyph.Code, R.string.about_github, "BIDZZ-RAWR", GITHUB_URL),
    Link(Glyph.Mail, R.string.about_email, EMAIL, "mailto:$EMAIL"),
)

/**
 * About the developer: who made the app, and where to find them.
 *
 * A profile card on top, then every contact in one card like Settings. Links open in
 * whatever the device uses for them (browser, Telegram, Instagram, the mail app).
 */
@Composable
fun AboutScreen(versionName: String, onBack: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val failed = stringResource(R.string.about_open_failed)

    val open: (String) -> Unit = remember(uriHandler, failed) {
        { url ->
            try {
                uriHandler.openUri(url)
            } catch (_: Exception) {
                Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    ScreenScaffold(title = stringResource(R.string.about_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        ) {
            CardGroup(modifier = Modifier.padding(top = spacing.md)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.lg, vertical = spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = HANDLE.first().uppercase(),
                            style = OpenCodeTypography.title.copy(fontSize = 32.sp),
                            color = colors.onAccent,
                        )
                    }
                    Text(
                        text = NAME,
                        style = OpenCodeTypography.title,
                        color = colors.onSurface,
                        modifier = Modifier.padding(top = spacing.md),
                    )
                    Text(
                        text = HANDLE,
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurfaceMuted,
                    )
                    Text(
                        text = stringResource(R.string.about_role),
                        style = OpenCodeTypography.label,
                        color = colors.accent,
                        modifier = Modifier
                            .padding(top = spacing.sm)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.14f))
                            .padding(horizontal = spacing.md, vertical = spacing.xs),
                    )
                    Text(
                        text = stringResource(R.string.about_bio),
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurfaceMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = spacing.lg),
                    )
                }
            }

            Text(
                text = stringResource(R.string.about_section_links).uppercase(),
                style = OpenCodeTypography.label,
                color = colors.onSurfaceFaint,
                modifier = Modifier.padding(start = spacing.xs, top = spacing.xl, bottom = spacing.sm),
            )
            CardGroup {
                LINKS.forEachIndexed { index, link ->
                    if (index > 0) RowDivider(inset = spacing.md + 36.dp + spacing.md)
                    LinkRow(link, onClick = { open(link.url) })
                }
            }

            Text(
                text = stringResource(R.string.about_version, versionName),
                style = OpenCodeTypography.label,
                color = colors.onSurfaceFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.xl),
            )
        }
    }
}

@Composable
private fun LinkRow(link: Link, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        IconBadge(link.glyph)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.md),
        ) {
            Text(stringResource(link.titleRes), style = OpenCodeTypography.body, color = colors.onSurface)
            Text(
                text = link.shown,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GlyphIcon(
            Glyph.External,
            tint = colors.onSurfaceFaint,
            size = 18.dp,
            modifier = Modifier.padding(start = spacing.sm),
        )
    }
}
