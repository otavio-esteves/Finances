package br.com.otavioesteves.finances.data.ai

import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.SuggestionSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Categorization policy, independently testable across synchronous native inference boundaries. */
internal class LocalTransactionCategorization(
    private val ruleBasedCategorizer: TransactionCategorizer,
    private val confidenceThreshold: Float
) {
    suspend fun categorize(
        entries: List<RawStatementEntry>,
        categories: List<Category>,
        onProgress: (Int, Int) -> Unit,
        infer: suspend (String) -> String
    ): List<CategorySuggestion> {
        currentCoroutineContext().ensureActive()
        val suggestions = mutableListOf<CategorySuggestion>()
        for (chunk in entries.chunked(CHUNK_SIZE)) {
            currentCoroutineContext().ensureActive()
            suggestions += categorizeChunk(infer, chunk, categories)
            currentCoroutineContext().ensureActive()
            onProgress(suggestions.size, entries.size)
        }
        currentCoroutineContext().ensureActive()
        return suggestions
    }

    private suspend fun categorizeChunk(
        infer: suspend (String) -> String,
        chunk: List<RawStatementEntry>,
        categories: List<Category>
    ): List<CategorySuggestion> {
        val prompt = buildPrompt(chunk, categories)

        var parsed = askSafely(infer, prompt)
        if (parsed == null) {
            currentCoroutineContext().ensureActive()
            val retryPrompt = "$prompt\n\n$STRICT_JSON_REMINDER"
            parsed = askSafely(infer, retryPrompt)
        }

        if (parsed == null) {
            // JSON malformado nas duas tentativas: fallback só para este lote.
            currentCoroutineContext().ensureActive()
            return ruleBasedCategorizer.categorize(chunk, categories).getOrThrow()
        }

        val byIndex = parsed.associateBy { it.index }
        return chunk.mapIndexed { index, entry ->
            resolveSuggestion(entry, categories, byIndex[index])
        }
    }

    private suspend fun resolveSuggestion(
        entry: RawStatementEntry,
        categories: List<Category>,
        item: CategorizationResponseItem?
    ): CategorySuggestion {
        currentCoroutineContext().ensureActive()
        val expectedType = CategoryType.valueOf(entry.type.name)
        val category = item?.category?.let { name ->
            categories.firstOrNull { it.type == expectedType && it.name.equals(name, ignoreCase = true) }
        }
        val confidence = item?.confidence?.coerceIn(0f, 1f) ?: 0f

        if (category == null || confidence < confidenceThreshold) {
            // Sem categoria reconhecida ou confiança baixa: uma transação não
            // fica na mão de uma resposta ruim da IA, cai pro fallback.
            return ruleBasedCategorizer.categorize(listOf(entry), categories).getOrThrow().single()
        }

        return CategorySuggestion(
            entry = entry,
            suggestedCategory = category,
            confidence = confidence,
            source = SuggestionSource.AI
        )
    }

    private suspend fun askSafely(infer: suspend (String) -> String, prompt: String): List<CategorizationResponseItem>? = try {
        currentCoroutineContext().ensureActive()
        askAndParse(infer, prompt)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        null
    }

    private suspend fun askAndParse(infer: suspend (String) -> String, prompt: String): List<CategorizationResponseItem>? {
        currentCoroutineContext().ensureActive()
        val rawResponse = infer(prompt)
        currentCoroutineContext().ensureActive()

        val jsonText = extractJsonArray(rawResponse) ?: return null
        return runCatching { responseJson.decodeFromString<List<CategorizationResponseItem>>(jsonText) }.getOrNull()
    }

    private fun buildPrompt(chunk: List<RawStatementEntry>, categories: List<Category>): String {
        // Só descrição, valor e data — nunca dados de titular/conta/agência,
        // que não existem em RawStatementEntry (ver docs/PLANO_MOTOR_IA_LOCAL.md, "Verificação e privacidade").
        val entriesJson = responseJson.encodeToString(chunk.mapIndexed { index, entry ->
            CategorizationPromptEntry(index, entry.description, entry.amount.cents, entry.date.toString(), entry.type.name)
        })

        val categoryNames = categories.joinToString(", ") { it.name }

        return """
            Categorize cada transação abaixo em uma das categorias disponíveis.
            Categorias disponíveis: $categoryNames

            Transações:
            $entriesJson

            Responda apenas com um array JSON, sem texto antes ou depois, no formato:
            [{"index":0,"category":"nome da categoria","confidence":0.9}, ...]

            "confidence" vai de 0 a 1. Se nenhuma categoria servir bem, use "category": null e confidence baixa.
        """.trimIndent()
    }

    private fun extractJsonArray(raw: String): String? {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start == -1 || end == -1 || end < start) return null
        return raw.substring(start, end + 1)
    }

    @Serializable
    private data class CategorizationPromptEntry(
        val index: Int,
        val description: String,
        val amount: Long,
        val date: String,
        val type: String
    )

    @Serializable
    private data class CategorizationResponseItem(
        val index: Int,
        val category: String? = null,
        val confidence: Float = 0f
    )

    private companion object {
        const val CHUNK_SIZE = 20

        const val STRICT_JSON_REMINDER =
            "Responda apenas com um array JSON válido, sem nenhum texto antes ou depois, sem markdown."

        val responseJson = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
