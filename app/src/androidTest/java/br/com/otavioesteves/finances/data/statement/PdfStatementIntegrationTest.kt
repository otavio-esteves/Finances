package br.com.otavioesteves.finances.data.statement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.domain.model.TransactionType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PdfStatementIntegrationTest {
    @Test
    fun actualPdfPreservesContinuationBelowACompleteTransactionInTextAndTableLayouts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        for (positioned in listOf(false, true)) {
            val bytes = ByteArrayOutputStream().use { output ->
                PDDocument().use { document ->
                    val page = PDPage()
                    document.addPage(page)
                    PDPageContentStream(document, page).use { stream ->
                        fun text(value: String, x: Float, y: Float) {
                            stream.beginText()
                            stream.setFont(PDType1Font.HELVETICA, 12f)
                            stream.newLineAtOffset(x, y)
                            stream.showText(value)
                            stream.endText()
                        }
                        text("Fatura", 50f, 740f)
                        if (positioned) {
                            text("Data", 50f, 710f); text("Descricao", 170f, 710f); text("Valor", 450f, 710f)
                            text("01/11/2026", 50f, 680f); text("PAGAMENTO", 170f, 680f); text("10,00", 450f, 680f)
                            text("LOJA A", 170f, 660f)
                            text("02/11/2026", 50f, 630f); text("MERCADO", 170f, 630f); text("20,00", 450f, 630f)
                        } else {
                            text("01/11/2026 PAGAMENTO 10,00", 50f, 680f)
                            text("LOJA A", 50f, 660f)
                            text("02/11/2026 MERCADO 20,00", 50f, 630f)
                        }
                    }
                    document.save(output)
                }
                output.toByteArray()
            }
            val entries = PdfStatementParser(PdfBoxTextExtractor(context)).parse("fatura.pdf", bytes)
            assertEquals(listOf("PAGAMENTO LOJA A", "MERCADO"), entries.map { it.description })
            assertEquals(listOf(1000L, 2000L), entries.map { it.amount.cents })
        }
    }

    @Test
    fun extractsTextFromPdfAndParsesTransaction() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        val bytes = ByteArrayOutputStream().use { output ->
            PDDocument().use { document ->
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(50f, 700f)
                    stream.showText("Fatura")
                    stream.newLineAtOffset(0f, -20f)
                    stream.showText("10/01/2026 Mercado Central 150,75")
                    stream.endText()
                }
                document.save(output)
            }
            output.toByteArray()
        }

        val entries = PdfStatementParser(PdfBoxTextExtractor(context)).parse("fatura.pdf", bytes)

        assertEquals(1, entries.size)
        assertEquals("Mercado Central", entries.single().description)
        assertEquals(15_075L, entries.single().amount.cents)
        assertEquals(TransactionType.EXPENSE, entries.single().type)
    }

    @Test
    fun extractsShortDatesAcrossYearsAndIgnoresLabelledRunningBalances() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        val bytes = ByteArrayOutputStream().use { output ->
            PDDocument().use { document ->
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(50f, 700f)
                    listOf(
                        "Extrato da conta",
                        "Periodo: 01/12/2025 a 31/01/2026",
                        "Data Historico Valor Saldo",
                        "20/12 Mercado Central 150,75 D 2.000,00 C",
                        "10/01",
                        "Salario 3.000,00 C 5.000,00 C"
                    ).forEach { line ->
                        stream.showText(line)
                        stream.newLineAtOffset(0f, -20f)
                    }
                    stream.endText()
                }
                document.save(output)
            }
            output.toByteArray()
        }

        val entries = PdfStatementParser(PdfBoxTextExtractor(context)).parse("extrato.pdf", bytes)

        assertEquals(2, entries.size)
        assertEquals(listOf(LocalDate.of(2025, 12, 20), LocalDate.of(2026, 1, 10)), entries.map { it.date })
        assertEquals(listOf(15_075L, 300_000L), entries.map { it.amount.cents })
        assertEquals(listOf(TransactionType.EXPENSE, TransactionType.INCOME), entries.map { it.type })
    }

}
