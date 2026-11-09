package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.model.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Synthetic cases based on the public sources documented in docs/PDF_IMPORTACAO.md. */
class BankCardPdfTextTest {
    private val parser = PdfStatementParser { "" }
    private fun parse(text: String, year: Int? = null) = parser.parseText(text, StatementKind.CARD_BILL, year)

    @Test fun santanderFooterDoesNotEndPurchasesOnFollowingPages() {
        val entries = parse("Santander\nHistórico das despesas\n01/11/2026 Mercado 10,00\n" +
            "Central de atendimento 0800 123 4567\nResumo da fatura disponível no app 999,00\n\u000c\n" +
            "Data Descrição Valor\n02/11/2026 Farmácia 50,00\nCentral de atendimento\n\u000c\n" +
            "03/11/2026 Total 30,00\nResumo da fatura\nOferta 300,00")
        assertEquals(listOf("Mercado", "Farmácia", "Total"), entries.map { it.description })
        assertEquals(listOf(1000L, 5000L, 3000L), entries.map { it.amount.cents })
    }

    @Test fun pageBreakDoesNotReopenATerminatedPurchaseSection() {
        val entries = parse("Santander\nDetalhamento da fatura\n01/11/2026 Mercado 10,00\n" +
            "Resumo da fatura\nCentral de atendimento\n\u000c\n02/12/2026 Oferta futura 900,00")
        assertEquals(listOf("Mercado"), entries.map { it.description })
    }

    @Test fun santanderFooterCannotCompleteAnUnfinishedPurchase() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("Santander\nHistórico das despesas\n01/11/2026 Compra incompleta\n" +
                "Central de atendimento\nValor de rodapé 10,00\n\u000c\n02/11/2026 Outra compra 20,00")
        }
    }

    @Test fun interNamedDatesSeparatorsCreditsAndConvertedForeignAmounts() {
        val entries = parse("""
            Banco Inter
            Resumo da fatura R$ 142,00
            Despesas da fatura
            CARTÃO 0000****1111
            Data Movimentação Beneficiário Valor
            18 de dez. 2025 PAGTO DEBITO AUTOMATICO - + R$ 100,00
            30 de dez. 2025 Loja exterior
            Valor e símbolo da moeda de origem: 20,00 USD
            Valor em dólar americano: $ 20,00
            Cotação do dólar americano: R$ 5,2500
            - R$ 105,00
            Total CARTÃO 0000****1111 R$ 105,00
            02 de jan. 2026 Mercado - R$ 37,00
            03 de jan. 2026 Estorno loja - + R$ 5,00
            Limite de crédito total:
            R$ 9.000,00
            Próxima fatura
            10 de fev. 2026 Compra futura - R$ 900,00
        """.trimIndent())
        assertEquals(listOf(10_000L, 10_500L, 3_700L, 500L), entries.map { it.amount.cents })
        assertEquals(listOf(TransactionType.TRANSFER, TransactionType.EXPENSE, TransactionType.EXPENSE, TransactionType.INCOME), entries.map { it.type })
        assertEquals(LocalDate.of(2025, 12, 30), entries[1].date)
        assertEquals(LocalDate.of(2026, 1, 2), entries[2].date)
        assertEquals("Loja exterior", entries[1].description)
    }

    @Test fun interRejectsIncompletePurchasesAndUnrecognizedMoney() {
        for (rows in listOf(
            "10 de jan. 2026 Compra sem valor",
            "10 de jan. 2026 Compra sem valor\n11 de jan. 2026 Outra compra - R$ 10,00",
            "10 de jan. 2026 Compra - R$ 10,00\nFarmácia - R$ 20,00",
            "10 de jan. 2026 Compra exterior\nValor em dólar americano: $ 20,00",
            "10 de jan. 2026 Compra - + - R$ 10,00"
        )) assertThrows(rows, IllegalArgumentException::class.java) {
            parse("Banco Inter\nDespesas da fatura\n$rows\nLimite de crédito total R$ 1.000,00")
        }
    }

    @Test fun bradescoSectionsKeepWrappedDescriptionsAndIgnoreFinancingOffers() {
        val entries = parse("""
            Bradesco Cartões
            Fatura de janeiro/2026
            Simulação 12 x 300,00
            Lançamentos
            Data Histórico de Lançamentos Cidade Valor
            03/01 LOJA EXEMPLO 02/03
            São Paulo
            120,00
            Total para Titular 120,00
            04/01 PAGTO ANTECIPADO PIX 100,00 -
            05/01 Estorno loja 10,00-
            Total da fatura em real 110,00
            Total parcelados 900,00
            20/02 Compra futura 300,00
        """.trimIndent())
        assertEquals(3, entries.size)
        assertEquals("LOJA EXEMPLO 02/03 São Paulo", entries[0].description)
        assertEquals(listOf(TransactionType.EXPENSE, TransactionType.TRANSFER, TransactionType.INCOME), entries.map { it.type })
    }

    @Test fun santanderPositionedCurrencyColumnsUseOnlyTheBilledBrlAmount() {
        val text = "Santander\nFatura de janeiro/2026\nLimite R$ 9.000,00\nDetalhamento da Fatura\n" +
            "data\tdescricao\tvalor\tvalor estrangeiro\n" +
            "03/01\tCompra exterior 02/03\t120,00\t20,00\n" +
            "04/01\tPagamento da fatura\t-80,00\t\n" +
            "Resumo da Fatura\nSaldo anterior 80,00\nTotal 120,00"
        val entries = parse(text)
        assertEquals(listOf(12_000L, 8_000L), entries.map { it.amount.cents })
        assertEquals(TransactionType.TRANSFER, entries[1].type)
        assertThrows(IllegalArgumentException::class.java) {
            parse(text.replace("120,00\t20,00", "\t20,00"))
        }
    }

    @Test fun bbDottedDatesCurrencyAndUserSuppliedYear() {
        val text = """
            |Banco do Brasil
            |Ourocard
            |Vencimento: 10/02/2026
            |Resumo em Real 900,00
            |Detalhamento da sua fatura
            |data	descricao	moeda	valor
            |	SALDO FATURA ANTERIOR	R$	500,00
            |05.01	PGTO DEBITO CONTA 0000	R$	-500,00
            |12.01	Compra exemplo BR	R$	120,00
            |	Subtotal	R$	120,00
            |Parcelamentos Próxima Fatura
            |12.02 Compra futura 100,00
        """.trimMargin()
        assertThrows(IllegalArgumentException::class.java) { parse(text) }
        val entries = parse(text, 2026)
        assertEquals(2, entries.size)
        assertEquals(LocalDate.of(2026, 1, 12), entries.last().date)
        assertEquals(TransactionType.TRANSFER, entries.first().type)
        assertThrows(IllegalArgumentException::class.java) { parse(text.replace("R$\t120,00", "US$\t120,00"), 2026) }
    }

    @Test fun caixaDemonstrativoKeepsChargesAndCreditDebitMarkers() {
        val entries = parse("""
            Cartões CAIXA
            Fatura de janeiro/2026
            Parcelamento de fatura 10 x 300,00
            Demonstrativo
            01/01 TOTAL DA FATURA ANTERIOR 100,00 D
            02/01 OBRIGADO PELO PAGAMENTO 100,00 C
            03/01 MERCADO 65,00 D
            04/01 IOF COMPRA INTERNACIONAL 2,00 D
            05/01 AJUSTE CRED PARC S/ JUROS 1,00 C
            Total 66,00 D
            Demonstrativo do Programa de Pontos Caixa
            Saldo anterior 200,00
        """.trimIndent())
        assertEquals(listOf(10_000L, 6_500L, 200L, 100L), entries.map { it.amount.cents })
        assertEquals(listOf(TransactionType.TRANSFER, TransactionType.EXPENSE, TransactionType.EXPENSE, TransactionType.INCOME), entries.map { it.type })
    }

    @Test fun manualYearNeverOverridesExplicitDocumentPeriods() {
        val entries = parse("Fatura\nPeríodo: 01/12/2025 a 31/01/2026\n30/12 Compra 10,00\n05/01 Compra 20,00", 2024)
        assertEquals(listOf(LocalDate.of(2025, 12, 30), LocalDate.of(2026, 1, 5)), entries.map { it.date })
        for (year in listOf(0, 10000)) assertThrows(IllegalArgumentException::class.java) {
            parse("Fatura\n10/01 Compra 10,00", year)
        }
    }

    @Test fun aSubtotalCannotSupplyTheAmountOfAnIncompletePurchase() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("Bradesco Cartões\nAno: 2026\nLançamentos\n10/01 Compra incompleta\nSubtotal 30,00")
        }
    }

    @Test fun bankNameAloneDoesNotDropUnknownFinancialRows() {
        for (bank in listOf("Santander", "Banco Inter", "Bradesco", "Ourocard", "Caixa")) {
            assertThrows(IllegalArgumentException::class.java) {
                parse("$bank\n10/01/2026 Compra 10,00\nLançamento sem data 20,00")
            }
        }
    }

    @Test fun repeatedBankSectionsKeepPurchasesFromEveryPage() {
        val entries = parse("Santander\nAno: 2026\nDetalhamento da Fatura\n10/01 Compra A 10,00\n" +
            "Resumo da Fatura\nOferta de parcelamento 900,00\n\u000c\nDetalhamento da Fatura\n" +
            "11/01 Compra B 20,00\nResumo da Fatura\nSaldo 30,00")
        assertEquals(listOf("Compra A", "Compra B"), entries.map { it.description })
        assertEquals(listOf(1_000L, 2_000L), entries.map { it.amount.cents })
    }

    @Test fun bbLeftColumnFooterDoesNotHideTheRightColumnPurchases() {
        val entries = parse("Banco do Brasil\nDetalhamento da sua fatura\n" +
            "03.01 PGTO DEBITO CONTA 0000 R$ -50,00\nCompras à vista\n" +
            "Banco do Brasil S.A.\nSBS Quadra 1 - Brasília\n" +
            "10.01 Compra A BR R$ 20,00\n11.01 Compra B BR R$ 30,00\nSubtotal R$ 50,00", 2026)
        assertEquals(listOf(5_000L, 2_000L, 3_000L), entries.map { it.amount.cents })
        assertEquals(TransactionType.TRANSFER, entries.first().type)
    }

    @Test fun currencyHeaderGeometryRetainsBothLocalAndForeignColumns() {
        fun word(text: String, x: Float, y: Float, right: Float = x + 40f) = PdfWord(text, x, y, right, y + 10f)
        val layout = PdfLayoutReconstructor().reconstruct(listOf(
            word("Data", 10f, 10f), word("Descrição", 90f, 10f, 170f),
            word("R$", 400f, 10f, 440f), word("US$", 520f, 10f, 560f),
            word("10/01/2026", 10f, 50f, 80f), word("Compra", 90f, 50f, 160f),
            word("120,00", 390f, 50f, 440f), word("20,00", 520f, 50f, 560f)
        ), 600f)
        assertEquals(12_000L, parse(layout).single().amount.cents)
        assertTrue(layout, layout.contains("valor\tvalor estrangeiro"))
    }
}
