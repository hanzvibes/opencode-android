package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Composer suggestion chips fill the draft, never send — so the rules
 * tested here are about *when* each set appears, not the wording (that
 * lives in string resources per language).
 */
class ComposerSuggestionsTest {

    @Test
    fun `no messages means no suggestions, the empty-state starters own that moment`() {
        assertEquals(
            emptyList<Int>(),
            composerSuggestions(lastAssistantText = null, hasFailure = false, hasMessages = false),
        )
    }

    @Test
    fun `a failure suggests fixing it and explaining it`() {
        assertEquals(
            listOf(
                R.string.composer_suggest_fix_error,
                R.string.composer_suggest_explain_error,
            ),
            composerSuggestions(lastAssistantText = "ok", hasFailure = true, hasMessages = true),
        )
    }

    @Test
    fun `a failure wins over code in the last reply`() {
        assertEquals(
            R.string.composer_suggest_fix_error,
            composerSuggestions(
                lastAssistantText = "Fixed in ChatViewModel.kt:\n```kotlin\nfun stop() { }\n```",
                hasFailure = true,
                hasMessages = true,
            ).first(),
        )
    }

    @Test
    fun `a reply showing code suggests a test, an explanation and an example`() {
        assertEquals(
            listOf(
                R.string.followup_make_test,
                R.string.followup_explain_code,
                R.string.followup_give_example,
            ),
            composerSuggestions(
                lastAssistantText = "Sudah diperbaiki di ChatViewModel.kt:\n```kotlin\nfun stop() { }\n```",
                hasFailure = false,
                hasMessages = true,
            ),
        )
    }

    @Test
    fun `a reply reporting a failure in its text suggests fixing and explaining it`() {
        assertEquals(
            listOf(
                R.string.composer_suggest_fix_error,
                R.string.composer_suggest_explain_error,
            ),
            composerSuggestions(
                lastAssistantText = "Build gagal karena error di gradle dan crash saat dibuka.",
                hasFailure = false,
                hasMessages = true,
            ),
        )
    }

    @Test
    fun `a long explanation suggests more detail, an example and a summary`() {
        val paragraph = "Arsitektur aplikasi ini memisahkan tampilan dari logika bisnis, " +
            "sehingga setiap layar hanya membaca state dan mengirim aksi ke view model. "
        assertEquals(
            listOf(
                R.string.followup_explain_more,
                R.string.followup_give_example,
                R.string.followup_summarize,
            ),
            composerSuggestions(
                lastAssistantText = paragraph.repeat(3),
                hasFailure = false,
                hasMessages = true,
            ),
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
            composerSuggestions(
                lastAssistantText = "Baik, sudah selesai.",
                hasFailure = false,
                hasMessages = true,
            ),
        )
    }

    @Test
    fun `no assistant reply yet falls back to the generic set`() {
        assertEquals(
            R.string.followup_explain_more,
            composerSuggestions(
                lastAssistantText = null,
                hasFailure = false,
                hasMessages = true,
            ).first(),
        )
    }

    @Test
    fun `suggestions are always distinct ids, never more than three`() {
        listOf(
            composerSuggestions("```\nfun x() {}\n```", false, true),
            composerSuggestions("error gagal crash", false, true),
            composerSuggestions("error", true, true),
            composerSuggestions("ok", false, true),
            composerSuggestions(null, false, true),
        ).forEach { suggestions ->
            assertTrue(suggestions.size <= 3)
            assertEquals(suggestions.size, suggestions.toSet().size)
            assertTrue(suggestions.all { it != 0 })
        }
    }

    @Test
    fun `a tap on an empty draft replaces it with the suggestion`() {
        assertEquals(
            "Fix this error",
            appendSuggestionToDraft("", "Fix this error"),
        )
        assertEquals(
            "Fix this error",
            appendSuggestionToDraft("   ", "  Fix this error  "),
        )
    }

    @Test
    fun `a tap with text already typed appends on a new line`() {
        assertEquals(
            "tolong cek\nFix this error",
            appendSuggestionToDraft("tolong cek", "Fix this error"),
        )
    }

    @Test
    fun `a blank suggestion leaves the draft untouched`() {
        assertEquals("tolong cek", appendSuggestionToDraft("tolong cek", "   "))
        assertEquals("", appendSuggestionToDraft("", ""))
    }
}
