package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Follow-up suggestions are derived from the last reply's text, never from
 * a model call — so the rules themselves are what gets tested here, not the
 * wording (that lives in string resources per language).
 */
class FollowUpSuggestionsTest {

    @Test
    fun `a reply showing code suggests a test, an explanation and an example`() {
        val suggestions = followUpSuggestions(
            "Sudah diperbaiki di ChatViewModel.kt:\n```kotlin\nfun stop() { }\n```",
        )
        assertEquals(
            listOf(
                R.string.followup_make_test,
                R.string.followup_explain_code,
                R.string.followup_give_example,
            ),
            suggestions,
        )
    }

    @Test
    fun `a bare source file name counts as code`() {
        val suggestions = followUpSuggestions("Perubahannya ada di ToolRow.kt dan MessageBubble.kt.")
        assertEquals(R.string.followup_make_test, suggestions.first())
    }

    @Test
    fun `a reply reporting a failure suggests another approach first`() {
        val suggestions = followUpSuggestions("Build gagal karena error di gradle dan crash saat dibuka.")
        assertEquals(
            listOf(
                R.string.followup_try_another,
                R.string.followup_explain_more,
                R.string.followup_give_example,
            ),
            suggestions,
        )
    }

    @Test
    fun `a long explanation suggests more detail, an example and a summary`() {
        val paragraph = "Arsitektur aplikasi ini memisahkan tampilan dari logika bisnis, " +
            "sehingga setiap layar hanya membaca state dan mengirim aksi ke view model. "
        val suggestions = followUpSuggestions(paragraph.repeat(3))
        assertEquals(
            listOf(
                R.string.followup_explain_more,
                R.string.followup_give_example,
                R.string.followup_summarize,
            ),
            suggestions,
        )
    }

    @Test
    fun `a short plain reply falls back to the generic set`() {
        assertEquals(
            listOf(
                R.string.followup_explain_more,
                R.string.followup_give_example,
                R.string.followup_continue,
            ),
            followUpSuggestions("Baik, sudah selesai."),
        )
    }

    @Test
    fun `a blank reply falls back to the generic set`() {
        assertEquals(3, followUpSuggestions("   ").size)
        assertEquals(R.string.followup_explain_more, followUpSuggestions("").first())
    }

    @Test
    fun `suggestions are always three distinct ids`() {
        listOf(
            "```\nfun x() {}\n```",
            "error gagal crash",
            "ok",
            "",
        ).forEach { reply ->
            val suggestions = followUpSuggestions(reply)
            assertEquals(3, suggestions.size)
            assertEquals(3, suggestions.toSet().size)
            assertTrue(suggestions.all { it != 0 })
        }
    }
}
