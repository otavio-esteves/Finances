package br.com.otavioesteves.finances.data.repository

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class CategoriesRepositoryImpl : CategoriesRepository {

    override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> = flow {
        delay(1000)

        val data = listOf(
            CategorySummary(Category(1, "Mercado"), Money.fromCents(125_075)),
            CategorySummary(Category(2, "Água"), Money.fromCents(5_000)),
            CategorySummary(Category(3, "Energia"), Money.fromCents(25_000)),
            CategorySummary(Category(4, "Farmácia"), Money.fromCents(12_000)),
            CategorySummary(Category(5, "Lazer"), Money.fromCents(30_000)),
            CategorySummary(Category(6, "Assinaturas"), Money.fromCents(4_590))
        )
        emit(data)
    }

    override suspend fun addCategory(name: String, amount: Money) = Unit

    override suspend fun deleteCategory(category: CategorySummary) = Unit
}
