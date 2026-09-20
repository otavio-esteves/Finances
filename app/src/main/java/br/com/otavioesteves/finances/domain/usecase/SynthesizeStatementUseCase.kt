package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import kotlinx.coroutines.flow.first

class SynthesizeStatementUseCase(
    private val transactionCategorizer: TransactionCategorizer,
    private val categoriesRepository: CategoriesRepository
) {
    suspend operator fun invoke(entries: List<RawStatementEntry>): List<CategorySuggestion> {
        val knownCategories = categoriesRepository.getCategories().first()
        return transactionCategorizer.categorize(entries, knownCategories).getOrThrow()
    }
}
