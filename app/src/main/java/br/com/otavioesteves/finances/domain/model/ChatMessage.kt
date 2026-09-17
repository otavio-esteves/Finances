package br.com.otavioesteves.finances.domain.model

import java.time.LocalDateTime

enum class ChatRole {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val content: String,
    val createdAt: LocalDateTime
) {
    init {
        require(id >= 0) { "id must be positive or zero" }
        require(content.isNotBlank()) { "content must not be blank" }
    }
}
