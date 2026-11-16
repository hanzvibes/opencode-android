package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickRepliesTest {

    private fun item(
        options: List<String>,
        multiple: Boolean = false,
        header: String? = null,
    ) = QuestionItem(
        header = header,
        question = "Pick one?",
        options = options.map { QuestionOption(id = it, label = it) },
        multiple = multiple,
    )

    private fun itemWithOptions(
        options: List<QuestionOption>,
        multiple: Boolean = false,
        header: String? = null,
    ) = QuestionItem(
        header = header,
        question = "Pick one?",
        options = options,
        multiple = multiple,
    )

    @Test
    fun `short single-choice options become chips`() {
        val chips = quickReplyChips(item(listOf("Ya", "Tidak"), header = "Lanjut?"))

        assertEquals(listOf("Ya", "Tidak"), chips.map { it.label })
        assertEquals(listOf("Ya", "Tidak"), chips.map { it.optionId })
    }

    @Test
    fun `chips are capped at four and keep option order`() {
        val chips = quickReplyChips(item(listOf("A", "B", "C", "D", "E", "F")))

        assertEquals(listOf("A", "B", "C", "D"), chips.map { it.label })
    }

    @Test
    fun `long labels are not chips`() {
        val chips = quickReplyChips(
            item(listOf("Ya", "Perbarui semua dependensi ke versi terbaru")),
        )

        assertEquals(listOf("Ya"), chips.map { it.label })
    }

    @Test
    fun `multi-choice questions have no chips`() {
        assertTrue(quickReplyChips(item(listOf("A", "B"), multiple = true)).isEmpty())
    }

    @Test
    fun `a question with no options has no chips and none are invented`() {
        assertTrue(quickReplyChips(item(emptyList())).isEmpty())
    }

    @Test
    fun `same option listed twice chips once`() {
        // Audit-200 D9 (m-447): the same id twice is one option, not two
        // — the answer it sends is unambiguous, so one chip is safe.
        // Ambiguity is the same label under DIFFERENT ids (next test):
        // those cannot be told apart, so that label gets no chip.
        // (m-117 guarantees ids are unique per option at parse time, so
        // a repeated id can only be the same option listed twice.)
        val chips = quickReplyChips(item(listOf("Ya", "Ya", "Tidak")))

        assertEquals(listOf("Ya", "Tidak"), chips.map { it.label })
    }

    @Test
    fun `same label with different ids still produces no chip for that label`() {
        val chips = quickReplyChips(
            itemWithOptions(
                listOf(
                    QuestionOption(id = "yes-a", label = "Ya"),
                    QuestionOption(id = "yes-b", label = "Ya"),
                    QuestionOption(id = "no", label = "Tidak"),
                ),
            ),
        )

        assertEquals(listOf("Tidak"), chips.map { it.label })
    }

    @Test
    fun `exact duplicate options chip once`() {
        val chips = quickReplyChips(
            itemWithOptions(
                listOf(
                    QuestionOption(id = "a", label = "A"),
                    QuestionOption(id = "a", label = "A"),
                    QuestionOption(id = "b", label = "B"),
                ),
            ),
        )

        assertEquals(listOf("A", "B"), chips.map { it.label })
    }

    @Test
    fun `formatQuestionAnswer matches the card with and without a header`() {
        assertEquals(
            "Pembaruan: Fitur / UI",
            formatQuestionAnswer(item(listOf("Fitur / UI"), header = "Pembaruan"), listOf("Fitur / UI")),
        )
        assertEquals(
            "A, B",
            formatQuestionAnswer(item(listOf("A", "B")), listOf("A", "B")),
        )
    }
}
