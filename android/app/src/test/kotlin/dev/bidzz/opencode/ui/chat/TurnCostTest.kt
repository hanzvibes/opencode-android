package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Turn-footer cost formatting (audit-100 Fase 6a, 66-F4): a sub-cent turn
 * is cheap, not free — the footer must agree with the session list.
 */
class TurnCostTest {

    @Test
    fun `sub-cent costs read as under a cent, never zero dollars`() {
        assertEquals("<$0.01", formatTurnCost(0.004))
        assertEquals("<$0.01", formatTurnCost(0.0))
        assertEquals("<$0.01", formatTurnCost(0.009))
    }

    @Test
    fun `a cent or more formats to two decimals`() {
        assertEquals("$0.01", formatTurnCost(0.01))
        assertEquals("$0.02", formatTurnCost(0.02))
        assertEquals("$12.34", formatTurnCost(12.34))
    }
}
