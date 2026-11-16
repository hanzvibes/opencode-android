package dev.bidzz.opencode.ui.chat

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionDataTest {

    @Test
    fun `isQuestionTool matches question tool variations and rejects others`() {
        assertTrue(isQuestionTool("question"))
        assertTrue(isQuestionTool("ask_question"))
        assertTrue(isQuestionTool("default_api:question"))
        assertTrue(isQuestionTool("default_api:ask_question"))
        assertTrue(isQuestionTool("askQuestion"))

        assertFalse(isQuestionTool("read"))
        assertFalse(isQuestionTool("write"))
        assertFalse(isQuestionTool("bash"))
        assertFalse(isQuestionTool("grep"))
    }

    @Test
    fun `parseQuestionInput parses structured questions array correctly`() {
        val json = """
            {
              "questions": [
                {
                  "header": "Pembaruan",
                  "question": "Pembaruan apa yang ingin kamu lakukan?",
                  "multiple": false,
                  "options": [
                    { "label": "Fitur / UI", "description": "Update tampilan" },
                    { "label": "Deps", "description": "Update deps" }
                  ]
                }
              ]
            }
        """.trimIndent()
        val element = Json.parseToJsonElement(json)
        val questions = parseQuestionInput(element)

        assertEquals(1, questions.size)
        val q = questions[0]
        assertEquals("Pembaruan", q.header)
        assertEquals("Pembaruan apa yang ingin kamu lakukan?", q.question)
        assertFalse(q.multiple)
        assertEquals(2, q.options.size)
        assertEquals("Fitur / UI", q.options[0].label)
        assertEquals("Update tampilan", q.options[0].description)
        assertEquals("Deps", q.options[1].label)
    }

    @Test
    fun `parseQuestionInput parses flat single question schema with string options`() {
        val json = """
            {
              "question": "Choose an environment",
              "multiple": true,
              "options": ["Android", "iOS", "Desktop"]
            }
        """.trimIndent()
        val element = Json.parseToJsonElement(json)
        val questions = parseQuestionInput(element)

        assertEquals(1, questions.size)
        val q = questions[0]
        assertEquals("Choose an environment", q.question)
        assertTrue(q.multiple)
        assertEquals(3, q.options.size)
        assertEquals("Android", q.options[0].label)
        assertEquals("iOS", q.options[1].label)
        assertEquals("Desktop", q.options[2].label)
    }

    @Test
    fun `duplicate labels without ids get distinct selection keys`() {
        // Audit-200 D9 (m-117): two "Ya" options used to share the id
        // (it defaults to the label), so one tap selected both rows and
        // answered "Header: Ya, Ya". The first keeps its id; later
        // duplicates get a "#n" suffix. Labels are untouched — the
        // engine receives labels, not ids.
        val json = """
            {
              "question": "Lanjut?",
              "options": ["Ya", "Ya", "Tidak"]
            }
        """.trimIndent()
        val questions = parseQuestionInput(Json.parseToJsonElement(json))

        assertEquals(1, questions.size)
        val ids = questions[0].options.map { it.id }
        assertEquals(listOf("Ya", "Ya#1", "Tidak"), ids)
        assertEquals(listOf("Ya", "Ya", "Tidak"), questions[0].options.map { it.label })
    }

    @Test
    fun `duplicate explicit ids get distinct selection keys`() {
        val json = """
            {
              "question": "Lanjut?",
              "options": [
                { "id": "a", "label": "Ya" },
                { "id": "a", "label": "Ya" },
                { "id": "b", "label": "Tidak" }
              ]
            }
        """.trimIndent()
        val questions = parseQuestionInput(Json.parseToJsonElement(json))

        assertEquals(listOf("a", "a#1", "b"), questions[0].options.map { it.id })
    }
}
