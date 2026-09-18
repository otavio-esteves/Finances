package br.com.otavioesteves.finances.presentation.chat

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
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
import br.com.otavioesteves.finances.domain.usecase.GetCategorySummariesUseCase
import br.com.otavioesteves.finances.domain.usecase.GetChatHistoryUseCase
import br.com.otavioesteves.finances.domain.usecase.GetMonthlyBalanceUseCase
import br.com.otavioesteves.finances.domain.usecase.SendChatMessageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakePeriod = MonthPeriod(year = 2026, month = 1)
    private val fakeDateProvider = object : DateProvider {
        override fun getCurrentMonthPeriod(): MonthPeriod = fakePeriod
        override fun getCurrentDate(): LocalDate = LocalDate.of(2026, 1, 15)
        override fun getCurrentDateTime(): LocalDateTime = LocalDate.of(2026, 1, 15).atStartOfDay()
    }

    private class FakeTransactionsRepository : TransactionsRepository {
        override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getMonthlyBalance(period: MonthPeriod): Flow<Money> = flowOf(Money.Zero)
        override suspend fun addTransaction(transaction: Transaction) = Unit
        override suspend fun removeTransaction(transactionId: Long) = Unit
        override suspend fun updateTransaction(transaction: Transaction) = Unit
    }

    private class FakeCategoriesRepository : CategoriesRepository {
        override fun getCategories(): Flow<List<Category>> = flowOf(emptyList())
        override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> = flowOf(emptyList())
    }

    private class FakeChatRepository : ChatRepository {
        val history = MutableStateFlow<List<ChatMessage>>(emptyList())

        override fun getMessages(): Flow<List<ChatMessage>> = history
        override suspend fun addMessage(message: ChatMessage) {
            history.value = history.value + message
        }
    }

    private class FakeLocalAiRepository : LocalAiRepository {
        var callCount = 0

        override suspend fun suggestCategories(
            entries: List<RawStatementEntry>,
            knownCategories: List<Category>
        ): List<CategorySuggestion> = throw NotImplementedError("Not used in this test")

        override suspend fun sendMessage(message: String, context: FinancialContext): ChatMessage {
            callCount++
            return ChatMessage(
                id = callCount.toLong(),
                role = ChatRole.ASSISTANT,
                content = "resposta $callCount",
                createdAt = LocalDateTime.of(2026, 1, 15, 10, 0)
            )
        }
    }

    private lateinit var chatRepository: FakeChatRepository
    private lateinit var localAiRepository: FakeLocalAiRepository
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        chatRepository = FakeChatRepository()
        localAiRepository = FakeLocalAiRepository()

        val getChatHistory = GetChatHistoryUseCase(chatRepository)
        val sendChatMessage = SendChatMessageUseCase(
            localAiRepository,
            chatRepository,
            GetMonthlyBalanceUseCase(FakeTransactionsRepository()),
            GetCategorySummariesUseCase(FakeCategoriesRepository()),
            fakeDateProvider
        )

        viewModel = ChatViewModel(getChatHistory, sendChatMessage)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `viewModel starts empty and reflects persisted history`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.messages.isEmpty())
    }

    @Test
    fun `onDraftChange updates draft in uiState`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDraftChange("Quanto gastei em janeiro?")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Quanto gastei em janeiro?", viewModel.uiState.value.draft)
    }

    @Test
    fun `sendMessage clears draft and persists user message and reply`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onDraftChange("Quanto gastei em janeiro?")

        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.draft)
        assertEquals(2, viewModel.uiState.value.messages.size)
        assertEquals(ChatRole.USER, viewModel.uiState.value.messages[0].role)
        assertEquals("Quanto gastei em janeiro?", viewModel.uiState.value.messages[0].content)
        assertEquals(ChatRole.ASSISTANT, viewModel.uiState.value.messages[1].role)
        assertEquals(false, viewModel.uiState.value.isSending)
    }

    @Test
    fun `sendMessage ignores blank draft`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onDraftChange("   ")

        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, localAiRepository.callCount)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
    }
}
