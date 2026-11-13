package dev.bidzz.opencode.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Blocks screenshots and the Recents thumbnail while composed
 * (audit-100 53-F3).
 *
 * Screens that display a secret — the engine password in Diagnostics, an
 * API key in Providers — had no `FLAG_SECURE` anywhere, so anyone holding
 * the phone, a screenshot, or a screen-share could read the secret while
 * it was on screen.
 *
 * The effect is scoped to the reveal, not the screen: it is only composed
 * while the secret is actually visible (`showPassword` / `showKey`), and
 * the flag clears when the secret hides again. A global flag would also
 * block the screenshots used for bug reports everywhere else — the
 * Diagnostics auto-hide (10s) and the key eye-toggle already bound the
 * window tightly enough.
 *
 * Note: if two screens ever composed this at once, the first dispose
 * would clear the flag while the second still shows a secret. The app is
 * single-screen navigated, so that cannot happen today.
 */
@Composable
fun SecureWindowEffect() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
