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
