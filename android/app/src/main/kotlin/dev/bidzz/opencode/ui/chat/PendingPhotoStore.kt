package dev.bidzz.opencode.ui.chat

import android.content.Context
import android.net.Uri

/**
 * Persists the URI of the photo the system camera is currently writing
 * (15-F6). The URI used to live only in a MainActivity field: a rotation
 * or a process death while the camera was open lost it, so the callback
 * came back with `success == true` and nowhere to deliver the photo —
 * the picture survived in the gallery, but the path into the workspace
 * was gone without a word. One SharedPreferences string, written when
 * the camera launches and cleared on every exit path of the callback,
 * survives both.
 */
class PendingPhotoStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "pending_photo"
        private const val KEY_URI = "uri"
    }

    fun get(): Uri? = prefs.getString(KEY_URI, null)?.let { Uri.parse(it) }

    fun put(uri: Uri) {
        prefs.edit().putString(KEY_URI, uri.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_URI).apply()
    }
}
