package br.com.otavioesteves.finances.data.statement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.domain.model.StatementKind
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

/** Complete synthetic PDFs exercise Android extraction as well as the bank section adapters. */
@RunWith(AndroidJUnit4::class)
class BankCardPdfIntegrationTest {
    private data class Cell(val x: Float, val y: Float, val text: String)

    private fun pdf(cells: List<Cell>): ByteArray = pdfPages(listOf(cells))

    private fun pdfPages(pages: List<List<Cell>>): ByteArray {
        PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        return ByteArrayOutputStream().use { output ->
            PDDocument().use { document ->
                pages.forEach { cells ->
                    val page = PDPage()
                    document.addPage(page)
                    PDPageContentStream(document, page).use { stream ->
                        cells.forEach { cell ->
                            stream.beginText()
                            stream.setFont(PDType1Font.HELVETICA, 9f)
                            stream.newLineAtOffset(cell.x, cell.y)
                            stream.showText(cell.text)
                            stream.endText()
                        }
                    }
                }
                document.save(output)
            }
            output.toByteArray()
        }
    }

    @Test fun santanderPageContinuationAndMerchantNamedTotalSurviveExtraction() = runBlocking {
        val content = pdfPages(listOf(
            listOf(Cell(40f, 740f, "Santander"), Cell(40f, 720f, "Histórico das despesas"),
                Cell(40f, 690f, "01/11/2026 Mercado 10,00"),
                Cell(40f, 100f, "Central de atendimento 0800 123 4567"),
                Cell(40f, 80f, "Informação de rodapé 999,00")),
            listOf(Cell(40f, 740f, "Data Descrição Valor"),
                Cell(40f, 710f, "02/11/2026 Total 50,00"),
                Cell(40f, 680f, "03/11/2026 Farmácia 20,00"),
                Cell(40f, 640f, "Resumo da fatura"), Cell(40f, 620f, "Total da fatura 80,00"))
        ))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entries = PdfStatementParser(PdfBoxTextExtractor(context)).parse("a.pdf", content, StatementKind.CARD_BILL)
        assertEquals(listOf("Mercado", "Total", "Farmácia"), entries.map { it.description })
        assertEquals(listOf(1000L, 5000L, 2000L), entries.map { it.amount.cents })
    }

    @Test fun extractsInterBradescoAndCaixaDocuments() = runBlocking {
        val cases = listOf(
            listOf("Banco Inter", "Resumo da fatura R$ 20,00", "Despesas da fatura",
                "10 de jan. 2026 Mercado - R$ 20,00", "11 de jan. 2026 PAGTO DEBITO AUTOMATICO - + R$ 50,00",
                "Limite de crédito total R$ 1.000,00"),
            listOf("Bradesco Cartões", "Lançamentos", "10/01 Mercado", "20,00",
                "11/01 PAGTO ANTECIPADO PIX 50,00-", "Total da fatura em real 20,00"),
            listOf("Cartões CAIXA", "Demonstrativo", "10/01 Mercado 20,00 D",
                "11/01 OBRIGADO PELO PAGAMENTO 50,00 C", "Total 20,00 D", "Programa de Pontos")
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (lines in cases) {
            val content = pdf(lines.mapIndexed { index, line -> Cell(40f, 740f - index * 20f, line) })
            val entries = PdfStatementParser(PdfBoxTextExtractor(context)).parse("a.pdf", content,
                StatementKind.CARD_BILL, referenceYear = 2026)
            assertEquals(lines.first(), listOf(2_000L, 5_000L), entries.map { it.amount.cents })
            assertEquals(listOf(TransactionType.EXPENSE, TransactionType.TRANSFER), entries.map { it.type })
        }
    }

    @Test fun extractsSantanderBrlAndUsdColumnsWithoutAddingForeignAmount() = runBlocking {
        val content = pdf(listOf(
            Cell(40f, 740f, "Santander"), Cell(40f, 720f, "Fatura de janeiro/2026"),
            Cell(40f, 700f, "Detalhamento da Fatura"),
            Cell(40f, 680f, "Data"), Cell(130f, 680f, "Descrição"), Cell(370f, 680f, "R$"), Cell(470f, 680f, "US$"),
            Cell(40f, 660f, "10/01"), Cell(130f, 660f, "Compra exterior"), Cell(350f, 660f, "120,00"), Cell(455f, 660f, "20,00"),
            Cell(40f, 620f, "Resumo da Fatura"), Cell(40f, 600f, "Saldo anterior R$ 500,00")
        ))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entries = PdfStatementParser(PdfBoxTextExtractor(context)).parse("a.pdf", content, StatementKind.CARD_BILL)
        assertEquals(12_000L, entries.single().amount.cents)
    }

    @Test fun extractsBbIndependentColumnsWithOnlyOnePaymentOnTheLeft() = runBlocking {
        val cells = mutableListOf(Cell(40f, 740f, "Banco do Brasil"), Cell(40f, 720f, "Detalhamento da sua fatura"))
        for (x in listOf(40f, 330f)) {
            cells += listOf(Cell(x, 700f, "Data"), Cell(x + 45f, 700f, "Transações"), Cell(x + 200f, 700f, "Valor"))
        }
        cells += listOf(
            Cell(40f, 650f, "03.01"), Cell(85f, 650f, "PGTO DEBITO CONTA"), Cell(240f, 650f, "-50,00"),
            Cell(330f, 680f, "10.01"), Cell(375f, 680f, "Mercado"), Cell(530f, 680f, "20,00"),
            Cell(330f, 660f, "11.01"), Cell(375f, 660f, "Farmácia"), Cell(530f, 660f, "30,00")
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entries = PdfStatementParser(PdfBoxTextExtractor(context)).parse("a.pdf", pdf(cells), StatementKind.CARD_BILL, referenceYear = 2026)
        assertEquals(listOf(5_000L, 2_000L, 3_000L), entries.map { it.amount.cents })
        assertEquals(TransactionType.TRANSFER, entries.first().type)
    }
}
