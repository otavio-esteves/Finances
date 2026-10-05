package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.model.StatementKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class PdfStatementParserTest {
    private fun parser(text: String) = PdfStatementParser(PdfTextExtractor { text })
    private val pdfBytes = "%PDF-1.4".toByteArray()

    @Test
    fun rejectsInvalidCalendarDatesAndAcceptsLeapDay() = runBlocking {
        for (date in listOf("31/02/2026", "29/02/2025", "31/04/2026")) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { parser("$date Mercado -10,00").parse("extrato.pdf", pdfBytes) }
            }
        }
        val entries = parser("29/02/2024 Mercado -10,00").parse("extrato.pdf", pdfBytes)
        assertEquals(LocalDate.of(2024, 2, 29), entries.single().date)
    }

    @Test
    fun preservesZeroAmountWithDebitMarker() = runBlocking {
        val entries = parser("10/01/2026 Sem movimento 0,00 D").parse("extrato.pdf", pdfBytes)
        assertEquals(0L, entries.single().amount.cents)
    }

    @Test
    fun bankStatement_readsSignedAmountsAndDebitCreditMarkers() = runBlocking {
        val entries = parser("""
            Extrato da conta
            10/01/2026 Mercado Central R$ -150,75
            11/01/2026 Salário +3.000,00
            12/01/2026 Farmácia 25,90 D
        """.trimIndent()).parse("extrato.pdf", pdfBytes)

        assertEquals(3, entries.size)
        assertEquals("Mercado Central", entries[0].description)
        assertEquals(15_075L, entries[0].amount.cents)
        assertEquals(TransactionType.EXPENSE, entries[0].type)
        assertEquals(LocalDate.of(2026, 1, 10), entries[0].date)
        assertEquals(300_000L, entries[1].amount.cents)
        assertEquals(TransactionType.INCOME, entries[1].type)
        assertEquals(TransactionType.EXPENSE, entries[2].type)
    }

    @Test
    fun cardBill_treatsUnsignedPurchasesAsExpensesAndNegativeRefundsAsIncome() = runBlocking {
        val entries = parser("""
            Fatura do cartão de crédito
            2026-01-10 Mercado Central 150,75
            2026-01-11 Estorno loja -20,00
            2026-01-20 Total da fatura 130,75
        """.trimIndent()).parse("fatura.pdf", pdfBytes)

        assertEquals(2, entries.size)
        assertEquals(TransactionType.EXPENSE, entries[0].type)
        assertEquals(TransactionType.INCOME, entries[1].type)
    }

    @Test
    fun selectedCardBill_treatsUnsignedPurchasesAsExpensesWithoutHeader() = runBlocking {
        val entries = parser("10/01/2026 Mercado 25,90")
            .parse("documento.pdf", pdfBytes, StatementKind.CARD_BILL)

        assertEquals(TransactionType.EXPENSE, entries.single().type)
    }

    @Test
    fun readsTransactionsWrappedAcrossLines() = runBlocking {
        val entries = parser("""
            Fatura
            10/01/2026 Mercado Central
            unidade centro
            150,75
            11/01/2026
            Estorno loja -20,00
        """.trimIndent()).parse("fatura.pdf", pdfBytes, StatementKind.CARD_BILL)

        assertEquals(2, entries.size)
        assertEquals("Mercado Central unidade centro", entries[0].description)
        assertEquals(15_075L, entries[0].amount.cents)
        assertEquals(TransactionType.EXPENSE, entries[0].type)
        assertEquals("Estorno loja", entries[1].description)
        assertEquals(TransactionType.INCOME, entries[1].type)
    }

    @Test
    fun rejectsAmbiguousUnsignedBankAmount() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { parser("10/01/2026 Mercado 25,90").parse("extrato.pdf", pdfBytes) }
        }
    }

    @Test
    fun rejectsPotentiallyIncompleteLayout() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                parser("Fatura\n10/01/2026 Mercado 25,90 30,00")
                    .parse("fatura.pdf", pdfBytes)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                parser("Fatura\n10/01/2026 Mercado\n11/01/2026 Loja 10,00")
                    .parse("fatura.pdf", pdfBytes)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                parser("Fatura\n10/01/2026 Mercado 25,90\n11/01 Loja 10,00")
                    .parse("fatura.pdf", pdfBytes)
            }
        }
    }

    @Test
    fun rejectsScannedAndInvalidPdf() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { parser(" ").parse("scan.pdf", pdfBytes) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { parser("Fatura\n10/01/2026 Mercado 25,90").parse("fake.pdf", "hello".toByteArray()) }
        }
    }
}
