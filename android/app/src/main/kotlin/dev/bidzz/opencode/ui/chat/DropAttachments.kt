package dev.bidzz.opencode.ui.chat

import android.content.ClipData
import android.content.ClipDescription
import android.net.Uri

/**
 * Drag & drop into the composer (Batch 7, Native C batch 2).
 *
 * Compose 1.7's `dragAndDropTarget` cannot be the drop side here: its
 * [androidx.compose.ui.draganddrop.DragAndDropEvent] only exposes the
 * platform DragEvent through an internal accessor, so app code cannot
 * read the dropped ClipData from it. The drop side is therefore a
 * plain [android.view.View.OnDragListener] on the composition root
 * (see ChatScreen), while the drag source is Compose
 * `dragAndDropSource` in the Explorer — both speak the platform drag
 * protocol, so they interoperate.
 *
 * What a drop carries:
 * - `content://` URIs (from another app, or the system picker): read with
 *   the same [readSafFile] rules as the SAF picker — text under 64 KB is
 *   inlined as an attachment, anything else is refused with the same
 *   message, never silently dropped.
 * - A workspace path dragged from the Explorer, under the private MIME
 *   type [MIME_WORKSPACE_PATH]: inserted as an `@path` mention in the
 *   draft, exactly like picking it from the `@` mention picker — the
 *   engine can read workspace paths directly, so inlining the file would
 *   be wasteful. Plain text from other apps is deliberately NOT accepted:
 *   it could be any sentence, and guessing it is a path would insert a
 *   broken mention.
 */

/** Private MIME type for a workspace path dragged out of the Explorer. */
const val MIME_WORKSPACE_PATH = "application/x-opencode-path"

/** The payload of one drop, split by kind, in drop order. */
data class DroppedContent(
    val uris: List<Uri>,
    val texts: List<String>,
)

/**
 * True when a drag carrying these MIME types is a drop this app can do
 * something with: a URI list (files from another app) or a workspace
 * path under [MIME_WORKSPACE_PATH] (a file dragged from the Explorer).
 * Plain text is not enough on its own — see the file doc. Pure, so the
 * rule is unit-tested here rather than through the drag listener.
 */
fun acceptsDropMimeTypes(mimeTypes: Set<String>): Boolean =
    ClipDescription.MIMETYPE_TEXT_URILIST in mimeTypes ||
        MIME_WORKSPACE_PATH in mimeTypes

/** Splits a drop's [ClipData] into URIs and plain-text items. */
fun droppedContent(clipData: ClipData?): DroppedContent {
    if (clipData == null) return DroppedContent(emptyList(), emptyList())
    val uris = mutableListOf<Uri>()
    val texts = mutableListOf<String>()
    for (i in 0 until clipData.itemCount) {
        val item = clipData.getItemAt(i)
        item.uri?.let { uris.add(it) }
        item.text?.let { texts.add(it.toString()) }
    }
    return DroppedContent(uris, texts)
}
