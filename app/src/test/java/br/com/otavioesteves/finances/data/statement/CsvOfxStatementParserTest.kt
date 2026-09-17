package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class CsvOfxStatementParserTest {

    private val parser = CsvOfxStatementParser()

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
