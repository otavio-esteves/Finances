package br.com.otavioesteves.finances.data.repository

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.sumMoney
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class InMemoryCategoriesRepository internal constructor(
    private val store: InMemoryFinanceStore = InMemoryRepositorySupport.sharedStore
) : CategoriesRepository {

    override fun getCategories(): Flow<List<Category>> = store.categories

    override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> {
        return combine(store.categories, store.transactions) { categories, transactions ->
            val transactionsInPeriod = transactions.filter { transaction ->
                transaction.date.year == period.year && transaction.date.monthValue == period.month
            }

            categories.mapNotNull { category ->
                val totalAmount = transactionsInPeriod
                    .asSequence()
                    .filter { transaction -> transaction.categoryId == category.id }
                    .map { transaction -> transaction.contributionAmount() }
                    .toList()
                    .sumMoney()

                if (totalAmount.isZero()) {
                    null
                } else {
                    CategorySummary(category = category, totalAmount = totalAmount)
                }
            }
        }.map { summaries ->
            summaries.sortedBy { summary -> summary.category.id }
        }
    }
}
