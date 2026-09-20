package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.ChatMessage
import br.com.otavioesteves.finances.domain.model.FinancialContext

/**
 * Chat-only contract for now — categorization moved to
 * [br.com.otavioesteves.finances.domain.ai.TransactionCategorizer]. This
 * interface itself is transitional: Fase 5 replaces it with a streaming
 * `FinancialChat` contract once retrieval-augmented chat is wired.
 */
interface LocalAiRepository {
    suspend fun sendMessage(message: String, context: FinancialContext): ChatMessage
}
