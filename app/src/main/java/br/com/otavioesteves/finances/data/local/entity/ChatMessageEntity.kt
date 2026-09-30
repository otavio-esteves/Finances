package br.com.otavioesteves.finances.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import br.com.otavioesteves.finances.domain.model.ChatRole
import java.time.LocalDateTime

@Entity(tableName = "chat_messages", indices = [Index(value = ["createdAt", "id"])])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: ChatRole,
    val content: String,
    val createdAt: LocalDateTime
)
