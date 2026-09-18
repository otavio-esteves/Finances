package br.com.otavioesteves.finances.presentation.importstatement

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion

sealed interface ImportStatementUiState {

    data object Idle : ImportStatementUiState

    data object Loading : ImportStatementUiState

    data class ReviewingSuggestions(
        val fileName: String,
        val suggestions: List<CategorySuggestion>,
        val availableCategories: List<Category>,
        val isConfirming: Boolean = false
    ) : ImportStatementUiState {
        val canConfirm: Boolean
            get() = suggestions.isNotEmpty() && suggestions.all { it.suggestedCategory != null } && !isConfirming
    }

    data class Success(val transactionCount: Int) : ImportStatementUiState

    data class Error(val message: String) : ImportStatementUiState
}
