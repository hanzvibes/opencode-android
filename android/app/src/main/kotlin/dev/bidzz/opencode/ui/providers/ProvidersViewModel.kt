package dev.bidzz.opencode.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.CredentialEntry
import dev.bidzz.opencode.core.api.dto.IntegrationInfo
import dev.bidzz.opencode.engine.EngineConfigStore
import dev.bidzz.opencode.ui.engineDownFailure
import dev.bidzz.opencode.ui.mcp.McpRuntimeChangesSignal
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProvidersUiState(
    val integrations: List<IntegrationInfo> = emptyList(),
    /** Providers the app wrote to the engine's config file, not from its catalog. */
    val customProviders: List<EngineConfigStore.CustomProvider> = emptyList(),
    /** Every stored credential; [rowsFor] filters these per provider. */
    val credentials: List<CredentialEntry> = emptyList(),
    val loading: Boolean = false,
    /**
     * Providers with an action in flight, so only their rows show progress
     * (audit-200 B4, M-035): the old single slot meant two parallel actions
     * on different targets overwrote each other's spinner — the second
     * action's `beginAction` cleared the first one's. A set lets each row
     * track its own action.
     */
    val busyFor: Set<String> = emptySet(),
    /** What went wrong, as a value; the screen words it in the app’s language. */
    val failure: ApiResult.Failure? = null,
    /** A message with no failure behind it, e.g. a config file that would be shadowed. */
    val failureKey: Int? = null,
    /** The new key saved but the old one could not be removed. */
    val staleKeyRemains: Boolean = false,
    /** Name of the provider most recently connected, for a transient confirmation. */
    val connected: String? = null,
    /** Transient result of deleting a key, which may have disconnected the provider. */
    val notice: DeleteNotice? = null,
    /** Google Antigravity OAuth accounts detected from config storage. */
    val antigravityAccounts: List<EngineConfigStore.AntigravityAccount> = emptyList(),
    val activeAntigravityEmail: String? = null,
    /** The accounts file exists but could not be read — not the same as "no accounts" (31-F10). */
    val antigravityAccountsCorrupt: Boolean = false,
    /** The engine config file could not be read at all (30-F3): which file, and why. */
    val configProblem: EngineConfigStore.ConfigReadProblem? = null,
    /**
     * Name of the custom provider whose engine restart is still pending
     * (30-F4): the provider is written to the config file, but the engine
     * has not re-read it yet, so its row must not claim CONNECTED. Cleared
     * when the restart lands (audit-200 B4, M-125) or by the next
     * screen-entry refresh — not before, so a failed restart stays visible.
     */
    val restartPendingFor: String? = null,
    /**
     * Name of the provider whose restart is waiting on the user's say-so
     * (audit-200 B4, M-457): runtime MCP changes are pending, and the
     * restart would discard them. The screen asks first; confirming
     * restarts, dismissing leaves the provider row PENDING.
     */
    val restartConfirm: String? = null,
    /**
     * Provider ids declared in `opencode.json` while the effective config
     * lives in `opencode.jsonc` (audit-200 B4, M-248): the engine reads
     * only the `.jsonc`, so these are shadowed — invisible in the list
     * and never loaded. The screen names them and offers the
     * delete-`.jsonc` flow that brings them back.
     */
    val shadowedJsonProviders: List<String> = emptyList(),
)

/**
 * Validates a custom OpenAI-compatible provider form (30-F1/31-F12/53-F7).
 *
 * Returns the string resource of the first rule broken, or null when the form
 * is acceptable. The dialog and [ProvidersViewModel.saveCustomProvider] share
 * this function, so the inline error and the enforced rule cannot drift apart.
 *
 * Rules: the name must not be blank; the base URL must parse with an `http` or
 * `https` scheme and a host; `http` is only allowed for a loopback host
 * (53-F7 — otherwise the API key travels in cleartext); the model list must
 * parse to at least one model whose id has no whitespace. A new provider also
 * needs a key; editing one does not ([requireKey] false keeps the stored key).
 */
internal fun validateCustomProvider(
    name: String,
    baseURL: String,
    apiKey: String,
    modelsText: String,
    requireKey: Boolean = true,
): Int? {
    if (name.isBlank()) return R.string.providers_custom_name_blank
    val url = baseURL.trim()
    val uri = runCatching { URI(url) }.getOrNull()
    val scheme = uri?.scheme?.lowercase(Locale.ROOT)
    val host = uri?.host
    if ((scheme != "http" && scheme != "https") || host.isNullOrBlank()) {
        return R.string.providers_custom_url_invalid
    }
    // http:// sends the API key in cleartext (53-F7): only loopback is safe.
    if (scheme == "http" && !isLoopbackHost(host)) {
        return R.string.providers_custom_url_http
    }
    if (requireKey && apiKey.isBlank()) return R.string.providers_custom_key_blank
    val models = EngineConfigStore.parseModels(modelsText)
    if (models.isEmpty()) return R.string.providers_custom_no_models
    if (models.any { spec -> spec.id.any { it.isWhitespace() } }) {
        return R.string.providers_custom_model_invalid
    }
    return null
}

/** True for hosts whose traffic never leaves the device (53-F7). */
internal fun isLoopbackHost(host: String): Boolean {
    val lower = host.lowercase(Locale.ROOT)
    // java.net.URI keeps the brackets on an IPv6 literal host ("[::1]"),
    // so both forms must match.
    return lower == "localhost" || lower == "127.0.0.1" || lower == "::1" || lower == "[::1]"
}

/**
 * True when [id] is already taken by another custom provider (30-F2): the
 * store keys by slug, so a name whose slug collides would silently
 * overwrite that provider's URL, key, and models. The provider being
 * edited keeps its own slug.
 */
internal fun isNameTaken(
    existing: List<EngineConfigStore.CustomProvider>,
    id: String,
    editingId: String?,
): Boolean = existing.any { it.id == id && it.id != editingId }

/**
 * Lists the engine's providers and stores credentials.
 *
 * Catalog providers go over `POST /api/credential`, which persists them in the engine's
 * own database. The app never writes `auth.json` for this: that file is legacy and the
 * migration importing it has already run on any existing install.
 *
 * Providers outside the catalog — an OpenAI-compatible endpoint with its own base URL —
 * have no credential endpoint at all, so those are written into the engine's config file
 * and need a restart before the engine sees them.
 */
class ProvidersViewModel(
    private val api: EngineApi,
    private val configStore: EngineConfigStore,
    /**
     * Restarts the engine; true when the restart was actually requested
     * (audit-200 B4, M-125): the old `() -> Unit` discarded the result,
     * so a restart that never started looked exactly like a successful
     * one. False means the supervisor was never initialised.
     */
    private val restartEngine: () -> Boolean,
    /**
     * Shared with the MCP screen (audit-200 B4, M-457): true while
     * runtime MCP changes are pending, which a provider restart would
     * discard. Defaults to a private instance so existing callers need
     * no wiring.
     */
    private val mcpRuntimeChanges: McpRuntimeChangesSignal = McpRuntimeChangesSignal(),
) : ViewModel() {

    private val _state = MutableStateFlow(ProvidersUiState())
    val state: StateFlow<ProvidersUiState> = _state.asStateFlow()

    /** The restart watch from the last provider write, if still running. */
    private var restartWatchJob: Job? = null

    /**
     * Re-reads the config file and, unless [reloadEngine] is false, the engine's
     * catalog and credentials.
     *
     * The disk reads run on [Dispatchers.IO] (30-F6): the old version did them
     * synchronously on the caller thread, i.e. the main thread from the screen's
     * `LaunchedEffect` and from every save path. Callers that restart the engine
     * pass `reloadEngine = false`: the engine is about to die, so a reload
     * racing the restart would report a spurious failure (31-F9 family) — the
     * catalog is untouched by a config-file change anyway, and the next
     * screen-entry refresh re-reads it.
     */
    fun refresh(preserveRestartPending: Boolean = false, reloadEngine: Boolean = true) {
        viewModelScope.launch {
            // Read the config first and independently: it is on disk, so it is worth showing
            // even while the engine is down.
            val agResult = withContext(Dispatchers.IO) {
                runCatching { configStore.antigravityAccountsResult() }
                    .getOrDefault(EngineConfigStore.AntigravityAccountsResult.Missing)
            }
            val agAccounts = (agResult as? EngineConfigStore.AntigravityAccountsResult.Ok)?.accounts ?: emptyList()
            val activeAg = withContext(Dispatchers.IO) { configStore.activeAntigravityAccount() }
            // customProviders() runs readRoot() first, which records the problem when the
            // config file is unreadable — read it back after, not before.
            val custom = withContext(Dispatchers.IO) {
                runCatching { configStore.customProviders() }.getOrDefault(emptyList())
            }
            // Providers shadowed by a .jsonc (audit-200 B4, M-248): read on
            // disk like the custom list, so the screen can name them.
            val shadowed = withContext(Dispatchers.IO) {
                runCatching { configStore.shadowedJsonProviderIds() }.getOrDefault(emptyList())
            }
            _state.value = _state.value.copy(
                customProviders = custom,
                antigravityAccounts = agAccounts,
                activeAntigravityEmail = activeAg?.email,
                antigravityAccountsCorrupt = agResult is EngineConfigStore.AntigravityAccountsResult.Corrupt,
                configProblem = configStore.lastConfigReadProblem,
                shadowedJsonProviders = shadowed,
                restartPendingFor = if (preserveRestartPending) _state.value.restartPendingFor else null,
            )
            if (!reloadEngine) return@launch
            val h = api.handle() ?: return@launch
            _state.value = _state.value.copy(loading = true, failure = null, failureKey = null)
            reload(h)
        }
    }

    /** Stores [key] for a catalog provider and re-reads so its row updates. */
    fun saveKey(integrationID: String, key: String) {
        if (key.isBlank()) return
        val h = api.handle() ?: run {
            reportEngineDown()
            return
        }
        _state.value = beginAction(integrationID)
        viewModelScope.launch {
            when (val result = h.credentialStore.createKey(integrationID, key.trim())) {
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    busyFor = _state.value.busyFor - integrationID,
                    failure = result,
                )
                is ApiResult.Success -> {
                    val name = providerName(integrationID)
                    reload(h)
                    // The save succeeded: a reload failure must not leave the
                    // green confirmation and a red error on screen together
                    // (31-F9). The list refreshes on the next entry.
                    _state.value = _state.value.copy(
                        busyFor = _state.value.busyFor - integrationID,
                        connected = name,
                        failure = null,
                        failureKey = null,
                    )
                }
            }
        }
    }

    /**
     * Removes one stored key.
     *
     * A provider can hold several keys with only one active, so deleting a key is not
     * the same as disconnecting a provider — but it becomes that when it was the last
     * one, which is why the count is re-read from the engine instead of decremented.
     */
    fun deleteCredential(integrationID: String, credentialID: String) {
        val h = api.handle() ?: run {
            reportEngineDown()
            return
        }
        val name = providerName(integrationID)
        _state.value = beginAction(credentialID)
        viewModelScope.launch {
            when (val result = h.credentialStore.remove(credentialID)) {
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    busyFor = _state.value.busyFor - credentialID,
                    failure = result,
                )
                is ApiResult.Success -> {
                    reload(h)
                    _state.value = _state.value.copy(
                        busyFor = _state.value.busyFor - credentialID,
                        notice = afterDeleteNotice(rowsFor(_state.value.credentials, integrationID).size, name),
                        failure = null,
                        failureKey = null,
                    )
                }
            }
        }
    }

    /**
     * Stores a new key for a provider and drops [credentialID] once the new one exists.
     *
     * The order is the point: creating first means a rejected key leaves the working
     * one in place, while deleting first can end with the provider holding nothing.
     */
    fun replaceCredential(integrationID: String, credentialID: String, key: String) {
        if (key.isBlank()) return
        val h = api.handle() ?: run {
            reportEngineDown()
            return
        }
        val name = providerName(integrationID)
        _state.value = beginAction(credentialID)
        viewModelScope.launch {
            when (val created = h.credentialStore.createKey(integrationID, key.trim())) {
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    busyFor = _state.value.busyFor - credentialID,
                    failure = created,
                )
                is ApiResult.Success -> {
                    // The old key outliving the new one is a warning, not a failure: the new
                    // key did save. Remembered separately so the screen can say both.
                    val stale = (h.credentialStore.remove(credentialID) as? ApiResult.Failure)
                    reload(h)
                    _state.value = _state.value.copy(
                        busyFor = _state.value.busyFor - credentialID,
                        connected = name,
                        failure = null,
                        failureKey = null,
                        staleKeyRemains = stale != null,
                    )
                }
            }
        }
    }

    /**
     * Writes a custom OpenAI-compatible provider, then restarts the engine.
     *
     * The restart is not optional: providers are resolved at startup, so without it the
     * form appears to succeed while the model never reaches the picker.
     *
     * [editingId] is the slug of the provider being edited, or null for a new one:
     * editing keeps its own slug (30-F2) and a blank key keeps the stored key,
     * while a new provider with the same slug as another entry is rejected.
     * The row shows "restart pending", not CONNECTED, until the next refresh
     * (30-F4): the engine has not re-read the config yet.
     */
    fun saveCustomProvider(
        name: String,
        baseURL: String,
        apiKey: String,
        modelsText: String,
        editingId: String? = null,
    ) {
        val trimmedName = name.trim()
        // Validate before any disk write or restart (30-F1/31-F12/53-F7).
        validateCustomProvider(trimmedName, baseURL, apiKey, modelsText, requireKey = editingId == null)?.let { key ->
            _state.value = _state.value.copy(
                failureKey = key,
                connected = null,
                notice = null,
                staleKeyRemains = false,
            )
            return
        }
        val id = EngineConfigStore.slug(trimmedName)
        val models = EngineConfigStore.parseModels(modelsText)
        // A blank key while editing keeps the stored key (30-F2); a new
        // provider always has one — the validation above enforced it.
        val keyToStore = apiKey.trim().ifEmpty { null }
        _state.value = beginAction(id)
        viewModelScope.launch {
            val existing = withContext(Dispatchers.IO) {
                runCatching { configStore.customProviders() }.getOrDefault(emptyList())
            }
            // A name whose slug already exists would silently overwrite that
            // provider's URL, key, and models: the store keys by slug, so
            // "My Provider" and "my-provider" are the same entry. Reject,
            // unless the slug is the one being edited (30-F2).
            if (isNameTaken(existing, id, editingId)) {
                _state.value = _state.value.copy(
                    busyFor = _state.value.busyFor - id,
                    failureKey = R.string.providers_name_taken,
                    connected = null,
                    notice = null,
                    staleKeyRemains = false,
                )
                return@launch
            }
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    // Audit-200 B4 (M-126): a rename passes the old slug
                    // so the store moves the entry in the same write —
                    // otherwise the old entry stayed behind as a second,
                    // still-CONNECTED provider.
                    configStore.putOpenAICompatible(id, trimmedName, baseURL.trim(), keyToStore, models, previousId = editingId)
                }
            }
            val written = outcome.getOrNull()
            if (written == null) {
                val blocked = outcome.exceptionOrNull() is EngineConfigStore.JsoncBlockedException
                _state.value = _state.value.copy(
                    busyFor = _state.value.busyFor - id,
                    failureKey = if (blocked) {
                        R.string.providers_config_jsonc_blocks_write
                    } else {
                        R.string.providers_config_write_failed
                    },
                    connected = null,
                    notice = null,
                    staleKeyRemains = false,
                )
                return@launch
            }
            _state.value = _state.value.copy(
                busyFor = _state.value.busyFor - id,
                restartPendingFor = trimmedName,
                connected = null,
                failureKey = null,
            )
            refresh(preserveRestartPending = true, reloadEngine = false)
            restartAfterWrite(trimmedName)
        }
    }

    /**
     * Deletes a custom provider, then restarts the engine (30-F2). The row
     * disappearing is the confirmation; there is no key to re-enter.
     */
    fun deleteCustomProvider(id: String) {
        _state.value = beginAction(id)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { configStore.deleteCustomProvider(id) }
            }
            val deleted = outcome.getOrNull() ?: false
            if (!deleted) {
                val blocked = outcome.exceptionOrNull() is EngineConfigStore.JsoncBlockedException
                _state.value = _state.value.copy(
                    busyFor = _state.value.busyFor - id,
                    failureKey = if (blocked) {
                        R.string.providers_config_jsonc_blocks_write
                    } else {
                        R.string.providers_config_write_failed
                    },
                    connected = null,
                    notice = null,
                    staleKeyRemains = false,
                )
                return@launch
            }
            _state.value = _state.value.copy(busyFor = _state.value.busyFor - id)
            refresh(reloadEngine = false)
            restartAfterWrite(id)
        }
    }

    /**
     * Makes another Antigravity account the active one (audit-100 31-F5): writes
     * `activeIndex` into the accounts file and restarts the engine so the plugin
     * picks the account up.
     */
    fun selectAntigravityAccount(id: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { configStore.setAntigravityActiveAccount(id) }
            if (!ok) {
                _state.value = _state.value.copy(
                    failureKey = R.string.providers_config_write_failed,
                    connected = null,
                    notice = null,
                    staleKeyRemains = false,
                )
                return@launch
            }
            refresh(reloadEngine = false)
            restartAfterWrite(id)
        }
    }

    /** Enables or disables one Antigravity account, then restarts the engine (31-F5). */
    fun setAntigravityAccountEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { configStore.setAntigravityAccountEnabled(id, enabled) }
            if (!ok) {
                _state.value = _state.value.copy(
                    failureKey = R.string.providers_config_write_failed,
                    connected = null,
                    notice = null,
                    staleKeyRemains = false,
                )
                return@launch
            }
            refresh(reloadEngine = false)
            restartAfterWrite(id)
        }
    }

    /**
     * Deletes `opencode.jsonc` so the app can manage providers in `opencode.json`
     * again (30-F5/38-F10). Offered when a save was refused because the effective
     * config lives in `.jsonc`.
     *
     * Audit-200 A7 (DL-002): the screen asks first, and the store writes a
     * `.jsonc.bak` backup before deleting — the file may hold hand-written
     * providers and MCP blocks. The notice names the backup and says where
     * providers now come from, so an existing `opencode.json` is never
     * silently switched on: its content may be stale, and the user should
     * know the list they see comes from it. (`refresh()` keeps the notice;
     * it only clears the failure key.)
     */
    fun deleteJsoncConfig() {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { configStore.deleteJsoncConfig() }
            when (outcome) {
                EngineConfigStore.JsoncDeleteResult.Deleted -> {
                    val fromJson = withContext(Dispatchers.IO) { configStore.hasJsonConfig() }
                    _state.value = _state.value.copy(
                        failureKey = null,
                        notice = DeleteNotice(
                            if (fromJson) R.string.providers_jsonc_deleted_with_json
                            else R.string.providers_jsonc_deleted_fresh,
                        ),
                    )
                    refresh()
                }
                EngineConfigStore.JsoncDeleteResult.Missing -> {
                    // Already gone (deleted elsewhere): the goal state is
                    // reached, so clear the failure and show what is there.
                    _state.value = _state.value.copy(failureKey = null)
                    refresh()
                }
                EngineConfigStore.JsoncDeleteResult.Failed -> {
                    _state.value = _state.value.copy(
                        failureKey = R.string.providers_config_write_failed,
                        connected = null,
                        notice = null,
                        staleKeyRemains = false,
                    )
                }
            }
        }
    }

    /**
     * State at the start of an action: busy for this target.
     *
     * Audit-200 B4 (M-035): the old version cleared every outcome and set
     * the single busy slot, so a second action on another target wiped
     * the first one's spinner and its just-completed confirmation. The
     * busy set lets each row track its own action, and an outcome is only
     * replaced when an action COMPLETES (31-F14) — an old green
     * "Connected" never sits next to a new red error, but it does survive
     * while another action is still in flight.
     */
    private fun beginAction(busyFor: String): ProvidersUiState =
        _state.value.copy(
            busyFor = _state.value.busyFor + busyFor,
        )

    /**
     * Restarts the engine after a provider write, asking first when
     * runtime MCP changes are pending (audit-200 B4, M-457).
     *
     * The write already happened — the confirm gates the RESTART, not
     * the save. Dismissing leaves the provider row PENDING (the engine
     * has not re-read the config yet); the user restarts from the MCP
     * screen instead, whose own confirm says the same thing.
     */
    private fun restartAfterWrite(providerName: String) {
        if (mcpRuntimeChanges.changes.value) {
            _state.value = _state.value.copy(restartConfirm = providerName)
            return
        }
        doRestart(providerName)
    }

    /** The user confirmed the restart despite pending MCP changes (M-457). */
    fun confirmRestart() {
        val pending = _state.value.restartConfirm ?: return
        _state.value = _state.value.copy(restartConfirm = null)
        doRestart(pending)
    }

    /** The user put the restart off (M-457): the row stays PENDING. */
    fun dismissRestart() {
        _state.value = _state.value.copy(restartConfirm = null)
    }

    /**
     * Requests the restart and watches it land (audit-200 B4, M-125).
     *
     * The old code fired the restart and forgot it: the result was
     * discarded, and the PENDING row only cleared when the user left and
     * re-entered the screen — a failed restart looked identical to a
     * slow one. The watch polls for a new engine handle (the same signal
     * the MCP screen uses: a restart attaches a new handle) and refreshes
     * once it appears, which clears the PENDING row. If the engine never
     * comes back, the failure is reported instead — the row keeps
     * PENDING, which is still true.
     */
    private fun doRestart(providerName: String) {
        val before = api.handle()
        if (!restartEngine()) {
            // The restart never started (supervisor not initialised):
            // say so now instead of leaving a PENDING row behind (M-125).
            _state.value = _state.value.copy(
                restartPendingFor = null,
                failureKey = R.string.providers_restart_failed,
                connected = null,
                notice = null,
                staleKeyRemains = false,
            )
            return
        }
        restartWatchJob?.cancel()
        restartWatchJob = viewModelScope.launch {
            repeat(RESTART_WATCH_ATTEMPTS) {
                delay(RESTART_WATCH_INTERVAL_MS)
                val now = api.handle()
                if (now != null && now !== before) {
                    refresh(preserveRestartPending = false)
                    return@launch
                }
            }
            _state.value = _state.value.copy(failure = engineDownFailure())
        }
    }

    companion object {
        /** How long the restart watch polls for a new engine handle (M-125). */
        private const val RESTART_WATCH_ATTEMPTS = 30
        private const val RESTART_WATCH_INTERVAL_MS = 1_000L
    }

    /**
     * The engine is down: report it instead of silently doing nothing (31-F7).
     * The old code returned without touching state, so the dialog had already
     * closed and the screen showed nothing at all.
     */
    private fun reportEngineDown() {
        _state.value = _state.value.copy(
            failure = engineDownFailure(),
            failureKey = null,
            connected = null,
            notice = null,
            staleKeyRemains = false,
        )
    }

    private fun providerName(integrationID: String): String =
        _state.value.integrations.firstOrNull { it.id == integrationID }?.name ?: integrationID

    /**
     * Re-reads both lists. Deleting a key can disconnect a provider, and adding one can
     * reconnect it, so the catalog status and the stored keys only ever make sense
     * together.
     */
    private suspend fun reload(h: EngineApi.Handle) {
        when (val result = h.integrations.list()) {
            is ApiResult.Success -> _state.value = _state.value.copy(
                integrations = result.value.sortedWith(
                    compareBy<IntegrationInfo> { !it.connected }.thenBy { it.name.lowercase() },
                ),
                loading = false,
            )
            is ApiResult.Failure -> _state.value = _state.value.copy(
                loading = false,
                failure = result,
            )
        }
        when (val result = h.credentialStore.list()) {
            is ApiResult.Success -> _state.value = _state.value.copy(credentials = result.value, loading = false)
            // Only reported when it is the first failure, so it does not hide the
            // catalog error that already explains an unreachable engine.
            is ApiResult.Failure -> if (_state.value.failure == null && _state.value.failureKey == null) {
                _state.value = _state.value.copy(loading = false, failure = result)
            }
        }
    }
}
