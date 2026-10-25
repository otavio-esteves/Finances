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
