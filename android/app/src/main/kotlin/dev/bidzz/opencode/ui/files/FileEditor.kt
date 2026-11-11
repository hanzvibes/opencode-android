package dev.bidzz.opencode.ui.files

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
     */
    fun preview(content: String): Pair<String, Int> {
        val lines = content.lines()
        if (!isLarge(content)) return content to lines.size
        return lines.take(PreviewLines).joinToString("\n") to lines.size
    }
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
    val q = query.trim().lowercase()
    if (q.isEmpty()) return files.take(limit)
    val exact = mutableListOf<FileRow>()
    val prefix = mutableListOf<FileRow>()
    val nameSubstring = mutableListOf<FileRow>()
    val pathSubstring = mutableListOf<FileRow>()
    for (file in files) {
        val name = file.name.lowercase()
        when {
            name == q -> exact.add(file)
            name.startsWith(q) -> prefix.add(file)
            name.contains(q) -> nameSubstring.add(file)
            file.path.lowercase().contains(q) -> pathSubstring.add(file)
        }
    }
    return (exact + prefix + nameSubstring + pathSubstring).take(limit)
}
