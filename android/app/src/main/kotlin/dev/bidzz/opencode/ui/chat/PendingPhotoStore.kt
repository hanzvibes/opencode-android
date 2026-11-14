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
        /** The camera result arrived and the photo was handed to the chat screen. */
        private const val KEY_DELIVERED = "delivered"
    }

    fun get(): Uri? = prefs.getString(KEY_URI, null)?.let { Uri.parse(it) }

    fun put(uri: Uri) {
        // Audit-200 A9-4 (DL-006, DL-031): commit(), not apply() — the
        // whole reason this store exists is surviving a process death
        // while the camera is open, and apply()'s async flush left
        // exactly that window open: the camera came back with success
        // and nowhere to deliver the photo. Same rule as lastSessionID
        // (69-F10). One small string write.
        prefs.edit().putString(KEY_URI, uri.toString()).remove(KEY_DELIVERED).commit()
    }

    /**
     * Marks the pending photo as delivered (audit-200 A6, DL-027): the
     * camera result arrived, but the workspace copy may still be running.
     * The trace stays until the copy's acknowledgement clears it, so a
     * death mid-copy leaves a photo to retry from on the next launch.
     */
    fun markDelivered() {
        // Audit-200 A9-4 (DL-006): commit(), for the same reason as put —
        // a lost acknowledgement would offer the same photo for a second
        // copy after a death mid-copy.
        prefs.edit().putBoolean(KEY_DELIVERED, true).commit()
    }

    /** True when the camera result arrived but the copy never acknowledged. */
    fun isDelivered(): Boolean = prefs.getBoolean(KEY_DELIVERED, false)

    fun clear() {
        // Audit-200 A9-4 (DL-006): commit(), for the same reason as put —
        // a clear lost in the flush window would resurrect a photo the
        // user already dealt with on the next launch.
        prefs.edit().remove(KEY_URI).remove(KEY_DELIVERED).commit()
    }
}
