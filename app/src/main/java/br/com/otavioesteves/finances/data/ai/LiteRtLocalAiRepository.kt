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
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException

/**
 * [LocalAiRepository] backed by a real LiteRT-LM engine — a scoped-down
 * stand-in for the `FinancialChat`/RAG contract planned in
 * docs/PLANO_MOTOR_IA_LOCAL.md, "Chat que consulta as finanças": free-text,
 * not streamed, and grounded only in the
 * selected month's balance + category totals ([FinancialContext]), not a
 * per-transaction retrieval. Built to validate the imported engine can hold a
 * conversation at all. Every `com.google.ai.edge.litertlm.*` call is isolated
 * here, same reasoning as [LiteRtTransactionCategorizer]. Runtime behavior
 * still needs validation with a real bundle on a device.
 *
 * Shares a serialized runtime with categorization. Each question still has its
 * own conversation and receives only the selected monthly financial context.
 */
class LiteRtLocalAiRepository(
    private val localAiEngine: LocalAiEngine,
    private val ruleBasedRepository: LocalAiRepository,
    private val dateProvider: DateProvider,
    private val engineSession: LiteRtEngineSession
) : LocalAiRepository {

    override suspend fun sendMessage(message: String, context: FinancialContext): ChatMessage {
        if (localAiEngine.state.value !is AiEngineState.Ready) {
            return ruleBasedRepository.sendMessage(message, context)
        }
        return try {
            engineSession.withEngine(localAiEngine) { engine ->

                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
                    samplerConfig = SamplerConfig(temperature = 0.3, topK = 40, topP = 0.9)
                )
                val reply = engine.createConversation(conversationConfig).use { conversation: Conversation ->
                    conversation.sendMessage(buildPrompt(message, context)).contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                }
                require(reply.isNotBlank()) { "O modelo não retornou texto." }

                ChatMessage(
                    id = 0,
                    role = ChatRole.ASSISTANT,
                    content = reply.trim(),
                    createdAt = dateProvider.getCurrentDateTime()
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            engineSession.release()
            ruleBasedRepository.sendMessage(message, context)
        }
    }

    private fun buildPrompt(message: String, context: FinancialContext): String {
        val categoriesText = if (context.categorySummaries.isEmpty()) {
            "Nenhuma transação registrada neste período."
        } else {
            context.categorySummaries.joinToString("\n") { summary ->
                "- ${summary.category.name}: ${MoneyFormatter.format(summary.totalAmount)}"
            }
        }

        return """
            Dados financeiros de ${formatMonthPeriod(context.period)} de ${context.period.year}:
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
