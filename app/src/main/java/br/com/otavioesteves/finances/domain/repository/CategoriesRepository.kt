package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import kotlinx.coroutines.flow.Flow

interface CategoriesRepository {
    fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>>
    suspend fun addCategory(name: String, amount: Money)
    suspend fun deleteCategory(category: CategorySummary)
}
