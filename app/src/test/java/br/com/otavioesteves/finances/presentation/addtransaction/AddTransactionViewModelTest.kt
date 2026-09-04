package br.com.otavioesteves.finances.presentation.addtransaction

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.usecase.AddTransactionUseCase
import br.com.otavioesteves.finances.domain.usecase.DeleteTransactionUseCase
import br.com.otavioesteves.finances.domain.usecase.GetTransactionUseCase
import br.com.otavioesteves.finances.domain.usecase.UpdateTransactionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val fakeDate = LocalDate.of(2025, 12, 15)
    private val fakeDateProvider = object : DateProvider {
        override fun getCurrentDate(): LocalDate = fakeDate
        override fun getCurrentMonthPeriod(): MonthPeriod = MonthPeriod(2025, 12)
    }

    private class FakeTransactionsRepository(
        var transactions: List<Transaction> = emptyList()
    ) : TransactionsRepository {
        var deletedId: Long? = null
        var shouldFailDelete = false

        override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> = flowOf(transactions)
        override fun getMonthlyBalance(period: MonthPeriod): Flow<Money> = flowOf(Money.Zero)
        override suspend fun addTransaction(transaction: Transaction) {
            transactions = transactions + transaction
        }
        override suspend fun removeTransaction(transactionId: Long) {
            if (shouldFailDelete) throw Exception("Error")
            deletedId = transactionId
            transactions = transactions.filter { it.id != transactionId }
        }
        override suspend fun updateTransaction(transaction: Transaction) {
            transactions = transactions.map { if (it.id == transaction.id) transaction else it }
        }
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
    fun `loadTransaction with valid ID updates UI state`() = runTest {
        val transaction = createFakeTransaction(1)
        val transRepo = FakeTransactionsRepository(listOf(transaction))
        
        val viewModel = createViewModel(transRepo, transactionId = 1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Test", viewModel.uiState.value.description)
    }

    @Test
    fun `onDeleteRequest shows confirmation`() = runTest {
        val viewModel = createViewModel(FakeTransactionsRepository())
        viewModel.onDeleteRequest()
        assertTrue(viewModel.uiState.value.showDeleteConfirmation)
    }

    @Test
    fun `onDeleteCancel hides confirmation`() = runTest {
        val viewModel = createViewModel(FakeTransactionsRepository())
        viewModel.onDeleteRequest()
        viewModel.onDeleteCancel()
        assertFalse(viewModel.uiState.value.showDeleteConfirmation)
    }

    @Test
    fun `onDeleteConfirm calls delete use case and emits success`() = runTest {
        val repository = FakeTransactionsRepository(listOf(createFakeTransaction(10)))
        val viewModel = createViewModel(repository, transactionId = 10)
        
        viewModel.onDeleteConfirm()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(10L, repository.deletedId)
        assertTrue(viewModel.uiState.value.isSaveSuccessful)
    }

    @Test
    fun `onDeleteConfirm failure emits error`() = runTest {
        val repository = FakeTransactionsRepository().apply { shouldFailDelete = true }
        val viewModel = createViewModel(repository, transactionId = 1)
        
        viewModel.onDeleteConfirm()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertNotNull(viewModel.uiState.value.error)
        assertEquals("Erro ao excluir transação", viewModel.uiState.value.error)
    }

    private fun createViewModel(
        repository: FakeTransactionsRepository,
        transactionId: Long? = null
    ): AddTransactionViewModel {
        return AddTransactionViewModel(
            addTransactionUseCase = AddTransactionUseCase(repository),
            updateTransactionUseCase = UpdateTransactionUseCase(repository),
            getTransactionUseCase = GetTransactionUseCase(repository),
            deleteTransactionUseCase = DeleteTransactionUseCase(repository),
            categoriesRepository = FakeCategoriesRepository(),
            dateProvider = fakeDateProvider,
            transactionId = transactionId
        )
    }

    private fun createFakeTransaction(id: Long) = Transaction(
        id = id,
        description = "Test",
        amount = Money.fromCents(10000),
        categoryId = 1,
        date = fakeDate,
        type = TransactionType.EXPENSE
    )
}
