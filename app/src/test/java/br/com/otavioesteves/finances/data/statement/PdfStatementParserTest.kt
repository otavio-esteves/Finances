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
    fun cardBillPositiveSignsRemainExpensesAndAgreeWithDebitMarkers(): Unit = runBlocking {
        for (amount in listOf("100,00", "+100,00", "100,00+", "R$ +100,00", "+100,00 D")) {
            val entry = parser("01/11/2026 Mercado $amount")
                .parse("fatura.pdf", pdfBytes, StatementKind.CARD_BILL).single()
            assertEquals(amount, TransactionType.EXPENSE, entry.type)
            assertEquals(10000L, entry.amount.cents)
        }
        val refund = parser("Fatura\n01/11/2026 Estorno loja -100,00 C")
            .parse("fatura.pdf", pdfBytes).single()
        assertEquals(TransactionType.INCOME, refund.type)
        for (text in listOf("Fatura\n01/11/2026 Mercado +100,00",
            "Fatura\ndata\tdescricao\tvalor\n01/11/2026\tMercado\t+100,00")) {
            val entry = parser(text).parse("fatura.pdf", pdfBytes).single()
            assertEquals(TransactionType.EXPENSE, entry.type)
            assertEquals(TransactionType.INCOME, entry.legacyInputType)
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { parser("Fatura\n01/11/2026 Mercado +100,00 C").parse("fatura.pdf", pdfBytes) }
        }
    }

    @Test
    fun cardPaymentIsTransferButRefundMentioningThePaymentRemainsIncome() = runBlocking {
        val entries = parser("Fatura\n01/11/2026 Pagamento do cartão -100,00\n" +
            "02/11/2026 Pagamento da fatura estornado -20,00").parse("fatura.pdf", pdfBytes)
        assertEquals(listOf(TransactionType.TRANSFER, TransactionType.INCOME), entries.map { it.type })
    }

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
    fun shortDatesUseExplicitPeriodAcrossYearBoundary() = runBlocking {
        val entries = parser("""
            Extrato da conta
            Período: 01/12/2025 até 31/01/2026
            Data Histórico Valor Saldo
            20/12 Mercado 150,75 D 2.000,00 C
            10/01 Salário 3.000,00 C 5.000,00 C
        """.trimIndent()).parse("extrato.pdf", pdfBytes)
        assertEquals(listOf(LocalDate.of(2025, 12, 20), LocalDate.of(2026, 1, 10)), entries.map { it.date })
        assertEquals(listOf(15_075L, 300_000L), entries.map { it.amount.cents })
        assertEquals(listOf(TransactionType.EXPENSE, TransactionType.INCOME), entries.map { it.type })
    }

    @Test
    fun shortAndNamedDatesUseExplicitMonthOrYear() = runBlocking {
        val monthly = parser("Fatura de janeiro/2026\n10 JAN Mercado 10,00\n11/01 Loja 25,00")
            .parse("fatura.pdf", pdfBytes)
        assertEquals(listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11)), monthly.map { it.date })
        val annual = parser("Ano: 2024\n29/02 Mercado -10,00\n01 MAR Farmácia 25,00 D")
            .parse("extrato.pdf", pdfBytes)
        assertEquals(LocalDate.of(2024, 2, 29), annual.first().date)
        val explicit = parser("10 JAN 2026 Mercado -10,00").parse("extrato.pdf", pdfBytes)
        assertEquals(LocalDate.of(2026, 1, 10), explicit.single().date)
    }

    @Test
    fun rejectsShortDatesWithoutUnambiguousDocumentReference() {
        listOf(
            "Fatura\n10/01 Mercado 10,00",
            "Fatura de janeiro/2026\n10/12 Mercado 10,00",
            "Fatura\nVencimento: 15/01/2026\n10/01 Mercado 10,00",
            "Ano: 2025\nAno: 2026\n10/01 Mercado -10,00",
            "Período: 01/01/2025 a 31/12/2026\n10/01 Mercado -10,00",
            "Período: 01/01/2026 a 31/01/2026\n10/02 Mercado -10,00",
            "Ano: 2026\n29/02 Mercado -10,00",
            "Ano: 2025\n10/01/26 Mercado -10,00"
        ).forEach { text ->
            assertThrows(text, IllegalArgumentException::class.java) {
                runBlocking { parser(text).parse("documento.pdf", pdfBytes) }
            }
        }
    }

    @Test
    fun acceptsTrailingSignsParenthesesAndWrittenDebitCredit() = runBlocking {
        val entries = parser("""
            10/01/2026 Mercado 150,75-
            11/01/2026 Farmácia (25,00)
            12/01/2026 Salário 3.000,00 Crédito
            13/01/2026 Ônibus 5,00 Débito
        """.trimIndent()).parse("extrato.pdf", pdfBytes)
        assertEquals(listOf(15_075L, 2_500L, 300_000L, 500L), entries.map { it.amount.cents })
        assertEquals(listOf(TransactionType.EXPENSE, TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.EXPENSE), entries.map { it.type })
        val refunds = parser("Fatura\n10/01/2026 Estorno 10,00-\n11/01/2026 Estorno loja (20,00)")
            .parse("fatura.pdf", pdfBytes)
        assertEquals(listOf(TransactionType.INCOME, TransactionType.INCOME), refunds.map { it.type })
    }

    @Test
    fun rejectsConflictingAmountDirectionInsteadOfOverridingIt() {
        listOf("-10,00 C", "+10,00 D", "-R$ -10,00", "(10,00)-").forEach { amount ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { parser("10/01/2026 Mercado $amount").parse("extrato.pdf", pdfBytes) }
            }
        }
    }

    @Test
    fun runningBalanceRequiresExplicitTableHeaderAndEveryColumn() {
        listOf(
            "10/01/2026 Mercado -10,00 500,00",
            "Data Histórico Valor Saldo\n10/01/2026 Mercado -10,00",
            "Data Histórico Valor Saldo\n10/01/2026 Mercado 10,00 500,00",
            "Data Histórico Débito Crédito Saldo\n10/01/2026 Mercado 10,00 0,00 500,00"
        ).forEach { text ->
            assertThrows(text, IllegalArgumentException::class.java) {
                runBlocking { parser(text).parse("extrato.pdf", pdfBytes) }
            }
        }
    }

    @Test
    fun wrapsLongDescriptionsAndPreservesPageHeadersWithoutImportingBalance() = runBlocking {
        val entries = parser("""
            Fatura
            10/01/2026 Mercado
            unidade centro
            compra parcelada
            cartão final 1234
            1 de 2
            Data Descrição Valor
            atendimento local
            150,75
        """.trimIndent()).parse("fatura.pdf", pdfBytes)
        assertEquals("Mercado unidade centro compra parcelada cartão final 1234 atendimento local", entries.single().description)
        assertEquals(15_075L, entries.single().amount.cents)
    }

    @Test
    fun shortDateOnItsOwnLineIsNeverMistakenForPageNumber() = runBlocking {
        val entries = parser("""
            Ano: 2026
            10/01/2026 Mercado -10,00
            11/01
            Farmácia -20,00
            Página 1/2
            1 de 2
            12/01
            Ônibus -5,00
        """.trimIndent()).parse("extrato.pdf", pdfBytes)
        assertEquals(listOf(1_000L, 2_000L, 500L), entries.map { it.amount.cents })
        assertEquals(listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 12)), entries.map { it.date })
    }

    @Test
    fun unsupportedDottedOrNamedDateNeverSilentlyDropsATransaction() {
        listOf("11 FEV/2026", "11 FEB", "11/01/27", "11 JAN..").forEach { date ->
            val text = "Ano: 2026\n10/01/2026 Mercado -10,00\n$date Farmácia -20,00"
            assertThrows(text, IllegalArgumentException::class.java) {
                runBlocking { parser(text).parse("extrato.pdf", pdfBytes) }
            }
        }
    }

    @Test
    fun dottedAndTwoDigitDatesUseExplicitYearWithoutGuessingTheCentury() = runBlocking {
        val entries = parser("Ano: 2026\n10.01.2026 Mercado -10,00\n11 JAN. Farmacia -20,00\n12/01/26 Onibus -5,00")
            .parse("extrato.pdf", pdfBytes)
        assertEquals(listOf(10, 11, 12), entries.map { it.date.dayOfMonth })
        assertEquals(listOf(2026, 2026, 2026), entries.map { it.date.year })
        assertThrows(IllegalArgumentException::class.java) { runBlocking { parser("10/01/26 Compra -10,00").parse("sem_ano.pdf", pdfBytes) } }
        Unit
    }

    @Test
    fun contradictoryYearAndMonthReferencesRejectTheWholeStatement() {
        listOf(
            "Ano: 2025\nFatura de janeiro/2026",
            "Ano: 2025\nReferência: janeiro de 2026",
            "Ano: 2025 Ano: 2026",
            "Ano: 2025\nReferência: janeiro/2025 e fevereiro/2026"
        ).forEach { header ->
            assertThrows(header, IllegalArgumentException::class.java) {
                runBlocking { parser("$header\n10/01/2025 Mercado -10,00\n11/01 Farmácia -20,00").parse("extrato.pdf", pdfBytes) }
            }
        }
    }

    @Test
    fun boundedWriterRejectsExpandedPdfTextBeforeAllocatingFullResult() {
        val writer = BoundedTextWriter(5)
        writer.write("12345".toCharArray(), 0, 5)
        assertEquals("12345", writer.toString())
        assertThrows(IllegalArgumentException::class.java) { writer.write(charArrayOf('6'), 0, 1) }
        assertEquals("12345", writer.toString())
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
