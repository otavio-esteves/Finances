package br.com.otavioesteves.finances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import br.com.otavioesteves.finances.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow
import br.com.otavioesteves.finances.domain.model.requirePersistableText

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    suspend fun getAllMessagesForBackup(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM (SELECT * FROM chat_messages ORDER BY createdAt DESC, id DESC LIMIT :limit) ORDER BY createdAt ASC, id ASC")
    fun getRecentMessages(limit: Int): Flow<List<ChatMessageEntity>>

    suspend fun insertMessage(message: ChatMessageEntity): Long {
        requirePersistableText(message.content, "mensagem")
        return insertMessageUnchecked(message)
    }

    suspend fun insertMessages(messages: List<ChatMessageEntity>) {
        messages.forEach { requirePersistableText(it.content, "mensagem") }
        insertMessagesUnchecked(messages)
    }

    @Insert
    suspend fun insertMessageUnchecked(message: ChatMessageEntity): Long

    @Insert
    suspend fun insertMessagesUnchecked(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}
