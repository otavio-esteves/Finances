package br.com.otavioesteves.finances.data.repository

import br.com.otavioesteves.finances.data.local.dao.ChatMessageDao
import br.com.otavioesteves.finances.data.local.mapper.toDomain
import br.com.otavioesteves.finances.data.local.mapper.toEntity
import br.com.otavioesteves.finances.domain.model.ChatMessage
import br.com.otavioesteves.finances.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomChatRepository(
    private val chatMessageDao: ChatMessageDao
) : ChatRepository {

    override fun getMessages(): Flow<List<ChatMessage>> {
        return chatMessageDao.getAllMessages().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun addMessage(message: ChatMessage) {
        chatMessageDao.insertMessage(message.toEntity())
    }
}
