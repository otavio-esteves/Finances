package br.com.otavioesteves.finances.presentation.chat

import br.com.otavioesteves.finances.domain.model.ChatMessage

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false
)

data class ChatHistoryPage(
    val messages: List<ChatMessage> = emptyList(),
    val hasOlder: Boolean = false
)
