package br.com.otavioesteves.finances.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.model.ChatMessage
import br.com.otavioesteves.finances.domain.usecase.GetChatHistoryUseCase
import br.com.otavioesteves.finances.domain.usecase.SendChatMessageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val getChatHistory: GetChatHistoryUseCase,
    private val sendChatMessage: SendChatMessageUseCase
) : ViewModel() {

    private val historyLimit = MutableStateFlow(HISTORY_PAGE_SIZE)

    private val draft = MutableStateFlow("")
    val draftState: StateFlow<String> = draft.asStateFlow()
    private val isSending = MutableStateFlow(false)
    val isSendingState: StateFlow<Boolean> = isSending.asStateFlow()

    val historyPage: StateFlow<ChatHistoryPage> = historyLimit.flatMapLatest { limit ->
        getChatHistory(limit + 1).map { loaded ->
            val hasOlder = loaded.size > limit
            ChatHistoryPage(
                messages = if (hasOlder) loaded.drop(1) else loaded,
                hasOlder = hasOlder
            )
        }
    }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChatHistoryPage()
        )

    val messages: StateFlow<List<ChatMessage>> = historyPage.map { it.messages }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val uiState: StateFlow<ChatUiState> = combine(
        messages,
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

    fun loadOlderMessages() {
        if (historyPage.value.hasOlder) historyLimit.value += HISTORY_PAGE_SIZE
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

    private companion object {
        const val HISTORY_PAGE_SIZE = 100
    }
}
