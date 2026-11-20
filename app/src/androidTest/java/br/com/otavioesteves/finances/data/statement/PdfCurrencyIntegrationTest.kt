package br.com.otavioesteves.finances.data.statement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.domain.model.StatementKind
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class PdfCurrencyIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun splitCurrencyDeclarationsSurviveExtractionAndBankCoverRemoval() {
        for (label in listOf("Moeda:", "Valores expressos em")) {
            for (bankCover in listOf(false, true)) for (converted in listOf(false, true)) {
                val bytes = pdf {
                    text("Fatura", 50f, 760f)
                    text(label, 50f, 735f); text("USD", 50f, 715f)
                    if (bankCover) {
                        text("Santander", 50f, 695f)
                        text("Detalhamento da Fatura", 50f, 675f)
                    }
                    text("Data", 50f, 650f); text("Descricao", 170f, 650f)
                    text(if (converted) "Valor (BRL)" else "Valor", 350f, 650f)
                    text("01/11/2026", 50f, 620f); text("Compra", 170f, 620f); text("100,00", 350f, 620f)
                }
                if (converted) {
                    val entry = runBlocking { PdfStatementParser(PdfBoxTextExtractor(context)).parse("a.pdf", bytes) }.single()
                    assertEquals("Compra", entry.description)
                    assertEquals(10_000L, entry.amount.cents)
                } else assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { PdfStatementParser(PdfBoxTextExtractor(context)).parse("a.pdf", bytes) }
                }
            }
        }
    }

    @Test fun splitBrlMetadataDoesNotBecomePartOfAPurchaseDescription() = runBlocking {
        val bytes = pdf {
            text("Fatura", 50f, 760f)
            text("01/11/2026 Compra 100,00", 50f, 700f)
            text("Moeda:", 50f, 675f); text("BRL", 50f, 650f)
            text("02/11/2026 Outra 20,00", 50f, 625f)
        }
        val entries = PdfStatementParser(PdfBoxTextExtractor(context)).parse("a.pdf", bytes)
        assertEquals(listOf("Compra", "Outra"), entries.map { it.description })
        assertEquals(listOf(10_000L, 2_000L), entries.map { it.amount.cents })
    }

    @Test fun aSummaryCannotCompleteAnUnfinishedPurchaseAfterPdfExtraction() {
        for (summary in listOf("Total da fatura", "Saldo", "Subtotal")) {
            val bytes = pdf {
                text("Fatura", 50f, 760f)
                text("Data", 50f, 710f); text("Descricao", 170f, 710f); text("Valor", 450f, 710f)
                text("01/11/2026", 50f, 680f); text("Compra", 170f, 680f)
                text(summary, 170f, 650f); text("100,00", 450f, 650f)
                text("Complemento", 170f, 620f); text("50,00", 450f, 620f)
            }
            assertThrows(summary, IllegalArgumentException::class.java) {
                runBlocking { PdfStatementParser(PdfBoxTextExtractor(context)).parse("a.pdf", bytes) }
            }
        }
    }

    private enum class Layout { VALUE, DEBIT_CREDIT, CENTERED, CAIXA }

    private fun PDPageContentStream.currencyTable(layout: Layout, unit: String, y: Float = 690f) {
        text(if (layout == Layout.CAIXA) "Lancamentos" else "Data", 10f, y)
        text(if (layout == Layout.CAIXA) "Historico/Complemento" else "Descricao", 100f, y)
        text("01/11/2026", 10f, y - 30f); text("Compra", 100f, y - 30f)
        when (layout) {
            Layout.DEBIT_CREDIT -> {
                text("Debito ($unit)", 300f, y); text("Credito ($unit)", 450f, y)
                text("100,00", 300f, y - 30f)
            }
            else -> {
                if (layout == Layout.CENTERED) text("ID da operacao", 260f, y)
                if (layout == Layout.CAIXA) text("Nr.Doc.", 260f, y)
                val valueLabel = "Valor ($unit)"
                assertTrue("Cabeçalho sobreposto: $valueLabel", 350f + PDType1Font.HELVETICA.getStringWidth(valueLabel) * 12f / 1000f < 550f)
                text(valueLabel, 350f, y); text("-100,00", 350f, y - 30f)
                if (layout != Layout.VALUE) { text("Saldo", 550f, y); text("200,00", 550f, y - 30f) }
            }
        }
    }

    @Test fun parenthesizedForeignUnitsAreRejectedAfterActualPdfWordExtractionInEveryLayout() {
        for (layout in Layout.entries) for (unit in listOf("USD", "EUR", "XYZ", "dolares")) {
            val bytes = pdf { currencyTable(layout, unit) }
            val error = assertThrows("$layout $unit", IllegalArgumentException::class.java) {
                runBlocking { PdfStatementParser(PdfBoxTextExtractor(context)).parse("foreign.pdf", bytes) }
            }
            assertTrue("$layout $unit: ${error.message}", error.message.orEmpty().contains("moeda", ignoreCase = true))
        }
    }

    @Test fun parenthesizedBrlUnitsRemainExplicitAfterActualPdfWordExtractionInEveryLayout() = runBlocking {
        for (layout in Layout.entries) for (unit in listOf("BRL", "R$", "Real Brasileiro")) {
            val bytes = pdf { text("Moeda: USD", 10f, 760f); currencyTable(layout, unit) }
            val entry = PdfStatementParser(PdfBoxTextExtractor(context)).parse("converted.pdf", bytes).single()
            assertEquals("$layout $unit", "Compra", entry.description)
            assertEquals("$layout $unit", 10_000L, entry.amount.cents)
            assertEquals(br.com.otavioesteves.finances.domain.model.TransactionType.EXPENSE, entry.type)
        }
    }

    @Test fun foreignHeaderOnALaterPageRejectsTheEntirePdf() {
        PDFBoxResourceLoader.init(context)
        val bytes = ByteArrayOutputStream().use { output ->
            PDDocument().use { document ->
                for (unit in listOf("BRL", "USD")) {
                    val page = PDPage(); document.addPage(page)
                    PDPageContentStream(document, page).use { it.currencyTable(Layout.VALUE, unit) }
                }
                document.save(output)
            }
            output.toByteArray()
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { PdfStatementParser(PdfBoxTextExtractor(context)).parse("mixed.pdf", bytes) }
        }
    }

    @Test fun centeredConvertedValueRetainsBothCurrenciesAndImportsOnlyBrl() = runBlocking {
        val bytes = pdf {
            text("Moeda: USD", 10f, 760f)
            text("Data", 10f, 690f); text("Descricao", 100f, 690f); text("ID da operacao", 240f, 690f)
            text("Valor (BRL)", 350f, 690f); text("Valor (USD)", 430f, 690f)
            text("Saldo", 510f, 690f); text("Currency", 550f, 690f)
            text("01/11/2026", 10f, 660f); text("Compra", 100f, 660f)
            text("-500,00", 350f, 660f); text("100,00", 430f, 660f)
            text("200,00", 510f, 660f); text("USD", 550f, 660f)
        }
        val entry = PdfStatementParser(PdfBoxTextExtractor(context)).parse("converted.pdf", bytes).single()
        assertEquals(50_000L, entry.amount.cents)
        assertEquals("Compra", entry.description)
    }

    @Test fun foreignCurrencyNamesCannotLoseTheirUnitDuringTextExtraction() {
        for (unit in listOf("dólares", "euros", "libras", "ienes")) for (positioned in listOf(false, true)) {
            val bytes = pdf {
                text("Fatura", 50f, 760f)
                if (positioned) {
                    text("Data", 50f, 690f); text("Descricao", 170f, 690f); text("Valor em $unit", 350f, 690f)
                    text("01/11/2026", 50f, 660f); text("Compra", 170f, 660f); text("100,00", 350f, 660f)
                } else {
                    text("Data Descricao Valor em $unit", 50f, 690f)
                    text("01/11/2026 Compra 100,00", 50f, 660f)
                }
            }
            assertThrows(unit, IllegalArgumentException::class.java) {
                runBlocking { PdfStatementParser(PdfBoxTextExtractor(context)).parse("a.pdf", bytes, StatementKind.CARD_BILL) }
            }
        }
    }

    @Test fun globalForeignCurrencyIsRejectedInPlainAndPositionedPdfsIncludingPrunedBankCovers() {
        for (positioned in listOf(false, true)) for (bankCover in listOf(false, true)) {
            val bytes = pdf {
                text("Fatura", 50f, 760f)
                text("Moeda: USD", 50f, 735f)
                if (bankCover) {
                    text("Santander", 50f, 710f)
                    text("Detalhamento da Fatura", 50f, 690f)
                }
                if (positioned) {
                    text("Data", 50f, 660f); text("Descricao", 170f, 660f); text("Valor", 350f, 660f)
                    text("01/11/2026", 50f, 630f); text("Compra", 170f, 630f); text("100,00", 350f, 630f)
                } else text("01/11/2026 Compra 100,00", 50f, 630f)
            }
            val error = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { PdfStatementParser(PdfBoxTextExtractor(context)).parse("a.pdf", bytes, StatementKind.CARD_BILL) }
            }
            assertTrue(error.message.orEmpty().contains("moeda", ignoreCase = true))
        }
    }

    @Test fun positionedConversionImportsOnlyTheExplicitBrlValue() = runBlocking {
        val bytes = pdf {
            text("Fatura", 50f, 760f); text("Moeda: USD", 50f, 735f)
            text("Data", 50f, 690f); text("Descricao", 170f, 690f)
            text("Valor (R$)", 350f, 690f); text("US$", 450f, 690f); text("Currency", 530f, 690f)
            text("01/11/2026", 50f, 660f); text("Compra", 170f, 660f)
            text("100,00", 350f, 660f); text("20,00", 450f, 660f); text("USD", 530f, 660f)
        }
        val entry = PdfStatementParser(PdfBoxTextExtractor(context)).parse("a.pdf", bytes, StatementKind.CARD_BILL).single()
        assertEquals("Compra", entry.description)
        assertEquals(10_000L, entry.amount.cents)
    }

    @Test fun foreignCurrencyAliasAndConflictingCurrencyColumnsCannotBeLostDuringExtraction() {
        for (duplicate in listOf(false, true)) {
            val bytes = pdf {
                text("Fatura", 50f, 760f)
                text("Data", 50f, 690f); text("Descricao", 170f, 690f); text("Valor", 350f, 690f)
                if (duplicate) text("Moeda", 450f, 690f)
                text("Currency", 530f, 690f)
                text("01/11/2026", 50f, 660f); text("Compra", 170f, 660f); text("100,00", 350f, 660f)
                if (duplicate) text("BRL", 450f, 660f)
                text("USD", 530f, 660f)
            }
            val error = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { PdfStatementParser(PdfBoxTextExtractor(context)).parse("a.pdf", bytes, StatementKind.CARD_BILL) }
            }
            assertTrue(error.message.orEmpty().contains("moeda", ignoreCase = true))
        }
    }

    private fun pdf(draw: PDPageContentStream.() -> Unit): ByteArray {
        PDFBoxResourceLoader.init(context)
        return ByteArrayOutputStream().use { output ->
            PDDocument().use { document ->
                val page = PDPage(); document.addPage(page)
                PDPageContentStream(document, page).use { it.draw() }
                document.save(output)
            }
            output.toByteArray()
        }
    }

    private fun PDPageContentStream.text(value: String, x: Float, y: Float) {
        beginText(); setFont(PDType1Font.HELVETICA, 12f); newLineAtOffset(x, y); showText(value); endText()
    }
}
