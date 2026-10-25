package br.com.otavioesteves.finances.data.statement

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.model.StatementPasswordRequiredException
import br.com.otavioesteves.finances.domain.model.TransactionType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PdfOcrIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun scannedStatementWorksOfflineWithBundledPortugueseModel() = runBlocking {
        val bytes = document { scanPage(it, listOf("EXTRATO DA CONTA", "Ano: 2026", "10/01/2026 Mercado Central -150,75")) }
        val entries = PdfStatementParser(PdfBoxTextExtractor(context)).parse("digitalizado.pdf", bytes)
        assertEquals(15075L, entries.single().amount.cents)
        assertEquals(LocalDate.of(2026, 1, 10), entries.single().date)
        assertEquals(TransactionType.EXPENSE, entries.single().type)
        assertTrue(entries.single().extractedByOcr)
    }

    @Test
    fun mixedDocumentNeverDropsTheImageOnlyPage() = runBlocking {
        val bytes = document {
            textPage(it, listOf("Ano: 2026", "10/01/2026 Mercado -20,00"))
            scanPage(it, listOf("11/01/2026 Farmacia -30,00"))
            it.addPage(PDPage()) // A genuinely blank page is not an unread transaction page.
        }
        val entries = PdfStatementParser(PdfBoxTextExtractor(context)).parse("misto.pdf", bytes)
        assertEquals(listOf(2000L, 3000L), entries.map { it.amount.cents })
        assertTrue(entries.all { it.extractedByOcr })
    }

    @Test
    fun passwordProtectedScanRequiresPasswordAndCanThenBeRead() = runBlocking {
        val bytes = document(password = "123456") { scanPage(it, listOf("Ano: 2026", "10/01/2026 Mercado -20,00")) }
        val parser = PdfStatementParser(PdfBoxTextExtractor(context))
        assertThrows(StatementPasswordRequiredException::class.java) { runBlocking { parser.parse("senha.pdf", bytes) } }
        assertThrows(StatementPasswordRequiredException::class.java) { runBlocking { parser.parse("senha.pdf", bytes, password = "errada") } }
        assertEquals(2000L, parser.parse("senha.pdf", bytes, password = "123456").single().amount.cents)
    }

    @Test
    fun representativeBankTablesUsePositionedColumnsWithoutOcr() = runBlocking {
        // Synthetic layouts exercise the financial column families, not a certification of bank releases.
        data class Layout(val bank: String, val kind: StatementKind, val columns: List<Pair<Float, String>>, val values: List<String>, val cents: Long)
        val layouts = listOf(
            Layout("Nubank", StatementKind.CARD_BILL, listOf(40f to "Data", 150f to "Descricao", 450f to "Valor"), listOf("10 JAN", "Mercado", "150,75"), 15075),
            Layout("PicPay", StatementKind.BANK_STATEMENT, listOf(30f to "Data", 110f to "Hora", 190f to "Descricao", 450f to "Valor"), listOf("10/01", "12:30", "Pix enviado", "-20,00"), 2000),
            Layout("Banco do Brasil", StatementKind.BANK_STATEMENT, listOf(20f to "Data balancete", 130f to "Data movimento", 250f to "Historico", 395f to "Debito", 465f to "Credito", 535f to "Saldo"), listOf("11/01/2026", "10/01/2026", "Pix enviado", "20,00", "", "980,00"), 2000),
            Layout("Caixa", StatementKind.BANK_STATEMENT, listOf(30f to "Data mov.", 125f to "Documento", 230f to "Historico", 430f to "Valor", 520f to "Saldo"), listOf("10/01", "12345", "Pix enviado", "20,00 D", "980,00 C"), 2000),
            Layout("Inter", StatementKind.BANK_STATEMENT, listOf(40f to "Data", 150f to "Descricao", 440f to "Valor", 525f to "Saldo"), listOf("10/01", "Compra", "-25,00", "975,00"), 2500)
        )
        layouts.forEach { layout ->
            val bytes = document { doc ->
                val page = PDPage(); doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    fun put(x: Float, y: Float, text: String) {
                        stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 10f); stream.newLineAtOffset(x, y); stream.showText(text); stream.endText()
                    }
                    put(30f, 780f, layout.bank)
                    put(30f, 760f, if (layout.kind == StatementKind.CARD_BILL) "Fatura de janeiro/2026" else "Ano: 2026")
                    layout.columns.forEach { (x, label) -> put(x, 720f, label) }
                    layout.columns.forEachIndexed { index, (x, _) -> if (layout.values[index].isNotEmpty()) put(x, 690f, layout.values[index]) }
                }
            }
            val entries = PdfStatementParser(PdfBoxTextExtractor(context)).parse("${layout.bank}.pdf", bytes, layout.kind)
            assertEquals(layout.bank, layout.cents, entries.single().amount.cents)
            assertEquals(layout.bank, LocalDate.of(2026, 1, 10), entries.single().date)
            assertFalse(layout.bank, entries.single().extractedByOcr)
        }
    }

    @Test
    fun smallBankLogoDoesNotTriggerOcrForReadableText() = runBlocking {
        val bytes = document { doc ->
            textPage(doc, listOf("Fatura", "10/01/2026 Mercado 20,00"))
            val logo = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
            try {
                logo.eraseColor(Color.BLUE)
                PDPageContentStream(doc, doc.getPage(0), PDPageContentStream.AppendMode.APPEND, true, true).use {
                    it.drawImage(LosslessFactory.createFromImage(doc, logo), 520f, 760f, 40f, 40f)
                }
            } finally { logo.recycle() }
        }
        val entry = PdfStatementParser(PdfBoxTextExtractor(context)).parse("logo.pdf", bytes).single()
        assertEquals(2000L, entry.amount.cents)
        assertFalse(entry.extractedByOcr)
    }

    private fun document(password: String? = null, build: (PDDocument) -> Unit): ByteArray {
        PDFBoxResourceLoader.init(context)
        return ByteArrayOutputStream().use { output ->
            PDDocument().use { doc ->
                build(doc)
                if (password != null) doc.protect(StandardProtectionPolicy("owner-secret", password, AccessPermission()).apply { encryptionKeyLength = 128 })
                doc.save(output)
            }
            output.toByteArray()
        }
    }
    private fun textPage(doc: PDDocument, lines: List<String>) {
        val page = PDPage(); doc.addPage(page)
        PDPageContentStream(doc, page).use { stream ->
            stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 12f); stream.newLineAtOffset(40f, 750f)
            lines.forEach { stream.showText(it); stream.newLineAtOffset(0f, -24f) }; stream.endText()
        }
    }
    private fun scanPage(doc: PDDocument, lines: List<String>) {
        val image = Bitmap.createBitmap(1400, 1900, Bitmap.Config.ARGB_8888)
        try {
            image.eraseColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 36f; typeface = android.graphics.Typeface.MONOSPACE }
            Canvas(image).apply { lines.forEachIndexed { index, line -> drawText(line, 65f, 110f + index * 85f, paint) } }
            val page = PDPage(); doc.addPage(page)
            PDPageContentStream(doc, page).use { it.drawImage(LosslessFactory.createFromImage(doc, image), 0f, 0f, page.mediaBox.width, page.mediaBox.height) }
        } finally { image.recycle() }
    }
}
