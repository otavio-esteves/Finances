package br.com.otavioesteves.finances.presentation.categories

import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.MonthPeriod

sealed interface CategoriesUiState {
    data object Loading : CategoriesUiState

    data class Success(
        val monthPeriod: MonthPeriod,
        val categories: List<CategorySummary>
    ) : CategoriesUiState

    data class Empty(
        val monthPeriod: MonthPeriod
    ) : CategoriesUiState

    data class Error(
        val monthPeriod: MonthPeriod,
        val message: String
    ) : CategoriesUiState
}
