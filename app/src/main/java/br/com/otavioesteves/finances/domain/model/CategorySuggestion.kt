package br.com.otavioesteves.finances.domain.model

data class CategorySuggestion(
    val entry: RawStatementEntry,
    val suggestedCategory: Category?,
    val confidence: Float
) {
    init {
        require(confidence in 0f..1f) { "confidence must be between 0 and 1" }
    }
}
