package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.LocalAiRepository
import kotlinx.coroutines.flow.first

class SynthesizeStatementUseCase(
    private val localAiRepository: LocalAiRepository,
    private val categoriesRepository: CategoriesRepository
) {
    suspend operator fun invoke(entries: List<RawStatementEntry>): List<CategorySuggestion> {
        val knownCategories = categoriesRepository.getCategories().first()
        return localAiRepository.suggestCategories(entries, knownCategories)
    }
}
