package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.usecase.SynthesizeStatementUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BetaImportIntegrityTest {
    private val pdf = PdfStatementParser { "" }
    private val category = Category(1, "Outros", CategoryType.EXPENSE)
    private val rows = listOf(
        RawStatementEntry("Compra parcela 01/03", Money(1234), LocalDate.of(2026, 12, 31), TransactionType.EXPENSE),
        RawStatementEntry("Compra parcela 02/03", Money(1234), LocalDate.of(2027, 1, 31), TransactionType.EXPENSE)
    )

    @Test fun unknownExplicitUnitsCannotSilentlyBecomeReais() {
        for (label in listOf("Valor em centésimos", "Valor em unidade desconhecida", "Amount in cents of a real", "Valor (XYZ)", "Valor em unidades")) {
            assertThrows(label, IllegalArgumentException::class.java) {
                runBlocking { CsvOfxStatementParser().parse("a.csv", "Data;Descrição;$label\n01/11/2026;Compra;1234".toByteArray()) }
            }
        }
    }

    @Test fun supportedMajorUnitHeadersKeepTheExactCents() = runBlocking {
        for (label in listOf("Valor", "Valor em reais", "Valor\u00a0em\u00a0reais", "Valor (R$)", "Valor da transação (BRL)", "Amount (BRL)", "Transaction Amount", "Valor líquido")) {
            val result = CsvOfxStatementParser().parse("a.csv", "Data;Descrição;$label\n01/11/2026;Compra;12,34".toByteArray()).single()
            assertEquals(label, 1234L, result.amount.cents)
        }
    }

    @Test fun allWordBoundariesInKnownCurrencyLabelsCanWrapAcrossLines() {
        for (label in listOf("Moeda do documento:", "Valores expressos em", "Todos os valores informados em", "All amounts expressed in")) {
            val words = label.split(' ')
            for (split in 1 until words.size) {
                val declaration = words.take(split).joinToString(" ") + "\n" + words.drop(split).joinToString(" ") + "\nUSD"
                assertThrows(declaration, IllegalArgumentException::class.java) {
                    pdf.parseText("Fatura\n$declaration\n01/11/2026 Compra 100,00")
                }
                val converted = pdf.parseText("Fatura\n$declaration\n01/11/2026 Compra R$ 100,00").single()
                assertEquals("Compra", converted.description)
                assertEquals(10_000L, converted.amount.cents)
            }
        }
    }

    @Test fun unresolvedCurrencyMetadataCannotBeDiscardedEvenWithAMarkedValue() {
        assertThrows(IllegalArgumentException::class.java) {
            pdf.parseText("Fatura\nMoeda:\nData Descrição Valor\n01/11/2026 Compra R$ 100,00")
        }
    }

    @Test fun categorizationCannotDropDuplicateReorderOrMutateFinancialInputs() {
        val valid = rows.map { CategorySuggestion(it, category, 0.7f) }
        val broken = listOf(valid.take(1), listOf(valid[0], valid[0]), valid.reversed(),
            listOf(valid[0].copy(entry = rows[0].copy(amount = Money(123400))), valid[1]),
            listOf(valid[0].copy(originalEntry = rows[1]), valid[1]))
        for (output in broken) assertThrows(IllegalArgumentException::class.java) {
            runBlocking { synthesize(output)(rows) }
        }
        assertEquals(valid, runBlocking { synthesize(valid)(rows) })
    }

    @Test fun identifiedAccountCoverBalancesNeverBecomeMovementsAndBothTotalsMustReconcile() {
        val text = """
            Extrato de conta
            Período: 01/11/2026 a 30/11/2026
            Entradas: R$ 100,00
            Saldo inicial: R$ 10,00 Saldo final: R$ 90,00
            Saídas: R$ -20,00
            DETALHE DOS MOVIMENTOS
            data\tdescricao\tdocumento\tvalor\tsaldo
            01/11/2026\tCrédito\t111\tR$ 100,00\tR$ 110,00
            02/11/2026\tCompra\t222\tR$ -20,00\tR$ 90,00
        """.trimIndent().replace("\\t", "\t")
        assertEquals(listOf(10_000L, 2000L), pdf.parseText(text).map { it.amount.cents })
        assertThrows(IllegalArgumentException::class.java) { pdf.parseText(text.replace("Entradas: R$ 100,00", "Entradas: R$ 101,00")) }
        assertThrows(IllegalArgumentException::class.java) { pdf.parseText(text.replace("R$ -20,00\tR$ 90,00", "R$ -21,00\tR$ 90,00")) }
    }

    @Test fun aHolderSubtotalIsAcceptedOnlyInAnIdentifiedBillAndMustMatchItsSection() {
        val text = """
            Nubank
            FATURA 07 FEV 2025 EMISSÃO E ENVIO 31 JAN 2025
            TRANSAÇÕES DE 31 DEZ A 31 JAN
            Pessoa Fictícia R$ 50,00
            02 JAN •••• 1234 Loja Exemplo R$ 60,00
            03 JAN Estorno da compra -R$ 10,00
            Pagamentos -R$ 40,00
            04 JAN Pagamento em 04 JAN -R$ 40,00
            Em cumprimento à regulação
        """.trimIndent()
        assertEquals(listOf(6000L, 1000L, 4000L), pdf.parseText(text, StatementKind.CARD_BILL).map { it.amount.cents })
        assertThrows(IllegalArgumentException::class.java) { pdf.parseText(text.replace("Pessoa Fictícia R$ 50,00", "Pessoa Fictícia R$ 51,00")) }
        assertThrows(IllegalArgumentException::class.java) { pdf.parseText(text.replace("Nubank", "Banco sem layout identificado")) }
        assertThrows(IllegalArgumentException::class.java) { pdf.parseText(text.replace("Pagamentos -R$ 40,00", "Pagamentos -R$ 41,00")) }
    }

    @Test fun aBrlAndPercentageLegendIsNotADeclarationThatTheMoneyIsAPercentage() {
        val entry = pdf.parseText("Fatura\nValores em R$ % do total parcelado\n01/11/2026 Compra 100,00").single()
        assertEquals(10_000L, entry.amount.cents)
        assertThrows(IllegalArgumentException::class.java) {
            pdf.parseText("Fatura\nValores em %\n01/11/2026 Compra R$ 100,00")
        }
    }

    @Test fun bankNamesAndTransactionSectionsCannotInvertAccountStatementSigns() {
        val headings = listOf(
            "Nubank\nEMISSÃO E ENVIO 31 JAN 2025\nTRANSAÇÕES DE 31 DEZ A 31 JAN",
            "PicPay\nFechamento: 29/01/2025\nTransações Nacionais"
        )
        for (heading in headings) {
            val text = "$heading\n02/01/2025 Compra -50,00"
            assertEquals(TransactionType.EXPENSE, pdf.parseText(text, StatementKind.BANK_STATEMENT).single().type)
            assertEquals(TransactionType.INCOME, pdf.parseText(text, StatementKind.CARD_BILL).single().type)
        }
    }

    @Test fun picpaySectionAndGrandTotalsExcludePaymentsButIncludeRefunds() {
        val text = "PicPay\nFechamento: 29/01/2025\nTransações Nacionais\n" +
            "02/01 PAGAMENTO DE FATURA -40,00\n03/01 Compra 60,00\n04/01 Estorno -10,00\n" +
            "Subtotal dos lançamentos 50,00\nTotal geral dos lançamentos 50,00"
        assertEquals(listOf(4000L, 6000L, 1000L), pdf.parseText(text, StatementKind.CARD_BILL).map { it.amount.cents })
        for (bad in listOf(text.replace("Subtotal dos lançamentos 50,00", "Subtotal dos lançamentos 51,00"),
            text.replace("Total geral dos lançamentos 50,00", "Total geral dos lançamentos 51,00"))) {
            assertThrows(IllegalArgumentException::class.java) { pdf.parseText(bad, StatementKind.CARD_BILL) }
        }
    }

    private fun synthesize(output: List<CategorySuggestion>) = SynthesizeStatementUseCase(
        object : TransactionCategorizer {
            override suspend fun categorize(entries: List<RawStatementEntry>, categories: List<Category>, onProgress: (Int, Int) -> Unit) = Result.success(output)
        },
        object : CategoriesRepository {
            override fun getCategories() = flowOf(listOf(category))
            override fun getCategorySummaries(period: MonthPeriod) = flowOf(emptyList<CategorySummary>())
        }
    )
}
