package br.com.otavioesteves.finances.data.ai

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.ai.AiEngineState
import br.com.otavioesteves.finances.domain.ai.LocalAiEngine
import br.com.otavioesteves.finances.domain.model.ChatMessage
import br.com.otavioesteves.finances.domain.model.ChatRole
import br.com.otavioesteves.finances.domain.model.FinancialContext
import br.com.otavioesteves.finances.domain.repository.LocalAiRepository
import br.com.otavioesteves.finances.utils.MoneyFormatter
import br.com.otavioesteves.finances.utils.formatMonthPeriod
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [LocalAiRepository] backed by a real LiteRT-LM engine — a scoped-down
 * stand-in for the `FinancialChat`/RAG contract docs/PLANO_MOTOR_IA_LOCAL.md
 * § 3/5 plans for Fase 5: free-text, not streamed, and grounded only in the
 * current month's balance + category totals ([FinancialContext]), not a
 * per-transaction retrieval. Built to validate the imported engine can hold a
 * conversation at all. Every `com.google.ai.edge.litertlm.*` call is isolated
 * here, same reasoning as [LiteRtTransactionCategorizer] — see its class doc
 * for the unverified-API caveat (the `.toString()` on the reply).
 *
 * Creates a fresh [Engine] per message (same pattern as
 * [LiteRtTransactionCategorizer], which does it per import batch) — there is
 * no warm/cached engine across turns yet, so every message reloads the whole
 * model. Callers should route through [EngineAwareLocalAiRepository] rather
 * than use this directly — it assumes the engine is [AiEngineState.Ready].
 */
class LiteRtLocalAiRepository(
    private val localAiEngine: LocalAiEngine,
    private val ruleBasedRepository: LocalAiRepository,
    private val dateProvider: DateProvider
) : LocalAiRepository {

    override suspend fun sendMessage(message: String, context: FinancialContext): ChatMessage {
        val ready = localAiEngine.state.value as? AiEngineState.Ready
            ?: return ruleBasedRepository.sendMessage(message, context)

        return withContext(Dispatchers.IO) {
            runCatching {
                val engineConfig = EngineConfig(modelPath = ready.model.path)
                Engine(engineConfig).use { engine ->
                    engine.initialize()

                    val conversationConfig = ConversationConfig(
                        systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
                        samplerConfig = SamplerConfig(temperature = 0.3, topK = 40, topP = 0.9)
                    )
                    // NOTA DE INCERTEZA — ver o comentário de classe: assumindo que
                    // toString() dá o texto da resposta, como o exemplo oficial sugere.
                    val reply = engine.createConversation(conversationConfig).use { conversation: Conversation ->
                        conversation.sendMessage(buildPrompt(message, context)).toString()
                    }

                    ChatMessage(
                        id = 0,
                        role = ChatRole.ASSISTANT,
                        content = reply.trim(),
                        createdAt = dateProvider.getCurrentDateTime()
                    )
                }
            }.recoverCatching {
                // Motor não respondeu (bundle incompatível, erro de inferência etc.) —
                // cai pro fallback de regras para esta mensagem.
                ruleBasedRepository.sendMessage(message, context)
            }.getOrThrow()
        }
    }

    private fun buildPrompt(message: String, context: FinancialContext): String {
        val categoriesText = if (context.categorySummaries.isEmpty()) {
            "Nenhuma transação registrada neste mês."
        } else {
            context.categorySummaries.joinToString("\n") { summary ->
                "- ${summary.category.name}: ${MoneyFormatter.format(summary.totalAmount)}"
            }
        }

        return """
            Dados financeiros de ${formatMonthPeriod(context.period)}:
            Saldo do mês: ${MoneyFormatter.format(context.monthlyBalance)}
            Gastos e receitas por categoria:
            $categoriesText

            Pergunta do usuário: $message

            Responda em português, de forma direta, usando apenas os dados acima.
            Se a pergunta não puder ser respondida com esses dados, diga isso
            claramente em vez de inventar um número.
        """.trimIndent()
    }

    private companion object {
        const val SYSTEM_INSTRUCTION =
            "Você é um assistente financeiro local. Responda sempre em português, " +
                "de forma breve, e nunca invente valores que não estejam nos dados fornecidos."
    }
}
