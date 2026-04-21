package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import kotlinx.coroutines.flow.Flow

class GetCategorySummariesUseCase(
    private val repository: CategoriesRepository
) {
    operator fun invoke(period: MonthPeriod): Flow<List<CategorySummary>> {
        return repository.getCategorySummaries(period)
    }
}
