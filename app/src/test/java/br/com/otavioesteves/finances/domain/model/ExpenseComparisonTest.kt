package br.com.otavioesteves.finances.domain.model

import org.junit.Assert.*
import org.junit.Test

class ExpenseComparisonTest {
    private val food = Category(1, "Alimentação", CategoryType.EXPENSE)
    @Test fun comparesRecordedExpensesAndExcludesTransfers() {
        val transfer = Category(2, "Transferência", CategoryType.TRANSFER)
        val result = compareExpenses(listOf(CategorySummary(food, Money(18000)), CategorySummary(transfer, Money(99999))),
            listOf(CategorySummary(food, Money(10000)), CategorySummary(transfer, Money(1))))
        assertEquals(1, result.size)
        assertEquals(8000L, result.single().difference.cents)
    }
    @Test fun noHistoryDoesNotInventIncreaseFromZero() {
        assertTrue(compareExpenses(listOf(CategorySummary(food, Money(100))), emptyList()).isEmpty())
    }
    @Test fun categoryWithoutNewEntriesShowsReduction() {
        assertEquals(-100L, compareExpenses(emptyList(), listOf(CategorySummary(food, Money(100)))).single().difference.cents)
    }
}
