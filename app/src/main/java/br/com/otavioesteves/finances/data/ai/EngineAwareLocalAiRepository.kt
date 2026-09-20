package br.com.otavioesteves.finances.data.ai

import br.com.otavioesteves.finances.domain.ai.AiEngineState
import br.com.otavioesteves.finances.domain.ai.LocalAiEngine
import br.com.otavioesteves.finances.domain.model.ChatMessage
import br.com.otavioesteves.finances.domain.model.FinancialContext
import br.com.otavioesteves.finances.domain.repository.LocalAiRepository

/**
 * Resolves which [LocalAiRepository] to use per call, based on the engine's
 * *current* state — mirrors [EngineAwareTransactionCategorizer]'s reasoning:
 * the user can import or remove a model at runtime from the Modelo de IA
 * screen, so this can't be decided once at DI wiring time.
 */
class EngineAwareLocalAiRepository(
    private val localAiEngine: LocalAiEngine,
    private val aiRepository: LocalAiRepository,
    private val ruleBasedRepository: LocalAiRepository
) : LocalAiRepository {

    override suspend fun sendMessage(message: String, context: FinancialContext): ChatMessage {
        val repository = if (localAiEngine.state.value is AiEngineState.Ready) {
            aiRepository
        } else {
            ruleBasedRepository
        }
        return repository.sendMessage(message, context)
    }
}
