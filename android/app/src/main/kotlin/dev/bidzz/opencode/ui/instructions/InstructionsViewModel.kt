package dev.bidzz.opencode.ui.instructions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.InstructionsApi
import dev.bidzz.opencode.core.api.dto.InstructionEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class InstructionsUiState(
    val entries: List<InstructionEntry> = emptyList(),
    val key: String = "",
    val value: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
    /** The key does not match the engine's key pattern (see below). */
    val keyInvalid: Boolean = false,
    /** The last save failed with 413: the value is over the 256 KB max. */
    val tooLarge: Boolean = false,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
)

/**
 * Whether a key is accepted by the engine.
 *
 * Verified against the fork source
 * (`packages/schema/src/instruction-entry.ts`): a key matches
 * `^[a-z0-9][a-z0-9._-]*$` — it starts with a lowercase letter or
 * digit, then lowercase letters, digits, dots, underscores, or hyphens.
 * The server derives the namespaced key `api/<key>` from it.
 */
internal fun isValidInstructionKey(key: String): Boolean =
    Regex("^[a-z0-9][a-z0-9._-]*$").matches(key)

/**
 * An entry's value as editable text.
 *
 * The app sends values as JSON strings, so a string value reads back
 * as its plain text; any other JSON reads back as its compact form.
 * Editing that form and saving it sends it as a string, which is a
 * documented simplification — the screen never pretends to be a JSON
 * editor.
 */
internal fun instructionValueText(value: JsonElement?): String = when (value) {
    null -> ""
    is JsonPrimitive -> value.contentOrNull ?: ""
    else -> value.toString()
}

/**
 * Per-session instruction entries
 * (`/api/experimental/session/{id}/instructions/entries`).
 *
 * List, put (attach or replace one entry) and delete. A save that the
 * engine rejects with 413 is reported as `tooLarge` with a friendly
 * message — the engine's own error body carries byte counts, not words
 * a user can act on.
 */
class InstructionsViewModel(
    private val api: EngineApi,
) : ViewModel() {

    private val _state = MutableStateFlow(InstructionsUiState())
    val state: StateFlow<InstructionsUiState> = _state.asStateFlow()

    private var sessionID: String? = null
    private var loadJob: Job? = null

    fun load(sessionID: String) {
        this.sessionID = sessionID
        val handle = api.handle() ?: run {
            _state.value = _state.value.copy(loading = false)
            return
        }
        // A reload must not wipe the editor (98-F9): the key/value being typed
        // survive the load, and only the list itself is replaced on success.
        _state.value = _state.value.copy(loading = true, failure = null)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when (val result = InstructionsApi(handle.client).list(sessionID)) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    entries = result.value.sortedBy { it.key.lowercase() },
                    loading = false,
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    failure = result,
                )
            }
        }
    }

    fun setKey(key: String) {
        _state.value = _state.value.copy(key = key, keyInvalid = false, tooLarge = false)
    }

    fun setValue(value: String) {
        _state.value = _state.value.copy(value = value, tooLarge = false)
    }

    fun edit(entry: InstructionEntry) {
        _state.value = _state.value.copy(
            key = entry.key,
            value = instructionValueText(entry.value),
            keyInvalid = false,
            tooLarge = false,
            failure = null,
        )
    }

    fun clearEditor() {
        _state.value = _state.value.copy(
            key = "",
            value = "",
            keyInvalid = false,
            tooLarge = false,
        )
    }

    fun save() {
        val sessionID = sessionID ?: return
        val key = _state.value.key.trim()
        val value = _state.value.value
        if (!isValidInstructionKey(key)) {
            _state.value = _state.value.copy(keyInvalid = true)
            return
        }
        val handle = api.handle() ?: return
        _state.value = _state.value.copy(saving = true, keyInvalid = false, tooLarge = false, failure = null)
        viewModelScope.launch {
            when (val result = InstructionsApi(handle.client).put(sessionID, key, JsonPrimitive(value))) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(key = "", value = "", saving = false)
                    load(sessionID)
                }
                is ApiResult.Failure -> {
                    _state.value = if (result.code == 413) {
                        _state.value.copy(saving = false, tooLarge = true)
                    } else {
                        _state.value.copy(saving = false, failure = result)
                    }
                }
            }
        }
    }

    fun delete(entry: InstructionEntry) {
        val sessionID = sessionID ?: return
        val handle = api.handle() ?: return
        viewModelScope.launch {
            when (val result = InstructionsApi(handle.client).delete(sessionID, entry.key)) {
                is ApiResult.Success -> {
                    if (_state.value.key == entry.key) clearEditor()
                    load(sessionID)
                }
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(failure = result)
                }
            }
        }
    }

    fun refresh() {
        sessionID?.let { load(it) }
    }
}
