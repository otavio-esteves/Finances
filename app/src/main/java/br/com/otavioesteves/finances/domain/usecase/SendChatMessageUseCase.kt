package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.ChatMessage
import br.com.otavioesteves.finances.domain.model.ChatRole
import br.com.otavioesteves.finances.domain.model.FinancialContext
import br.com.otavioesteves.finances.domain.repository.ChatRepository
import br.com.otavioesteves.finances.domain.repository.LocalAiRepository
import kotlinx.coroutines.flow.first

class SendChatMessageUseCase(
    private val localAiRepository: LocalAiRepository,
    private val chatRepository: ChatRepository,
    private val getMonthlyBalanceUseCase: GetMonthlyBalanceUseCase,
    private val getCategorySummariesUseCase: GetCategorySummariesUseCase,
    private val dateProvider: DateProvider
) {
    suspend operator fun invoke(message: String): ChatMessage {
        val period = dateProvider.getCurrentMonthPeriod()
        val context = FinancialContext(
            period = period,
            monthlyBalance = getMonthlyBalanceUseCase(period).first(),
            categorySummaries = getCategorySummariesUseCase(period).first()
        )

        chatRepository.addMessage(
            ChatMessage(
                id = 0,
                role = ChatRole.USER,
                content = message,
                createdAt = dateProvider.getCurrentDateTime()
            )
        )

        val reply = localAiRepository.sendMessage(message, context)
        chatRepository.addMessage(reply)
        return reply
    }
}
