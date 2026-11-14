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
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
        currentCoroutineContext().ensureActive()
        val suggestions = entries.mapIndexed { index, entry ->
            currentCoroutineContext().ensureActive()
            suggestCategory(entry, categories).also { onProgress(index + 1, entries.size) }
        }
        currentCoroutineContext().ensureActive()
        return Result.success(suggestions)
    }

    private fun suggestCategory(entry: RawStatementEntry, knownCategories: List<Category>): CategorySuggestion {
        val expectedType = CategoryType.valueOf(entry.type.name)
        val candidates = knownCategories.filter { it.type == expectedType }
        val description = normalize(entry.description)
        // Marketplace names do not imply groceries, but an explicitly named marketplace category still applies.
        val genericDescription = description.replace(MARKETPLACE, " ").trim()
        val directMatches = candidates.filter { normalize(it.name) != "outros" }.mapNotNull { category ->
            val name = normalize(category.name)
            val searchable = if (name == "mercado") genericDescription else description
            if (containsPhrase(searchable, name)) category to name else null
        }
        val keywordMatch = unambiguousCategory(directMatches)
        val vocabularyMatches = if (directMatches.isEmpty()) candidates.flatMap { category ->
            VOCABULARY.filter { normalize(category.name) in it.categories }.flatMap { rule ->
                rule.terms.filter { containsPhrase(genericDescription, it) }.map { category to it }
            }
        } else emptyList()
        val vocabularyMatch = unambiguousCategory(vocabularyMatches)
        val matchedCategory = keywordMatch ?: vocabularyMatch
        if (matchedCategory != null) {
            return CategorySuggestion(
                entry = entry,
                suggestedCategory = matchedCategory,
                confidence = if (keywordMatch != null) 0.8f else 0.7f,
                source = SuggestionSource.RULE
            )
        }

        val fallback = candidates.firstOrNull { normalize(it.name) == normalize(FALLBACK_CATEGORY_NAME) }
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
        data class CategoryVocabulary(val categories: Set<String>, val terms: Set<String>)
        const val FALLBACK_CATEGORY_NAME = "Outros"
        val BALANCE_KEYWORDS = setOf("saldo", "balanço", "balance")
        val MARKETPLACE = Regex("\\bmercado (?:livre|pago)\\b")
        val VOCABULARY = listOf(
            CategoryVocabulary(setOf("alimentacao", "mercado", "supermercado"), setOf(
                "mercado", "supermercado", "supermercados", "atacadao", "assai", "carrefour", "pao de acucar",
                "restaurante", "restaurantes", "padaria", "lanchonete", "cafeteria", "ifood", "uber eats"
            )),
            CategoryVocabulary(setOf("transporte"), setOf(
                "uber", "99app", "99 taxi", "cabify", "onibus", "metro", "combustivel",
                "posto de combustivel", "estacionamento", "pedagio"
            )),
            CategoryVocabulary(setOf("saude", "farmacia"), setOf(
                "farmacia", "drogaria", "drogasil", "droga raia", "laboratorio", "hospital", "clinica", "plano de saude"
            )),
            CategoryVocabulary(setOf("moradia"), setOf(
                "aluguel", "condominio", "energia eletrica", "conta de luz", "conta de agua", "internet residencial"
            )),
            CategoryVocabulary(setOf("lazer"), setOf("netflix", "spotify", "cinema", "teatro", "steam")),
            CategoryVocabulary(setOf("educacao"), setOf("escola", "faculdade", "universidade", "mensalidade escolar", "curso")),
            CategoryVocabulary(setOf("salario"), setOf("salario", "folha de pagamento", "pro labore")),
            CategoryVocabulary(setOf("investimentos"), setOf("rendimento", "rendimentos", "dividendo", "dividendos", "juros sobre capital"))
        )

        fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

        fun containsPhrase(text: String, phrase: String): Boolean = phrase.isNotEmpty() && " $phrase " in " $text "

        fun unambiguousCategory(matches: List<Pair<Category, String>>): Category? {
            // Prefer a complete specific phrase, e.g. Uber Eats over Uber, without breaking ties by database order.
            val specific = matches.filter { (_, term) -> matches.none { (_, other) ->
                other != term && containsPhrase(other, term)
            } }
            return specific.map { it.first }.distinctBy { it.id }.singleOrNull()
        }
    }
}
