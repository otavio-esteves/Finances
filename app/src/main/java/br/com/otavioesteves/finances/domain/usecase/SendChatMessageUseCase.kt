package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.ChatMessage
import br.com.otavioesteves.finances.domain.model.ChatRole
import br.com.otavioesteves.finances.domain.model.ChatSendRequest
import br.com.otavioesteves.finances.domain.repository.ChatRepository
import br.com.otavioesteves.finances.domain.repository.FinancialContextRepository
import br.com.otavioesteves.finances.domain.repository.LocalAiRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock

class SendChatMessageUseCase(
    private val localAiRepository: LocalAiRepository,
    private val chatRepository: ChatRepository,
    private val financialContextRepository: FinancialContextRepository,
    private val dateProvider: DateProvider,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend operator fun invoke(message: String): ChatMessage = invoke(ChatSendRequest(message))

    suspend operator fun invoke(request: ChatSendRequest): ChatMessage = request.mutex.withLock {
        withContext(workerDispatcher) {
            val revision = chatRepository.dataRevision
            if (request.dataRevision != revision) {
                request.dataRevision = revision
                request.userMessageSaved = false
                request.reply = null
                request.completed = false
            }
            if (request.completed) return@withContext requireNotNull(request.reply)
            if (!request.userMessageSaved) {
                // Keep the write and its acknowledgement together if the caller is cancelled after the commit.
                withContext(NonCancellable) {
                    chatRepository.addMessage(
                        ChatMessage(0, ChatRole.USER, request.message, dateProvider.getCurrentDateTime()), revision
                    )
                    request.userMessageSaved = true
                }
            }
            val reply = request.reply ?: createReply(request.message).also { request.reply = it }
            withContext(NonCancellable) {
                chatRepository.addMessage(reply, revision)
                request.completed = true
            }
            reply
        }
    }

    private suspend fun createReply(message: String): ChatMessage {
        val period = resolveChatPeriod(message, dateProvider.getCurrentMonthPeriod())
        val context = period?.let { financialContextRepository.getContext(it) }

        return if (context == null) ChatMessage(
            id = 0,
            role = ChatRole.ASSISTANT,
            content = "Consigo consultar um mês por vez. Informe o mês e o ano, por exemplo, " +
                "'fevereiro de ${dateProvider.getCurrentMonthPeriod().year}'. " +
                "Ainda não consigo responder sobre dias, intervalos ou períodos ambíguos.",
            createdAt = dateProvider.getCurrentDateTime()
        ) else answerFinancialQuestion(message, context)?.let { answer ->
            ChatMessage(
                id = 0,
                role = ChatRole.ASSISTANT,
                content = answer,
                createdAt = dateProvider.getCurrentDateTime()
            )
        } ?: localAiRepository.sendMessage(message, context)
    }
}
