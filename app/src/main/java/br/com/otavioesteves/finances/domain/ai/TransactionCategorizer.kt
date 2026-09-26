package br.com.otavioesteves.finances.domain.ai

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.RawStatementEntry

/**
 * Categorizes a batch of raw statement entries. Implementations may be
 * AI-backed (Fase 3) or the permanent rule-based fallback — see
 * docs/ARCHITECTURE.md, "Motor de IA local".
 */
interface TransactionCategorizer {
    suspend fun categorize(
        entries: List<RawStatementEntry>,
        categories: List<Category>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<List<CategorySuggestion>>
}
