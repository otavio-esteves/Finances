package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ChatRepository {
    fun getMessages(): Flow<List<ChatMessage>>
    fun getRecentMessages(limit: Int): Flow<List<ChatMessage>> =
        getMessages().map { it.takeLast(limit) }
    suspend fun addMessage(message: ChatMessage)
}
