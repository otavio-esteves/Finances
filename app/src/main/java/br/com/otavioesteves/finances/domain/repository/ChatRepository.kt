package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ChatRepository {
    /** Changes whenever a restore replaces the dataset, even while the same ViewModel remains alive. */
    val dataRevision: Long get() = 0L
    fun getMessages(): Flow<List<ChatMessage>>
    fun getRecentMessages(limit: Int): Flow<List<ChatMessage>> =
        getMessages().map { it.takeLast(limit) }
    suspend fun addMessage(message: ChatMessage)

    /** Implementations with mutable datasets must check the revision and insert atomically. */
    suspend fun addMessage(message: ChatMessage, expectedRevision: Long) {
        check(dataRevision == expectedRevision) { "Os dados foram restaurados. Envie a pergunta novamente." }
        addMessage(message)
    }
}
