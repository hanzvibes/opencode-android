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
}
