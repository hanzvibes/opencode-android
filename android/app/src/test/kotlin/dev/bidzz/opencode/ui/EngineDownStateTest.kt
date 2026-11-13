package dev.bidzz.opencode.ui

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.dto.InstructionEntry
import dev.bidzz.opencode.ui.config.ConfigViewModel
import dev.bidzz.opencode.ui.export.ExportViewModel
import dev.bidzz.opencode.ui.inbox.InboxViewModel
import dev.bidzz.opencode.ui.instructions.InstructionsViewModel
import dev.bidzz.opencode.ui.mcp.McpServersViewModel
import dev.bidzz.opencode.ui.move.MoveSessionViewModel
import dev.bidzz.opencode.ui.plugin.PluginsViewModel
import dev.bidzz.opencode.ui.stats.StatsViewModel
import dev.bidzz.opencode.ui.usage.UsageLimitsViewModel
import dev.bidzz.opencode.ui.worktree.WorktreesViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * With the engine down, every Tier-3 load must report a failure instead of
 * silently clearing `loading` and letting the screen show its "nothing
 * here" empty text (audit-100 64-F12, 64-F1, 65-F7, 64-F14).
 *
 * A fresh `EngineApi { "/tmp" }` has no handle, so each load takes the
 * handle-null branch synchronously — no engine, no coroutines involved.
 */
class EngineDownStateTest {

    private fun api() = EngineApi { "/tmp" }

    @Test
    fun `config load with no engine reports a failure`() {
        val vm = ConfigViewModel(api())
        vm.refresh()
        assertFalse(vm.state.value.loading)
        assertEquals(0, vm.state.value.failure?.code)
    }

    @Test
    fun `plugins load with no engine reports a failure`() {
        val vm = PluginsViewModel(api())
        vm.refresh()
        assertFalse(vm.state.value.loading)
        assertEquals(0, vm.state.value.failure?.code)
    }

    @Test
    fun `worktrees load with no engine reports a failure`() {
        val vm = WorktreesViewModel(api())
        vm.refresh()
        assertFalse(vm.state.value.loading)
        assertEquals(0, vm.state.value.failure?.code)
    }

    @Test
    fun `stats load with no engine reports a failure`() {
        val vm = StatsViewModel(api())
        vm.refresh()
        assertFalse(vm.state.value.loading)
        assertEquals(0, vm.state.value.failure?.code)
    }

    @Test
    fun `export load with no engine reports a failure and no export`() {
        val vm = ExportViewModel(api())
        vm.load("s1")
        assertFalse(vm.state.value.loading)
        assertEquals(0, vm.state.value.failure?.code)
        assertNull(vm.state.value.export)
    }

    @Test
    fun `inbox load with no engine reports a failure`() {
        val vm = InboxViewModel(api())
        vm.load("s1")
        assertFalse(vm.state.value.loading)
        assertEquals(0, vm.state.value.failure?.code)
    }

    @Test
    fun `instructions load with no engine reports a failure`() {
        val vm = InstructionsViewModel(api())
        vm.load("s1")
        assertFalse(vm.state.value.loading)
        assertEquals(0, vm.state.value.failure?.code)
    }

    @Test
    fun `instructions save with no engine reports a failure and does not hang in saving`() {
        val vm = InstructionsViewModel(api())
        vm.load("s1")
        vm.setKey("my.key")
        vm.setValue("hello")
        vm.save()
        assertFalse(vm.state.value.saving)
        assertNotNull(vm.state.value.failure)
    }

    @Test
    fun `instructions delete with no engine reports a failure`() {
        val vm = InstructionsViewModel(api())
        vm.load("s1")
        vm.delete(InstructionEntry(key = "my.key"))
        assertNotNull(vm.state.value.failure)
    }

    @Test
    fun `move load with no engine reports a failure`() {
        val vm = MoveSessionViewModel(api())
        vm.load("s1")
        assertFalse(vm.state.value.loading)
        assertEquals(0, vm.state.value.failure?.code)
    }

    @Test
    fun `usage limits load with no engine reports a failure and no info`() {
        val vm = UsageLimitsViewModel(api())
        vm.refresh()
        assertFalse(vm.state.value.loading)
        assertEquals(0, vm.state.value.failure?.code)
        assertNull(vm.state.value.info)
    }

    @Test
    fun `mcp load with no engine reports a failure and no servers`() {
        val vm = McpServersViewModel(api())
        vm.refresh()
        assertFalse(vm.state.value.loading)
        assertEquals(0, vm.state.value.failure?.code)
        assertEquals(0, vm.state.value.servers.size)
    }
}
