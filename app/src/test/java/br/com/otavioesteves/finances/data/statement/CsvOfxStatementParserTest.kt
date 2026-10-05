package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.model.StatementKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class CsvOfxStatementParserTest {

    private val parser = CsvOfxStatementParser()

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
