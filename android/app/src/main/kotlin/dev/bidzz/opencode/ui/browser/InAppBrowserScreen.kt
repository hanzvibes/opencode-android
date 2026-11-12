package dev.bidzz.opencode.ui.browser

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.bidzz.opencode.OpenCodeApplication
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.ScreenScaffold

/**
 * True when [url] is a web page the in-app browser can open: http/https
 * with a host. Everything else (mailto:, tel:, javascript:, intent:,
 * file:, relative) stays outside — the same allowlist the chat link
 * guard already enforces — and is offered to the system instead.
 *
 * Pure string parsing on purpose: android.net.Uri is a framework stub
 * in local unit tests (it returns null there), so these helpers must
 * not depend on it — that is what made InAppBrowserTest fail in CI.
 */
fun isBrowsableUrl(url: String): Boolean {
    val host = urlHostAfterScheme(url) ?: return false
    val scheme = url.substringBefore("://").lowercase()
    if (scheme != "http" && scheme != "https") return false
    return host.isNotBlank()
}

/** The host for the toolbar title, or the raw URL when it has none. */
fun browserDisplayHost(url: String): String {
    val host = urlHostAfterScheme(url)
    return host?.takeIf { it.isNotBlank() } ?: url
}

/**
 * The authority part after "<scheme>://", up to the first '/', '?' or
 * '#'. Null when there is no "://". Pure Kotlin, no android.net.Uri.
 */
private fun urlHostAfterScheme(url: String): String? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0) return null
    val rest = url.substring(schemeEnd + 3)
    return rest.substringBefore('/').substringBefore('?').substringBefore('#')
}

/**
 * Shares plain text through the system chooser. One helper for the
 * browser toolbar and the chat message actions, so both share the same
 * chooser title and the same ActivityNotFound guard.
 */
fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.browser_share)),
        )
    }
}

/**
 * The in-app browser (Native C): a system WebView in a full screen, so a
 * link tapped in chat never throws the user out of the app.
 *
 * The engine is the system's WebView, not a bundled Chromium — the APK
 * grows by nothing for it. JavaScript is on (most pages need it) but
 * there is no JavascriptInterface and no file/content access, so a page
 * cannot reach app files. Non-web links are offered to the system.
 * Downloads go through DownloadManager into the public Downloads folder.
 */
@Composable
fun InAppBrowserScreen(
    url: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    var currentUrl by remember { mutableStateOf(url) }
    var pageTitle by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // No file/content access: a page must never read app files.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val target = request.url.toString()
                    return if (isBrowsableUrl(target)) {
                        false
                    } else {
                        // mailto:/tel:/intent: and friends belong to the
                        // system, not this WebView. Guarded: no app for
                        // the scheme must not crash the browser.
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, request.url),
                            )
                        }
                        true
                    }
                }

                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    currentUrl = finishedUrl
                    canGoBack = view.canGoBack()
                    canGoForward = view.canGoForward()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, title: String?) {
                    pageTitle = title.orEmpty()
                }

                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress
                }
            }
            setDownloadListener { downloadUrl, _, contentDisposition, mimeType, _ ->
                val filename = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                val downloaded = runCatching {
                    val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                        setMimeType(mimeType)
                        addRequestHeader("User-Agent", settings.userAgentString)
                        setTitle(filename)
                        setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                        )
                        setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            filename,
                        )
                    }
                    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    manager.enqueue(request)
                }.isSuccess
                (context.applicationContext as OpenCodeApplication).showSnackbar(
                    context.getString(
                        if (downloaded) R.string.browser_download_started
                        else R.string.browser_download_failed,
                    ),
                )
            }
            loadUrl(url)
        }
    }
    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    // System Back walks the page history first; only at the first page
    // does it leave the browser (the app-level BackHandler then goes
    // back to chat, same as every other screen).
    BackHandler(enabled = canGoBack) { webView.goBack() }

    ScreenScaffold(
        title = pageTitle.ifBlank { browserDisplayHost(currentUrl) },
        onBack = onBack,
        actions = {
            BrowserAction(
                glyph = Glyph.ChevronLeft,
                description = stringResource(R.string.browser_back),
                enabled = canGoBack,
                onClick = { webView.goBack() },
            )
            BrowserAction(
                glyph = Glyph.ChevronRight,
                description = stringResource(R.string.browser_forward),
                enabled = canGoForward,
                onClick = { webView.goForward() },
            )
            BrowserAction(
                glyph = Glyph.Refresh,
                description = stringResource(R.string.browser_reload),
                onClick = { webView.reload() },
            )
            BrowserAction(
                glyph = Glyph.Share,
                description = stringResource(R.string.browser_share),
                onClick = { shareText(context, currentUrl) },
            )
            BrowserAction(
                glyph = Glyph.External,
                description = stringResource(R.string.browser_open_external),
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)),
                        )
                    }
                },
            )
        },
    ) {
        // The full URL, one line: tap copies it. The title above shows
        // the host (or page title); this row is the exact address.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    manager.setPrimaryClip(ClipData.newPlainText("url", currentUrl))
                    (context.applicationContext as OpenCodeApplication).showSnackbar(
                        context.getString(R.string.browser_copied),
                    )
                }
                .padding(horizontal = spacing.md, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(
                glyph = Glyph.Globe,
                tint = colors.onSurfaceFaint,
                size = 12.dp,
            )
            Text(
                text = currentUrl,
                style = OpenCodeTypography.caption,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.xs),
            )
        }
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = colors.accent,
                trackColor = colors.hairline,
            )
        }
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun BrowserAction(
    glyph: Glyph,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = OpenCodeTheme.colors
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
            .size(40.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            glyph = glyph,
            tint = if (enabled) colors.onSurface else colors.onSurfaceFaint.copy(alpha = 0.4f),
            size = 20.dp,
        )
    }
}
