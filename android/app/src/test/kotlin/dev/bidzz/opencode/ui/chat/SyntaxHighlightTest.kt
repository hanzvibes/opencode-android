package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The code-block highlighter.
 *
 * The tests are ordered by what would hurt: first that colouring never costs the user
 * a character, then the cases where a naive scanner colours the wrong run — and each
 * of those is a bug that looks fine until you read the output.
 */
class SyntaxHighlightTest {

    private fun kindOf(language: String?, code: String) = highlight(language, code)

    private fun kind(language: String?, needle: String, code: String): TokenKind =
        highlight(language, code).first { it.text == needle }.kind

    @Test
    fun `oversized code is left plain and intact`() {
        val big = "val x = 1\n".repeat(MAX_HIGHLIGHT_CHARS / 5)
        val tokens = highlight("kotlin", big)
        assertEquals(1, tokens.size)
        assertEquals(TokenKind.Plain, tokens[0].kind)
        assertEquals(big, tokens[0].text)
    }

    @Test
    fun `tokenising never loses a character`() {
        val samples = listOf(
            "fun main() { println(\"hi\") } // done",
            "val s = \"a\\\"b\" // still comment",
            "val u = \"http://example.com\"",
            "/* unterminated block",
            "val broken = \"no closing quote\nval next = 2",
            "x = \"\"\"multi\nline\"\"\"",
            "{\"a\": 1, \"ok\": true}",
            "# shell\nfor f in *; do echo \$f; done",
            "SELECT * FROM t -- not highlighted",
            "",
            " ",
            "emoji 🎉 in a kotlin file",
        )
        for (language in listOf("kotlin", "python", "json", "bash", "yaml", "go", null, "fortran")) {
            samples.forEach { code ->
                val joined = kindOf(language, code).joinToString("") { it.text }
                assertEquals("language=$language code=${code.take(30)}", code, joined)
            }
        }
    }

    @Test
    fun `a keyword only matches a whole word`() {
        // `before` contains `for`. Substring matching would colour identifiers and read
        // as a bug in the code, not in the highlighter.
        val code = "for (val x in before) {}"
        // Plain runs merge, so `before` is never a token of its own; what matters is that
        // it did not produce a keyword.
        val keywords = highlight("kotlin", code).filter { it.kind == TokenKind.Keyword }.map { it.text }
        assertEquals(listOf("for", "val", "in"), keywords)
    }

    @Test
    fun `an escaped quote does not end the string`() {
        val tokens = highlight("kotlin", "val s = \"a\\\"b\" // still comment")
        assertEquals(
            "\"a\\\"b\"",
            tokens.first { it.kind == TokenKind.String }.text,
        )
        // The line tail is a comment, not more string — which is only true if the
        // escaped quote above closed nothing.
        assertEquals(TokenKind.Comment, tokens.first { it.text.startsWith("//") }.kind)
    }

    @Test
    fun `slashes inside a string are not a comment`() {
        val tokens = highlight("kotlin", "val u = \"http://example.com\"")
        assertFalse(tokens.any { it.kind == TokenKind.Comment })
        assertEquals("\"http://example.com\"", tokens.first { it.kind == TokenKind.String }.text)
    }

    @Test
    fun `a line comment stops at the newline`() {
        val tokens = highlight("kotlin", "// trailing note\nval next = 2")
        val comment = tokens.first { it.kind == TokenKind.Comment }
        assertEquals("// trailing note", comment.text)
        assertEquals(TokenKind.Keyword, kind("kotlin", "val", "// trailing note\nval next = 2"))
    }

    @Test
    fun `a hash is a comment in python and a character in kotlin`() {
        assertTrue(highlight("python", "x = 1  # count").any { it.kind == TokenKind.Comment })
        assertFalse(highlight("kotlin", "x = 1  # count").any { it.kind == TokenKind.Comment })
    }

    @Test
    fun `an unknown language is left completely plain`() {
        val code = "mov eax, 1 ; a comment that is not one"
        assertEquals(listOf(Token(code, TokenKind.Plain)), highlight("fortran", code))
        assertEquals(listOf(Token(code, TokenKind.Plain)), highlight(null, code))
        assertEquals(listOf(Token(code, TokenKind.Plain)), highlight("", code))
    }

    @Test
    fun `a block comment ends at its terminator`() {
        val code = "/* note */ val x = 1"
        assertEquals("/* note */", highlight("kotlin", code).first().text)
        assertEquals(TokenKind.Keyword, kind("kotlin", "val", code))
    }

    @Test
    fun `an unterminated string stops at its line`() {
        // Models stream, so this is the normal state of a half-received block: the
        // rest of the file must not go string-coloured.
        val code = "val s = \"abc\nval t = 2"
        val tokens = highlight("kotlin", code)
        assertEquals("\"abc", tokens.first { it.kind == TokenKind.String }.text)
        assertEquals(TokenKind.Keyword, kind("kotlin", "val", code))
    }

    @Test
    fun `a python docstring does span lines`() {
        val code = "x = \"\"\"a\nb\"\"\""
        val string = highlight("python", code).first { it.kind == TokenKind.String }
        assertEquals("\"\"\"a\nb\"\"\"", string.text)
    }

    @Test
    fun `numbers keep their shape and identifiers do not become one`() {
        listOf("1_000", "3.14", "0xFF", "2e-3", "100L").forEach { literal ->
            assertEquals("$literal should be one Number token", TokenKind.Number, kind("kotlin", literal, "x = $literal"))
        }
        // `v1` starts with a letter: the digits inside it are not a number literal.
        assertFalse(highlight("kotlin", "val v1 = 2").any { it.text == "1" && it.kind == TokenKind.Number })
    }

    @Test
    fun `json has no comments and no single-quoted strings`() {
        val tokens = highlight("json", "{\"a\": \"b\", \"ok\": true}")
        assertFalse(tokens.any { it.kind == TokenKind.Comment })
        assertEquals(TokenKind.Keyword, kind("json", "true", "{\"a\": \"b\", \"ok\": true}"))
        // `'` is not a JSON string delimiter; treating it as one would colour the tail
        // of every file that contains an apostrophe.
        assertFalse(highlight("json", "{'a': 1}").any { it.kind == TokenKind.String })
    }

    @Test
    fun `empty code produces no tokens`() {
        assertTrue(highlight("kotlin", "").isEmpty())
    }

    @Test
    fun `a kotlin triple-quoted string is one string token`() {
        // Audit-200 D1 (m-077): the old dialect swallowed the first `""` as an
        // empty string and opened a runaway string at the third quote.
        val code = "val s = \"\"\"hello\nworld\"\"\""
        val string = highlight("kotlin", code).first { it.kind == TokenKind.String }
        assertEquals("\"\"\"hello\nworld\"\"\"", string.text)
        assertFalse(highlight("kotlin", code).any { it.kind == TokenKind.String && it.text == "\"\"" })
    }

    @Test
    fun `a java text block is one string token`() {
        val code = "String s = \"\"\"hello\nworld\"\"\";"
        val string = highlight("java", code).first { it.kind == TokenKind.String }
        assertEquals("\"\"\"hello\nworld\"\"\"", string.text)
    }

    @Test
    fun `a rust lifetime is not a string`() {
        // Audit-200 D1 (m-079): `'a` in `&'a` used to open a string that ran
        // to the next quote — `>(x: &'` — swallowing the generic and the
        // reference.
        val code = "fn foo<'a>(x: &'a str) {}"
        val tokens = highlight("rust", code)
        assertTrue(tokens.joinToString("") { it.text } == code)
        assertFalse(tokens.any { it.kind == TokenKind.String && it.text.contains(">(x: &") })
        assertFalse(tokens.any { it.kind == TokenKind.String && it.text == "'a>(x: &'" })
        // A real char literal is still a string.
        assertEquals(TokenKind.String, kind("rust", "'a'", "let c = 'a';"))
    }

    @Test
    fun `sql keywords and dash comments are coloured`() {
        // Audit-200 D2 (m-1148): SQL blocks rendered plain even though the
        // pill and the Save action both recognised the tag.
        val code = "SELECT * FROM t WHERE id = 1 -- keep this row"
        val tokens = highlight("sql", code)
        assertEquals(code, tokens.joinToString("") { it.text })
        assertEquals(TokenKind.Keyword, kind("sql", "SELECT", code))
        assertEquals(TokenKind.Keyword, kind("sql", "FROM", code))
        assertEquals(TokenKind.Keyword, kind("sql", "WHERE", code))
        assertEquals(TokenKind.Number, kind("sql", "1", code))
        assertTrue(tokens.any { it.kind == TokenKind.Comment && it.text == "-- keep this row" })
        // Keywords are case-insensitive in SQL.
        assertEquals(TokenKind.Keyword, kind("sql", "select", "select 1"))
    }

    @Test
    fun `css property words are coloured, selectors stay plain`() {
        // Audit-200 D2 (m-1148): first pass only — declarations get
        // colour, selectors do not pretend to be parsed.
        val code = "color: red; margin: 0;"
        assertEquals(TokenKind.Keyword, kind("css", "color", code))
        assertEquals(TokenKind.Keyword, kind("css", "margin", code))
        assertEquals(TokenKind.Number, kind("css", "0", code))
        // Plain runs merge, so "red" is never a token of its own — it
        // rides inside the " red; " run. What matters is that run is plain.
        assertEquals(TokenKind.Plain, highlight("css", code).first { it.text.contains("red") }.kind)
        assertEquals(code, highlight("css", code).joinToString("") { it.text })
    }

    @Test
    fun `html tags are coloured, text stays plain`() {
        // Audit-200 D2 (m-1148): the tag name is a Type, attribute names
        // are Keywords and quoted values are Strings.
        val code = "<div class=\"x\">hi</div>"
        val tokens = highlight("html", code)
        assertEquals(code, tokens.joinToString("") { it.text })
        assertEquals(TokenKind.Type, kind("html", "div", code))
        assertEquals(TokenKind.Keyword, kind("html", "class", code))
        assertEquals(TokenKind.String, kind("html", "\"x\"", code))
        assertEquals(TokenKind.Plain, kind("html", "hi", code))
        assertEquals(
            TokenKind.Comment,
            highlight("html", "<!-- c -->").single().kind,
        )
        // A `>` inside a quoted value does not close the tag early.
        val quoted = "<a title=\"x > y\">t</a>"
        assertEquals(quoted, highlight("html", quoted).joinToString("") { it.text })
        assertEquals(TokenKind.String, kind("html", "\"x > y\"", quoted))
        // A stray `<` in text is not a tag.
        val stray = highlight("html", "a < b")
        assertEquals("a < b", stray.joinToString("") { it.text })
        assertTrue(stray.none { it.kind == TokenKind.Type })
    }

    @Test
    fun `a js template literal is one string token`() {
        // Audit-200 D2 (m-1148): backticks used to fall through as plain
        // text, so a template and its interpolation had no colour at all.
        val code = "const s = `halo \${nama}`;"
        val string = highlight("js", code).first { it.kind == TokenKind.String }
        assertEquals("`halo \${nama}`", string.text)
        // Templates span lines, like triple-quoted strings.
        val multi = "const s = `a\nb`;"
        assertEquals("`a\nb`", highlight("js", multi).first { it.kind == TokenKind.String }.text)
        // An unterminated template runs to the end of the input.
        val unterminated = "const s = `abc"
        assertEquals("`abc", highlight("js", unterminated).first { it.kind == TokenKind.String }.text)
        assertEquals(unterminated, highlight("js", unterminated).joinToString("") { it.text })
    }
}
