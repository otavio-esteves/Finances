package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GetCategorySummariesUseCaseTest {
    @Test
    fun invoke_returnsRepositorySummaries() = runBlocking {
        val period = MonthPeriod(year = 2026, month = 1)
        val expected = listOf(
            CategorySummary(
                category = Category(id = 1L, name = "Mercado", type = CategoryType.EXPENSE),
                totalAmount = Money.fromCents(25_000)
            )
        )
        val repository = FakeCategoriesRepository(expected)

        val result = GetCategorySummariesUseCase(repository)(period).first()

        assertEquals(expected, result)
        assertEquals(period, repository.lastRequestedPeriod)
    }

    private class FakeCategoriesRepository(
        private val summaries: List<CategorySummary>
    ) : CategoriesRepository {
        var lastRequestedPeriod: MonthPeriod? = null

        override fun getCategories(): Flow<List<Category>> = flowOf(emptyList())

        override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> {
            lastRequestedPeriod = period
            return flowOf(summaries)
        }
    }
}
