package br.com.otavioesteves.finances.domain.model

import kotlinx.coroutines.sync.Mutex

/** One send operation, retained for retries without merging separate questions with identical text. */
class ChatSendRequest(val message: String) {
    init { require(message.isNotBlank()) }

    internal val mutex = Mutex()
    internal var dataRevision: Long? = null
    internal var userMessageSaved = false
    internal var reply: ChatMessage? = null
    internal var completed = false
}
