package dev.bidzz.opencode.ui.chat

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Photos taken from the composer live here, inside the workspace. */
const val CAMERA_PHOTOS_DIR = ".opencode-photos"

/**
 * "IMG_20261112_023045.jpg" — sortable, unique per second. UTC, so the
 * name never depends on the device timezone.
 */
fun cameraPhotoFileName(nowMillis: Long): String {
    val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return "IMG_${format.format(Date(nowMillis))}.jpg"
}

/**
 * Copies a just-taken photo ([uri] from the system camera) into the
 * workspace, so the agent can reference it by path. Returns the
 * workspace-relative path (".opencode-photos/IMG_....jpg"), or null when
 * the workspace is unknown or the copy failed. The original stays in the
 * gallery — this is a copy, not a move.
 *
 * A photo travels as a path, not as an attachment: attachments are
 * text-only by design (see AttachedFile), and a binary inlined as
 * base64 would only hand the engine unreadable noise.
 */
fun saveCameraPhoto(
    contentResolver: ContentResolver,
    uri: Uri,
    workspacePath: String?,
): String? {
    if (workspacePath.isNullOrBlank()) return null
    return runCatching {
        val dir = File(workspacePath, CAMERA_PHOTOS_DIR)
        if (!dir.mkdirs() && !dir.isDirectory) return null
        val name = cameraPhotoFileName(System.currentTimeMillis())
        val target = File(dir, name)
        contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        "$CAMERA_PHOTOS_DIR/$name"
    }.getOrNull()
}
