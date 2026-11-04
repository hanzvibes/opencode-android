package dev.bidzz.opencode.ui

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * The context that shows the app's chosen language. Set once in `OpenCodeApp`.
 *
 * Kept as its own local because a dialog is a separate window with its own compose root, and
 * that root resets [LocalContext] and [LocalConfiguration] to the activity's, which follows the
 * phone's language. Custom locals pass through into the dialog; those two do not.
 */
val LocalAppContext = staticCompositionLocalOf<Context?> { null }

@Composable
private fun Localized(content: @Composable () -> Unit) {
    val app = LocalAppContext.current
    if (app == null) {
        content()
    } else {
        CompositionLocalProvider(
            LocalContext provides app,
            LocalConfiguration provides app.resources.configuration,
            content = content,
        )
    }
}

/** [AlertDialog] whose content is drawn in the app's chosen language instead of the phone's. */
@Composable
fun LocalizedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { Localized(confirmButton) },
        dismissButton = dismissButton?.let { button -> { Localized(button) } },
        title = title?.let { heading -> { Localized(heading) } },
        text = text?.let { body -> { Localized(body) } },
    )
}
