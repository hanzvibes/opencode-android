package dev.bidzz.opencode.ui.diff

/**
 * Unified diff parsing for display.
 *
 * The engine returns a raw patch string. Rendering it as plain monospace loses the
 * only information that matters — which lines were added and which were removed —
 * so the patch is split into typed lines here.
 *
 * Only what a diff viewer needs is parsed: hunk headers and line kinds. File
 * headers (`---`, `+++`) are recognised and dropped, because the file name is
 * already shown above the patch.
 */
data class DiffLine(
    val kind: Kind,
    /** Text without the leading `+`, `-` or space. */
    val text: String,
    /** Line numbers from the hunk header; null for headers. */
    val oldLine: Int? = null,
    val newLine: Int? = null,
) {
    enum class Kind {
        /** `@@ -1,3 +1,4 @@` */
        HUNK_HEADER,
        /** `+added` */
        ADDED,
        /** `-removed` */
        REMOVED,
        /** ` unchanged` */
        CONTEXT,
        /** `\ No newline at end of file` and anything unrecognised. */
        META,
    }
}

/** One hunk, with its header text and lines. */
data class DiffHunk(
    val header: String,
    val lines: List<DiffLine>,
)

object DiffParser {

    private val hunkHeader = Regex("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@(.*)$")

    /**
     * Splits [patch] into hunks.
     *
     * Tolerant by design: a patch the engine truncated mid-line, or one with an
     * unusual header, degrades to [DiffLine.Kind.META] lines instead of throwing.
     * A diff viewer that crashes on an odd patch is worse than one that shows it
     * plainly.
     */
    fun parse(patch: String): List<DiffHunk> {
        if (patch.isBlank()) return emptyList()

        val hunks = mutableListOf<DiffHunk>()
        var currentHeader: String? = null
        var currentLines = mutableListOf<DiffLine>()

        // Line counters advance only on context/added/removed lines, which is what
        // makes the gutter numbers correct across hunks.
        var oldNo = 0
        var newNo = 0

        fun flush() {
            val header = currentHeader ?: return
            hunks += DiffHunk(header = header, lines = currentLines.toList())
            currentLines = mutableListOf()
        }

        patch.split('\n').forEach { raw ->
            val line = raw.removeSuffix("\r")
            val header = hunkHeader.find(line)
            when {
                header != null -> {
                    flush()
                    currentHeader = line
                    oldNo = header.groupValues[1].toIntOrNull() ?: 0
                    newNo = header.groupValues[3].toIntOrNull() ?: 0
                }

                // File headers carry no line content; the file name is shown separately.
                line.startsWith("--- ") || line.startsWith("+++ ") ||
                    line.startsWith("diff ") || line.startsWith("index ") -> Unit

                line.startsWith("@@") -> {
                    // Malformed hunk header: keep it visible rather than dropping it.
                    currentLines += DiffLine(DiffLine.Kind.META, line)
                }

                currentHeader == null -> Unit // preamble before the first hunk

                line.startsWith("+") -> {
                    currentLines += DiffLine(DiffLine.Kind.ADDED, line.substring(1), newLine = newNo)
                    newNo++
                }

                line.startsWith("-") -> {
                    currentLines += DiffLine(DiffLine.Kind.REMOVED, line.substring(1), oldLine = oldNo)
                    oldNo++
                }

                line.startsWith(" ") || line.isEmpty() -> {
                    // A blank line inside a hunk is a context line whose space was
                    // trimmed by some producers; treating it as context keeps counts right.
                    currentLines += DiffLine(
                        DiffLine.Kind.CONTEXT,
                        line.removePrefix(" "),
                        oldLine = oldNo,
                        newLine = newNo,
                    )
                    oldNo++
                    newNo++
                }

                else -> currentLines += DiffLine(DiffLine.Kind.META, line)
            }
        }
        flush()
        return hunks
    }

    /** Counts of added and removed lines, for a summary line. */
    fun counts(hunks: List<DiffHunk>): Pair<Int, Int> {
        var added = 0
        var removed = 0
        hunks.forEach { hunk ->
            hunk.lines.forEach { line ->
                when (line.kind) {
                    DiffLine.Kind.ADDED -> added++
                    DiffLine.Kind.REMOVED -> removed++
                    else -> Unit
                }
            }
        }
        return added to removed
    }
}
