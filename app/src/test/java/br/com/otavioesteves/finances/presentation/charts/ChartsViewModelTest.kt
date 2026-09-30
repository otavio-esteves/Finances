package br.com.otavioesteves.finances.presentation.charts

import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartsViewModelTest {
    @Test
    fun `annual totals include only expenses in selected year and category`() {
        val transactions = listOf(
            transaction(1, 2026, 1, 3, 100, TransactionType.EXPENSE),
            transaction(2, 2026, 1, 8, 250, TransactionType.EXPENSE),
            transaction(1, 2026, 2, 3, 500, TransactionType.EXPENSE),
            transaction(1, 2026, 1, 10, 900, TransactionType.INCOME),
            transaction(1, 2025, 1, 3, 700, TransactionType.EXPENSE)
        )

        assertEquals(350L, annualExpenseTotals(transactions, 2026)[0])
        assertEquals(100L, annualExpenseTotals(transactions, 2026, categoryId = 1)[0])
        assertEquals(500L, annualExpenseTotals(transactions, 2026, categoryId = 1)[1])
        assertEquals(0L, annualExpenseTotals(transactions, 2026, categoryId = 1)[2])
    }

    private fun transaction(categoryId: Long, year: Int, month: Int, day: Int, cents: Long, type: TransactionType) =
        Transaction(
            id = 0,
            description = "Teste",
            amount = Money.fromCents(cents),
            categoryId = categoryId,
            date = LocalDate.of(year, month, day),
            type = type
        )
}
