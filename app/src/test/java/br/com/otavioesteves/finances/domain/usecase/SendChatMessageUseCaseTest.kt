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
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.ChatRepository
import br.com.otavioesteves.finances.domain.repository.LocalAiRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            dateProvider,
            FakeCategoriesRepository(summaries)
        )

        val result = useCase("Faça um resumo do mês.")

        assertEquals(expectedReply, result)
        assertEquals(
            FinancialContext(period = period, monthlyBalance = balance, categorySummaries = summaries),
            localAiRepository.lastContext
        )
        assertEquals("Faça um resumo do mês.", localAiRepository.lastMessage)
        assertEquals(2, chatRepository.savedMessages.size)
        assertEquals(ChatRole.USER, chatRepository.savedMessages[0].role)
        assertEquals("Faça um resumo do mês.", chatRepository.savedMessages[0].content)
        assertEquals(expectedReply, chatRepository.savedMessages[1])
    }

    @Test
    fun invoke_usesRequestedMonthInsteadOfCurrentMonth() = runBlocking {
        val period = MonthPeriod(2026, 1)
        val ai = FakeLocalAiRepository(ChatMessage(1, ChatRole.ASSISTANT, "Fevereiro", LocalDateTime.now()))
        val chat = FakeChatRepository()
        val transactions = FakeTransactionsRepository(Money.fromCents(100_000))
        val categories = FakeCategoriesRepository(emptyList())
        val useCase = SendChatMessageUseCase(
            ai, chat,
            GetMonthlyBalanceUseCase(transactions),
            GetCategorySummariesUseCase(categories),
            FakeDateProvider(period),
            categories
        )

        val reply = useCase("Mostre as categorias em fevereiro de 2026.")

        assertEquals("Fevereiro", reply.content)
        assertEquals(MonthPeriod(2026, 2), ai.lastContext?.period)
        assertEquals(MonthPeriod(2026, 2), transactions.requestedPeriod)
        assertEquals(MonthPeriod(2026, 2), categories.requestedPeriod)
        assertEquals(2, chat.savedMessages.size)
    }

    @Test
    fun invoke_refusesMerchantQuestionWithoutTransactionDetails() = runBlocking {
        val period = MonthPeriod(2026, 2)
        val ai = FakeLocalAiRepository(ChatMessage(1, ChatRole.ASSISTANT, "inventado", LocalDateTime.now()))
        val summaries = listOf(CategorySummary(Category(1, "Alimentação", CategoryType.EXPENSE), Money.fromCents(25_000)))
        val useCase = SendChatMessageUseCase(
            ai, FakeChatRepository(),
            GetMonthlyBalanceUseCase(FakeTransactionsRepository(Money.Zero)),
            GetCategorySummariesUseCase(FakeCategoriesRepository(summaries)),
            FakeDateProvider(period),
            FakeCategoriesRepository(summaries)
        )

        val answer = useCase("Quanto gastei com Mercado em fevereiro de 2026?")

        assertTrue(answer.content.contains("não consigo calcular"))
        assertEquals(null, ai.lastMessage)
    }

    @Test
    fun invoke_calculatesCategoryExpenseWithoutAi() = runBlocking {
        val period = MonthPeriod(2026, 2)
        val ai = FakeLocalAiRepository(ChatMessage(1, ChatRole.ASSISTANT, "inventado", LocalDateTime.now()))
        val summaries = listOf(CategorySummary(Category(1, "Alimentação", CategoryType.EXPENSE), Money.fromCents(25_000)))
        val useCase = SendChatMessageUseCase(
            ai, FakeChatRepository(),
            GetMonthlyBalanceUseCase(FakeTransactionsRepository(Money.Zero)),
            GetCategorySummariesUseCase(FakeCategoriesRepository(summaries)),
            FakeDateProvider(period),
            FakeCategoriesRepository(summaries)
        )

        val answer = useCase("Quanto gastei com alimentação em fevereiro de 2026?")

        assertTrue(answer.content.contains("250,00"))
        assertTrue(answer.content.contains("Fevereiro de 2026"))
        assertEquals(null, ai.lastMessage)
    }

    @Test
    fun invoke_refusesUnsupportedPeriodWithoutSendingFiguresToAi() = runBlocking {
        val period = MonthPeriod(2026, 1)
        val ai = FakeLocalAiRepository(ChatMessage(1, ChatRole.ASSISTANT, "incorreto", LocalDateTime.now()))
        val chat = FakeChatRepository()
        val useCase = SendChatMessageUseCase(
            ai, chat,
            GetMonthlyBalanceUseCase(FakeTransactionsRepository(Money.fromCents(100_000))),
            GetCategorySummariesUseCase(FakeCategoriesRepository(emptyList())),
            FakeDateProvider(period),
            FakeCategoriesRepository(emptyList())
        )

        val reply = useCase("Quanto gastei em janeiro e fevereiro de 2026?")

        assertTrue(reply.content.contains("um mês por vez"))
        assertEquals(null, ai.lastMessage)
        assertEquals(2, chat.savedMessages.size)
    }

    @Test
    fun invoke_answersZeroForRegisteredCategoryWithoutMovementAndDoesNotCallAi() = runBlocking {
        val period = MonthPeriod(2026, 2)
        val ai = FakeLocalAiRepository(ChatMessage(1, ChatRole.ASSISTANT, "inventado", LocalDateTime.now()))
        val categories = FakeCategoriesRepository(emptyList(), listOf(Category(3, "Saúde", CategoryType.EXPENSE)))
        val chat = FakeChatRepository()
        val useCase = SendChatMessageUseCase(
            ai, chat, GetMonthlyBalanceUseCase(FakeTransactionsRepository(Money.Zero)),
            GetCategorySummariesUseCase(categories), FakeDateProvider(period), categories
        )

        val reply = useCase("Quanto gastei com saúde em fevereiro de 2026?")

        assertTrue(reply.content.contains("0,00"))
        assertTrue(reply.content.contains("Saúde"))
        assertEquals(null, ai.lastMessage)
        assertEquals(2, chat.savedMessages.size)
    }

    @Test
    fun resolveChatPeriod_acceptsSingleMonthAndRejectsUnsupportedPeriods() {
        val period = MonthPeriod(2026, 1)
        assertEquals(false, canAnswerWithCurrentMonth("Quanto gastei no mês passado?", period))
        assertEquals(false, canAnswerWithCurrentMonth("Quanto gastei no mês anterior?", period))
        assertEquals(false, canAnswerWithCurrentMonth("Quanto gastarei no mês seguinte?", period))
        assertEquals(false, canAnswerWithCurrentMonth("Quanto vou gastar no mês que vem?", period))
        assertEquals(false, canAnswerWithCurrentMonth("Quanto gastei hoje?", period))
        assertEquals(false, canAnswerWithCurrentMonth("Quanto gastei na última semana?", period))
        assertEquals(false, canAnswerWithCurrentMonth("Quanto gastei em janeiro de 2025?", period))
        assertEquals(MonthPeriod(2026, 2), resolveChatPeriod("Quanto gastei em fev/2026?", period))
        assertEquals(MonthPeriod(2026, 2), resolveChatPeriod("Quanto gastei em 02/2026?", period))
        assertEquals(MonthPeriod(2026, 2), resolveChatPeriod("Quanto gastei em 2026-02?", period))
        assertEquals(MonthPeriod(2025, 12), resolveChatPeriod("Quanto gastei em dezembro?", period))
        assertEquals(null, resolveChatPeriod("Quanto gastei em janeiro e fevereiro de 2026?", period))
        assertEquals(null, resolveChatPeriod("Quanto gastei em 2025 e 2026?", period))
        assertEquals(false, canAnswerWithCurrentMonth("Quanto gastei em 05/01/2026?", period))
        assertEquals(true, canAnswerWithCurrentMonth("Quanto gastei em janeiro de 2026?", period))
    }

    private class FakeDateProvider(private val period: MonthPeriod) : DateProvider {
        override fun getCurrentMonthPeriod(): MonthPeriod = period
        override fun getCurrentDate(): java.time.LocalDate = period.toYearMonth().atDay(1)
        override fun getCurrentDateTime(): LocalDateTime = period.toYearMonth().atDay(1).atStartOfDay()
    }

    private class FakeTransactionsRepository(private val balance: Money) : TransactionsRepository {
        var requestedPeriod: MonthPeriod? = null
        override fun getTransaction(id: Long): Flow<Transaction?> = flowOf(null)
        override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getMonthlyBalance(period: MonthPeriod): Flow<Money> {
            requestedPeriod = period
            return flowOf(balance)
        }
        override suspend fun addTransaction(transaction: Transaction) = Unit
        override suspend fun removeTransaction(transactionId: Long) = Unit
        override suspend fun updateTransaction(transaction: Transaction) = Unit
    }

    private class FakeCategoriesRepository(
        private val summaries: List<CategorySummary>,
        private val categories: List<Category> = summaries.map { it.category }
    ) : CategoriesRepository {
        var requestedPeriod: MonthPeriod? = null
        override fun getCategories(): Flow<List<Category>> = flowOf(categories)
        override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> {
            requestedPeriod = period
            return flowOf(summaries)
        }
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

        override suspend fun sendMessage(message: String, context: FinancialContext): ChatMessage {
            lastMessage = message
            lastContext = context
            return reply
        }
    }
}
