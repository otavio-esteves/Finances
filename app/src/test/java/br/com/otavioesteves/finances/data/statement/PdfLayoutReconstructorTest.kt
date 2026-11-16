package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.model.TransactionType
import org.junit.Assert.*
import org.junit.Test

class PdfLayoutReconstructorTest {
    private val parser = PdfStatementParser { "" }
    private fun w(text: String, left: Float, right: Float, top: Float = 40f) =
        PdfWord(text, left, top, right, top + 10f)

    private fun header() = listOf(w("Data", 10f, 35f, 10f), w("Descrição", 100f, 180f, 10f),
        w("Valor", 430f, 480f, 10f))

    private fun row() = listOf(w("01/11/2026", 10f, 90f), w("Loja", 100f, 160f), w("1.000,00", 420f, 480f))

    @Test
    fun separateSignsAndCreditDebitMarkersStayWithTheAmount() {
        val cases = listOf(
            listOf(w("-", 410f, 415f)) to TransactionType.INCOME,
            listOf(w("−", 410f, 415f)) to TransactionType.INCOME,
            listOf(w("+", 410f, 415f)) to TransactionType.EXPENSE,
            listOf(w("C", 485f, 492f)) to TransactionType.INCOME,
            listOf(w("CR", 485f, 499f)) to TransactionType.INCOME,
            listOf(w("D", 485f, 492f)) to TransactionType.EXPENSE,
            listOf(w("(", 410f, 415f), w(")", 485f, 490f)) to TransactionType.INCOME,
            listOf(w("R$", 385f, 400f), w("-", 410f, 415f)) to TransactionType.INCOME,
            listOf(w("-R$", 395f, 415f)) to TransactionType.INCOME,
            listOf(w("R$-", 395f, 415f)) to TransactionType.INCOME,
            listOf(w("(R$", 395f, 415f), w(")", 485f, 490f)) to TransactionType.INCOME
        )
        for ((markers, expected) in cases) {
            val text = PdfLayoutReconstructor().reconstruct(header() + row() + markers, 600f)
            val entry = parser.parseText(text, StatementKind.CARD_BILL).single()
            assertEquals(text, expected, entry.type)
            assertEquals(text, "Loja", entry.description)
            assertEquals(100_000L, entry.amount.cents)
        }
    }

    @Test
    fun conflictingSeparatedMarkersAreRejectedInsteadOfChangingTheDirection() {
        val text = PdfLayoutReconstructor().reconstruct(header() + row() +
            listOf(w("-", 410f, 415f), w("D", 485f, 492f)), 600f)
        assertThrows(IllegalArgumentException::class.java) { parser.parseText(text, StatementKind.CARD_BILL) }
    }

    @Test
    fun aMerchantEndingWithALetterBeforeTheValueDoesNotBecomeACreditMarker() {
        for (letter in listOf("C", "D", "CR")) {
            val text = PdfLayoutReconstructor().reconstruct(header() + row() + w(letter, 405f, 415f), 600f)
            val entry = parser.parseText(text, StatementKind.CARD_BILL).single()
            assertEquals("Loja $letter", entry.description)
            assertEquals(TransactionType.EXPENSE, entry.type)
        }
    }

    @Test
    fun aMarkerEquidistantFromTwoAmountsIsRejected() {
        val words = header().dropLast(1) + listOf(w("Valor", 400f, 440f, 10f), w("Saldo", 480f, 520f, 10f)) +
            row().dropLast(1) + listOf(w("100,00", 400f, 440f), w("-", 455f, 465f), w("200,00", 480f, 520f))
        assertThrows(IllegalArgumentException::class.java) { PdfLayoutReconstructor().reconstruct(words, 600f) }
    }

    @Test
    fun centeredStatementKeepsSeparateSignsOnTheirOwnValueAndBalance() {
        val words = header().dropLast(1) + listOf(w("ID", 260f, 270f, 10f), w("da", 275f, 290f, 10f),
            w("operação", 295f, 340f, 10f), w("Valor", 400f, 430f, 10f), w("Saldo", 510f, 550f, 10f)) +
            row().dropLast(1) + listOf(w("-", 365f, 370f), w("100,00", 375f, 430f),
            w("200,00", 495f, 550f), w("C", 555f, 563f))
        val text = PdfLayoutReconstructor().reconstruct(words, 600f)
        val entry = parser.parseText(text).single()
        assertEquals(TransactionType.EXPENSE, entry.type)
        assertEquals("Loja", entry.description)
        assertEquals(10_000L, entry.amount.cents)
    }
}
