package br.com.otavioesteves.finances.data.statement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.domain.model.StatementKind
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Generated structures only; no private PDF or customer information is packaged here. */
@RunWith(AndroidJUnit4::class)
class BetaImportPdfIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun accountCoverWithTwoBalancesIsRemovedOnlyWhenBothMovementTotalsReconcile() = runBlocking {
        for (total in listOf("100,00", "101,00")) {
            val bytes = pdf {
                text("Extrato de conta", 30f, 770f)
                text("Entradas: R$ $total", 30f, 745f)
                text("Saldo inicial: R$ 10,00 Saldo final: R$ 90,00", 30f, 720f)
                text("Saidas: R$ -20,00", 30f, 695f)
                text("DETALHE DOS MOVIMENTOS", 30f, 670f)
                text("Data", 30f, 640f); text("Descricao", 160f, 640f)
                text("Valor", 350f, 640f); text("Saldo", 470f, 640f)
                text("01/11/2026", 30f, 610f); text("Credito", 160f, 610f)
                text("R$ 100,00", 350f, 610f); text("R$ 110,00", 470f, 610f)
                text("02/11/2026", 30f, 580f); text("Compra", 160f, 580f)
                text("R$ -20,00", 350f, 580f); text("R$ 90,00", 470f, 580f)
            }
            if (total == "100,00") assertEquals(listOf(10_000L, 2000L), parse(bytes).map { it.amount.cents })
            else assertThrows(IllegalArgumentException::class.java) { runBlocking { parse(bytes) } }
        }
    }

    @Test fun namedHolderAndPaymentsAreReconciledAfterActualWordExtraction() = runBlocking {
        for (subtotal in listOf("50,00", "51,00")) {
            val bytes = pdf {
                listOf("Nubank", "FATURA 07 FEV 2025 EMISSAO E ENVIO 31 JAN 2025",
                    "TRANSACOES DE 31 DEZ A 31 JAN", "Pessoa Ficticia R$ $subtotal",
                    "02 JAN Loja Exemplo R$ 60,00", "03 JAN Estorno -R$ 10,00",
                    "Pagamentos -R$ 40,00", "04 JAN Pagamento em 04 JAN -R$ 40,00")
                    .forEachIndexed { index, line -> text(line, 30f, 770f - index * 28f) }
            }
            if (subtotal == "50,00") assertEquals(listOf(6000L, 1000L, 4000L), parse(bytes, StatementKind.CARD_BILL).map { it.amount.cents })
            else assertThrows(IllegalArgumentException::class.java) { runBlocking { parse(bytes, StatementKind.CARD_BILL) } }
        }
    }

    @Test fun percentageLegendsAndWrappedForeignDeclarationsRemainDistinct() = runBlocking {
        val valid = pdf {
            text("Fatura", 30f, 770f); text("Valores em R$ % do total parcelado", 30f, 740f)
            text("01/11/2026 Compra 100,00", 30f, 700f)
        }
        assertEquals(10_000L, parse(valid, StatementKind.CARD_BILL).single().amount.cents)
        val foreign = pdf {
            listOf("Fatura", "Valores expressos", "em", "USD", "01/11/2026 Compra 100,00")
                .forEachIndexed { index, line -> text(line, 30f, 770f - index * 28f) }
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { parse(foreign, StatementKind.CARD_BILL) } }
        Unit
    }

    @Test fun aDatedZeroOnTheCoverCannotVanishWhenAccountTotalsMatch() {
        val bytes = pdf {
            text("Extrato de conta", 30f, 770f)
            text("01/11/2026 Movimento nao conferido R$ 0,00", 30f, 740f)
            text("Entradas: R$ 100,00", 30f, 710f)
            text("Saldo inicial: R$ 10,00 Saldo final: R$ 90,00", 30f, 680f)
            text("Saidas: R$ -20,00", 30f, 650f)
            text("DETALHE DOS MOVIMENTOS", 30f, 620f)
            text("Data", 30f, 590f); text("Descricao", 160f, 590f)
            text("Valor", 350f, 590f); text("Saldo", 470f, 590f)
            text("01/11/2026", 30f, 560f); text("Credito", 160f, 560f)
            text("R$ 100,00", 350f, 560f); text("R$ 110,00", 470f, 560f)
            text("02/11/2026", 30f, 530f); text("Compra", 160f, 530f)
            text("R$ -20,00", 350f, 530f); text("R$ 90,00", 470f, 530f)
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { parse(bytes) } }
    }

    private suspend fun parse(bytes: ByteArray, kind: StatementKind = StatementKind.BANK_STATEMENT) =
        PdfStatementParser(PdfBoxTextExtractor(context)).parse("synthetic.pdf", bytes, kind)

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
        beginText(); setFont(PDType1Font.HELVETICA, 11f); newLineAtOffset(x, y); showText(value); endText()
    }
}
