package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.AgentInfo
import dev.bidzz.opencode.core.api.dto.ModelInfo
import dev.bidzz.opencode.core.api.dto.ModelRef

/**
 * Who produced one assistant message: the mode it ran in, and the model that answered.
 *
 * The engine records the model on the assistant message itself, so changing the model
 * halfway through a conversation does not relabel the earlier replies — the transcript
 * says what actually produced each one. That per-message identity is the whole point;
 * a header chip only shows what the *next* reply will use.
 *
 * Returns null when neither is known. That is the normal state while a message is still
 * streaming: the text deltas carry no model, and the identity arrives with the history
 * re-read once the run finishes.
 */
fun provenanceLabel(
    agent: String?,
    model: ModelRef?,
    agents: List<AgentInfo>,
    models: List<ModelInfo>,
): String? {
    val mode = agent?.trim()?.takeIf { it.isNotEmpty() }?.let { id ->
        agents.firstOrNull { it.id == id }?.name?.trim()?.takeIf { it.isNotEmpty() } ?: id
    }
    // Falls back to the id the message carries, not to the catalog's: after a provider
    // is removed, the transcript should still name the model that answered.
    val modelName = model?.id?.trim()?.takeIf { it.isNotEmpty() }?.let { id ->
        models.firstOrNull { it.ref() == model }?.label() ?: id
    }
    return listOfNotNull(mode, modelName)
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
}
