package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.utils.ExportFormat
import br.com.otavioesteves.finances.utils.TransactionExporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class CsvOfxStatementParserTest {

    private val parser = CsvOfxStatementParser()

    @Test
    fun ofxDecodesNamedAndNumericEntitiesExactlyOnceAndMatchesCsvText() = runBlocking {
        val memo = "Loja &amp; Filhos &lt;centro&gt; &quot;A&quot; &apos;B&apos; Caf&#233; &#x1F600; &amp;amp;"
        val expected = "Loja & Filhos <centro> \"A\" 'B' Café 😀 &amp;"
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><OFX><BANKTRANLIST><STMTTRN>" +
            "<DTPOSTED>20261101</DTPOSTED><TRNAMT>-10.00</TRNAMT><MEMO>$memo</MEMO>" +
            "</STMTTRN></BANKTRANLIST></OFX>"
        val entry = parser.parse("a.ofx", xml.toByteArray()).single()
        assertEquals(expected, entry.description)
        val csv = "Data;Descrição;Valor\n01/11/2026;\"${expected.replace("\"", "\"\"")}\";-10.00"
        assertEquals(parser.parse("a.csv", csv.toByteArray()).single(), entry.copy(legacyOfxDescription = null))
        assertEquals(memo, entry.legacyOfxDescription)
    }

    @Test
    fun ofxKeepsBareSgmlAmpersandsAndDecodesNameWhenMemoIsEncodedWhitespace() = runBlocking {
        val prefix = "<OFX><BANKTRANLIST><STMTTRN><DTPOSTED>20261101<TRNAMT>-10.00"
        val suffix = "</STMTTRN></BANKTRANLIST></OFX>"
        assertEquals("A & B", parser.parse("a.ofx", "$prefix<MEMO>A & B$suffix".toByteArray()).single().description)
        assertEquals("A & B", parser.parse("a.ofx", "$prefix<NAME>A &amp; B<MEMO>&#32;$suffix".toByteArray()).single().description)
    }

    @Test
    fun ofxRejectsUnknownEntitiesAndInvalidUnicodeWithoutResolvingExternalEntities() {
        val prefix = "<!DOCTYPE OFX [<!ENTITY external SYSTEM 'file:///unreadable'>]><OFX><BANKTRANLIST>" +
            "<STMTTRN><DTPOSTED>20261101<TRNAMT>-10.00<MEMO>"
        for (entity in listOf("&external;", "&unknown;", "&#0;", "&#xD800;", "&#x110000;", "&#999999999999;", "&#-1;", "&#x+41;")) {
            assertThrows(entity, IllegalArgumentException::class.java) {
                runBlocking { parser.parse("a.ofx", "$prefix$entity</STMTTRN></BANKTRANLIST></OFX>".toByteArray()) }
            }
        }
    }

    @Test
    fun parse_csv_rejectsAmbiguousColumnsAndRowsWithExtraOrMissingFields() {
        for (csv in listOf(
            "Data;Descrição;Valor original;Valor em reais\n01/11/2026;Compra;100,00;580,00",
            "Data;Data de pagamento;Descrição;Valor\n01/11/2026;02/11/2026;Compra;-10,00",
            "Data;Descrição;Histórico;Valor\n01/11/2026;Compra;Outra descrição;-10,00",
            "Data,Descrição,Valor\n01/11/2026,Mercado,-150,75",
            "Data;Descrição;Valor;Conta\n01/11/2026;Mercado;-150,75"
        )) {
            assertThrows(csv, IllegalArgumentException::class.java) { runBlocking { parser.parse("a.csv", csv.toByteArray()) } }
        }
    }

    @Test
    fun parse_csv_preservesQuotedDecimalsAndAcceptsUnrelatedExtraColumns() = runBlocking {
        val csv = "Data,Descrição,Valor,Conta\n01/11/2026,\"Mercado, Central\",\"-150,75\",Principal"
        val entry = parser.parse("a.csv", csv.toByteArray()).single()
        assertEquals(15075L, entry.amount.cents)
        assertEquals("Mercado, Central", entry.description)
    }

    @Test
    fun parse_csv_rejectsMisplacedQuotesInsteadOfDroppingDescriptionCharacters() {
        for (row in listOf("01/11/2026;Mer\"cado\";-10,00", "01/11/2026;\"Mercado\"outro;-10,00")) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { parser.parse("a.csv", ("Data;Descrição;Valor\n" + row).toByteArray()) }
            }
        }
    }

    @Test
    fun parse_ofx_honorsLegacyAndXmlEncodingDeclarations() = runBlocking {
        val body = "<OFX><BANKTRANLIST><STMTTRN><DTPOSTED>20261101<TRNAMT>-15.00<MEMO>Alimentação</STMTTRN></BANKTRANLIST></OFX>"
        for ((declaration, charset) in listOf(
            "OFXHEADER:100\r\nENCODING:USASCII\r\nCHARSET:1252\r\n\r\n" to "windows-1252",
            "OFXHEADER:100\nENCODING:USASCII\nCHARSET:ISO-8859-1\n\n" to "ISO-8859-1",
            "OFXHEADER:100\nENCODING:UNICODE\nCHARSET:NONE\n\n" to "UTF-8",
            "<?xml version=\"1.0\" encoding=\"windows-1252\"?>" to "windows-1252"
        )) {
            val bytes = (declaration + body).toByteArray(java.nio.charset.Charset.forName(charset))
            assertEquals("Alimentação", parser.parse("a.ofx", bytes).single().description)
        }
        assertEquals("Alimentação", parser.parse("a.ofx", body.toByteArray(Charsets.UTF_16)).single().description)
    }

    @Test
    fun parse_rejectsMalformedBytesAndUnsupportedCharsetsWithoutReplacementCharacters() {
        val invalidUtf8 = "Data;Descrição;Valor\n01/11/2026;".toByteArray() + byteArrayOf(0xc3.toByte()) + ";-10,00".toByteArray()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { parser.parse("a.csv", invalidUtf8) } }
        val ofx = "OFXHEADER:100\nENCODING:USASCII\nCHARSET:unknown-charset\n\n<OFX>"
        assertThrows(IllegalArgumentException::class.java) { runBlocking { parser.parse("a.ofx", ofx.toByteArray()) } }
    }

    @Test
    fun parse_ofx_usesNameWhenMemoIsEmpty() = runBlocking {
        val ofx = "<OFX><BANKTRANLIST><STMTTRN><DTPOSTED>20261101<TRNAMT>-15.00<NAME>Mercado<MEMO>\n</STMTTRN></BANKTRANLIST></OFX>"
        assertEquals("Mercado", parser.parse("a.ofx", ofx.toByteArray()).single().description)
    }

    @Test
    fun parse_ofx_rejectsConflictingOrRepeatedEncodingHeaders() {
        for (header in listOf("ENCODING:UNICODE\nCHARSET:1252", "CHARSET:1252\nCHARSET:UTF-8", "ENCODING:UTF-8\nENCODING:USASCII")) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { parser.parse("a.ofx", ("OFXHEADER:100\n$header\n\n<OFX>").toByteArray()) }
            }
        }
    }

    @Test
    fun parse_csv_recognizesAccentedHistoryHeader() = runBlocking {
        val entry = parser.parse("a.csv", "Data;Histórico;Valor\n01/11/2026;Mercado;-10,00".toByteArray()).single()
        assertEquals("Mercado", entry.description)
    }

    @Test
    fun parse_csv_rejectsDatesOutsideTheSelectableCalendar() {
        for (date in listOf("0000-01-01", "+10000-01-01")) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { parser.parse("a.csv", "Data;Descrição;Valor\n$date;Compra;-10,00".toByteArray()) }
            }
        }
    }

    @Test
    fun parse_nativeExport_preservesAmountsTypesDatesAndQuotedDescriptionsForEitherStatementKind() = runBlocking {
        val date = LocalDate.of(2026, 11, 1)
        val transactions = listOf(
            Transaction(1, "Mercado, com \"aspas\"\ne continuação", Money(1_000), 1, date, TransactionType.EXPENSE),
            Transaction(2, "Salário", Money(350_050), 2, date.plusDays(1), TransactionType.INCOME),
            Transaction(3, "Entre contas", Money(1), 3, date.plusDays(2), TransactionType.TRANSFER),
            Transaction(4, "Lançamento zerado", Money.Zero, 1, date.plusDays(3), TransactionType.EXPENSE),
            Transaction(5, "Limite de centavos", Money(Long.MAX_VALUE), 2, date.plusDays(4), TransactionType.INCOME)
        )
        val csv = TransactionExporter().export(transactions, emptyMap(), ExportFormat.CSV)

        for (kind in StatementKind.entries) {
            val entries = parser.parse("finances.csv", csv.toByteArray(), kind)
            assertEquals(transactions.map { it.description }, entries.map { it.description })
            assertEquals(transactions.map { it.amount }, entries.map { it.amount })
            assertEquals(transactions.map { it.date }, entries.map { it.date })
            assertEquals(transactions.map { it.type }, entries.map { it.type })
        }
    }

    @Test
    fun parse_nativeExport_acceptsReorderedColumnsWithoutLosingTheExplicitType() = runBlocking {
        val csv = "Tipo;Valor (Centavos);Descrição;Data;ID;Observações;Categoria\n" +
            "TRANSFER;0;Entre contas;2026-11-01;1;;Transferência"

        val entry = parser.parse("finances.csv", csv.toByteArray()).single()

        assertEquals(Money.Zero, entry.amount)
        assertEquals(TransactionType.TRANSFER, entry.type)
    }

    @Test
    fun parse_nativeExport_rejectsInvalidCentsAndTypesInsteadOfUsingBankSignRules() {
        val header = "ID,Data,Descrição,Valor (Centavos),Tipo,Categoria,Observações\n"
        for ((amount, type) in listOf(
            "-1000" to "EXPENSE", "10.00" to "INCOME", "10,00" to "INCOME",
            "9223372036854775808" to "INCOME", "1000" to "UNKNOWN", "1000" to ""
        )) {
            val csv = header + "1,2026-11-01,Teste,\"$amount\",$type,Outros,"
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { parser.parse("finances.csv", csv.toByteArray()) }
            }
        }
    }

    @Test
    fun parse_csv_rejectsIncompleteOrDuplicateNativeHeadersRatherThanMultiplyingCents() {
        for (csv in listOf(
            "Data,Descrição,Valor (Centavos)\n2026-11-01,Mercado,1000",
            "ID,Data,Descrição,Valor (Centavos),Tipo,Categoria,Tipo\n1,2026-11-01,Mercado,1000,EXPENSE,Outros,EXPENSE",
            "ID,Data,Descrição,Valor (Centavos),Tipo,Categoria,Observações\n1,2026-11-01,Mercado,1000,EXPENSE,Outros"
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { parser.parse("finances.csv", csv.toByteArray()) }
            }
        }
    }

    @Test
    fun parse_csv_rejectsInvalidCalendarDatesAndAcceptsLeapDay() = runBlocking {
        for (date in listOf("31/02/2026", "29/02/2025", "31/04/2026")) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { parser.parse("extrato.csv", "Data;Descrição;Valor\n$date;Mercado;-10,00".toByteArray()) }
            }
        }
        val entries = parser.parse("extrato.csv", "Data;Descrição;Valor\n29/02/2024;Mercado;-10,00".toByteArray())
        assertEquals(LocalDate.of(2024, 2, 29), entries.single().date)
    }

    @Test
    fun parse_csvAndOfx_preserveZeroAmounts() = runBlocking {
        val csv = parser.parse("extrato.csv", "Data;Descrição;Valor\n10/01/2026;Sem movimento;0,00".toByteArray())
        val ofx = parser.parse("extrato.ofx", "<STMTTRN><DTPOSTED>20260110<TRNAMT>0.00<MEMO>Sem movimento</BANKTRANLIST>".toByteArray())
        assertEquals(0L, csv.single().amount.cents)
        assertEquals(0L, ofx.single().amount.cents)
    }

    @Test
    fun parse_csv_readsIncomeAndExpenseEntries() = runBlocking {
        val csv = """
            Data,Descrição,Valor
            10/01/2026,Mercado Central,"-150,75"
            15/01/2026,Salário,"3000,00"
        """.trimIndent()

        val entries = parser.parse("extrato.csv", csv.toByteArray())

        assertEquals(2, entries.size)
        assertEquals("Mercado Central", entries[0].description)
        assertEquals(15_075L, entries[0].amount.cents)
        assertEquals(TransactionType.EXPENSE, entries[0].type)
        assertEquals(LocalDate.of(2026, 1, 10), entries[0].date)

        assertEquals("Salário", entries[1].description)
        assertEquals(300_000L, entries[1].amount.cents)
        assertEquals(TransactionType.INCOME, entries[1].type)
    }

    @Test
    fun parse_cardBillCsv_treatsPositivePurchasesAsExpensesAndNegativeRefundsAsIncome() = runBlocking {
        val csv = "Data;Descrição;Valor\n10/01/2026;Mercado;150,75\n11/01/2026;Estorno;-20,00"

        val entries = parser.parse("fatura.csv", csv.toByteArray(), StatementKind.CARD_BILL)

        assertEquals(TransactionType.EXPENSE, entries[0].type)
        assertEquals(15_075L, entries[0].amount.cents)
        assertEquals(TransactionType.INCOME, entries[1].type)
        assertEquals(2_000L, entries[1].amount.cents)
    }

    @Test
    fun parse_csv_withoutExpectedColumns_throws() {
        val csv = """
            Foo,Bar
            1,2
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { parser.parse("extrato.csv", csv.toByteArray()) }
        }
    }

    @Test
    fun parse_csv_readsSemicolonDelimitedMultilineAndEscapedQuotes() = runBlocking {
        val csv = "Data;Descrição;Valor\r\n10/01/2026;\"Mercado\r\n\"\"Central\"\"\";-1.250,75\r\n"

        val entries = parser.parse("extrato.csv", csv.toByteArray())

        assertEquals(1, entries.size)
        assertEquals("Mercado\r\n\"Central\"", entries.single().description)
        assertEquals(125_075L, entries.single().amount.cents)
    }

    @Test
    fun parse_csv_rejectsUnclosedQuotedField() {
        val csv = "Data,Descrição,Valor\n10/01/2026,\"Mercado,-10.00"
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { parser.parse("extrato.csv", csv.toByteArray()) }
        }
    }

    @Test
    fun parse_ofx_readsTransactionsFromStmttrnBlocks() = runBlocking {
        val ofx = """
            <OFX>
            <BANKTRANLIST>
            <STMTTRN>
            <TRNTYPE>DEBIT
            <DTPOSTED>20260110120000
            <TRNAMT>-52.30
            <MEMO>SUPERMERCADO XYZ
            </STMTTRN>
            <STMTTRN>
            <TRNTYPE>CREDIT
            <DTPOSTED>20260115000000
            <TRNAMT>3000.00
            <MEMO>SALARIO
            </STMTTRN>
            </BANKTRANLIST>
            </OFX>
        """.trimIndent()

        val entries = parser.parse("extrato.ofx", ofx.toByteArray())

        assertEquals(2, entries.size)
        assertEquals("SUPERMERCADO XYZ", entries[0].description)
        assertEquals(5_230L, entries[0].amount.cents)
        assertEquals(TransactionType.EXPENSE, entries[0].type)
        assertEquals(LocalDate.of(2026, 1, 10), entries[0].date)

        assertEquals("SALARIO", entries[1].description)
        assertEquals(300_000L, entries[1].amount.cents)
        assertEquals(TransactionType.INCOME, entries[1].type)
    }
}
