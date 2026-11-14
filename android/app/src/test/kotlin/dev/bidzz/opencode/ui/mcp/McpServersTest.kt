package dev.bidzz.opencode.ui.mcp

import dev.bidzz.opencode.core.api.dto.McpServer
import dev.bidzz.opencode.core.api.dto.McpStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * MCP status mapping.
 *
 * The engine's status strings are matched case-insensitively, and anything
 * unrecognised falls back to UNKNOWN: a new engine status must render as
 * "Unknown", never crash the list.
 */
class McpServersTest {

    private fun server(status: String, error: String? = null) =
        McpServer(name = "test", status = McpStatus(status = status, error = error))

    @Test
    fun `maps every documented variant`() {
        assertEquals(McpStatusKind.CONNECTED, server("connected").statusKind())
        assertEquals(McpStatusKind.DISABLED, server("disabled").statusKind())
        assertEquals(McpStatusKind.FAILED, server("failed", "boom").statusKind())
        assertEquals(McpStatusKind.NEEDS_AUTH, server("needs-auth", "login").statusKind())
        assertEquals(McpStatusKind.PENDING, server("pending").statusKind())
    }

    @Test
    fun `matching is case-insensitive`() {
        assertEquals(McpStatusKind.CONNECTED, server("Connected").statusKind())
        assertEquals(McpStatusKind.FAILED, server("FAILED").statusKind())
        assertEquals(McpStatusKind.NEEDS_AUTH, server("needsAuth").statusKind())
    }

    @Test
    fun `unknown statuses fall back to unknown`() {
        assertEquals(McpStatusKind.UNKNOWN, server("warming-up").statusKind())
        assertEquals(McpStatusKind.UNKNOWN, server("").statusKind())
    }

    // mcpServerNamed (audit-200 A9-1, DL-014) ----------------------------

    @Test
    fun `mcpServerNamed finds an exact name match`() {
        val servers = listOf(server("connected"), McpServer("docs", McpStatus("disabled")))

        assertEquals("docs", mcpServerNamed(servers, "docs")?.name)
    }

    @Test
    fun `mcpServerNamed returns null for a missing or differently-cased name`() {
        val servers = listOf(McpServer("docs", McpStatus("connected")))

        assertEquals(null, mcpServerNamed(servers, "other"))
        assertEquals(null, mcpServerNamed(servers, "Docs"))
        assertEquals(null, mcpServerNamed(emptyList(), "docs"))
    }

    // RuntimeAddedServers (audit-200 A9-1, DL-015) -----------------------

    @Test
    fun `a runtime-added server is runtime-only, a config server is not`() {
        val added = RuntimeAddedServers()
        added.added("tmp")

        assertEquals(true, added.isRuntimeOnly("tmp"))
        assertEquals(false, added.isRuntimeOnly("docs"))
    }

    @Test
    fun `retainListed drops names the engine no longer lists`() {
        val added = RuntimeAddedServers()
        added.added("tmp")
        added.added("gone")

        added.retainListed(listOf("tmp", "docs"))

        assertEquals(true, added.isRuntimeOnly("tmp"))
        assertEquals(false, added.isRuntimeOnly("gone"))
    }

    @Test
    fun `a name can be added again after it was pruned`() {
        val added = RuntimeAddedServers()
        added.added("tmp")
        added.retainListed(emptyList())
        assertEquals(false, added.isRuntimeOnly("tmp"))

        added.added("tmp")
        assertEquals(true, added.isRuntimeOnly("tmp"))
    }
}
