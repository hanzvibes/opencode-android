package dev.bidzz.opencode.ui.stats

import dev.bidzz.opencode.core.api.dto.TokenUsage
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsLabelTest {

    @Test
    fun `total tokens sum input output and reasoning, not cache`() {
        val tokens = TokenUsage(
            input = 4032L,
            output = 9L,
            reasoning = 83L,
            cache = dev.bidzz.opencode.core.api.dto.CacheUsage(read = 2393L, write = 0L),
        )
        assertEquals(4124L, tokens.totalTokens())
    }

    @Test
    fun `total tokens tolerate nulls, absence is null`() {
        val absent: TokenUsage? = null
        assertNull(absent.totalTokens())
        assertEquals(0L, TokenUsage().totalTokens())
        assertEquals(5L, TokenUsage(output = 5L).totalTokens())
    }

    @Test
    fun `cost formats to cents, zero reads as zero`() {
        assertEquals("$0.00", formatStatsCost(0.0))
        assertEquals("<$0.01", formatStatsCost(0.004))
        assertEquals("$0.42", formatStatsCost(0.42))
        assertEquals("$12.34", formatStatsCost(12.34))
    }

    @Test
    fun `model label reads the model id from a ref object`() {
        val ref = buildJsonObject {
            put("providerID", "opencode")
            put("modelID", "big-pickle")
        }
        assertEquals("big-pickle", statsModelLabel(ref))
    }

    @Test
    fun `model label reads a plain string`() {
        assertEquals("big-pickle", statsModelLabel(JsonPrimitive("big-pickle")))
        assertNull(statsModelLabel(null))
        assertNull(statsModelLabel(buildJsonObject { }))
    }
}
