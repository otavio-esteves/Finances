package br.com.otavioesteves.finances.data.repository

import br.com.otavioesteves.finances.data.local.AppDatabase
import androidx.room.withTransaction
import br.com.otavioesteves.finances.data.local.mapper.toDomain
import br.com.otavioesteves.finances.data.local.mapper.toEntity
import br.com.otavioesteves.finances.domain.model.ChatMessage
import br.com.otavioesteves.finances.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

class RoomChatRepository(
    private val database: AppDatabase
) : ChatRepository {
    private val chatMessageDao = database.chatMessageDao()
    override val dataRevision: Long get() = database.dataRevision

    override fun getMessages(): Flow<List<ChatMessage>> {
        return chatMessageDao.getAllMessages().distinctUntilChanged()
            .map { entities -> entities.map { it.toDomain() } }.flowOn(Dispatchers.Default)
    }

    override fun getRecentMessages(limit: Int): Flow<List<ChatMessage>> {
        require(limit > 0)
        return chatMessageDao.getRecentMessages(limit).distinctUntilChanged()
            .map { entities -> entities.map { it.toDomain() } }.flowOn(Dispatchers.Default)
    }

    override suspend fun addMessage(message: ChatMessage) {
        chatMessageDao.insertMessage(message.toEntity())
    }

    override suspend fun addMessage(message: ChatMessage, expectedRevision: Long) {
        database.withTransaction {
            check(dataRevision == expectedRevision) { "Os dados foram restaurados. Envie a pergunta novamente." }
            chatMessageDao.insertMessage(message.toEntity())
        }
    }
}
