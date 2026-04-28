package br.com.otavioesteves.finances.utils

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import org.junit.Assert.assertTrue
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
        
        assertTrue(result.contains("ID,Data,Descrição,Valor (Centavos),Tipo,Categoria,Observações"))
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
}
