package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.TransactionType
import org.junit.Assert.*
import org.junit.Test

class PdfLayoutCurrencyTest {
    private val parser = PdfStatementParser { "" }
    private fun w(text: String, left: Float, right: Float, top: Float = 10f) =
        PdfWord(text, left, top, right, top + 10f)
    private fun header() = listOf(w("Data", 10f, 35f), w("Descrição", 100f, 180f))
    private fun row() = listOf(w("01/11/2026", 10f, 90f, 40f), w("Compra", 100f, 160f, 40f))
    private fun reference() = listOf(w("ID", 240f, 250f), w("da", 255f, 270f), w("operação", 275f, 340f))

    private fun label(name: String, unit: String, style: Int, left: Float = 400f): List<PdfWord> = when (style) {
        0 -> listOf(w("$name($unit)", left, left + 80))
        1 -> listOf(w("$name ($unit)", left, left + 80))
        2 -> listOf(w(name, left, left + 30), w("($unit)", left + 35, left + 80))
        else -> listOf(w(name, left, left + 30), w("(", left + 35, left + 38),
            w(unit, left + 40, left + 75), w(")", left + 77, left + 80))
    }

    @Test fun foreignValueUnitsSurviveEveryHeaderTokenization() {
        for (unit in listOf("USD", "EUR", "CHF", "US$", "€", "XYZ", "dólares", "libras", "ienes")) {
            for (style in 0..3) {
                assertThrows("$unit, style=$style", IllegalArgumentException::class.java) {
                    val text = PdfLayoutReconstructor().reconstruct(header() + label("Valor", unit, style) + row() +
                        w("-100,00", 400f, 480f, 40f), 600f)
                    parser.parseText(text)
                }
            }
        }
    }

    @Test fun debitCreditAndBalanceCannotLoseForeignCurrencyQualifiers() {
        for (unit in listOf("USD", "EUR", "XYZ", "dólares")) for (style in 0..3) {
            assertThrows(IllegalArgumentException::class.java) {
                val text = PdfLayoutReconstructor().reconstruct(header() + label("Débito", unit, style, 300f) +
                    label("Crédito", unit, style, 450f) + row() + w("100,00", 325f, 380f, 40f), 600f)
                parser.parseText(text)
            }
            assertThrows(IllegalArgumentException::class.java) {
                val text = PdfLayoutReconstructor().reconstruct(header() + label("Valor", "BRL", style, 350f) +
                    label("Saldo", unit, style, 480f) + row() +
                    listOf(w("-100,00", 350f, 430f, 40f), w("200,00", 480f, 560f, 40f)), 600f)
                parser.parseText(text)
            }
        }
    }

    @Test fun centeredStatementsUseTheSameCurrencyValidationAsOrdinaryTables() {
        for (unit in listOf("USD", "EUR", "XYZ", "dólares")) for (style in 0..3) {
            assertThrows(IllegalArgumentException::class.java) {
                val text = PdfLayoutReconstructor().reconstruct(header() + reference() + label("Valor", unit, style) +
                    w("Saldo", 510f, 550f) + row() +
                    listOf(w("-100,00", 400f, 480f, 40f), w("200,00", 510f, 550f, 40f)), 600f)
                parser.parseText(text)
            }
        }
    }

    @Test fun explicitBrlColumnsRemainValidInAllLayoutsEvenWithAForeignCover() {
        for (unit in listOf("BRL", "R$", "reais", "Real Brasileiro")) for (style in 0..3) {
            for (centered in listOf(false, true)) {
                val words = header() + (if (centered) reference() else emptyList()) + label("Valor", unit, style) +
                    (if (centered) listOf(w("Saldo", 510f, 550f)) else emptyList()) + row() +
                    w("-100,00", 400f, 480f, 40f) +
                    (if (centered) listOf(w("200,00", 510f, 550f, 40f)) else emptyList())
                val entry = parser.parseText("Moeda: USD\n" + PdfLayoutReconstructor().reconstruct(words, 600f)).single()
                assertEquals("Compra", entry.description)
                assertEquals(10_000L, entry.amount.cents)
                assertEquals(TransactionType.EXPENSE, entry.type)
            }
            val debitCredit = header() + label("Débito", unit, style, 300f) + label("Crédito", unit, style, 450f) +
                row() + w("100,00", 325f, 380f, 40f)
            val entry = parser.parseText("Moeda: USD\n" + PdfLayoutReconstructor().reconstruct(debitCredit, 600f)).single()
            assertEquals(10_000L, entry.amount.cents)
            assertEquals(TransactionType.EXPENSE, entry.type)
        }
    }

    @Test fun convertedBrlValuesAndRowCurrencySurviveCenteredAndOrdinaryHeaders() {
        for (centered in listOf(false, true)) {
            val words = header() + (if (centered) reference() else emptyList()) + listOf(
                w("Valor", 350f, 375f), w("(BRL)", 378f, 405f),
                w("Valor", 430f, 455f), w("(USD)", 458f, 490f),
                w("Saldo", 510f, 545f), w("Currency", 550f, 598f)
            ) + row() + listOf(w("-500,00", 350f, 405f, 40f), w("100,00", 430f, 490f, 40f),
                w("200,00", 510f, 545f, 40f), w("USD", 550f, 580f, 40f))
            val entry = parser.parseText("Moeda: USD\n" + PdfLayoutReconstructor().reconstruct(words, 612f)).single()
            assertEquals(50_000L, entry.amount.cents)
            assertEquals("Compra", entry.description)
        }
    }

    @Test fun aColumnCannotDeclareBothBrlAndForeignCurrency() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfLayoutReconstructor().reconstruct(header() + listOf(
                w("Valor", 350f, 380f), w("(BRL", 385f, 415f), w("USD)", 420f, 455f)
            ), 600f)
        }
    }

    @Test fun aCurrencyColumnQualifierCannotBeDiscardedByEitherLayout() {
        for (centered in listOf(false, true)) {
            assertThrows(IllegalArgumentException::class.java) {
                val words = header() + (if (centered) reference() else emptyList()) + listOf(
                    w("Valor", 350f, 400f), w("Saldo", 450f, 490f),
                    w("Currency", 510f, 550f), w("(USD)", 555f, 590f)
                ) + row() + listOf(w("-100,00", 350f, 400f, 40f), w("200,00", 450f, 490f, 40f))
                parser.parseText(PdfLayoutReconstructor().reconstruct(words, 600f))
            }
        }
    }

    @Test fun centeredRowCurrencyIsPreservedForValidationAndExplicitBrlRowsRemainValid() {
        for (currency in listOf("USD", "BRL")) {
            val words = header() + reference() + listOf(w("Valor", 350f, 400f), w("Saldo", 450f, 490f),
                w("Currency", 510f, 590f)) + row() + listOf(w("-100,00", 350f, 400f, 40f),
                w("200,00", 450f, 490f, 40f), w(currency, 510f, 550f, 40f))
            val text = "Moeda: USD\n" + PdfLayoutReconstructor().reconstruct(words, 600f)
            if (currency == "BRL") assertEquals(10_000L, parser.parseText(text).single().amount.cents)
            else assertThrows(IllegalArgumentException::class.java) { parser.parseText(text) }
        }
    }

    @Test fun ordinaryHeadersPreserveTheHistoricalDescriptionComposition() {
        val words = header() + listOf(w("Favorecido", 220f, 285f), w("CPF/CNPJ", 300f, 365f),
            w("Valor", 430f, 480f)) + row() + listOf(w("Pessoa", 220f, 270f, 40f),
            w("***000***", 300f, 360f, 40f), w("-100,00", 430f, 480f, 40f))
        val entry = parser.parseText(PdfLayoutReconstructor().reconstruct(words, 600f)).single()
        assertEquals("Compra Pessoa ***000***", entry.description)
    }

    @Test fun excessiveHeaderQualifiersAreRejectedBeforeBuildingUnboundedLabels() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfLayoutReconstructor().reconstruct(header() + w("Valor", 350f, 380f) +
                (1..260).map { w("qualificador", 380f + it, 390f + it) }, 1000f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfLayoutReconstructor().reconstruct(header() + w("Valor", 350f, 380f) +
                listOf(w("a".repeat(600), 385f, 410f), w("b".repeat(600), 415f, 440f)), 600f)
        }
    }
}
