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
 * The workspace file a photo copy should land in (14-F7). The name is
 * unique per second, so two copies in the same second used to point at
 * the same file and the second overwrote the first — the `@`-mention in
 * the draft then pointed at the wrong photo. A taken name gets a
 * numbered suffix (`IMG_…_1.jpg`) instead. Pure, so the rule is
 * unit-tested here rather than through the camera.
 */
internal fun uniquePhotoTarget(dir: File, baseName: String): File {
    val stem = baseName.removeSuffix(".jpg")
    var candidate = File(dir, baseName)
    var n = 1
    while (candidate.exists()) {
        candidate = File(dir, "${stem}_$n.jpg")
        n++
    }
    return candidate
}

/**
 * Copies a just-taken photo into the workspace directory, returning its
 * workspace-relative path, or null when the copy failed (15-F7).
 *
 * The bytes go to a `<name>.tmp` file first and are renamed to the
 * target only after a complete, non-empty copy: the old code wrote
 * straight into the target, so a failure mid-copy left a partial `.jpg`
 * in the workspace for the agent to trip over — and a 0-byte copy
 * (an empty MediaStore row) came back as a success. A failed or empty
 * copy deletes the temp file and reports nothing; a rename that fails
 * does the same. Pure over [java.io] streams, so the rule is
 * unit-tested here rather than through the camera.
 */
internal fun copyPhotoToWorkspace(
    input: java.io.InputStream,
    dir: File,
    baseName: String,
): String? {
    val target = uniquePhotoTarget(dir, baseName)
    val tmp = File(dir, target.name + ".tmp")
    return try {
        val bytes = tmp.outputStream().use { output -> input.copyTo(output) }
        if (bytes <= 0 || !tmp.renameTo(target)) {
            tmp.delete()
            null
        } else {
            "$CAMERA_PHOTOS_DIR/${target.name}"
        }
    } catch (_: Exception) {
        tmp.delete()
        null
    }
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
        contentResolver.openInputStream(uri)?.use { input ->
            copyPhotoToWorkspace(input, dir, name)
        }
    }.getOrNull()
}
