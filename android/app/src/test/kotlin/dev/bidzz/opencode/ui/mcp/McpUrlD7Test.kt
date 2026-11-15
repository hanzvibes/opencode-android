package dev.bidzz.opencode.ui.mcp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D7 (m-631): a remote MCP server needs a real URL before Add
 * is enabled — unit-tested directly, since the validator is pure
 * (java.net.URI on purpose: the framework Uri is a stub on the JVM).
 */
class McpUrlD7Test {

    @Test
    fun `m-631 real remote URLs are accepted`() {
        assertTrue(isValidMcpRemoteUrl("https://example.com:8080/mcp"))
        assertTrue(isValidMcpRemoteUrl("http://localhost:3000"))
        assertTrue(isValidMcpRemoteUrl("https://example.com"))
        assertTrue(isValidMcpRemoteUrl("  https://example.com/mcp  "))
    }

    @Test
    fun `m-631 bare words and scheme-less hosts are rejected`() {
        assertFalse(isValidMcpRemoteUrl("notaurl"))
        assertFalse(isValidMcpRemoteUrl("example.com"))
        assertFalse(isValidMcpRemoteUrl("example.com:8080/mcp"))
    }

    @Test
    fun `m-631 a scheme with no host is rejected`() {
        assertFalse(isValidMcpRemoteUrl("http://"))
        assertFalse(isValidMcpRemoteUrl("https://"))
        assertFalse(isValidMcpRemoteUrl("http://:8080"))
    }

    @Test
    fun `m-631 a wrong scheme is rejected`() {
        assertFalse(isValidMcpRemoteUrl("ftp://example.com"))
        assertFalse(isValidMcpRemoteUrl("ws://example.com"))
    }

    @Test
    fun `m-631 whitespace anywhere is rejected`() {
        assertFalse(isValidMcpRemoteUrl("http://exa mple.com"))
        assertFalse(isValidMcpRemoteUrl(""))
        assertFalse(isValidMcpRemoteUrl("   "))
    }
}
