package br.com.otavioesteves.finances.presentation.importstatement

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion

sealed interface ImportStatementUiState {

    data object Idle : ImportStatementUiState

    data class Loading(
        val fileName: String,
        val stage: ImportProcessingStage = ImportProcessingStage.READING,
        val processedEntries: Int = 0,
        val totalEntries: Int? = null
    ) : ImportStatementUiState {
        /** Only categorization reports measured work; extraction/OCR has no estimated percentage. */
        val progress: Float?
            get() = totalEntries?.takeIf { it > 0 && stage == ImportProcessingStage.CATEGORIZING }
                ?.let { processedEntries.coerceIn(0, it).toFloat() / it }
    }
    data class NeedsPassword(val fileName: String, val error: String? = null) : ImportStatementUiState

    data class ReviewingSuggestions(
        val fileName: String,
        val suggestions: List<CategorySuggestion>,
        val availableCategories: List<Category>,
        val potentialOverlapCount: Int = 0,
        val overlapAcknowledged: Boolean = false,
        val isConfirming: Boolean = false,
        val ocrAcknowledged: Boolean = false,
        val isCheckingOverlaps: Boolean = false,
        val validationError: String? = null,
        val sourceLabel: String = "",
        val expectedExpenseTotal: String = "",
        val differenceAcknowledged: Boolean = false
    ) : ImportStatementUiState {
        val canConfirm: Boolean
            get() = suggestions.isNotEmpty() && suggestions.all {
                it.suggestedCategory?.type?.name == it.entry.type.name
            } && (potentialOverlapCount == 0 || overlapAcknowledged) && !isConfirming &&
                (!requiresOcrReview || ocrAcknowledged) && !isCheckingOverlaps && validationError == null &&
                (expectedExpenseTotal.isBlank() || (expectedTotalValid && (expenseDifference?.isZero() == true || differenceAcknowledged)))
        val expenseTotal: br.com.otavioesteves.finances.domain.model.Money get() = suggestions
            .filter { it.entry.type == br.com.otavioesteves.finances.domain.model.TransactionType.EXPENSE }
            .fold(br.com.otavioesteves.finances.domain.model.Money.Zero) { total, it -> total + it.entry.amount }
        val expectedTotalValid: Boolean get() = br.com.otavioesteves.finances.utils.MoneyFormatter.parse(expectedExpenseTotal)
            ?.let { !it.isNegative() } == true
        val expenseDifference: br.com.otavioesteves.finances.domain.model.Money? get() =
            br.com.otavioesteves.finances.utils.MoneyFormatter.parse(expectedExpenseTotal)?.takeIf { !it.isNegative() }
                ?.let { expenseTotal - it }
        val requiresOcrReview: Boolean get() = suggestions.any { it.entry.extractedByOcr }
    }

    data class Success(val transactionCount: Int) : ImportStatementUiState

    data class Error(val message: String) : ImportStatementUiState
}

enum class ImportProcessingStage {
    READING, EXTRACTING, CHECKING_OVERLAPS, CATEGORIZING, PREPARING_REVIEW
}
