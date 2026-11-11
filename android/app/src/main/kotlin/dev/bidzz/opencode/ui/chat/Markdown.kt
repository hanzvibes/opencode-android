package dev.bidzz.opencode.ui.chat

/**
 * Minimal markdown for chat messages.
 *
 * Written rather than pulled from a library because none is available offline here,
 * and because a chat transcript needs a small, predictable subset. What it handles:
 *
 * - fenced code blocks (``` or ~~~), with an optional language tag
 * - inline code (`code`)
 * - bold (**text** or __text__), italic (*text* or _text_), strikethrough (~~text~~)
 * - highlighted text (==text== or <mark>text</mark>)
 * - headings (# .. ######)
 * - blockquotes (> text)
 * - unordered and ordered lists
 * - links ([text](url)) and bare URLs (https://… autolinks)
 * - horizontal rules / dividers (---, ***, ___ or <hr>)
 * - pipe tables with a `| --- | --- |` separator row
 *
 * What it deliberately does **not** handle: images, nested lists, HTML, footnotes,
 * task lists, merged cells. A table needs a header row plus a
 * separator row; anything less is left as literal text, because rendering a
 * half-recognised table is worse than showing the pipes. Column alignment
 * (`:---:`, `---:`) is read from the separator row and kept on the table.
 *
 * Parsing is line-oriented, which is enough for the subset above and keeps the
 * renderer free of a second AST pass.
 */
sealed interface MdBlock {
    /** A paragraph of prose; may contain inline spans. */
    data class Paragraph(val spans: List<MdSpan>) : MdBlock

    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock

    data class Code(val language: String?, val code: String) : MdBlock

    /** A quoted block of text. */
    data class Blockquote(val spans: List<MdSpan>) : MdBlock

    data class Bullet(val items: List<List<MdSpan>>) : MdBlock

    data class Numbered(val items: List<List<MdSpan>>, val start: Int = 1) : MdBlock

    /** A horizontal rule. */
    data object Rule : MdBlock

    /**
     * A pipe table. Every row holds exactly [columns] cells; short rows are padded
     * rather than rejected, since models drop trailing pipes constantly.
     * [alignments] holds one [TableAlign] per column, read from the separator
     * row (`:---:` centre, `---:` right, anything else left); missing entries
     * are left-aligned.
     */
    data class Table(
        val header: List<List<MdSpan>>,
        val rows: List<List<List<MdSpan>>>,
        val alignments: List<TableAlign> = emptyList(),
    ) : MdBlock {
        val columns: Int get() = header.size
    }
}

/** Horizontal alignment of one table column, read from the separator row. */
enum class TableAlign {
    LEFT,
    CENTER,
    RIGHT,
}

/** Inline formatting inside a block. */
sealed interface MdSpan {
    data class Text(val text: String) : MdSpan
    data class Bold(val text: String) : MdSpan
    data class Italic(val text: String) : MdSpan
    data class Code(val text: String) : MdSpan
    data class Link(val text: String, val url: String) : MdSpan
    data class Highlight(val text: String) : MdSpan
    data class Strikethrough(val text: String) : MdSpan
}

object Markdown {

    private val fence = Regex("^\\s*(```+|~~~+)\\s*(.*)$")
    private val heading = Regex("^\\s*(#{1,6})\\s+(.*)$")
    private val blockquote = Regex("^\\s*>\\s?(.*)$")
    private val bullet = Regex("^\\s*[-*+•●]\\s+(.*)$")
    private val numbered = Regex("^\\s*(\\d+)[.)]\\s+(.*)$")
    private val rule = Regex("^\\s*([-*_])\\s*\\1\\s*\\1[\\s\\-*_]*$")
    private val hrTag = Regex("^\\s*<hr\\s*/?>\\s*$", RegexOption.IGNORE_CASE)

    /** Schemes a model-written link may open (item 2). */
    private val openableSchemes = setOf("http", "https", "mailto", "tel")

    /**
     * True when [url] names a destination this app can actually open.
     *
     * Anything else — empty, relative, `javascript:`, `intent:`, `file:` —
     * stays plain text instead of a link that crashes on tap, because the
     * default UriHandler throws when no app can handle the URI.
     */
    internal fun isOpenableLink(url: String): Boolean {
        val scheme = url.substringBefore(':').lowercase()
        return scheme in openableSchemes && url.length > scheme.length + 1
    }

    /**
     * Parses [text] into blocks.
     *
     * Never throws: malformed markup degrades to literal text, because a chat
     * message that fails to render is worse than one that renders plainly.
     */
    fun parse(text: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val lines = text.replace("\r\n", "\n").split('\n')

        var i = 0
        val paragraph = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            // Soft-wrapped prose joins with a space, like list items
            // already do; only a line ending in two spaces is a hard
            // break (its trailing spaces are dropped, the newline kept).
            val joined = buildString {
                paragraph.forEachIndexed { idx, line ->
                    if (idx > 0) {
                        append(if (paragraph[idx - 1].endsWith("  ")) "\n" else " ")
                    }
                    append(if (idx < paragraph.size - 1 && line.endsWith("  ")) line.trimEnd() else line)
                }
            }.trim()
            if (joined.isNotEmpty()) blocks += MdBlock.Paragraph(inline(joined))
            paragraph.clear()
        }

        while (i < lines.size) {
            val line = lines[i]

            // Fenced code block: consume until the matching fence or EOF.
            val fenceMatch = fence.find(line)
            if (fenceMatch != null) {
                flushParagraph()
                val marker = fenceMatch.groupValues[1]
                val lang = fenceMatch.groupValues[2].trim().ifEmpty { null }
                val body = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(marker.take(3))) {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(lines[i])
                    i++
                }
                // Skip the closing fence when present.
                if (i < lines.size) i++
                // Models often open a fence with a blank line; shown, copied and saved it is noise.
                val code = body.toString().lines().dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
                blocks += MdBlock.Code(lang, code.joinToString("\n"))
                continue
            }

            if (line.isBlank()) {
                flushParagraph()
                i++
                continue
            }

            if (rule.matches(line) || hrTag.matches(line)) {
                flushParagraph()
                blocks += MdBlock.Rule
                i++
                continue
            }

            val headingMatch = heading.find(line)
            if (headingMatch != null) {
                flushParagraph()
                // Closing ATX hashes ("## Title ##") are decoration, not
                // text — but only when separated by a space, so "C#" keeps
                // its hash.
                val headingText = headingMatch.groupValues[2].trim()
                    .replace(Regex("\\s+#+$"), "")
                blocks += MdBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    spans = inline(headingText),
                )
                i++
                continue
            }

            // Blockquote: consecutive lines starting with `>`.
            val quoteMatch = blockquote.find(line)
            if (quoteMatch != null) {
                flushParagraph()
                val quoteLines = mutableListOf<String>()
                while (i < lines.size) {
                    val m = blockquote.find(lines[i]) ?: break
                    quoteLines += m.groupValues[1]
                    i++
                }
                val quoteText = quoteLines.joinToString("\n").trim()
                if (quoteText.isNotEmpty()) {
                    blocks += MdBlock.Blockquote(inline(quoteText))
                }
                continue
            }

            // Table: a piped header row followed by a `| --- | --- |` separator.
            // Checked before the paragraph path so the header is not swallowed as prose.
            if (line.contains('|') && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
                flushParagraph()
                val header = splitCells(line)
                val width = header.size
                val alignments = splitCells(lines[i + 1]).map { cell ->
                    when {
                        cell.startsWith(':') && cell.endsWith(':') -> TableAlign.CENTER
                        cell.endsWith(':') -> TableAlign.RIGHT
                        else -> TableAlign.LEFT
                    }
                }
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                    rows += splitCells(lines[i])
                    i++
                }
                blocks += MdBlock.Table(
                    header = header.map { inline(it) },
                    rows = rows.map { row ->
                        // Pad or trim so every row has the header's width; the renderer
                        // relies on it to keep columns square.
                        (0 until width).map { inline(row.getOrElse(it) { "" }) }
                    },
                    alignments = alignments,
                )
                continue
            }

            // Lists: gather consecutive items of the same kind.
            if (bullet.containsMatchIn(line)) {
                flushParagraph()
                val items = mutableListOf<List<MdSpan>>()
                while (i < lines.size) {
                    val m = bullet.find(lines[i]) ?: break
                    val itemLines = mutableListOf(m.groupValues[1].trim())
                    i++
                    while (i < lines.size && lines[i].isNotBlank() &&
                        (lines[i].startsWith("  ") || lines[i].startsWith("\t")) &&
                        !bullet.containsMatchIn(lines[i]) && !numbered.containsMatchIn(lines[i])
                    ) {
                        itemLines += lines[i].trim()
                        i++
                    }
                    items += inline(itemLines.joinToString(" "))
                }
                blocks += MdBlock.Bullet(items)
                continue
            }

            if (numbered.containsMatchIn(line)) {
                flushParagraph()
                // The model's own start number is kept ("3." stays 3.),
                // instead of renumbering every list 1, 2, 3.
                val start = numbered.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val items = mutableListOf<List<MdSpan>>()
                while (i < lines.size) {
                    val m = numbered.find(lines[i]) ?: break
                    val itemLines = mutableListOf(m.groupValues[2].trim())
                    i++
                    while (i < lines.size && lines[i].isNotBlank() &&
                        (lines[i].startsWith("  ") || lines[i].startsWith("\t")) &&
                        !bullet.containsMatchIn(lines[i]) && !numbered.containsMatchIn(lines[i])
                    ) {
                        itemLines += lines[i].trim()
                        i++
                    }
                    items += inline(itemLines.joinToString(" "))
                }
                blocks += MdBlock.Numbered(items, start = start)
                continue
            }

            paragraph += line
            i++
        }
        flushParagraph()
        return blocks
    }

    /**
     * True when [line] is a table separator such as `| --- | :-: |`.
     *
     * Requiring a pipe keeps a bare `---` a horizontal rule, which is what the
     * `rule` pattern already claims.
     */
    private fun isTableSeparator(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.contains('|') || !trimmed.contains('-')) return false
        val cells = splitCells(trimmed)
        return cells.isNotEmpty() && cells.all { it.matches(Regex(":?-+:?")) }
    }

    /** Splits a table row into trimmed cells, dropping the optional outer pipes. */
    private fun splitCells(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    /**
     * Splits one line of prose into inline spans.
     *
     * Order matters: code first, so `**not bold**` inside backticks stays literal,
     * then links, then emphasis.
     */
    fun inline(text: String): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        var i = 0
        val plain = StringBuilder()

        fun flushPlain() {
            if (plain.isNotEmpty()) {
                spans += MdSpan.Text(plain.toString())
                plain.setLength(0)
            }
        }

        while (i < text.length) {
            val c = text[i]
            when {
                // Inline code.
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        flushPlain()
                        spans += MdSpan.Code(text.substring(i + 1, end))
                        i = end + 1
                    } else {
                        plain.append(c); i++
                    }
                }

                // Bold: **...** or __...__
                ((c == '*' && i + 1 < text.length && text[i + 1] == '*') ||
                    (c == '_' && i + 1 < text.length && text[i + 1] == '_')) -> {
                    val delim = if (c == '*') "**" else "__"
                    val end = text.indexOf(delim, i + 2)
                    if (end > i + 2) {
                        flushPlain()
                        spans += MdSpan.Bold(text.substring(i + 2, end))
                        i = end + 2
                    } else {
                        plain.append(c); i++
                    }
                }

                // Strikethrough: ~~...~~
                c == '~' && i + 1 < text.length && text[i + 1] == '~' -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i + 2) {
                        flushPlain()
                        spans += MdSpan.Strikethrough(text.substring(i + 2, end))
                        i = end + 2
                    } else {
                        plain.append(c); i++
                    }
                }

                // Highlight: ==...== or <mark>...</mark>
                c == '=' && i + 1 < text.length && text[i + 1] == '=' -> {
                    val end = text.indexOf("==", i + 2)
                    if (end > i + 2) {
                        flushPlain()
                        spans += MdSpan.Highlight(text.substring(i + 2, end))
                        i = end + 2
                    } else {
                        plain.append(c); i++
                    }
                }

                c == '<' && text.startsWith("<mark>", i, ignoreCase = true) -> {
                    val end = text.indexOf("</mark>", i + 6, ignoreCase = true)
                    if (end > i + 6) {
                        flushPlain()
                        spans += MdSpan.Highlight(text.substring(i + 6, end))
                        i = end + 7
                    } else {
                        plain.append(c); i++
                    }
                }

                // Autolink: a bare https://… URL becomes a link. Trailing
                // punctuation (.,;:!?) and an unmatched closing paren are
                // left as plain text so they are not swallowed into the URL.
                c == 'h' && (text.startsWith("http://", i) || text.startsWith("https://", i)) -> {
                    val match = Regex("""https?://[^\s<>"']+""").find(text, i)
                    if (match != null && match.range.first == i) {
                        var url = match.value.trimEnd('.', ',', ';', ':', '!', '?')
                        if (url.endsWith(")") && url.count { it == '(' } < url.count { it == ')' }) {
                            url = url.dropLast(1)
                        }
                        flushPlain()
                        spans += MdSpan.Link(url, url)
                        i += url.length
                    } else {
                        plain.append(c); i++
                    }
                }

                // Link: [text](url)
                c == '[' -> {
                    val close = text.indexOf(']', i + 1)
                    val open = if (close > 0) text.indexOf('(', close) else -1
                    val end = if (open > 0) text.indexOf(')', open) else -1
                    // A URL the app cannot open is not a link at all (item 2):
                    // it renders as plain text instead of crashing on tap.
                    if (close > i + 1 && open == close + 1 && end > open &&
                        isOpenableLink(text.substring(open + 1, end))
                    ) {
                        flushPlain()
                        spans += MdSpan.Link(
                            text = text.substring(i + 1, close),
                            url = text.substring(open + 1, end),
                        )
                        i = end + 1
                    } else {
                        plain.append(c); i++
                    }
                }

                // Italic: *...* or _..._
                // Guarded, or snake_case and arithmetic read as emphasis:
                // the opener must follow the start, whitespace or
                // punctuation, the closer must not follow a space, and
                // `_` must not be flanked by alphanumerics on either side
                // (my_variable_name and 2 * 3 * 4 stay literal).
                (c == '*' || c == '_') -> {
                    val prev = if (i == 0) null else text[i - 1]
                    val openerOk = prev == null || prev.isWhitespace() || !prev.isLetterOrDigit()
                    val end = text.indexOf(c, i + 1)
                    val closerOk = end > i + 1 && text[end - 1] != ' ' && text[end - 1] != '\n' &&
                        !(c == '_' && end + 1 < text.length && text[end + 1].isLetterOrDigit())
                    if (openerOk && closerOk) {
                        flushPlain()
                        spans += MdSpan.Italic(text.substring(i + 1, end))
                        i = end + 1
                    } else {
                        plain.append(c); i++
                    }
                }

                else -> {
                    plain.append(c); i++
                }
            }
        }
        flushPlain()
        return spans
    }
}
