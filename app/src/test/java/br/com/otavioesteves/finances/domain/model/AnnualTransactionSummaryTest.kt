package br.com.otavioesteves.finances.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AnnualTransactionSummaryTest {
    @Test
    fun `summary preserves months categories zero amounts and exact note counts`() {
        val rows = listOf(
            transaction(1, 2026, 1, 100, TransactionType.EXPENSE, "tracked"),
            transaction(1, 2026, 1, 250, TransactionType.EXPENSE),
            transaction(2, 2026, 1, 700, TransactionType.EXPENSE),
            transaction(1, 2026, 12, 0, TransactionType.EXPENSE),
            transaction(3, 2026, 2, 900, TransactionType.INCOME, "tracked"),
            transaction(4, 2026, 3, 1000, TransactionType.TRANSFER, "tracked"),
            transaction(1, 2025, 1, 800, TransactionType.EXPENSE, "tracked"),
            transaction(1, 2026, 2, 200, TransactionType.EXPENSE, "tracked suffix")
        )
        val summary = summarizeAnnualTransactions(rows, 2026, "tracked")

        assertEquals(12, summary.monthlyExpenses.size)
        assertEquals(1050L, summary.monthlyExpenses[0])
        assertEquals(200L, summary.monthlyExpenses[1])
        assertEquals(0L, summary.monthlyExpenses[11])
        assertEquals(350L, summary.categoryMonthlyExpenses.getValue(1)[0])
        assertEquals(700L, summary.categoryMonthlyExpenses.getValue(2)[0])
        assertEquals(setOf(1L, 2L), summary.categoryMonthlyExpenses.keys)
        assertEquals(3, summary.trackedNoteCount)
        assertEquals(0, summarizeAnnualTransactions(rows, 2026, null).trackedNoteCount)
    }

    @Test
    fun `empty year returns twelve zero months without categories`() {
        assertEquals(AnnualTransactionSummary(2026), summarizeAnnualTransactions(emptyList(), 2026, "tracked"))
    }

    private fun transaction(category: Long, year: Int, month: Int, cents: Long, type: TransactionType, note: String? = null) =
        Transaction(0, "Teste", Money(cents), category, LocalDate.of(year, month, 1), type, notes = note)
}
