package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.StatementKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Variations of the same financial meaning must keep the same safety decision. */
class ImportSafetyContractTest {
    private val pdf = PdfStatementParser { "" }

    @Test fun foreignDeclarationsRequireBrlRegardlessOfLineBreaksAndLabels() {
        for (label in listOf("Moeda:", "Currency =", "Moeda do documento:", "Valores expressos em", "Amounts in")) {
            for (separator in listOf(" ", "\t", "\n", "\r\n\r\n")) {
                for (currency in listOf("USD", "EUR", "XYZ", "Dólares americanos")) {
                    for (kind in StatementKind.entries) {
                        val declaration = "$label$separator$currency"
                        val amount = if (kind == StatementKind.CARD_BILL) "100,00" else "-100,00"
                        assertThrows("$declaration / $kind", IllegalArgumentException::class.java) {
                            pdf.parseText("$declaration\n01/11/2026 Compra $amount", kind)
                        }
                        val converted = pdf.parseText("$declaration\n01/11/2026 Compra R$ $amount", kind).single()
                        assertEquals("$declaration / $kind", 10_000L, converted.amount.cents)
                        assertEquals("Compra", converted.description)
                    }
                }
            }
        }
    }

    @Test fun splitBrlDeclarationsAreRemovedByPositionWithoutErasingMerchantText() {
        for (currency in listOf("BRL", "R$", "Reais", "BRL (Real Brasileiro)")) {
            val entries = pdf.parseText("Fatura\nMoeda:\n$currency\n01/11/2026 Compra 100,00\n" +
                "Moeda:\n$currency\nCurrency Café\n02/11/2026 Outra 20,00\nMoeda Arte")
            assertEquals(listOf("Compra Currency Café", "Outra Moeda Arte"), entries.map { it.description })
            assertEquals(listOf(10_000L, 2_000L), entries.map { it.amount.cents })
        }
    }

    @Test fun missingOrConflictingCurrencyDeclarationsCannotAuthorizeUnmarkedAmounts() {
        for (declaration in listOf("Moeda:", "Moeda:\n\n", "Moeda:\nBRL\nCurrency:\nUSD")) {
            assertThrows(declaration, IllegalArgumentException::class.java) {
                pdf.parseText("Fatura\n$declaration\n01/11/2026 Compra 100,00")
            }
        }
    }

    @Test fun unsupportedAmountUnitsCannotFallBackToReais() = runBlocking {
        val csv = CsvOfxStatementParser()
        for (unit in listOf("centavos", "CENTAVO", "cents", "minor units", "mil reais", "milhões de reais", "%")) {
            for (label in listOf("Valor em $unit", "Valor ($unit)", "Amount_$unit")) {
                for (kind in StatementKind.entries) {
                    assertThrows("$label / $kind", IllegalArgumentException::class.java) {
                        runBlocking { csv.parse("a.csv", "Data;Descrição;$label\n01/11/2026;Compra;1234".toByteArray(), kind) }
                    }
                }
            }
        }
        for (label in listOf("Valor", "Valor em reais", "Amount (BRL)")) {
            assertEquals(1234L, csv.parse("a.csv", "Data;Descrição;$label\n01/11/2026;Compra;12,34".toByteArray()).single().amount.cents)
        }
    }

    @Test fun pdfUnitDeclarationsCannotBeOverriddenByAnUnscaledBrlMarker() {
        for (unit in listOf("centavos", "mil reais", "milhões de reais", "%")) {
            for (declaration in listOf("Valores expressos em $unit", "Valores expressos em\n$unit", "Data Descrição Valor em $unit")) {
                assertThrows(declaration, IllegalArgumentException::class.java) {
                    pdf.parseText("Fatura\n$declaration\n01/11/2026 Compra R$ 100,00")
                }
            }
        }
    }

    @Test fun anIncompleteTransactionCannotCrossAnySummaryBoundary() {
        for (summary in listOf("Total da fatura", "Saldo", "Subtotal", "Valor total")) {
            for (dated in listOf(false, true)) {
                for (positioned in listOf(false, true)) {
                    val boundary = if (positioned) "${if (dated) "02/11/2026" else ""}\t$summary\t100,00"
                        else "$summary 100,00"
                    assertThrows(boundary, IllegalArgumentException::class.java) {
                        pdf.parseText("Fatura\nData\tDescrição\tValor\n01/11/2026\tCompra\t\n" +
                            "$boundary\n\tComplemento\t50,00")
                    }
                }
            }
        }
    }

    @Test fun completedRowsMayEndAtASummaryAndWrappedRowsMayCrossRepeatedHeaders() {
        val header = "Data\tDescrição\tValor"
        val entries = pdf.parseText("Fatura\n$header\n01/11/2026\tCompra\t\nPágina 1 de 2\n$header\n" +
            "\tComplemento\t50,00\n\tTotal da fatura\t50,00\n02/11/2026\tOutra\t20,00")
        assertEquals(listOf("Compra Complemento", "Outra"), entries.map { it.description })
        assertEquals(listOf(5000L, 2000L), entries.map { it.amount.cents })
    }
}
