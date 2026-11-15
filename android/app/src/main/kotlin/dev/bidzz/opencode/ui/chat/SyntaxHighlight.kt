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
enum class TokenKind { Plain, Keyword, String, Number, Comment, Function, Type, Operator }

internal const val MAX_HIGHLIGHT_CHARS = 20_000

/** While streaming, blocks longer than this render plain until the stream finishes (M-252). */
internal const val STREAMING_HIGHLIGHT_CHARS = 1_000

/** One run of characters sharing a [TokenKind]. */
data class Token(val text: String, val kind: TokenKind)

/**
 * Colours [code] as the language [language] names, or leaves it plain when unknown.
 *
 * The input is whatever the model streamed, so it is frequently mid-statement: an
 * unterminated string stops at the end of its line rather than running to the end of
 * the block, which keeps one stray quote from colouring everything below it.
 *
 * While [streaming], a block over 1000 characters is rendered plain until the
 * stream finishes: re-highlighting the whole block on every delta is the
 * expensive part, and a half-coloured block is no better than a plain one.
 * The threshold used to be 4000 (audit-200 C17, M-252): a 20-line snippet —
 * the common case, and the one the 30-line collapse does not protect —
 * was re-highlighted ~10 times a second for its whole stream. Short
 * snippets still colour live; the full highlight runs once, on completion.
 */
fun highlight(language: String?, code: String, streaming: Boolean = false): List<Token> {
    if (code.isEmpty()) return emptyList()
    // ponytail: past this size colouring is skipped; it runs on the UI thread on every
    // streamed delta. Move to a background dispatcher if big blocks need colour.
    if (code.length > MAX_HIGHLIGHT_CHARS) return listOf(Token(code, TokenKind.Plain))
    if (streaming && code.length > STREAMING_HIGHLIGHT_CHARS) return listOf(Token(code, TokenKind.Plain))
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
        if (dialect.dashDash && c == '-' && code.regionMatches(i + 1, "-", 0, 1)) {
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
            // An unterminated block comment stops at the end of its line,
            // like an unterminated string: one stray "/*" mid-stream must
            // not paint the whole rest of the block as a comment.
            val end = if (close < 0) {
                code.indexOf('\n', i).let { if (it < 0) code.length else it }
            } else {
                close + 2
            }
            tokens.add(code.substring(i, end), TokenKind.Comment)
            i = end
            continue
        }

        if (c == '`' && dialect.template) {
            val end = scanTemplate(code, i)
            tokens.add(code.substring(i, end), TokenKind.String)
            i = end
            continue
        }
        if (dialect.angle && c == '<' && i + 1 < code.length &&
            (code[i + 1].isLetter() || code[i + 1] == '/' || code[i + 1] == '!')
        ) {
            i = scanTag(code, i, tokens)
            continue
        }

        if (c == '"' || (c == '\'' && dialect.singleQuote)) {
            if (c == '\'' && dialect.lifetime && isLifetime(code, i)) {
                // `&'a`, `<'a>`: the quote belongs to the lifetime, not to a
                // string — a char literal (`'a'`) still falls through below.
                tokens.add(c.toString(), TokenKind.Plain)
                i++
                continue
            }
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
            val lookup = if (dialect.caseInsensitiveKeywords) word.lowercase() else word
            val kind = when {
                lookup in dialect.keywords -> TokenKind.Keyword
                // A word followed by "(" is a call: `foo()`, `obj.bar()`.
                // Keywords win above, so `if (` / `fun foo(` stay keywords.
                isCallStart(code, end) -> TokenKind.Function
                // Capitalised words are type/class names in the dialects
                // here (`String`, `Foo`); constants read as types too, which
                // is the accepted cost of a one-pass heuristic.
                word[0].isUpperCase() -> TokenKind.Type
                else -> TokenKind.Plain
            }
            tokens.add(word, kind)
            i = end
            continue
        }

        // Operators are single characters; adjacent runs merge in TokenList,
        // so `==` / `->` / `&&` stay one run. Comment/string openers are
        // handled above, so `/` here is division, never a comment start.
        if (c in OperatorChars) {
            tokens.add(c.toString(), TokenKind.Operator)
            i++
            continue
        }

        tokens.add(c.toString(), TokenKind.Plain)
        i++
    }
    return tokens.toList()
}

/** True when the quote at [start] opens a Rust lifetime, not a char literal. */
private fun isLifetime(code: String, start: Int): Boolean {
    var j = start + 1
    if (j >= code.length || !(code[j].isLetter() || code[j] == '_')) return false
    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
    return j >= code.length || code[j] != '\''
}

/** True when the next non-blank character after [end] opens a call. */
private fun isCallStart(code: String, end: Int): Boolean {
    var j = end
    while (j < code.length && (code[j] == ' ' || code[j] == '\t')) j++
    return j < code.length && code[j] == '('
}

private val OperatorChars = setOf(
    '+', '-', '*', '/', '%', '=', '<', '>', '!', '&', '|', '^', '~', '?', ':',
)

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

/**
 * End of the template literal starting at [start], exclusive.
 *
 * A template may span lines, so — like a triple-quoted string — an
 * unterminated one runs to the end of the input rather than stopping at
 * the line break: stopping mid-template would paint the rest of the
 * block as code. `${…}` interpolation is coloured as one string run for
 * now, the same limitation as Kotlin `"""` interpolation today.
 */
private fun scanTemplate(code: String, start: Int): Int {
    var j = start + 1
    while (j < code.length) {
        val ch = code[j]
        if (ch == '\\' && j + 1 < code.length) {
            j += 2
            continue
        }
        if (ch == '`') return j + 1
        j++
    }
    return code.length
}

/**
 * Colours one `<tag …>` run (audit-200 D2, m-1148): `<!-- … -->` is a
 * comment, the tag name is a Type, attribute names are Keywords and
 * quoted values are Strings. Returns the end index, or [start] + 1 when
 * no `>` closes the tag before the line ends — a stray `<` in text is
 * not a tag, and a `>` inside a quoted value does not close it.
 */
private fun scanTag(code: String, start: Int, tokens: TokenList): Int {
    if (code.startsWith("<!--", start)) {
        val close = code.indexOf("-->", start + 4)
        val end = if (close < 0) {
            code.indexOf('\n', start).let { if (it < 0) code.length else it }
        } else {
            close + 3
        }
        tokens.add(code.substring(start, end), TokenKind.Comment)
        return end
    }
    var j = start + 1
    var quote: Char? = null
    var gt = -1
    while (j < code.length) {
        val ch = code[j]
        if (quote != null) {
            if (ch == quote) quote = null
        } else if (ch == '"' || ch == '\'') {
            quote = ch
        } else if (ch == '>') {
            gt = j
            break
        } else if (ch == '\n') {
            break
        }
        j++
    }
    if (gt < 0) return start + 1
    tokens.add("<", TokenKind.Operator)
    j = start + 1
    if (j < gt && code[j] == '/') {
        tokens.add("/", TokenKind.Operator)
        j++
    }
    val nameStart = j
    while (j < gt && (code[j].isLetterOrDigit() || code[j] == '-' || code[j] == ':')) j++
    if (j > nameStart) tokens.add(code.substring(nameStart, j), TokenKind.Type)
    while (j < gt) {
        val ch = code[j]
        when {
            ch == '"' || ch == '\'' -> {
                val end = scanString(code, j, ch, false)
                tokens.add(code.substring(j, end), TokenKind.String)
                j = end
            }
            ch.isLetter() || ch == '_' -> {
                var e = j
                while (e < gt && (code[e].isLetterOrDigit() || code[e] == '_' || code[e] == '-' || code[e] == ':')) e++
                tokens.add(code.substring(j, e), TokenKind.Keyword)
                j = e
            }
            ch in OperatorChars -> {
                tokens.add(ch.toString(), TokenKind.Operator)
                j++
            }
            else -> {
                tokens.add(ch.toString(), TokenKind.Plain)
                j++
            }
        }
    }
    tokens.add(">", TokenKind.Operator)
    return gt + 1
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
    /** Rust lifetimes (`'a`): a quote followed by an identifier that no closing quote follows. */
    val lifetime: Boolean = false,
    /** SQL `--` line comments (audit-200 D2, m-1148). */
    val dashDash: Boolean = false,
    /** JS/TS template literals: backtick strings that may span lines (audit-200 D2, m-1148). */
    val template: Boolean = false,
    /** HTML/XML tags: `<name attr="…">` runs (audit-200 D2, m-1148). */
    val angle: Boolean = false,
    /** Keyword lookup ignores case — SQL is case-insensitive (audit-200 D2, m-1148). */
    val caseInsensitiveKeywords: Boolean = false,
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

private val RubyKeywords = setOf(
    "alias", "and", "begin", "break", "case", "class", "def", "defined?", "do",
    "else", "elsif", "end", "ensure", "false", "for", "if", "in", "module",
    "next", "nil", "not", "or", "redo", "require", "rescue", "retry", "return",
    "self", "super", "then", "true", "undef", "unless", "until", "when",
    "while", "yield",
)

private val DialectClike = Dialect(ClikeKeywords, lineSlash = true, block = true)
/**
 * Audit-200 D1 (m-077): Kotlin/Java text blocks, Swift and Scala all have
 * triple-quoted raw strings (`"""…"""`), and Dart has `'''`/`"""` — the
 * plain C-like dialect would swallow the first `""` as an empty string and
 * open a runaway string at the third quote.
 */
private val DialectClikeTriple = Dialect(ClikeKeywords, lineSlash = true, block = true, triple = true)
/** Audit-200 D1 (m-079): Rust lifetimes (`'a`) are not string openers. */
private val DialectRust = Dialect(ClikeKeywords, lineSlash = true, block = true, lifetime = true)
private val DialectPython = Dialect(PythonKeywords, hash = true, triple = true)
private val DialectShell = Dialect(ShellKeywords, hash = true)
/** YAML, TOML, Dockerfile and friends: comments and strings, no reserved words. */
private val DialectHashData = Dialect(emptySet(), hash = true)
private val DialectJson = Dialect(JsonKeywords, singleQuote = false)
private val DialectJsonc = Dialect(JsonKeywords, lineSlash = true, singleQuote = false)
private val DialectRuby = Dialect(RubyKeywords, hash = true)

/**
 * Audit-200 D2 (m-1148): SQL had no dialect, so `SELECT` blocks rendered
 * plain even though the pill and the Save action both recognised the tag.
 * Keywords are lowercase-only and looked up case-insensitively — SQL is
 * case-insensitive, and doubling the set for upper case would be noise.
 */
private val SqlKeywords = setOf(
    "select", "from", "where", "and", "or", "not", "null", "insert", "into",
    "values", "update", "set", "delete", "create", "table", "join", "on",
    "group", "by", "order", "limit", "as", "distinct", "left", "right",
    "inner", "outer", "full", "cross", "union", "all", "having", "exists",
    "in", "is", "between", "like", "case", "when", "then", "else", "end",
    "primary", "key", "foreign", "references", "index", "alter", "drop",
    "add", "column", "constraint", "unique", "check", "default", "if",
    "begin", "commit", "rollback", "transaction", "grant", "revoke",
    "truncate", "view", "procedure", "function", "trigger", "database",
    "schema", "use", "show", "describe", "explain", "with", "recursive",
    "over", "partition", "count", "sum", "avg", "min", "max",
)
private val DialectSql = Dialect(SqlKeywords, dashDash = true, caseInsensitiveKeywords = true)

/**
 * Audit-200 D2 (m-1148): CSS property words only — selectors stay plain,
 * which is the honest first pass: a property set this size colours the
 * declarations that matter without painting prose. Words are single
 * tokens because the scanner splits on `-` (`background-color` scans as
 * two words), so multi-word properties are listed as their parts.
 */
private val CssKeywords = setOf(
    "color", "background", "display", "margin", "padding", "border", "font",
    "size", "family", "weight", "width", "height", "position", "top", "left",
    "right", "bottom", "flex", "grid", "align", "items", "justify", "content",
    "gap", "overflow", "index", "opacity", "transition", "transform",
    "cursor", "text", "line", "letter", "spacing", "box", "sizing", "float",
    "clear", "visibility", "white", "decoration", "radius", "shadow",
    "outline", "list", "style", "vertical", "direction", "wrap", "basis",
    "grow", "shrink", "order", "repeat", "auto", "none", "solid", "block",
    "inline", "relative", "absolute", "fixed", "sticky", "hidden", "visible",
    "important",
)
private val DialectCss = Dialect(CssKeywords, block = true)

/** Audit-200 D2 (m-1148): HTML/XML — tags only; text content stays plain. */
private val DialectHtml = Dialect(emptySet(), angle = true)

/**
 * Audit-200 D2 (m-1148): JS/TS with backtick template literals. Kept
 * separate from [DialectClike] on purpose — backtick is not valid code in
 * C/Java, so giving the shared dialect template strings would paint a
 * stray backtick there as a string opener.
 */
private val DialectJs = Dialect(ClikeKeywords, lineSlash = true, block = true, template = true)

private fun dialectOf(language: String?): Dialect? {
    // Strip a fence filename/path suffix first: ```kotlin:Main.kt and
    // ```python /app/main.py name the language, and the suffix used to
    // silently kill highlighting (no dialect matched "kotlin:main.kt").
    // Same stripping as formatLanguageName() in MarkdownText.kt.
    val tag = language?.trim()?.substringBefore(' ')?.substringBefore(':')?.lowercase()
    return when (tag) {
    "kt", "kts", "kotlin", "java", "swift", "scala", "dart" -> DialectClikeTriple
    "rust", "rs" -> DialectRust
    "js", "jsx", "mjs", "ts", "tsx", "typescript", "javascript" -> DialectJs
    "go", "c", "h", "cpp", "cc", "cxx", "hpp",
    "c++", "cs", "c#", "csharp", "php" -> DialectClike
    "sql" -> DialectSql
    "css", "scss" -> DialectCss
    "html", "htm", "xml" -> DialectHtml
    "py", "python" -> DialectPython
    "rb", "ruby" -> DialectRuby
    "sh", "bash", "zsh", "shell", "console", "terminal" -> DialectShell
    // Audit-200 D4 (m-076): "docker" is a Dockerfile, like "dockerfile" —
    // it used to highlight as plain text while its label said Dockerfile.
    "yaml", "yml", "toml", "dockerfile", "docker", "ini", "conf", "properties" -> DialectHashData
    "json" -> DialectJson
    "jsonc" -> DialectJsonc
    // No tag, or one this does not know: plain text. Deliberate — an unrecognised tag
    // is more likely to be prose-ish than to share C's comment syntax.
    else -> null
    }
}
