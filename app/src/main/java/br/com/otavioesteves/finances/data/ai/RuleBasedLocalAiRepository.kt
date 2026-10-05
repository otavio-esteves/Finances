package br.com.otavioesteves.finances.data.ai

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.ChatMessage
import br.com.otavioesteves.finances.domain.model.ChatRole
import br.com.otavioesteves.finances.domain.model.FinancialContext
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.SuggestionSource
import br.com.otavioesteves.finances.domain.repository.LocalAiRepository
import br.com.otavioesteves.finances.domain.usecase.answerFinancialQuestion
import br.com.otavioesteves.finances.utils.MoneyFormatter
import br.com.otavioesteves.finances.utils.formatMonthPeriod

/**
 * Deterministic, keyword-based stand-in for an on-device LLM. Permanent
 * fallback for [TransactionCategorizer] when the real engine is absent or
 * unsupported (see docs/ARCHITECTURE.md, "Motor de IA local"); also provides
 * limited chat responses when the model is unavailable or inference fails.
 */
class RuleBasedLocalAiRepository(
    private val dateProvider: DateProvider
) : LocalAiRepository, TransactionCategorizer {

    override suspend fun categorize(
        entries: List<RawStatementEntry>,
        categories: List<Category>,
        onProgress: (done: Int, total: Int) -> Unit
    ): Result<List<CategorySuggestion>> {
        val suggestions = entries.mapIndexed { index, entry ->
            suggestCategory(entry, categories).also { onProgress(index + 1, entries.size) }
        }
        return Result.success(suggestions)
    }

    private fun suggestCategory(entry: RawStatementEntry, knownCategories: List<Category>): CategorySuggestion {
        val expectedType = CategoryType.valueOf(entry.type.name)
        val candidates = knownCategories.filter { it.type == expectedType }
        val description = entry.description.lowercase()

        val keywordMatch = candidates.firstOrNull { category ->
            val words = category.name.lowercase().split(" ").filter { it.length > 2 }
            words.any { word -> description.contains(word) }
        }
        if (keywordMatch != null) {
            return CategorySuggestion(
                entry = entry,
                suggestedCategory = keywordMatch,
                confidence = 0.8f,
                source = SuggestionSource.RULE
            )
        }

        val fallback = candidates.firstOrNull { it.name.equals(FALLBACK_CATEGORY_NAME, ignoreCase = true) }
        return CategorySuggestion(
            entry = entry,
            suggestedCategory = fallback,
            confidence = if (fallback != null) 0.3f else 0f,
            source = SuggestionSource.RULE
        )
    }

    override suspend fun sendMessage(message: String, context: FinancialContext): ChatMessage {
        val normalized = message.lowercase()

        val numericAnswer = answerFinancialQuestion(message, context)
        if (numericAnswer != null) {
            return ChatMessage(0, ChatRole.ASSISTANT, numericAnswer, dateProvider.getCurrentDateTime())
        }

        val matchedSummary = context.categorySummaries.firstOrNull { summary ->
            normalized.contains(summary.category.name.lowercase())
        }

        val content = when {
            matchedSummary != null -> {
                "Você movimentou ${MoneyFormatter.format(matchedSummary.totalAmount)} em " +
                    "${matchedSummary.category.name} em ${formatMonthPeriod(context.period)} de ${context.period.year}."
            }

            BALANCE_KEYWORDS.any { normalized.contains(it) } -> {
                "Seu saldo em ${formatMonthPeriod(context.period)} de ${context.period.year} é de " +
                    "${MoneyFormatter.format(context.monthlyBalance)}."
            }

            context.categorySummaries.isEmpty() -> {
                "Ainda não há transações registradas em ${formatMonthPeriod(context.period)} de ${context.period.year} para eu analisar."
            }

            normalized.contains("com ") -> {
                "Ainda não consigo consultar lançamentos por estabelecimento. Posso resumir as categorias do mês."
            }

            else -> {
                val topCategories = context.categorySummaries
                    .filter { it.category.type == CategoryType.EXPENSE }
                    .sortedByDescending { it.totalAmount.cents }
                    .take(3)
                    .joinToString(", ") { "${it.category.name} (${MoneyFormatter.format(it.totalAmount)})" }
                if (topCategories.isEmpty()) {
                    "Não há despesas por categoria em ${formatMonthPeriod(context.period)} de ${context.period.year}."
                } else {
                    "Em ${formatMonthPeriod(context.period)} de ${context.period.year}, suas maiores categorias de despesa foram: $topCategories."
                }
            }
        }

        return ChatMessage(
            id = 0,
            role = ChatRole.ASSISTANT,
            content = content,
            createdAt = dateProvider.getCurrentDateTime()
        )
    }

    private companion object {
        const val FALLBACK_CATEGORY_NAME = "Outros"
        val BALANCE_KEYWORDS = setOf("saldo", "balanço", "balance")
    }
}
