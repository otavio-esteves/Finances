package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.ChatMessage
import br.com.otavioesteves.finances.domain.model.FinancialContext
import br.com.otavioesteves.finances.domain.model.RawStatementEntry

interface LocalAiRepository {
    suspend fun suggestCategories(
        entries: List<RawStatementEntry>,
        knownCategories: List<Category>
    ): List<CategorySuggestion>

    suspend fun sendMessage(message: String, context: FinancialContext): ChatMessage
}
