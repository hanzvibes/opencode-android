package dev.bidzz.opencode.ui.files

import dev.bidzz.opencode.ui.chat.DIFF_LCS_LINE_LIMIT
import dev.bidzz.opencode.ui.chat.computeDiffHunks
import dev.bidzz.opencode.ui.diff.DiffLine
import java.util.Locale

/**
 * What pressing save means for the file on disk.
 *
 * The agent writes the same files the editor shows, and it does so while the user is
 * typing. A save that always wrote the draft would silently delete whatever the agent
 * produced in the meantime, so the decision is taken from all three versions — what the
 * editor loaded, what the user typed, and what the file holds now — before any write.
 */
enum class SaveAction {
    /**
     * The draft equals what was loaded, so nothing is written.
     *
     * This wins over every other case: a draft typed and then undone is byte-identical
     * to the version that was loaded, and writing it would overwrite the agent's newer
     * content with the older text the editor still holds.
     */
    Unchanged,

    /** The file is still what the editor loaded, and the draft differs from it. */
    Write,

    /** Something changed the file since it was loaded. */
    Conflict,

    /**
     * The current content could not be read, so a change underneath cannot be ruled
     * out. Refusing here costs one retry; guessing wrong costs the agent's work.
     */
    Unverifiable,
}

/**
 * [original] is what the editor loaded, [draft] what is in the field now, and
 * [onDisk] what the file holds at save time — null when that could not be read.
 *
 * [overwrite] is the user's answer to a conflict, never a default.
 */
fun saveAction(original: String, draft: String, onDisk: String?, overwrite: Boolean = false): SaveAction = when {
    draft == original -> SaveAction.Unchanged
    onDisk == null -> SaveAction.Unverifiable
    onDisk != original && !overwrite -> SaveAction.Conflict
    else -> SaveAction.Write
}

/**
 * What changed on disk since the editor opened, for the conflict dialog
 * (audit-200 A6, DL-022): the user must see the agent's edit before
 * choosing Overwrite, not a blind "the file changed". Pure, so the
 * counts and the cap are unit-tested here rather than through the sheet.
 * The diff is the same [computeDiffHunks] the full-screen diff renders,
 * so "+" and "−" never disagree between the two views.
 */
internal data class ConflictChanges(
    /** Lines added on disk. */
    val added: Int,
    /** Lines removed from the version the editor opened. */
    val removed: Int,
    /** Changed lines to show, each prefixed "+" or "−". */
    val shownLines: List<String>,
    /** Changed lines beyond [shownLines]. */
    val moreLines: Int,
    /**
     * True when the file is too large for an exact diff: the counts are
     * the whole file, not the change, so the dialog must not present them
     * as the change.
     */
    val tooLarge: Boolean,
)

/** The agent's edit between [base] (what the editor opened) and [disk] (what the file holds now). */
internal fun conflictChanges(base: String, disk: String, maxShown: Int = 12): ConflictChanges {
    val oldLines = base.split('\n').dropLastWhile { it.isEmpty() }
    val newLines = disk.split('\n').dropLastWhile { it.isEmpty() }
    if (oldLines == newLines) return ConflictChanges(0, 0, emptyList(), 0, false)
    if (oldLines.size + newLines.size > DIFF_LCS_LINE_LIMIT) {
        return ConflictChanges(oldLines.size, newLines.size, emptyList(), 0, true)
    }
    val changed = computeDiffHunks(base, disk)
        .flatMap { it.lines }
        .filter { it.kind != DiffLine.Kind.CONTEXT }
    val added = changed.count { it.kind == DiffLine.Kind.ADDED }
    val removed = changed.count { it.kind == DiffLine.Kind.REMOVED }
    val shown = changed.take(maxShown).map { line ->
        (if (line.kind == DiffLine.Kind.ADDED) "+" else "−") + " " + line.text
    }
    return ConflictChanges(
        added = added,
        removed = removed,
        shownLines = shown,
        moreLines = (changed.size - shown.size).coerceAtLeast(0),
        tooLarge = false,
    )
}

/**
 * Large-file guard: files over [MaxChars] are never held, highlighted or searched in
 * full. The viewer shows only the first [PreviewLines] lines until the user
 * explicitly asks for the whole file.
 *
 * The threshold counts characters, which for the UTF-8 text the engine returns is
 * ~1 MB. Honest limit: the engine's read endpoint has no range/head call, so one
 * full response is still transferred and decoded to measure it — the guard limits
 * what the app keeps in memory, runs the highlighter over, and searches, not the
 * transfer itself. Editing is blocked while truncated for the same reason a save
 * must never write a preview back over the real file. The preview is line-based:
 * a file with at most [PreviewLines] lines is shown whole even over the threshold,
 * because cutting it mid-line would corrupt the display (minified single-line
 * files are the usual case).
 */
object LargeFileGuard {
    /** ~1 MB of text. */
    const val MaxChars: Int = 1_048_576

    /** Lines kept for the preview of a large file. */
    const val PreviewLines: Int = 2_000

    fun isLarge(content: String): Boolean = content.length > MaxChars

    /**
     * Returns the text to display and the file's full line count. Small files come
     * back untouched; large files come back as their first [PreviewLines] lines.
     *
     * [totalLines] is the file's true line count, which may come from the
     * reader instead of this text: a capped read (59-F5) keeps only a
     * prefix but still counts the whole file, so the banner never
     * understates how much was held back.
     */
    fun preview(text: String, totalLines: Int): Pair<String, Int> {
        val lines = text.lines()
        if (!isLarge(text)) return text to totalLines
        return lines.take(PreviewLines).joinToString("\n") to totalLines
    }

    /** Same as [preview] with the line count read off the text itself. */
    fun preview(content: String): Pair<String, Int> = preview(content, content.lines().size)
}

/**
 * "512 B", "1 KB", "1.5 MB", "8 MB" — the size shown for a file the read
 * capped (audit-200 A8, DL-005). One decimal, with a trailing ".0" dropped.
 */
internal fun formatFileBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    val text = String.format(Locale.US, "%.1f", value).removeSuffix(".0")
    return "$text ${units[unit]}"
}

/**
 * Plain-text, case-insensitive find over the text actually loaded. Matches never
 * overlap; the list is capped at [maxMatches] so a one-character query in a big
 * file cannot build an unbounded list. Callers show the cap (e.g. "1000+") and,
 * for a truncated file, say the search covers the loaded preview only.
 */
fun findMatches(text: String, query: String, maxMatches: Int = 1_000): List<IntRange> {
    if (query.isEmpty()) return emptyList()
    val out = mutableListOf<IntRange>()
    var from = 0
    while (out.size < maxMatches) {
        val idx = text.indexOf(query, from, ignoreCase = true)
        if (idx < 0) break
        out.add(idx until idx + query.length)
        from = idx + query.length
    }
    return out
}

/** Quick-open ranking, same ordering as the chat command palette: exact filename, then filename prefix, then filename substring, then path substring. */
fun rankQuickOpen(files: List<FileRow>, query: String, limit: Int = 50): List<FileRow> {
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return files.take(limit)
    val exact = mutableListOf<FileRow>()
    val prefix = mutableListOf<FileRow>()
    val nameSubstring = mutableListOf<FileRow>()
    val pathSubstring = mutableListOf<FileRow>()
    for (file in files) {
        val name = file.name.lowercase(Locale.ROOT)
        when {
            name == q -> exact.add(file)
            name.startsWith(q) -> prefix.add(file)
            name.contains(q) -> nameSubstring.add(file)
            file.path.lowercase(Locale.ROOT).contains(q) -> pathSubstring.add(file)
        }
    }
    return (exact + prefix + nameSubstring + pathSubstring).take(limit)
}
