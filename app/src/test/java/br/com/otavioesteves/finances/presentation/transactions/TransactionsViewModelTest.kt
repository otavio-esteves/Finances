package br.com.otavioesteves.finances.presentation.transactions

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.usecase.DeleteTransactionUseCase
import br.com.otavioesteves.finances.domain.usecase.GetTransactionsByMonthUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val fakeMonthPeriod = MonthPeriod(year = 2025, month = 12)
    private val fakeDateProvider = object : DateProvider {
        override fun getCurrentMonthPeriod(): MonthPeriod = fakeMonthPeriod
        override fun getCurrentDate(): LocalDate = LocalDate.of(2025, 12, 1)
        override fun getCurrentDateTime(): java.time.LocalDateTime = LocalDate.of(2025, 12, 1).atStartOfDay()
    }

    private class FakeTransactionsRepository : TransactionsRepository {
        override fun getTransaction(id: Long): Flow<Transaction?> = flowOf(null)
        var deletedId: Long? = null
        var shouldFail = false
        var transactions: List<Transaction> = emptyList()
        val monthlyFlows = mutableMapOf<MonthPeriod, Flow<List<Transaction>>>()

        override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> = monthlyFlows[period] ?: flowOf(transactions)
        override fun getMonthlyBalance(period: MonthPeriod): Flow<Money> = flowOf(Money.Zero)
        override suspend fun addTransaction(transaction: Transaction) {}
        override suspend fun removeTransaction(transactionId: Long) {
            if (shouldFail) throw Exception("DB Error")
            deletedId = transactionId
        }
        override suspend fun updateTransaction(transaction: Transaction) {}
    }

    private class FakeCategoriesRepository : CategoriesRepository {
        override fun getCategories(): Flow<List<Category>> = flowOf(emptyList())
        override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> = flowOf(emptyList())
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onDeleteRequest updates uiState with transaction to delete`() = runTest {
        val viewModel = createViewModel(FakeTransactionsRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        val transaction = createFakeTransaction(id = 1)
        
        viewModel.onDeleteRequest(transaction)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(transaction, viewModel.uiState.value.transactionToDelete)
    }

    @Test
    fun `onDeleteCancel clears transaction to delete`() = runTest {
        val viewModel = createViewModel(FakeTransactionsRepository())
        viewModel.onDeleteRequest(createFakeTransaction(id = 1))
        
        viewModel.onDeleteCancel()
        
        assertNull(viewModel.uiState.value.transactionToDelete)
    }

    @Test
    fun `onDeleteConfirm calls repository and clears state`() = runTest {
        val repository = FakeTransactionsRepository()
        val viewModel = createViewModel(repository)
        val transaction = createFakeTransaction(id = 123)
        
        viewModel.onDeleteRequest(transaction)
        viewModel.onDeleteConfirm()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(123L, repository.deletedId)
        assertNull(viewModel.uiState.value.transactionToDelete)
    }

    @Test
    fun `onDeleteConfirm failure updates error state`() = runTest {
        val repository = FakeTransactionsRepository().apply { shouldFail = true }
        val viewModel = createViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        
        viewModel.onDeleteRequest(createFakeTransaction(id = 1))
        viewModel.onDeleteConfirm()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertNotNull(viewModel.uiState.value.error)
        assertEquals("Erro ao excluir transação", viewModel.uiState.value.error)
    }

    @Test
    fun `onMonthSelected switches the displayed month`() = runTest {
        val viewModel = createViewModel(FakeTransactionsRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val previousMonth = fakeMonthPeriod.previousMonth()
        viewModel.onMonthSelected(previousMonth)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(previousMonth, viewModel.uiState.value.monthPeriod)
        assertEquals(previousMonth, viewModel.selectedMonthPeriod.value)
    }

    @Test
    fun `history rows receive formatted text before composition`() = runTest {
        val transaction = createFakeTransaction(id = 7)
        val repository = FakeTransactionsRepository().apply { transactions = listOf(transaction) }
        val viewModel = createViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        testDispatcher.scheduler.advanceUntilIdle()

        val item = viewModel.uiState.value.transactions.single()
        assertEquals("R\$ 1,00", item.formattedAmount)
        assertEquals(br.com.otavioesteves.finances.utils.DateFormatter.format(transaction.date), item.formattedDate)
    }

    @Test
    fun `switching months never labels old rows with the new period while its query is pending`() = runTest {
        val previousMonth = fakeMonthPeriod.previousMonth()
        val pendingRows = MutableSharedFlow<List<Transaction>>()
        val repository = FakeTransactionsRepository().apply {
            transactions = listOf(createFakeTransaction(1))
            monthlyFlows[previousMonth] = pendingRows
        }
        val viewModel = createViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.transactions.size)

        viewModel.onMonthSelected(previousMonth)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(previousMonth, viewModel.uiState.value.monthPeriod)
        org.junit.Assert.assertTrue(viewModel.uiState.value.isLoading)
        org.junit.Assert.assertTrue(viewModel.uiState.value.transactions.isEmpty())

        pendingRows.emit(listOf(createFakeTransaction(2)))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(previousMonth, viewModel.uiState.value.monthPeriod)
        assertEquals(2L, viewModel.uiState.value.transactions.single().transaction.id)
    }

    @Test
    fun `returning after collectors stop keeps rows and formatting while the same month refreshes`() = runTest {
        val transaction = createFakeTransaction(7)
        val repository = FakeTransactionsRepository().apply { transactions = listOf(transaction) }
        val viewModel = createViewModel(repository)
        val states = mutableListOf<TransactionsUiState>()
        val firstCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states += it }
        }
        testDispatcher.scheduler.runCurrent()
        val originalItem = viewModel.uiState.value.transactions.single()
        firstCollector.cancel()
        testDispatcher.scheduler.advanceTimeBy(5_001)
        testDispatcher.scheduler.runCurrent()

        val pendingRefresh = MutableSharedFlow<List<Transaction>>()
        repository.monthlyFlows[fakeMonthPeriod] = pendingRefresh
        states.clear()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states += it }
        }
        testDispatcher.scheduler.runCurrent()
        org.junit.Assert.assertTrue(states.none { it.isLoading })
        org.junit.Assert.assertSame(originalItem, viewModel.uiState.value.transactions.single())
        pendingRefresh.emit(listOf(transaction))
        testDispatcher.scheduler.runCurrent()
        org.junit.Assert.assertSame(originalItem, viewModel.uiState.value.transactions.single())
    }

    private fun createViewModel(repository: FakeTransactionsRepository): TransactionsViewModel {
        return TransactionsViewModel(
            getTransactionsByMonth = GetTransactionsByMonthUseCase(repository),
            deleteTransaction = DeleteTransactionUseCase(repository),
            categoriesRepository = FakeCategoriesRepository(),
            dateProvider = fakeDateProvider,
            mappingDispatcher = testDispatcher
        )
    }

    private fun createFakeTransaction(id: Long) = Transaction(
        id = id,
        description = "Test",
        amount = Money.fromCents(100),
        categoryId = 1,
        date = LocalDate.now(),
        type = br.com.otavioesteves.finances.domain.model.TransactionType.EXPENSE
    )
}
