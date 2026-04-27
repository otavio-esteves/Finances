package br.com.otavioesteves.finances.data.repository

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class InMemoryCategoriesRepositoryTest {
    @Test
    fun getCategorySummaries_calculatesTotalsFromTransactions() = runBlocking {
        val store = InMemoryFinanceStore(
            categories = listOf(
                Category(id = 1L, name = "Mercado", type = CategoryType.EXPENSE),
                Category(id = 2L, name = "Salário", type = CategoryType.INCOME),
                Category(id = 3L, name = "Outros", type = CategoryType.EXPENSE)
            ),
            transactions = listOf(
                Transaction(
                    id = 1L,
                    description = "Compra 1",
                    amount = Money.fromCents(10_000),
                    categoryId = 1L,
                    date = LocalDate.of(2026, 1, 3),
                    type = TransactionType.EXPENSE
                ),
                Transaction(
                    id = 2L,
                    description = "Compra 2",
                    amount = Money.fromCents(5_500),
                    categoryId = 1L,
                    date = LocalDate.of(2026, 1, 9),
                    type = TransactionType.EXPENSE
                ),
                Transaction(
                    id = 3L,
                    description = "Salário",
                    amount = Money.fromCents(300_000),
                    categoryId = 2L,
                    date = LocalDate.of(2026, 1, 5),
                    type = TransactionType.INCOME
                ),
                Transaction(
                    id = 4L,
                    description = "Fevereiro",
                    amount = Money.fromCents(99_999),
                    categoryId = 3L,
                    date = LocalDate.of(2026, 2, 1),
                    type = TransactionType.EXPENSE
                )
            )
        )
        val repository = InMemoryCategoriesRepository(store)

        val summaries = repository.getCategorySummaries(MonthPeriod(year = 2026, month = 1)).first()

        assertEquals(2, summaries.size)
        assertEquals("Mercado", summaries[0].category.name)
        assertEquals(15_500L, summaries[0].totalAmount.cents)
        assertEquals("Salário", summaries[1].category.name)
        assertEquals(300_000L, summaries[1].totalAmount.cents)
    }

    @Test
    fun getCategorySummaries_ignoresTransferTransactions() = runBlocking {
        val store = InMemoryFinanceStore(
            categories = listOf(
                Category(id = 1L, name = "Outros", type = CategoryType.EXPENSE)
            ),
            transactions = listOf(
                Transaction(
                    id = 1L,
                    description = "Transferência",
                    amount = Money.fromCents(50_000),
                    categoryId = 1L,
                    date = LocalDate.of(2026, 1, 8),
                    type = TransactionType.TRANSFER
                )
            )
        )
        val repository = InMemoryCategoriesRepository(store)

        val summaries = repository.getCategorySummaries(MonthPeriod(year = 2026, month = 1)).first()

        assertEquals(0, summaries.size)
    }
}
