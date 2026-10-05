package br.com.otavioesteves.finances.data.ai

import br.com.otavioesteves.finances.domain.ai.AiEngineState
import br.com.otavioesteves.finances.domain.ai.LocalAiEngine
import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.SuggestionSource
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * [TransactionCategorizer] backed by a real LiteRT-LM engine
 * (docs/PLANO_MOTOR_IA_LOCAL.md, Fase 3). Categorization calls to the
 * `com.google.ai.edge.litertlm.*` API are isolated here. Text is read from
 * the response's `Content.Text` items. Runtime loading and one categorization
 * are covered by the isolated device smoke test.
 *
 * Falls back to [ruleBasedCategorizer] whenever the engine fails to load or
 * a chunk's JSON can't be parsed even after one retry (see the plan's fallback decision).
 * Per-transaction, a suggestion below [confidenceThreshold] also falls back
 * — never leaves a transaction on a low-confidence AI guess.
 */
class LiteRtTransactionCategorizer(
    private val localAiEngine: LocalAiEngine,
    private val ruleBasedCategorizer: TransactionCategorizer,
    private val confidenceThreshold: Float = 0.5f,
    private val engineSession: LiteRtEngineSession
) : TransactionCategorizer {

    override suspend fun categorize(
        entries: List<RawStatementEntry>,
        categories: List<Category>,
        onProgress: (done: Int, total: Int) -> Unit
    ): Result<List<CategorySuggestion>> {
        if (entries.isEmpty()) return Result.success(emptyList())

        if (localAiEngine.state.value !is AiEngineState.Ready) {
            return ruleBasedCategorizer.categorize(entries, categories, onProgress)
        }
        return try {
            Result.success(engineSession.withEngine(localAiEngine) { engine ->

                val suggestions = mutableListOf<CategorySuggestion>()
                for (chunk in entries.chunked(CHUNK_SIZE)) {
                    suggestions += categorizeChunk(engine, chunk, categories)
                    onProgress(suggestions.size, entries.size)
                }
                suggestions
            })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            engineSession.release()
            ruleBasedCategorizer.categorize(entries, categories, onProgress)
        }
    }

    private suspend fun categorizeChunk(
        engine: Engine,
        chunk: List<RawStatementEntry>,
        categories: List<Category>
    ): List<CategorySuggestion> {
        val prompt = buildPrompt(chunk, categories)

        var parsed = askSafely(engine, prompt)
        if (parsed == null) {
            val retryPrompt = "$prompt\n\n$STRICT_JSON_REMINDER"
            parsed = askSafely(engine, retryPrompt)
        }

        if (parsed == null) {
            // JSON malformado nas duas tentativas: fallback só para este lote.
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

    private suspend fun askSafely(engine: Engine, prompt: String): List<CategorizationResponseItem>? = try {
        askAndParse(engine, prompt)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun askAndParse(engine: Engine, prompt: String): List<CategorizationResponseItem>? {
        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
            samplerConfig = SamplerConfig(temperature = 0.2, topK = 40, topP = 0.9)
        )

        val rawResponse: String = engine.createConversation(conversationConfig).use { conversation: Conversation ->
            conversation.sendMessage(prompt).contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
        }

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

        const val SYSTEM_INSTRUCTION =
            "Você categoriza transações financeiras. Responda sempre em JSON válido, sem comentários."

        const val STRICT_JSON_REMINDER =
            "Responda apenas com um array JSON válido, sem nenhum texto antes ou depois, sem markdown."

        val responseJson = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
