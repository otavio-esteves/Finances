package br.com.otavioesteves.finances.presentation.importstatement

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.model.checkedMagnitudeTotal

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
        val overlapMatches: Map<Int, List<Transaction>> = emptyMap(),
        val overlapAcknowledged: Boolean = false,
        val isConfirming: Boolean = false,
        val ocrAcknowledged: Boolean = false,
        val isCheckingOverlaps: Boolean = false,
        val validationError: String? = null,
        val sourceLabel: String = "",
        val expectedExpenseTotal: String = "",
        val differenceAcknowledged: Boolean = false,
        val excludedIndices: Set<Int> = emptySet(),
        val selectedIndices: Set<Int> = emptySet(),
        val selectionMode: Boolean = false,
        val filter: ImportReviewFilter = ImportReviewFilter.ALL,
        val referenceYear: Int? = null
    ) : ImportStatementUiState {
        val includedSuggestions: List<CategorySuggestion>
            get() = suggestions.filterIndexed { index, _ -> index !in excludedIndices }
        val potentialOverlapCount: Int
            get() = overlapMatches.keys.count { it !in excludedIndices }
        val visibleIndices: List<Int> get() = indicesFor(filter)
        val bulkCategories: List<Category>
            get() {
                val types = selectedIndices.mapNotNull { suggestions.getOrNull(it)?.entry?.type }.distinct()
                return if (types.size == 1) availableCategories.filter { it.type.name == types.single().name }
                else emptyList()
            }

        fun indicesFor(filter: ImportReviewFilter): List<Int> = suggestions.indices.filter { index ->
            val suggestion = suggestions[index]
            when (filter) {
                ImportReviewFilter.ALL -> true
                ImportReviewFilter.EXCLUDED -> index in excludedIndices
                ImportReviewFilter.UNCATEGORIZED -> index !in excludedIndices &&
                    suggestion.suggestedCategory?.type?.name != suggestion.entry.type.name
                ImportReviewFilter.UNCERTAIN -> index !in excludedIndices && suggestion.confidence < 0.75f
                ImportReviewFilter.OVERLAPS -> index !in excludedIndices && overlapMatches[index].orEmpty().isNotEmpty()
            }
        }

        val canConfirm: Boolean
            get() = includedSuggestions.isNotEmpty() && includedSuggestions.all {
                it.suggestedCategory?.type?.name == it.entry.type.name
            } && amountError == null && (potentialOverlapCount == 0 || overlapAcknowledged) && !isConfirming &&
                (!requiresOcrReview || ocrAcknowledged) && !isCheckingOverlaps && validationError == null &&
                (expectedExpenseTotal.isBlank() || (expectedTotalValid && (expenseDifference?.isZero() == true || differenceAcknowledged)))
        val amountError: String? get() = try {
            checkedMagnitudeTotal(includedSuggestions.asSequence().map { it.entry.amount.cents })
            null
        } catch (error: IllegalArgumentException) { error.message }
        val expenseTotal: Money? get() = totalFor(TransactionType.EXPENSE)
        val incomeTotal: Money? get() = totalFor(TransactionType.INCOME)
        private fun totalFor(type: TransactionType): Money? = try {
            checkedMagnitudeTotal(includedSuggestions.asSequence().filter { it.entry.type == type }.map { it.entry.amount.cents })
        } catch (_: IllegalArgumentException) { null }
        val expectedTotalValid: Boolean get() = br.com.otavioesteves.finances.utils.MoneyFormatter.parse(expectedExpenseTotal)
            ?.let { !it.isNegative() } == true
        val expenseDifference: br.com.otavioesteves.finances.domain.model.Money? get() =
            br.com.otavioesteves.finances.utils.MoneyFormatter.parse(expectedExpenseTotal)?.takeIf { !it.isNegative() }
                ?.let { expected -> expenseTotal?.minus(expected) }
        val requiresOcrReview: Boolean get() = includedSuggestions.any { it.entry.extractedByOcr }
    }

    data class Success(val transactionCount: Int) : ImportStatementUiState

    data class Error(val message: String) : ImportStatementUiState
}

enum class ImportReviewFilter(val label: String) {
    ALL("Todos"), UNCATEGORIZED("Sem categoria"), UNCERTAIN("Sugestão incerta"),
    OVERLAPS("Possível duplicata"), EXCLUDED("Excluídos")
}

enum class ImportProcessingStage {
    READING, EXTRACTING, CHECKING_OVERLAPS, CATEGORIZING, PREPARING_REVIEW
}
