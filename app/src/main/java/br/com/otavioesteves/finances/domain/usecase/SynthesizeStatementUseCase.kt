package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.model.SuggestionSource
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import kotlinx.coroutines.flow.first

class SynthesizeStatementUseCase(
    private val transactionCategorizer: TransactionCategorizer,
    private val categoriesRepository: CategoriesRepository
) {
    suspend operator fun invoke(
        entries: List<RawStatementEntry>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<CategorySuggestion> {
        val knownCategories = categoriesRepository.getCategories().first()
        val suggestions = transactionCategorizer.categorize(entries, knownCategories, onProgress).getOrThrow()
        val transferCategory = knownCategories.firstOrNull { it.type == CategoryType.TRANSFER }
        return suggestions.map { suggestion ->
            if (suggestion.source != SuggestionSource.MEMORY && transferCategory != null && (suggestion.entry.type == TransactionType.TRANSFER ||
                (suggestion.entry.type == TransactionType.EXPENSE &&
                    CARD_PAYMENT_DESCRIPTION.containsMatchIn(suggestion.entry.description)))
            ) {
                suggestion.copy(
                    entry = suggestion.entry.copy(type = TransactionType.TRANSFER),
                    suggestedCategory = transferCategory,
                    confidence = 0.7f,
                    source = SuggestionSource.RULE
                )
            } else suggestion
        }
    }

    private companion object {
        val CARD_PAYMENT_DESCRIPTION = Regex(
            "(?i)\\b(?:pagamento\\s+(?:da\\s+)?fatura|pag(?:amento)?\\s+(?:do\\s+)?cart[aã]o)\\b"
        )
    }
}
