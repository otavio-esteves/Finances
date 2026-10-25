package br.com.otavioesteves.finances.domain.model

data class CategorySuggestion(
    val entry: RawStatementEntry,
    val suggestedCategory: Category?,
    val confidence: Float,
    val source: SuggestionSource = SuggestionSource.RULE,
    val originalType: TransactionType = entry.type,
    val originalEntry: RawStatementEntry = entry
) {
    init {
        require(confidence in 0f..1f) { "confidence must be between 0 and 1" }
    }
}
