package dev.bidzz.opencode.ui.chat

/**
 * A single-pass syntax highlighter for fenced code blocks.
 *
 * Written rather than pulled from a library for the same reason the markdown parser
 * was (§2.26): nothing is available offline here, and a chat transcript only needs
 * enough colour to separate code's parts at a glance — not a parser.
 *
 * Two rules the whole file is built around:
 *
 * - **Text is never lost.** Joining every token's `text` always reproduces the input,
 *   including for a language this does not know and for code that is syntactically
 *   broken.
 * - **Unknown means plain.** A language with no dialect gets no colours. Guessing on
 *   an unrecognised tag is how a highlighter ends up painting half a JSON file green
 *   because `"` happened to open a string.
 */

/** The role a run of source plays. */
enum class TokenKind { Plain, Keyword, String, Number, Comment }

internal const val MAX_HIGHLIGHT_CHARS = 20_000

/** One run of characters sharing a [TokenKind]. */
data class Token(val text: String, val kind: TokenKind)

/**
 * Colours [code] as the language [language] names, or leaves it plain when unknown.
 *
 * The input is whatever the model streamed, so it is frequently mid-statement: an
 * unterminated string stops at the end of its line rather than running to the end of
 * the block, which keeps one stray quote from colouring everything below it.
 */
fun highlight(language: String?, code: String): List<Token> {
    if (code.isEmpty()) return emptyList()
    // ponytail: past this size colouring is skipped; it runs on the UI thread on every
    // streamed delta. Move to a background dispatcher if big blocks need colour.
    if (code.length > MAX_HIGHLIGHT_CHARS) return listOf(Token(code, TokenKind.Plain))
    val dialect = dialectOf(language) ?: return listOf(Token(code, TokenKind.Plain))

    val tokens = TokenList()
    var i = 0
    while (i < code.length) {
        val c = code[i]

        if (dialect.lineSlash && c == '/' && code.regionMatches(i + 1, "/", 0, 1)) {
            val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
            tokens.add(code.substring(i, end), TokenKind.Comment)
            i = end
            continue
        }
        if (dialect.hash && c == '#') {
            val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
            tokens.add(code.substring(i, end), TokenKind.Comment)
            i = end
            continue
        }
        if (dialect.block && c == '/' && code.startsWith("/*", i)) {
            val close = code.indexOf("*/", i + 2)
            val end = if (close < 0) code.length else close + 2
            tokens.add(code.substring(i, end), TokenKind.Comment)
            i = end
            continue
        }

        if (c == '"' || (c == '\'' && dialect.singleQuote)) {
            val end = scanString(code, i, c, dialect.triple)
            tokens.add(code.substring(i, end), TokenKind.String)
            i = end
            continue
        }

        if (c.isDigit()) {
            val end = scanNumber(code, i)
            tokens.add(code.substring(i, end), TokenKind.Number)
            i = end
            continue
        }

        if (c.isLetter() || c == '_' || c == '$') {
            var end = i
            while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_' || code[end] == '$')) end++
            val word = code.substring(i, end)
            tokens.add(word, if (word in dialect.keywords) TokenKind.Keyword else TokenKind.Plain)
            i = end
            continue
        }

        tokens.add(c.toString(), TokenKind.Plain)
        i++
    }
    return tokens.toList()
}

/**
 * End of the string starting at [start], exclusive.
 *
 * A triple-quoted run may cross lines; a single-quoted one that never closes stops at
 * the line break, so unfinished code from the stream stays readable below it.
 */
private fun scanString(code: String, start: Int, quote: Char, triple: Boolean): Int {
    val marker = quote.toString().repeat(3)
    val open = if (triple && code.startsWith(marker, start)) 3 else 1
    var j = start + open
    while (j < code.length) {
        val ch = code[j]
        if (open == 1 && ch == '\\') {
            // The escaped character cannot close the string, but a `\` before a
            // newline is not an escape either.
            j += if (j + 1 < code.length && code[j + 1] != '\n') 2 else 1
            continue
        }
        if (open == 3) {
            if (code.startsWith(marker, j)) return j + 3
            j++
        } else {
            if (ch == quote) return j + 1
            if (ch == '\n') return j
            j++
        }
    }
    return code.length
}

private val HexDigits = ('0'..'9') + ('a'..'f') + ('A'..'F')

private fun scanNumber(code: String, start: Int): Int {
    var j = start
    if (code[j] == '0' && j + 1 < code.length && (code[j + 1] == 'x' || code[j + 1] == 'X')) {
        j += 2
        while (j < code.length && (code[j] in HexDigits || code[j] == '_')) j++
        return j
    }
    while (j < code.length && (code[j].isDigit() || code[j] == '_')) j++
    if (j + 1 < code.length && code[j] == '.' && code[j + 1].isDigit()) {
        j++
        while (j < code.length && (code[j].isDigit() || code[j] == '_')) j++
    }
    if (j < code.length && (code[j] == 'e' || code[j] == 'E')) {
        var k = j + 1
        if (k < code.length && (code[k] == '+' || code[k] == '-')) k++
        if (k < code.length && code[k].isDigit()) {
            while (k < code.length && code[k].isDigit()) k++
            j = k
        }
    }
    // Type suffixes are part of the literal in these languages; `1_000L`, `2.0f`.
    if (j < code.length && (code[j] == 'L' || code[j] == 'f' || code[j] == 'd' || code[j] == 'u')) j++
    return j
}

/** Keeps the token list small: adjacent runs of the same kind are merged. */
private class TokenList {
    private val out = mutableListOf<Token>()

    fun add(text: String, kind: TokenKind) {
        if (text.isEmpty()) return
        val last = out.lastOrNull()
        if (last != null && last.kind == kind) {
            out[out.size - 1] = Token(last.text + text, kind)
        } else {
            out += Token(text, kind)
        }
    }

    fun toList(): List<Token> = out
}

private class Dialect(
    val keywords: Set<String>,
    val lineSlash: Boolean = false,
    val hash: Boolean = false,
    val block: Boolean = false,
    val triple: Boolean = false,
    val singleQuote: Boolean = true,
)

private val ClikeKeywords = setOf(
    "abstract", "as", "async", "await", "break", "by", "case", "catch", "class", "companion",
    "const", "continue", "data", "default", "do", "else", "enum", "export", "extends",
    "external", "false", "final", "finally", "for", "fun", "function", "get", "if",
    "implement", "import", "in", "init", "inline", "instanceof", "interface", "internal",
    "is", "lateinit", "let", "mut", "namespace", "native", "null", "object", "open",
    "operator", "override", "package", "private", "protected", "pub", "public", "sealed",
    "set", "static", "struct", "super", "suspend", "switch", "this", "throw", "throws",
    "trait", "true", "try", "type", "typealias", "undefined", "val", "var", "vararg",
    "void", "when", "where", "while", "with", "yield",
)

private val PythonKeywords = setOf(
    "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
    "elif", "else", "except", "False", "finally", "for", "from", "global", "if", "import",
    "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return",
    "self", "True", "try", "while", "with", "yield",
)

private val ShellKeywords = setOf(
    "case", "do", "done", "elif", "else", "esac", "fi", "for", "function", "if", "in",
    "local", "return", "then", "until", "while",
)

private val JsonKeywords = setOf("true", "false", "null")

private val DialectClike = Dialect(ClikeKeywords, lineSlash = true, block = true)
private val DialectPython = Dialect(PythonKeywords, hash = true, triple = true)
private val DialectShell = Dialect(ShellKeywords, hash = true)
/** YAML, TOML, Dockerfile and friends: comments and strings, no reserved words. */
private val DialectHashData = Dialect(emptySet(), hash = true)
private val DialectJson = Dialect(JsonKeywords, singleQuote = false)
private val DialectJsonc = Dialect(JsonKeywords, lineSlash = true, singleQuote = false)

private fun dialectOf(language: String?): Dialect? = when (language?.trim()?.lowercase()) {
    "kt", "kts", "kotlin", "java", "js", "jsx", "mjs", "ts", "tsx", "typescript",
    "javascript", "swift", "rust", "rs", "go", "c", "h", "cpp", "cc", "cxx", "hpp",
    "cs", "csharp", "scala", "dart", "php" -> DialectClike
    "py", "python" -> DialectPython
    "sh", "bash", "zsh", "shell", "console" -> DialectShell
    "yaml", "yml", "toml", "dockerfile", "ini", "conf", "properties" -> DialectHashData
    "json" -> DialectJson
    "jsonc" -> DialectJsonc
    // No tag, or one this does not know: plain text. Deliberate — an unrecognised tag
    // is more likely to be prose-ish than to share C's comment syntax.
    else -> null
}
