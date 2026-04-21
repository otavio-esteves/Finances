package br.com.otavioesteves.finances.ui.state

import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.MonthPeriod

sealed interface CategoriesUiState {
    data object Loading : CategoriesUiState
    data class Success(
        val monthPeriod: MonthPeriod,
        val categories: List<CategorySummary>
    ) : CategoriesUiState
    data class Error(val message: String) : CategoriesUiState
}
