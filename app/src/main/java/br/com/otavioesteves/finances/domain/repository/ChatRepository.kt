package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getMessages(): Flow<List<ChatMessage>>
    suspend fun addMessage(message: ChatMessage)
}
