package br.com.otavioesteves.finances.data.repository

import br.com.otavioesteves.finances.data.local.dao.CategoryDao
import br.com.otavioesteves.finances.data.local.dao.TransactionDao
import br.com.otavioesteves.finances.data.local.mapper.toDomain
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomCategoriesRepository(
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao
) : CategoriesRepository {

    override fun getCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> {
        val startDate = LocalDate.of(period.year, period.month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)

        val transactionsFlow = transactionDao.getTransactionsByDateRange(startDate.toString(), endDate.toString())
            .map { entities -> entities.map { it.toDomain() } }
            
        val categoriesFlow = getCategories()

        return combine(categoriesFlow, transactionsFlow) { categories, transactions ->
            categories.mapNotNull { category ->
                val totalAmount = transactions
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
