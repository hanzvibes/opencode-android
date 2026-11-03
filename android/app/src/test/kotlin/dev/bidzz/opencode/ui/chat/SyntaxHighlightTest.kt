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
}
