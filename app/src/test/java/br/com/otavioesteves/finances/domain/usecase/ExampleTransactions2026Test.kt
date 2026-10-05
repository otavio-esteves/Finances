package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleTransactions2026Test {
    @Test
    fun createsSixMarkedTransactionsForEachElapsedMonthWithoutFutureDates() {
        val today = LocalDate.of(2026, 10, 1)
        val categories = listOf(
            Category(1, "Outros", CategoryType.EXPENSE),
            Category(2, "Salário", CategoryType.INCOME)
        )

        val examples = exampleTransactions2026(categories, today)

        assertEquals(60, examples.size)
        assertEquals((1..10).toList(), examples.map { it.date.monthValue }.distinct())
        assertTrue(examples.all { it.date <= today && it.notes == EXAMPLE_TRANSACTIONS_NOTE })
        assertTrue(examples.all { it.description.startsWith("[EXEMPLO]") && it.amount.cents > 0 })
        assertEquals(10, examples.count { it.type == TransactionType.INCOME && it.categoryId == 2L })
        assertEquals(50, examples.count { it.type == TransactionType.EXPENSE && it.categoryId == 1L })
    }
}
