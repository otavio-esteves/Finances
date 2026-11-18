package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.StatementKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StatementIntegrityRegressionTest {
    private val parser = CsvOfxStatementParser()
    private val purchase = "<STMTTRN><DTPOSTED>20261101<TRNAMT>-100.00<MEMO>Loja ABC</STMTTRN>"
    private val document = "<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>BRL<BANKTRANLIST>" +
        purchase + "</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>"

    @Test fun csvRejectsForeignCurrencyNamesInTheAmountHeader() {
        for (unit in listOf("dólar", "DÓLARES AMERICANOS", "euros", "libras", "ienes", "dollars", "pounds", "yen")) {
            for (kind in StatementKind.entries) assertThrows(unit, IllegalArgumentException::class.java) {
                runBlocking { parser.parse("a.csv", "Data;Descrição;Valor em $unit\n01/11/2026;Compra;-100,00".toByteArray(), kind) }
            }
        }
    }

    @Test fun pdfRejectsForeignCurrencyNamesInPlainAndPositionedHeaders() {
        val pdf = PdfStatementParser { "" }
        for (unit in listOf("dólares", "euros", "libras", "ienes", "dollars")) {
            for (text in listOf(
                "Data Descrição Valor em $unit\n01/11/2026 Compra -100,00",
                "Data\tDescrição\tValor em $unit\n01/11/2026\tCompra\t-100,00"
            )) assertThrows(unit, IllegalArgumentException::class.java) { pdf.parseText(text) }
        }
    }

    @Test fun brlHeadersAndUnlabelledValuesKeepTheirExistingMeaning() = runBlocking {
        for (label in listOf("Valor", "Valor em reais", "Valor (BRL)", "Valor (R$)")) {
            assertEquals(10_000L, parser.parse("a.csv", "Data;Descrição;$label\n01/11/2026;Compra;-100,00".toByteArray()).single().amount.cents)
            assertEquals(10_000L, PdfStatementParser { "" }
                .parseText("Data Descrição $label\n01/11/2026 Compra -100,00").single().amount.cents)
        }
    }

    @Test fun inlineCommentsAndInstructionsCannotTruncateAnyFinancialField() {
        for (markup in listOf("<!-- split -->", "<?split?>")) {
            for ((field, raw) in listOf(
                "TRNAMT" to "-1${markup}00.00", "DTPOSTED" to "202611${markup}01",
                "MEMO" to "Loja ${markup}ABC", "NAME" to "Loja ${markup}ABC", "CURDEF" to "BR${markup}L"
            )) {
                val second = "<STMTTRN><DTPOSTED>${if (field == "DTPOSTED") raw else "20261102"}" +
                    "<TRNAMT>${if (field == "TRNAMT") raw else "-10.00"}" +
                    "<MEMO>${if (field == "MEMO") raw else "Compra"}" +
                    (if (field == "NAME") "<NAME>$raw" else "") + "</STMTTRN>"
                val changed = if (field == "CURDEF") document.replace("<CURDEF>BRL", "<CURDEF>$raw")
                    else document.replace(purchase, purchase + second)
                val error = assertThrows(field, IllegalArgumentException::class.java) {
                    runBlocking { parser.parse("a.ofx", changed.toByteArray()) }
                }
                assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("interrompe um campo"))
            }
        }
    }

    @Test fun commentsBetweenSgmlFieldsDoNotChangeAmountsOrDescriptions() = runBlocking {
        val text = document.replace("<TRNAMT>", "<!-- <TRNAMT>-999.00 --><?skip?><TRNAMT>")
        assertEquals(10_000L, parser.parse("a.ofx", text.toByteArray()).single().amount.cents)
        assertEquals("Loja ABC", parser.parse("a.ofx", text.toByteArray()).single().description)
    }

    @Test fun noTruncatedPrefixOfACompleteDocumentCanReturnAPartialBatch() {
        for (end in 1 until document.length) assertThrows("cut=$end", IllegalArgumentException::class.java) {
            runBlocking { parser.parse("a.ofx", document.substring(0, end).toByteArray()) }
        }
        assertEquals(1, runBlocking { parser.parse("a.ofx", document.toByteArray()) }.size)
    }

    @Test fun financialContainersRequireMatchingClosuresButSgmlLeavesRemainOptional() = runBlocking {
        assertEquals(1, parser.parse("a.ofx", document.toByteArray()).size)
        assertEquals(1, parser.parse("a.ofx", document
            .replace("20261101", "20261101</DTPOSTED>")
            .replace("-100.00", "-100.00</TRNAMT>")
            .replace("Loja ABC", "Loja ABC</MEMO>")
            .replace("<CURDEF>BRL", "<CURDEF>BRL</CURDEF>").toByteArray()).size)
        for (tag in listOf("OFX", "BANKTRANLIST", "STMTTRN", "STMTRS", "STMTTRNRS", "BANKMSGSRSV1")) {
            assertThrows(tag, IllegalArgumentException::class.java) {
                runBlocking { parser.parse("a.ofx", document.replace("</$tag>", "").toByteArray()) }
            }
        }
    }
}
