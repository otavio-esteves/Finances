package br.com.otavioesteves.finances.data.ai

import br.com.otavioesteves.finances.domain.ai.AiEngineState
import br.com.otavioesteves.finances.domain.ai.LocalAiEngine
import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Reuses the native runtime; the policy checks cancellation between calls, retries and chunks. */
class LiteRtTransactionCategorizer(
    private val localAiEngine: LocalAiEngine,
    private val ruleBasedCategorizer: TransactionCategorizer,
    confidenceThreshold: Float = 0.5f,
    private val engineSession: LiteRtEngineSession
) : TransactionCategorizer {
    private val categorization = LocalTransactionCategorization(ruleBasedCategorizer, confidenceThreshold)

    override suspend fun categorize(
        entries: List<RawStatementEntry>,
        categories: List<Category>,
        onProgress: (done: Int, total: Int) -> Unit
    ): Result<List<CategorySuggestion>> {
        currentCoroutineContext().ensureActive()
        if (entries.isEmpty()) return Result.success(emptyList())
        if (localAiEngine.state.value !is AiEngineState.Ready) {
            return categorizeWithRules(entries, categories, onProgress)
        }
        return try {
            Result.success(engineSession.withEngine(localAiEngine) { engine ->
                categorization.categorize(entries, categories, onProgress) { prompt ->
                    val config = ConversationConfig(
                        systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
                        samplerConfig = SamplerConfig(temperature = 0.2, topK = 40, topP = 0.9)
                    )
                    engine.createConversation(config).use { conversation ->
                        conversation.sendMessage(prompt).contents.contents
                            .filterIsInstance<Content.Text>().joinToString("") { it.text }
                    }
                }
            })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            engineSession.release()
            categorizeWithRules(entries, categories, onProgress)
        }
    }

    private suspend fun categorizeWithRules(
        entries: List<RawStatementEntry>, categories: List<Category>, onProgress: (Int, Int) -> Unit
    ): Result<List<CategorySuggestion>> {
        currentCoroutineContext().ensureActive()
        val result = ruleBasedCategorizer.categorize(entries, categories, onProgress)
        currentCoroutineContext().ensureActive()
        return result
    }

    private companion object {
        const val SYSTEM_INSTRUCTION =
            "Você categoriza transações financeiras. Responda sempre em JSON válido, sem comentários."
    }
}
