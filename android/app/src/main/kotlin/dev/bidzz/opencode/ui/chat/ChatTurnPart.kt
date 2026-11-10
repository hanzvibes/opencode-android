package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.MessageContent
import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.core.api.dto.toToolCall

/**
 * A chronological segment of an assistant turn.
 *
 * Preserves the natural order of execution:
 * thinking -> explanation prose -> tool calls / questions -> further explanation -> final response.
 */
sealed interface ChatTurnPart {
    data class Reasoning(
        val text: String,
        val isActivelyStreaming: Boolean = false,
    ) : ChatTurnPart

    data class Text(
        val markdown: String,
    ) : ChatTurnPart

    data class ToolGroup(
        val tools: List<ToolCall>,
        val streaming: Boolean = false,
    ) : ChatTurnPart

    data class Question(
        val call: ToolCall,
        val questions: List<QuestionItem>,
    ) : ChatTurnPart
}

/**
 * Builds chronological turn parts from a message's content blocks, live tools, and streaming text.
 */
fun buildTurnParts(
    content: List<MessageContent>,
    streamingText: String? = null,
    isStreaming: Boolean = false,
    liveTools: List<ToolCall> = emptyList(),
    explicitReasoning: String? = null,
    fallbackText: String = "",
): List<ChatTurnPart> {
    val parts = mutableListOf<ChatTurnPart>()
    val reasoningBuilder = StringBuilder()
    if (!explicitReasoning.isNullOrBlank()) {
        reasoningBuilder.append(explicitReasoning.trim())
    }

    val currentText = StringBuilder()
    val currentTools = mutableListOf<ToolCall>()
    val recordedToolIds = mutableSetOf<String>()

    fun flushText() {
        val trimmed = currentText.toString().trim()
        if (trimmed.isNotEmpty()) {
            parts += ChatTurnPart.Text(trimmed)
        }
        currentText.clear()
    }

    fun flushTools() {
        if (currentTools.isNotEmpty()) {
            val isGroupStreaming = isStreaming && currentTools.any { it.status == "running" || it.status == "streaming" }
            parts += ChatTurnPart.ToolGroup(tools = currentTools.toList(), streaming = isGroupStreaming)
            currentTools.clear()
        }
    }

    for (block in content) {
        when (block.type) {
            "reasoning" -> {
                val rText = block.text?.trim()
                if (!rText.isNullOrEmpty()) {
                    if (reasoningBuilder.isNotEmpty()) reasoningBuilder.append("\n\n")
                    reasoningBuilder.append(rText)
                }
            }

            "text" -> {
                flushTools()
                block.text?.let { currentText.append(it) }
            }

            "tool" -> {
                val call = block.toToolCall()
                if (call != null) {
                    recordedToolIds.add(call.id)
                    if (isQuestionTool(call.name)) {
                        flushText()
                        flushTools()
                        val questions = parseQuestionInput(call.input)
                        parts += ChatTurnPart.Question(call = call, questions = questions)
                    } else {
                        flushText()
                        currentTools.add(call)
                    }
                }
            }
        }
    }

    flushText()
    flushTools()

    // Append unrecorded live tools from stream
    val unrecordedLive = liveTools.filter { it.id !in recordedToolIds }
    if (unrecordedLive.isNotEmpty()) {
        val regularLive = unrecordedLive.filter { !isQuestionTool(it.name) }
        val questionLive = unrecordedLive.filter { isQuestionTool(it.name) }

        if (regularLive.isNotEmpty()) {
            val last = parts.lastOrNull()
            if (last is ChatTurnPart.ToolGroup) {
                parts[parts.lastIndex] = last.copy(
                    tools = (last.tools + regularLive).distinctBy { it.id },
                    streaming = isStreaming,
                )
            } else {
                parts += ChatTurnPart.ToolGroup(tools = regularLive, streaming = isStreaming)
            }
        }

        questionLive.forEach { q ->
            parts += ChatTurnPart.Question(call = q, questions = parseQuestionInput(q.input))
        }
    }

    // Attach streaming text if present
    if (!streamingText.isNullOrBlank()) {
        val last = parts.lastOrNull()
        if (last is ChatTurnPart.Text) {
            // Update the trailing text block with live stream content
            parts[parts.lastIndex] = ChatTurnPart.Text(streamingText)
        } else {
            parts += ChatTurnPart.Text(streamingText)
        }
    }

    // If still empty, fall back to parsing plain fallbackText (with <think> detection)
    if (parts.isEmpty()) {
        val parsed = parseThinkingContent(fallbackText, explicitReasoning)
        if (!parsed.reasoning.isNullOrBlank()) {
            if (reasoningBuilder.isNotEmpty()) reasoningBuilder.append("\n\n")
            reasoningBuilder.append(parsed.reasoning)
        }

        if (liveTools.isNotEmpty()) {
            val regularLive = liveTools.filter { !isQuestionTool(it.name) }
            val questionLive = liveTools.filter { isQuestionTool(it.name) }
            if (regularLive.isNotEmpty()) {
                parts += ChatTurnPart.ToolGroup(tools = regularLive, streaming = isStreaming)
            }
            questionLive.forEach { q ->
                parts += ChatTurnPart.Question(call = q, questions = parseQuestionInput(q.input))
            }
        }

        if (parsed.answer.isNotBlank()) {
            parts += ChatTurnPart.Text(parsed.answer)
        }
    }

    // Prepend reasoning if present
    val reasoningText = reasoningBuilder.toString().trim()
    if (reasoningText.isNotEmpty()) {
        val isThinkingActive = isStreaming && parts.none { it is ChatTurnPart.Text }
        parts.add(0, ChatTurnPart.Reasoning(text = reasoningText, isActivelyStreaming = isThinkingActive))
    }

    return parts
}

/**
 * Merges two lists of turn parts, collapsing adjacent parts of the same kind.
 */
fun mergeTurnParts(first: List<ChatTurnPart>, second: List<ChatTurnPart>): List<ChatTurnPart> {
    if (first.isEmpty()) return second
    if (second.isEmpty()) return first

    val result = mutableListOf<ChatTurnPart>()
    val all = first + second
    for (part in all) {
        val prev = result.lastOrNull()
        when {
            prev is ChatTurnPart.Text && part is ChatTurnPart.Text -> {
                val combined = listOf(prev.markdown, part.markdown).filter { it.isNotBlank() }.joinToString("\n\n")
                result[result.lastIndex] = ChatTurnPart.Text(combined)
            }
            prev is ChatTurnPart.ToolGroup && part is ChatTurnPart.ToolGroup -> {
                val toolMap = prev.tools.associateBy { it.id }.toMutableMap()
                part.tools.forEach { toolMap[it.id] = it }
                val orderedIds = (prev.tools.map { it.id } + part.tools.map { it.id }).distinct()
                result[result.lastIndex] = ChatTurnPart.ToolGroup(
                    tools = orderedIds.mapNotNull { toolMap[it] },
                    streaming = prev.streaming || part.streaming,
                )
            }
            prev is ChatTurnPart.Reasoning && part is ChatTurnPart.Reasoning -> {
                val combined = listOf(prev.text, part.text).filter { it.isNotBlank() }.joinToString("\n\n")
                result[result.lastIndex] = ChatTurnPart.Reasoning(
                    text = combined,
                    isActivelyStreaming = prev.isActivelyStreaming || part.isActivelyStreaming,
                )
            }
            else -> result.add(part)
        }
    }
    return result
}

/**
 * Collapses every [ChatTurnPart.ToolGroup] in a turn into a single group at
 * the position of the first one, so a turn renders one trace card.
 *
 * Why here and not in [buildTurnParts]: a text block between two tool calls
 * flushes the pending tools ([flushTools] is called on every "text" block),
 * so one turn can hold several ToolGroups — and [MessageBubble] renders one
 * [ToolTrace] per group. Changing the builder would break the interleaved
 * order [ChatTurnPartsTest] pins (text, tools, text, question, text), so the
 * merge happens at render time instead: the chronological prose order is
 * untouched, only the tool cards collapse. Tools keep their original order;
 * the merged group streams while any of its steps is still running.
 */
fun mergeToolGroups(parts: List<ChatTurnPart>): List<ChatTurnPart> {
    val groups = parts.filterIsInstance<ChatTurnPart.ToolGroup>()
    if (groups.size <= 1) return parts
    val merged = ChatTurnPart.ToolGroup(
        tools = groups.flatMap { it.tools }.distinctBy { it.id },
        streaming = groups.any { it.streaming },
    )
    var inserted = false
    val result = mutableListOf<ChatTurnPart>()
    for (part in parts) {
        if (part is ChatTurnPart.ToolGroup) {
            if (!inserted) {
                result += merged
                inserted = true
            }
        } else {
            result += part
        }
    }
    return result
}
