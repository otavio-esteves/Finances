package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.StatementKind
import org.junit.Assert.*
import org.junit.Test

class PdfCurrencyPolicyTest {
    private val parser = PdfStatementParser { "" }

    @Test fun merchantContinuationsAreNotMistakenForCurrencyMetadata() {
        for (name in listOf("Currency Café", "Moeda Arte")) {
            val entry = parser.parseText("Fatura\n01/11/2026 Loja R$ 100,00\n$name").single()
            assertEquals("Loja $name", entry.description)
        }
    }

    @Test fun globalForeignOrUnknownCurrenciesCannotBecomeUnmarkedBrlAmounts() {
        for (currency in listOf("USD", "EUR", "US$", "€", "XYZ", "Dólar", "Dólares americanos", "US Dollars")) {
            for (label in listOf("Moeda: $currency", "Currency: $currency", "Moeda $currency")) {
                for (body in listOf(
                    "01/11/2026 Compra 100,00",
                    "Data\tDescrição\tValor\n01/11/2026\tCompra\t100,00"
                )) assertThrows(IllegalArgumentException::class.java) {
                    parser.parseText("Fatura\n$label\n$body", StatementKind.CARD_BILL)
                }
            }
        }
    }

    @Test fun globalBrlDeclarationsRemainSupportedAndDoNotJoinMerchantDescriptions() {
        for (currency in listOf("BRL", "R$", "Real", "Reais", "BRL (Real Brasileiro)")) {
            val entries = parser.parseText("Fatura\nMoeda: $currency\n01/11/2026 Compra 100,00\n" +
                "Moeda: $currency\n02/11/2026 Outra 20,00")
            assertEquals(listOf("Compra", "Outra"), entries.map { it.description })
            assertEquals(listOf(10_000L, 2_000L), entries.map { it.amount.cents })
        }
    }

    @Test fun globalForeignCurrencyNeedsExplicitBrlOnEverySelectedAmountOrItsColumn() {
        val cover = "Fatura\nMoeda: USD\n"
        assertEquals(10_000L, parser.parseText(cover + "01/11/2026 Compra R$ 100,00").single().amount.cents)
        assertEquals(10_000L, parser.parseText(cover + "Data Descrição Valor (R$)\n01/11/2026 Compra 100,00").single().amount.cents)
        assertEquals(10_000L, parser.parseText(cover + "Data\tDescrição\tValor (BRL)\tValor estrangeiro\tMoeda\n" +
            "01/11/2026\tCompra\t100,00\t20,00\tUSD").single().amount.cents)
        assertEquals(10_000L, parser.parseText(cover + "Data\tDescrição\tValor\tCurrency\n" +
            "01/11/2026\tCompra\t100,00\tBRL").single().amount.cents)

        // A valid first row cannot authorize unmarked later rows or use the balance's currency.
        for (body in listOf(
            "01/11/2026 Compra R$ 100,00\n02/11/2026 Outra 20,00",
            "Data Descrição Valor Saldo (R$)\n01/11/2026 Compra 100,00 200,00",
            "Data\tDescrição\tValor\tSaldo (BRL)\n01/11/2026\tCompra\t100,00\t200,00"
        )) assertThrows(IllegalArgumentException::class.java) { parser.parseText(cover + body) }
    }

    @Test fun tableCurrencyAliasesAndRepeatedCurrencyColumnsFailClosed() {
        for (label in listOf("Moeda", "Currency")) {
            assertThrows(IllegalArgumentException::class.java) {
                parser.parseText("Fatura\nData\tDescrição\tValor\t$label\n01/11/2026\tCompra\t100,00\tUSD")
            }
        }
        for (second in listOf("Moeda", "Currency")) {
            assertThrows(IllegalArgumentException::class.java) {
                parser.parseText("Fatura\nData\tDescrição\tValor\tMoeda\t$second\n01/11/2026\tCompra\t100,00\tBRL\tUSD")
            }
        }
    }

    @Test fun declarationsAreCheckedBeforeBankSpecificCoversAreDiscarded() {
        for ((bank, section) in listOf("Santander" to "Detalhamento da Fatura", "Bradesco" to "Lancamentos", "Caixa" to "Demonstrativo")) {
            val original = "$bank\nFatura\nMoeda: USD\n$section\n01/11/2026 Compra 100,00"
            assertThrows(IllegalArgumentException::class.java) { parser.parseText(original, StatementKind.CARD_BILL) }
        }
        val inter = "Banco Inter\nFatura\nMoeda: USD\nDespesas da fatura\n01 de nov. 2026 Compra - R$ 100,00"
        assertEquals(10_000L, parser.parseText(inter, StatementKind.CARD_BILL).single().amount.cents)
    }

    @Test fun positionedHeadersRetainTheirExplicitBrlConversionAndCurrencyColumns() {
        fun w(text: String, x: Float, y: Float, right: Float = x + 40f) = PdfWord(text, x, y, right, y + 10f)
        for (labels in listOf(listOf("Valor", "R$"), listOf("Valor", "(R$)"), listOf("Valor(BRL)"), listOf("Valor", "em", "reais"))) {
            val words = listOf(w("Data", 20f, 10f), w("Descrição", 160f, 10f, 240f)) +
                labels.mapIndexed { index, label -> w(label, 360f + index * 50f, 10f) } +
                listOf(w("US$", 520f, 10f), w("Currency", 650f, 10f, 720f),
                    w("01/11/2026", 20f, 40f, 115f), w("Compra", 160f, 40f), w("100,00", 360f, 40f),
                    w("20,00", 520f, 40f), w("USD", 650f, 40f))
            val text = PdfLayoutReconstructor().reconstruct(words, 800f)
            val entry = parser.parseText("Fatura\nMoeda: USD\n$text").single()
            assertEquals(10_000L, entry.amount.cents)
            assertEquals("Compra", entry.description)
        }
    }
}
