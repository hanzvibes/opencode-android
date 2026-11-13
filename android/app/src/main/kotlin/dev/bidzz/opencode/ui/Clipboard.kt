package dev.bidzz.opencode.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.ClipboardManager as ComposeClipboardManager
import androidx.compose.ui.text.AnnotatedString

/**
 * Copies plain text to the system clipboard (audit-100 65-F4).
 *
 * Returns false — instead of showing "Copied" anyway — when the clipboard
 * service is missing or the set throws. Every copy site in the app must
 * only claim success when this returns true: a "Copied" check that fires
 * while the copy was skipped or failed is a lie the user cannot detect.
 */
internal fun copyPlainText(context: Context, label: String, text: String): Boolean =
    runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        manager.setPrimaryClip(ClipData.newPlainText(label, text))
        true
    }.getOrDefault(false)

/**
 * Copies through the Compose clipboard manager (audit-100 65-F4).
 *
 * Same contract as [copyPlainText]: false when the set throws, so the
 * caller only flips its "Copied" state on a real success.
 */
internal fun setClipboardText(clipboardManager: ComposeClipboardManager, text: AnnotatedString): Boolean =
    runCatching {
        clipboardManager.setText(text)
        true
    }.getOrDefault(false)
