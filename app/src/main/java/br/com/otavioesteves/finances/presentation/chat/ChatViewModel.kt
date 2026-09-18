package br.com.otavioesteves.finances.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.usecase.GetChatHistoryUseCase
import br.com.otavioesteves.finances.domain.usecase.SendChatMessageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val getChatHistory: GetChatHistoryUseCase,
    private val sendChatMessage: SendChatMessageUseCase
) : ViewModel() {

    private val draft = MutableStateFlow("")
    private val isSending = MutableStateFlow(false)

    val uiState: StateFlow<ChatUiState> = combine(
        getChatHistory(),
        draft,
        isSending
    ) { messages, draftValue, sending ->
        ChatUiState(
            messages = messages,
            draft = draftValue,
            isSending = sending
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState()
    )

    fun onDraftChange(value: String) {
        draft.value = value
    }

    fun sendMessage() {
        val message = draft.value.trim()
        if (message.isEmpty() || isSending.value) return

        draft.value = ""
        viewModelScope.launch {
            isSending.value = true
            try {
                sendChatMessage(message)
            } finally {
                isSending.value = false
            }
        }
    }
}
