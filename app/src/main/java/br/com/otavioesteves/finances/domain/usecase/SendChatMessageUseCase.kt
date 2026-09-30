package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.ChatMessage
import br.com.otavioesteves.finances.domain.model.ChatRole
import br.com.otavioesteves.finances.domain.model.FinancialContext
import br.com.otavioesteves.finances.domain.repository.ChatRepository
import br.com.otavioesteves.finances.domain.repository.LocalAiRepository
import kotlinx.coroutines.flow.first
import br.com.otavioesteves.finances.utils.formatMonthPeriod

class SendChatMessageUseCase(
    private val localAiRepository: LocalAiRepository,
    private val chatRepository: ChatRepository,
    private val getMonthlyBalanceUseCase: GetMonthlyBalanceUseCase,
    private val getCategorySummariesUseCase: GetCategorySummariesUseCase,
    private val dateProvider: DateProvider
) {
    suspend operator fun invoke(message: String): ChatMessage {
        val period = dateProvider.getCurrentMonthPeriod()
        val currentMonthQuestion = canAnswerWithCurrentMonth(message, period)
        val context = if (currentMonthQuestion) FinancialContext(
            period = period,
            monthlyBalance = getMonthlyBalanceUseCase(period).first(),
            categorySummaries = getCategorySummariesUseCase(period).first()
        ) else null

        chatRepository.addMessage(
            ChatMessage(
                id = 0,
                role = ChatRole.USER,
                content = message,
                createdAt = dateProvider.getCurrentDateTime()
            )
        )

        val reply = if (context == null) ChatMessage(
            id = 0,
            role = ChatRole.ASSISTANT,
            content = "No momento, só consigo consultar o resumo de ${formatMonthPeriod(period)} de ${period.year}. " +
                "Ainda não consigo responder perguntas sobre outros períodos ou dias específicos.",
            createdAt = dateProvider.getCurrentDateTime()
        ) else localAiRepository.sendMessage(message, context)
        chatRepository.addMessage(reply)
        return reply
    }
}
