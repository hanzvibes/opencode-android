package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.ui.browser.isForwardableUrl

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
    /**
     * Formatting spans hold child spans, not a raw String (04-F2): a link
     * or code inside bold — `**see [docs](…) for `rm -rf`** — parses
     * recursively, so the link stays clickable and the code stays
     * monospace instead of rendering as raw `[...](...)` markup.
     */
    data class Bold(val spans: List<MdSpan>) : MdSpan
    data class Italic(val spans: List<MdSpan>) : MdSpan
    data class Code(val text: String) : MdSpan
    data class Link(val spans: List<MdSpan>, val url: String) : MdSpan
    data class Highlight(val spans: List<MdSpan>) : MdSpan
    data class Strikethrough(val spans: List<MdSpan>) : MdSpan
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
     *
     * `tel:` and `mailto:` use the same gate as the tap handler and the
     * in-app browser ([isForwardableUrl], audit-100 53-F2): a `tel:` link
     * is only a number — digits, spaces, parentheses, dots, dashes, with
     * an optional leading `+`. MMI/USSD characters (`*`, `#`, `,`, `;`)
     * never belong in a dialled number, so `tel:*#06#` is not even
     * rendered as a link. The tap handler already refused it (Fase 5);
     * this closes the render side too, so the link never looks tappable.
     */
    internal fun isOpenableLink(url: String): Boolean {
        val scheme = url.substringBefore(':').lowercase()
        if (scheme !in openableSchemes) return false
        return when (scheme) {
            "tel", "mailto" -> isForwardableUrl(url)
            else -> url.length > scheme.length + 1
        }
    }

    /**
     * True when [line] closes a fenced code block opened with [marker]
     * (04-F3). CommonMark: the closing fence is a run of the same fence
     * character, at least as long as the opening fence, with no info
     * string — so a ``` line must NOT close a ```` block, and a
     * ```kotlin line must not close anything. The old check,
     * `startsWith(marker.take(3))`, closed on both: a nested example
     * (````markdown containing ```kotlin … ```) was cut off mid-block,
     * and Copy/Save only copied the truncated part.
     */
    internal fun isClosingFence(line: String, marker: String): Boolean {
        val trimmed = line.trim()
        val fenceChar = marker.first()
        return trimmed.length >= marker.length && trimmed.all { it == fenceChar }
    }

    /**
     * Parses [text] into blocks.
     *
     * Never throws: malformed markup degrades to literal text, because a chat
     * message that fails to render is worse than one that renders plainly.
     *
     * [streaming] is true while the text is still arriving (04-F7): markup
     * that is not closed yet is rendered as if closed at the end of the
     * text so far — an unclosed fence's body stays readable prose instead
     * of swallowing the rest of the answer into one monospace block, and
     * an unclosed `**` shows its words bold instead of raw asterisks. The
     * final parse (streaming = false) is unchanged: unclosed markup falls
     * back to literal text, so nothing is ever lost.
     */
    fun parse(text: String, streaming: Boolean = false): List<MdBlock> {
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
            if (joined.isNotEmpty()) blocks += MdBlock.Paragraph(inline(joined, streaming))
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
                val bodyLines = mutableListOf<String>()
                i++
                while (i < lines.size && !isClosingFence(lines[i], marker)) {
                    bodyLines += lines[i]
                    i++
                }
                val closed = i < lines.size
                // Skip the closing fence when present.
                if (closed) i++
                if (!closed && streaming) {
                    // 04-F7: the fence is not closed yet and more text is
                    // coming — its body is prose-in-progress, not a code
                    // block, so the rest of the answer is not swallowed
                    // into monospace until the closer arrives (or the
                    // final parse, which keeps the old code-block reading).
                    paragraph += bodyLines
                } else {
                    // Models often open a fence with a blank line; shown, copied and saved it is noise.
                    val code = bodyLines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
                    blocks += MdBlock.Code(lang, code.joinToString("\n"))
                }
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
                    spans = inline(headingText, streaming),
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
                    blocks += MdBlock.Blockquote(inline(quoteText, streaming))
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
                    header = header.map { inline(it, streaming) },
                    rows = rows.map { row ->
                        // Pad or trim so every row has the header's width; the renderer
                        // relies on it to keep columns square.
                        (0 until width).map { inline(row.getOrElse(it) { "" }, streaming) }
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
                    items += inline(itemLines.joinToString(" "), streaming)
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
                    items += inline(itemLines.joinToString(" "), streaming)
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

    /**
     * Splits a table row into trimmed cells, dropping the optional outer pipes.
     *
     * A pipe inside an inline code span (`|`a\|b``) or escaped as `\|` is part
     * of the cell, not a separator (04-F4) — a plain `split('|')` cut those
     * cells in two.
     */
    internal fun splitCells(line: String): List<String> {
        val trimmed = line.trim().removePrefix("|").removeSuffix("|")
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        var inCode = false
        var codeRun = 0
        while (i < trimmed.length) {
            val c = trimmed[i]
            when {
                c == '\\' && i + 1 < trimmed.length &&
                    (trimmed[i + 1] == '|' || trimmed[i + 1] == '\\') -> {
                    current.append(trimmed[i + 1])
                    i += 2
                }
                c == '`' -> {
                    var run = 0
                    while (i + run < trimmed.length && trimmed[i + run] == '`') run++
                    if (!inCode) {
                        inCode = true
                        codeRun = run
                    } else if (run == codeRun) {
                        inCode = false
                        codeRun = 0
                    }
                    repeat(run) { current.append('`') }
                    i += run
                }
                c == '|' && !inCode -> {
                    cells += current.toString().trim()
                    current.setLength(0)
                    i++
                }
                else -> {
                    current.append(c)
                    i++
                }
            }
        }
        cells += current.toString().trim()
        return cells
    }

    /**
     * Splits one line of prose into inline spans.
     *
     * Order matters: code first, so `**not bold**` inside backticks stays literal,
     * then links, then emphasis.
     */
    /**
     * Splits one line of prose into inline spans.
     *
     * Order matters: code first, so `**not bold**` inside backticks stays literal,
     * then links, then emphasis. Formatting spans recurse into [inline] so
     * nested markup parses (04-F2).
     *
     * [streaming] (04-F7): while the text is still arriving, a delimiter with
     * no closer yet is treated as closed at the end of the text so far, so
     * the words render formatted instead of raw. The final parse is
     * unchanged — unclosed markup falls back to literal text.
     */
    fun inline(text: String, streaming: Boolean = false): List<MdSpan> {
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
                // Inline code: a run of N backticks closes only on a run of
                // exactly N (04-F5) — `` ``code`` `` used to leave a stray
                // backtick on each side.
                c == '`' -> {
                    var run = 1
                    while (i + run < text.length && text[i + run] == '`') run++
                    val end = findCodeClose(text, i + run, run)
                    if (end >= 0) {
                        flushPlain()
                        var content = text.substring(i + run, end)
                        // CommonMark: one leading + one trailing space are
                        // stripped when the content both starts and ends
                        // with a space and is not all spaces.
                        if (content.length > 2 && content.startsWith(' ') && content.endsWith(' ') &&
                            content.any { it != ' ' }
                        ) {
                            content = content.substring(1, content.length - 1)
                        }
                        spans += MdSpan.Code(content)
                        i = end + run
                    } else if (streaming && i + run < text.length) {
                        flushPlain()
                        spans += MdSpan.Code(text.substring(i + run))
                        i = text.length
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
                        spans += MdSpan.Bold(inline(text.substring(i + 2, end), streaming))
                        i = end + 2
                    } else if (streaming && i + 2 < text.length && text[i + 2] != ' ' && text[i + 2] != c) {
                        flushPlain()
                        spans += MdSpan.Bold(inline(text.substring(i + 2), streaming))
                        i = text.length
                    } else {
                        plain.append(c); i++
                    }
                }

                // Strikethrough: ~~...~~
                c == '~' && i + 1 < text.length && text[i + 1] == '~' -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i + 2) {
                        flushPlain()
                        spans += MdSpan.Strikethrough(inline(text.substring(i + 2, end), streaming))
                        i = end + 2
                    } else if (streaming && i + 2 < text.length && text[i + 2] != ' ' && text[i + 2] != '~') {
                        flushPlain()
                        spans += MdSpan.Strikethrough(inline(text.substring(i + 2), streaming))
                        i = text.length
                    } else {
                        plain.append(c); i++
                    }
                }

                // Highlight: ==...== or <mark>...</mark>
                c == '=' && i + 1 < text.length && text[i + 1] == '=' -> {
                    val end = text.indexOf("==", i + 2)
                    if (end > i + 2) {
                        flushPlain()
                        spans += MdSpan.Highlight(inline(text.substring(i + 2, end), streaming))
                        i = end + 2
                    } else if (streaming && i + 2 < text.length && text[i + 2] != ' ' && text[i + 2] != '=') {
                        flushPlain()
                        spans += MdSpan.Highlight(inline(text.substring(i + 2), streaming))
                        i = text.length
                    } else {
                        plain.append(c); i++
                    }
                }

                c == '<' && text.startsWith("<mark>", i, ignoreCase = true) -> {
                    val end = text.indexOf("</mark>", i + 6, ignoreCase = true)
                    if (end > i + 6) {
                        flushPlain()
                        spans += MdSpan.Highlight(inline(text.substring(i + 6, end), streaming))
                        i = end + 7
                    } else if (streaming && i + 6 < text.length && text[i + 6] != ' ') {
                        flushPlain()
                        spans += MdSpan.Highlight(inline(text.substring(i + 6), streaming))
                        i = text.length
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
                        spans += MdSpan.Link(listOf(MdSpan.Text(url)), url)
                        i += url.length
                    } else {
                        plain.append(c); i++
                    }
                }

                // Link: [text](url) — see [parseLink] for the details
                // (04-F1 parens in the URL, 04-F8 `]` in the text, titles,
                // `<...>` destinations).
                c == '[' -> {
                    when (val link = parseLink(text, i, streaming)) {
                        null -> {
                            plain.append(c); i++
                        }
                        else -> {
                            flushPlain()
                            if (link.url != null) {
                                spans += MdSpan.Link(link.spans, link.url)
                            } else {
                                // Streaming fallback: an unclosed link renders
                                // its words as plain text; the partial URL is
                                // markup-in-progress, not words.
                                spans += link.spans
                            }
                            i = link.endIndex
                        }
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
                        spans += MdSpan.Italic(inline(text.substring(i + 1, end), streaming))
                        i = end + 1
                    } else if (streaming && openerOk && i + 1 < text.length && text[i + 1] != ' ') {
                        flushPlain()
                        spans += MdSpan.Italic(inline(text.substring(i + 1), streaming))
                        i = text.length
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

    /** Index of the next backtick run of exactly [run] backticks at or after [start], or -1. */
    private fun findCodeClose(text: String, start: Int, run: Int): Int {
        var j = start
        while (j < text.length) {
            if (text[j] == '`') {
                var len = 0
                while (j + len < text.length && text[j + len] == '`') len++
                if (len == run) return j
                j += len
            } else {
                j++
            }
        }
        return -1
    }

    /**
     * One `[text](destination)` starting at [i], or null when the text at
     * [i] is not a link (the caller then renders the `[` literally).
     *
     * 04-F1: the destination is scanned with paren depth, so a URL with
     * balanced parens — `https://en.wikipedia.org/wiki/Kotlin_(programming_language)`
     * — is not cut at the first `)`. 04-F8: the link text is scanned with
     * bracket depth, so `]` inside it does not end the text; destinations
     * may be `<...>` (spaces and parens allowed) and an optional `"title"`,
     * `'title'` or `(title)` after the destination is consumed, not shown.
     * The link text itself is parsed recursively, so `[**bold** text](url)`
     * keeps its formatting (04-F2).
     *
     * A [LinkParse] with a null [LinkParse.url] is the streaming fallback
     * (04-F7): the link is not closed yet, so its words render as plain
     * text and the partial destination is dropped until it completes.
     */
    private data class LinkParse(val spans: List<MdSpan>, val url: String?, val endIndex: Int)

    private fun parseLink(text: String, i: Int, streaming: Boolean): LinkParse? {
        // Link text with bracket depth: a nested `[` does not confuse the `]`.
        var depth = 1
        var j = i + 1
        var close = -1
        while (j < text.length) {
            val c = text[j]
            if (c == '\\' && j + 1 < text.length) {
                j += 2
                continue
            }
            if (c == '[') depth++
            if (c == ']') {
                depth--
                if (depth == 0) {
                    close = j
                    break
                }
            }
            j++
        }
        if (close < 0) {
            if (streaming && i + 1 < text.length) {
                return LinkParse(inline(text.substring(i + 1), streaming), null, text.length)
            }
            return null
        }
        if (close + 1 >= text.length || text[close + 1] != '(') return null
        val linkText = text.substring(i + 1, close)
        // Destination: `<...>` or a paren-balanced run, then an optional title.
        var k = close + 2
        while (k < text.length && (text[k] == ' ' || text[k] == '\t')) k++
        if (k >= text.length) {
            if (streaming) return LinkParse(inline(linkText, streaming), null, text.length)
            return null
        }
        val url: String
        var end: Int
        if (text[k] == '<') {
            val gt = text.indexOf('>', k + 1)
            if (gt < 0) {
                if (streaming) return LinkParse(inline(linkText, streaming), null, text.length)
                return null
            }
            url = text.substring(k + 1, gt)
            end = gt + 1
        } else {
            var parenDepth = 1
            var m = k
            var destEnd = -1
            while (m < text.length) {
                val c = text[m]
                if (c == '\\' && m + 1 < text.length) {
                    m += 2
                    continue
                }
                if (c == '(') parenDepth++
                if (c == ')') {
                    parenDepth--
                    if (parenDepth == 0) {
                        destEnd = m
                        break
                    }
                }
                if (c.isWhitespace()) break
                m++
            }
            if (destEnd >= 0) {
                url = text.substring(k, destEnd)
                end = destEnd
            } else if (m >= text.length) {
                // Ran out of text with no closing ')': the link is
                // incomplete (04-F7). Streaming shows the words; a final
                // parse keeps the old behaviour and leaves it literal.
                if (streaming) return LinkParse(inline(linkText, streaming), null, text.length)
                return null
            } else {
                // Whitespace ended the destination — a title may follow.
                url = text.substring(k, m)
                end = m
            }
        }
        // Optional title: "..." / '...' / (...) then the closing ')'.
        var t = end
        while (t < text.length && (text[t] == ' ' || text[t] == '\t')) t++
        if (t < text.length && (text[t] == '"' || text[t] == '\'' || text[t] == '(')) {
            val openTitle = text[t]
            val closeTitle = if (openTitle == '(') ')' else openTitle
            var m = t + 1
            var titleEnd = -1
            var titleParenDepth = 0
            while (m < text.length) {
                val c = text[m]
                if (c == '\\' && m + 1 < text.length) {
                    m += 2
                    continue
                }
                if (openTitle == '(' && c == '(') titleParenDepth++
                if (c == closeTitle) {
                    if (openTitle == '(' && titleParenDepth > 0) {
                        titleParenDepth--
                        m++
                        continue
                    }
                    titleEnd = m
                    break
                }
                m++
            }
            if (titleEnd < 0) {
                if (streaming) return LinkParse(inline(linkText, streaming), null, text.length)
                return null
            }
            t = titleEnd + 1
            while (t < text.length && (text[t] == ' ' || text[t] == '\t')) t++
        }
        if (t >= text.length || text[t] != ')') {
            if (streaming) return LinkParse(inline(linkText, streaming), null, text.length)
            return null
        }
        // A URL the app cannot open is not a link at all (item 2): it renders
        // as plain text instead of crashing on tap.
        if (!isOpenableLink(url)) return null
        return LinkParse(inline(linkText, streaming), url, t + 1)
    }
}
