package br.com.otavioesteves.finances.data.repository

import br.com.otavioesteves.finances.data.local.dao.CategoryDao
import br.com.otavioesteves.finances.data.local.dao.TransactionDao
import br.com.otavioesteves.finances.data.local.mapper.toDomain
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import kotlinx.coroutines.flow.Flow
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

        return categoryDao.getCategorySummaries(startDate.toString(), endDate.toString())
            .map { entities -> 
                entities.map { it.toDomain() }
                    .sortedBy { it.category.id }
            }
    }
}
