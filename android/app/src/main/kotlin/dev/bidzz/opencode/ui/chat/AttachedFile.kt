package dev.bidzz.opencode.ui.chat

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.UUID

/**
 * Largest file that may be inlined into a prompt from the system file
 * picker (64 KiB). The engine cannot read paths outside the workspace, so
 * an outside file is pasted into the message as text — and a prompt that
 * grows without bound is a runaway token bill, not an attachment. The cap
 * is enforced at read time in [readSafFile] (see below) and named in the
 * refusal string (`attach_file_too_large`), so the number lives in exactly
 * one place: here.
 */
const val MAX_INLINE_FILE_BYTES = 65_536

/**
 * A file picked from outside the workspace, already read into memory as
 * text. It is pending, not sent: the [content] is appended to the message
 * as a fenced block only when the user sends (see
 * [buildMessageWithAttachments]), and the chip can be dismissed until then.
 *
 * The content is inlined, never a path: the engine has no access to the
 * device's files, so a path would be a dead reference. Only text is ever
 * stored here — binaries are refused at read time, never base64'd.
 */
data class AttachedFile(
    /** Stable id for chip keys and for clearing exactly the sent files. */
    val id: String,
    val name: String,
    val content: String,
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
    /**
     * The block pasted into the prompt: a fenced code block headed by the
     * filename (```filename), so the agent sees which file the text came
     * from and the transcript stays readable.
     *
     * The fence is one backtick longer than the longest backtick run inside
     * the content (minimum three), so file content that itself contains
     * ``` cannot close the block early. The filename is sanitised for the
     * fence line only — backticks and newlines would break the fence or
     * smuggle a second block in; the chip still shows the real name.
     */
    fun toFencedBlock(): String {
        val fenceLength = maxOf(3, longestBacktickRun(content) + 1)
        val fence = "`".repeat(fenceLength)
        val safeName = name
            .replace('`', '\'')
            .replace('\n', ' ')
            .replace('\r', ' ')
        return "$fence$safeName\n$content\n$fence"
    }
}

/** Longest run of consecutive backticks in [text], or 0. */
private fun longestBacktickRun(text: String): Int {
    var longest = 0
    var run = 0
    for (ch in text) {
        if (ch == '`') {
            run++
            if (run > longest) longest = run
        } else {
            run = 0
        }
    }
    return longest
}

/**
 * The prompt actually sent: the typed text, then one fenced block per
 * pending attachment. A message that is only attachments sends just the
 * blocks — picking a file and hitting Send with no typed text still means
 * "show the agent this file".
 */
fun buildMessageWithAttachments(
    typedText: String,
    attachments: List<AttachedFile>,
): String {
    val blocks = attachments.map { it.toFencedBlock() }
    return (listOf(typedText).filter { it.isNotBlank() } + blocks)
        .joinToString("\n\n")
}

/**
 * True when [candidate] is already pending — same name and same content.
 * A re-pick of the same file is ignored instead of stacking a duplicate
 * chip; a different file that happens to share the name is kept, so two
 * folders' notes can share a filename. Pure, so the rule is unit-tested
 * here rather than through the view model.
 */
fun isDuplicateAttachment(
    existing: List<AttachedFile>,
    candidate: AttachedFile,
): Boolean = existing.any { it.name == candidate.name && it.content == candidate.content }

/**
 * The list without the attachment with [id] — the chip's X. Pure, so the
 * removal rule is unit-tested here rather than through the view model;
 * [buildMessageWithAttachments] only ever sees the list that survives
 * this filter, so a removed file can never be sent.
 */
fun withoutAttachment(
    attachments: List<AttachedFile>,
    id: String,
): List<AttachedFile> = attachments.filter { it.id != id }

/** The file's size in bytes, as the UTF-8 text that will be inlined. */
fun attachmentByteSize(file: AttachedFile): Int =
    file.content.toByteArray(Charsets.UTF_8).size

/**
 * "512 B", "1 KB", "1.5 KB", "64 KB" — the size shown on the pending
 * attachment chip. One decimal, with a trailing ".0" dropped.
 */
fun formatAttachmentSize(bytes: Int): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    val text = String.format(Locale.US, "%.1f", kb).removeSuffix(".0")
    return "$text KB"
}

/** What reading one system-picker file produced. */
sealed interface SafReadResult {
    /** Read and decoded as UTF-8 text; ready to attach. */
    data class Success(val file: AttachedFile) : SafReadResult

    /** The file is over [MAX_INLINE_FILE_BYTES]; refused, never read. */
    data class TooLarge(val name: String) : SafReadResult

    /** Not UTF-8 text (decode failure or NUL bytes): a binary, refused. */
    data class NotText(val name: String) : SafReadResult

    /** The content could not be opened or read at all. */
    data class Unreadable(val name: String) : SafReadResult
}

/**
 * Reads one file picked from the system picker (a `content://` URI)
 * into an [AttachedFile], enforcing the inline rules:
 *
 * - Size is checked via [OpenableColumns.SIZE] before any bytes are read,
 *   and the stream itself is capped at [MAX_INLINE_FILE_BYTES] + 1, so a
 *   provider that lies about the size still cannot make us read a large
 *   file into memory.
 * - The bytes must decode as strict UTF-8, and must contain no NUL bytes.
 *   Anything else is a binary: refused with [SafReadResult.NotText],
 *   never base64'd — the engine reads the inlined text as text, so
 *   base64 would only hand it unreadable noise.
 * - A leading UTF-8 BOM is stripped: it is not part of the file's text.
 *
 * Runs on the caller's thread; call it from a background dispatcher.
 */
fun readSafFile(contentResolver: ContentResolver, uri: Uri): SafReadResult {
    // Display name and size from one query; a provider that answers
    // neither still works — the name falls back below and the size is
    // enforced by the capped read instead.
    var name = fallbackName(uri)
    val declaredSize: Long = try {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use -1L
            cursor.displayName()?.let { name = it }
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
        } ?: -1L
    } catch (_: Exception) {
        // A broken provider can throw anything here (a dead provider
        // process, an IllegalStateException from its cursor): the file is
        // unreadable, not a crash (item 5).
        return SafReadResult.Unreadable(name)
    }
    // Over the cap on the declared size: refuse without reading a byte.
    if (declaredSize > MAX_INLINE_FILE_BYTES) {
        return SafReadResult.TooLarge(name)
    }

    val bytes: ByteArray = try {
        contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                // The stream outgrew the cap: stop reading, refuse.
                if (total > MAX_INLINE_FILE_BYTES) {
                    return SafReadResult.TooLarge(name)
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: return SafReadResult.Unreadable(name)
    } catch (_: Exception) {
        // Same rule as the query above (item 5): any failure reading from
        // the provider — dead process, revoked grant mid-read — is an
        // unreadable file, not a crash.
        return SafReadResult.Unreadable(name)
    }

    val text = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        return SafReadResult.NotText(name)
    }
    // NUL bytes mean binary even when the bytes happen to decode.
    if ('\u0000' in text) return SafReadResult.NotText(name)

    return SafReadResult.Success(
        AttachedFile(
            id = UUID.randomUUID().toString(),
            name = name,
            content = text.removePrefix("\uFEFF"),
        ),
    )
}

private fun Cursor.displayName(): String? =
    getColumnIndex(OpenableColumns.DISPLAY_NAME)
        .takeIf { it >= 0 }
        ?.let { getString(it) }
        ?.takeIf { it.isNotBlank() }

private fun fallbackName(uri: Uri): String =
    uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        ?: "file"
