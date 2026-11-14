package dev.bidzz.opencode.ui.mcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the MCP screen changed a server at runtime in this session
 * (audit-200 B4, M-457): an add, connect, disconnect or remove the next
 * engine restart undoes.
 *
 * One instance is shared by [McpServersViewModel] (which sets it) and
 * [dev.bidzz.opencode.ui.providers.ProvidersViewModel] (which reads it
 * before restarting the engine for a provider change). The provider
 * restart used to discard those changes with no trace — the MCP screen's
 * own restart asks first, a provider save did not. The signal and the
 * MCP screen's `hasRuntimeChanges` banner stay in sync by construction:
 * both are set at the same mutation sites and cleared when the engine
 * handle changes (a restart already happened).
 */
class McpRuntimeChangesSignal {
    private val _changes = MutableStateFlow(false)

    /** True while unrestarted runtime MCP changes exist. */
    val changes: StateFlow<Boolean> = _changes.asStateFlow()

    /** A runtime MCP change just happened. */
    fun markChanged() {
        _changes.value = true
    }

    /** The engine restarted (or the changes were otherwise applied): nothing is pending. */
    fun clear() {
        _changes.value = false
    }
}
