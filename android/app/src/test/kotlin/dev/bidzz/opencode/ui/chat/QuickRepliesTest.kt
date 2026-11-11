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

    @Test
    fun `short single-choice options become chips with the card answer text`() {
        val chips = quickReplyChips(item(listOf("Ya", "Tidak"), header = "Lanjut?"))

        assertEquals(listOf("Ya", "Tidak"), chips.map { it.label })
        assertEquals(listOf("Ya", "Tidak"), chips.map { it.optionId })
        assertEquals("Lanjut?: Ya", chips[0].answer)
        assertEquals("Lanjut?: Tidak", chips[1].answer)
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
    fun `duplicate labels chip once`() {
        val chips = quickReplyChips(item(listOf("Ya", "Ya", "Tidak")))

        assertEquals(listOf("Ya", "Tidak"), chips.map { it.label })
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
