package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.ChatMessage
import br.com.otavioesteves.finances.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class GetChatHistoryUseCase(
    private val repository: ChatRepository
) {
    operator fun invoke(limit: Int? = null): Flow<List<ChatMessage>> =
        if (limit == null) repository.getMessages() else repository.getRecentMessages(limit)
}
