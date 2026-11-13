package dev.bidzz.opencode.ui.about

import dev.bidzz.opencode.OpenCodeApplication
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

private const val NAME = "Raihan Ramadhani"
private const val HANDLE = "hanzvibes"
private const val GITHUB_URL = "https://github.com/hanzvibes"

private data class Link(val glyph: Glyph, val titleRes: Int, val shown: String, val url: String)

private val LINKS = listOf(
    Link(Glyph.Code, R.string.about_github, "hanzvibes", GITHUB_URL),
)

/**
 * The license texts bundled in the APK (audit-100 Fase 6 batch 6l): the five
 * full texts under `assets/licenses/` — the display names are the documents'
 * own titles, identical in every language. The third-party notices file
 * (`assets/THIRD_PARTY_NOTICES.md`, a byte-identical copy of the repo-root
 * file, CI-checked) is listed separately in the screen because its title is
 * a localised string, not a document title. The unit test pins this list to
 * the files actually in assets, so adding a license to the APK without
 * listing it here (or the reverse) fails the build.
 */
internal data class LicenseDoc(val displayName: String, val assetPath: String)

internal val LICENSE_DOCS = listOf(
    LicenseDoc("Apache License 2.0", "licenses/Apache-2.0.txt"),
    LicenseDoc("GNU General Public License v2.0", "licenses/GPL-2.0.txt"),
    LicenseDoc("GNU General Public License v3.0", "licenses/GPL-3.0.txt"),
    LicenseDoc("GNU Lesser General Public License v2.1", "licenses/LGPL-2.1.txt"),
    LicenseDoc("SIL Open Font License", "licenses/OFL.txt"),
)

private const val NOTICES_ASSET = "THIRD_PARTY_NOTICES.md"

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
    val readFailed = stringResource(R.string.about_licenses_read_failed)

    // Which license document the viewer dialog shows: the asset path and the
    // title survive recreate (rememberSaveable); the text is re-read from
    // assets when the path changes, so a process death mid-view just reloads.
    var viewingAsset by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(viewingAsset) {
        val asset = viewingAsset ?: return@LaunchedEffect
        viewingText = try {
            context.assets.open(asset).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
        if (viewingText == null) {
            (context.applicationContext as OpenCodeApplication).showSnackbar(readFailed)
            viewingAsset = null
            viewingTitle = null
        }
    }

    val open: (String) -> Unit = remember(uriHandler, failed) {
        { url ->
            try {
                uriHandler.openUri(url)
            } catch (_: Exception) {
                (context.applicationContext as OpenCodeApplication).showSnackbar(failed)
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
                            .background(colors.accentFill),
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
                        color = colors.accentText,
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
                    if (index > 0) RowDivider(inset = spacing.md + 32.dp + spacing.md)
                    LinkRow(link, onClick = { open(link.url) })
                }
            }

            // The licenses the APK actually ships: the notices file (which
            // component, which license, where the source is) plus the five
            // full license texts. They were bundled but unreachable from the
            // UI; each row opens a scrollable viewer for the bundled text.
            Text(
                text = stringResource(R.string.about_licenses_title).uppercase(),
                style = OpenCodeTypography.label,
                color = colors.onSurfaceFaint,
                modifier = Modifier.padding(start = spacing.xs, top = spacing.xl, bottom = spacing.sm),
            )
            CardGroup {
                LicenseRow(
                    glyph = Glyph.Info,
                    title = stringResource(R.string.about_licenses_notices),
                    subtitle = stringResource(R.string.about_licenses_notices_body),
                    onClick = {
                        viewingTitle = context.getString(R.string.about_licenses_notices)
                        viewingAsset = NOTICES_ASSET
                    },
                )
                LICENSE_DOCS.forEach { doc ->
                    RowDivider(inset = spacing.md + 32.dp + spacing.md)
                    LicenseRow(
                        glyph = Glyph.FileCode,
                        title = doc.displayName,
                        subtitle = doc.assetPath.substringAfterLast("/"),
                        onClick = {
                            viewingTitle = doc.displayName
                            viewingAsset = doc.assetPath
                        },
                    )
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

        // The license viewer: the bundled text, scrollable (the GPL texts
        // are hundreds of lines). The dialog only opens once the text has
        // loaded; a failed read shows a snackbar and clears the request
        // instead (see the LaunchedEffect above).
        val text = viewingText
        val title = viewingTitle
        if (viewingAsset != null && text != null && title != null) {
            AlertDialog(
                onDismissRequest = { viewingAsset = null },
                title = { Text(title, style = OpenCodeTypography.title, color = colors.onSurface) },
                text = {
                    Text(
                        text = text,
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurface,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewingAsset = null }) {
                        Text(stringResource(R.string.common_close), color = colors.accentText)
                    }
                },
            )
        }
    }
}

@Composable
private fun LicenseRow(glyph: Glyph, title: String, subtitle: String, onClick: () -> Unit) {
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
        IconBadge(glyph)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.md),
        ) {
            Text(title, style = OpenCodeTypography.body, color = colors.onSurface)
            Text(
                text = subtitle,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GlyphIcon(
            Glyph.ChevronRight,
            tint = colors.onSurfaceFaint,
            size = 18.dp,
            modifier = Modifier.padding(start = spacing.sm),
        )
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
