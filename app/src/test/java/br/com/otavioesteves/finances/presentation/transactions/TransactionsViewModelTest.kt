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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
    }

    private class FakeTransactionsRepository : TransactionsRepository {
        var deletedId: Long? = null
        var shouldFail = false

        override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> = flowOf(emptyList())
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
        val transaction = createFakeTransaction(id = 1)
        
        viewModel.onDeleteRequest(transaction)
        
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
        
        viewModel.onDeleteRequest(createFakeTransaction(id = 1))
        viewModel.onDeleteConfirm()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertNotNull(viewModel.uiState.value.error)
        assertEquals("Erro ao excluir transação", viewModel.uiState.value.error)
    }

    private fun createViewModel(repository: FakeTransactionsRepository): TransactionsViewModel {
        return TransactionsViewModel(
            getTransactionsByMonth = GetTransactionsByMonthUseCase(repository),
            deleteTransaction = DeleteTransactionUseCase(repository),
            categoriesRepository = FakeCategoriesRepository(),
            dateProvider = fakeDateProvider
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
