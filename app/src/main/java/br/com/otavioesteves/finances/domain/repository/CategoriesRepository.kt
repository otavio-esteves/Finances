package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import kotlinx.coroutines.flow.Flow

interface CategoriesRepository {
    fun getCategories(): Flow<List<Category>>
    fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>>
}
