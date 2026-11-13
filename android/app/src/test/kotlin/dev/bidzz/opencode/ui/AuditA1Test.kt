package dev.bidzz.opencode.ui

import dev.bidzz.opencode.ui.mcp.mcpResourceListKey
import dev.bidzz.opencode.ui.mcp.mcpServerListKey
import dev.bidzz.opencode.ui.mcp.mcpTemplateListKey
import dev.bidzz.opencode.ui.websearch.webSearchResultKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Audit-200 Fase A1 (C-001, C-002, C-003): LazyColumn keys must stay
 * unique when the engine returns duplicates — the same MCP server
 * name twice, the same resource URI twice, the same search URL twice.
 *
 * Pure JVM: the key functions, fed with artificial duplicates, the
 * same pattern as the Hatch batch tests. The screens themselves are
 * verified by compile + the fast-verify run; what is pinned here is
 * that a duplicated value can never produce a duplicated key.
 */
class AuditA1Test {

    @Test
    fun `mcp server keys are unique for a duplicated name`() {
        assertEquals("0:github", mcpServerListKey(0, "github"))
        assertNotEquals(mcpServerListKey(0, "github"), mcpServerListKey(1, "github"))
    }

    @Test
    fun `mcp server keys are stable for the same row`() {
        assertEquals(mcpServerListKey(2, "github"), mcpServerListKey(2, "github"))
        assertNotEquals(mcpServerListKey(0, "github"), mcpServerListKey(0, "gitlab"))
    }

    @Test
    fun `mcp resource keys are unique for a duplicated uri`() {
        assertEquals("res:0:srv:file:///x", mcpResourceListKey(0, "srv", "file:///x"))
        assertNotEquals(
            mcpResourceListKey(0, "srv", "file:///x"),
            mcpResourceListKey(1, "srv", "file:///x"),
        )
    }

    @Test
    fun `mcp template keys are unique for a duplicated uri template`() {
        assertEquals("tpl:0:srv:file:///{p}", mcpTemplateListKey(0, "srv", "file:///{p}"))
        assertNotEquals(
            mcpTemplateListKey(0, "srv", "file:///{p}"),
            mcpTemplateListKey(1, "srv", "file:///{p}"),
        )
    }

    @Test
    fun `mcp resource and template keys never collide`() {
        assertNotEquals(
            mcpResourceListKey(0, "srv", "file:///x"),
            mcpTemplateListKey(0, "srv", "file:///x"),
        )
    }

    @Test
    fun `web search keys are unique for a duplicated url`() {
        assertEquals("0:https://example.com", webSearchResultKey(0, "https://example.com"))
        assertNotEquals(
            webSearchResultKey(0, "https://example.com"),
            webSearchResultKey(1, "https://example.com"),
        )
    }

    @Test
    fun `a duplicated list produces all-unique keys`() {
        val names = listOf("a", "a", "b", "b", "b")
        val keys = names.mapIndexed { index, name -> mcpServerListKey(index, name) }
        assertEquals(names.size, keys.toSet().size)
        val urls = listOf("https://x", "https://x", "https://y")
        val urlKeys = urls.mapIndexed { index, url -> webSearchResultKey(index, url) }
        assertEquals(urls.size, urlKeys.toSet().size)
    }
}
