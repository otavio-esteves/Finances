package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.model.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Synthetic reproductions of the structures found in the private corpus; no customer data. */
class RealLayoutRegressionTest {
    private val parser = PdfStatementParser { "" }

    @Test
    fun financialOcrConfidenceDoesNotApplyToMaskedCpfOrAccountFragments() {
        listOf("***.123.456***", "123.456", "1.234", "000000", "12:30:00").forEach {
            assertFalse(it, isFinancialOcrToken(it))
        }
        listOf("1.234,56", "-20,00", "10/01/2025", "10.01.2025", "10/01", "2025-01-10").forEach {
            assertTrue(it, isFinancialOcrToken(it))
        }
    }

    @Test
    fun namedBillPeriodUsesEmissionAndExcludesOldYearInLegalNotice() {
        val text = """
            Fatura anterior e declaração de quitação no ano de 2023
            FATURA 07 FEV 2025 EMISSÃO E ENVIO 31 JAN 2025
            Simulação de parcelamento R$ 999,00
            RESUMO DA FATURA ATUAL
            TRANSAÇÕES DE 31 DEZ A 31 JAN
            Cartão titular R$ 50,00
            02 JAN •••• 1234 Mercado R$ 50,00
            Pagamentos -R$ 40,00
            03 JAN Pagamento em 03 JAN −R$ 40,00
            Em cumprimento à regulação do Banco Central
            01/01/2020 Informação legal R$ 800,00
        """.trimIndent()
        val entries = parser.parseText(text, StatementKind.CARD_BILL)
        assertEquals(listOf(5000L, 4000L), entries.map { it.amount.cents })
        assertEquals(listOf(TransactionType.EXPENSE, TransactionType.TRANSFER), entries.map { it.type })
        assertEquals(listOf(LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 3)), entries.map { it.date })
    }

    @Test
    fun picpayClosingResolvesYearBoundaryWithoutUsingDueYearOrPromotionalRows() {
        val text = """
            PicPay
            Vencimento: 05/02/2025 | Fechamento: 29/01/2025
            Simulação de parcelamento 10/02 R$ 900,00
            
            PicPay
            Transações Nacionais
            Data Estabelecimento Valor (R$)
            30/12 Mercado 20,00
            03/01 Farmácia 30,00
            04/01 PAGAMENTO DE FATURA -40,00
            Subtotal dos lançamentos 50,00
            
            Total parcelado - próximas faturas
            10/02 Oferta 900,00
        """.trimIndent()
        val entries = parser.parseText(text, StatementKind.CARD_BILL)
        assertEquals(3, entries.size)
        assertEquals(LocalDate.of(2024, 12, 30), entries[0].date)
        assertEquals(LocalDate.of(2025, 1, 3), entries[1].date)
        assertEquals(TransactionType.TRANSFER, entries[2].type)
        assertThrows(IllegalArgumentException::class.java) {
            parser.parseText(text.replace("30/12 Mercado", "30/10 Mercado"), StatementKind.CARD_BILL)
        }
    }

    @Test
    fun picpaySubtotalsSplitAcrossPositionedCellsAreNotTransactionDates() {
        val subtotals = listOf(
            "Subtotal\tdos lançamentos\t50,00",
            "\tSubtotal dos lançamentos\t50,00",
            "\t\tSubtotal dos lançamentos 50,00",
            "Subtotal dos\tlançamentos\tR$ 50,00"
        )
        for (subtotal in subtotals) {
            val text = "PicPay\nFechamento: 29/01/2025\n\u000c\nTransações Nacionais\n" +
                "data\tdescricao\tvalor\n" +
                "02/01\tMercado Exemplo\t20,00\n" +
                "03/01\tFarmácia Exemplo\t30,00\n" + subtotal
            val entries = parser.parseText(text, StatementKind.CARD_BILL, strictUnmatchedMoney = true)
            assertEquals(subtotal, listOf(2000L, 3000L), entries.map { it.amount.cents })
            assertEquals(listOf(LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 3)), entries.map { it.date })
        }
    }

    @Test
    fun subtotalDoesNotCompleteAnUnfinishedTransactionOrHideAnInvalidDate() {
        val prefix = "Fatura de janeiro/2025\ndata\tdescricao\tvalor\n"
        for (row in listOf("03/01\tCompra incompleta\t", "31/02/2025\tCompra inválida\t20,00")) {
            assertThrows(IllegalArgumentException::class.java) {
                parser.parseText(prefix + row + "\nSubtotal\tdos lançamentos\t20,00", StatementKind.CARD_BILL)
            }
        }
        val datedPurchase = parser.parseText(prefix + "03/01\tSubtotal dos lançamentos Loja Exemplo\t20,00",
            StatementKind.CARD_BILL)
        assertEquals("Subtotal dos lançamentos Loja Exemplo", datedPurchase.single().description)
    }

    @Test
    fun independentCardColumnsIncludePurchasesAboveTheFirstHeader() {
        val words = listOf(
            w("PicPay", 20f, 10f),
            w("03/01", 305f, 40f), w("Loja A", 355f, 40f), w("10,00", 550f, 40f, 585f),
            w("Data", 20f, 65f), w("Estabelecimento", 80f, 65f), w("Valor", 245f, 65f),
            w("04/01", 305f, 70f), w("Loja B", 355f, 70f), w("20,00", 550f, 70f, 585f),
            w("01/01", 20f, 95f), w("Loja C", 80f, 95f), w("30,00", 250f, 95f, 285f),
            w("02/01", 20f, 125f), w("Loja D", 80f, 125f), w("40,00", 250f, 125f, 285f)
        )
        val text = PdfLayoutReconstructor().reconstruct(words, 600f)
        val entries = parser.parseText("Ano: 2025\n$text", StatementKind.CARD_BILL)
        assertEquals(listOf(3000L, 4000L, 1000L, 2000L), entries.map { it.amount.cents })
        assertEquals(listOf("Loja C", "Loja D", "Loja A", "Loja B"), entries.map { it.description })
    }

    @Test
    fun centeredDescriptionsKeepPrefixesSuffixesAndPageContinuationsTogether() {
        val layout = PdfLayoutReconstructor()
        val first = layout.reconstruct(statementHeader() + listOf(
            w("Pix enviado", 100f, 50f, 180f),
            w("10/01/2025", 10f, 60f, 80f), w("Comércio", 100f, 60f, 170f), w("123456789", 260f, 60f, 330f),
            w("R$", 365f, 60f, 380f), w("-20,00", 390f, 60f, 430f), w("R$", 470f, 60f, 485f), w("80,00", 500f, 60f, 540f),
            w("Centro", 100f, 70f, 150f),
            w("Pix recebido", 100f, 110f, 190f), w("1/2", 520f, 140f, 540f)
        ), 600f)
        val second = layout.reconstruct(statementHeader() + listOf(
            w("11/01/2025", 10f, 50f, 80f), w("Pessoa exemplo", 100f, 50f, 210f), w("987654321", 260f, 50f, 330f),
            w("R$", 365f, 50f, 380f), w("30,00", 395f, 50f, 430f), w("R$", 470f, 50f, 485f), w("110,00", 500f, 50f, 540f)
        ), 600f)
        layout.finish()
        val entries = parser.parseText("Mercado Pago\nDETALHE DOS MOVIMENTOS\nEntradas: R$ 30,00\nSaidas: R$ -20,00\n$first\n$second")
        assertEquals(listOf(2000L, 3000L), entries.map { it.amount.cents })
        assertEquals("Pix enviado Comércio Centro 123456789", entries[0].description)
        assertEquals("Pix recebido Pessoa exemplo 987654321", entries[1].description)
        assertEquals(listOf(TransactionType.EXPENSE, TransactionType.INCOME), entries.map { it.type })
        assertThrows(IllegalArgumentException::class.java) {
            parser.parseText("Mercado Pago\nDETALHE DOS MOVIMENTOS\nEntradas: R$ 31,00\nSaidas: R$ -20,00\n$first\n$second")
        }
    }

    @Test
    fun unfinishedDescriptionAtEndOfDocumentIsRejected() {
        val layout = PdfLayoutReconstructor()
        layout.reconstruct(statementHeader() + w("Pix enviado", 100f, 50f, 190f), 600f)
        assertThrows(IllegalArgumentException::class.java) { layout.finish() }
    }

    @Test
    fun caixaTimeAndDailyBalanceNeverCreateExtraTransactions() {
        val entries = parser.parseText("Extrato da conta\nConta 1234 / 5678\nData 20/01/2025 - 12:00\n" +
            "data\tdocumento\tdescricao\tfavorecido\tidentificador\tvalor\tsaldo\n" +
            "10/01/2025 - 00:00:00\t000000\tSALDO DIA\t\t\t0,00 C\t900,00 C\n" +
            "10/01/2025 - 10:30:00\t000123\tRECEBIMENTO TED SALARIO\tPessoa exemplo\t***000***\t900,00 C\t900,00 C")
        assertEquals(1, entries.size)
        assertEquals(90000L, entries.single().amount.cents)
        assertEquals(TransactionType.INCOME, entries.single().type)
        assertFalse(entries.single().description.contains("***"))
    }

    private fun statementHeader() = listOf(
        w("Data", 10f, 10f), w("Descrição", 100f, 10f, 170f),
        w("ID", 260f, 10f, 270f), w("da", 275f, 10f, 290f), w("operação", 295f, 10f, 340f),
        w("Valor", 400f, 10f, 430f), w("Saldo", 510f, 10f, 540f)
    )

    private fun w(text: String, x: Float, y: Float, right: Float = x + 40f) = PdfWord(text, x, y, right, y + 8f)
}
