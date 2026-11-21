package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.model.SuggestionSource
import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.model.isCardPaymentDescription
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import kotlinx.coroutines.flow.first

class SynthesizeStatementUseCase(
    private val transactionCategorizer: TransactionCategorizer,
    private val categoriesRepository: CategoriesRepository
) {
    suspend operator fun invoke(
        entries: List<RawStatementEntry>,
        kind: StatementKind = StatementKind.BANK_STATEMENT,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<CategorySuggestion> {
        val knownCategories = categoriesRepository.getCategories().first()
        val suggestions = transactionCategorizer.categorize(entries, knownCategories, onProgress).getOrThrow()
        val categoriesById = knownCategories.associateBy { it.id }
        require(suggestions.size == entries.size && suggestions.indices.all { index ->
            val suggestion = suggestions[index]
            val original = entries[index]
            val suggestedEntry = if (suggestion.source == SuggestionSource.MEMORY && !original.hasNativeCsvType) {
                suggestion.entry.copy(type = original.type)
            } else suggestion.entry
            suggestion.originalEntry == original && suggestion.originalType == original.type &&
                suggestedEntry == original &&
                (suggestion.suggestedCategory == null || categoriesById[suggestion.suggestedCategory.id] == suggestion.suggestedCategory)
        }) {
            "A categorização devolveu lançamentos incompletos ou alterados. Nenhum lançamento foi importado. Selecione o arquivo novamente."
        }
        val transferCategory = knownCategories.firstOrNull { it.type == CategoryType.TRANSFER }
        return suggestions.map { suggestion ->
            if (suggestion.source == SuggestionSource.MEMORY || transferCategory == null) return@map suggestion
            val entry = suggestion.entry
            val needsTransferCategory = entry.type == TransactionType.TRANSFER &&
                (!entry.hasNativeCsvType || suggestion.suggestedCategory == null)
            if (needsTransferCategory || (!entry.hasNativeCsvType &&
                    isCardPaymentDescription(entry.description, includeBankLabels = kind == StatementKind.CARD_BILL))) {
                suggestion.copy(
                    entry = suggestion.entry.copy(type = TransactionType.TRANSFER),
                    suggestedCategory = transferCategory,
                    confidence = 0.7f,
                    source = SuggestionSource.RULE
                )
            } else suggestion
        }
    }
}
