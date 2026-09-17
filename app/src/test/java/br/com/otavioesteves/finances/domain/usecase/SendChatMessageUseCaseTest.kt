package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.ChatMessage
import br.com.otavioesteves.finances.domain.model.ChatRole
import br.com.otavioesteves.finances.domain.model.FinancialContext
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.ChatRepository
import br.com.otavioesteves.finances.domain.repository.LocalAiRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class SendChatMessageUseCaseTest {

    @Test
    fun invoke_buildsFinancialContextFromExistingUseCasesAndDelegatesToAi() = runBlocking {
        val period = MonthPeriod(year = 2026, month = 1)
        val balance = Money.fromCents(150_000)
        val summaries = listOf(
            CategorySummary(
                category = Category(id = 1L, name = "Mercado", type = CategoryType.EXPENSE),
                totalAmount = Money.fromCents(25_000)
            )
        )
        val dateProvider = FakeDateProvider(period)
        val getMonthlyBalanceUseCase = GetMonthlyBalanceUseCase(FakeTransactionsRepository(balance))
        val getCategorySummariesUseCase = GetCategorySummariesUseCase(FakeCategoriesRepository(summaries))
        val expectedReply = ChatMessage(
            id = 1L,
            role = ChatRole.ASSISTANT,
            content = "Você gastou R$ 250,00 com Mercado.",
            createdAt = LocalDateTime.of(2026, 1, 15, 10, 0)
        )
        val localAiRepository = FakeLocalAiRepository(expectedReply)
        val chatRepository = FakeChatRepository()

        val useCase = SendChatMessageUseCase(
            localAiRepository,
            chatRepository,
            getMonthlyBalanceUseCase,
            getCategorySummariesUseCase,
            dateProvider
        )

        val result = useCase("Quanto gastei com mercado?")

        assertEquals(expectedReply, result)
        assertEquals(
            FinancialContext(period = period, monthlyBalance = balance, categorySummaries = summaries),
            localAiRepository.lastContext
        )
        assertEquals("Quanto gastei com mercado?", localAiRepository.lastMessage)
        assertEquals(2, chatRepository.savedMessages.size)
        assertEquals(ChatRole.USER, chatRepository.savedMessages[0].role)
        assertEquals("Quanto gastei com mercado?", chatRepository.savedMessages[0].content)
        assertEquals(expectedReply, chatRepository.savedMessages[1])
    }

    private class FakeDateProvider(private val period: MonthPeriod) : DateProvider {
        override fun getCurrentMonthPeriod(): MonthPeriod = period
        override fun getCurrentDate(): java.time.LocalDate = period.toYearMonth().atDay(1)
        override fun getCurrentDateTime(): LocalDateTime = period.toYearMonth().atDay(1).atStartOfDay()
    }

    private class FakeTransactionsRepository(private val balance: Money) : TransactionsRepository {
        override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getMonthlyBalance(period: MonthPeriod): Flow<Money> = flowOf(balance)
        override suspend fun addTransaction(transaction: Transaction) = Unit
        override suspend fun removeTransaction(transactionId: Long) = Unit
        override suspend fun updateTransaction(transaction: Transaction) = Unit
    }

    private class FakeCategoriesRepository(
        private val summaries: List<CategorySummary>
    ) : CategoriesRepository {
        override fun getCategories(): Flow<List<Category>> = flowOf(emptyList())
        override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> = flowOf(summaries)
    }

    private class FakeChatRepository : ChatRepository {
        val savedMessages = mutableListOf<ChatMessage>()

        override fun getMessages(): Flow<List<ChatMessage>> = flowOf(savedMessages)
        override suspend fun addMessage(message: ChatMessage) {
            savedMessages += message
        }
    }

    private class FakeLocalAiRepository(
        private val reply: ChatMessage
    ) : LocalAiRepository {
        var lastMessage: String? = null
        var lastContext: FinancialContext? = null

        override suspend fun suggestCategories(
            entries: List<RawStatementEntry>,
            knownCategories: List<Category>
        ): List<CategorySuggestion> {
            throw NotImplementedError("Not used in this test")
        }

        override suspend fun sendMessage(message: String, context: FinancialContext): ChatMessage {
            lastMessage = message
            lastContext = context
            return reply
        }
    }
}
