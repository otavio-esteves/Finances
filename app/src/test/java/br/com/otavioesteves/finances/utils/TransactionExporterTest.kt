package br.com.otavioesteves.finances.utils

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TransactionExporterTest {

    private val exporter = TransactionExporter()
    private val date = LocalDate.of(2026, 4, 27)
    private val categories = mapOf(
        1L to Category(1L, "Alimentação", CategoryType.EXPENSE),
        2L to Category(2L, "Salário", CategoryType.INCOME)
    )
    private val transactions = listOf(
        Transaction(1L, "Almoço", Money.fromCents(2500), 1L, date, TransactionType.EXPENSE),
        Transaction(2L, "Salário mensal", Money.fromCents(500000), 2L, date, TransactionType.INCOME)
    )

    @Test
    fun `export to CSV returns correct format`() {
        val result = exporter.export(transactions, categories, ExportFormat.CSV)
        
        assertTrue(result.contains("ID,Data,Descrição,Valor (Centavos),Tipo,Categoria,Observações,Formato Finances"))
        assertTrue(result.contains("1,2026-04-27,\"Almoço\",2500,EXPENSE,\"Alimentação\""))
        assertTrue(result.contains("2,2026-04-27,\"Salário mensal\",500000,INCOME,\"Salário\""))
    }

    @Test
    fun `export to JSON returns correct format`() {
        val result = exporter.export(transactions, categories, ExportFormat.JSON)
        
        assertTrue(result.contains("\"id\": 1"))
        assertTrue(result.contains("\"description\": \"Almoço\""))
        assertTrue(result.contains("\"amountCents\": 2500"))
        assertTrue(result.contains("\"categoryName\": \"Alimentação\""))
        assertTrue(result.contains("\"id\": 2"))
        assertTrue(result.contains("\"description\": \"Salário mensal\""))
    }

    @Test
    fun `export handles special characters correctly`() {
        val specialTransactions = listOf(
            Transaction(3L, "Café com \"aspas\"", Money.fromCents(500), 1L, date, TransactionType.EXPENSE)
        )
        val csv = exporter.export(specialTransactions, categories, ExportFormat.CSV)
        val json = exporter.export(specialTransactions, categories, ExportFormat.JSON)

        // CSV should escape quotes with double quotes
        assertTrue(csv.contains("\"Café com \"\"aspas\"\"\""))
        // JSON should escape quotes with backslash
        assertTrue(json.contains("\"description\": \"Café com \\\"aspas\\\"\""))
    }

    @Test
    fun `export CSV quotes commas line breaks carriage returns and category text`() {
        val transaction = Transaction(
            4L,
            "Descrição, com\nquebra\rde linha",
            Money.fromCents(500),
            3L,
            date,
            TransactionType.EXPENSE,
            notes = "Observação, com \"aspas\""
        )
        val csv = exporter.export(
            listOf(transaction),
            mapOf(3L to Category(3L, "Categoria, \"especial\"", CategoryType.EXPENSE)),
            ExportFormat.CSV
        )

        assertEquals(
            "ID,Data,Descrição,Valor (Centavos),Tipo,Categoria,Observações,Formato Finances\n" +
                "4,2026-04-27,\"Descrição, com\nquebra\rde linha\",500,EXPENSE," +
                "\"Categoria, \"\"especial\"\"\",\"Observação, com \"\"aspas\"\"\",2",
            csv
        )
    }

    @Test
    fun `export CSV prefixes formula-like text while retaining its value`() {
        val transaction = transactions.first().copy(
            description = "=HYPERLINK(\"https://example.invalid\")",
            notes = "-1+1"
        )
        val csv = exporter.export(
            listOf(transaction),
            mapOf(1L to Category(1L, "+Categoria", CategoryType.EXPENSE)),
            ExportFormat.CSV
        )

        assertTrue(csv.contains("\"'=HYPERLINK(\"\"https://example.invalid\"\")\""))
        assertTrue(csv.contains("\"'+Categoria\""))
        assertTrue(csv.contains("\"'-1+1\""))
    }

    @Test
    fun `export escapes controls and full width formula prefixes in every text column`() {
        for (prefix in listOf("\t", "\r", "\n", "\r\n", "\u0000", " \t", "  ", "＝", "＋", "－", "＠")) {
            val text = "${prefix}=1+1"
            val csv = exporter.export(listOf(transactions.first().copy(description = text, notes = text)),
                mapOf(1L to Category(1, text, CategoryType.EXPENSE)), ExportFormat.CSV)
            assertEquals("ID,Data,Descrição,Valor (Centavos),Tipo,Categoria,Observações,Formato Finances\n" +
                "1,2026-04-27,\"'$text\",2500,EXPENSE,\"'$text\",\"'$text\",2", csv)
        }
    }

    @Test
    fun `native CSV round trip preserves controls formulas and existing apostrophes`() = kotlinx.coroutines.runBlocking {
        val parser = br.com.otavioesteves.finances.data.statement.CsvOfxStatementParser()
        for (description in listOf("\t=1+1", "\r=1+1", "\n=1+1", "\u0000=1+1", " \t=1+1", "＝1+1", "'=1+1", "Normal")) {
            val transaction = transactions.first().copy(description = description)
            val csv = exporter.export(listOf(transaction), categories, ExportFormat.CSV)
            val entry = parser.parse("export.csv", csv.toByteArray()).single()
            assertEquals(description, entry.description)
            assertEquals(transaction.amount, entry.amount)
            assertEquals(transaction.type, entry.type)
        }
    }
}
