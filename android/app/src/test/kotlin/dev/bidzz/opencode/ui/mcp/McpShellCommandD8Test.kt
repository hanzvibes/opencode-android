package dev.bidzz.opencode.ui.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D8 (m-578): a local MCP command splits the way a shell
 * would — single quotes, double quotes, and backslash escapes keep
 * spaces inside one argument. Unit-tested directly, since the parser is
 * pure (same shape as [McpUrlD7Test]).
 */
class McpShellCommandD8Test {

    @Test
    fun `m-578 a plain command splits on whitespace`() {
        assertEquals(
            listOf("npx", "-y", "@scope/server", "/tmp"),
            splitShellCommand("npx -y @scope/server /tmp"),
        )
        assertEquals(
            listOf("npx", "-y", "@scope/server", "/tmp"),
            splitShellCommand("  npx   -y  @scope/server   /tmp  "),
        )
    }

    @Test
    fun `m-578 double quotes keep spaces inside one argument`() {
        assertEquals(
            listOf("npx", "-y", "@scope/server", "/tmp/my dir"),
            splitShellCommand("npx -y @scope/server \"/tmp/my dir\""),
        )
    }

    @Test
    fun `m-578 single quotes keep spaces inside one argument`() {
        assertEquals(
            listOf("cmd", "a b", "c"),
            splitShellCommand("cmd 'a b' c"),
        )
    }

    @Test
    fun `m-578 a backslash escapes the next character`() {
        assertEquals(
            listOf("cmd", "a b", "c"),
            splitShellCommand("cmd a\\ b c"),
        )
        // Inside single quotes the backslash is literal, like a shell.
        assertEquals(
            listOf("cmd", "a\\ b"),
            splitShellCommand("cmd 'a\\ b'"),
        )
    }

    @Test
    fun `m-578 an empty quoted string is an empty argument`() {
        assertEquals(
            listOf("cmd", "", "x"),
            splitShellCommand("cmd \"\" x"),
        )
    }

    @Test
    fun `m-578 an unmatched quote is tolerated, not fatal`() {
        assertEquals(
            listOf("cmd", "a b"),
            splitShellCommand("cmd \"a b"),
        )
    }

    @Test
    fun `m-578 blank input splits to nothing`() {
        assertTrue(splitShellCommand("").isEmpty())
        assertTrue(splitShellCommand("   ").isEmpty())
    }
}
