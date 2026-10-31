package dev.bidzz.opencode.ui.chat

/**
 * Minimal markdown for chat messages.
 *
 * Written rather than pulled from a library because none is available offline here,
 * and because a chat transcript needs a small, predictable subset. What it handles:
 *
 * - fenced code blocks (``` or ~~~), with an optional language tag
 * - inline code (`code`)
 * - bold (**text**), italic (*text* or _text_)
 * - headings (# .. ###)
 * - unordered and ordered lists
 * - links ([text](url))
 *
 * What it deliberately does **not** handle: tables, images, nested lists, HTML,
 * footnotes, task lists. Pretending otherwise would be worse than the limit: the
 * model will occasionally emit a table, and it must degrade to readable plain text
 * rather than render as broken markup.
 *
 * Parsing is line-oriented, which is enough for the subset above and keeps the
 * renderer free of a second AST pass.
 */
sealed interface MdBlock {
    /** A paragraph of prose; may contain inline spans. */
    data class Paragraph(val spans: List<MdSpan>) : MdBlock

    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock

    data class Code(val language: String?, val code: String) : MdBlock

    data class Bullet(val items: List<List<MdSpan>>) : MdBlock

    data class Numbered(val items: List<List<MdSpan>>) : MdBlock

    /** A horizontal rule. */
    data object Rule : MdBlock
}

/** Inline formatting inside a block. */
sealed interface MdSpan {
    data class Text(val text: String) : MdSpan
    data class Bold(val text: String) : MdSpan
    data class Italic(val text: String) : MdSpan
    data class Code(val text: String) : MdSpan
    data class Link(val text: String, val url: String) : MdSpan
}

object Markdown {

    private val fence = Regex("^\\s*(```+|~~~+)\\s*(.*)$")
    private val heading = Regex("^\\s*(#{1,3})\\s+(.*)$")
    private val bullet = Regex("^\\s*[-*+]\\s+(.*)$")
    private val numbered = Regex("^\\s*(\\d+)[.)]\\s+(.*)$")
    private val rule = Regex("^\\s*([-*_])\\s*\\1\\s*\\1[\\s\\-*_]*$")

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
            val joined = paragraph.joinToString("\n").trim()
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
                blocks += MdBlock.Code(lang, body.toString())
                continue
            }

            if (line.isBlank()) {
                flushParagraph()
                i++
                continue
            }

            if (rule.matches(line)) {
                flushParagraph()
                blocks += MdBlock.Rule
                i++
                continue
            }

            val headingMatch = heading.find(line)
            if (headingMatch != null) {
                flushParagraph()
                blocks += MdBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    spans = inline(headingMatch.groupValues[2].trim()),
                )
                i++
                continue
            }

            // Lists: gather consecutive items of the same kind.
            if (bullet.containsMatchIn(line)) {
                flushParagraph()
                val items = mutableListOf<List<MdSpan>>()
                while (i < lines.size) {
                    val m = bullet.find(lines[i]) ?: break
                    items += inline(m.groupValues[1].trim())
                    i++
                }
                blocks += MdBlock.Bullet(items)
                continue
            }

            if (numbered.containsMatchIn(line)) {
                flushParagraph()
                val items = mutableListOf<List<MdSpan>>()
                while (i < lines.size) {
                    val m = numbered.find(lines[i]) ?: break
                    items += inline(m.groupValues[2].trim())
                    i++
                }
                blocks += MdBlock.Numbered(items)
                continue
            }

            paragraph += line
            i++
        }
        flushParagraph()
        return blocks
    }

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

                // Bold: **...**
                c == '*' && i + 1 < text.length && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        flushPlain()
                        spans += MdSpan.Bold(text.substring(i + 2, end))
                        i = end + 2
                    } else {
                        plain.append(c); i++
                    }
                }

                // Link: [text](url)
                c == '[' -> {
                    val close = text.indexOf(']', i + 1)
                    val open = if (close > 0) text.indexOf('(', close) else -1
                    val end = if (open > 0) text.indexOf(')', open) else -1
                    if (close > i + 1 && open == close + 1 && end > open) {
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
                (c == '*' || c == '_') -> {
                    val end = text.indexOf(c, i + 1)
                    if (end > i + 1) {
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
