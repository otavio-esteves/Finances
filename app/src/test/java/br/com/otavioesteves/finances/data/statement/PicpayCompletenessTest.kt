package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.StatementKind
import org.junit.Assert.*
import org.junit.Test
import java.util.Currency

class PicpayCompletenessTest {
    private val parser = PdfStatementParser { "" }
    private val cover = "PicPay\nFechamento: 29/01/2025\nSimulação de parcelamento 900,00\n\u000c\n"
    private val national = "Transações Nacionais\nData Estabelecimento Valor (R$)\n02/01 Mercado 20,00\nSubtotal dos lançamentos 20,00\n"
    private fun parse(body: String) = parser.parseText(cover + body, StatementKind.CARD_BILL)

    @Test fun continuationPagesWithoutRepeatedSectionTitleKeepEveryPurchase() {
        val result = parse(national + "\u000c\nPicPay\nData Estabelecimento Valor (R$)\n03/01 Farmácia 30,00")
        assertEquals(listOf("Mercado", "Farmácia"), result.map { it.description })
        assertEquals(5000L, result.sumOf { it.amount.cents })
    }

    @Test fun internationalPagesAndTheirContinuationsKeepBrlPurchases() {
        val result = parse(national + "\u000c\nTransações Internacionais\n" +
            "data\tdescricao\tvalor\tvalor estrangeiro\n03/01\tLoja exterior\t30,00\t5,00\n" +
            "\u000c\n04/01\tOutra loja\t40,00\t7,00")
        assertEquals(listOf(2000L, 3000L, 4000L), result.map { it.amount.cents })
    }

    @Test fun internationalOnlyBillDoesNotDependOnNationalHeadingOrSubtotal() {
        val result = parse("Transações Internacionais\nData Estabelecimento Valor (R$)\n03/01 Loja exterior 30,00")
        assertEquals(3000L, result.single().amount.cents)
    }

    @Test fun foreignAmountWithoutUnambiguousBrlColumnRejectsTheWholeBill() {
        val currencies = Currency.getAvailableCurrencies().map { it.currencyCode }.filter { it != "BRL" } +
            listOf("US$", "CA$", "A$", "$", "€", "£", "¥", "₹", "₽", "₩", "₺", "₴", "฿")
        for (currency in currencies) {
            for (amount in listOf("$currency 30,00", "30,00 $currency", "30,00$currency")) {
                assertThrows(amount, IllegalArgumentException::class.java) {
                    parse(national + "\u000c\nTransações Internacionais\n03/01 Compra exterior $amount")
                }
            }
        }
    }

    @Test fun brlSymbolsArePreservedWhileForeignCurrencyNamesStayInMerchantNames() {
        val result = parse(national + "\u000c\n03/01 CHF Store R$ 30,00\n04/01 Fleur R$ 40,00")
        assertEquals(listOf("Mercado", "CHF Store", "Fleur"), result.map { it.description })
        assertEquals(listOf(2000L, 3000L, 4000L), result.map { it.amount.cents })
    }

    @Test fun punctuatedSectionHeadingsKeepInternationalPagesBeforeNationalOnes() {
        for (suffix in listOf(":", ".", " - Continuação:", " (continuação)")) {
            val result = parse("Transações Internacionais$suffix\n03/01 Loja exterior 30,00\n" +
                "\u000c\nTransações Nacionais$suffix\n02/01 Mercado 20,00")
            assertEquals(suffix, listOf(3000L, 2000L), result.map { it.amount.cents })
        }
    }

    @Test fun unknownSectionHeadingCannotSilentlyDiscardAnEarlierMovementPage() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("Transações Internacionais (detalhamento)\n03/01 Loja exterior 30,00\n\u000c\n$national")
        }
    }

    @Test fun purchasesAboveTheFirstSectionHeadingArePreserved() {
        val result = parse("03/01 Loja acima do cabeçalho 30,00\n$national")
        assertEquals(listOf(3000L, 2000L), result.map { it.amount.cents })
    }

    @Test fun merchantNamesContainingCurrencyLettersAreNotCurrencyMarkers() {
        val result = parse(national + "\u000c\n03/01 Fleur 30,00\n04/01 USD Store 40,00")
        assertEquals(listOf("Mercado", "Fleur", "USD Store"), result.map { it.description })
    }

    @Test fun futureInstallmentsStayOutsideCurrentMovementsAcrossPages() {
        for (heading in listOf("Total parcelado - próximas faturas", "Total parcelado para as próximas faturas",
            "Parcelamentos das próximas faturas", "Lançamentos de faturas futuras", "Próxima fatura")) {
            val result = parse(national + "\u000c\n03/01 Farmácia 30,00\n" +
                "\u000c\n$heading\n10/02/2025 Oferta 900,00\n\u000c\n11/02/2025 Oferta 800,00")
            assertEquals(heading, listOf(2000L, 3000L), result.map { it.amount.cents })
        }
    }

    @Test fun futureSummaryOnCoverDoesNotTerminateMovementsThatHaveNotStarted() {
        val result = parser.parseText(
            "PicPay\nFechamento: 29/01/2025\nPróximas faturas\n10/02/2025 Oferta 900,00\n\u000c\n$national",
            StatementKind.CARD_BILL
        )
        assertEquals(listOf(2000L), result.map { it.amount.cents })
    }

    @Test fun futureSummaryBeforeTheFirstCurrentSectionOnTheSamePageIsNotAmbiguous() {
        val result = parser.parseText(
            "PicPay\nFechamento: 29/01/2025\nPróximas faturas\n10/02/2025 Oferta 900,00\n$national",
            StatementKind.CARD_BILL
        )
        assertEquals(listOf(2000L), result.map { it.amount.cents })
    }

    @Test fun purchasesBeforeAFutureSummaryCannotBeLostWhenACurrentHeadingFollows() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("03/01 Compra 30,00\nPróximas faturas\n10/02 Oferta 900,00\n$national")
        }
    }

    @Test fun unrecognizedFinancialRowsOnContinuationPageRejectTheWholeBill() {
        assertThrows(IllegalArgumentException::class.java) {
            parse(national + "\u000c\nAjuste sem data 30,00")
        }
    }

    @Test fun currentSectionAfterFutureBoundaryIsAmbiguousAndCannotBePartiallyImported() {
        assertThrows(IllegalArgumentException::class.java) {
            parse(national + "\u000c\nPróximas faturas\n10/02 Oferta 900,00\n" +
                "\u000c\nTransações Nacionais\n03/01 Farmácia 30,00")
        }
    }
}
