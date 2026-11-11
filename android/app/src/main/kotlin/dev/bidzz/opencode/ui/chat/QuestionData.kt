package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.ToolCall
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * An option presented in an agent question.
 */
data class QuestionOption(
    val id: String,
    val label: String,
    val description: String? = null,
)

/**
 * One question entry asked by the agent.
 */
data class QuestionItem(
    val header: String? = null,
    val question: String,
    val options: List<QuestionOption> = emptyList(),
    val multiple: Boolean = false,
)

/**
 * One answer to one question on a question card, as submitted to the
 * engine (audit-70 10-F1, 10-F3, 44-F1).
 *
 * The answer travels to the engine as a form reply, not as a prompt:
 * [callId] is the question tool call's id (the form's metadata names
 * it), [questionIndex] picks the field (`q0`, `q1`, …), [labels] are
 * the selected option labels — or the write-in text — and [multiple]
 * decides the wire shape (one label as a string, several as an array).
 * [displayText] is the human-readable "Header: a, b" shown on the
 * card once the engine confirms, and put back into the composer when
 * the reply cannot be delivered.
 */
data class QuestionAnswer(
    val callId: String,
    val questionIndex: Int,
    val labels: List<String>,
    val multiple: Boolean,
    val displayText: String,
)

/**
 * Checks whether [name] identifies an agent question tool.
 */
fun isQuestionTool(name: String): Boolean {
    val norm = name.lowercase().substringAfterLast(':').trim()
    return norm == "question" || norm == "ask_question" || norm == "askquestion"
}

/**
 * Parses the [input] payload of a question tool call into a list of [QuestionItem].
 *
 * Supports both modern schema (`{ "questions": [...] }`) and flat schema (`{ "question": "...", "options": [...] }`).
 */
fun parseQuestionInput(input: JsonElement?): List<QuestionItem> {
    if (input == null) return emptyList()
    val obj = input as? JsonObject ?: return emptyList()

    val questionsArray = obj["questions"] as? JsonArray
        ?: obj["Questions"] as? JsonArray
    if (questionsArray != null) {
        return questionsArray.mapNotNull { parseSingleQuestion(it) }
    }

    val single = parseSingleQuestion(obj)
    return if (single != null) listOf(single) else emptyList()
}

private fun parseSingleQuestion(element: JsonElement): QuestionItem? {
    val obj = element as? JsonObject ?: return null
    val questionText = (obj["question"] ?: obj["Question"] ?: obj["text"] ?: obj["prompt"] ?: obj["title"])
        ?.let { (it as? JsonPrimitive)?.contentOrNull } ?: ""
    val header = (obj["header"] ?: obj["Header"] ?: obj["title"])
        ?.let { (it as? JsonPrimitive)?.contentOrNull }
    val multiple = (obj["multiple"] ?: obj["Multiple"] ?: obj["isMultiSelect"] ?: obj["is_multi_select"])
        ?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false

    val rawOptions = obj["options"] as? JsonArray
        ?: obj["Options"] as? JsonArray
        ?: obj["choices"] as? JsonArray

    val options = rawOptions?.mapNotNull { optElem ->
        when (optElem) {
            is JsonPrimitive -> {
                val text = optElem.contentOrNull ?: return@mapNotNull null
                QuestionOption(id = text, label = text)
            }
            is JsonObject -> {
                val label = (optElem["label"] ?: optElem["text"] ?: optElem["name"] ?: optElem["title"])
                    ?.let { (it as? JsonPrimitive)?.contentOrNull }
                    ?: (optElem["id"] as? JsonPrimitive)?.contentOrNull
                    ?: return@mapNotNull null
                val id = (optElem["id"] ?: optElem["value"])?.let { (it as? JsonPrimitive)?.contentOrNull } ?: label
                val desc = optElem["description"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                QuestionOption(id = id, label = label, description = desc)
            }
            else -> null
        }
    }.orEmpty()

    if (questionText.isBlank() && options.isEmpty()) return null
    return QuestionItem(
        header = header,
        question = questionText,
        options = options,
        multiple = multiple,
    )
}
